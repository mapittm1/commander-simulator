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
public class CommanderMultiplayerAttackOpportunityTest extends AITest {
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
        String name = kind.equals("bears") ? "Grizzly Bears" : kind.equals("flying") ? "Serra Angel"
                : kind.equals("defender") ? "Wall of Wood" : "Goreclaw, Terror of Qal Sisma";
        Card creature = addCardToZone(name, actor, ZoneType.Battlefield);
        creature.setSickness(kind.equals("sick"));
        creature.setTapped(kind.equals("tapped"));
        if (!kind.equals("open")) {
            for (int seat = 2; seat < 4; seat++) {
                addCardToZone("Colossal Dreadmaw", game.getPlayers().get(seat), ZoneType.Battlefield);
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
        assertEquals(actor.getLife(), 80, "Planning cannot deal real damage");
        for (Player opponent : actor.getOpponents()) assertEquals(opponent.getLife(), 40);
        assertEquals(creature.isTapped(), kind.equals("tapped"), "Planning cannot tap an attacker");
        System.out.println("ATTACK_OPPORTUNITY mode=" + mode + " copied=" + copied + " kind=" + kind
                + " attackers=" + combat.getAttackers().size() + " defender="
                + (combat.isAttacking(creature) ? combat.getDefenderByAttacker(creature).getName() : "none"));
        if (kind.equals("sick") || kind.equals("tapped") || kind.equals("defender")) {
            assertTrue(combat.getAttackers().isEmpty(), "An unavailable creature must stay out of combat");
        } else if (kind.equals("flying")) {
            assertTrue(combat.isAttacking(creature), "Flying avoids the two ground blockers");
        } else {
            assertTrue(combat.isAttacking(creature), "Reconsider an open opponent when the preferred opponents deter all attacks");
            if (!kind.equals("open")) {
                assertSame(combat.getDefenderByAttacker(creature), open, "Use the undefended opponent for the ground attack");
            }
        }
    }

    private void explicitNativeCombatOutcome(String kind, boolean copied, boolean attack) {
        Game game = board(kind, copied);
        Player actor = seat(game, 0);
        Player open = seat(game, 1);
        Card creature = actor.getCreaturesInPlay().get(0);
        Combat combat = new Combat(actor);
        game.getPhaseHandler().setCombat(combat);
        LinkedHashMap<Card, forge.game.GameEntity> selection = new LinkedHashMap<>();
        if (attack) {
            assertTrue(CombatUtil.canAttack(creature, open));
            selection.put(creature, open);
        }
        ((PlayerControllerAi) actor.getController()).getAi().setAttackOverride(selection);
        game.getPhaseHandler().devDeclareAttackers();
        GameSimulator.resolveStack(game, actor.getWeakestOpponent());
        if (attack && kind.equals("goreclaw")) {
            assertEquals(creature.getNetPower(), 5, "The real attack trigger resolves before combat damage");
            assertTrue(creature.hasKeyword("Trample"));
        }
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2,
                () -> GameSimulator.resolveStack(game, actor.getWeakestOpponent())));
        GameSimulator.resolveStack(game, actor.getWeakestOpponent());
        int damage = attack ? (kind.equals("goreclaw") ? 5 : 2) : 0;
        assertEquals(open.getLife(), 40 - damage, "Real native unblocked combat damage");
        assertEquals(actor.getLife(), 80);
        assertEquals(seat(game, 2).getLife(), 40);
        assertEquals(seat(game, 3).getLife(), 40);
        assertTrue(creature.isInZone(ZoneType.Battlefield));
        assertEquals(creature.isTapped(), attack);
        System.out.println("ATTACK_OUTCOME kind=" + kind + " copied=" + copied + " attack=" + attack + " damage=" + damage);
    }
    // Named entry points keep every fixed case unique in native evidence.
    @Test public void classicGoreclawOriginal() { actualNativeDecision(0, false, "goreclaw"); }
    @Test public void classicBearsOriginal() { actualNativeDecision(0, false, "bears"); }
    @Test public void classicFlyingOriginal() { actualNativeDecision(0, false, "flying"); }
    @Test public void classicOpenOriginal() { actualNativeDecision(0, false, "open"); }
    @Test public void classicSickOriginal() { actualNativeDecision(0, false, "sick"); }
    @Test public void classicTappedOriginal() { actualNativeDecision(0, false, "tapped"); }
    @Test public void classicDefenderOriginal() { actualNativeDecision(0, false, "defender"); }
    @Test public void classicGoreclawCopied() { actualNativeDecision(0, true, "goreclaw"); }
    @Test public void classicBearsCopied() { actualNativeDecision(0, true, "bears"); }
    @Test public void classicFlyingCopied() { actualNativeDecision(0, true, "flying"); }
    @Test public void classicOpenCopied() { actualNativeDecision(0, true, "open"); }
    @Test public void classicSickCopied() { actualNativeDecision(0, true, "sick"); }
    @Test public void classicTappedCopied() { actualNativeDecision(0, true, "tapped"); }
    @Test public void classicDefenderCopied() { actualNativeDecision(0, true, "defender"); }
    @Test public void hybridGoreclawOriginal() { actualNativeDecision(1, false, "goreclaw"); }
    @Test public void hybridBearsOriginal() { actualNativeDecision(1, false, "bears"); }
    @Test public void hybridFlyingOriginal() { actualNativeDecision(1, false, "flying"); }
    @Test public void hybridOpenOriginal() { actualNativeDecision(1, false, "open"); }
    @Test public void hybridSickOriginal() { actualNativeDecision(1, false, "sick"); }
    @Test public void hybridTappedOriginal() { actualNativeDecision(1, false, "tapped"); }
    @Test public void hybridDefenderOriginal() { actualNativeDecision(1, false, "defender"); }
    @Test public void hybridGoreclawCopied() { actualNativeDecision(1, true, "goreclaw"); }
    @Test public void hybridBearsCopied() { actualNativeDecision(1, true, "bears"); }
    @Test public void hybridFlyingCopied() { actualNativeDecision(1, true, "flying"); }
    @Test public void hybridOpenCopied() { actualNativeDecision(1, true, "open"); }
    @Test public void hybridSickCopied() { actualNativeDecision(1, true, "sick"); }
    @Test public void hybridTappedCopied() { actualNativeDecision(1, true, "tapped"); }
    @Test public void hybridDefenderCopied() { actualNativeDecision(1, true, "defender"); }
    @Test public void fullGoreclawOriginal() { actualNativeDecision(2, false, "goreclaw"); }
    @Test public void fullBearsOriginal() { actualNativeDecision(2, false, "bears"); }
    @Test public void fullFlyingOriginal() { actualNativeDecision(2, false, "flying"); }
    @Test public void fullOpenOriginal() { actualNativeDecision(2, false, "open"); }
    @Test public void fullSickOriginal() { actualNativeDecision(2, false, "sick"); }
    @Test public void fullTappedOriginal() { actualNativeDecision(2, false, "tapped"); }
    @Test public void fullDefenderOriginal() { actualNativeDecision(2, false, "defender"); }
    @Test public void fullGoreclawCopied() { actualNativeDecision(2, true, "goreclaw"); }
    @Test public void fullBearsCopied() { actualNativeDecision(2, true, "bears"); }
    @Test public void fullFlyingCopied() { actualNativeDecision(2, true, "flying"); }
    @Test public void fullOpenCopied() { actualNativeDecision(2, true, "open"); }
    @Test public void fullSickCopied() { actualNativeDecision(2, true, "sick"); }
    @Test public void fullTappedCopied() { actualNativeDecision(2, true, "tapped"); }
    @Test public void fullDefenderCopied() { actualNativeDecision(2, true, "defender"); }
    @Test public void goreclawOriginalHolding() { explicitNativeCombatOutcome("goreclaw", false, false); }
    @Test public void goreclawOriginalAttacking() { explicitNativeCombatOutcome("goreclaw", false, true); }
    @Test public void goreclawCopiedHolding() { explicitNativeCombatOutcome("goreclaw", true, false); }
    @Test public void goreclawCopiedAttacking() { explicitNativeCombatOutcome("goreclaw", true, true); }
    @Test public void bearsOriginalHolding() { explicitNativeCombatOutcome("bears", false, false); }
    @Test public void bearsOriginalAttacking() { explicitNativeCombatOutcome("bears", false, true); }
    @Test public void bearsCopiedHolding() { explicitNativeCombatOutcome("bears", true, false); }
    @Test public void bearsCopiedAttacking() { explicitNativeCombatOutcome("bears", true, true); }

}
