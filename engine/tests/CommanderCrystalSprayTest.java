package forge.ai;

import forge.ai.simulation.GameCopier;
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

/** Paid real-card choices, native card draws, and copied pending resolution. */
public class CommanderCrystalSprayTest extends AITest {
    @Test public void heuristicColorAndCantrip() { verify(false, null, false); }
    @Test public void hybridColorAndCantrip() { verify(false, AIOption.USE_HYBRID_SIMULATION, false); }
    @Test public void fullColorAndCantrip() { verify(false, AIOption.USE_FULL_SIMULATION, false); }
    @Test public void copiedFullColorAndCantrip() { verify(false, AIOption.USE_FULL_SIMULATION, true); }
    @Test public void heuristicLandAndCantrip() { verify(true, null, false); }
    @Test public void hybridLandAndCantrip() { verify(true, AIOption.USE_HYBRID_SIMULATION, false); }
    @Test public void fullLandAndCantrip() { verify(true, AIOption.USE_FULL_SIMULATION, false); }

    private void verify(boolean land, AIOption mode, boolean copiedPending) {
        Pod pod = combatPod(land);
        AiController ai = ((PlayerControllerAi) pod.actor.getController()).getAi();
        ai.setUseSimulation(mode);
        List<SpellAbility> actions = ai.chooseSpellAbilityToPlay();
        assertTrue(actions != null && actions.stream().anyMatch(action -> action.getHostCard().getId() == pod.spell.getId()),
                "Choose the beneficial paid text change in mode " + mode);
        SpellAbility selected = actions.stream().filter(action -> action.getHostCard().getId() == pod.spell.getId())
                .findFirst().orElseThrow();
        assertTrue(ComputerUtil.playStack(selected, pod.actor, pod.game));
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 3L);
        if (copiedPending) {
            GameCopier copier = new GameCopier(pod.game).withStack(true);
            Game copy = copier.makeCopy();
            assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
            settle(copy);
            assertEquals(copier.find(pod.attacker).getNetPower(), land ? 4 : 3);
            assertEquals(copy.getPlayer(pod.actor.getId()).getCardsIn(ZoneType.Library).size(), 7);
            assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 8);
            assertEquals(pod.attacker.getNetPower(), 2);
            assertEquals(pod.game.getStack().size(), 1);
        }
        settle(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 7);
        assertTrue(pod.actor.getCardsIn(ZoneType.Hand).stream()
                .anyMatch(card -> card.getId() == pod.top.getId()), "Draw the actual native library top");
        assertEquals(pod.attacker.getNetPower(), land ? 4 : 3);
        assertEquals(pod.attacker.getNetToughness(), land ? 4 : 2);
        pod.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE, pod.actor, false, 2);
        assertTrue(pod.combat.assignCombatDamage(false));
        pod.combat.dealAssignedDamage();
        pod.game.getAction().checkStateEffects(true);
        assertTrue(pod.game.getCardState(pod.attacker).isInZone(ZoneType.Battlefield),
                "The text change must save the attacker through native combat damage");
        assertTrue(pod.game.getCardState(pod.blocker).isInZone(ZoneType.Graveyard));
    }

    private Pod combatPod(boolean land) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("crystal-combat-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Crystal Spray paid combat outcomes"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++)
            addCardToZone("Forest", player, ZoneType.Library);
        for (int i = 0; i < 3; i++) addCardToZone("Island", actor, ZoneType.Battlefield);
        if (land) {
            for (int i = 0; i < 2; i++) addCardToZone("Island", enemy, ZoneType.Battlefield);
        } else {
            addCardToZone("Honor of the Pure", actor, ZoneType.Battlefield);
        }
        Card attacker = addCardToZone(land ? "Crusading Knight" : "Coral Merfolk", actor, ZoneType.Battlefield);
        Card blocker = addCardToZone(land ? "Grizzly Bears" : "Memnite", enemy, ZoneType.Battlefield);
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
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, actor, 2);
        game.getPhaseHandler().setCombat(combat);
        // Uncastable blue spells prevent a coincidental empty-hand color choice.
        addCardToZone("Omniscience", actor, ZoneType.Hand);
        addCardToZone("Enter the Infinite", actor, ZoneType.Hand);
        Card spell = addCardToZone("Crystal Spray", actor, ZoneType.Hand);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        assertEquals(attacker.getNetPower(), 2);
        return new Pod(game, actor, spell, attacker, blocker, combat,
                actor.getCardsIn(ZoneType.Library).get(0));
    }

    private void settle(Game game) {
        for (int i = 0; i < 32; i++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        throw new AssertionError("Crystal Spray combat stack did not settle");
    }

    private record Pod(Game game, Player actor, Card spell, Card attacker, Card blocker,
                       Combat combat, Card top) { }
}
