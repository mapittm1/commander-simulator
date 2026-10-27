"""Controlled partial states driven through Forge damage, trigger, and resolution APIs."""

from .responses import RESPONSE_CARDS
from .suite import STAGES


def observe_case(java, cards: dict, case: str) -> dict:
    """Observe one built-in scenario; this never creates a production deck or game job."""
    game = _new_game(java)
    player = game.getPlayers().get(java.JInt(0))
    opponent = game.getPlayers().get(java.JInt(1))
    zone = java.JClass("forge.game.zone.ZoneType")
    source = _card(java, cards["source"], player, zone.Battlefield)
    equipment = None
    if case != "no_equipment":
        owner = opponent if case == "opponent_equipment" else player
        equipment = _card(java, cards["equipment"], owner, zone.Graveyard)
    _modifiers(java, cards, game, case)
    for participant in game.getPlayers():
        participant.setLife(40, None)
        _card(java, cards["basic"], participant, zone.Library)
    _settle(game)
    game.getTriggerHandler().resetActiveTriggers()
    game.copyLastState()
    tracked_cards = (source, equipment)
    states = {STAGES[0]: _snapshot(java, game, tracked_cards, cards, case)}
    _deal_damage(java, game, source, opponent, not case.startswith("noncombat_"))
    _settle(game)
    states[STAGES[1]] = _snapshot(java, game, tracked_cards, cards, case)
    if case == "source_removed":
        game.getAction().moveToGraveyard(source, None)
        _settle(game)
    if case == "initial_trigger_countered":
        _counter_trigger(java, game, cards["counter"])
    if case == "targeting_restored_before_selection":
        _remove_guard(java, game, cards)
    states[STAGES[2]] = _snapshot(java, game, tracked_cards, cards, case)
    _resolve_one(game)
    states[STAGES[3]] = _snapshot(java, game, tracked_cards, cards, case)
    transitions = _respond(java, game, player, equipment, case)
    response = (
        _response_spell(java, game, cards[RESPONSE_CARDS[case]])
        if case in RESPONSE_CARDS
        else None
    )
    if case == "targeting_blocked_in_response":
        _card(java, cards["graveyard_guard"], opponent, zone.Battlefield)
        _settle(game)
    if case == "reflexive_trigger_countered":
        _counter_trigger(java, game, cards["counter"])
    states[STAGES[4]] = _snapshot(java, game, tracked_cards, cards, case)
    if case == "target_zone_roundtrip":
        states[STAGES[4]]["equipment_transitions"] = transitions
    if response is not None:
        states[STAGES[4]]["response_spell"] = response
    _resolve_one(game)
    states[STAGES[5]] = _snapshot(java, game, tracked_cards, cards, case)
    return {"id": case, "states": states}


def _deal_damage(java, game, source, opponent, combat) -> None:
    damage = java.JClass("forge.game.card.CardDamageTable")()
    damage.put(source, opponent, java.JInt(3))
    game.getAction().dealDamage(
        combat,
        damage,
        java.JClass("forge.game.card.CardDamageTable")(),
        java.JClass("forge.game.GameEntityCounterTable")(),
        None,
    )


def _new_game(java):
    players = java.JClass("java.util.ArrayList")()
    for index in range(4):
        name = f"InteractionFixture{index}"
        registered = java.JClass("forge.game.player.RegisteredPlayer")(
            java.JClass("forge.deck.Deck")(name)
        )
        registered.setPlayer(
            java.JClass("forge.player.GamePlayerUtil").createAiPlayer(name, index, "")
        )
        players.add(registered)
    rules = java.JClass("forge.game.GameRules")(
        java.JClass("forge.game.GameType").Commander
    )
    game = java.JClass("forge.game.Match")(
        rules, players, "Controlled interaction fixture"
    ).createGame()
    game.setAge(java.JClass("forge.game.GameStage").Play)
    player = game.getPlayers().get(java.JInt(0))
    game.getPhaseHandler().devModeSet(
        java.JClass("forge.game.phase.PhaseType").MAIN1, player, java.JInt(1)
    )
    return game


def _modifiers(java, cards, game, case) -> None:
    player = game.getPlayers().get(java.JInt(0))
    opponent = game.getPlayers().get(java.JInt(1))
    zone = java.JClass("forge.game.zone.ZoneType")
    if case == "equipment_creature":
        _card(java, cards["modifier"], opponent, zone.Battlefield)
    if case in {"combat_prevented", "doubled_damage_prevented"}:
        _card(java, cards["preventer"], opponent, zone.Battlefield)
    if case in {"noncombat_prevented", "combat_unaffected_by_noncombat_prevention"}:
        walker = _card(java, cards["noncombat_preventer"], opponent, zone.Battlefield)
        counter = java.JClass("forge.game.card.CounterType").getType("LOYALTY")
        walker.addCounterInternal(
            counter,
            java.JInt(int(cards["noncombat_preventer"]["oracle"]["loyalty"])),
            None,
            False,
            None,
            None,
        )
    if case in {"combat_doubled", "noncombat_doubled", "doubled_damage_prevented"}:
        _card(java, cards["doubler"], player, zone.Battlefield)
    if case in {"graveyard_targeting_blocked", "targeting_restored_before_selection"}:
        _card(java, cards["graveyard_guard"], opponent, zone.Battlefield)


