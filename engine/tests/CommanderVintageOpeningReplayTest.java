package forge.ai;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.testng.annotations.Test;
import static org.testng.Assert.assertTrue;

/** Fixed opening replay from the retained broad-suite failure; no random opening draw. */
public class CommanderVintageOpeningReplayTest extends AITest {
    private record Entry(int originalId, String name) { }
    private static final List<Entry> DECK = List.of(
        new Entry(1, "Adeline, Resplendent Cathar"),
        new Entry(2, "Ajani, Nacatl Pariah"),
        new Entry(3, "Arid Mesa"),
        new Entry(4, "Black Widow, Super Spy"),
        new Entry(5, "Bleachbone Verge"),
        new Entry(6, "Bloodstained Mire"),
        new Entry(7, "Cecil, Dark Knight"),
        new Entry(8, "Chrome Mox"),
        new Entry(9, "Clarion Conqueror"),
        new Entry(10, "Concealed Courtyard"),
        new Entry(11, "Dauthi Voidwalker"),
        new Entry(12, "Deathrite Shaman"),
        new Entry(13, "Eiganjo, Seat of the Empire"),
        new Entry(14, "Fatal Push"),
        new Entry(15, "Flooded Strand"),
        new Entry(16, "Giver of Runes"),
        new Entry(17, "Godless Shrine"),
        new Entry(18, "Gollum, Riddle Master"),
        new Entry(19, "Grief"),
        new Entry(20, "Guide of Souls"),
        new Entry(21, "Inquisition of Kozilek"),
        new Entry(22, "Jennifer Walters"),
        new Entry(23, "Karakas"),
        new Entry(24, "Lotus Petal"),
        new Entry(25, "Mai, Scornful Striker"),
        new Entry(26, "Marsh Flats"),
        new Entry(27, "Mishra's Bauble"),
        new Entry(28, "Moonshadow"),
        new Entry(29, "Mother of Runes"),
        new Entry(30, "Ocelot Pride"),
        new Entry(31, "Orcish Bowmasters"),
        new Entry(32, "Phelia, Exuberant Shepherd"),
        new Entry(33, "Plains"),
        new Entry(34, "Polluted Delta"),
        new Entry(35, "Prismatic Ending"),
        new Entry(36, "Sage of the Skies"),
        new Entry(37, "Scrubland"),
        new Entry(38, "Seasoned Dungeoneer"),
        new Entry(39, "Shadowy Backstreet"),
        new Entry(40, "Silent Clearing"),
        new Entry(41, "Solitude"),
        new Entry(42, "Spectacular Spider-Man"),
        new Entry(43, "Super Shredder"),
        new Entry(44, "Swamp"),
        new Entry(45, "Swamp"),
        new Entry(46, "Swords to Plowshares"),
        new Entry(47, "Syr Vondam, Sunstar Exemplar"),
        new Entry(48, "Takenuma, Abandoned Mire"),
        new Entry(49, "Temple Garden"),
        new Entry(50, "Thalia, Guardian of Thraben"),
        new Entry(51, "Thalia, Heretic Cathar"),
        new Entry(52, "The Queen of Dale"),
        new Entry(53, "Thoughtseize"),
        new Entry(54, "Tokka & Rahzar, Terrible Twos"),
        new Entry(55, "Unearth"),
        new Entry(56, "Vanishing Verse"),
        new Entry(57, "Verdant Catacombs"),
        new Entry(58, "Voice of Victory"),
        new Entry(59, "White Plume Adventurer"),
        new Entry(60, "Windswept Heath"));
    private static final int[] ORDER = {29, 58, 9, 32, 46, 16, 27, 36, 7, 48, 14, 22, 38, 25, 12, 33, 45, 2, 15, 17, 42, 52, 53, 24, 3, 51, 5, 21, 37, 1, 13, 4, 28, 23, 57, 26, 54, 40, 41, 43, 50, 55, 47, 49, 59, 30, 8, 19, 6, 10, 60, 34, 31, 44, 18, 39, 20, 11, 56, 35};

    @Test public void openingAtSeed42() { replay(42); }
    @Test public void openingAtSeed0() { replay(0); }
    @Test public void openingAtSeed4096() { replay(4096); }

    private void replay(long seed) {
        MyRandom.setRandom(new Random(seed));
        Game game = initAndCreateGame();
        Player actor = game.getPlayers().get(1);
        Map<Integer, Card> cards = new HashMap<>();
        for (Entry entry : DECK) {
            cards.put(entry.originalId(), addCardToZone(entry.name(), actor, ZoneType.Library));
        }
        List<Card> ordered = new ArrayList<>();
        for (int id : ORDER) {
            ordered.add(cards.get(id));
        }
        actor.getZone(ZoneType.Library).setCards(ordered);
        actor.drawCards(7);
        assertTrue(actor.getCardsIn(ZoneType.Hand).stream().anyMatch(card -> card.getName().equals("Mishra's Bauble")));
        assertTrue(actor.getCardsIn(ZoneType.Hand).stream().noneMatch(Card::isLand));
        game.AI_FLAT_MONTE_CARLO = true;
        game.AI_TIMEOUT = 2;
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor);
        game.getAction().checkStateEffects(true);
        for (int step = 0; step < 600 && !game.isGameOver()
                && !game.getPhaseHandler().is(PhaseType.END_OF_TURN)
                && game.getPhaseHandler().getPlayerTurn() == actor; step++) {
            game.getPhaseHandler().mainLoopStep();
        }
        assertTrue(!actor.getCardsIn(ZoneType.Battlefield).isEmpty()
                || !actor.getCardsIn(ZoneType.Graveyard).isEmpty()
                || actor.getCardsIn(ZoneType.Hand).size() < 7,
                "The retained opening must perform an action if its zero-cost artifact is eligible");
    }
}
