package forge.ai;

import forge.card.GamePieceType;
import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.game.ability.ApiType;
import forge.game.cost.CostPayment;
import forge.game.spellability.SpellAbility;
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
    private Card named(Player player, String name) {
        for (Card card : player.getCardsIn(ZoneType.Battlefield)) {
            if (card.getName().equals(name)) return card;
        }
        throw new AssertionError("Missing supplied permanent: " + name);
    }

    private Card activeDungeon(Player player) {
        for (Card card : player.getCardsIn(ZoneType.Command)) {
            if (card.getType().isDungeon()) return card;
        }
        return null;
    }

    private void paidCompletion(int depth) {
        Game original = board();
        dungeon(original, "Dark Pool");
        Player payer = actor(original);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Plains", payer, ZoneType.Battlefield);
        }
        addCardToZone("Dungeon Map", payer, ZoneType.Battlefield);
        addCardToZone("Nadaar, Selfless Paladin", payer, ZoneType.Battlefield);
        addCardToZone("Grizzly Bears", payer, ZoneType.Battlefield);
        original.getAction().checkStaticAbilities(false);
        assertEquals(bears(payer).getNetPower(), 2);

        Game game = original;
        for (int index = 0; index < depth; index++) game = copy(game);
        payer = actor(game);
        Card current = activeDungeon(payer);
        assertNotNull(current, "The existing dungeon remains in the copied command zone");
        assertEquals(current.getCurrentRoom(), "Dark Pool");
        assertEquals(current.getGamePieceType(), GamePieceType.DUNGEON);
        assertTrue(payer.getCompletedDungeons().isEmpty());
        assertEquals(payer.getCardsIn(ZoneType.Hand).size(), 0);

        Card map = named(payer, "Dungeon Map");
        SpellAbility venture = null;
        for (SpellAbility ability : map.getSpellAbilities()) {
            if (ability.getApi() == ApiType.Venture) venture = ability;
        }
        assertNotNull(venture);
        venture.setActivatingPlayer(payer);
        assertTrue(venture.getRestrictions().canPlay(map, venture));
        assertTrue(ComputerUtilCost.canPayCost(venture, payer, false));
        assertFalse(map.isTapped(), "Affordability does not activate the live artifact");
        assertEquals(current.getCurrentRoom(), "Dark Pool");
        assertTrue(new CostPayment(venture.getPayCosts(), venture).payComputerCosts(
                new AiCostDecision(payer, venture, false, true)));
        game.getStack().add(venture);
        GameSimulator.resolveStack(game, payer);

        assertTrue(map.isTapped());
        for (Card card : payer.getCardsIn(ZoneType.Battlefield)) {
            if (card.getName().equals("Plains")) assertTrue(card.isTapped());
        }
        assertEquals(payer.getCardsIn(ZoneType.Hand).size(), 1,
                "The final room actually resolves and draws its card");
        assertEquals(payer.getVenturedThisTurn(), 1);
        assertEquals(payer.getCompletedDungeons().size(), 1);
        assertEquals(payer.getCompletedDungeons().get(0).getName(), "Lost Mine of Phandelver");
        assertNull(activeDungeon(payer), "The completed dungeon leaves the game");
        assertEquals(bears(payer).getNetPower(), 3);
        assertTrue(game.getStack().isEmpty());

        Game after = copy(game);
        assertEquals(actor(after).getCompletedDungeons().size(), 1);
        assertEquals(actor(after).getVenturedThisTurn(), 1);
        assertEquals(actor(after).getCompletedDungeons().get(0).getCurrentRoom(), "Temple of Dumathoin");
        assertSame(actor(after).getCompletedDungeons().get(0).getGame(), after);
        assertEquals(bears(actor(after)).getNetPower(), 3);
        assertNull(activeDungeon(actor(after)));
        if (depth > 0) {
            assertEquals(actor(original).getCardsIn(ZoneType.Hand).size(), 0);
            assertTrue(actor(original).getCompletedDungeons().isEmpty());
            assertEquals(activeDungeon(actor(original)).getCurrentRoom(), "Dark Pool");
            assertFalse(named(actor(original), "Dungeon Map").isTapped());
        }
    }

    @Test
    public void paidVentureCompletesOriginalFromSuppliedRoom() {
        paidCompletion(0);
    }

    @Test
    public void paidVentureCompletesAfterOneWorldCopy() {
        paidCompletion(1);
    }

    @Test
    public void paidVentureCompletesAfterTwoWorldCopies() {
        paidCompletion(2);
    }

}
