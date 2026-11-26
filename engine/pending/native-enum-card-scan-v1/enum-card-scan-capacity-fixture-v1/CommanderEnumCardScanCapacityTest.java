package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.PlayerZone;
import forge.game.zone.ZoneType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Supplied-world collection contracts; not complete legal-game histories. */
public class CommanderEnumCardScanCapacityTest extends AITest {
    static Game newBoard() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("scan-capacity-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Scan capacity contracts"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        return game;
    }

    private Player actor(Game game) { return game.getPlayers().getFirst(); }

    private void sequence(CardCollectionView actual, Card... expected) {
        assertEquals(actual.size(), expected.length);
        for (int index = 0; index < expected.length; index++) {
            assertSame(actual.get(index), expected[index], "Card order/identity at index " + index);
        }
    }

    private static final class CallbackZone extends PlayerZone {
        private final CardCollectionView supplied;
        private final Runnable callback;
        private int reads;
        CallbackZone(ZoneType type, Player player, CardCollectionView cards, Runnable action) {
            super(type, player);
            supplied = cards;
            callback = action;
        }
        @Override public CardCollectionView getCards(boolean filter) {
            reads++;
            if (callback != null) callback.run();
            return supplied;
        }
    }

    @SuppressWarnings("unchecked")
    private void install(Player player, ZoneType type, PlayerZone zone) throws Exception {
        Field field = Player.class.getDeclaredField("zones");
        field.setAccessible(true);
        ((Map<ZoneType, PlayerZone>) field.get(player)).put(type, zone);
    }

    private CardCollection supplied(Card card) {
        CardCollection cards = new CardCollection();
        cards.add(card);
        return cards;
    }

    @Test public void emptyEnumSetReturnsFreshEmptySnapshot() {
        Game game = newBoard();
        CardCollectionView first = game.getCardsIn(EnumSet.noneOf(ZoneType.class));
        CardCollectionView second = game.getCardsIn(EnumSet.noneOf(ZoneType.class));
        assertTrue(first.isEmpty());
        assertTrue(second.isEmpty());
        assertNotSame(first, second);
    }

    @Test public void enumQueryPreservesZoneThenPlayerOrder() {
        Game game = newBoard();
        Card first = addCardToZone("Forest", actor(game), ZoneType.Hand);
        Card second = addCardToZone("Island", game.getPlayers().get(1), ZoneType.Hand);
        Card third = addCardToZone("Mountain", actor(game), ZoneType.Graveyard);
        sequence(game.getCardsIn(EnumSet.of(ZoneType.Graveyard, ZoneType.Hand)), first, second, third);
    }

