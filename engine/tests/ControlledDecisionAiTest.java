package forge.ai;

import forge.ai.simulation.GameSimulator;
import forge.ai.simulation.GameStateEvaluator;
import forge.ai.simulation.GameStateEvaluator.Score;
import forge.ai.simulation.SimulationController;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.mana.Mana;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

/** Decision-policy probe; a controlled actor must not kill its master on an empty board. */
public class ControlledDecisionAiTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            players.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("decision" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Controlled decision"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (int seat = 0; seat < 4; seat++) {
            Player player = game.getPlayers().get(seat);
            for (int i = 0; i < 10; i++) {
                addCardToZone("Swamp", player, ZoneType.Battlefield);
                addCardToZone("Mountain", player, ZoneType.Battlefield);
                addCardToZone(seat == 1 ? "Shock" : "Forest", player, ZoneType.Library);
            }
        }
        for (Player player : game.getPlayers()) {
            for (int i = 0; i < 4; i++) addCardToZone("Island", player, ZoneType.Battlefield);
        }
        game.getPlayers().get(0).setLife(2, null);
        return game;
    }

    private void settle(Game game) {
        for (int guard = 0; guard < 40; guard++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Fixture did not settle");
    }

    private Game prepared(boolean controlled) {
        Game game = pod();
        Player master = game.getPlayers().get(0);
        Player actor = game.getPlayers().get(1);
        if (controlled) {
            Card fears = addCardToZone("Worst Fears", master, ZoneType.Hand);
            SpellAbility spell = fears.getFirstSpellAbility();
            spell.setActivatingPlayer(master);
            assertTrue(spell.canTarget(actor));
            spell.getTargets().add(actor);
            assertTrue(ComputerUtil.playStack(spell, master, game));
            assertEquals(game.getStack().peekAbility().getPayingMana().size(), 8);
            game.getStack().resolveStack();
            settle(game);
        }
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(
                PhaseType.MAIN1, () -> settle(game)));
        settle(game);
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2, () -> settle(game)));
        settle(game);
        assertEquals(actor.getControllingPlayer(), controlled ? master : null);
        assertEquals(actor.getCardsIn(ZoneType.Hand).size(), 1);
        assertEquals(actor.getCardsIn(ZoneType.Hand).get(0).getName(), "Shock");
        return game;
    }

    private void choice(Game game, boolean full, Player target, boolean protect) {
        Player actor = game.getPlayers().get(1);
        PlayerControllerAi controller = (PlayerControllerAi) actor.getController();
        controller.getAi().setUseSimulation(full ? AIOption.USE_FULL_SIMULATION : null);
        List<SpellAbility> actions = controller.chooseSpellAbilityToPlay();
        boolean lethal = false;
        String selected = "pass";
        if (actions != null && !actions.isEmpty()) {
            SpellAbility action = actions.get(0);
            selected = action.getHostCard().getName() + " " + action.getTargets();
            assertSame(action.getActivatingPlayer(), actor);
            if (action.getHostCard().getName().equals("Shock")) {
                for (Player player : action.getTargets().getTargetPlayers()) {
                    if (player == target) lethal = true;
                }
            }
        }
        System.out.println("CONTROL_DECISION protect=" + protect + " full=" + full
                + " choice=" + selected + " lethalToBeneficiary=" + lethal);
        if (protect) assertFalse(lethal, "Avoid an immediate loss for the decision beneficiary");
        else assertTrue(lethal, "Ordinary actor should take its available lethal damage");
    }

    private void decision(boolean controlled, boolean full) {
        Game game = prepared(controlled);
        choice(game, full, game.getPlayers().get(0), controlled);
    }

    private int tapped(Player player) {
        return (int) player.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isTapped).count();
    }

    private void outcome(int constructor, boolean killMaster) {
        Game game = prepared(true);
        Player master = game.getPlayers().get(0);
        Player actor = game.getPlayers().get(1);
        Card shock = actor.getCardsIn(ZoneType.Hand).get(0);
        SpellAbility action = shock.getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        Player target = game.getPlayers().get(killMaster ? 0 : 2);
        assertTrue(action.canTarget(target));
        action.getTargets().add(target);
        assertTrue(action.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(action, actor, false));
        GameSimulator simulator;
        if (constructor == 0) {
            Score initial = new GameStateEvaluator().getScoreForGameState(game, master);
            simulator = new GameSimulator(new SimulationController(initial, 0), game, actor, null);
        } else if (constructor == 1) {
            simulator = new GameSimulator(game, actor);
        } else {
            simulator = new GameSimulator(game, actor, true);
        }
        int originalMasterTaps = tapped(master);
        int originalActorTaps = tapped(actor);
        Score actual = simulator.simulateSpellAbility(action);
        Game copied = simulator.getSimulatedGameState();
        Player copiedMaster = copied.getRegisteredPlayers().get(0);
        Player copiedActor = copied.getRegisteredPlayers().get(1);
        assertSame(simulator.getSimulatedAiPlayer(), copiedActor);
        assertEquals(tapped(copiedActor), originalActorTaps + 1, "Actor pays the red mana");
        assertEquals(tapped(actor), originalActorTaps, "Source payment is unchanged");
        assertEquals(master.getLife(), 2, "Source life is unchanged");
        assertSame(actor.getControllingPlayer(), master);
        assertTrue(copiedActor.getCardsIn(ZoneType.Graveyard).stream()
                .anyMatch(c -> c.getId() == shock.getId()));
        SpellAbility cast = copied.getStack().getSpellsCastThisTurn().stream()
                .filter(sa -> sa.getHostCard().getId() == shock.getId()).findFirst().orElseThrow();
        assertEquals(cast.getPayingMana().size(), 1);
        assertSame(cast.getActivatingPlayer(), copiedActor);
        for (Mana mana : cast.getPayingMana()) {
            assertSame(mana.getSourceCard().getController(), copiedActor);
        }
        if (killMaster) {
            assertTrue(copiedMaster.hasLost());
            assertNull(copiedActor.getControllingPlayer(), "Native departure ends control");
        } else {
            assertEquals(copiedMaster.getLife(), 2);
            assertEquals(tapped(copiedMaster), originalMasterTaps, "Controller does not pay actor's cost");
            assertEquals(copied.getRegisteredPlayers().get(2).getLife(), 38);
        }
        Score expected = new GameStateEvaluator().getScoreForGameState(copied, copiedMaster);
        assertTrue(actual.equals(expected), "Keep the captured beneficiary even after control ends: " + actual + " / " + expected);
    }

    private void originalScore() {
        Game game = prepared(true);
        Player master = game.getPlayers().get(0);
        Player actor = game.getPlayers().get(1);
        Score expected = new GameStateEvaluator().getScoreForGameState(game, master);
        GameSimulator simulator = new GameSimulator(new SimulationController(expected, 0), game, actor, null);
        assertTrue(simulator.getScoreForOrigGame().equals(expected), "Initial score belongs to the controller");
    }

    private void chain(boolean full) {
        Game game = prepared(true);
        Player master = game.getPlayers().get(0);
        Player beneficiary = game.getPlayers().get(2);
        master.setLife(40, null);
        beneficiary.setLife(2, null);
        master.addController(game.getNextTimestamp(), beneficiary);
        choice(game, full, beneficiary, true);
    }

    private void self(boolean full) {
        Game game = prepared(false);
        Player actor = game.getPlayers().get(1);
        actor.addController(game.getNextTimestamp(), actor);
        choice(game, full, game.getPlayers().get(0), false);
    }

    private void fullCandidate(String name) {
        Game game = prepared(false);
        Player actor = game.getPlayers().get(1);
        Card card = addCardToZone(name, actor, ZoneType.Hand);
        if (name.equals("Mindslaver")) {
            SpellAbility spell = card.getFirstSpellAbility();
            spell.setActivatingPlayer(actor);
            assertTrue(ComputerUtil.playStack(spell, actor, game));
            assertEquals(game.getStack().peekAbility().getPayingMana().size(), 6);
            game.getStack().resolveStack();
            settle(game);
        }
        final int identity = card.getId();
        List<SpellAbility> candidates = ((PlayerControllerAi) actor.getController()).getAi()
                .getSimulationPicker().getCandidateSpellsAndAbilities();
        assertTrue(candidates.stream().anyMatch(sa -> sa.getHostCard().getId() == identity
                && sa.getApi() == ApiType.ControlPlayer), "Full simulation admits the legal control action");
    }

    private void response(boolean controlled, boolean full) {
        response(controlled, full, false);
    }

    private void response(boolean controlled, boolean full, boolean uncounterable) {
        Game game = prepared(controlled);
        Player actor = game.getPlayers().get(1);
        Player master = controlled ? game.getPlayers().get(0) : actor;
        if (!controlled) {
            game.getPlayers().get(0).setLife(40, null);
            actor.setLife(2, null);
        }
        Player enemy = game.getPlayers().get(2);
        Card threat = addCardToZone(uncounterable ? "Volcanic Fallout" : "Shock", enemy, ZoneType.Hand);
        SpellAbility pending = threat.getFirstSpellAbility();
        pending.setActivatingPlayer(enemy);
        if (!uncounterable) {
            assertTrue(pending.canTarget(master));
            pending.getTargets().add(master);
        }
        assertTrue(ComputerUtil.playStack(pending, enemy, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), uncounterable ? 3 : 1);
        addCardToZone("Counterspell", actor, ZoneType.Hand);
        assertEquals(game.getStack().size(), 1);
        PlayerControllerAi agent = (PlayerControllerAi) actor.getController();
        if (!controlled && !full) {
            // The default profile deliberately randomizes cheap counters. Use the
            // shipped always-counter-damage preference for this positive control.
            AiProfileUtil.loadAllProfiles(forge.localinstance.properties.ForgeConstants.AI_PROFILE_DIR);
            ((LobbyPlayerAi) actor.getOriginalLobbyPlayer()).setAiProfile("Cautious");
            assertEquals(AiProfileUtil.getIntProperty(actor, AiProps.MIN_SPELL_CMC_TO_COUNTER), 2);
            assertTrue(AiProfileUtil.getBoolProperty(actor, AiProps.ALWAYS_COUNTER_DAMAGE_SPELLS));
        }
        agent.getAi().setUseSimulation(full ? AIOption.USE_FULL_SIMULATION : null);
        List<SpellAbility> choices = agent.chooseSpellAbilityToPlay();
        SpellAbility choice = choices == null || choices.isEmpty() ? null : choices.get(0);
        boolean counter = choice != null && choice.getApi() == ApiType.Counter;
        System.out.println("CONTROL_RESPONSE controlled=" + controlled + " full=" + full
                + " choice=" + (choice == null ? "pass" : choice.getHostCard().getName())
                + " target=" + (choice == null ? "none" : choice.getTargets()));
        assertEquals(counter, !uncounterable, "Counter a preventable loss; do not waste a counter on an uncounterable threat");
        assertEquals(game.getStack().size(), 1, "Planning cannot resolve the original pending spell");
        assertEquals(master.getLife(), 2);
        if (choice != null) {
            assertSame(choice.getActivatingPlayer(), actor);
            assertTrue(ComputerUtil.playStack(choice, actor, game));
            assertEquals(game.getStack().peekAbility().getPayingMana().size(), counter ? 2 : 1);
            game.getStack().resolveStack();
        }
        settle(game);
        assertEquals(master.hasLost(), uncounterable);
        if (!uncounterable) {
            assertEquals(master.getLife(), 2);
            if (controlled) assertSame(actor.getControllingPlayer(), master);
            assertTrue(enemy.getCardsIn(ZoneType.Graveyard).stream()
                    .anyMatch(c -> c.getId() == threat.getId()));
        }
    }

    private void abilityResponse(boolean full) {
        Game game = prepared(true);
        Player master = game.getPlayers().get(0);
        Player actor = game.getPlayers().get(1);
        Player enemy = game.getPlayers().get(2);
        for (int i = 0; i < 7; i++) addCardToZone("Forest", enemy, ZoneType.Graveyard);
        Card ring = addCardToZone("Barbarian Ring", enemy, ZoneType.Battlefield);
        SpellAbility ability = ring.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.DealDamage)
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(enemy);
        assertTrue(ability.canTarget(master));
        ability.getTargets().add(master);
        assertTrue(ability.canPlay(), "Threshold and native activation restrictions");
        assertTrue(ComputerUtil.playStack(ability, enemy, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 1);
        assertFalse(game.getStack().peekAbility().isSpell());
        assertTrue(enemy.getCardsIn(ZoneType.Graveyard).stream()
                .anyMatch(c -> c.getId() == ring.getId()), "Ring was actually sacrificed");
        addCardToZone("Stifle", actor, ZoneType.Hand);
        PlayerControllerAi agent = (PlayerControllerAi) actor.getController();
        agent.getAi().setUseSimulation(full ? AIOption.USE_FULL_SIMULATION : null);
        List<SpellAbility> choices = agent.chooseSpellAbilityToPlay();
        assertNotNull(choices);
        assertFalse(choices.isEmpty());
        SpellAbility chosen = choices.get(0);
        assertEquals(chosen.getHostCard().getName(), "Stifle");
        assertSame(chosen.getActivatingPlayer(), actor);
        assertEquals(game.getStack().size(), 1);
        assertEquals(master.getLife(), 2);
        assertTrue(ComputerUtil.playStack(chosen, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 1);
        game.getStack().resolveStack();
        settle(game);
        assertEquals(master.getLife(), 2);
        assertFalse(master.hasLost());
        assertSame(actor.getControllingPlayer(), master);
    }

    @Test public void heuristicOrdinary() { decision(false, false); }
    @Test public void heuristicControlled() { decision(true, false); }
    @Test public void fullOrdinary() { decision(false, true); }
    @Test public void fullControlled() { decision(true, true); }
    @Test public void outcome0Payment() { outcome(0, false); }
    @Test public void outcome0Release() { outcome(0, true); }
    @Test public void outcome1Payment() { outcome(1, false); }
    @Test public void outcome1Release() { outcome(1, true); }
    @Test public void outcome2Payment() { outcome(2, false); }
    @Test public void outcome2Release() { outcome(2, true); }
    @Test public void initialControllerScore() { originalScore(); }
    @Test public void chainHeuristic() { chain(false); }
    @Test public void selfHeuristic() { self(false); }
    @Test public void chainFull() { chain(true); }
    @Test public void selfFull() { self(true); }
    @Test public void fullCandidateWorst() { fullCandidate("Worst Fears"); }
    @Test public void fullCandidateMindslaver() { fullCandidate("Mindslaver"); }
    @Test public void fullCandidateSecret() { fullCandidate("Secret of Bloodbending"); }
    @Test public void responseOrdinaryHeuristic() { response(false, false); }
    @Test public void responseOrdinaryFull() { response(false, true); }
    @Test public void responseControlledHeuristic() { response(true, false); }
    @Test public void responseControlledFull() { response(true, true); }
    @Test public void uncounterableFull() { response(true, true, true); }
    @Test public void uncounterableHeuristic() { response(true, false, true); }
    @Test public void activatedResponseFull() { abilityResponse(true); }
    @Test public void activatedResponseHeuristic() { abilityResponse(false); }
}
