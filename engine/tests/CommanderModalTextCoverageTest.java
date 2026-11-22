package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Composition witnesses: choices made through parent targeting and modal spell chains. */
public class CommanderModalTextCoverageTest extends AITest {
    @Test public void heuristicWhimChoosesTheLandWordMode() { verifyLand("Whim of Volrath", null, false); }
    @Test public void hybridWhimChoosesTheLandWordMode() { verifyLand("Whim of Volrath", AIOption.USE_HYBRID_SIMULATION, false); }
    @Test public void fullWhimChoosesTheLandWordMode() { verifyLand("Whim of Volrath", AIOption.USE_FULL_SIMULATION, false); }
    @Test public void heuristicMindBendChoosesTheLandWordMode() { verifyLand("Mind Bend", null, false); }
    @Test public void hybridMindBendChoosesTheLandWordMode() { verifyLand("Mind Bend", AIOption.USE_HYBRID_SIMULATION, false); }
    @Test public void fullMindBendChoosesTheLandWordMode() { verifyLand("Mind Bend", AIOption.USE_FULL_SIMULATION, false); }
    @Test public void heuristicSpectralShiftChoosesTheLandWordMode() { verifyLand("Spectral Shift", null, false); }
    @Test public void hybridSpectralShiftChoosesTheLandWordMode() { verifyLand("Spectral Shift", AIOption.USE_HYBRID_SIMULATION, false); }
    @Test public void fullSpectralShiftChoosesTheLandWordMode() { verifyLand("Spectral Shift", AIOption.USE_FULL_SIMULATION, false); }
    @Test public void copiedFullGenericChoiceKeepsTheLandWordMode() { verifyLand("Mind Bend", AIOption.USE_FULL_SIMULATION, true); }
    @Test public void copiedFullTemporaryChoiceKeepsTheLandWordMode() { verifyLand("Whim of Volrath", AIOption.USE_FULL_SIMULATION, true); }
    @Test public void copiedHeuristicGenericChoiceKeepsTheLandWordMode() { verifyLand("Mind Bend", null, true); }
    @Test public void copiedHybridCharmKeepsTheLandWordMode() { verifyLand("Spectral Shift", AIOption.USE_HYBRID_SIMULATION, true); }

    @Test public void heuristicDoesNotSpendWhimWithoutAnOutcomeBenefit() { verifyNoBenefit(null); }
    @Test public void hybridDoesNotSpendWhimWithoutAnOutcomeBenefit() { verifyNoBenefit(AIOption.USE_HYBRID_SIMULATION); }

    private void verifyNoBenefit(AIOption mode) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("no-benefit" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Buyback reservation control"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        for (Player player : game.getPlayers()) for (int j = 0; j < 8; j++)
            addCardToZone("Forest", player, ZoneType.Library);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        Card spell = addCardToZone("Whim of Volrath", actor, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        AiController ai = ((PlayerControllerAi) actor.getController()).getAi();
        ai.setUseSimulation(mode);
        List<SpellAbility> actions = ai.chooseSpellAbilityToPlay();
        assertTrue(actions == null || actions.stream().noneMatch(action -> action.getHostCard().getId() == spell.getId()),
                "Bypassing the wait-for-buyback preference must still require a beneficial paid outcome");
        assertTrue(spell.isInZone(ZoneType.Hand));
        assertEquals(actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 0L);
    }

    private void verifyLand(String name, AIOption mode, boolean copiedPending) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("land-word" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Modal land-word changes"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, actor, 2);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++)
            addCardToZone("Forest", player, ZoneType.Library);
        for (int i = 0; i < 2; i++) {
            addCardToZone("Island", actor, ZoneType.Battlefield);
            addCardToZone("Island", enemy, ZoneType.Battlefield);
        }
        Card attacker = addCardToZone("Crusading Knight", actor, ZoneType.Battlefield);
        Card blocker = addCardToZone("Grizzly Bears", enemy, ZoneType.Battlefield);
        attacker.setTapped(true);
        attacker.setSickness(false);
        Combat combat = new Combat(actor);
        combat.addAttacker(attacker, enemy);
        combat.addBlocker(attacker, blocker);
        combat.getBandOfAttacker(attacker).setBlocked(true);
        attacker.addBlockedByThisTurn(CardCopyService.getLKICopy(blocker));
        blocker.addBlockedThisTurn(CardCopyService.getLKICopy(attacker));
        combat.orderBlockersForDamageAssignment(attacker, combat.getBlockers(attacker));
        combat.orderAttackersForDamageAssignment(blocker);
        game.getPhaseHandler().setCombat(combat);
        addCardToZone("Omniscience", actor, ZoneType.Hand);
        addCardToZone("Enter the Infinite", actor, ZoneType.Hand);
        Card host = addCardToZone(name, actor, ZoneType.Hand);
        int expectedCost = host.getCMC();
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        assertEquals(attacker.getNetPower(), 2);
        AiController ai = ((PlayerControllerAi) actor.getController()).getAi();
        ai.setUseSimulation(mode);
        List<SpellAbility> actions = ai.chooseSpellAbilityToPlay();
        assertTrue(actions != null && !actions.isEmpty(), "Expected a paid land-word action");
        SpellAbility selected = actions.stream().filter(action -> action.getHostCard().getId() == host.getId())
                .findFirst().orElseThrow();
        assertTrue(ComputerUtil.playStack(selected, actor, game));
        assertEquals(actor.getLandsInPlay().stream().filter(Card::isTapped).count(), (long) expectedCost);
        if (copiedPending) {
            forge.ai.simulation.GameCopier copier = new forge.ai.simulation.GameCopier(game).withStack(true);
            Game copy = copier.makeCopy();
            assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
            settle(copy);
            assertEquals(copier.find(attacker).getNetPower(), 4);
            assertEquals(attacker.getNetPower(), 2, "A copied resolution must leave the original pending");
            assertEquals(game.getStack().size(), 1);
        }
        settle(game);
        assertEquals(attacker.getNetPower(), 4, "The type mode must replace Swamp with Island");
        assertEquals(attacker.getNetToughness(), 4);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE, actor, false, 2);
        assertTrue(combat.assignCombatDamage(false));
        combat.dealAssignedDamage();
        game.getAction().checkStateEffects(true);
        assertTrue(attacker.isInZone(ZoneType.Battlefield), "The paid type change must save the attacker");
        assertTrue(blocker.isInZone(ZoneType.Graveyard));
    }

    private void settle(Game game) {
        for(int i=0;i<20;i++) {
            game.getAction().checkStateEffects(true);game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();game.getStack().addAllTriggeredAbilitiesToStack();
            if(game.getStack().isEmpty())return;game.getStack().resolveStack();
        }
        throw new AssertionError("Native composed stack did not settle");
    }
}
