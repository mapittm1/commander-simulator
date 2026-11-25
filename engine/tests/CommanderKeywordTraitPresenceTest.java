package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.card.CardType;
import forge.game.keyword.Keyword;
import forge.game.keyword.SimpleKeyword;
import forge.game.card.CardState;
import forge.game.keyword.KeywordCollection;
import forge.game.staticability.StaticAbility;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
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

/** Opaque keyword applicators must remain visible on original and copied cards. */
public class CommanderKeywordTraitPresenceTest extends AITest {
    private Player actor(Game game) {
        return game.getRegisteredPlayers().stream()
                .filter(player -> player.getName().equals("presence-0")).findFirst().orElseThrow();
    }

    private Game setup() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("presence-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Live replacement sources"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 20; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        addCardToZone("Forest", actor(game), ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        game.getAction().checkStaticAbilities(false);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        return game;
    }

    private Card forest(Game game) {
        return actor(game).getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Forest")).findFirst().orElseThrow();
    }

    private ReplacementEffect preventTap(Card host, String valid) {
        return ReplacementHandler.parseReplacement("Event$ Tap | ActiveZones$ Battlefield | ValidCard$ "
                + valid + " | Layer$ CantHappen", host, true);
    }

    private static final class OpaqueKeywords extends KeywordCollection {
        private StaticAbility additionalStatic;
        private ReplacementEffect additionalReplacement;

        @Override public List<StaticAbility> applyStaticAbility(List<StaticAbility> list) {
            super.applyStaticAbility(list);
            if (additionalStatic != null) list.add(additionalStatic);
            return list;
        }

        @Override public List<ReplacementEffect> applyReplacementEffect(List<ReplacementEffect> list) {
            super.applyReplacementEffect(list);
            if (additionalReplacement != null) list.add(additionalReplacement);
            return list;
        }
    }

    private void check(boolean statics, int depth) {
        Game original = setup();
        Game game = original;
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game));
            copier.release();
        }
        Card land = forest(game);
        CardState state = land.getCurrentState();
        OpaqueKeywords keywords = new OpaqueKeywords();
        state.setCachedKeywords(keywords);
        assertTrue(keywords.isEmpty());
        assertTrue(land.getStaticAbilities().isEmpty());
        assertTrue(land.getReplacementEffects().isEmpty());
        if (statics) {
            StaticAbility ban = new StaticAbility("Mode$ CantBeActivated | AffectedZone$ Battlefield | ValidCard$ Card.Self | ValidSA$ Activated", land, state);
            keywords.additionalStatic = ban;
            assertTrue(land.getStaticAbilities().contains(ban), "Opaque keyword static callback");
            keywords.additionalStatic = null;
            assertFalse(land.getStaticAbilities().contains(ban));
        } else {
            ReplacementEffect prevention = preventTap(land, "Card.Self");
            keywords.additionalReplacement = prevention;
            assertFalse(land.canTap(), "Opaque keyword replacement must prevent tapping");
            assertTrue(land.getReplacementEffects().contains(prevention));
            keywords.additionalReplacement = null;
            assertTrue(land.canTap());
            assertFalse(land.getReplacementEffects().contains(prevention));
        }
        if (depth > 0) {
            assertTrue(forest(original).canTap());
            assertTrue(forest(original).getStaticAbilities().isEmpty());
        }
    }
    @Test public void opaqueStaticDepth0() { check(true, 0); }
    @Test public void opaqueStaticDepth1() { check(true, 1); }
    @Test public void opaqueStaticDepth2() { check(true, 2); }
    @Test public void opaqueReplacementDepth0() { check(false, 0); }
    @Test public void opaqueReplacementDepth1() { check(false, 1); }
    @Test public void opaqueReplacementDepth2() { check(false, 2); }

    private static final class GetterKeyword extends SimpleKeyword {
        private StaticAbility additionalStatic;
        private ReplacementEffect additionalReplacement;

        GetterKeyword() { initialize("Flying", Keyword.FLYING, ""); }

        @Override public Collection<StaticAbility> getStaticAbilities() {
            return additionalStatic == null ? List.of() : List.of(additionalStatic);
        }

        @Override public Collection<ReplacementEffect> getReplacements() {
            return additionalReplacement == null ? List.of() : List.of(additionalReplacement);
        }
    }

    private static final class ApplicatorKeyword extends SimpleKeyword {
        private StaticAbility additionalStatic;
        private ReplacementEffect additionalReplacement;

        ApplicatorKeyword() { initialize("Flying", Keyword.FLYING, ""); }

        @Override public List<StaticAbility> applyStaticAbility(List<StaticAbility> list) {
            super.applyStaticAbility(list);
            if (additionalStatic != null) list.add(additionalStatic);
            return list;
        }

        @Override public List<ReplacementEffect> applyReplacementEffect(List<ReplacementEffect> list) {
            super.applyReplacementEffect(list);
            if (additionalReplacement != null) list.add(additionalReplacement);
            return list;
        }
    }

    private Game copiedSetup(int depth) {
        Game game = setup();
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game));
            copier.release();
        }
        return game;
    }

    private void checkKeywordOverride(boolean statics, boolean getter, int depth) {
        Game game = copiedSetup(depth);
        Card land = forest(game);
        CardState state = land.getCurrentState();
        GetterKeyword getterKeyword = new GetterKeyword();
        ApplicatorKeyword applicatorKeyword = new ApplicatorKeyword();
        KeywordCollection keywords = new KeywordCollection();
        keywords.insert(getter ? getterKeyword : applicatorKeyword);
        state.setCachedKeywords(keywords);
        assertTrue(state.hasKeyword(Keyword.FLYING));
        assertTrue(land.getStaticAbilities().isEmpty());
        assertTrue(land.getReplacementEffects().isEmpty());
        if (statics) {
            StaticAbility ban = new StaticAbility("Mode$ CantBeActivated | AffectedZone$ Battlefield | ValidCard$ Card.Self | ValidSA$ Activated", land, state);
            if (getter) getterKeyword.additionalStatic = ban;
            else applicatorKeyword.additionalStatic = ban;
            assertTrue(land.getStaticAbilities().contains(ban), "Opaque keyword instance static source");
            getterKeyword.additionalStatic = null;
            applicatorKeyword.additionalStatic = null;
            assertFalse(land.getStaticAbilities().contains(ban));
        } else {
            ReplacementEffect prevention = preventTap(land, "Card.Self");
            if (getter) getterKeyword.additionalReplacement = prevention;
            else applicatorKeyword.additionalReplacement = prevention;
            assertFalse(land.canTap(), "Opaque keyword instance replacement source");
            assertTrue(land.getReplacementEffects().contains(prevention));
            getterKeyword.additionalReplacement = null;
            applicatorKeyword.additionalReplacement = null;
            assertTrue(land.canTap());
            assertFalse(land.getReplacementEffects().contains(prevention));
        }
    }

    private void checkReadAhead(boolean gain, boolean rulesHost, int depth) {
        Game game = copiedSetup(depth);
        CardState state = forest(game).getCurrentState();
        state.setType(CardType.parse("Enchantment Saga", false));
        state.updateTypes();
        KeywordCollection keywords = new KeywordCollection();
        // Keyword values only: test Saga's implicit condition without keyword-owned replacements.
        if (!gain) keywords.add("Read ahead");
        state.setCachedKeywords(keywords);
        ReplacementEffect saga = state.getSagaRep();
        assertEquals(state.hasKeyword(Keyword.READ_AHEAD), !gain);
        assertEquals(state.getReplacementEffects(rulesHost).contains(saga), gain);
        if (gain) keywords.add("Read ahead");
        else keywords.removeAll(Keyword.READ_AHEAD);
        assertEquals(state.hasKeyword(Keyword.READ_AHEAD), gain);
        assertEquals(state.getReplacementEffects(rulesHost).contains(saga), !gain);
        if (gain) keywords.removeAll(Keyword.READ_AHEAD);
        else keywords.add("Read ahead");
        assertEquals(state.getReplacementEffects(rulesHost).contains(saga), gain);
    }
    @Test public void getterStaticDepth0() { checkKeywordOverride(true, true, 0); }
    @Test public void getterStaticDepth1() { checkKeywordOverride(true, true, 1); }
    @Test public void getterStaticDepth2() { checkKeywordOverride(true, true, 2); }
    @Test public void getterReplacementDepth0() { checkKeywordOverride(false, true, 0); }
    @Test public void getterReplacementDepth1() { checkKeywordOverride(false, true, 1); }
    @Test public void getterReplacementDepth2() { checkKeywordOverride(false, true, 2); }
    @Test public void applicatorStaticDepth0() { checkKeywordOverride(true, false, 0); }
    @Test public void applicatorStaticDepth1() { checkKeywordOverride(true, false, 1); }
    @Test public void applicatorStaticDepth2() { checkKeywordOverride(true, false, 2); }
    @Test public void applicatorReplacementDepth0() { checkKeywordOverride(false, false, 0); }
    @Test public void applicatorReplacementDepth1() { checkKeywordOverride(false, false, 1); }
    @Test public void applicatorReplacementDepth2() { checkKeywordOverride(false, false, 2); }
    @Test public void readAheadGainRulesDepth0() { checkReadAhead(true, true, 0); }
    @Test public void readAheadGainRulesDepth1() { checkReadAhead(true, true, 1); }
    @Test public void readAheadGainRulesDepth2() { checkReadAhead(true, true, 2); }
    @Test public void readAheadGainNoRulesDepth0() { checkReadAhead(true, false, 0); }
    @Test public void readAheadGainNoRulesDepth1() { checkReadAhead(true, false, 1); }
    @Test public void readAheadGainNoRulesDepth2() { checkReadAhead(true, false, 2); }
    @Test public void readAheadRemoveRulesDepth0() { checkReadAhead(false, true, 0); }
    @Test public void readAheadRemoveRulesDepth1() { checkReadAhead(false, true, 1); }
    @Test public void readAheadRemoveRulesDepth2() { checkReadAhead(false, true, 2); }
    @Test public void readAheadRemoveNoRulesDepth0() { checkReadAhead(false, false, 0); }
    @Test public void readAheadRemoveNoRulesDepth1() { checkReadAhead(false, false, 1); }
    @Test public void readAheadRemoveNoRulesDepth2() { checkReadAhead(false, false, 2); }
}
