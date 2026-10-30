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
import forge.game.card.CounterEnumType;
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
public class TurnUsageGameCopyTest extends AITest {
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

    @Test
    public void paidOncePerTurnActivationResetsThroughNativeTurnHooks() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        for (String name : List.of("Plains", "Mountain", "Forest")) {
            addCard(name, owner);
        }
        Card bear = addCard("Grizzly Bears", owner);
        Card spring = cast(source, "Invigorating Hot Spring", owner, 3);
        SpellAbility action = activated(spring, ApiType.PutCounter, owner);
        action.getTargets().add(bear);
        pay(source, action, owner, 0);
        source.getStack().resolveStack();
        Game copy = snapshot(source);
        round(copy, owner.getId());
        Player mappedOwner = copy.getPlayer(owner.getId());
        SpellAbility restored = activated(find(copy, spring.getId()), ApiType.PutCounter, mappedOwner);
        assertEquals(restored.getActivationsThisTurn(), 0);
        assertEquals(restored.getActivationsThisGame(), 1);
        assertEquals(restored.getResolvedThisTurn(), 0);
        restored.getTargets().clear();
        restored.getTargets().add(find(copy, bear.getId()));
        pay(copy, restored, mappedOwner, 0);
        copy.getStack().resolveStack();
        assertEquals(restored.getActivationsThisTurn(), 1);
        assertEquals(restored.getActivationsThisGame(), 2);
        assertEquals(find(copy, bear.getId()).getCounters(CounterEnumType.P1P1), 2);
        assertEquals(bear.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(action.getActivationsThisTurn(), 1);
        assertEquals(source.getPhaseHandler().getTurn(), 1);
        assertEquals(mappedOwner.getCardsIn(ZoneType.Hand).size(), 1, "Normal draw happened");
        assertEquals(owner.getCardsIn(ZoneType.Hand).size(), 0);
    }

