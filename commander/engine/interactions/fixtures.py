"""Controlled partial states driven through Forge damage, trigger, and resolution APIs."""

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
    if case == "equipment_creature":
        _card(java, cards["modifier"], opponent, zone.Battlefield)
    for participant in game.getPlayers():
        participant.setLife(40, None)
        _card(java, cards["basic"], participant, zone.Library)
    _settle(game)
    game.getTriggerHandler().resetActiveTriggers()
    game.copyLastState()
    states = {STAGES[0]: _snapshot(java, game, source, equipment)}
    damage = java.JClass("forge.game.card.CardDamageTable")()
    damage.put(source, opponent, java.JInt(3))
    game.getAction().dealDamage(
        case != "noncombat_damage",
        damage,
        java.JClass("forge.game.card.CardDamageTable")(),
        java.JClass("forge.game.GameEntityCounterTable")(),
        None,
    )
    _settle(game)
    states[STAGES[1]] = _snapshot(java, game, source, equipment)
    if case == "source_removed":
        game.getAction().moveToGraveyard(source, None)
        _settle(game)
    states[STAGES[2]] = _snapshot(java, game, source, equipment)
    _resolve_one(game)
    states[STAGES[3]] = _snapshot(java, game, source, equipment)
    _respond(java, game, player, equipment, case)
    states[STAGES[4]] = _snapshot(java, game, source, equipment)
    _resolve_one(game)
    states[STAGES[5]] = _snapshot(java, game, source, equipment)
    return {"id": case, "states": states}


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


def _respond(java, game, player, equipment, case) -> None:
    if case == "target_exiled":
        game.getAction().exile(
            game.getCardState(equipment), None, java.JClass("java.util.HashMap")()
        )
    elif case == "token_removed":
        tokens = _tokens(java, player)
        if len(tokens) != 1:
            raise ValueError("Removal scenario did not produce exactly one token")
        game.getAction().exile(tokens[0], None, java.JClass("java.util.HashMap")())
    _settle(game)


def _snapshot(java, game, source, equipment) -> dict:
    player = game.getPlayers().get(java.JInt(0))
    opponent = game.getPlayers().get(java.JInt(1))
    tokens = _tokens(java, player)
    top = None if game.getStack().isEmpty() else game.getStack().peekAbility()
    targets = (
        []
        if top is None
        else [int(card.getId()) for card in top.getTargets().getTargetCards()]
    )
    current = game.getCardState(equipment) if equipment is not None else None
    token = tokens[0] if len(tokens) == 1 else None
    return {
        "life": int(opponent.getLife()),
        "stack": int(game.getStack().size()),
        "target_count": len(targets),
        "targets": targets,
        "equipment_targeted": current is not None and targets == [int(current.getId())],
        "tokens": len(tokens),
        "token_ids": [int(card.getId()) for card in tokens],
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
        "equipment_owned_by_actor": (
            bool(current.getOwner() == player) if current is not None else None
        ),
        "source_zone": str(game.getCardState(source).getZone().getZoneType()),
    }
