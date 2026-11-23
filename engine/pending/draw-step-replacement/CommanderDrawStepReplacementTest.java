package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameSnapshot;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Per-draw outcomes distinguish a correctly timed replacement from equal batch totals. */
public class CommanderDrawStepReplacementTest extends AITest {
    @Test public void originalOwnFirst() { exercise("ownFirst", 0); }
    @Test public void activeCopyOwnFirst() { exercise("ownFirst", 1); }
    @Test public void commonSnapshotOwnFirst() { exercise("ownFirst", 2); }
    @Test public void originalOwnSecond() { exercise("ownSecond", 0); }
    @Test public void activeCopyOwnSecond() { exercise("ownSecond", 1); }
    @Test public void commonSnapshotOwnSecond() { exercise("ownSecond", 2); }
    @Test public void originalOwnThird() { exercise("ownThird", 0); }
    @Test public void activeCopyOwnThird() { exercise("ownThird", 1); }
    @Test public void commonSnapshotOwnThird() { exercise("ownThird", 2); }
    @Test public void originalOwnBatch() { exercise("ownBatch", 0); }
    @Test public void activeCopyOwnBatch() { exercise("ownBatch", 1); }
    @Test public void commonSnapshotOwnBatch() { exercise("ownBatch", 2); }
    @Test public void originalForeignFirst() { exercise("foreignFirst", 0); }
    @Test public void activeCopyForeignFirst() { exercise("foreignFirst", 1); }
    @Test public void commonSnapshotForeignFirst() { exercise("foreignFirst", 2); }
    @Test public void originalMainFirst() { exercise("mainFirst", 0); }
    @Test public void activeCopyMainFirst() { exercise("mainFirst", 1); }
    @Test public void commonSnapshotMainFirst() { exercise("mainFirst", 2); }

    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("draw-step-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Draw step replacement"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 30; index++) addCardToZone("Shock", player, ZoneType.Library);
        }
        return game;
    }

    private void exercise(String state, int world) {
        Game game = pod();
        Player actor = game.getPlayers().get(1);
        Player turnPlayer = state.equals("foreignFirst") ? game.getPlayers().get(0) : actor;
        PhaseType phase = state.equals("mainFirst") ? PhaseType.MAIN2 : PhaseType.DRAW;
        game.getPhaseHandler().devModeSet(phase, turnPlayer, 2);
        addCardToZone("Reed Richards, Smartest Man", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        if (state.equals("ownSecond") || state.equals("ownThird")) {
            drawAndCheck(actor, 1, 1, "normal first own draw");
        }
        if (state.equals("ownThird")) {
            // Observe the third draw independently: the preceding eligible event has happened,
            // even on the baseline engine whose replacement timing may be wrong.
            actor.drawCards(1);
        }
        game.copyLastState();
        Player original = actor;
        int originalHand = actor.getCardsIn(ZoneType.Hand).size();
        int originalLibrary = actor.getCardsIn(ZoneType.Library).size();
        int originalDrawCount = actor.getNumDrawnThisTurn();
        if (world == 1) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor);
            actor = copier.find(actor);
        } else if (world == 2) {
            int actorId = actor.getId();
            game = new GameSnapshot(game).makeCopy();
            game.setNoGUIUser();
            actor = game.getPlayer(actorId);
        }
        assertEquals(actor.getCardsIn(ZoneType.Hand).size(), originalHand);
        assertEquals(actor.getNumDrawnThisTurn(), originalDrawCount);
        int expected = state.equals("ownFirst") || state.equals("ownThird") ? 1
                : state.equals("ownBatch") ? 6 : 4;
        drawAndCheck(actor, state.equals("ownBatch") ? 3 : 1, expected, state);
        if (world != 0) {
            assertNotSame(actor, original);
            assertEquals(original.getCardsIn(ZoneType.Hand).size(), originalHand);
            assertEquals(original.getCardsIn(ZoneType.Library).size(), originalLibrary);
            assertEquals(original.getNumDrawnThisTurn(), originalDrawCount);
        }
    }

    private void drawAndCheck(Player player, int requested, int expected, String context) {
        int hand = player.getCardsIn(ZoneType.Hand).size();
        int library = player.getCardsIn(ZoneType.Library).size();
        int drawn = player.getNumDrawnThisTurn();
        player.drawCards(requested);
        assertEquals(player.getCardsIn(ZoneType.Hand).size() - hand, expected, context + " hand delta");
        assertEquals(library - player.getCardsIn(ZoneType.Library).size(), expected, context + " library delta");
        assertEquals(player.getNumDrawnThisTurn() - drawn, expected, context + " draw history");
        assertTrue(player.getCardsIn(ZoneType.Hand).stream().allMatch(Card::getDrawnThisTurn));
    }
}
