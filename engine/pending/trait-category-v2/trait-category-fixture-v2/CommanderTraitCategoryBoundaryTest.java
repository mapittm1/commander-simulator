package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.card.CardTraitChanges;
import forge.game.card.ICardTraitChanges;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityDevotion;
import java.util.concurrent.atomic.AtomicBoolean;
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

/** Static-category absence must preserve native list mutation and opaque callbacks. */
public class CommanderTraitCategoryBoundaryTest extends AITest {
    private Player actor(Game game) { return game.getPlayers().getFirst(); }

    private Game board(String spell) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("spell-game-history-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Game-long spell history"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setStartingLife(40);
            for (int index = 0; index < 20; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Island", actor(game), ZoneType.Battlefield);
            addCardToZone("Plains", actor(game), ZoneType.Battlefield);
        }

        if (spell != null) addCardToZone(spell, actor(game), ZoneType.Hand);
        game.getAction().checkStaticAbilities(false);
        game.getTriggerHandler().resetActiveTriggers();
        return game;
    }

    private Card named(Player player, ZoneType zone, String name) {
        for (Card card : player.getCardsIn(zone)) if (card.getName().equals(name)) return card;
        throw new AssertionError("Missing supplied card: " + name);
    }

    private Game copy(Game game, int depth) {
        for (int index = 0; index < depth; index++) {
            game.getAction().checkStaticAbilities(false);
            game.getTriggerHandler().resetActiveTriggers();
            game.copyLastState();
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game));
            copier.release();
        }
        return game;
    }

    private static final class ObservableList extends ArrayList<StaticAbility> {
        int reads;
        @Override public boolean isEmpty() { reads++; return super.isEmpty(); }
    }

    private void assertDevotion(Game game, Card host, StaticAbility effect, int expected) {
        assertEquals(host.getStaticAbilities().contains(effect), expected != 0);
        assertEquals(StaticAbilityDevotion.getDevotionMod(actor(game)), expected);
    }

    private void check(String mode, int depth) {
        Game game = copy(board(null), depth);
        Card host = actor(game).getCardsIn(ZoneType.Battlefield).getFirst();
        StaticAbility effect = new StaticAbility("Mode$ Devotion | ValidPlayer$ You | Value$ 2",
                host, host.getCurrentState());
        List<StaticAbility> list = mode.equals("opaqueList") ? new ObservableList() : new ArrayList<>();
        AtomicBoolean active = new AtomicBoolean();
        int[] callbacks = {0};
        if (mode.equals("callback")) {
            host.addChangedCardTraits(new ICardTraitChanges() {
                @Override public List<StaticAbility> applyStaticAbility(List<StaticAbility> result) {
                    callbacks[0]++;
                    if (active.get()) result.add(effect);
                    return result;
                }
                @Override public ICardTraitChanges copy(Card newHost, boolean lki) { return this; }
            }, game.getNextTimestamp(), 0L, false);
        } else if (mode.equals("predicate")) {
            host.addStaticAbility(effect);
            host.addChangedCardTraits(new CardTraitChanges(null, null, null, null,
                    ignored -> !active.get()), game.getNextTimestamp(), 0L, false);
        } else {
            host.addChangedCardTraits(new CardTraitChanges(null, null, null, list, null),
                    game.getNextTimestamp(), 0L, false);
        }
        assertDevotion(game, host, effect, 0);
        if (mode.equals("opaqueList")) assertTrue(((ObservableList) list).reads > 0,
                "Empty opaque collections retain their native reads");
        if (mode.equals("callback")) assertTrue(callbacks[0] > 0,
                "Empty opaque applicators are still called");
        active.set(true);
        list.add(effect);
        assertDevotion(game, host, effect, 2);
        active.set(false);
        list.clear();
        assertDevotion(game, host, effect, 0);
        active.set(true);
        list.add(effect);
        assertDevotion(game, host, effect, 2);
    }

    @Test public void nativeMutableListDepth0() { check("nativeList", 0); }
    @Test public void nativeMutableListDepth1() { check("nativeList", 1); }
    @Test public void nativeMutableListDepth2() { check("nativeList", 2); }
    @Test public void opaqueMutableListDepth0() { check("opaqueList", 0); }
    @Test public void opaqueMutableListDepth1() { check("opaqueList", 1); }
    @Test public void opaqueMutableListDepth2() { check("opaqueList", 2); }
    @Test public void opaqueTraitCallbackDepth0() { check("callback", 0); }
    @Test public void opaqueTraitCallbackDepth1() { check("callback", 1); }
    @Test public void opaqueTraitCallbackDepth2() { check("callback", 2); }
    @Test public void liveRemovalPredicateDepth0() { check("predicate", 0); }
    @Test public void liveRemovalPredicateDepth1() { check("predicate", 1); }
    @Test public void liveRemovalPredicateDepth2() { check("predicate", 2); }

    private void callbackMutationOrdering(int depth) {
        Game game = copy(board(null), depth);
        Card host = actor(game).getCardsIn(ZoneType.Battlefield).getFirst();
        StaticAbility effect = new StaticAbility("Mode$ Devotion | ValidPlayer$ You | Value$ 2",
                host, host.getCurrentState());
        List<StaticAbility> values = new ArrayList<StaticAbility>() {
            private boolean fired;
            @Override public boolean isEmpty() {
                if (!fired) {
                    fired = true;
                    host.addStaticAbility(effect);
                }
                return super.isEmpty();
            }
        };
        host.addChangedCardTraits(new CardTraitChanges(null, null, null, values, null),
                game.getNextTimestamp(), 0L, false);
        assertFalse(host.getStaticAbilities().contains(effect),
                "Native gathering copies intrinsic abilities before reading the opaque source");
        assertTrue(host.getStaticAbilities().contains(effect),
                "The callback's intrinsic addition appears on the next query");
        assertEquals(StaticAbilityDevotion.getDevotionMod(actor(game)), 2);
    }

    @Test public void opaqueCollectionMutationOrderingDepth0() { callbackMutationOrdering(0); }
    @Test public void opaqueCollectionMutationOrderingDepth1() { callbackMutationOrdering(1); }
    @Test public void opaqueCollectionMutationOrderingDepth2() { callbackMutationOrdering(2); }
}
