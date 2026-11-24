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
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Paid World casts and phasing distinguish state metadata from actual survivors. */
public class CommanderWorldStateTest extends AITest {
    private Player seat(Game game, int index) {
        return game.getPlayers().stream().filter(player -> player.getName().equals("world-" + index))
                .findFirst().orElseThrow();
    }

    private Card named(Game game, int owner, String name, ZoneType zone) {
        return seat(game, owner).getCardsIn(zone, false).stream()
                .filter(card -> card.getName().equals(name)).findFirst().orElseThrow();
    }

    private void settle(Game game) {
        GameSimulator.resolveStack(game, seat(game, 0));
    }

    private void cast(Game game, int owner, String name, int mana) {
        Card spell = addCardToZone(name, seat(game, owner), ZoneType.Hand);
        SpellAbility ability = spell.getFirstSpellAbility();
        ability.setActivatingPlayer(seat(game, owner));
        assertTrue(ability.canPlay() && ability.checkRestrictions(ability.getHostCard(), seat(game, owner)));
        assertTrue(ComputerUtil.playStack(ability, seat(game, owner), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        settle(game);
    }

    private Game oneWorld() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            players.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("world-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Paid World ordering"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
            for (int index = 0; index < 12; index++) addCardToZone("Swamp", player, ZoneType.Battlefield);
        }
        for (int index = 0; index < 4; index++) addCardToZone("Plains", seat(game, 0), ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, seat(game, 0), 5);
        cast(game, 0, "The Abyss", 4);
        assertTrue(named(game, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp() > 0);
        long original = named(game, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp();
        cast(game, 0, "Ornithopter", 0);
        assertTrue(game.getTimestamp() > original, "A later legal spell advances game time");
        assertEquals(named(game, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp(), original,
                "An unrelated permanent entering does not restart a World's age");
        game.copyLastState();
        return game;
    }

    private Game twoWorldsWithOnePhasedOut() {
        Game game = oneWorld();
        long earlier = named(game, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp();
        cast(game, 0, "Teferi's Protection", 3);
        assertTrue(named(game, 0, "The Abyss", ZoneType.Battlefield).isPhasedOut());
        assertTrue(seat(game, 0).getCardsIn(ZoneType.Battlefield).isEmpty());
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, seat(game, 1), 6);
        cast(game, 1, "Nether Void", 4);
        assertEquals(named(game, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp(), earlier);
        assertTrue(named(game, 1, "Nether Void", ZoneType.Battlefield).getWorldTimestamp() > earlier);
        game.copyLastState();
        return game;
    }

    private void advanceAndCheckSurvivor(Game game) {
        assertTrue(named(game, 0, "The Abyss", ZoneType.Battlefield).isPhasedOut());
        boolean reached = false;
        for (int turn = 0; turn < 4; turn++) {
            assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
            settle(game);
            if (game.getPhaseHandler().getPlayerTurn() == seat(game, 0)) { reached = true; break; }
        }
        assertTrue(reached);
        assertNotNull(named(game, 0, "The Abyss", ZoneType.Graveyard));
        assertNotNull(named(game, 1, "Nether Void", ZoneType.Battlefield));
        assertTrue(seat(game, 1).getCardsIn(ZoneType.Graveyard).stream()
                .noneMatch(card -> card.getName().equals("Nether Void")), "The more recent World survives");
    }

    private void timestampCopy(int depth) {
        Game original = oneWorld();
        long timestamp = named(original, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp();
        Game copy = original;
        for (int index = 0; index < depth; index++) copy = new GameCopier(copy).withStack(true).makeCopy(null, seat(copy, 0));
        assertEquals(named(copy, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp(), timestamp,
                "A simulation snapshot preserves how long a permanent has been a World");
        assertEquals(named(original, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp(), timestamp);
    }

    private void copiedSurvivor(int depth, boolean copyFirst) {
        Game original = twoWorldsWithOnePhasedOut();
        Game copy = original;
        for (int index = 0; index < depth; index++) copy = new GameCopier(copy).withStack(true).makeCopy(null, seat(copy, 0));
        Game first = copyFirst ? copy : original;
        Game other = copyFirst ? original : copy;
        advanceAndCheckSurvivor(first);
        assertTrue(named(other, 0, "The Abyss", ZoneType.Battlefield).isPhasedOut());
        assertNotNull(named(other, 1, "Nether Void", ZoneType.Battlefield));
        advanceAndCheckSurvivor(other);
    }

    @Test public void originalPaidWorldGetsTimestamp() { oneWorld(); }
    @Test public void originalPhasingRetainsLaterWorld() { advanceAndCheckSurvivor(twoWorldsWithOnePhasedOut()); }
    @Test public void oneCopyPreservesWorldTimestamp() { timestampCopy(1); }
    @Test public void twoCopiesPreserveWorldTimestamp() { timestampCopy(2); }
    @Test public void oneCopyOriginalAdvancesFirst() { copiedSurvivor(1, false); }
    @Test public void twoCopiesOriginalAdvancesFirst() { copiedSurvivor(2, false); }
    @Test public void oneCopyAdvancesBeforeOriginal() { copiedSurvivor(1, true); }
    @Test public void twoCopiesAdvanceBeforeOriginal() { copiedSurvivor(2, true); }
}
