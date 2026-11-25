package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardTraitChanges;
import forge.game.card.CounterEnumType;
import forge.game.keyword.Keyword;
import forge.game.keyword.KeywordCollection;
import forge.game.keyword.KeywordInterface;
import forge.game.keyword.SimpleKeyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementHandler;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Actual untap checks retain live sources and consume stun counters in copied games. */
public class CommanderUntapPresenceTest extends AITest {
    private Player actor(Game game) { return game.getPlayers().getFirst(); }
    private Card land(Game game) { return actor(game).getCardsIn(ZoneType.Battlefield).getFirst(); }
    private Game setup(int depth) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("untap-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Untap presence"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 20; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        addCardToZone("Forest", actor(game), ZoneType.Battlefield).setTapped(true);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        game.getAction().checkStaticAbilities(false); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game)); copier.release();
        }
        return game;
    }
    private static final class OpaqueCollection extends KeywordCollection {
        final List<ReplacementEffect> extra = new ArrayList<>();
        @Override public List<ReplacementEffect> applyReplacementEffect(List<ReplacementEffect> list) {
            super.applyReplacementEffect(list); list.addAll(extra); return list;
        }
    }
    private static final class GetterKeyword extends SimpleKeyword {
        final List<ReplacementEffect> extra = new ArrayList<>();
        GetterKeyword() { initialize("Flying", Keyword.FLYING, ""); }
        @Override public Collection<ReplacementEffect> getReplacements() { return extra; }
    }
    private static final class ApplicatorKeyword extends SimpleKeyword {
        final List<ReplacementEffect> extra = new ArrayList<>();
        ApplicatorKeyword() { initialize("Flying", Keyword.FLYING, ""); }
        @Override public List<ReplacementEffect> applyReplacementEffect(List<ReplacementEffect> list) {
            super.applyReplacementEffect(list); list.addAll(extra); return list;
        }
    }
    private void live(String mode, int depth) {
        Game game = setup(depth); Card card = land(game);
        List<ReplacementEffect> effects = new ArrayList<>();
        KeywordCollection keywords = new KeywordCollection();
        if (mode.equals("collection")) {
            OpaqueCollection opaque = new OpaqueCollection(); effects = opaque.extra; keywords = opaque;
        } else if (mode.equals("getter")) {
            GetterKeyword keyword = new GetterKeyword(); effects = keyword.extra; keywords.insert(keyword);
        } else if (mode.equals("applicator")) {
            ApplicatorKeyword keyword = new ApplicatorKeyword(); effects = keyword.extra; keywords.insert(keyword);
        } else if (mode.equals("keyword")) {
            KeywordInterface keyword = keywords.add("Flying"); effects = (List<ReplacementEffect>) keyword.getReplacements();
        } else if (mode.equals("traits")) {
            card.addChangedCardTraits(new CardTraitChanges(List.of(), List.of(), effects, List.of(), null), game.getNextTimestamp(), 0L, false);
        } else throw new IllegalArgumentException(mode);
        card.getCurrentState().setCachedKeywords(keywords);
        assertTrue(card.canUntap(actor(game), false));
        ReplacementEffect effect = ReplacementHandler.parseReplacement("Event$ Untap | ActiveZones$ Battlefield | ValidCard$ Card.Self | Layer$ CantHappen", card, true);
        effects.add(effect);
        assertFalse(card.canUntap(actor(game), false), "Live replacement must prevent untapping");
        assertFalse(card.untap(actor(game))); assertTrue(card.isTapped());
        effects.clear();
        assertTrue(card.canUntap(actor(game), false)); assertTrue(card.untap(actor(game))); assertFalse(card.isTapped());
    }
    private void intrinsic(int depth) {
        Game game = setup(depth); Card card = land(game);
        assertTrue(card.canUntap(actor(game), false));
        card.addReplacementEffect(ReplacementHandler.parseReplacement("Event$ Untap | ActiveZones$ Battlefield | ValidCard$ Card.Self | Layer$ CantHappen", card, true));
        assertFalse(card.canUntap(actor(game), false)); assertFalse(card.untap(actor(game))); assertTrue(card.isTapped());
    }
    private void stun(int depth) {
        Game game = setup(depth); Card card = land(game);
        assertTrue(card.canUntap(actor(game), false));
        card.setCounters(CounterEnumType.STUN, 1);
        assertFalse(card.untap(actor(game))); assertTrue(card.isTapped()); assertEquals(card.getCounters(CounterEnumType.STUN), 0);
        assertTrue(card.canUntap(actor(game), false)); assertTrue(card.untap(actor(game))); assertFalse(card.isTapped());
        card.setTapped(true); card.setCounters(CounterEnumType.STUN, 1); card.setCounters(CounterEnumType.STUN, 0);
        assertTrue(card.untap(actor(game))); assertFalse(card.isTapped());
    }
    @Test public void collectionDepth0() { live("collection", 0); }
    @Test public void collectionDepth1() { live("collection", 1); }
    @Test public void collectionDepth2() { live("collection", 2); }
    @Test public void getterDepth0() { live("getter", 0); }
    @Test public void getterDepth1() { live("getter", 1); }
    @Test public void getterDepth2() { live("getter", 2); }
    @Test public void applicatorDepth0() { live("applicator", 0); }
    @Test public void applicatorDepth1() { live("applicator", 1); }
    @Test public void applicatorDepth2() { live("applicator", 2); }
    @Test public void keywordDepth0() { live("keyword", 0); }
    @Test public void keywordDepth1() { live("keyword", 1); }
    @Test public void keywordDepth2() { live("keyword", 2); }
    @Test public void traitsDepth0() { live("traits", 0); }
    @Test public void traitsDepth1() { live("traits", 1); }
    @Test public void traitsDepth2() { live("traits", 2); }
    @Test public void intrinsicDepth0() { intrinsic(0); }
    @Test public void intrinsicDepth1() { intrinsic(1); }
    @Test public void intrinsicDepth2() { intrinsic(2); }
    @Test public void stunDepth0() { stun(0); }
    @Test public void stunDepth1() { stun(1); }
    @Test public void stunDepth2() { stun(2); }
}
