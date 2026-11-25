package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
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
}
