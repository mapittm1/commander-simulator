package forge.ai;

import static org.testng.Assert.*;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardLists;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Exact native snapshots must retain filtering behavior across repeated copies. */
public class CommanderNativeFilterContractTest extends AITest {
    @DataProvider(name = "copyDepths")
    public Object[][] copyDepths() {
        return new Object[][] {{0}, {1}, {2}};
    }

    private CardCollection cards(int depth) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("native-filter-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Native filter contracts"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            addCardToZone("Forest", player, ZoneType.Library);
        }
        Player actor = game.getPlayers().getFirst();
        addCardToZone("Forest", actor, ZoneType.Battlefield);
        addCardToZone("Mountain", actor, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 5);
        game.copyLastState();
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, game.getPlayers().getFirst());
            copier.release();
        }
        return new CardCollection(game.getPlayers().getFirst().getCardsIn(ZoneType.Battlefield));
    }

    private void snapshotMutation(int depth, String mode) {
        CardCollection source = cards(depth);
        List<Card> initial = new ArrayList<>(source);
        List<Card> visits = new ArrayList<>();
        CardCollection result = CardLists.filter(source, card -> {
            visits.add(card);
            if (visits.size() == 1) {
                if (mode.equals("clear")) {
                    source.clear();
                } else if (mode.equals("remove")) {
                    source.remove(initial.get(1));
                } else {
                    source.clear();
                    source.add(initial.get(1));
                    source.add(initial.get(0));
                }
            }
            return true;
        });
        assertEquals(visits, initial, "Predicate observes the original snapshot exactly once");
        assertEquals(result, initial, "Output retains snapshot order despite source mutation");
        result.clear();
        assertEquals(source.size(), mode.equals("clear") ? 0 : mode.equals("remove") ? 1 : 2);
    }

    @Test(dataProvider = "copyDepths")
    public void clearDuringPredicate(int depth) { snapshotMutation(depth, "clear"); }

    @Test(dataProvider = "copyDepths")
    public void removeDuringPredicate(int depth) { snapshotMutation(depth, "remove"); }

    @Test(dataProvider = "copyDepths")
    public void reorderDuringPredicate(int depth) { snapshotMutation(depth, "reorder"); }

    @Test(dataProvider = "copyDepths")
    public void predicateFailureIdentity(int depth) {
        CardCollection source = cards(depth);
        List<Card> visits = new ArrayList<>();
        RuntimeException expected = new IllegalStateException("fixed native predicate failure");
        try {
            CardLists.filter(source, card -> {
                visits.add(card);
                throw expected;
            });
            fail("Predicate failure must propagate");
        } catch (RuntimeException actual) {
            assertSame(actual, expected);
        }
        assertEquals(visits, List.of(source.getFirst()));
    }

    @Test(dataProvider = "copyDepths")
    public void composedPredicatesShortCircuit(int depth) {
        CardCollection source = cards(depth);
        Card first = source.getFirst();
        List<String> visits = new ArrayList<>();
        CardCollection result = CardLists.filter(source,
                card -> { visits.add("first:" + card.getId()); return card == first; },
                card -> { visits.add("second:" + card.getId()); return true; });
        assertEquals(result, List.of(first));
        assertEquals(visits, List.of("first:" + first.getId(), "second:" + first.getId(),
                "first:" + source.get(1).getId()));
    }

    @Test(dataProvider = "copyDepths")
    public void iterablePredicatesPreserveOrder(int depth) {
        CardCollection source = cards(depth);
        List<Card> visits = new ArrayList<>();
        List<Predicate<Card>> predicates = List.of(card -> { visits.add(card); return true; },
                card -> card == source.getFirst());
        assertEquals(CardLists.filter(source, predicates), List.of(source.getFirst()));
        assertEquals(visits, new ArrayList<>(source));
    }

    @Test(dataProvider = "copyDepths")
    public void subclassRetainsSpliterator(int depth) {
        CardCollection initial = cards(depth);
        AtomicInteger metadataReads = new AtomicInteger();
        CardCollection custom = new CardCollection() {
            @Override public Iterator<Card> iterator() {
                throw new AssertionError("Subclass supplies its own traversal");
            }
            @Override public Spliterator<Card> spliterator() {
                metadataReads.incrementAndGet();
                return List.of(initial.get(1), initial.get(0)).spliterator();
            }
        };
        assertEquals(CardLists.filter(custom, card -> true), List.of(initial.get(1), initial.get(0)));
        assertEquals(metadataReads.get(), 1);
    }

    @Test(dataProvider = "copyDepths")
    public void emptyResultsStayMutableAndNullPredicatesFail(int depth) {
        Card first = cards(depth).getFirst();
        CardCollection source = new CardCollection();
        CardCollection result = CardLists.filter(source, card -> true);
        result.add(first);
        assertTrue(source.isEmpty());
        assertEquals(result, List.of(first));
        expectThrows(NullPointerException.class, () -> CardLists.filter(source, (Predicate<Card>) null));
    }
}
