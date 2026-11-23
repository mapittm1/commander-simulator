package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Separate missed native declarations from the rules outcomes of an explicitly selected attack. */
public class CommanderMultiplayerAttackSafetyTest extends AITest {
    private Game board(String kind, boolean copied) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("opportunity-" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Multiplayer attack opportunity"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        actor.setLife(80, null);
        for (Player player : game.getPlayers()) {
            player.setLife(player == actor ? 80 : 40, null);
            for (int index = 0; index < 12; index++) addCardToZone("Forest", player, ZoneType.Library);
            for (int index = 0; index < 5; index++) addCardToZone("Forest", player, ZoneType.Battlefield);
        }
        String name = kind.equals("forced") ? "Juggernaut" : kind.equals("group") ? "Mogg Flunkies" : kind.equals("bears") ? "Grizzly Bears" : kind.equals("flying") ? "Serra Angel"
                : kind.equals("defender") ? "Wall of Wood" : "Goreclaw, Terror of Qal Sisma";
        Card creature = addCardToZone(name, actor, ZoneType.Battlefield);
        creature.setSickness(kind.equals("sick"));
        if (kind.equals("group")) addCardToZone("Mogg Flunkies", actor, ZoneType.Battlefield).setSickness(false);
        if (kind.equals("guard")) actor.setLife(7, null);
        if (kind.equals("tax")) {
            addCardToZone("Propaganda", game.getPlayers().get(1), ZoneType.Battlefield);
            for (Card land : actor.getCardsIn(ZoneType.Battlefield)) if (land.isLand()) land.setTapped(true);
        }
        if (kind.equals("peace")) addCardToZone("Peacekeeper", game.getPlayers().get(1), ZoneType.Battlefield);
        if (kind.equals("allblocked")) addCardToZone("Colossal Dreadmaw", game.getPlayers().get(1), ZoneType.Battlefield);
        creature.setTapped(kind.equals("tapped"));
        if (!kind.equals("open")) {
            for (int seat = 2; seat < 4; seat++) {
                addCardToZone(kind.equals("guard") ? "Craw Wurm" : "Colossal Dreadmaw", game.getPlayers().get(seat), ZoneType.Battlefield);
            }
        }
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, actor, 5);
        game.copyLastState();
        if (copied) game = new GameCopier(game).withStack(true).makeCopy(null, actor);
        return game;
    }

    private Player seat(Game game, int index) {
        return game.getPlayers().stream().filter(player -> player.getName().equals("opportunity-" + index)).findFirst().orElseThrow();
    }

    private void actualNativeDecision(int mode, boolean copied, String kind) {
        Game game = board(kind, copied);
        Player actor = seat(game, 0);
        Player open = seat(game, 1);
        Card creature = actor.getCreaturesInPlay().get(0);
        PlayerControllerAi controller = (PlayerControllerAi) actor.getController();
        controller.getAi().setUseSimulation(mode == 0 ? null : mode == 1 ? AIOption.USE_HYBRID_SIMULATION : AIOption.USE_FULL_SIMULATION);
        Combat combat = new Combat(actor);
        game.getPhaseHandler().setCombat(combat);
        controller.declareAttackers(actor, combat);
        assertTrue(CombatUtil.validateAttackers(combat));
        assertEquals(actor.getLife(), kind.equals("guard") ? 7 : 80, "Planning cannot deal real damage");
        for (Player opponent : actor.getOpponents()) assertEquals(opponent.getLife(), 40);
        assertEquals(creature.isTapped(), kind.equals("tapped"), "Planning cannot tap an attacker");
        System.out.println("ATTACK_OPPORTUNITY mode=" + mode + " copied=" + copied + " kind=" + kind
                + " attackers=" + combat.getAttackers().size() + " defender="
                + (combat.isAttacking(creature) ? combat.getDefenderByAttacker(creature).getName() : "none"));
        if (kind.equals("forced")) {
            assertTrue(combat.isAttacking(creature), "Respect the mandatory attack");
        } else if (kind.equals("group")) {
            assertEquals(combat.getAttackers().size(), 2, "Consider legal group attacks");
            for (Card attacker : combat.getAttackers()) assertSame(combat.getDefenderByAttacker(attacker), open);
        } else {
            assertTrue(combat.getAttackers().isEmpty(), "Retain blockers against lethal crackback, respect taxes/restrictions, and avoid hopeless blocked attacks");
        }
    }
    private void nativeCounterattackOutcome(boolean copied, boolean attack) {
        Game game = board("guard", copied);
        Player actor = seat(game, 0);
        Card blocker = actor.getCreaturesInPlay().get(0);
        java.util.Map<Card, forge.game.GameEntity> ownAttack = new LinkedHashMap<>();
        if (attack) ownAttack.put(blocker, seat(game, 1));
        resolveCombat(game, actor, ownAttack, 5);
        for (int enemy = 2; enemy < 4 && !actor.hasLost(); enemy++) {
            Player opponent = seat(game, enemy);
            Card wurm = opponent.getCreaturesInPlay().get(0);
            wurm.setSickness(false);
            java.util.Map<Card, forge.game.GameEntity> retaliation = new LinkedHashMap<>();
            retaliation.put(wurm, actor);
            resolveCombat(game, opponent, retaliation, enemy + 4);
        }
        assertEquals(actor.hasLost(), attack, "Native sequential counterattacks distinguish holding the blocker from attacking");
        if (!attack) assertEquals(actor.getLife(), 1, "A legal block prevents one six-damage attack");
        System.out.println("COUNTERATTACK_OUTCOME copied=" + copied + " attack=" + attack
                + " lost=" + actor.hasLost() + " life=" + actor.getLife());
    }

    private void resolveCombat(Game game, Player attacker, java.util.Map<Card, forge.game.GameEntity> declaration, int turn) {
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, attacker, turn);
        Combat combat = new Combat(attacker);
        game.getPhaseHandler().setCombat(combat);
        game.copyLastState();
        for (java.util.Map.Entry<Card, forge.game.GameEntity> entry : declaration.entrySet()) {
            assertTrue(CombatUtil.canAttack(entry.getKey(), entry.getValue()));
        }
        ((PlayerControllerAi) attacker.getController()).getAi().setAttackOverride(declaration);
        game.getPhaseHandler().devDeclareAttackers();
        GameSimulator.resolveStack(game, attacker.getWeakestOpponent());
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2,
                () -> GameSimulator.resolveStack(game, attacker.getWeakestOpponent())));
        GameSimulator.resolveStack(game, attacker.getWeakestOpponent());
    }

    // Named entry points keep every fixed case unique in native evidence.
    @Test public void classicGuardOriginal() { actualNativeDecision(0, false, "guard"); }
    @Test public void classicTaxOriginal() { actualNativeDecision(0, false, "tax"); }
    @Test public void classicPeaceOriginal() { actualNativeDecision(0, false, "peace"); }
    @Test public void classicForcedOriginal() { actualNativeDecision(0, false, "forced"); }
    @Test public void classicGroupOriginal() { actualNativeDecision(0, false, "group"); }
    @Test public void classicAllblockedOriginal() { actualNativeDecision(0, false, "allblocked"); }
    @Test public void classicGuardCopied() { actualNativeDecision(0, true, "guard"); }
    @Test public void classicTaxCopied() { actualNativeDecision(0, true, "tax"); }
    @Test public void classicPeaceCopied() { actualNativeDecision(0, true, "peace"); }
    @Test public void classicForcedCopied() { actualNativeDecision(0, true, "forced"); }
    @Test public void classicGroupCopied() { actualNativeDecision(0, true, "group"); }
    @Test public void classicAllblockedCopied() { actualNativeDecision(0, true, "allblocked"); }
    @Test public void hybridGuardOriginal() { actualNativeDecision(1, false, "guard"); }
    @Test public void hybridTaxOriginal() { actualNativeDecision(1, false, "tax"); }
    @Test public void hybridPeaceOriginal() { actualNativeDecision(1, false, "peace"); }
    @Test public void hybridForcedOriginal() { actualNativeDecision(1, false, "forced"); }
    @Test public void hybridGroupOriginal() { actualNativeDecision(1, false, "group"); }
    @Test public void hybridAllblockedOriginal() { actualNativeDecision(1, false, "allblocked"); }
    @Test public void hybridGuardCopied() { actualNativeDecision(1, true, "guard"); }
    @Test public void hybridTaxCopied() { actualNativeDecision(1, true, "tax"); }
    @Test public void hybridPeaceCopied() { actualNativeDecision(1, true, "peace"); }
    @Test public void hybridForcedCopied() { actualNativeDecision(1, true, "forced"); }
    @Test public void hybridGroupCopied() { actualNativeDecision(1, true, "group"); }
    @Test public void hybridAllblockedCopied() { actualNativeDecision(1, true, "allblocked"); }
    @Test public void fullGuardOriginal() { actualNativeDecision(2, false, "guard"); }
    @Test public void fullTaxOriginal() { actualNativeDecision(2, false, "tax"); }
    @Test public void fullPeaceOriginal() { actualNativeDecision(2, false, "peace"); }
    @Test public void fullForcedOriginal() { actualNativeDecision(2, false, "forced"); }
    @Test public void fullGroupOriginal() { actualNativeDecision(2, false, "group"); }
    @Test public void fullAllblockedOriginal() { actualNativeDecision(2, false, "allblocked"); }
    @Test public void fullGuardCopied() { actualNativeDecision(2, true, "guard"); }
    @Test public void fullTaxCopied() { actualNativeDecision(2, true, "tax"); }
    @Test public void fullPeaceCopied() { actualNativeDecision(2, true, "peace"); }
    @Test public void fullForcedCopied() { actualNativeDecision(2, true, "forced"); }
    @Test public void fullGroupCopied() { actualNativeDecision(2, true, "group"); }
    @Test public void fullAllblockedCopied() { actualNativeDecision(2, true, "allblocked"); }
    @Test public void originalBlockerSurvivesCounterattacks() { nativeCounterattackOutcome(false, false); }
    @Test public void originalAttackLosesToCounterattacks() { nativeCounterattackOutcome(false, true); }
    @Test public void copiedBlockerSurvivesCounterattacks() { nativeCounterattackOutcome(true, false); }
    @Test public void copiedAttackLosesToCounterattacks() { nativeCounterattackOutcome(true, true); }

}