    @Test public void largeQueryGrowsPastHintCapWithoutOmittingCards() {
        Game game = newBoard();
        Card[] expected = new Card[600];
        int offset = 0;
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 150; index++) {
                expected[offset++] = addCardToZone("Forest", player, ZoneType.Battlefield);
            }
        }
        sequence(game.getCardsIn(EnumSet.of(ZoneType.Battlefield)), expected);
    }

    @Test public void phasedOutCardsStayFiltered() {
        Game game = newBoard();
        Card hidden = addCardToZone("Forest", actor(game), ZoneType.Battlefield);
        Card visible = addCardToZone("Island", actor(game), ZoneType.Battlefield);
        hidden.setPhasedOut(actor(game));
        sequence(game.getCardsIn(EnumSet.of(ZoneType.Battlefield)), visible);
    }

    @Test public void sharedStackIsCollectedOnceAcrossFourPlayers() {
        Game game = newBoard();
        Card card = addCardToZone("Forest", actor(game), ZoneType.Hand);
        game.getZoneOf(card).remove(card);
        game.getStackZone().add(card);
        sequence(game.getCardsIn(EnumSet.of(ZoneType.Stack)), card);
    }

    @Test public void flashbackDerivedViewKeepsOrdinaryPath() {
        Game game = newBoard();
        Card battlefield = addCardToZone("Forest", actor(game), ZoneType.Battlefield);
        sequence(game.getCardsIn(EnumSet.of(ZoneType.Flashback, ZoneType.Battlefield)), battlefield);
    }

    @Test public void genericIterableKeepsItsOrderAndDuplicateSemantics() {
        Game game = newBoard();
        Card hand = addCardToZone("Forest", actor(game), ZoneType.Hand);
        Card grave = addCardToZone("Island", actor(game), ZoneType.Graveyard);
        int[] iterators = {0};
        Iterable<ZoneType> zones = () -> {
            iterators[0]++;
            return Arrays.asList(ZoneType.Graveyard, ZoneType.Hand, ZoneType.Graveyard).iterator();
        };
        sequence(game.getCardsIn(zones), grave, hand);
        assertEquals(iterators[0], 1);
    }

    @Test public void originalCallbackCanAddLaterEnumZone() throws Exception {
        Game game = newBoard();
        Card hand = addCardToZone("Forest", actor(game), ZoneType.Library);
        Card grave = addCardToZone("Island", actor(game), ZoneType.Graveyard);
        EnumSet<ZoneType> zones = EnumSet.of(ZoneType.Hand);
        CallbackZone custom = new CallbackZone(ZoneType.Hand, actor(game), supplied(hand),
                () -> zones.add(ZoneType.Graveyard));
        install(actor(game), ZoneType.Hand, custom);
        sequence(game.getCardsIn(zones), hand, grave);
        assertEquals(custom.reads, 1);
    }

    @Test public void originalCallbackCanRemoveLaterEnumZone() throws Exception {
        Game game = newBoard();
        Card hand = addCardToZone("Forest", actor(game), ZoneType.Library);
        addCardToZone("Island", actor(game), ZoneType.Graveyard);
        EnumSet<ZoneType> zones = EnumSet.of(ZoneType.Hand, ZoneType.Graveyard);
        CallbackZone custom = new CallbackZone(ZoneType.Hand, actor(game), supplied(hand),
                () -> zones.remove(ZoneType.Graveyard));
        install(actor(game), ZoneType.Hand, custom);
        sequence(game.getCardsIn(zones), hand);
        assertEquals(custom.reads, 1);
    }

    @Test public void opaqueZoneViewCanContainMoreThanRawSize() throws Exception {
        Game game = newBoard();
        Card card = addCardToZone("Forest", actor(game), ZoneType.Library);
        CallbackZone custom = new CallbackZone(ZoneType.Hand, actor(game), supplied(card), null);
        assertEquals(custom.size(), 0);
        install(actor(game), ZoneType.Hand, custom);
        sequence(game.getCardsIn(EnumSet.of(ZoneType.Hand)), card);
        assertEquals(custom.reads, 1);
    }

    @Test public void selectedZoneCallbacksRunOnceInPlayerOrder() throws Exception {
        Game game = newBoard();
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            final int seat = index;
            Player player = game.getPlayers().get(index);
            install(player, ZoneType.Hand, new CallbackZone(ZoneType.Hand, player,
                    new CardCollection(), () -> order.add(seat)));
        }
        assertTrue(game.getCardsIn(EnumSet.of(ZoneType.Hand)).isEmpty());
        assertEquals(order, Arrays.asList(0, 1, 2, 3));
    }

    @Test public void returnedSnapshotRemainsMutableWithoutChangingZone() {
        Game game = newBoard();
        Card card = addCardToZone("Forest", actor(game), ZoneType.Hand);
        CardCollection result = (CardCollection) game.getCardsIn(EnumSet.of(ZoneType.Hand));
        assertTrue(result.remove(card));
        assertTrue(result.isEmpty());
        sequence(actor(game).getCardsIn(ZoneType.Hand), card);
    }

    @Test public void zoneMutationDoesNotChangeEarlierSnapshot() {
        Game game = newBoard();
        Card card = addCardToZone("Forest", actor(game), ZoneType.Hand);
        CardCollectionView before = game.getCardsIn(EnumSet.of(ZoneType.Hand));
        Card later = addCardToZone("Island", actor(game), ZoneType.Hand);
        sequence(before, card);
        sequence(game.getCardsIn(EnumSet.of(ZoneType.Hand)), card, later);
    }

    @Test public void separateQueryResultsRemainIndependent() {
        Game game = newBoard();
        Card card = addCardToZone("Forest", actor(game), ZoneType.Hand);
        CardCollection first = (CardCollection) game.getCardsIn(EnumSet.of(ZoneType.Hand));
        CardCollectionView second = game.getCardsIn(EnumSet.of(ZoneType.Hand));
        first.clear();
        sequence(second, card);
    }

    @Test public void nullIterableStillReturnsEmptyView() {
        assertTrue(newBoard().getCardsIn((Iterable<ZoneType>) null).isEmpty());
    }

    @Test public void inactivePlayerCardsStayOutsideQuery() {
        Game game = newBoard();
        Card active = addCardToZone("Forest", actor(game), ZoneType.Hand);
        Player inactive = game.getPlayers().get(3);
        addCardToZone("Island", inactive, ZoneType.Hand);
        game.getPlayers().remove(inactive);
        sequence(game.getCardsIn(EnumSet.of(ZoneType.Hand)), active);
    }

    @Test public void realStaticSourceSetKeepsZoneOrderAndSharedStack() {
        Game game = newBoard();
        Card grave = addCardToZone("Forest", actor(game), ZoneType.Graveyard);
        Card field = addCardToZone("Island", actor(game), ZoneType.Battlefield);
        Card exile = addCardToZone("Mountain", actor(game), ZoneType.Exile);
        Card command = addCardToZone("Swamp", actor(game), ZoneType.Command);
        Card stack = addCardToZone("Plains", actor(game), ZoneType.Hand);
        game.getZoneOf(stack).remove(stack);
        game.getStackZone().add(stack);
        sequence(game.getCardsIn(EnumSet.copyOf(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)),
                grave, field, exile, command, stack);
    }
}
