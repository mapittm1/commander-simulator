package forge.ai;

import forge.card.GamePieceType;
import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
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

/** Supplied dungeon states test snapshot isolation and normal completion bookkeeping. */
public class CommanderDungeonSnapshotTest extends AITest {
    private Player actor(Game game) {
        return game.getPlayers().getFirst();
    }

    private Game board() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("dungeon-history-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Dungeon snapshot"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(40, null);
            for (int index = 0; index < 12; index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        return game;
    }

    private Card dungeon(Game game, String room) {
        Card dungeon = createToken("lost_mine_of_phandelver", actor(game));
        assertNotNull(dungeon);
        dungeon.setGamePieceType(GamePieceType.DUNGEON);
        game.getAction().moveToCommand(dungeon, null);
        dungeon.setCurrentRoom(room);
        return dungeon;
    }

    private Card complete(Game game) {
        Card dungeon = dungeon(game, "Temple of Dumathoin");
        assertTrue(dungeon.isInLastRoom());
        game.getAction().completeDungeon(actor(game), dungeon);
        assertEquals(actor(game).getCompletedDungeons().size(), 1);
        return dungeon;
    }

    private Game copy(Game game) {
        game.getAction().checkStaticAbilities(false);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        GameCopier copier = new GameCopier(game).withStack(true);
        Game result = copier.makeCopy(null, actor(game));
        copier.release();
        return result;
    }

    private Card bears(Player player) {
        for (Card creature : player.getCreaturesInPlay()) {
            if (creature.getName().equals("Grizzly Bears")) return creature;
        }
        throw new AssertionError("Missing supplied Grizzly Bears");
    }

    @Test
    public void completedHistorySurvivesAWorldCopy() {
        Game original = board();
        Card completed = complete(original);
        Game copied = copy(original);
        assertEquals(actor(copied).getCompletedDungeons().size(), 1);
        Card history = actor(copied).getCompletedDungeons().get(0);
        assertEquals(history.getName(), completed.getName());
        assertNotSame(history, completed);
        assertSame(history.getGame(), copied);
        assertSame(history.getOwner(), actor(copied));
        actor(copied).resetCompletedDungeons();
        assertEquals(actor(original).getCompletedDungeons().size(), 1,
                "The copied history container must not alias the original");
    }

    @Test
    public void currentRoomSurvivesAWorldCopy() {
        Game original = board();
        Card source = dungeon(original, "Mine Tunnels");
        Game copied = copy(original);
        Card dungeon = actor(copied).getCardsIn(ZoneType.Command).getFirst();
        assertTrue(dungeon.getType().isDungeon());
        assertEquals(dungeon.getCurrentRoom(), "Mine Tunnels");
        dungeon.setCurrentRoom("Dark Pool");
        assertEquals(source.getCurrentRoom(), "Mine Tunnels");
    }

    @Test
    public void ventureCountsSurviveAWorldCopy() {
        Game original = board();
        actor(original).incrementVenturedThisTurn();
        actor(original).incrementVenturedThisTurn();
        original.getPlayers().get(1).incrementVenturedThisTurn();
        Game copied = copy(original);
        assertEquals(actor(copied).getVenturedThisTurn(), 2);
        assertEquals(copied.getPlayers().get(1).getVenturedThisTurn(), 1);
        assertEquals(copied.getPlayers().get(2).getVenturedThisTurn(), 0);
        actor(copied).resetVenturedThisTurn();
        assertEquals(actor(original).getVenturedThisTurn(), 2);
    }

    @Test
    public void completedDungeonKeepsNadaarsStaticBonus() {
        Game original = board();
        complete(original);
        addCardToZone("Nadaar, Selfless Paladin", actor(original), ZoneType.Battlefield);
        addCardToZone("Grizzly Bears", actor(original), ZoneType.Battlefield);
        original.getAction().checkStaticAbilities(false);
        assertEquals(bears(actor(original)).getNetPower(), 3);
        Game copied = copy(original);
        copied.getAction().checkStaticAbilities(false);
        assertEquals(bears(actor(copied)).getNetPower(), 3,
                "Copying a completed dungeon must preserve the real static bonus");
    }

    @Test
    public void noHistoryDoesNotInventNadaarsStaticBonus() {
        Game original = board();
        addCardToZone("Nadaar, Selfless Paladin", actor(original), ZoneType.Battlefield);
        addCardToZone("Grizzly Bears", actor(original), ZoneType.Battlefield);
        original.getAction().checkStaticAbilities(false);
        assertEquals(bears(actor(original)).getNetPower(), 2);
        Game copied = copy(original);
        copied.getAction().checkStaticAbilities(false);
        assertEquals(bears(actor(copied)).getNetPower(), 2);
        assertTrue(actor(copied).getCompletedDungeons().isEmpty());
    }
}