    @Test
    public void resolvedRootwallaPumpExpiresOnlyInTheAdvancedSnapshot() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        addCards("Forest", 3, owner);
        Card rootwalla = cast(source, "Basking Rootwalla", owner, 1);
        SpellAbility action = activated(rootwalla, ApiType.Pump, owner);
        pay(source, action, owner, 2);
        source.getStack().resolveStack();
        assertEquals(rootwalla.getNetPower(), 3);
        Game copy = snapshot(source);
        assertEquals(find(copy, rootwalla.getId()).getNetPower(), 3);
        nextTurn(copy);
        Card mapped = find(copy, rootwalla.getId());
        assertEquals(mapped.getNetPower(), 1, "Until-end-of-turn pump expires at cleanup");
        assertEquals(mapped.getNetToughness(), 1);
        assertEquals(rootwalla.getNetPower(), 3, "Source turn has not advanced");
        assertEquals(action.getActivationsThisTurn(), 1);
        SpellAbility restored = activated(mapped, ApiType.Pump, copy.getPlayer(owner.getId()));
        assertEquals(restored.getActivationsThisTurn(), 0);
        assertEquals(restored.getActivationsThisGame(), 1);
    }

    private SpellAbility mode(Card card) {
        return card.getTriggers().stream()
                .filter(trigger -> "TrigCharm".equals(trigger.getParam("Execute")))
                .findFirst().orElseThrow().getOverridingAbility();
    }

    @Test
    public void allianceModeHistoryRollsOverThroughNativeTurns() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        addCard("Plains", owner);
        addCard("Island", owner);
        addCards("Forest", 13, owner);
        Card galadriel = cast(source, "Galadriel, Light of Valinor", owner, 5);
        for (int index = 0; index < 3; index++) {
            cast(source, "Grizzly Bears", owner, 2);
            settle(source);
        }
        List<String> chosen = galadriel.getChosenModes(mode(galadriel), "ThisTurn");
        assertEquals(new java.util.HashSet<>(chosen).size(), 3);
        Game copy = snapshot(source);
        round(copy, owner.getId());
        Card mapped = find(copy, galadriel.getId());
        assertNull(mapped.getChosenModes(mode(mapped), "ThisTurn"));
        assertEquals(mapped.getChosenModes(mode(mapped), "ThisGame").size(), 3);
        cast(copy, "Grizzly Bears", copy.getPlayer(owner.getId()), 2);
        settle(copy);
        assertEquals(mapped.getChosenModes(mode(mapped), "ThisTurn").size(), 1);
        assertEquals(mapped.getChosenModes(mode(mapped), "ThisGame").size(), 4);
        assertEquals(chosen.size(), 3);
        assertEquals(galadriel.getChosenModes(mode(galadriel), "ThisGame").size(), 3);
    }

    private SpellAbility gainedPump(Card card, Card origin, Player owner) {
        SpellAbility ability = card.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.Pump
                        && sa.getOriginalAbility() != null
                        && sa.getOriginalAbility().getHostCard().getId() == origin.getId())
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(owner);
        return ability;
    }

    @Test
    public void krajKeepsDistinctUsageLimitsForTwoRealGrantedAbilities() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        addCards("Forest", 14, owner);
        addCards("Island", 2, owner);
        addCard("Plains", owner);
        Card first = cast(source, "Basking Rootwalla", owner, 1);
        Card second = cast(source, "Rootwalla", owner, 3);
        Card kraj = cast(source, "Experiment Kraj", owner, 6);
        Card bond = addCardToZone("Common Bond", owner, ZoneType.Hand);
        SpellAbility spell = bond.getFirstSpellAbility();
        spell.getTargets().add(first);
        spell.getSubAbility().getTargets().add(second);
        pay(source, spell, owner, 3);
        source.getStack().resolveStack();
        source.getAction().checkStateEffects(true);
        assertEquals(first.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(second.getCounters(CounterEnumType.P1P1), 1);
        SpellAbility firstPump = gainedPump(kraj, first, owner);
        SpellAbility secondPump = gainedPump(kraj, second, owner);
        pay(source, firstPump, owner, 2);
        source.getStack().resolveStack();
        assertFalse(firstPump.canPlay());
        assertTrue(secondPump.canPlay(), "The other grant has its own once-per-turn limit");
        Game copy = snapshot(source);
        Player mappedOwner = copy.getPlayer(owner.getId());
        Card mappedKraj = find(copy, kraj.getId());
        SpellAbility mappedFirst = gainedPump(mappedKraj, find(copy, first.getId()), mappedOwner);
        SpellAbility mappedSecond = gainedPump(mappedKraj, find(copy, second.getId()), mappedOwner);
        assertEquals(mappedFirst.getActivationsThisTurn(), 1);
        assertEquals(mappedSecond.getActivationsThisTurn(), 0);
        assertFalse(mappedFirst.canPlay());
        assertTrue(mappedSecond.canPlay());
        assertSame(mappedFirst.getOriginalAbility().getHostCard().getGame(), copy);
        assertSame(mappedSecond.getOriginalAbility().getHostCard().getGame(), copy);
        pay(copy, mappedSecond, mappedOwner, 2);
        copy.getStack().resolveStack();
        assertEquals(mappedFirst.getActivationsThisTurn(), 1);
        assertEquals(mappedSecond.getActivationsThisTurn(), 1);
        assertEquals(firstPump.getActivationsThisTurn(), 1);
        assertEquals(secondPump.getActivationsThisTurn(), 0);
        assertEquals(mappedKraj.getNetPower(), 8);
        assertEquals(kraj.getNetPower(), 6);
    }

    private Card targetedSpell(Game game, String name, Player caster, Card target, int mana) {
        Card spell = addCardToZone(name, caster, ZoneType.Hand);
        SpellAbility ability = spell.getFirstSpellAbility();
        ability.getTargets().add(target);
        pay(game, ability, caster, mana);
        game.getStack().resolveStack();
        settle(game);
        return find(game, spell.getId());
    }

    @org.testng.annotations.DataProvider(name = "singlePumps")
    public Object[][] singlePumps() {
        return new Object[][] {{"Giant Growth", "Forest", 5, false}, {"Jump", "Island", 2, true}};
    }

    @Test(dataProvider = "singlePumps")
    public void resolvedSpellPumpExpiresAcrossRepeatedSnapshots(
            String name, String land, int power, boolean flying) {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        addCard(land, owner);
        Card bear = addCard("Grizzly Bears", owner);
        targetedSpell(source, name, owner, bear, 1);
        assertEquals(bear.getNetPower(), power);
        assertEquals(bear.hasKeyword(forge.game.keyword.Keyword.FLYING), flying);
        Game middle = snapshot(source);
        Game copy = snapshot(middle);
        Card mapped = find(copy, bear.getId());
        assertEquals(mapped.getNetPower(), power);
        assertEquals(mapped.hasKeyword(forge.game.keyword.Keyword.FLYING), flying);
        nextTurn(copy);
        assertEquals(mapped.getNetPower(), 2);
        assertFalse(mapped.hasKeyword(forge.game.keyword.Keyword.FLYING));
        assertEquals(bear.getNetPower(), power);
        assertEquals(bear.hasKeyword(forge.game.keyword.Keyword.FLYING), flying);
        assertEquals(find(middle, bear.getId()).getNetPower(), power);
        assertEquals(find(middle, bear.getId()).hasKeyword(forge.game.keyword.Keyword.FLYING), flying);
    }

    @Test
    public void paidOverrunExpiresOnEveryAffectedCreatureInTheCopiedTurn() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        addCards("Forest", 5, owner);
        Card first = addCard("Grizzly Bears", owner);
        Card second = addCard("Runeclaw Bear", owner);
        cast(source, "Overrun", owner, 5);
        assertEquals(first.getNetPower(), 5);
        assertEquals(second.getNetPower(), 5);
        Game copy = snapshot(source);
        nextTurn(copy);
        for (Card original : List.of(first, second)) {
            Card mapped = find(copy, original.getId());
            assertEquals(mapped.getNetPower(), 2);
            assertFalse(mapped.hasKeyword(forge.game.keyword.Keyword.TRAMPLE));
            assertEquals(original.getNetPower(), 5);
            assertTrue(original.hasKeyword(forge.game.keyword.Keyword.TRAMPLE));
        }
    }

    @Test
    public void temporaryTheftReturnsControlAndRemovesHasteAfterCopiedCleanup() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        Player opponent = source.getPlayers().get(1);
        addCards("Mountain", 3, owner);
        Card bear = addCard("Grizzly Bears", opponent);
        targetedSpell(source, "Act of Treason", owner, bear, 3);
        assertSame(bear.getController(), owner);
        assertTrue(bear.hasKeyword(forge.game.keyword.Keyword.HASTE));
        Game copy = snapshot(snapshot(source));
        Card mapped = find(copy, bear.getId());
        assertSame(mapped.getController(), copy.getPlayer(owner.getId()));
        nextTurn(copy);
        assertSame(mapped.getController(), copy.getPlayer(opponent.getId()));
        assertSame(mapped.getZone().getPlayer(), copy.getPlayer(opponent.getId()));
        assertFalse(mapped.hasKeyword(forge.game.keyword.Keyword.HASTE));
        assertSame(bear.getController(), owner);
        assertTrue(bear.hasKeyword(forge.game.keyword.Keyword.HASTE));
    }

    @Test
    public void changingControllersDoesNotRefundTheSameTurnsRootwallaActivation() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        Player opponent = source.getPlayers().get(1);
        addCards("Mountain", 3, owner);
        addCards("Forest", 2, owner);
        addCards("Forest", 2, opponent);
        Card rootwalla = addCard("Basking Rootwalla", opponent);
        SpellAbility original = activated(rootwalla, ApiType.Pump, opponent);
        pay(source, original, opponent, 2);
        source.getStack().resolveStack();
        targetedSpell(source, "Act of Treason", owner, rootwalla, 3);
        SpellAbility controlled = activated(rootwalla, ApiType.Pump, owner);
        assertEquals(controlled.getActivationsThisTurn(), 1);
        assertFalse(controlled.canPlay(), "The new controller has mana, but the object already used its limit");
        Game copy = snapshot(source);
        Card mapped = find(copy, rootwalla.getId());
        SpellAbility copied = activated(mapped, ApiType.Pump, copy.getPlayer(owner.getId()));
        assertEquals(copied.getActivationsThisTurn(), 1);
        assertFalse(copied.canPlay());
        nextTurn(copy);
        assertSame(mapped.getController(), copy.getPlayer(opponent.getId()));
        copied.setActivatingPlayer(copy.getPlayer(opponent.getId()));
        assertEquals(copied.getActivationsThisTurn(), 0);
        assertTrue(copied.canPlay());
        assertEquals(mapped.getNetPower(), 1);
        assertEquals(rootwalla.getNetPower(), 3);
        assertEquals(original.getActivationsThisTurn(), 1);
    }

    @Test
    public void sowerLeavesPlayCallbackReturnsControlOnlyInTheCopiedWorld() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        Player opponent = source.getPlayers().get(1);
        addCards("Island", 4, owner);
        addCard("Mountain", opponent);
        Card bear = addCard("Grizzly Bears", opponent);
        Card sower = cast(source, "Sower of Temptation", owner, 4);
        settle(source);
        assertSame(bear.getController(), owner);
        Game copy = snapshot(source);
        Card mappedSower = find(copy, sower.getId());
        targetedSpell(copy, "Lightning Bolt", copy.getPlayer(opponent.getId()), mappedSower, 1);
        assertEquals(find(copy, sower.getId()).getZone().getZoneType(), ZoneType.Graveyard);
        Card mappedBear = find(copy, bear.getId());
        assertSame(mappedBear.getController(), copy.getPlayer(opponent.getId()));
        assertSame(mappedBear.getZone().getPlayer(), copy.getPlayer(opponent.getId()));
        assertSame(bear.getController(), owner);
        assertEquals(sower.getZone().getZoneType(), ZoneType.Battlefield);
    }

    @Test
    public void shieldExpirationRetainsThePermanentCounter() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        addCard("Plains", owner);
        addCard("Forest", owner);
        Card bear = addCard("Grizzly Bears", owner);
        targetedSpell(source, "Take Up the Shield", owner, bear, 2);
        assertEquals(bear.getCounters(CounterEnumType.P1P1), 1);
        assertTrue(bear.hasKeyword(forge.game.keyword.Keyword.INDESTRUCTIBLE));
        assertTrue(bear.hasKeyword(forge.game.keyword.Keyword.LIFELINK));
        Game copy = snapshot(source);
        Card mapped = find(copy, bear.getId());
        nextTurn(copy);
        assertEquals(mapped.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(mapped.getNetPower(), 3);
        assertFalse(mapped.hasKeyword(forge.game.keyword.Keyword.INDESTRUCTIBLE));
        assertFalse(mapped.hasKeyword(forge.game.keyword.Keyword.LIFELINK));
        assertTrue(bear.hasKeyword(forge.game.keyword.Keyword.INDESTRUCTIBLE));
        assertTrue(bear.hasKeyword(forge.game.keyword.Keyword.LIFELINK));
    }


    @Test
    public void playerShroudSurvivesCopyAndExpiresAtTheCopiedCleanup() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        addCard("Plains", owner);
        addCard("Forest", owner);
        cast(source, "Gilded Light", owner, 2);
        assertTrue(owner.hasKeyword(forge.game.keyword.Keyword.SHROUD));
        Game copy = snapshot(source);
        Player mapped = copy.getPlayer(owner.getId());
        assertTrue(mapped.hasKeyword(forge.game.keyword.Keyword.SHROUD), "Resolved player keyword is copied");
        nextTurn(copy);
        assertFalse(mapped.hasKeyword(forge.game.keyword.Keyword.SHROUD));
        assertTrue(owner.hasKeyword(forge.game.keyword.Keyword.SHROUD));
    }

    @Test
    public void endoskeletonBonusEndsWhenTheCopiedArtifactActuallyUntaps() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        addCards("Forest", 5, owner);
        addCard("Island", owner);
        Card bear = addCard("Grizzly Bears", owner);
        Card skeleton = cast(source, "Endoskeleton", owner, 2);
        SpellAbility pump = activated(skeleton, ApiType.Pump, owner);
        pump.getTargets().add(bear);
        pay(source, pump, owner, 2);
        source.getStack().resolveStack();
        assertTrue(skeleton.isTapped());
        assertEquals(bear.getNetToughness(), 5);
        Game copy = snapshot(source);
        Card mapped = find(copy, skeleton.getId());
        cast(copy, "Dramatic Reversal", copy.getPlayer(owner.getId()), 2);
        assertFalse(mapped.isTapped());
        assertEquals(find(copy, bear.getId()).getNetToughness(), 2);
        assertTrue(skeleton.isTapped());
        assertEquals(bear.getNetToughness(), 5);
        assertEquals(skeleton.getGainControlTargets().size(), 1);
        assertTrue(mapped.getGainControlTargets().isEmpty());
    }

    @Test
    public void bandingEndsAtTheCopiedCombatBoundary() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        addCards("Forest", 2, owner);
        Card ram = cast(source, "Battering Ram", owner, 2);
        assertTrue(source.getPhaseHandler().devAdvanceToPhase(
                PhaseType.COMBAT_DECLARE_ATTACKERS, () -> settle(source)));
        settle(source);
        assertTrue(ram.hasKeyword(forge.game.keyword.Keyword.BANDING));
        Game copy = snapshot(source);
        Card mapped = find(copy, ram.getId());
        assertTrue(mapped.hasKeyword(forge.game.keyword.Keyword.BANDING));
        assertTrue(copy.getPhaseHandler().devAdvanceToPhase(
                PhaseType.MAIN2, () -> settle(copy)));
        settle(copy);
        assertFalse(mapped.hasKeyword(forge.game.keyword.Keyword.BANDING));
        assertTrue(ram.hasKeyword(forge.game.keyword.Keyword.BANDING));
        assertEquals(source.getPhaseHandler().getPhase(), PhaseType.COMBAT_DECLARE_ATTACKERS);
    }

    /** Synthetic scheduler contract: mutable callback state must retain aliases and new-world cards. */
    private static final class RecordingCommand implements forge.game.SnapshotCommand {
        private static final long serialVersionUID = 1L;
        private final Card marker;
        private final int base;
        private int executions;

        private RecordingCommand(Card marker, int base, int executions) {
            this.marker = marker;
            this.base = base;
            this.executions = executions;
        }

        @Override
        public void run() {
            marker.setChosenNumber(marker.getChosenNumber() * 10 + base + ++executions);
        }

        @Override
        public forge.game.SnapshotCommand copyForSnapshot(
                forge.game.spellability.SpellAbilitySnapshot context) {
            return new RecordingCommand(context.map(marker), base, executions);
        }
    }

    @Test
    public void nativeSchedulerQueuesKeepOrderAliasesAndMappedPlayers() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        Card marker = addCard("Grizzly Bears", owner);
        marker.setChosenNumber(0);
        forge.game.phase.Phase phase = source.getUpkeep();
        RecordingCommand shared = new RecordingCommand(marker, 0, 0);
        phase.addAt(shared);
        phase.addAt(new RecordingCommand(marker, 2, 0));
        phase.addUntil(shared);
        phase.addUntil(owner, shared);
        phase.addUntilEnd(owner, new RecordingCommand(marker, 4, 0));
        phase.registerUntilEnd(owner, new RecordingCommand(marker, 6, 0));
        Game copy = snapshot(source);
        Player mappedOwner = copy.getPlayer(owner.getId());
        Card mappedMarker = find(copy, marker.getId());
        forge.game.phase.Phase mapped = copy.getUpkeep();
        mapped.executeAt();
        assertEquals(mappedMarker.getChosenNumber(), 13);
        mapped.executeUntil();
        assertEquals(mappedMarker.getChosenNumber(), 132);
        mapped.executeUntil(mappedOwner);
        assertEquals(mappedMarker.getChosenNumber(), 1323);
        mapped.executeUntilEndOfPhase(mappedOwner);
        assertEquals(mappedMarker.getChosenNumber(), 13235);
        mapped.registerUntilEndCommand(mappedOwner);
        mapped.executeUntilEndOfPhase(mappedOwner);
        assertEquals(mappedMarker.getChosenNumber(), 132357);
        mapped.executeAt();
        mapped.executeUntil();
        mapped.executeUntil(mappedOwner);
        mapped.registerUntilEndCommand(mappedOwner);
        mapped.executeUntilEndOfPhase(mappedOwner);
        assertEquals(mappedMarker.getChosenNumber(), 132357, "Drained queues do not replay");
        assertEquals(marker.getChosenNumber(), 0);
        phase.executeAt();
        assertEquals(marker.getChosenNumber(), 13, "Source callbacks have independent mutable state");
        assertEquals(mappedMarker.getChosenNumber(), 132357);
    }

    @Test
    public void unconvertedCallbackKindsAreReportedAndNeverSharedOrExecutedByCopying() {
        Game source = pod();
        java.util.concurrent.atomic.AtomicInteger executions = new java.util.concurrent.atomic.AtomicInteger();
        source.getEndOfTurn().addUntil((forge.GameCommand) executions::incrementAndGet);
        GameCopier copier = new GameCopier(source).withStack(true);
        Game copy = copier.makeCopy();
        assertEquals(executions.get(), 0);
        assertEquals(copier.getUnmappedSnapshotCommands().size(), 1);
        assertTrue(copier.getUnmappedSnapshotCommands().get(0).contains(".until:"));
        nextTurn(copy);
        assertEquals(executions.get(), 0, "A source closure cannot run in the copied world");
        nextTurn(source);
        assertEquals(executions.get(), 1);
    }


    @Test
    public void paidPrelateKeepsItsChosenNumberAndSpellRestriction() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        // A fixed legal player choice isolates rules preservation from the AI's choice policy.
        owner.dangerouslySetController(new PlayerControllerAi(
                source, owner, owner.getRegisteredPlayer().getPlayer()) {
            @Override
            public int chooseNumber(SpellAbility sa, String title, int min, int max) {
                if ("Sanctum Prelate".equals(sa.getHostCard().getName())) {
                    assertTrue(min <= 2 && 2 <= max);
                    return 2;
                }
                return super.chooseNumber(sa, title, min, max);
            }
        });
        addCards("Plains", 3, owner);
        addCards("Forest", 2, owner);
        addCard("Island", owner);
        Card prelate = cast(source, "Sanctum Prelate", owner, 3);
        assertEquals(prelate.getChosenNumber(), Integer.valueOf(2));
        Card blocked = addCardToZone("Rampant Growth", owner, ZoneType.Hand);
        Card creature = addCardToZone("Grizzly Bears", owner, ZoneType.Hand);
        Card otherValue = addCardToZone("Ponder", owner, ZoneType.Hand);
        SpellAbility denied = blocked.getFirstSpellAbility();
        denied.setActivatingPlayer(owner);
        assertFalse(denied.checkRestrictions(owner));
        assertFalse(ComputerUtil.playStack(denied, owner, source));
        assertEquals(blocked.getZone().getZoneType(), ZoneType.Hand);
        Game copy = snapshot(source);
        Card mapped = find(copy, prelate.getId());
        assertEquals(mapped.getChosenNumber(), Integer.valueOf(2));
        assertEquals(mapped.getView().getChosenNumber(), "2");
        Player mappedOwner = copy.getPlayer(owner.getId());
        SpellAbility copiedDenied = find(copy, blocked.getId()).getFirstSpellAbility();
        copiedDenied.setActivatingPlayer(mappedOwner);
        assertFalse(copiedDenied.checkRestrictions(mappedOwner));
        assertFalse(ComputerUtil.playStack(copiedDenied, mappedOwner, copy));
        assertEquals(find(copy, blocked.getId()).getZone().getZoneType(), ZoneType.Hand);
        SpellAbility copiedCreature = find(copy, creature.getId()).getFirstSpellAbility();
        copiedCreature.setActivatingPlayer(mappedOwner);
        assertTrue(copiedCreature.canPlay(), "Prelate excludes creatures");
        SpellAbility copiedOtherValue = find(copy, otherValue.getId()).getFirstSpellAbility();
        copiedOtherValue.setActivatingPlayer(mappedOwner);
        assertTrue(copiedOtherValue.canPlay(), "A different noncreature mana value remains legal");
        pay(copy, copiedOtherValue, mappedOwner, 1);
        copy.getStack().resolveStack();
        settle(copy);
        assertEquals(find(copy, otherValue.getId()).getZone().getZoneType(), ZoneType.Graveyard);
        pay(copy, copiedCreature, mappedOwner, 2);
        copy.getStack().resolveStack();
        settle(copy);
        assertEquals(find(copy, creature.getId()).getZone().getZoneType(), ZoneType.Battlefield);
        assertEquals(otherValue.getZone().getZoneType(), ZoneType.Hand);
        assertEquals(creature.getZone().getZoneType(), ZoneType.Hand);
        assertEquals(prelate.getChosenNumber(), Integer.valueOf(2));
    }

    @Test
    public void secretChosenNumberKeepsItsOriginalVisibility() {
        Game source = pod();
        Player owner = source.getPlayers().get(0);
        Card marker = addCard("Grizzly Bears", owner);
        marker.setChosenNumber(7, true);
        String visible = marker.getView().getChosenNumber();
        Game copy = snapshot(source);
        Card mapped = find(copy, marker.getId());
        assertEquals(mapped.getChosenNumber(), Integer.valueOf(7));
        assertEquals(mapped.getView().getChosenNumber(), visible);
        assertEquals(marker.getChosenNumber(), Integer.valueOf(7));
    }

}
