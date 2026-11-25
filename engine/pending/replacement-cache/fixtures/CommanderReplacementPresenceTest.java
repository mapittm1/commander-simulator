package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.game.ability.AbilityFactory;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerHandler;
import forge.deck.Deck;
import forge.card.CardStateName;
import forge.game.card.CardState;
import forge.game.staticability.StaticAbility;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardTraitChanges;
import forge.game.cost.CostPayment;
import forge.game.keyword.KeywordInterface;
import forge.game.mana.Mana;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementHandler;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollection;
import forge.util.collect.FCollectionView;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Live replacement sources must remain visible across empty/present transitions. */
public class CommanderReplacementPresenceTest extends AITest {
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

    private void blocked(Card land, Player payer) {
        assertFalse(land.canTap());
        SpellAbility mana = land.getManaAbilities().get(0);
        mana.setActivatingPlayer(payer);
        assertFalse(new CostPayment(mana.getPayCosts(), mana)
                .payComputerCosts(new AiCostDecision(payer, mana, false, true)));
        assertFalse(land.isTapped());
        assertEquals(payer.getManaPool().totalMana(), 0);
    }

    /** An override may expose replacements absent from CardState's intrinsic list. */
    private static final class ReplacementOverrideCard extends Card {
        private final FCollection<ReplacementEffect> effects = new FCollection<>();

        ReplacementOverrideCard(Game game) {
            super(game.nextCardId(), game);
        }

        @Override public FCollectionView<ReplacementEffect> getReplacementEffects() {
            return effects;
        }
    }

    private StaticAbility activationBan(Card host) {
        return new StaticAbility("Mode$ CantBeActivated | AffectedZone$ Battlefield | ValidCard$ Card.Self | ValidSA$ Activated",
                host, host.getCurrentState());
    }

    private void activationAllowed(Card card, Player payer, boolean expected) {
        SpellAbility mana = card.getManaAbilities().get(0);
        mana.setActivatingPlayer(payer);
        assertEquals(mana.canPlay() && mana.checkRestrictions(payer), expected);
    }

    private void checkStatics(String mode, Game game, Card land, Player payer) {
        if (mode.equals("staticLeftSplit") || mode.equals("staticRightSplit")) {
            Card split = addCardToZone("Fire // Ice", payer, ZoneType.Hand);
            CardState original = split.getState(CardStateName.Original);
            CardState half = split.getState(mode.equals("staticLeftSplit") ? CardStateName.LeftSplit : CardStateName.RightSplit);
            StaticAbility ban = activationBan(split);
            assertFalse(original.getStaticAbilities().contains(ban));
            half.addStaticAbility(ban);
            assertTrue(original.getStaticAbilities().contains(ban));
            assertTrue(half.removeStaticAbility(ban));
            assertFalse(original.getStaticAbilities().contains(ban));
            return;
        }
        assertTrue(land.getStaticAbilities().isEmpty());
        activationAllowed(land, payer, true);
        StaticAbility ban = activationBan(land);
        if (mode.equals("staticMutable")) {
            FCollectionView<StaticAbility> returned = land.getStaticAbilities();
            assertTrue(returned instanceof FCollection<?>);
            @SuppressWarnings("unchecked")
            FCollection<StaticAbility> mutable = (FCollection<StaticAbility>) returned;
            assertTrue(mutable.add(ban));
            assertTrue(mutable.contains(ban));
            assertTrue(land.getStaticAbilities().isEmpty());
            assertTrue(FCollection.<StaticAbility>getEmpty().isEmpty());
            activationAllowed(land, payer, true);
            mutable.clear();
            return;
        }
        long timestamp = game.getNextTimestamp();
        KeywordInterface keyword = null;
        if (mode.equals("staticIntrinsic")) land.getCurrentState().addStaticAbility(ban);
        else if (mode.equals("staticKeyword")) {
            keyword = land.addIntrinsicKeyword("Flying");
            keyword.getStaticAbilities().add(ban);
            land.updateKeywordsCache();
        } else {
            CardTraitChanges change = new CardTraitChanges(List.of(), List.of(), List.of(), List.of(ban), null);
            if (mode.equals("staticTextGranted")) land.getChangedCardTraitsByText().put(timestamp, 0L, change);
            else {
                assertEquals(mode, "staticGranted");
                land.addChangedCardTraits(change, timestamp, 0L, false);
            }
        }
        assertTrue(land.getStaticAbilities().contains(ban));
        activationAllowed(land, payer, false);
        if (mode.equals("staticIntrinsic")) assertTrue(land.getCurrentState().removeStaticAbility(ban));
        else if (mode.equals("staticKeyword")) {
            assertTrue(keyword.getStaticAbilities().remove(ban));
            land.updateKeywordsCache();
        } else if (mode.equals("staticTextGranted")) assertTrue(land.removeChangedCardTraitsByText(timestamp, 0L));
        else assertTrue(land.removeChangedCardTraits(timestamp, 0L));
        assertFalse(land.getStaticAbilities().contains(ban));
        activationAllowed(land, payer, true);
    }

