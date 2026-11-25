package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.card.CardType;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CardState;
import forge.game.card.CardTraitChanges;
import forge.game.keyword.Keyword;
import forge.game.keyword.KeywordCollection;
import forge.game.keyword.KeywordInterface;
import forge.game.keyword.SimpleKeyword;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityDevotion;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerHandler;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Native registry/devotion gathering must see live traits after empty reads. */
public class CommanderTraitGatherTest extends AITest {
    private Player actor(Game game) { return game.getPlayers().getFirst(); }
    private Game setup(int depth) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("trait-gather-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Live trait gathering"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int i = 0; i < 20; i++) addCardToZone("Forest", player, ZoneType.Library);
        }
        addCardToZone("Forest", actor(game), ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        game.getAction().checkStaticAbilities(false); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        for (int i = 0; i < depth; i++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game)); copier.release();
        }
        return game;
    }
    private static final class OpaqueKeywords extends KeywordCollection {
        final List<Trigger> triggerTraits = new ArrayList<>();
        final List<StaticAbility> staticTraits = new ArrayList<>();
        @Override public List<Trigger> applyTrigger(List<Trigger> list) {
            super.applyTrigger(list); list.addAll(triggerTraits); return list;
        }
        @Override public List<StaticAbility> applyStaticAbility(List<StaticAbility> list) {
            super.applyStaticAbility(list); list.addAll(staticTraits); return list;
        }
    }
    private static final class GetterKeyword extends SimpleKeyword {
        final List<Trigger> triggerTraits = new ArrayList<>();
        final List<StaticAbility> staticTraits = new ArrayList<>();
        GetterKeyword() { initialize("Flying", Keyword.FLYING, ""); }
        @Override public Collection<Trigger> getTriggers() { return triggerTraits; }
        @Override public Collection<StaticAbility> getStaticAbilities() { return staticTraits; }
    }
    private static final class ApplicatorKeyword extends SimpleKeyword {
        final List<Trigger> triggerTraits = new ArrayList<>();
        final List<StaticAbility> staticTraits = new ArrayList<>();
        ApplicatorKeyword() { initialize("Flying", Keyword.FLYING, ""); }
        @Override public List<Trigger> applyTrigger(List<Trigger> list) {
            super.applyTrigger(list); list.addAll(triggerTraits); return list;
        }
        @Override public List<StaticAbility> applyStaticAbility(List<StaticAbility> list) {
            super.applyStaticAbility(list); list.addAll(staticTraits); return list;
        }
    }
    private static final class CallbackCard extends Card {
        final List<Trigger> triggerTraits = new ArrayList<>();
        final List<StaticAbility> staticTraits = new ArrayList<>();
        CallbackCard(Game game) { super(game.nextCardId(), game); }
        @Override public void updateTriggers(List<Trigger> list, CardState state) {
            super.updateTriggers(list, state); list.addAll(triggerTraits);
        }
        @Override public void updateStaticAbilities(List<StaticAbility> list, CardState state) {
            super.updateStaticAbilities(list, state); list.addAll(staticTraits);
        }
    }
    private void absent(Game game, Card host, boolean trigger) {
        if (trigger) {
            game.getTriggerHandler().resetActiveTriggers();
            assertTrue(game.getTriggerHandler().getActiveTrigger(TriggerType.Taps, AbilityKey.mapFromCard(host)).isEmpty());
        } else assertEquals(StaticAbilityDevotion.getDevotionMod(actor(game)), 0);
    }
    private void present(Game game, Card host, boolean trigger, Trigger event) {
        if (trigger) {
            game.getTriggerHandler().resetActiveTriggers();
            assertEquals(game.getTriggerHandler().getActiveTrigger(TriggerType.Taps, AbilityKey.mapFromCard(host)), List.of(event));
        } else {
            assertEquals(StaticAbilityDevotion.getDevotionMod(actor(game)), 2);
            assertEquals(StaticAbilityDevotion.getDevotionMod(game.getPlayers().get(1)), 0);
        }
    }
    private void check(String mode, boolean trigger, int depth) {
        Game game = setup(depth); Card host = actor(game).getCardsIn(ZoneType.Battlefield).getFirst();
        List<Trigger> triggers = new ArrayList<>(); List<StaticAbility> statics = new ArrayList<>();
        KeywordCollection keywords = new KeywordCollection();
        if (mode.equals("callback")) {
            CallbackCard card = new CallbackCard(game); card.setOwner(actor(game)); card.setName("Trait callback control");
            card.getCurrentState().setType(CardType.parse("Artifact", false)); actor(game).getZone(ZoneType.Battlefield).add(card);
            host = card; triggers = card.triggerTraits; statics = card.staticTraits;
        } else if (mode.equals("collection")) {
            OpaqueKeywords collection = new OpaqueKeywords(); keywords = collection; triggers = collection.triggerTraits; statics = collection.staticTraits;
        } else if (mode.equals("getter")) {
            GetterKeyword keyword = new GetterKeyword(); keywords.insert(keyword); triggers = keyword.triggerTraits; statics = keyword.staticTraits;
        } else if (mode.equals("applicator")) {
            ApplicatorKeyword keyword = new ApplicatorKeyword(); keywords.insert(keyword); triggers = keyword.triggerTraits; statics = keyword.staticTraits;
        } else if (mode.equals("keyword")) {
            KeywordInterface keyword = keywords.add("Flying"); triggers = (List<Trigger>) keyword.getTriggers(); statics = (List<StaticAbility>) keyword.getStaticAbilities();
        } else if (mode.equals("traits") || mode.equals("text")) {
            CardTraitChanges traits = new CardTraitChanges(List.of(), triggers, List.of(), statics, null);
            if (mode.equals("text")) host.getChangedCardTraitsByText().put(game.getNextTimestamp(), 0L, traits);
            else host.addChangedCardTraits(traits, game.getNextTimestamp(), 0L, false);
        } else if (!mode.equals("intrinsic")) throw new IllegalArgumentException(mode);
        host.getCurrentState().setCachedKeywords(keywords);
        Trigger event = TriggerHandler.parseTrigger("Mode$ Taps | ValidCard$ Card.Self | TriggerZones$ Battlefield", host, true);
        StaticAbility devotion = new StaticAbility("Mode$ Devotion | ValidPlayer$ You | Value$ 2", host, host.getCurrentState());
        absent(game, host, trigger);
        if (mode.equals("intrinsic")) {
            if (trigger) host.addTrigger(event); else host.addStaticAbility(devotion);
        } else { triggers.add(event); statics.add(devotion); }
        present(game, host, trigger, event);
        if (mode.equals("intrinsic")) {
            if (trigger) event.setSuppressed(true); else assertTrue(host.getCurrentState().removeStaticAbility(devotion));
        } else { triggers.clear(); statics.clear(); }
        absent(game, host, trigger);
        if (mode.equals("intrinsic")) {
            if (trigger) event.setSuppressed(false); else host.addStaticAbility(devotion);
        } else { triggers.add(event); statics.add(devotion); }
        present(game, host, trigger, event);
    }
    @Test public void triggerIntrinsicDepth0() { check("intrinsic", true, 0); }
    @Test public void triggerIntrinsicDepth1() { check("intrinsic", true, 1); }
    @Test public void triggerIntrinsicDepth2() { check("intrinsic", true, 2); }
    @Test public void triggerKeywordDepth0() { check("keyword", true, 0); }
    @Test public void triggerKeywordDepth1() { check("keyword", true, 1); }
    @Test public void triggerKeywordDepth2() { check("keyword", true, 2); }
    @Test public void triggerCollectionDepth0() { check("collection", true, 0); }
    @Test public void triggerCollectionDepth1() { check("collection", true, 1); }
    @Test public void triggerCollectionDepth2() { check("collection", true, 2); }
    @Test public void triggerGetterDepth0() { check("getter", true, 0); }
    @Test public void triggerGetterDepth1() { check("getter", true, 1); }
    @Test public void triggerGetterDepth2() { check("getter", true, 2); }
    @Test public void triggerApplicatorDepth0() { check("applicator", true, 0); }
    @Test public void triggerApplicatorDepth1() { check("applicator", true, 1); }
    @Test public void triggerApplicatorDepth2() { check("applicator", true, 2); }
    @Test public void triggerTraitsDepth0() { check("traits", true, 0); }
    @Test public void triggerTraitsDepth1() { check("traits", true, 1); }
    @Test public void triggerTraitsDepth2() { check("traits", true, 2); }
    @Test public void triggerTextDepth0() { check("text", true, 0); }
    @Test public void triggerTextDepth1() { check("text", true, 1); }
    @Test public void triggerTextDepth2() { check("text", true, 2); }
    @Test public void triggerCallbackDepth0() { check("callback", true, 0); }
    @Test public void triggerCallbackDepth1() { check("callback", true, 1); }
    @Test public void triggerCallbackDepth2() { check("callback", true, 2); }
    @Test public void devotionIntrinsicDepth0() { check("intrinsic", false, 0); }
    @Test public void devotionIntrinsicDepth1() { check("intrinsic", false, 1); }
    @Test public void devotionIntrinsicDepth2() { check("intrinsic", false, 2); }
    @Test public void devotionKeywordDepth0() { check("keyword", false, 0); }
    @Test public void devotionKeywordDepth1() { check("keyword", false, 1); }
    @Test public void devotionKeywordDepth2() { check("keyword", false, 2); }
    @Test public void devotionCollectionDepth0() { check("collection", false, 0); }
    @Test public void devotionCollectionDepth1() { check("collection", false, 1); }
    @Test public void devotionCollectionDepth2() { check("collection", false, 2); }
    @Test public void devotionGetterDepth0() { check("getter", false, 0); }
    @Test public void devotionGetterDepth1() { check("getter", false, 1); }
    @Test public void devotionGetterDepth2() { check("getter", false, 2); }
    @Test public void devotionApplicatorDepth0() { check("applicator", false, 0); }
    @Test public void devotionApplicatorDepth1() { check("applicator", false, 1); }
    @Test public void devotionApplicatorDepth2() { check("applicator", false, 2); }
    @Test public void devotionTraitsDepth0() { check("traits", false, 0); }
    @Test public void devotionTraitsDepth1() { check("traits", false, 1); }
    @Test public void devotionTraitsDepth2() { check("traits", false, 2); }
    @Test public void devotionTextDepth0() { check("text", false, 0); }
    @Test public void devotionTextDepth1() { check("text", false, 1); }
    @Test public void devotionTextDepth2() { check("text", false, 2); }
    @Test public void devotionCallbackDepth0() { check("callback", false, 0); }
    @Test public void devotionCallbackDepth1() { check("callback", false, 1); }
    @Test public void devotionCallbackDepth2() { check("callback", false, 2); }
}
