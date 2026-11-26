package forge.ai;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Table;
import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.phase.PhaseType;
import forge.game.player.RegisteredPlayer;

import forge.game.Game;
import forge.game.GameEntityCounterTable;
import forge.game.GameObject;
import forge.game.IEntityMap;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardDamageTable;
import forge.game.card.CardZoneTable;
import forge.game.player.Player;
import forge.game.player.PlayerCollection;
import forge.game.spellability.SpellAbilitySnapshot;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Supplied-world graph-copy contracts, separate from complete gameplay claims. */
public class CommanderPayloadGraphTest extends AITest {
    static Game newBoard() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("ability-supplier-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Snapshot supplier contracts"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().getFirst(), 3);
        return game;
    }

    private final List<GameCopier> copies = new ArrayList<>();
    @AfterMethod public void releaseCopies() {
        for (GameCopier copier : copies) copier.release();
        copies.clear();
    }
    private static final class Scene {
        Game game;
        Card elf;
        Card ring;
        Player player;
        GameCopier copier;
        SpellAbilitySnapshot context;
    }
    private Scene scene() {
        Scene s = new Scene(); s.game = newBoard();
        s.player = s.game.getPlayers().getFirst();
        s.elf = addCardToZone("Llanowar Elves", s.player, ZoneType.Battlefield);
        s.ring = addCardToZone("Sol Ring", s.player, ZoneType.Battlefield);
        s.copier = branch(s.game); s.context = context(s.copier); return s;
    }
    private GameCopier branch(Game game) {
        game.getAction().checkStaticAbilities(false); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        GameCopier copier = new GameCopier(game).withStack(true); copier.makeCopy(null, game.getPlayers().getFirst());
        copies.add(copier); return copier;
    }
    private SpellAbilitySnapshot context(GameCopier copier) {
        return new SpellAbilitySnapshot(new IEntityMap() {
            @Override public Game getGame() { return copier.getCopiedGame(); }
            @Override public GameObject map(GameObject value) { return copier.find(value); }
        });
    }
    private List<Object> repeated(Object value) { List<Object> list = new ArrayList<>(); list.add(value); list.add(value); return list; }
    private List<?> copiedList(Scene s, Object source) { return (List<?>) s.context.data(source); }
    private CardCollection cards(Card card) { CardCollection result = new CardCollection(); result.add(card); return result; }
    private static final class ObservedList extends ArrayList<Object> {
        int iterations;
        Runnable onIterator;
        RuntimeException failure;
        @Override public Iterator<Object> iterator() {
            iterations++;
            if (onIterator != null) onIterator.run();
            if (failure != null) throw failure;
            return super.iterator();
        }
    }
    @Test public void listAliasesWithinMapStaySharedAndIndependentOfSource() {
        Scene s = scene(); List<Object> shared = new ArrayList<>(); shared.add(s.elf);
        Map<String, Object> source = new LinkedHashMap<>(); source.put("a", shared); source.put("b", shared);
        Map<?, ?> result = (Map<?, ?>) s.context.data(source);
        assertSame(result.get("a"), result.get("b")); assertNotSame(result.get("a"), shared);
        assertSame(((List<?>) result.get("a")).get(0), s.copier.find(s.elf));
    }
    @Test public void listSelfCycleKeepsMappedCards() {
        Scene s = scene(); List<Object> source = new ArrayList<>(); source.add(source); source.add(s.elf);
        List<?> result = copiedList(s, source); assertSame(result.get(0), result); assertSame(result.get(1), s.copier.find(s.elf));
    }
    @Test public void mutualListMapCycleKeepsOneCopyPerSource() {
        Scene s = scene(); List<Object> source = new ArrayList<>(); Map<String, Object> map = new LinkedHashMap<>();
        source.add(map); map.put("back", source); map.put("card", s.elf);
        List<?> result = copiedList(s, source); Map<?, ?> nested = (Map<?, ?>) result.get(0);
        assertSame(nested.get("back"), result); assertSame(nested.get("card"), s.copier.find(s.elf));
    }
    @Test public void objectArraySelfCycleKeepsMappedCards() {
        Scene s = scene(); Object[] source = new Object[2]; source[0] = source; source[1] = s.elf;
        Object[] result = (Object[]) s.context.data(source);
        assertSame(result[0], result); assertSame(result[1], s.copier.find(s.elf)); assertNotSame(result, source);
    }
    @Test public void objectArrayAliasesAcrossPayloadReferencesStayShared() {
        Scene s = scene(); Object[] source = {s.elf}; List<?> result = copiedList(s, repeated(source));
        assertSame(result.get(0), result.get(1)); assertNotSame(result.get(0), source);
        assertSame(((Object[]) result.get(0))[0], s.copier.find(s.elf));
    }
    @Test public void intArrayAliasPreservesIndependentMutableState() {
        Scene s = scene(); int[] source = {1}; List<?> result = copiedList(s, repeated(source));
        assertSame(result.get(0), result.get(1)); ((int[]) result.get(0))[0] = 8; assertEquals(source[0], 1);
    }
    @Test public void byteArrayAliasPreservesIndependentMutableState() {
        Scene s = scene(); byte[] source = {1}; List<?> result = copiedList(s, repeated(source));
        assertSame(result.get(0), result.get(1)); ((byte[]) result.get(0))[0] = 8; assertEquals(source[0], (byte) 1);
    }
    @Test public void longArrayAliasPreservesIndependentMutableState() {
        Scene s = scene(); long[] source = {1}; List<?> result = copiedList(s, repeated(source));
        assertSame(result.get(0), result.get(1)); ((long[]) result.get(0))[0] = 8; assertEquals(source[0], 1L);
    }
    @Test public void mapSelfCycleKeepsMappedCards() {
        Scene s = scene(); Map<String, Object> source = new LinkedHashMap<>(); source.put("self", source); source.put("card", s.elf);
        Map<?, ?> result = (Map<?, ?>) s.context.data(source);
        assertSame(result.get("self"), result); assertSame(result.get("card"), s.copier.find(s.elf));
    }
    @Test public void sharedSetUsesOneIndependentMappedContainer() {
        Scene s = scene(); Set<Object> source = new LinkedHashSet<>(); source.add(s.elf);
        List<?> result = copiedList(s, repeated(source)); assertSame(result.get(0), result.get(1));
        assertNotSame(result.get(0), source); assertTrue(((Set<?>) result.get(0)).contains(s.copier.find(s.elf)));
    }
    @Test public void sharedCardCollectionUsesOneMappedPayloadContainer() {
        Scene s = scene(); CardCollection source = cards(s.elf); List<?> result = copiedList(s, repeated(source));
        assertSame(result.get(0), result.get(1)); assertNotSame(result.get(0), source);
        assertSame(((CardCollection) result.get(0)).getFirst(), s.copier.find(s.elf));
    }
    @Test public void sharedPlayerCollectionUsesMappedPlayers() {
        Scene s = scene(); PlayerCollection source = new PlayerCollection(); source.add(s.player);
        List<?> result = copiedList(s, repeated(source)); assertSame(result.get(0), result.get(1));
        assertSame(((PlayerCollection) result.get(0)).getFirst(), s.copier.find(s.player));
    }
    @Test public void explicitFieldOwnedCardCopiesRemainSeparate() {
        Scene s = scene(); CardCollection source = cards(s.elf);
        CardCollection a = s.context.cards(source); CardCollection b = s.context.cards(source);
        assertNotSame(a, b); assertSame(a.getFirst(), b.getFirst());
        Object payload = s.context.data(source); assertSame(payload, s.context.data(source));
        assertNotSame(payload, a); assertNotSame(payload, source);
    }
    @Test public void sharedFCollectionPreservesMappedPayloadIdentity() {
        Scene s = scene(); FCollection<Object> source = new FCollection<>(); source.add(s.elf);
        List<?> result = copiedList(s, repeated(source)); assertSame(result.get(0), result.get(1));
        assertSame(((FCollection<?>) result.get(0)).getFirst(), s.copier.find(s.elf));
    }
    @Test public void multisetAliasesKeepCountsAndMappedCards() {
        Scene s = scene(); Multiset<Object> source = HashMultiset.create(); source.add(s.elf, 2);
        List<?> result = copiedList(s, repeated(source)); assertSame(result.get(0), result.get(1));
        assertEquals(((Multiset<?>) result.get(0)).count(s.copier.find(s.elf)), 2); assertEquals(source.count(s.elf), 2);
    }
    @Test public void genericTableSelfCycleKeepsMappedValues() {
        Scene s = scene(); Table<Object, Object, Object> source = HashBasedTable.create();
        source.put("row", "self", source); source.put("row", "card", s.elf);
        Table<?, ?, ?> result = (Table<?, ?, ?>) s.context.data(source);
        assertSame(result.get("row", "self"), result); assertSame(result.get("row", "card"), s.copier.find(s.elf));
    }
    @Test public void nativeDamageTableAliasesKeepMappedCells() {
        Scene s = scene(); CardDamageTable source = new CardDamageTable(); source.put(s.elf, s.player, 3);
        List<?> result = copiedList(s, repeated(source)); assertSame(result.get(0), result.get(1));
        CardDamageTable value = (CardDamageTable) result.get(0);
        assertEquals(value.get(s.copier.find(s.elf), s.copier.find(s.player)), Integer.valueOf(3));
    }
    @Test public void emptyNativeCounterTableAliasesRemainMutable() {
        Scene s = scene(); GameEntityCounterTable source = new GameEntityCounterTable();
        List<?> result = copiedList(s, repeated(source)); assertSame(result.get(0), result.get(1));
        assertNotSame(result.get(0), source); assertEquals(result.get(0).getClass(), GameEntityCounterTable.class);
    }
    @Test public void nativeZoneTableAliasesKeepMappedLastStatesAndCells() {
        Scene s = scene(); CardZoneTable source = new CardZoneTable(cards(s.elf), new CardCollection());
        source.put(ZoneType.Hand, ZoneType.Battlefield, s.elf);
        List<?> result = copiedList(s, repeated(source)); assertSame(result.get(0), result.get(1));
        CardZoneTable value = (CardZoneTable) result.get(0);
        assertSame(value.getLastStateBattlefield().getFirst(), s.copier.find(s.elf));
        assertSame(value.get(ZoneType.Hand, ZoneType.Battlefield).getFirst(), s.copier.find(s.elf));
    }
    @Test public void unknownPayloadFailureNeverReturnsCachedPartialContainer() {
        Scene s = scene(); Map<String, Object> source = new LinkedHashMap<>();
        source.put("good", repeated(s.elf)); source.put("bad", new Object());
        for (int attempt = 0; attempt < 2; attempt++) {
            try { s.context.data(source); fail("Unknown mutable payload must fail"); }
            catch (IllegalStateException expected) { assertTrue(expected.getMessage().startsWith("Unmapped snapshot payload:")); }
        }
        source.remove("bad"); Map<?, ?> result = (Map<?, ?>) s.context.data(source);
        assertSame(((List<?>) result.get("good")).get(0), s.copier.find(s.elf));
    }
    @Test public void opaqueListTraversalOccursOncePerSourceGraphNode() {
        Scene s = scene(); ObservedList source = new ObservedList(); source.add(s.elf);
        List<?> result = copiedList(s, repeated(source)); assertSame(result.get(0), result.get(1));
        assertEquals(source.iterations, 1); assertSame(((List<?>) result.get(0)).get(0), s.copier.find(s.elf));
    }
    @Test public void iteratorCallbackCanResolveInProgressContainer() {
        Scene s = scene(); ObservedList source = new ObservedList(); source.add(s.elf); Object[] observed = new Object[1];
        source.onIterator = () -> observed[0] = s.context.data(source);
        Object result = s.context.data(source); assertSame(observed[0], result); assertEquals(source.iterations, 1);
    }
    @Test public void opaqueIteratorFailureKeepsIdentityAndCanRetry() {
        Scene s = scene(); ObservedList source = new ObservedList(); source.add(s.elf);
        RuntimeException failure = new IllegalArgumentException("payload-iterator-marker"); source.failure = failure;
        for (int attempt = 0; attempt < 2; attempt++) {
            try { s.context.data(source); fail("Opaque iterator must fail"); } catch (IllegalArgumentException actual) { assertSame(actual, failure); }
        }
        source.failure = null; List<?> result = copiedList(s, source);
        assertSame(result.get(0), s.copier.find(s.elf)); assertEquals(source.iterations, 3);
    }
    @Test public void failedNestedCycleDoesNotLeaveStaleContainerCopies() {
        Scene s = scene(); List<Object> root = new ArrayList<>(); List<Object> child = new ArrayList<>();
        root.add(child); child.add(root); child.add(new Object());
        try { s.context.data(root); fail("Unknown nested payload must fail"); } catch (IllegalStateException expected) { }
        child.remove(1); List<?> result = copiedList(s, root);
        assertSame(((List<?>) result.get(0)).get(0), result); assertNotSame(result, root);
    }
    @Test public void identicalSourcesStayIndependentAcrossCopiedGames() {
        Scene s = scene(); List<Object> source = repeated(s.elf);
        List<?> a = copiedList(s, source); GameCopier other = branch(s.game); List<?> b = (List<?>) context(other).data(source);
        assertNotSame(a, b); assertSame(a.get(0), s.copier.find(s.elf)); assertSame(b.get(0), other.find(s.elf));
        assertNotSame(a.get(0), b.get(0)); assertSame(source.get(0), s.elf);
    }
    @Test public void distinctEqualContainersRemainDistinct() {
        Scene s = scene(); List<Object> a = new ArrayList<>(); List<Object> b = new ArrayList<>();
        List<Object> source = new ArrayList<>(); source.add(a); source.add(b);
        List<?> result = copiedList(s, source); assertEquals(a, b); assertNotSame(result.get(0), result.get(1));
    }
    private static final class ReentrantZoneTable extends CardZoneTable {
        Runnable onLastStateRead;
        int lastStateReads;
        @Override public forge.game.card.CardCollectionView getLastStateBattlefield() {
            lastStateReads++;
            if (onLastStateRead != null) onLastStateRead.run();
            return super.getLastStateBattlefield();
        }
    }
    @Test public void nativeZoneTableRegistersBeforeReentrantLastStateMapping() {
        Scene s = scene(); ReentrantZoneTable source = new ReentrantZoneTable();
        source.setLastStateBattlefield(cards(s.elf)); Object[] observed = new Object[1];
        source.onLastStateRead = () -> observed[0] = s.context.data(source);
        CardZoneTable result = (CardZoneTable) s.context.data(source);
        assertSame(observed[0], result); assertEquals(source.lastStateReads, 1);
        assertSame(result.getLastStateBattlefield().getFirst(), s.copier.find(s.elf));
        assertSame(s.context.data(source), result); assertEquals(source.lastStateReads, 1);
    }

}
