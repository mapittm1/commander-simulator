package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
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
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.replacement.ReplacementEffect;
import forge.game.replacement.ReplacementHandler;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Live replacement sources must remain visible across empty/present transitions. */
public class CommanderLiveTraitMutationTest extends AITest {
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

    private static final class DynamicCard extends Card {
        private StaticAbility additionalStatic;
        private ReplacementEffect additionalReplacement;
        DynamicCard(Game game) { super(game.nextCardId(), game); }
        @Override public void updateStaticAbilities(List<StaticAbility> list, CardState state) {
            super.updateStaticAbilities(list, state);
            if (additionalStatic != null) list.add(additionalStatic);
        }
        @Override public void updateReplacementEffects(List<ReplacementEffect> list, CardState state, boolean rulesHost) {
            super.updateReplacementEffects(list, state, rulesHost);
            if (additionalReplacement != null) list.add(additionalReplacement);
        }
    }

    private void check(String mode, int depth) {
        Game game = setup();
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game));
            copier.release();
        }
        Player payer = actor(game);
        Card land = forest(game);
        boolean statics = mode.startsWith("static");
        StaticAbility ban = new StaticAbility("Mode$ CantBeActivated | AffectedZone$ Battlefield | ValidCard$ Card.Self | ValidSA$ Activated", land, land.getCurrentState());
        ReplacementEffect prevention = preventTap(land, "Card.Self");
        List<StaticAbility> staticList = new ArrayList<>();
        List<ReplacementEffect> replacementList = new ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean remove = new java.util.concurrent.atomic.AtomicBoolean();
        if (mode.endsWith("Subclass")) {
            DynamicCard dynamic = new DynamicCard(game);
            dynamic.setOwner(payer);
            dynamic.getCurrentState().setType(forge.card.CardType.parse("Artifact", false));
            assertTrue(dynamic.getStaticAbilities().isEmpty());
            assertTrue(dynamic.getReplacementEffects().isEmpty());
            dynamic.additionalStatic = ban;
            dynamic.additionalReplacement = prevention;
            if (statics) assertTrue(dynamic.getStaticAbilities().contains(ban), "Live subclass static callback");
            else assertTrue(dynamic.getReplacementEffects().contains(prevention), "Live subclass replacement callback");
            dynamic.additionalStatic = null;
            dynamic.additionalReplacement = null;
            assertTrue(dynamic.getStaticAbilities().isEmpty());
            assertTrue(dynamic.getReplacementEffects().isEmpty());
            return;
        }
        if (mode.endsWith("Keyword")) {
            KeywordInterface keyword = land.addIntrinsicKeyword("Flying");
            land.updateKeywordsCache();
            staticList = (List<StaticAbility>) keyword.getStaticAbilities();
            replacementList = (List<ReplacementEffect>) keyword.getReplacements();
        } else if (mode.endsWith("Predicate")) {
            land.addStaticAbility(ban);
            land.addReplacementEffect(prevention);
            land.addChangedCardTraits(new CardTraitChanges(List.of(), List.of(), List.of(), List.of(), ignored -> remove.get()), game.getNextTimestamp(), 0L, false);
            assertTrue(land.getStaticAbilities().contains(ban));
            assertTrue(land.getReplacementEffects().contains(prevention));
            remove.set(true);
            if (statics) assertFalse(land.getStaticAbilities().contains(ban), "Live removal predicate for static");
            else assertFalse(land.getReplacementEffects().contains(prevention), "Live removal predicate for replacement");
            remove.set(false);
            assertTrue(land.getStaticAbilities().contains(ban));
            assertTrue(land.getReplacementEffects().contains(prevention));
            return;
        } else {
            CardTraitChanges changes = new CardTraitChanges(List.of(), List.of(), replacementList, staticList, null);
            if (mode.endsWith("Text")) land.getChangedCardTraitsByText().put(game.getNextTimestamp(), 0L, changes);
            else land.addChangedCardTraits(changes, game.getNextTimestamp(), 0L, false);
        }
        assertFalse(land.getStaticAbilities().contains(ban));
        assertFalse(land.getReplacementEffects().contains(prevention));
        if (statics) {
            staticList.add(ban);
            assertTrue(land.getStaticAbilities().contains(ban), "In-place static addition");
            staticList.clear();
            assertFalse(land.getStaticAbilities().contains(ban), "In-place static removal");
        } else {
            replacementList.add(prevention);
            assertTrue(land.getReplacementEffects().contains(prevention), "In-place replacement addition");
            blocked(land, payer);
            replacementList.clear();
            assertFalse(land.getReplacementEffects().contains(prevention), "In-place replacement removal");
            assertTrue(land.canTap());
        }
    }
    @Test public void staticKeywordDepth0() { check("staticKeyword", 0); }
    @Test public void staticKeywordDepth1() { check("staticKeyword", 1); }
    @Test public void staticKeywordDepth2() { check("staticKeyword", 2); }
    @Test public void replacementKeywordDepth0() { check("replacementKeyword", 0); }
    @Test public void replacementKeywordDepth1() { check("replacementKeyword", 1); }
    @Test public void replacementKeywordDepth2() { check("replacementKeyword", 2); }
    @Test public void staticTraitsDepth0() { check("staticTraits", 0); }
    @Test public void staticTraitsDepth1() { check("staticTraits", 1); }
    @Test public void staticTraitsDepth2() { check("staticTraits", 2); }
    @Test public void replacementTraitsDepth0() { check("replacementTraits", 0); }
    @Test public void replacementTraitsDepth1() { check("replacementTraits", 1); }
    @Test public void replacementTraitsDepth2() { check("replacementTraits", 2); }
    @Test public void staticTextDepth0() { check("staticText", 0); }
    @Test public void staticTextDepth1() { check("staticText", 1); }
    @Test public void staticTextDepth2() { check("staticText", 2); }
    @Test public void replacementTextDepth0() { check("replacementText", 0); }
    @Test public void replacementTextDepth1() { check("replacementText", 1); }
    @Test public void replacementTextDepth2() { check("replacementText", 2); }
    @Test public void staticPredicateDepth0() { check("staticPredicate", 0); }
    @Test public void staticPredicateDepth1() { check("staticPredicate", 1); }
    @Test public void staticPredicateDepth2() { check("staticPredicate", 2); }
    @Test public void replacementPredicateDepth0() { check("replacementPredicate", 0); }
    @Test public void replacementPredicateDepth1() { check("replacementPredicate", 1); }
    @Test public void replacementPredicateDepth2() { check("replacementPredicate", 2); }
    @Test public void staticSubclassDepth0() { check("staticSubclass", 0); }
    @Test public void staticSubclassDepth1() { check("staticSubclass", 1); }
    @Test public void staticSubclassDepth2() { check("staticSubclass", 2); }
    @Test public void replacementSubclassDepth0() { check("replacementSubclass", 0); }
    @Test public void replacementSubclassDepth1() { check("replacementSubclass", 1); }
    @Test public void replacementSubclassDepth2() { check("replacementSubclass", 2); }
}
