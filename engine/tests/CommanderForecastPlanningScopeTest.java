package forge.ai;

import forge.ai.simulation.GameCopier;
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
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Separate missed native declarations from the rules outcomes of an explicitly selected attack. */
public class CommanderForecastPlanningScopeTest extends AITest {
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



    private void nestedForecastScopeRestoresOrdinaryPlanning(boolean copied, boolean throwing) {
        Game game = board("goreclaw", copied);
        Player actor = seat(game, 0);
        Card creature = actor.getCreaturesInPlay().get(0);
        Combat combat = new Combat(actor);
        game.getPhaseHandler().setCombat(combat);
        java.util.Random random = forge.util.MyRandom.getRandom();
        long draws = forge.util.MyRandom.callCount();
        try {
            forge.ai.simulation.MultiplayerAttackPlanner.withCombatForecast(() -> {
                forge.ai.simulation.MultiplayerAttackPlanner.refine(actor, combat);
                assertTrue(combat.getAttackers().isEmpty(), "Do not start a whole-round search inside a single-combat forecast");
                forge.ai.simulation.MultiplayerAttackPlanner.withCombatForecast(() -> {
                    forge.ai.simulation.MultiplayerAttackPlanner.refine(actor, combat);
                    assertTrue(combat.getAttackers().isEmpty());
                    return null;
                });
                forge.ai.simulation.MultiplayerAttackPlanner.refine(actor, combat);
                assertTrue(combat.getAttackers().isEmpty(), "The nested scope must preserve its enclosing scope");
                if (throwing) throw new IllegalArgumentException("forecast-scope-control");
                return null;
            });
            assertFalse(throwing);
        } catch (IllegalArgumentException error) {
            assertTrue(throwing);
            assertEquals(error.getMessage(), "forecast-scope-control");
        }
        assertSame(forge.util.MyRandom.getRandom(), random);
        assertEquals(forge.util.MyRandom.callCount(), draws);
        forge.ai.simulation.MultiplayerAttackPlanner.refine(actor, combat);
        assertTrue(combat.isAttacking(creature), "Ordinary planning resumes after success or failure");
        assertSame(combat.getDefenderByAttacker(creature), seat(game, 1));
        assertEquals(forge.util.MyRandom.callCount(), draws);
        assertTrue(CombatUtil.validateAttackers(combat));
    }
    // Named entry points keep every fixed case unique in native evidence.
    @Test public void originalNestedForecastSuccess() { nestedForecastScopeRestoresOrdinaryPlanning(false, false); }
    @Test public void originalNestedForecastFailure() { nestedForecastScopeRestoresOrdinaryPlanning(false, true); }
    @Test public void copiedNestedForecastSuccess() { nestedForecastScopeRestoresOrdinaryPlanning(true, false); }
    @Test public void copiedNestedForecastFailure() { nestedForecastScopeRestoresOrdinaryPlanning(true, true); }

}
