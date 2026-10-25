"""Ordered turn phases using the simulator's existing heuristic rules."""

from __future__ import annotations

from . import settings
from .cards import cost


def develop_mana(p, turn, emit):
    # Draw, land drop, and simplified mana development.
    p.draw()
    p.lands += p.ramp_pending
    p.ramp_pending = 0
    land = next((x for x in p.hand if x in settings.LANDS), None)
    if land:
        p.hand.remove(land)
        p.lands += 1
        emit(turn, p, 'Land: ' + land)
    mana = p.mana()
    for c in sorted([x for x in p.hand if x in settings.RAMP], key=lambda x: cost(x)):
        if mana >= cost(c) and (turn <= 5 or p.lands < 5):
            p.hand.remove(c)
            mana -= cost(c)
            if c in settings.LAND_RAMP:
                p.ramp_pending += 1
            else:
                p.rocks += 2 if c == settings.SOL_RING else 1
                p.board.append(c)
            emit(turn, p, 'Ramp: ' + c)
    return mana


def cast_spells(p, mana, turn, emit):
    if not p.cmd and mana >= settings.COST[p.name] + p.tax and (turn >= 3 or p.name == settings.AESI):
        mana -= settings.COST[p.name] + p.tax
        p.cmd = True
        emit(turn, p, 'Cast commander')
    for c in sorted([x for x in p.hand if x not in settings.LANDS and x not in settings.REMOVAL and (x not in settings.WIPES) and (x not in settings.RAMP)], key=lambda x: (x not in settings.ENGINES[p.name], cost(x))):
        if mana < cost(c):
            continue
        p.hand.remove(c)
        mana -= cost(c)
        p.board.append(c)
        emit(turn, p, 'Cast ' + c)
        if c in settings.DRAW_CARDS:
            p.draw(2)
        if c == settings.NECROPOTENCE:
            p.life -= 3
            p.draw(3)
    return mana


def apply_engine(p, players, turn, emit):
    # Apply an archetype-specific engine; these are NOT Oracle triggers.
    if p.name == settings.TEYSA:
        p.tokens += sum((x in p.board for x in settings.TEYSA_TOKEN_CARDS))
        outlet = bool(settings.OUTLETS.intersection(p.board))
        drains = len(settings.DRAIN.intersection(p.board))
        n = min(p.tokens, 2) if outlet and drains else 0
        if n:
            p.tokens -= n
            hits = n * drains * (2 if p.cmd else 1)
            for q in players:
                if q.alive and q is not p:
                    q.life -= hits
            p.life += n * min(drains, 2)
            p.drain += hits
            emit(turn, p, f'Sacrifice {n} tokens, drain each opponent {hits} (abstract)')
    elif p.name == settings.PROSPER:
        if p.cmd:
            p.treasures += 1
            p.tokens += 1
        p.tokens += sum((x in p.board for x in settings.PROSPER_TOKEN_CARDS))
    elif p.name == settings.AESI:
        if p.cmd:
            p.draw(1)
        p.tokens += sum((x in p.board for x in settings.AESI_TOKEN_CARDS))
    else:
        if p.cmd:
            p.tokens += 1
        p.tokens += sum((x in p.board for x in settings.RIN_TOKEN_CARDS))


def interact(p, players, mana, turn, emit):
    # Removal is directed toward the largest modeled threat.
    other = [q for q in players if q.alive and q is not p]
    if other:
        target = max(other, key=lambda q: q.power() + max(0, 40 - q.life) * 0.15)
        if target.power() > 5 and any((c in settings.REMOVAL for c in p.hand)) and (mana >= 2):
            spell = next((c for c in p.hand if c in settings.REMOVAL))
            p.hand.remove(spell)
            mana -= 2
            if target.board:
                victim = max(target.board, key=lambda c: (c in settings.ENGINES[target.name], cost(c)))
                target.board.remove(victim)
                if victim in settings.RAMP:
                    target.rocks = max(0, target.rocks - (2 if victim == settings.SOL_RING else 1))
                emit(turn, p, f'{spell} removes {target.name}: {victim}')
            elif target.cmd:
                target.cmd = False
                target.tax += 2
                emit(turn, p, f'{spell} removes {target.name} commander')
        if target.power() > 12 and any((c in settings.WIPES for c in p.hand)) and (mana >= 5) and (p.power() < target.power() * 0.7):
            spell = next((c for c in p.hand if c in settings.WIPES))
            p.hand.remove(spell)
            mana -= 5
            for q in players:
                q.board = [x for x in q.board if x in settings.RAMP]
                q.tokens = 0
                if q.cmd:
                    q.cmd = False
                    q.tax += 2
            emit(turn, p, 'Board wipe: ' + spell)


def combat(p, players, turn, emit):
    if turn >= 4 and p.tokens + len(p.board) > 2:
        opponents = [q for q in players if q.alive and q is not p]
        if opponents:
            target = min(opponents, key=lambda q: (q.tokens + len(q.board)) * 1.2 + q.life * 0.1)
            attack = max(0, int(p.tokens * 0.7 + len(p.board) * 0.65 - (target.tokens * 0.35 + len(target.board) * 0.25)))
            if attack:
                target.life -= attack
                emit(turn, p, f'Combat: {attack} damage to {target.name} (abstract)')


