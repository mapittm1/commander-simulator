package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Printed replacement outcomes exercise draw-step and extra-draw history in snapshots. */
public class CommanderDrawHistorySnapshotTest extends AITest {
    @Test public void originalFirstDrawIsNotStolen() { thief(false, false); }
    @Test public void copiedFirstDrawIsNotStolen() { thief(true, false); }
    @Test public void originalSecondDrawIsStolen() { thief(false, true); }
    @Test public void copiedSecondDrawIsStolen() { thief(true, true); }
    @Test public void originalFirstExtraDrawIsReplaced() { reed(false, false); }
    @Test public void copiedFirstExtraDrawIsReplaced() { reed(true, false); }
    @Test public void originalLaterExtraDrawIsNotReplaced() { reed(false, true); }
    @Test public void copiedLaterExtraDrawIsNotReplaced() { reed(true, true); }

    private Game game() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("draw-history-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Draw history"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 12; index++) addCardToZone("Shock", player, ZoneType.Library);
        }
        return game;
    }

    private void thief(boolean copied, boolean alreadyDrew) {
        Game game = game();
        Player actor = game.getPlayers().get(1), enemy = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.DRAW, actor, 2);
        actor.setNumDrawnLastTurn(3);
        if (alreadyDrew) assertEquals(actor.drawCards(1).size(), 1);
        addCardToZone("Notion Thief", enemy, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        game.copyLastState();
        Player original = actor;
        if (copied) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor);
            actor = copier.find(actor);
            enemy = copier.find(enemy);
        }
        assertEquals(actor.getNumDrawnLastTurn(), 3);
        assertEquals(actor.numDrawnThisDrawStep(), alreadyDrew ? 1 : 0);
        int actorHand = actor.getCardsIn(ZoneType.Hand).size();
        int enemyHand = enemy.getCardsIn(ZoneType.Hand).size();
        assertEquals(actor.drawCards(1).size(), alreadyDrew ? 0 : 1);
        assertEquals(actor.getCardsIn(ZoneType.Hand).size(), actorHand + (alreadyDrew ? 0 : 1));
        assertEquals(enemy.getCardsIn(ZoneType.Hand).size(), enemyHand + (alreadyDrew ? 1 : 0));
        if (copied) {
            assertEquals(original.getCardsIn(ZoneType.Hand).size(), actorHand);
            actor.resetNumDrawnThisTurn();
            actor.resetNumDrawnThisDrawStep();
            assertEquals(original.getNumDrawnThisTurn(), alreadyDrew ? 1 : 0);
            assertEquals(original.numDrawnThisDrawStep(), alreadyDrew ? 1 : 0);
        }
    }

    private void reed(boolean copied, boolean alreadyDrew) {
        Game game = game();
        Player actor = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, actor, 2);
        actor.setNumDrawnLastTurn(3);
        if (alreadyDrew) assertEquals(actor.drawCards(1).size(), 1);
        addCardToZone("Reed Richards, Smartest Man", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        game.copyLastState();
        Player original = actor;
        if (copied) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor);
            actor = copier.find(actor);
        }
        int hand = actor.getCardsIn(ZoneType.Hand).size();
        int library = actor.getCardsIn(ZoneType.Library).size();
        int drawn = alreadyDrew ? 1 : 4;
        assertEquals(actor.getNumDrawnLastTurn(), 3);
        actor.drawCards(1);
        assertEquals(actor.getCardsIn(ZoneType.Hand).size(), hand + drawn);
        assertEquals(actor.getCardsIn(ZoneType.Library).size(), library - drawn);
        assertEquals(actor.getNumDrawnThisTurn(), (alreadyDrew ? 1 : 0) + drawn);
        if (copied) {
            assertEquals(original.getCardsIn(ZoneType.Hand).size(), hand);
            assertEquals(original.getCardsIn(ZoneType.Library).size(), library);
            assertEquals(original.getNumDrawnThisTurn(), alreadyDrew ? 1 : 0);
        }
    }
}
