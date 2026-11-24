package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameActionUtil;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.mana.Mana;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.OptionalCost;
import forge.game.spellability.OptionalCostValue;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Inspect actual nested filter payments, not only an affordability preview. */
public class CommanderWaterbendFilterTest extends AITest {
    private Player actor(Game game) {
        return game.getPlayers().stream().filter(player -> player.getName().equals("filter-0"))
                .findFirst().orElseThrow();
    }

    private Card named(Game game, String name, ZoneType zone) {
        return actor(game).getCardsIn(zone, false).stream().filter(card -> card.getName().equals(name))
                .findFirst().orElseThrow();
    }

    private void settle(Game game) {
        GameSimulator.resolveStack(game, actor(game));
    }

    private void cast(Game game, String name, int mana) {
        SpellAbility ability = addCardToZone(name, actor(game), ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(actor(game));
        assertTrue(ComputerUtil.playStack(ability, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        settle(game);
    }

    private Game setup(boolean floatGeneric, String filterName) {
        return setup(floatGeneric, filterName, 6, false);
    }

    private Game setup(boolean floatGeneric, String filterName, int clueCount, boolean preTapped) {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("filter-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Nested waterbend filter payment"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        for (int index = 0; index < 7; index++) addCardToZone("Island", actor(game), ZoneType.Battlefield);
        cast(game, "Krark-Clan Ironworks", 4);
        cast(game, filterName, 2);
        addTokens("c_a_clue_draw", clueCount, actor(game));
        if (preTapped) {
            Card clue = actor(game).getCardsIn(ZoneType.Battlefield).stream()
                    .filter(Card::isToken).findFirst().orElseThrow();
            assertTrue(clue.tap(null, actor(game)));
        }
        if (floatGeneric) {
            Card wastes = addCardToZone("Wastes", actor(game), ZoneType.Battlefield);
            SpellAbility mana = wastes.getManaAbilities().get(0);
            mana.setActivatingPlayer(actor(game));
            assertTrue(ComputerUtil.playNoStack(actor(game), mana, game, false));
            assertEquals(actor(game).getManaPool().totalMana(), 1);
        }
        addCardToZone("Grizzly Bears", actor(game), ZoneType.Graveyard);
        addCardToZone("Spirit Water Revival", actor(game), ZoneType.Hand);
        assertEquals(actor(game).getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isLand() && !card.isTapped()).count(), 1L);
        assertEquals(tappedArtifacts(game), preTapped ? 1L : 0L);
        return game;
    }

    private long tappedArtifacts(Game game) {
        return actor(game).getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isArtifact() && card.isTapped()).count();
    }

    private SpellAbility revival(Game game, boolean waterbend) {
        SpellAbility base = named(game, "Spirit Water Revival", ZoneType.Hand).getFirstSpellAbility();
        base.setActivatingPlayer(actor(game));
        if (!waterbend) return base;
        List<OptionalCostValue> choices = GameActionUtil.getOptionalCostValues(base).stream()
                .filter(value -> value.getType() == OptionalCost.Generic
                        && "6".equals(value.getCost().getMaxWaterbend())).toList();
        assertEquals(choices.size(), 1);
        SpellAbility chosen = GameActionUtil.addOptionalCosts(base, choices);
        chosen.setActivatingPlayer(actor(game));
        assertEquals(chosen.getPayCosts().getTotalMana().getCMC(), 9);
        return chosen;
    }

    private void nestedSacrificeFilter(int depth) {
        nestedSacrificeFilter(depth, false);
    }

    private void nestedSacrificeFilter(int depth, boolean floatGeneric) {
        nestedSacrificeFilter(depth, floatGeneric, true);
    }

    private void nestedSacrificeFilter(int depth, boolean floatGeneric, boolean waterbend) {
        nestedSacrificeFilter(depth, floatGeneric, waterbend, "Prismite");
    }

    private void nestedSacrificeFilter(int depth, boolean floatGeneric, boolean waterbend, String filterName) {
        nestedSacrificeFilter(depth, floatGeneric, waterbend, filterName, false);
    }

    private void nestedSacrificeFilter(int depth, boolean floatGeneric, boolean waterbend, String filterName, boolean preTapped) {
        nestedSacrificeFilter(depth, floatGeneric, waterbend, filterName, preTapped, 6);
    }

    private void nestedSacrificeFilter(int depth, boolean floatGeneric, boolean waterbend, String filterName, boolean preTapped, int clueCount) {
        Game original = setup(floatGeneric, filterName, clueCount, preTapped);
        Set<Integer> originallyTapped = new java.util.HashSet<>();
        for (Card card : actor(original).getCardsIn(ZoneType.Battlefield))
            if (card.isTapped() && card.isArtifact()) originallyTapped.add(card.getId());
        original.copyLastState();
        Game game = original;
        for (int index = 0; index < depth; index++)
            game = new GameCopier(game).withStack(true).makeCopy(null, actor(game));
        SpellAbility chosen = revival(game, waterbend);
        assertTrue(ComputerUtil.playStack(chosen, actor(game), game),
                "A legal allocation exists: waterbend with independent artifacts, sacrifice others for Prismite and normal mana");
        SpellAbility paid = game.getStack().peekAbility();
        assertEquals(paid.isOptionalCostPaid(OptionalCost.Generic), waterbend);
        Set<SpellAbility> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<SpellAbility> pending = new ArrayDeque<>();
        pending.add(paid);
        Set<Card> manaTapSources = Collections.newSetFromMap(new IdentityHashMap<>());
        int sacrifices = 0;
        int preTappedSacrifices = 0;
        int filtersUsingSacrificeMana = 0;
        while (!pending.isEmpty()) {
            SpellAbility ability = pending.removeFirst();
            if (!visited.add(ability)) continue;
            if (ability != paid && ability.getPayCosts().hasTapCost()) manaTapSources.add(ability.getHostCard());
            for (Card sacrificed : ability.getPaidList("Sacrificed")) {
                sacrifices++;
                if (originallyTapped.contains(sacrificed.getId())) preTappedSacrifices++;
                assertFalse(sacrificed.isTapped() && !originallyTapped.contains(sacrificed.getId()),
                        "A starting-untapped artifact cannot pay a waterbend tap and nested filter mana sacrifice: "
                                + sacrificed.getName() + "; root mana=" + paid.getPayingMana().size()
                                + "; remaining taps=" + tappedArtifacts(game));
            }
            for (Mana mana : ability.getPayingMana()) {
                if (ability.getHostCard().getName().equals(filterName)
                        && mana.getSourceCard().getName().equals("Krark-Clan Ironworks"))
                    filtersUsingSacrificeMana++;
                if (mana.getManaAbility() != null && mana.getManaAbility().getSourceSA() != null)
                    pending.add(mana.getManaAbility().getSourceSA());
            }
        }
        assertTrue(filtersUsingSacrificeMana > 0, "Actually fund the selected filter with native KCI mana");
        assertTrue(sacrifices >= (floatGeneric || !filterName.equals("Prismite") ? 1 : 2), "Inspect the nested native sacrifice receipts needed for the actual initial pool");
        if (preTapped) assertTrue(preTappedSacrifices > 0, "Use the already-tapped Clue as legal filter fuel");
        long alreadyTappedRemaining = actor(game).getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isTapped() && originallyTapped.contains(card.getId())).count();
        long waterTaps = tappedArtifacts(game) - alreadyTappedRemaining - manaTapSources.stream()
                .filter(card -> card.isArtifact() && card.isTapped() && card.isInPlay()).count();
        assertTrue(waterTaps <= 6L);
        assertEquals(paid.getPayingMana().size() + waterTaps, (waterbend ? 9L : 3L),
                "Normal mana and independent waterbend taps cover the actual total cost");
        long graveyardCards = actor(game).getCardsIn(ZoneType.Graveyard).stream()
                .filter(card -> !card.isToken()).count();
        settle(game);
        assertEquals(actor(game).isUnlimitedHandSize(), waterbend);
        assertEquals(actor(game).getCardsIn(ZoneType.Hand).size(), waterbend ? 7 : 2);
        assertEquals((long) actor(game).getCardsIn(ZoneType.Library).size(), waterbend ? 24L + graveyardCards - 7L : 22L,
                "Shuffle actual sacrificed nontoken cards as well as the starting graveyard");
        assertEquals((long) actor(game).getCardsIn(ZoneType.Graveyard).size(), waterbend ? 0L : graveyardCards);
        named(game, "Spirit Water Revival", ZoneType.Exile);
        if (depth > 0) {
            assertEquals(tappedArtifacts(original), preTapped ? 1L : 0L);
            assertEquals(actor(original).getCardsIn(ZoneType.Hand).size(), 1);
            named(original, filterName, ZoneType.Battlefield);
            named(original, "Krark-Clan Ironworks", ZoneType.Battlefield);
        }
    }

    @Test public void nestedSacrificeFilterDepth0() { nestedSacrificeFilter(0); }
    @Test public void nestedSacrificeFilterDepth1() { nestedSacrificeFilter(1); }
    @Test public void nestedSacrificeFilterDepth2() { nestedSacrificeFilter(2); }

    @Test public void floatingGenericFilterDepth0() { nestedSacrificeFilter(0, true); }
    @Test public void floatingGenericFilterDepth1() { nestedSacrificeFilter(1, true); }
    @Test public void floatingGenericFilterDepth2() { nestedSacrificeFilter(2, true); }

    @Test public void basicFilterDepth0() { nestedSacrificeFilter(0, true, false); }
    @Test public void basicFilterDepth1() { nestedSacrificeFilter(1, true, false); }
    @Test public void basicFilterDepth2() { nestedSacrificeFilter(2, true, false); }

    @Test public void signetFilterDepth0() { nestedSacrificeFilter(0, false, true, "Azorius Signet"); }
    @Test public void signetFilterDepth1() { nestedSacrificeFilter(1, false, true, "Azorius Signet"); }
    @Test public void signetFilterDepth2() { nestedSacrificeFilter(2, false, true, "Azorius Signet"); }

    private java.util.Map<Integer, Boolean> board(Game game) {
        java.util.Map<Integer, Boolean> result = new java.util.LinkedHashMap<>();
        for (Card card : actor(game).getCardsIn(ZoneType.Battlefield)) result.put(card.getId(), card.isTapped());
        return result;
    }

    private Game copied(Game game, int depth) {
        game.copyLastState();
        for (int index = 0; index < depth; index++)
            game = new GameCopier(game).withStack(true).makeCopy(null, actor(game));
        return game;
    }

    private void insufficientFilter(int depth) {
        Game original = setup(true, "Prismite", 1, false);
        Game game = copied(original, depth);
        java.util.Map<Integer, Boolean> before = board(game);
        assertEquals(actor(game).getManaPool().totalMana(), 1);
        assertFalse(ComputerUtil.playStack(revival(game, true), actor(game), game),
                "Even all available sacrifices, waterbend and the initial pool cannot cover nine mana with conversion");
        assertEquals(board(game), before);
        assertEquals(actor(game).getManaPool().totalMana(), 1);
        assertEquals(actor(game).getCardsIn(ZoneType.Hand).size(), 1);
        assertEquals(actor(game).getCardsIn(ZoneType.Graveyard).size(), 1);
        assertEquals(actor(game).getCardsIn(ZoneType.Library).size(), 24);
        assertTrue(game.getStack().isEmpty());
        assertFalse(actor(game).isUnlimitedHandSize());
        if (depth > 0) assertEquals(board(original), before);
    }

    /** CR734.1: refund waterbend while retained non-reversed mana abilities keep their costs and mana. */
    private void rejectedNestedFilter(int depth) {
        Game original = setup(false, "Azorius Signet");
        Game game = copied(original, depth);
        Player payer = actor(game);
        java.util.Map<Integer, Boolean> before = board(game);
        int[] payments = {0};
        payer.dangerouslySetController(new PlayerControllerAi(game, payer, payer.getLobbyPlayer()) {
            @Override
            public boolean payManaCost(forge.card.mana.ManaCost cost, forge.game.cost.CostPartMana part,
                    SpellAbility ability, String prompt, forge.game.mana.ManaConversionMatrix matrix, boolean effect) {
                boolean paid = super.payManaCost(cost, part, ability, prompt, matrix, effect);
                if (paid && ability.getHostCard().getName().equals("Spirit Water Revival")) {
                    payments[0]++;
                    assertEquals(ability.getPayingMana().size(), 3);
                    assertEquals(tappedArtifacts(game), 7L, "Six waterbend taps plus Signet's separate mana tap");
                    return payments[0] > 1;
                }
                return paid;
            }
        });
        assertFalse(ComputerUtil.playStack(revival(game, true), payer, game));
        assertEquals(payments[0], 1);
        Card sacrificedIronworks = named(game, "Krark-Clan Ironworks", ZoneType.Graveyard);
        java.util.Map<Integer, Boolean> expected = new java.util.LinkedHashMap<>(before);
        assertNotNull(expected.remove(sacrificedIronworks.getId()));
        assertEquals(board(game), expected, "Refund waterbend and reversible taps; keep the non-reversed mana sacrifice");
        assertEquals(payer.getManaPool().totalMana(), 2, "Retain the two mana from the committed KCI activation");
        for (Mana mana : payer.getManaPool()) assertEquals(mana.getSourceCard().getName(), "Krark-Clan Ironworks");
        assertEquals(payer.getCardsIn(ZoneType.Hand).size(), 1);
        assertEquals(payer.getCardsIn(ZoneType.Graveyard).size(), 2);
        assertEquals(payer.getCardsIn(ZoneType.Library).size(), 24);
        assertTrue(game.getStack().isEmpty());
        assertFalse(payer.isUnlimitedHandSize());
        assertTrue(ComputerUtil.playStack(revival(game, true), payer, game),
                "A fresh attempt can use refunded taps and the legitimately retained floating mana");
        assertEquals(payments[0], 2);
        settle(game);
        assertTrue(payer.isUnlimitedHandSize());
        assertEquals(payer.getCardsIn(ZoneType.Hand).size(), 7);
        named(game, "Spirit Water Revival", ZoneType.Exile);
        if (depth > 0) assertEquals(board(original), before);
    }

    @Test public void preTappedSignetDepth0() { nestedSacrificeFilter(0, false, true, "Azorius Signet", true); }
    @Test public void preTappedSignetDepth1() { nestedSacrificeFilter(1, false, true, "Azorius Signet", true); }
    @Test public void preTappedSignetDepth2() { nestedSacrificeFilter(2, false, true, "Azorius Signet", true); }
    @Test public void insufficientFilterDepth0() { insufficientFilter(0); }
    @Test public void insufficientFilterDepth1() { insufficientFilter(1); }
    @Test public void insufficientFilterDepth2() { insufficientFilter(2); }
    @Test public void rejectedNestedFilterDepth0() { rejectedNestedFilter(0); }
    @Test public void rejectedNestedFilterDepth1() { rejectedNestedFilter(1); }
    @Test public void rejectedNestedFilterDepth2() { rejectedNestedFilter(2); }

    @Test public void consumeFilterAfterColorDepth0() { assessFilter(0, 2, "PAYMENT_FOUND_WITHOUT_VALUE_POLICY"); }
    @Test public void consumeFilterAfterColorDepth1() { assessFilter(1, 2, "PAYMENT_FOUND_WITHOUT_VALUE_POLICY"); }
    @Test public void consumeFilterAfterColorDepth2() { assessFilter(2, 2, "PAYMENT_FOUND_WITHOUT_VALUE_POLICY"); }

    private void nativeSacrificeMana(Game game, SpellAbility ability, Card selected) {
        ability.setActivatingPlayer(actor(game));
        assertTrue(ability.canPlay());
        AiCostDecision decision = new AiCostDecision(actor(game), ability, false, true)
                .withPlannedSacrifices(new forge.game.card.CardCollection(selected));
        assertTrue(new forge.game.cost.CostPayment(ability.getPayCosts(), ability).payComputerCosts(decision));
        forge.game.ability.AbilityUtils.resolve(ability);
    }

    /** A native ordered witness proves the two-Clue line before asking the automatic planner to find it. */
    private void orderedTwoClueWitness(int depth) {
        Game original = setup(true, "Prismite", 2, false);
        Game game = copied(original, depth);
        SpellAbility ironworks = named(game, "Krark-Clan Ironworks", ZoneType.Battlefield).getManaAbilities().get(0);
        List<Card> clues = actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).toList();
        assertEquals(clues.size(), 2);
        nativeSacrificeMana(game, ironworks, clues.get(0));
        assertEquals(actor(game).getManaPool().totalMana(), 3);
        nativeSacrificeMana(game, ironworks, clues.get(1));
        assertEquals(actor(game).getManaPool().totalMana(), 5);
        Card prismite = named(game, "Prismite", ZoneType.Battlefield);
        SpellAbility conversion = prismite.getManaAbilities().get(0);
        conversion.setActivatingPlayer(actor(game));
        conversion.getManaPart().setExpressChoice("U");
        assertTrue(ComputerUtil.playNoStack(actor(game), conversion, game, false));
        assertEquals(actor(game).getManaPool().totalMana(), 4);
        nativeSacrificeMana(game, ironworks, prismite);
        assertEquals(actor(game).getManaPool().totalMana(), 6);
        nativeSacrificeMana(game, ironworks, ironworks.getHostCard());
        assertEquals(actor(game).getManaPool().totalMana(), 8);
        Card island = actor(game).getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Island") && !card.isTapped()).findFirst().orElseThrow();
        SpellAbility blue = island.getManaAbilities().get(0);
        blue.setActivatingPlayer(actor(game));
        assertTrue(ComputerUtil.playNoStack(actor(game), blue, game, false));
        assertEquals(actor(game).getManaPool().totalMana(), 9);
        assertTrue(ComputerUtil.playStack(revival(game, true), actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 9);
        settle(game);
        assertEquals(actor(game).getCardsIn(ZoneType.Hand).size(), 7);
        assertEquals(actor(game).getCardsIn(ZoneType.Library).size(), 20);
        assertTrue(actor(game).isUnlimitedHandSize());
        named(game, "Spirit Water Revival", ZoneType.Exile);
        if (depth > 0) {
            assertEquals(actor(original).getManaPool().totalMana(), 1);
            named(original, "Prismite", ZoneType.Battlefield);
            named(original, "Krark-Clan Ironworks", ZoneType.Battlefield);
        }
    }
    @Test public void orderedTwoClueWitnessDepth0() { orderedTwoClueWitness(0); }
    @Test public void orderedTwoClueWitnessDepth1() { orderedTwoClueWitness(1); }
    @Test public void orderedTwoClueWitnessDepth2() { orderedTwoClueWitness(2); }

    // Reflection keeps this compatibility fixture compilable against the baseline without the new API.
    private Object assessNativeMana(Game game, SpellAbility ability, forge.game.mana.ManaCostBeingPaid cost) {
        try {
            return ComputerUtilMana.class.getMethod("assessManaPayment", forge.game.mana.ManaCostBeingPaid.class,
                    SpellAbility.class, Player.class, boolean.class, Set.class)
                    .invoke(null, cost, ability, actor(game), false, Set.of());
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("The native mana assessment API must be available", error);
        }
    }

    /** A legal payment and an AI's decision to make it are separate observable outcomes. */
    private void assessFilter(int depth, int clues, String expected) {
        Game original = setup(true, "Prismite", clues, false);
        Game game = copied(original, depth);
        SpellAbility ability = revival(game, true);
        forge.game.mana.ManaCostBeingPaid cost = new forge.game.mana.ManaCostBeingPaid(ability.getPayCosts().getTotalMana());
        String beforeCost = cost.toString();
        java.util.Map<Integer, Boolean> before = board(game);
        Set<Card> taps = AiCardMemory.getMemorySet(actor(game), AiCardMemory.MemorySet.PAYS_TAP_COST);
        Set<Card> sacrifices = AiCardMemory.getMemorySet(actor(game), AiCardMemory.MemorySet.PAYS_SAC_COST);
        Set<forge.game.mana.ManaCostBeingPaid> unpaid = AiCardMemory.getMemorySet(actor(game), AiCardMemory.MemorySetMana.UNPAID_COSTS);
        Card sentinel = named(game, "Grizzly Bears", ZoneType.Graveyard);
        taps.add(sentinel);
        sacrifices.add(sentinel);
        unpaid.add(new forge.game.mana.ManaCostBeingPaid(forge.card.mana.ManaCost.get(99)));
        Set<Card> tapsBefore = new java.util.HashSet<>(taps);
        Set<Card> sacrificesBefore = new java.util.HashSet<>(sacrifices);
        Set<forge.game.mana.ManaCostBeingPaid> unpaidBefore = new java.util.HashSet<>(unpaid);
        for (int repeat = 0; repeat < 3; repeat++) {
            assertEquals(assessNativeMana(game, ability, cost).toString(), expected);
            assertEquals(cost.toString(), beforeCost);
            assertEquals(board(game), before);
            assertEquals(actor(game).getManaPool().totalMana(), 1);
            assertEquals(taps, tapsBefore);
            assertEquals(sacrifices, sacrificesBefore);
            assertEquals(unpaid, unpaidBefore);
            assertTrue(game.getStack().isEmpty());
        }
        assertEquals(actor(game).getCardsIn(ZoneType.Hand).size(), 1);
        assertEquals(actor(game).getCardsIn(ZoneType.Graveyard).size(), 1);
        if (expected.equals("PAYMENT_FOUND_WITHOUT_VALUE_POLICY")) {
            assertFalse(ComputerUtil.playStack(ability, actor(game), game),
                    "Assessment must not disable the existing sacrifice-value policy during normal play");
            assertEquals(board(game), before);
            assertEquals(actor(game).getManaPool().totalMana(), 1);
        }
        if (depth > 0) assertEquals(board(original), before);
    }
    @Test public void assessmentFoundDepth0() { assessFilter(0, 6, "PAYMENT_FOUND"); }
    @Test public void assessmentFoundDepth1() { assessFilter(1, 6, "PAYMENT_FOUND"); }
    @Test public void assessmentFoundDepth2() { assessFilter(2, 6, "PAYMENT_FOUND"); }
    @Test public void assessmentNoPlanDepth0() { assessFilter(0, 1, "NO_PAYMENT_FOUND"); }
    @Test public void assessmentNoPlanDepth1() { assessFilter(1, 1, "NO_PAYMENT_FOUND"); }
    @Test public void assessmentNoPlanDepth2() { assessFilter(2, 1, "NO_PAYMENT_FOUND"); }

    /** Six colorless mana can pay 2UU by activating the same untapped filter twice. */
    private Game repeatSetup(int colorless, boolean directBlue) {
        Game game = setup(false, "Prismite", 0, false);
        for (Card card : actor(game).getCardsIn(ZoneType.Battlefield)) {
            if (card.isLand() && !card.isTapped() && !directBlue) assertTrue(card.tap(null, actor(game)));
        }
        SpellAbility ironworks = named(game, "Krark-Clan Ironworks", ZoneType.Battlefield).getManaAbilities().get(0);
        nativeSacrificeMana(game, ironworks, ironworks.getHostCard());
        for (int index = 2; index < colorless; index++) {
            Card wastes = addCardToZone("Wastes", actor(game), ZoneType.Battlefield);
            SpellAbility mana = wastes.getManaAbilities().get(0);
            mana.setActivatingPlayer(actor(game));
            assertTrue(ComputerUtil.playNoStack(actor(game), mana, game, false));
        }
        assertEquals(actor(game).getManaPool().totalMana(), colorless);
        addCardToZone("Concentrate", actor(game), ZoneType.Hand);
        return game;
    }

    private void repeatedConversion(int depth, boolean ordered, boolean directBlue) {
        int initialMana = directBlue ? 4 : 6;
        Game original = repeatSetup(initialMana, directBlue);
        Game game = copied(original, depth);
        if (ordered) {
            SpellAbility filter = named(game, "Prismite", ZoneType.Battlefield).getManaAbilities().get(0);
            for (int index = 0; index < 2; index++) {
                filter.setActivatingPlayer(actor(game));
                filter.getManaPart().setExpressChoice("U");
                assertTrue(ComputerUtil.playNoStack(actor(game), filter, game, false));
                assertEquals(actor(game).getManaPool().totalMana(), 5 - index);
            }
        }
        SpellAbility spell = named(game, "Concentrate", ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(actor(game));
        assertTrue(ComputerUtil.playStack(spell, actor(game), game),
                "2UU is payable using six C and two activations of Prismite, or four C plus an Island");
        SpellAbility paid = game.getStack().peekAbility();
        assertEquals(paid.getPayingMana().size(), 4);
        assertEquals(paid.getPayingMana().stream().filter(m -> m.getSourceCard().getName().equals("Prismite")).count(),
                directBlue ? 1L : 2L);
        assertEquals(actor(game).getManaPool().totalMana(), 0);
        settle(game);
        assertEquals(actor(game).getCardsIn(ZoneType.Hand).size(), 4);
        assertEquals(actor(game).getCardsIn(ZoneType.Library).size(), 21);
        named(game, "Concentrate", ZoneType.Graveyard);
        assertFalse(named(game, "Prismite", ZoneType.Battlefield).isTapped());
        if (depth > 0) {
            assertEquals(actor(original).getManaPool().totalMana(), initialMana);
            assertEquals(actor(original).getCardsIn(ZoneType.Hand).size(), 2);
            assertEquals(actor(original).getCardsIn(ZoneType.Library).size(), 24);
        }
    }

    private void insufficientRepeat(int depth) {
        Game original = repeatSetup(5, false);
        Game game = copied(original, depth);
        SpellAbility spell = named(game, "Concentrate", ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(actor(game));
        java.util.Map<Integer, Boolean> before = board(game);
        for (int repeat = 0; repeat < 3; repeat++) {
            assertFalse(ComputerUtil.playStack(spell, actor(game), game));
            assertEquals(board(game), before);
            assertEquals(actor(game).getManaPool().totalMana(), 5);
            assertEquals(actor(game).getCardsIn(ZoneType.Hand).size(), 2);
            assertTrue(game.getStack().isEmpty());
        }
    }
    @Test public void repeatedFilterDepth0() { repeatedConversion(0, false, false); }
    @Test public void repeatedFilterDepth1() { repeatedConversion(1, false, false); }
    @Test public void repeatedFilterDepth2() { repeatedConversion(2, false, false); }
    @Test public void orderedRepeatedFilterDepth0() { repeatedConversion(0, true, false); }
    @Test public void orderedRepeatedFilterDepth1() { repeatedConversion(1, true, false); }
    @Test public void orderedRepeatedFilterDepth2() { repeatedConversion(2, true, false); }
    @Test public void directBlueFilterDepth0() { repeatedConversion(0, false, true); }
    @Test public void directBlueFilterDepth1() { repeatedConversion(1, false, true); }
    @Test public void directBlueFilterDepth2() { repeatedConversion(2, false, true); }
    @Test public void insufficientRepeatedFilterDepth0() { insufficientRepeat(0); }
    @Test public void insufficientRepeatedFilterDepth1() { insufficientRepeat(1); }
    @Test public void insufficientRepeatedFilterDepth2() { insufficientRepeat(2); }

    /** Synthetic replacement contract: the filter produces C, so it cannot supply either U. */
    private void replacedFilterOutput(int depth) {
        Game original = repeatSetup(6, false);
        Card filter = named(original, "Prismite", ZoneType.Battlefield);
        filter.setSVar("OnlyColorless", "DB$ ReplaceMana | ReplaceType$ C");
        filter.addReplacementEffect(forge.game.replacement.ReplacementHandler.parseReplacement(
                "Event$ ProduceMana | ActiveZones$ Battlefield | ValidCard$ Card.Self | ValidSA$ Activated.ManaAbility | ReplaceWith$ OnlyColorless | Description$ Synthetic colorless replacement",
                filter, true));
        Game game = copied(original, depth);
        SpellAbility conversion = named(game, "Prismite", ZoneType.Battlefield).getManaAbilities().get(0);
        conversion.setActivatingPlayer(actor(game));
        conversion.getManaPart().setExpressChoice("U");
        assertTrue(ComputerUtil.playNoStack(actor(game), conversion, game, false));
        assertEquals(actor(game).getManaPool().totalMana(), 5);
        for (Mana mana : actor(game).getManaPool()) {
            assertTrue(mana.isColorless(),
                    "Confirm the native replacement actually changed the produced blue to colorless");
        }
        SpellAbility spell = named(game, "Concentrate", ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(actor(game));
        java.util.Map<Integer, Boolean> before = board(game);
        forge.game.mana.ManaCostBeingPaid cost = new forge.game.mana.ManaCostBeingPaid(spell.getPayCosts().getTotalMana());
        for (int repeat = 0; repeat < 3; repeat++) {
            assertEquals(assessNativeMana(game, spell, cost).toString(), "NO_PAYMENT_FOUND");
            assertFalse(ComputerUtil.playStack(spell, actor(game), game));
            assertEquals(actor(game).getManaPool().totalMana(), 5);
            assertEquals(board(game), before);
            assertTrue(game.getStack().isEmpty());
        }
        if (depth > 0) assertEquals(actor(original).getManaPool().totalMana(), 6);
    }
    @Test public void replacedFilterOutputDepth0() { replacedFilterOutput(0); }
    @Test public void replacedFilterOutputDepth1() { replacedFilterOutput(1); }
    @Test public void replacedFilterOutputDepth2() { replacedFilterOutput(2); }
}
