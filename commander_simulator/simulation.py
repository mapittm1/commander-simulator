"""Game orchestration and event capture."""

from __future__ import annotations

from collections import Counter

from . import settings
from .player import Player
from .phases import develop_mana, cast_spells, apply_engine, interact, combat


def game(decks, rng, log=False, max_turn=15):
    """Run one heuristic multiplayer game and optionally capture events."""
    if max_turn < 1:
        raise ValueError("max_turn must be at least 1")
    players = [Player(n, d, rng) for n, d in decks.items()]
    events = []
    metrics = Counter()
    winner = None

    def emit(t, p, msg):
        if log:
            events.append({'turn': t, 'player': p.name, 'action': msg, 'life': p.life, 'lands': p.lands, 'ramp': p.rocks, 'board': len(p.board), 'tokens': p.tokens})
    # Each simulated round visits players in deck-list order.
    for turn in range(1, max_turn + 1):
        for p in players:
            if not p.alive:
                continue
            mana = develop_mana(p, turn, emit)
            mana = cast_spells(p, mana, turn, emit)
            apply_engine(p, players, turn, emit)
            interact(p, players, mana, turn, emit)
            combat(p, players, turn, emit)
            for q in players:
                if q.alive and q.life <= 0:
                    q.alive = False
                    emit(turn, p, f'{q.name} eliminated')
            if sum((q.alive for q in players)) <= 1:
                winner = next((q.name for q in players if q.alive), None)
                break
        if winner:
            break
    for p in players:
        metrics[p.name + '_life'] = p.life
        metrics[p.name + '_commander'] = p.cmd
        metrics[p.name + '_engine'] = p.engine()
    metrics['turns'] = turn
    metrics['winner'] = winner or settings.UNRESOLVED
    metrics['Teysa_alive'] = next((p.alive for p in players if p.name == settings.TEYSA))
    return (metrics, events)