    private Trigger manaTrigger(Card host) {
        Trigger trigger = TriggerHandler.parseTrigger(
                "Mode$ TapsForMana | ValidCard$ Card.Self | TriggerZones$ Battlefield", host, true);
        trigger.setOverridingAbility(AbilityFactory.getAbility("DB$ GainLife | Defined$ You | LifeAmount$ 1", host));
        return trigger;
    }

    private int checkTriggers(String mode, Game game, Card land) {
        if (mode.equals("triggerLeftSplit") || mode.equals("triggerRightSplit")) {
            Card split = addCardToZone("Fire // Ice", actor(game), ZoneType.Hand);
            CardState original = split.getState(CardStateName.Original);
            CardState half = split.getState(mode.equals("triggerLeftSplit") ? CardStateName.LeftSplit : CardStateName.RightSplit);
            Trigger trigger = manaTrigger(split);
            assertFalse(original.getTriggers().contains(trigger));
            half.addTrigger(trigger);
            assertTrue(original.getTriggers().contains(trigger));
            FCollectionView<Trigger> returned = original.getTriggers();
            ((FCollection<Trigger>) returned).clear();
            assertTrue(original.getTriggers().contains(trigger));
            return 100;
        }
        assertTrue(land.getTriggers().isEmpty());
        Trigger trigger = manaTrigger(land);
        if (mode.equals("triggerMutable")) {
            FCollectionView<Trigger> returned = land.getTriggers();
            assertTrue(returned instanceof FCollection<?>);
            FCollection<Trigger> mutable = (FCollection<Trigger>) returned;
            assertTrue(mutable.add(trigger));
            assertTrue(mutable.contains(trigger));
            assertTrue(land.getTriggers().isEmpty());
            assertTrue(FCollection.<Trigger>getEmpty().isEmpty());
            return 100;
        }
        long timestamp = game.getNextTimestamp();
        if (mode.equals("triggerIntrinsic")) land.addTrigger(trigger);
        else if (mode.equals("triggerKeyword")) {
            KeywordInterface keyword = land.addIntrinsicKeyword("Flying");
            keyword.getTriggers().add(trigger);
            land.updateKeywordsCache();
            assertTrue(land.getTriggers().contains(trigger));
            assertTrue(keyword.getTriggers().remove(trigger));
            land.updateKeywordsCache();
            assertFalse(land.getTriggers().contains(trigger));
            keyword.getTriggers().add(trigger);
            land.updateKeywordsCache();
        } else {
            CardTraitChanges change = new CardTraitChanges(List.of(), List.of(trigger), List.of(), List.of(), null);
            if (mode.equals("triggerTextGranted")) {
                land.getChangedCardTraitsByText().put(timestamp, 0L, change);
                assertTrue(land.getTriggers().contains(trigger));
                assertTrue(land.removeChangedCardTraitsByText(timestamp, 0L));
                assertFalse(land.getTriggers().contains(trigger));
                land.getChangedCardTraitsByText().put(timestamp, 0L, change);
            } else {
                assertEquals(mode, "triggerGranted");
                land.addChangedCardTraits(change, timestamp, 0L, false);
                assertTrue(land.getTriggers().contains(trigger));
                assertTrue(land.removeChangedCardTraits(timestamp, 0L));
                assertFalse(land.getTriggers().contains(trigger));
                land.addChangedCardTraits(change, timestamp, 0L, false);
            }
        }
        assertTrue(land.getTriggers().contains(trigger));
        game.getTriggerHandler().resetActiveTriggers();
        return 101;
    }

