"""Use Forge's legality and cost gates before its paid spell-casting entry point."""

from .fixtures import _card, _new_game, _resolve_one, _settle


def observe_casting_case(java, cards: dict, case: str) -> dict:
    """Control the priority snapshot; exercise actual native mana payment and resolution."""
    context = _setup(java, cards, case)
    ability, actor, game = (context[key] for key in ("ability", "actor", "game"))
    context.update(
        native_can_play=bool(ability.canPlay()),
        native_can_pay=bool(
            java.JClass("forge.ai.ComputerUtilCost").canPayCost(ability, actor, False)
        ),
        has_priority=bool(game.getPhaseHandler().getPriorityPlayer() == actor),
        cast_api_called=False,
        cast_succeeded=False,
        payment=[],
        prepared_target_ids=[
            int(card.getId()) for card in ability.getTargets().getTargetCards()
        ],
    )
    states = {"before": _snapshot(context)}
    if context["has_priority"] and context["native_can_play"]:
        context["cast_api_called"] = True
        context["cast_succeeded"] = bool(
            java.JClass("forge.ai.ComputerUtil").playStack(ability, actor, game)
        )
        context["payment"] = _payment(java, ability)
        _settle(game)
    states["after_attempt"] = _snapshot(context)
    if context["cast_succeeded"]:
        _resolve_one(game)
    states["after_resolution"] = _snapshot(context)
    return {"id": case, "states": states}


def _setup(java, cards, case) -> dict:
    game = _new_game(java)
    actor = game.getPlayers().get(java.JInt(0))
    zone = java.JClass("forge.game.zone.ZoneType")
    target = _card(java, cards["source"], actor, zone.Battlefield)
    for player in game.getPlayers():
        player.setLife(40, None)
        _card(java, cards["basic"], player, zone.Library)
    count = (
        0
        if case == "cast_instant_unaffordable"
        else (2 if case == "cast_permanent_nonempty_stack" else 1)
    )
    land = (
        cards["white_basic"] if case == "cast_instant_wrong_color" else cards["basic"]
    )
    sources = [_card(java, land, actor, zone.Battlefield) for _ in range(count)]
    phase = java.JClass("forge.game.phase.PhaseType")
    game.getPhaseHandler().devModeSet(
        (
            phase.COMBAT_DECLARE_ATTACKERS
            if case in {"cast_permanent_combat_timing", "cast_instant_combat_paid"}
            else phase.MAIN1
        ),
        actor,
        java.JInt(1),
    )
    game.getPhaseHandler().setPriority(
        game.getPlayers().get(
            java.JInt(1 if case == "cast_instant_without_priority" else 0)
        )
    )
    _settle(game)
    if case == "cast_permanent_nonempty_stack":
        blocker = _prepared_spell(java, cards["hexproof"], actor, target, True)
        if not java.JClass("forge.ai.ComputerUtil").playStack(blocker, actor, game):
            raise ValueError("Pending-spell control did not pay its native cost")
        _settle(game)
    spell = _prepared_spell(
        java,
        cards["equipment"] if "permanent" in case else cards["hexproof"],
        actor,
        target,
        "permanent" not in case,
    )
    return {
        "game": game,
        "actor": actor,
        "target": target,
        "ability": spell,
        "spell": spell.getHostCard(),
        "sources": sources,
    }


def _prepared_spell(java, record, actor, target, targeted):
    spell = _card(java, record, actor, java.JClass("forge.game.zone.ZoneType").Hand)
    ability = spell.getFirstSpellAbility()
    ability.setActivatingPlayer(actor)
    if targeted:
        if not ability.canTarget(target):
            raise ValueError("Prepared casting target is not legal")
        ability.getTargets().add(target)
    return ability


def _payment(java, ability) -> list:
    return [
        {
            "source_id": int(mana.getSourceCard().getId()),
            "source_timestamp": int(mana.getSourceCard().getGameTimestamp()),
            "color": int(mana.getColor()),
            "symbol": str(
                java.JClass("forge.card.MagicColor").toShortString(mana.getColor())
            ),
        }
        for mana in ability.getPayingMana()
    ]


def _snapshot(context) -> dict:
    game, actor, ability = (context[key] for key in ("game", "actor", "ability"))
    spell = game.getCardState(context["spell"])
    target = game.getCardState(context["target"])
    entries = [entry.getSpellAbility() for entry in game.getStack()]
    sources = [
        {
            "id": int(card.getId()),
            "timestamp": int(card.getGameTimestamp()),
            "controller_id": int(card.getController().getId()),
            "name": str(card.getName()),
            "zone": str(card.getZone().getZoneType()),
            "tapped": bool(card.isTapped()),
        }
        for card in context["sources"]
    ]
    return {
        "actor_id": int(actor.getId()),
        "priority_id": int(game.getPhaseHandler().getPriorityPlayer().getId()),
        "active_player_id": int(game.getPhaseHandler().getPlayerTurn().getId()),
        "phase": str(game.getPhaseHandler().getPhase().name()),
        "actor_life": int(actor.getLife()),
        "spell_id": int(spell.getId()),
        "spell_timestamp": int(spell.getGameTimestamp()),
        "spell_zone": str(spell.getZone().getZoneType()),
        "ability_id": int(ability.getId()),
        "target_id": int(target.getId()),
        "target_timestamp": int(target.getGameTimestamp()),
        "target_power": int(target.getNetPower()),
        "target_toughness": int(target.getNetToughness()),
        "sources": sources,
        "source_count": len(sources),
        "tapped_sources": sum(source["tapped"] for source in sources),
        "stack": len(entries),
        "stack_ability_ids": [int(entry.getId()) for entry in entries],
        "stack_card_ids": [int(entry.getHostCard().getId()) for entry in entries],
        "prepared_target_ids": context["prepared_target_ids"],
        "stack_target_ids": (
            []
            if not entries
            else [
                int(card.getId()) for card in entries[0].getTargets().getTargetCards()
            ]
        ),
        "mana_pool": int(actor.getManaPool().totalMana()),
        "payment": list(context["payment"]),
        "mana_paid": len(context["payment"]),
        **{
            key: context[key]
            for key in (
                "native_can_play",
                "native_can_pay",
                "has_priority",
                "cast_api_called",
                "cast_succeeded",
            )
        },
    }
