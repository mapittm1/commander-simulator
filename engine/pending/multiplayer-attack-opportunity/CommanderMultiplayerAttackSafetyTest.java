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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Separate missed native declarations from the rules outcomes of an explicitly selected attack. */
public class CommanderMultiplayerAttackSafetyTest extends AITest {
    @DataProvider(name = "decisions")
    public Object[][] decisions() {
        List<Object[]> cases = new ArrayList<>();
        for (int mode = 0; mode < 3; mode++) {
            for (boolean copied : new boolean[] {false, true}) {
                for (String kind : new String[] {"guard", "tax", "peace", "forced", "group", "allblocked"}) {
                    cases.add(new Object[] {mode, copied, kind});
                }
            }
        }
        return cases.toArray(new Object[0][]);
    }

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

    @Test(dataProvider = "decisions")
    public void actualNativeDecision(int mode, boolean copied, String kind) {
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
    @DataProvider(name = "counterattacks")
    public Object[][] counterattacks() {
        return new Object[][] {{false, false}, {false, true}, {true, false}, {true, true}};
    }

    @Test(dataProvider = "counterattacks")
    public void nativeCounterattackOutcome(boolean copied, boolean attack) {
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

}
