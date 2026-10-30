package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

/** Paid action histories must constrain future actions in every copied world. */
public class DelayedGameCopyTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            players.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("usage" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Usage fixture"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 4; index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        return game;
    }

    private void pay(Game game, SpellAbility ability, Player owner, int mana) {
        ability.setActivatingPlayer(owner);
        assertTrue(ability.canPlay(), "Native action admission");
        assertTrue(ComputerUtil.playStack(ability, owner, game), "Native action payment");
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        game.getAction().checkStateEffects(true);
    }

    private Card cast(Game game, String name, Player owner, int mana) {
        Card card = addCardToZone(name, owner, ZoneType.Hand);
        pay(game, card.getFirstSpellAbility(), owner, mana);
        game.getStack().resolveStack();
        game.getAction().checkStateEffects(true);
        return find(game, card.getId());
    }

    private Card find(Game game, int identity) {
        return game.getCardsInGame().stream().filter(card -> card.getId() == identity)
                .findFirst().orElseThrow();
    }

    private SpellAbility activated(Card card, ApiType api, Player owner) {
        SpellAbility ability = card.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == api).findFirst().orElseThrow();
        ability.setActivatingPlayer(owner);
        return ability;
    }

    private Game snapshot(Game game) {
        GameCopier copier = new GameCopier(game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty(),
                "All callbacks in this fixed witness have explicit mappings: "
                        + copier.getUnmappedSnapshotCommands());
        return copy;
    }

    private void triggers(Game game) {
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }



    /** Run native phase hooks, not individual counter-reset methods. No priority is offered. */
    private void nextTurn(Game game) {
        int previousTurn = game.getPhaseHandler().getTurn();
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(
                PhaseType.MAIN1, () -> settle(game)), "Native turn advance");
        settle(game);
        assertEquals(game.getPhaseHandler().getTurn(), previousTurn + 1);
        assertEquals(game.getPhaseHandler().getPhase(), PhaseType.MAIN1);
    }

    private void settle(Game game) {
        triggers(game);
        int guard = 0;
        while (!game.getStack().isEmpty()) {
            assertTrue(++guard < 40, "Bounded fixture trigger resolution");
            game.getStack().resolveStack();
            triggers(game);
        }
    }

    private void round(Game game, int ownerId) {
        for (int seat = 0; seat < 4; seat++) {
            nextTurn(game);
        }
        assertEquals(game.getPhaseHandler().getPlayerTurn().getId(), ownerId);
    }



    private void advance(Game game, PhaseType phase) {
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(phase, () -> settle(game)));
        settle(game);
    }

    @org.testng.annotations.DataProvider(name = "kikiCopies")
    public Object[][] kikiCopies() {
        return new Object[][] {{false, false}, {false, true}, {true, false}, {true, true}};
    }

    @Test(dataProvider = "kikiCopies")
    public void kikiTokenIsSacrificedAfterSnapshotEvenWhenItsSourceLeaves(
            boolean removeSource, boolean includeStack) {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        Player opponent = source.getPlayers().get(1);
        addCards("Mountain", 5, owner);
        addCard("Mountain", opponent);
        Card bear = addCard("Grizzly Bears", owner);
        Card kiki = cast(source, "Kiki-Jiki, Mirror Breaker", owner, 5);
        SpellAbility copyAbility = activated(kiki, ApiType.CopyPermanent, owner);
        copyAbility.getTargets().add(bear);
        pay(source, copyAbility, owner, 0);
        source.getStack().resolveStack();
        settle(source);
        Card token = owner.getCardsIn(ZoneType.Battlefield).stream()
                .filter(Card::isToken).findFirst().orElseThrow();
        assertEquals(token.getName(), "Grizzly Bears");
        if (removeSource) {
            targetedSpell(source, "Lightning Bolt", opponent, kiki, 1);
            assertEquals(find(source, kiki.getId()).getZone().getZoneType(), ZoneType.Graveyard);
        }
        Game copy = new GameCopier(source).withStack(includeStack).makeCopy();
        assertTrue(copy.getCardsIn(ZoneType.Battlefield).stream()
                .anyMatch(card -> card.getId() == token.getId()));
        assertTrue(copy.getPhaseHandler().devAdvanceToPhase(
                PhaseType.END_OF_TURN, () -> settle(copy)));
        settle(copy);
        assertFalse(copy.getCardsIn(ZoneType.Battlefield).stream()
                .anyMatch(card -> card.getId() == token.getId()),
                "The delayed sacrifice survives copying, even without visible stack entries");
        assertTrue(source.getCardsIn(ZoneType.Battlefield).contains(token));
        assertSame(token.getGame(), source);
    }

    @org.testng.annotations.DataProvider(name = "stackCopies")
    public Object[][] stackCopies() {
        return new Object[][] {{false}, {true}};
    }

    @Test(dataProvider = "stackCopies")
    public void ghostwayReturnsBothRememberedCardsAtTheCopiedEndStep(boolean includeStack) {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        addCards("Plains", 3, owner);
        Card first = addCard("Grizzly Bears", owner);
        Card second = addCard("Runeclaw Bear", owner);
        cast(source, "Ghostway", owner, 3);
        settle(source);
        assertEquals(find(source, first.getId()).getZone().getZoneType(), ZoneType.Exile);
        assertEquals(find(source, second.getId()).getZone().getZoneType(), ZoneType.Exile);
        Game copy = new GameCopier(source).withStack(includeStack).makeCopy();
        assertTrue(copy.getPhaseHandler().devAdvanceToPhase(
                PhaseType.END_OF_TURN, () -> settle(copy)));
        settle(copy);
        for (Card original : List.of(first, second)) {
            Card mapped = find(copy, original.getId());
            assertEquals(mapped.getZone().getZoneType(), ZoneType.Battlefield);
            assertSame(mapped.getController(), copy.getPlayer(owner.getId()));
            assertSame(mapped.getGame(), copy);
            assertEquals(find(source, original.getId()).getZone().getZoneType(), ZoneType.Exile);
        }
        advance(copy, PhaseType.UPKEEP);
        assertEquals(copy.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getId() == first.getId() || card.getId() == second.getId()).count(), 2L);
    }

    @org.testng.annotations.DataProvider(name = "pactPayment")
    public Object[][] pactPayment() {
        return new Object[][] {{false}, {true}};
    }

    @Test(dataProvider = "pactPayment")
    public void pactDebtReachesTheCorrectCopiedPlayersNextUpkeep(boolean canPay) {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        Player opponent = source.getPlayers().get(1);
        source.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent, 1);
        addCards("Forest", 2, opponent);
        if (canPay) {
            addCards("Island", 5, owner);
        }
        Card bear = addCardToZone("Grizzly Bears", opponent, ZoneType.Hand);
        SpellAbility creatureSpell = bear.getFirstSpellAbility();
        pay(source, creatureSpell, opponent, 2);
        Card pact = addCardToZone("Pact of Negation", owner, ZoneType.Hand);
        SpellAbility counter = pact.getFirstSpellAbility();
        counter.getTargets().add(source.getStack().peekAbility());
        pay(source, counter, owner, 0);
        source.getStack().resolveStack();
        settle(source);
        assertEquals(find(source, bear.getId()).getZone().getZoneType(), ZoneType.Graveyard);
        Game copy = snapshot(source);
        Player mappedOwner = copy.getPlayer(owner.getId());
        for (int seat = 0; seat < 3; seat++) {
            advance(copy, PhaseType.UPKEEP);
        }
        if (canPay) {
            assertFalse(mappedOwner.hasLost());
            assertEquals(mappedOwner.getCardsIn(ZoneType.Battlefield).stream()
                    .filter(card -> card.isLand() && card.isTapped()).count(), 5L,
                    "The real upkeep cost was paid");
            for (int seat = 0; seat < 4; seat++) {
                advance(copy, PhaseType.UPKEEP);
            }
            assertFalse(mappedOwner.hasLost());
            assertEquals(mappedOwner.getCardsIn(ZoneType.Battlefield).stream()
                    .filter(card -> card.isLand() && !card.isTapped()).count(), 5L,
                    "A consumed delayed trigger does not charge a second upkeep");
        } else {
            assertTrue(mappedOwner.hasLost(), "The unpaid Pact loses the game");
            assertEquals(copy.getPlayers().size(), 3);
        }
        assertFalse(owner.hasLost());
        assertEquals(source.getPlayers().size(), 4);
        assertEquals(source.getPhaseHandler().getPlayerTurn(), opponent);
        assertEquals(owner.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isLand() && card.isTapped()).count(), 0L);
    }

    private void targetedSpell(Game game, String name, Player owner, Card target, int mana) {
        Card card = addCardToZone(name, owner, ZoneType.Hand);
        SpellAbility ability = card.getFirstSpellAbility();
        ability.getTargets().add(target);
        pay(game, ability, owner, mana);
        game.getStack().resolveStack();
        settle(game);
    }

    @org.testng.annotations.DataProvider(name = "sneakBlink")
    public Object[][] sneakBlink() {
        return new Object[][] {{false}, {true}};
    }

    @Test(dataProvider = "sneakBlink")
    public void delayedSneakSacrificeTracksTheRightIncarnation(boolean blink) {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        addCards("Mountain", 5, owner);
        addCard("Plains", owner);
        Card sneak = cast(source, "Sneak Attack", owner, 4);
        Card bear = addCardToZone("Grizzly Bears", owner, ZoneType.Hand);
        SpellAbility action = activated(sneak, ApiType.ChangeZone, owner);
        pay(source, action, owner, 1);
        source.getStack().resolveStack();
        settle(source);
        Card admitted = find(source, bear.getId());
        assertEquals(admitted.getZone().getZoneType(), ZoneType.Battlefield);
        long timestamp = admitted.getGameTimestamp();
        if (blink) {
            targetedSpell(source, "Cloudshift", owner, admitted, 1);
            admitted = find(source, bear.getId());
            assertNotEquals(admitted.getGameTimestamp(), timestamp);
        }
        Game copy = snapshot(source);
        assertTrue(copy.getPhaseHandler().devAdvanceToPhase(
                PhaseType.END_OF_TURN, () -> settle(copy)));
        settle(copy);
        boolean present = copy.getCardsIn(ZoneType.Battlefield).stream()
                .anyMatch(card -> card.getId() == bear.getId());
        assertEquals(present, blink, "A new incarnation is not the object owed to the delayed sacrifice");
        assertEquals(find(source, bear.getId()).getZone().getZoneType(), ZoneType.Battlefield);
    }

    @Test
    public void delayedSacrificeCreatedDuringEndStepWaitsForTheNextEndStep() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        addCards("Mountain", 5, owner);
        Card sneak = cast(source, "Sneak Attack", owner, 4);
        assertTrue(source.getPhaseHandler().devAdvanceToPhase(
                PhaseType.END_OF_TURN, () -> settle(source)));
        Card bear = addCardToZone("Grizzly Bears", owner, ZoneType.Hand);
        pay(source, activated(sneak, ApiType.ChangeZone, owner), owner, 1);
        source.getStack().resolveStack();
        settle(source);
        Game copy = snapshot(source);
        advance(copy, PhaseType.UPKEEP);
        assertEquals(find(copy, bear.getId()).getZone().getZoneType(), ZoneType.Battlefield);
        assertTrue(copy.getPhaseHandler().devAdvanceToPhase(
                PhaseType.END_OF_TURN, () -> settle(copy)));
        settle(copy);
        assertEquals(find(copy, bear.getId()).getZone().getZoneType(), ZoneType.Graveyard);
        assertEquals(find(source, bear.getId()).getZone().getZoneType(), ZoneType.Battlefield);
    }

    @Test
    public void infuseRegistersItsSlowtripOnlyForTheFollowingTurn() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        addCards("Island", 3, owner);
        Card bear = addCard("Grizzly Bears", owner);
        targetedSpell(source, "Infuse", owner, bear, 3);
        assertEquals(owner.getCardsIn(ZoneType.Hand).size(), 0);
        Game copy = snapshot(source);
        assertTrue(copy.getPhaseHandler().devAdvanceToPhase(
                PhaseType.END_OF_TURN, () -> settle(copy)));
        settle(copy);
        Player mappedOwner = copy.getPlayer(owner.getId());
        assertEquals(mappedOwner.getCardsIn(ZoneType.Hand).size(), 0);
        advance(copy, PhaseType.UPKEEP);
        assertEquals(mappedOwner.getCardsIn(ZoneType.Hand).size(), 1, "The slowtrip draws on the next turn");
        advance(copy, PhaseType.UPKEEP);
        assertEquals(mappedOwner.getCardsIn(ZoneType.Hand).size(), 1, "A consumed slowtrip is not repeated");
        assertEquals(owner.getCardsIn(ZoneType.Hand).size(), 0);
    }

    @Test
    public void ephemerateReboundsOnceAndBlinksItsCreatureAgain() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        addCards("Forest", 2, owner);
        addCard("Plains", owner);
        Card visionary = cast(source, "Elvish Visionary", owner, 2);
        settle(source);
        assertEquals(owner.getCardsIn(ZoneType.Hand).size(), 1);
        targetedSpell(source, "Ephemerate", owner, visionary, 1);
        Card ephemerate = owner.getCardsIn(ZoneType.Exile).stream()
                .filter(card -> "Ephemerate".equals(card.getName())).findFirst().orElseThrow();
        assertEquals(owner.getCardsIn(ZoneType.Hand).size(), 2);
        Game copy = snapshot(source);
        for (int seat = 0; seat < 4; seat++) {
            advance(copy, PhaseType.UPKEEP);
        }
        Player mappedOwner = copy.getPlayer(owner.getId());
        assertEquals(mappedOwner.getCardsIn(ZoneType.Hand).size(), 3,
                "The rebound blink resolves Visionary's new enter trigger");
        assertEquals(find(copy, ephemerate.getId()).getZone().getZoneType(), ZoneType.Graveyard);
        assertEquals(find(source, ephemerate.getId()).getZone().getZoneType(), ZoneType.Exile);
        assertEquals(owner.getCardsIn(ZoneType.Hand).size(), 2);
        for (int seat = 0; seat < 4; seat++) {
            advance(copy, PhaseType.UPKEEP);
        }
        assertEquals(mappedOwner.getCardsIn(ZoneType.Hand).size(), 4,
                "Only the normal draw was added; rebound is not scheduled again");
    }

    @org.testng.annotations.DataProvider(name = "suppressTiming")
    public Object[][] suppressTiming() {
        return new Object[][] {{false}, {true}};
    }

    @Test(dataProvider = "suppressTiming")
    public void suppressRestoresTheTargetPlayersHandAtThatPlayersNextEndStep(boolean afterRegistration) {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        Player opponent = source.getPlayers().get(1);
        addCards("Swamp", 3, owner);
        Card first = addCardToZone("Grizzly Bears", opponent, ZoneType.Hand);
        Card second = addCardToZone("Island", opponent, ZoneType.Hand);
        Card spell = addCardToZone("Suppress", owner, ZoneType.Hand);
        SpellAbility action = spell.getFirstSpellAbility();
        action.getTargets().add(opponent);
        pay(source, action, owner, 3);
        source.getStack().resolveStack();
        settle(source);
        assertTrue(opponent.getCardsIn(ZoneType.Hand).isEmpty());
        if (afterRegistration) {
            advance(source, PhaseType.UPKEEP);
        }
        Game copy = snapshot(source);
        if (!afterRegistration) {
            assertTrue(copy.getPhaseHandler().devAdvanceToPhase(
                    PhaseType.END_OF_TURN, () -> settle(copy)));
            settle(copy);
            assertTrue(copy.getPlayer(opponent.getId()).getCardsIn(ZoneType.Hand).isEmpty());
            advance(copy, PhaseType.UPKEEP);
        }
        assertEquals(copy.getPhaseHandler().getPlayerTurn().getId(), opponent.getId());
        assertTrue(copy.getPhaseHandler().devAdvanceToPhase(
                PhaseType.END_OF_TURN, () -> settle(copy)));
        settle(copy);
        for (Card original : List.of(first, second)) {
            Card mapped = find(copy, original.getId());
            assertEquals(mapped.getZone().getZoneType(), ZoneType.Hand);
            assertSame(mapped.getOwner(), copy.getPlayer(opponent.getId()));
            assertEquals(find(source, original.getId()).getZone().getZoneType(), ZoneType.Exile);
        }
        assertTrue(opponent.getCardsIn(ZoneType.Hand).isEmpty());
    }

    /** Synthetic held-event contract: preserve collection-time facts across later board changes. */
    @Test
    public void collectedWaitingTriggersKeepTheirOriginalControllerAndMultiplier() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        Player opponent = source.getPlayers().get(1);
        Card warden = addCard("Soul Warden", owner);
        Card panharmonicon = addCard("Panharmonicon", owner);
        addCards("Forest", 2, owner);
        source.getTriggerHandler().suppressMode(forge.game.trigger.TriggerType.ChangesZone);
        Card bear = cast(source, "Grizzly Bears", owner, 2);
        source.getTriggerHandler().clearSuppression(forge.game.trigger.TriggerType.ChangesZone);
        source.getAction().checkStateEffects(true);
        java.util.Map<forge.game.ability.AbilityKey, Object> params = forge.game.ability.AbilityKey.newMap();
        params.put(forge.game.ability.AbilityKey.Card, bear);
        params.put(forge.game.ability.AbilityKey.Origin, "Stack");
        params.put(forge.game.ability.AbilityKey.Destination, "Battlefield");
        source.getTriggerHandler().runTrigger(forge.game.trigger.TriggerType.ChangesZone, params, true);
        source.getTriggerHandler().collectTriggerForWaiting();
        source.getAction().moveToGraveyard(panharmonicon, null);
        warden.setController(opponent, source.getNextTimestamp());
        source.getAction().controllerChangeZoneCorrection(warden);
        assertEquals(owner.getLife(), 40);
        Game copy = snapshot(source);
        settle(copy);
        assertEquals(copy.getPlayer(owner.getId()).getLife(), 42);
        assertEquals(copy.getPlayer(opponent.getId()).getLife(), 40);
        assertEquals(owner.getLife(), 40);
        settle(source);
        assertEquals(owner.getLife(), 42);
        assertEquals(opponent.getLife(), 40);
        assertEquals(copy.getPlayer(owner.getId()).getLife(), 42);
    }

    /** Synthetic payload contract: nested containers, actors and captured multipliers are independent. */
    @Test
    public void waitingPayloadCopiesHaveMappedReferencesAndIndependentCollections() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        Player opponent = source.getPlayers().get(1);
        Card warden = addCard("Soul Warden", owner);
        Card bear = addCard("Grizzly Bears", owner);
        forge.game.trigger.Trigger trigger = warden.getTriggers().get(0);
        java.util.Map<forge.game.ability.AbilityKey, Object> params = forge.game.ability.AbilityKey.newMap();
        params.put(forge.game.ability.AbilityKey.Card, bear);
        params.put(forge.game.ability.AbilityKey.Player, owner);
        params.put(forge.game.ability.AbilityKey.Amount, new ArrayList<>(List.of(bear, owner)));
        forge.game.trigger.TriggerWaiting waiting = new forge.game.trigger.TriggerWaiting(
                forge.game.trigger.TriggerType.ChangesZone, params);
        waiting.setTriggers(List.of(trigger));
        waiting.setAdditionalTriggers(trigger, 1);
        warden.setController(opponent, source.getNextTimestamp());
        source.getAction().controllerChangeZoneCorrection(warden);
        GameCopier copier = new GameCopier(source).withStack(true);
        Game copy = copier.makeCopy();
        forge.game.spellability.SpellAbilitySnapshot context =
                new forge.game.spellability.SpellAbilitySnapshot(new forge.game.IEntityMap() {
                    @Override
                    public Game getGame() { return copy; }

                    @Override
                    public forge.game.GameObject map(forge.game.GameObject object) {
                        if (object == null) return null;
                        if (object instanceof Card card) return copier.find(card);
                        if (object instanceof Player player) return copier.find(player);
                        throw new IllegalArgumentException("Unexpected fixed fixture object");
                    }
                });
        forge.game.trigger.TriggerWaiting mapped = waiting.copyForSnapshot(context);
        forge.game.trigger.Trigger mappedTrigger = mapped.getTriggers().iterator().next();
        assertSame(mapped.getController(mappedTrigger), copy.getPlayer(owner.getId()));
        assertSame(mappedTrigger.getHostCard().getController(), copy.getPlayer(opponent.getId()));
        assertEquals(mapped.getAdditionalTriggers(mappedTrigger), 1);
        assertSame(((Card) mapped.getParams().get(forge.game.ability.AbilityKey.Card)).getGame(), copy);
        assertSame(mapped.getParams().get(forge.game.ability.AbilityKey.Player), copy.getPlayer(owner.getId()));
        mapped.setAdditionalTriggers(mappedTrigger, 3);
        assertEquals(waiting.getAdditionalTriggers(trigger), 1);
        ((List<?>) mapped.getParams().get(forge.game.ability.AbilityKey.Amount)).clear();
        assertEquals(((List<?>) params.get(forge.game.ability.AbilityKey.Amount)).size(), 2);
    }

    @Test
    public void triggerSuppressionFlagsAreRestoredWithoutSharingTheSet() {
        Game source = pod();
        source.getTriggerHandler().suppressMode(forge.game.trigger.TriggerType.ChangesZone);
        source.getTriggerHandler().setSuppressAllTriggers(true);
        Game copy = snapshot(source);
        assertTrue(copy.getTriggerHandler().isTriggerSuppressed(forge.game.trigger.TriggerType.Phase));
        copy.getTriggerHandler().setSuppressAllTriggers(false);
        assertFalse(copy.getTriggerHandler().isTriggerSuppressed(forge.game.trigger.TriggerType.Phase));
        assertTrue(source.getTriggerHandler().isTriggerSuppressed(forge.game.trigger.TriggerType.Phase));
        assertTrue(copy.getTriggerHandler().isTriggerSuppressed(forge.game.trigger.TriggerType.ChangesZone));
        copy.getTriggerHandler().clearSuppression(forge.game.trigger.TriggerType.ChangesZone);
        assertFalse(copy.getTriggerHandler().isTriggerSuppressed(forge.game.trigger.TriggerType.ChangesZone));
        assertTrue(source.getTriggerHandler().isTriggerSuppressed(forge.game.trigger.TriggerType.ChangesZone));
    }


    @org.testng.annotations.DataProvider(name = "registrationLifetimes")
    public Object[][] registrationLifetimes() {
        return new Object[][] {{false}, {true}};
    }

    /** Synthetic native action distinguishes a next-turn window from persistent future registration. */
    @Test(dataProvider = "registrationLifetimes")
    public void deferredRegistrationKeepsItsOriginalLifetime(boolean thisTurnOnly) {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        Card host = addCard("Grizzly Bears", owner);
        host.setSVar("SnapshotDelayedGain", "DB$ GainLife | Defined$ You | LifeAmount$ 1");
        String timing = thisTurnOnly ? "NextTurn" : "UpcomingTurn";
        SpellAbility action = forge.game.ability.AbilityFactory.getAbility(
                "AB$ DelayedTrigger | Cost$ 0 | " + timing
                        + "$ True | Mode$ Phase | Phase$ Upkeep | ValidPlayer$ You | Execute$ SnapshotDelayedGain",
                host);
        pay(source, action, owner, 0);
        source.getStack().resolveStack();
        settle(source);
        Game copy = snapshot(source);
        for (int seat = 0; seat < 4; seat++) {
            advance(copy, PhaseType.UPKEEP);
        }
        assertEquals(copy.getPhaseHandler().getPlayerTurn().getId(), owner.getId());
        assertEquals(copy.getPlayer(owner.getId()).getLife(), thisTurnOnly ? 40 : 41);
        for (int seat = 0; seat < 4; seat++) {
            advance(copy, PhaseType.UPKEEP);
        }
        assertEquals(copy.getPlayer(owner.getId()).getLife(), thisTurnOnly ? 40 : 41,
                "Expired or consumed registrations do not replay");
        assertEquals(owner.getLife(), 40);
    }


    /** Synthetic receipt graph; no claim of resuming an in-progress payment interaction. */
    @Test
    public void costReceiptCopiesRetainAliasesAndIndependentConversionMatrices() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        Card fling = addCardToZone("Fling", owner, ZoneType.Hand);
        SpellAbility action = fling.getFirstSpellAbility();
        action.setActivatingPlayer(owner);
        forge.game.cost.Cost cost = action.getPayCosts();
        forge.game.cost.CostPart part = cost.getCostParts().get(0);
        forge.game.cost.CostPayment receipt = new forge.game.cost.CostPayment(cost, action);
        receipt.setSnowForColor(true);
        receipt.adjustColorReplacement(forge.card.MagicColor.WHITE, forge.card.MagicColor.RED, true);
        forge.game.zone.CostPaymentStack.Entry entry = source.costPaymentStack.push(part, receipt);
        Game copy = snapshot(source);
        forge.game.zone.CostPaymentStack.Entry mapped = copy.costPaymentStack.peek();
        assertNotNull(mapped);
        assertNotSame(mapped, entry);
        assertNotSame(mapped.payment(), receipt);
        assertSame(mapped.payment().getAbility().getHostCard(), find(copy, fling.getId()));
        assertSame(mapped.payment().getAbility().getActivatingPlayer(), copy.getPlayer(owner.getId()));
        assertSame(mapped.payment().getCost(), mapped.payment().getAbility().getPayCosts());
        assertSame(mapped.cost(), mapped.payment().getCost().getCostParts().get(0));
        assertFalse(mapped.payment().isFullyPaid());
        assertEquals(mapped.payment().getPossibleColorUses(forge.card.MagicColor.WHITE),
                receipt.getPossibleColorUses(forge.card.MagicColor.WHITE));
        assertTrue(mapped.payment().isSnowForColor());
        mapped.payment().setSnowForColor(false);
        mapped.payment().adjustColorReplacement(forge.card.MagicColor.WHITE, forge.card.MagicColor.WHITE, false);
        assertTrue(receipt.isSnowForColor());
        assertNotEquals(mapped.payment().getPossibleColorUses(forge.card.MagicColor.WHITE),
                receipt.getPossibleColorUses(forge.card.MagicColor.WHITE));
        copy.costPaymentStack.clear();
        assertSame(source.costPaymentStack.peek(), entry);
    }

}