def _remove_guard(java, game, cards) -> None:
    zone = java.JClass("forge.game.zone.ZoneType")
    opponent = game.getPlayers().get(java.JInt(1))
    guards = [
        card
        for card in opponent.getCardsIn(zone.Battlefield)
        if card.getName() == cards["graveyard_guard"]["engine_name"]
    ]
    if len(guards) != 1:
        raise ValueError("Target selection scenario requires exactly one Ground Seal")
    game.getAction().exile(guards[0], None, java.JClass("java.util.HashMap")())
    _settle(game)


def _counter_trigger(java, game, record) -> None:
    if game.getStack().size() != 1 or not game.getStack().peekAbility().isTrigger():
        raise ValueError(
            "Counter scenario requires exactly one pending triggered ability"
        )
    opponent = game.getPlayers().get(java.JInt(1))
    spell = _card(java, record, opponent, java.JClass("forge.game.zone.ZoneType").Hand)
    ability = spell.getFirstSpellAbility()
    ability.setActivatingPlayer(opponent)
    ability.getTargets().add(game.getStack().peekAbility())
    game.getAction().moveToStack(spell, ability)
    game.getStack().add(ability)
    _settle(game)


def _stack_state(game) -> dict:
    entries = [instance.getSpellAbility() for instance in game.getStack()]
    top = entries[0] if entries else None
    targets = (
        []
        if top is None
        else [int(card.getId()) for card in top.getTargets().getTargetCards()]
    )
    spells = [] if top is None else list(top.getTargets().getTargetSpells())
    is_counter = top is not None and str(top.getApi()) == "Counter"
    return {
        "stack": len(entries),
        "targets": targets,
        "target_timestamps": (
            []
            if top is None
            else [
                int(card.getGameTimestamp())
                for card in top.getTargets().getTargetCards()
            ]
        ),
        "target_count": len(targets),
        "spell_target_count": len(spells),
        "counter_on_stack": is_counter,
        "counter_targets_pending_trigger": bool(
            is_counter
            and len(spells) == 1
            and len(entries) == 2
            and entries[1].isTrigger()
            and spells[0].getId() == entries[1].getId()
        ),
        "stack_ability_ids": [int(ability.getId()) for ability in entries],
        "spell_target_ids": [int(ability.getId()) for ability in spells],
    }


def _effect_state(java, game, cards) -> dict:
    zone = java.JClass("forge.game.zone.ZoneType")
    player = game.getPlayers().get(java.JInt(0))
    opponent = game.getPlayers().get(java.JInt(1))
    opponent_cards = {
        str(card.getName()): card for card in opponent.getCardsIn(zone.Battlefield)
    }
    walker = opponent_cards.get(cards["noncombat_preventer"]["engine_name"])
    return {
        "graveyard_guard_active": cards["graveyard_guard"]["engine_name"]
        in opponent_cards,
        "prevention_active": cards["preventer"]["engine_name"] in opponent_cards,
        "noncombat_prevention_active": walker is not None,
        "noncombat_preventer_loyalty": (
            int(
                walker.getCounters(
                    java.JClass("forge.game.card.CounterType").getType("LOYALTY")
                )
            )
            if walker is not None
            else None
        ),
        "doubler_active": any(
            card.getName() == cards["doubler"]["engine_name"]
            for card in player.getCardsIn(zone.Battlefield)
        ),
        "counter_in_graveyard": any(
            card.getName() == cards["counter"]["engine_name"]
            for card in opponent.getCardsIn(zone.Graveyard)
        ),
    }


def _card(java, record, owner, zone):
    paper = (
        java.JClass("forge.model.FModel")
        .getMagicDb()
        .getCommonCards()
        .getCard(record["engine_name"])
    )
    if paper is None:
        raise ValueError("Pinned engine card is absent: " + record["engine_name"])
    card = java.JClass("forge.game.card.Card").fromPaperCard(paper, owner)
    card.setGameTimestamp(owner.getGame().getNextTimestamp())
    if zone is not None:
        owner.getZone(zone).add(card)
    return card


def _settle(game) -> None:
    game.getAction().checkStateEffects(True)
    game.getStack().addAllTriggeredAbilitiesToStack()


def _resolve_one(game) -> None:
    if not game.getStack().isEmpty():
        game.getStack().resolveStack()
        _settle(game)


def _tokens(java, player):
    zone = java.JClass("forge.game.zone.ZoneType")
    return [card for card in player.getCardsIn(zone.Battlefield) if card.isToken()]