    private void check(String mode, int depth) {
        Game original = setup();
        Game game = original;
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game));
            copier.release();
        }
        Player payer = actor(game);
        Card land = forest(game);
        // Warm the empty-list path before any source is introduced.
        assertTrue(land.canTap());
        land.getReplacementEffects();
        int expectedMana = 1;
        int expectedLife = 100;
        if (mode.startsWith("trigger")) {
            expectedLife = checkTriggers(mode, game, land);
        } else if (mode.startsWith("static")) {
            checkStatics(mode, game, land, payer);
        } else if (mode.equals("intrinsic") || mode.equals("suppressed") || mode.equals("invalidTarget")) {
            ReplacementEffect replacement = preventTap(land, mode.equals("invalidTarget") ? "Creature.Self" : "Card.Self");
            land.addReplacementEffect(replacement);
            if (!mode.equals("invalidTarget")) {
                blocked(land, payer);
                replacement.setSuppressed(true);
                assertTrue(land.canTap());
                if (mode.equals("suppressed")) {
                    replacement.setSuppressed(false);
                    blocked(land, payer);
                    replacement.setSuppressed(true);
                }
            }
        } else if (mode.equals("granted") || mode.equals("textGranted")) {
            ReplacementEffect replacement = preventTap(land, "Card.Self");
            CardTraitChanges changes = new CardTraitChanges(List.of(), List.of(), List.of(replacement), List.of(), null);
            long timestamp = game.getNextTimestamp();
            if (mode.equals("textGranted")) land.getChangedCardTraitsByText().put(timestamp, 0L, changes);
            else land.addChangedCardTraits(changes, timestamp, 0L, false);
            blocked(land, payer);
            if (mode.equals("textGranted")) assertTrue(land.removeChangedCardTraitsByText(timestamp, 0L));
            else assertTrue(land.removeChangedCardTraits(timestamp, 0L));
        } else if (mode.equals("keyword")) {
            KeywordInterface keyword = land.addIntrinsicKeyword("Flying");
            ReplacementEffect replacement = preventTap(land, "Card.Self");
            keyword.getReplacements().add(replacement);
            land.updateKeywordsCache();
            blocked(land, payer);
            replacement.setSuppressed(true);
        } else if (mode.equals("override")) {
            ReplacementOverrideCard effect = new ReplacementOverrideCard(game);
            effect.setOwner(payer);
            effect.setName("Override replacement control");
            payer.getZone(ZoneType.Battlefield).add(effect);
            ReplacementEffect replacement = preventTap(effect, "Land.YouCtrl");
            effect.effects.add(replacement);
            blocked(land, payer);
            replacement.setSuppressed(true);
        } else if (mode.equals("reflection") || mode.equals("contamination")) {
            addCardToZone(mode.equals("reflection") ? "Mana Reflection" : "Contamination", payer, ZoneType.Battlefield);
            game.getAction().checkStaticAbilities(false);
            expectedMana = mode.equals("reflection") ? 2 : 1;
        } else assertEquals(mode, "bare");
        assertTrue(land.canTap());
        SpellAbility mana = land.getManaAbilities().get(0);
        mana.setActivatingPlayer(payer);
        assertTrue(new CostPayment(mana.getPayCosts(), mana)
                .payComputerCosts(new AiCostDecision(payer, mana, false, true)));
        game.getStack().addAndUnfreeze(mana);
        if (mode.startsWith("trigger")) {
            game.getAction().checkStateEffects(false);
            game.getStack().addAllTriggeredAbilitiesToStack();
            GameSimulator.resolveStack(game, payer);
            assertEquals(payer.getLife(), expectedLife);
        }
        assertTrue(land.isTapped());
        assertEquals(payer.getManaPool().totalMana(), expectedMana);
        assertTrue(game.getStack().isEmpty());
        byte expectedColor = mode.equals("contamination") ? forge.card.MagicColor.BLACK : forge.card.MagicColor.GREEN;
        for (Mana unit : payer.getManaPool()) assertEquals(unit.getColor(), expectedColor);
        if (depth > 0) {
            assertFalse(forest(original).isTapped());
            assertEquals(actor(original).getManaPool().totalMana(), 0);
        }
    }
    @Test public void bareDepth0() { check("bare", 0); }
    @Test public void bareDepth1() { check("bare", 1); }
    @Test public void bareDepth2() { check("bare", 2); }
    @Test public void intrinsicDepth0() { check("intrinsic", 0); }
    @Test public void intrinsicDepth1() { check("intrinsic", 1); }
    @Test public void intrinsicDepth2() { check("intrinsic", 2); }
    @Test public void grantedDepth0() { check("granted", 0); }
    @Test public void grantedDepth1() { check("granted", 1); }
    @Test public void grantedDepth2() { check("granted", 2); }
    @Test public void textGrantedDepth0() { check("textGranted", 0); }
    @Test public void textGrantedDepth1() { check("textGranted", 1); }
    @Test public void textGrantedDepth2() { check("textGranted", 2); }
    @Test public void suppressedDepth0() { check("suppressed", 0); }
    @Test public void suppressedDepth1() { check("suppressed", 1); }
    @Test public void suppressedDepth2() { check("suppressed", 2); }
    @Test public void invalidTargetDepth0() { check("invalidTarget", 0); }
    @Test public void invalidTargetDepth1() { check("invalidTarget", 1); }
    @Test public void invalidTargetDepth2() { check("invalidTarget", 2); }
    @Test public void keywordDepth0() { check("keyword", 0); }
    @Test public void keywordDepth1() { check("keyword", 1); }
    @Test public void keywordDepth2() { check("keyword", 2); }
    @Test public void overrideDepth0() { check("override", 0); }
    @Test public void overrideDepth1() { check("override", 1); }
    @Test public void overrideDepth2() { check("override", 2); }
    @Test public void reflectionDepth0() { check("reflection", 0); }
    @Test public void reflectionDepth1() { check("reflection", 1); }
    @Test public void reflectionDepth2() { check("reflection", 2); }
    @Test public void contaminationDepth0() { check("contamination", 0); }
    @Test public void contaminationDepth1() { check("contamination", 1); }
    @Test public void contaminationDepth2() { check("contamination", 2); }
    @Test public void staticIntrinsicDepth0() { check("staticIntrinsic", 0); }
    @Test public void staticIntrinsicDepth1() { check("staticIntrinsic", 1); }
    @Test public void staticIntrinsicDepth2() { check("staticIntrinsic", 2); }
    @Test public void staticGrantedDepth0() { check("staticGranted", 0); }
    @Test public void staticGrantedDepth1() { check("staticGranted", 1); }
    @Test public void staticGrantedDepth2() { check("staticGranted", 2); }
    @Test public void staticTextGrantedDepth0() { check("staticTextGranted", 0); }
    @Test public void staticTextGrantedDepth1() { check("staticTextGranted", 1); }
    @Test public void staticTextGrantedDepth2() { check("staticTextGranted", 2); }
    @Test public void staticKeywordDepth0() { check("staticKeyword", 0); }
    @Test public void staticKeywordDepth1() { check("staticKeyword", 1); }
    @Test public void staticKeywordDepth2() { check("staticKeyword", 2); }
    @Test public void staticMutableDepth0() { check("staticMutable", 0); }
    @Test public void staticMutableDepth1() { check("staticMutable", 1); }
    @Test public void staticMutableDepth2() { check("staticMutable", 2); }
    @Test public void staticLeftSplitDepth0() { check("staticLeftSplit", 0); }
    @Test public void staticLeftSplitDepth1() { check("staticLeftSplit", 1); }
    @Test public void staticLeftSplitDepth2() { check("staticLeftSplit", 2); }
    @Test public void staticRightSplitDepth0() { check("staticRightSplit", 0); }
    @Test public void staticRightSplitDepth1() { check("staticRightSplit", 1); }
    @Test public void staticRightSplitDepth2() { check("staticRightSplit", 2); }
    @Test public void triggerIntrinsicDepth0() { check("triggerIntrinsic", 0); }
    @Test public void triggerIntrinsicDepth1() { check("triggerIntrinsic", 1); }
    @Test public void triggerIntrinsicDepth2() { check("triggerIntrinsic", 2); }
    @Test public void triggerGrantedDepth0() { check("triggerGranted", 0); }
    @Test public void triggerGrantedDepth1() { check("triggerGranted", 1); }
    @Test public void triggerGrantedDepth2() { check("triggerGranted", 2); }
    @Test public void triggerTextGrantedDepth0() { check("triggerTextGranted", 0); }
    @Test public void triggerTextGrantedDepth1() { check("triggerTextGranted", 1); }
    @Test public void triggerTextGrantedDepth2() { check("triggerTextGranted", 2); }
    @Test public void triggerKeywordDepth0() { check("triggerKeyword", 0); }
    @Test public void triggerKeywordDepth1() { check("triggerKeyword", 1); }
    @Test public void triggerKeywordDepth2() { check("triggerKeyword", 2); }
    @Test public void triggerMutableDepth0() { check("triggerMutable", 0); }
    @Test public void triggerMutableDepth1() { check("triggerMutable", 1); }
    @Test public void triggerMutableDepth2() { check("triggerMutable", 2); }
    @Test public void triggerLeftSplitDepth0() { check("triggerLeftSplit", 0); }
    @Test public void triggerLeftSplitDepth1() { check("triggerLeftSplit", 1); }
    @Test public void triggerLeftSplitDepth2() { check("triggerLeftSplit", 2); }
    @Test public void triggerRightSplitDepth0() { check("triggerRightSplit", 0); }
    @Test public void triggerRightSplitDepth1() { check("triggerRightSplit", 1); }
    @Test public void triggerRightSplitDepth2() { check("triggerRightSplit", 2); }
}