def _respond(java, game, player, equipment, case) -> list:
    transitions = []
    if case == "target_exiled":
        game.getAction().exile(
            game.getCardState(equipment), None, java.JClass("java.util.HashMap")()
        )
    elif case == "token_removed":
        tokens = _tokens(java, player)
        if len(tokens) != 1:
            raise ValueError("Removal scenario did not produce exactly one token")
        game.getAction().exile(tokens[0], None, java.JClass("java.util.HashMap")())
    elif case == "target_zone_roundtrip":
        current = game.getCardState(equipment)
        exiled = game.getAction().exile(
            current, None, java.JClass("java.util.HashMap")()
        )
        _settle(game)
        transitions.append(_zone_identity(exiled))
        returned = game.getAction().moveToGraveyard(exiled, None)
        _settle(game)
        transitions.append(_zone_identity(returned))
    _settle(game)
    return transitions


def _zone_identity(card) -> dict:
    return {
        "id": int(card.getId()),
        "timestamp": int(card.getGameTimestamp()),
        "zone": str(card.getZone().getZoneType()),
    }


def _response_spell(java, game, record) -> dict:
    player = game.getPlayers().get(java.JInt(0))
    tokens = _tokens(java, player)
    if len(tokens) != 1 or game.getStack().size() != 1:
        raise ValueError(
            "Response requires one token and one pending reflexive trigger"
        )
    spell = _card(java, record, player, java.JClass("forge.game.zone.ZoneType").Hand)
    ability = spell.getFirstSpellAbility()
    ability.setActivatingPlayer(player)
    if not ability.canTarget(tokens[0]):
        raise ValueError("Response spell cannot legally target the fixture token")
    ability.getTargets().add(tokens[0])
    game.getAction().moveToStack(spell, ability)
    game.getStack().add(ability)
    _settle(game)
    evidence = _stack_state(game)
    _resolve_one(game)
    evidence["zone"] = str(game.getCardState(spell).getZone().getZoneType())
    return evidence


def _token_defenses(java, game, token, cards, case) -> dict:
    if case not in RESPONSE_CARDS:
        return {}
    keyword = java.JClass("forge.game.keyword.Keyword")
    state = {
        "assassin_protection_artifacts": (
            bool(token.hasKeyword("Protection:Artifact")) if token is not None else None
        ),
        "assassin_hexproof": (
            bool(token.hasKeyword(keyword.HEXPROOF)) if token is not None else None
        ),
        "assassin_shroud": (
            bool(token.hasKeyword(keyword.SHROUD)) if token is not None else None
        ),
        "actor_can_target_assassin": None,
        "opponent_can_target_assassin": None,
    }
    if token is not None:
        for index, field in (
            (0, "actor_can_target_assassin"),
            (1, "opponent_can_target_assassin"),
        ):
            owner = game.getPlayers().get(java.JInt(index))
            probe = _card(
                java, cards["artifact_protection"], owner, None
            ).getFirstSpellAbility()
            probe.setActivatingPlayer(owner)
            state[field] = bool(probe.canTarget(token))
    return state


def _snapshot(java, game, tracked_cards, cards, case) -> dict:
    source, equipment = tracked_cards
    player = game.getPlayers().get(java.JInt(0))
    opponent = game.getPlayers().get(java.JInt(1))
    tokens = _tokens(java, player)
    stack_state = _stack_state(game)
    current = game.getCardState(equipment) if equipment is not None else None
    token = tokens[0] if len(tokens) == 1 else None
    return {
        "life": int(opponent.getLife()),
        **stack_state,
        "equipment_targeted": current is not None
        and stack_state["targets"] == [int(current.getId())],
        "tokens": len(tokens),
        "token_ids": [int(card.getId()) for card in tokens],
        "token_timestamps": [int(card.getGameTimestamp()) for card in tokens],
        "token_name": str(token.getName()) if token is not None else None,
        "token_power": int(token.getNetPower()) if token is not None else None,
        "token_toughness": int(token.getNetToughness()) if token is not None else None,
        "token_black": bool(token.isBlack()) if token is not None else None,
        "token_menace": (
            bool(token.hasKeyword(java.JClass("forge.game.keyword.Keyword").MENACE))
            if token is not None
            else None
        ),
        "equipment_zone": (
            str(current.getZone().getZoneType()) if current is not None else None
        ),
        "equipment_is_creature": (
            bool(current.isCreature()) if current is not None else None
        ),
        "attached": bool(
            current is not None
            and token is not None
            and current.getAttachedTo() == token
        ),
        "equipment_id": int(current.getId()) if current is not None else None,
        "equipment_timestamp": (
            int(current.getGameTimestamp()) if current is not None else None
        ),
        "equipment_owned_by_actor": (
            bool(current.getOwner() == player) if current is not None else None
        ),
        "source_zone": str(game.getCardState(source).getZone().getZoneType()),
        **_effect_state(java, game, cards),
        **_token_defenses(java, game, token, cards, case),
    }
