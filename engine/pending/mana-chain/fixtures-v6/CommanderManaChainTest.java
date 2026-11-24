package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.cost.CostPayment;
import forge.game.mana.Mana;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Native ordered payments distinguish legal filter chains from automatic planning. */
public class CommanderManaChainTest extends AITest {
    private Player actor(Game game) {
        return game.getPlayers().stream().filter(p -> p.getName().equals("chain-0")).findFirst().orElseThrow();
    }

    private Card named(Game game, String name, ZoneType zone) {
        return actor(game).getCardsIn(zone, false).stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }

    private void activate(Game game, SpellAbility ability, String choice) {
        ability.setActivatingPlayer(actor(game));
        if (choice != null) ability.getManaPart().setExpressChoice(choice);
        assertTrue(ability.canPlay(), "The actual native ability must be legal before payment");
        int used = ability.getActivationsThisTurn();
        assertTrue(new CostPayment(ability.getPayCosts(), ability)
                .payComputerCosts(new AiCostDecision(actor(game), ability, false, true)));
        game.getStack().addAndUnfreeze(ability);
        assertEquals(ability.getActivationsThisTurn(), used + 1);
    }

    private Game setup(int colorless, boolean directBlue, boolean usedGate) {
        return setup(colorless, directBlue, usedGate, "Concentrate", false);
    }

    private Game setup(int colorless, boolean directBlue, boolean usedGate, String spellName, boolean black) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("chain-" + seat, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Chained mana filters"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int card = 0; card < 24; card++) addCardToZone("Forest", player, ZoneType.Library);
        }
        addCardToZone("Prismite", actor(game), ZoneType.Battlefield);
        Card gate = addCardToZone("Mystic Gate", actor(game), ZoneType.Battlefield);
        if (usedGate) assertTrue(gate.tap(null, actor(game)));
        for (int index = 0; index < colorless; index++) {
            Card wastes = addCardToZone("Wastes", actor(game), ZoneType.Battlefield);
            activate(game, wastes.getManaAbilities().get(0), null);
        }
        if (directBlue) addCardToZone("Island", actor(game), ZoneType.Battlefield);
        if (black) addCardToZone("Swamp", actor(game), ZoneType.Battlefield);
        addCardToZone(spellName, actor(game), ZoneType.Hand);
        assertEquals(actor(game).getManaPool().totalMana(), colorless);
        for (Mana mana : actor(game).getManaPool()) assertTrue(mana.isColorless());
        return game;
    }

    private Game copied(Game original, int depth) {
        original.copyLastState();
        Game result = original;
        for (int copy = 0; copy < depth; copy++) result = new GameCopier(result).withStack(true).makeCopy(null, actor(result));
        return result;
    }

    private List<String> state(Game game) {
        List<String> rows = new ArrayList<>();
        rows.add("life=" + actor(game).getLife());
        for (ZoneType zone : List.of(ZoneType.Battlefield, ZoneType.Hand, ZoneType.Library, ZoneType.Graveyard, ZoneType.Exile)) {
            for (Card card : actor(game).getCardsIn(zone, false)) {
                rows.add(card.getId() + ":" + zone + ":" + card.isTapped() + ":" + card.getTurnInZone());
                for (SpellAbility ability : card.getManaAbilities()) rows.add(card.getId() + ":" + ability.getActivationsThisTurn() + ":" + ability.getActivationsThisGame());
            }
        }
        for (Mana mana : actor(game).getManaPool()) rows.add("mana=" + mana.getColor() + ":" + mana.getSourceCard().getId());
        Collections.sort(rows);
        return rows;
    }

    private SpellAbility spell(Game game) {
        SpellAbility spell = named(game, "Concentrate", ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(actor(game));
        return spell;
    }

    private SpellAbility gateFilter(Game game) {
        return named(game, "Mystic Gate", ZoneType.Battlefield).getManaAbilities().stream()
                .filter(a -> a.getPayCosts().hasManaCost()).findFirst().orElseThrow();
    }

    private void assertResolved(Game game) {
        SpellAbility paid = game.getStack().peekAbility();
        assertEquals(paid.getPayingMana().size(), 4);
        assertEquals(paid.getPayingMana().stream().filter(m -> m.getSourceCard().getName().equals("Mystic Gate")).count(), 2L);
        assertEquals(actor(game).getManaPool().totalMana(), 0);
        GameSimulator.resolveStack(game, actor(game));
        assertEquals(actor(game).getCardsIn(ZoneType.Hand).size(), 3);
        assertEquals(actor(game).getCardsIn(ZoneType.Library).size(), 21);
        named(game, "Concentrate", ZoneType.Graveyard);
        assertTrue(named(game, "Mystic Gate", ZoneType.Battlefield).isTapped());
        assertFalse(named(game, "Prismite", ZoneType.Battlefield).isTapped());
    }

    private void positive(int depth, boolean ordered, boolean directBlue) {
        Game original = setup(directBlue ? 2 : 4, directBlue, false);
        List<String> originalBefore = state(original);
        Game game = copied(original, depth);
        if (ordered) {
            activate(game, named(game, "Prismite", ZoneType.Battlefield).getManaAbilities().get(0), "U");
            assertEquals(actor(game).getManaPool().totalMana(), 3);
            activate(game, gateFilter(game), "U U");
            assertEquals(actor(game).getManaPool().totalMana(), 4);
        }
        assertTrue(ComputerUtil.playStack(spell(game), actor(game), game),
                "A native legal allocation exists: convert C through Prismite and Mystic Gate, or directly fund the Gate with an Island");
        assertResolved(game);
        if (depth > 0) assertEquals(state(original), originalBefore);
    }

    private void negative(int depth, int colorless, boolean usedGate) {
        Game original = setup(colorless, false, usedGate);
        List<String> originalBefore = state(original);
        Game game = copied(original, depth);
        SpellAbility ability = spell(game);
        ManaCostBeingPaid cost = new ManaCostBeingPaid(ability.getPayCosts().getTotalMana());
        String beforeCost = cost.toString();
        List<String> before = state(game);
        for (int repeat = 0; repeat < 3; repeat++) {
            assertEquals(ComputerUtilMana.assessManaPayment(cost, ability, actor(game), false, Set.of()),
                    ComputerUtilMana.ManaPaymentAssessment.NO_PAYMENT_FOUND);
            assertEquals(cost.toString(), beforeCost);
            assertEquals(state(game), before);
            assertFalse(ComputerUtil.playStack(ability, actor(game), game));
            assertTrue(game.getStack().isEmpty());
            assertEquals(state(game), before);
        }
        if (depth > 0) assertEquals(state(original), originalBefore);
    }
    @Test public void orderedChainDepth0() { positive(0, true, false); }
    @Test public void orderedChainDepth1() { positive(1, true, false); }
    @Test public void orderedChainDepth2() { positive(2, true, false); }
    @Test public void automaticChainDepth0() { positive(0, false, false); }
    @Test public void automaticChainDepth1() { positive(1, false, false); }
    @Test public void automaticChainDepth2() { positive(2, false, false); }
    @Test public void directGateFundingDepth0() { positive(0, false, true); }
    @Test public void directGateFundingDepth1() { positive(1, false, true); }
    @Test public void directGateFundingDepth2() { positive(2, false, true); }
    @Test public void insufficientChainDepth0() { negative(0, 3, false); }
    @Test public void insufficientChainDepth1() { negative(1, 3, false); }
    @Test public void insufficientChainDepth2() { negative(2, 3, false); }
    @Test public void unseededChainDepth0() { negative(0, 0, false); }
    @Test public void unseededChainDepth1() { negative(1, 0, false); }
    @Test public void unseededChainDepth2() { negative(2, 0, false); }
    @Test public void usedGateChainDepth0() { negative(0, 4, true); }
    @Test public void usedGateChainDepth1() { negative(1, 4, true); }
    @Test public void usedGateChainDepth2() { negative(2, 4, true); }

    private void floatingSeed(int depth, boolean ordered) {
        Game original = setup(2, true, false);
        activate(original, named(original, "Island", ZoneType.Battlefield).getManaAbilities().get(0), null);
        assertEquals(actor(original).getManaPool().totalMana(), 3);
        List<String> before = state(original);
        Game game = copied(original, depth);
        if (ordered) {
            activate(game, gateFilter(game), "U U");
            assertEquals(actor(game).getManaPool().totalMana(), 4);
        }
        assertTrue(ComputerUtil.playStack(spell(game), actor(game), game),
                "Hold floating U to activate the Gate instead of prematurely spending it on the spell");
        assertResolved(game);
        if (depth > 0) assertEquals(state(original), before);
    }

    private void scarceBlack(int depth, boolean ordered) {
        Game original = setup(3, false, false, "Notion Thief", true);
        List<String> before = state(original);
        Game game = copied(original, depth);
        if (ordered) {
            activate(game, named(game, "Prismite", ZoneType.Battlefield).getManaAbilities().get(0), "U");
            assertEquals(actor(game).getManaPool().totalMana(), 2);
            activate(game, gateFilter(game), "U U");
            assertEquals(actor(game).getManaPool().totalMana(), 3);
            activate(game, named(game, "Swamp", ZoneType.Battlefield).getManaAbilities().get(0), null);
        }
        SpellAbility ability = named(game, "Notion Thief", ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(actor(game));
        assertTrue(ComputerUtil.playStack(ability, actor(game), game),
                "Preserve the only B source and use the Gate's spare U for the generic part of 2UB");
        SpellAbility paid = game.getStack().peekAbility();
        assertEquals(paid.getPayingMana().size(), 4);
        long gateMana = paid.getPayingMana().stream().filter(m -> m.getSourceCard().getName().equals("Mystic Gate")).count();
        if (ordered) assertEquals(gateMana, 2L);
        else assertTrue(gateMana >= 0L && gateMana <= 2L,
                "Gate mana may pay the spell directly or fund Prismite's nested activation");
        assertEquals(named(game, "Mystic Gate", ZoneType.Battlefield).getManaAbilities().stream()
                .mapToInt(SpellAbility::getActivationsThisTurn).sum(), 1);
        assertTrue(named(game, "Mystic Gate", ZoneType.Battlefield).isTapped());
        assertEquals(paid.getPayingMana().stream().filter(m -> m.getSourceCard().getName().equals("Swamp")).count(), 1L);
        assertEquals(actor(game).getManaPool().totalMana(), 0);
        GameSimulator.resolveStack(game, actor(game));
        named(game, "Notion Thief", ZoneType.Battlefield);
        assertEquals(actor(game).getCardsIn(ZoneType.Hand).size(), 0);
        assertEquals(actor(game).getCardsIn(ZoneType.Library).size(), 24);
        assertTrue(named(game, "Swamp", ZoneType.Battlefield).isTapped());
        if (depth > 0) assertEquals(state(original), before);
    }

    private void unseededColorCycle(int depth) {
        Game original = setup(0, false, false, "Boomerang", false);
        addCardToZone("Sunken Ruins", actor(original), ZoneType.Battlefield);
        List<String> beforeOriginal = state(original);
        Game game = copied(original, depth);
        SpellAbility ability = named(game, "Boomerang", ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(actor(game));
        ability.getTargets().add(named(game, "Mystic Gate", ZoneType.Battlefield));
        ManaCostBeingPaid cost = new ManaCostBeingPaid(ability.getPayCosts().getTotalMana());
        String beforeCost = cost.toString();
        List<String> before = state(game);
        for (int repeat = 0; repeat < 3; repeat++) {
            assertEquals(ComputerUtilMana.assessManaPayment(cost, ability, actor(game), false, Set.of()),
                    ComputerUtilMana.ManaPaymentAssessment.NO_PAYMENT_FOUND);
            assertEquals(cost.toString(), beforeCost);
            assertEquals(state(game), before);
            assertFalse(ComputerUtil.playStack(ability, actor(game), game));
            assertTrue(game.getStack().isEmpty());
            assertEquals(state(game), before);
        }
        if (depth > 0) assertEquals(state(original), beforeOriginal);
    }
    @Test public void floatingSeedAutoDepth0() { floatingSeed(0, false); }
    @Test public void floatingSeedAutoDepth1() { floatingSeed(1, false); }
    @Test public void floatingSeedAutoDepth2() { floatingSeed(2, false); }
    @Test public void floatingSeedOrderedDepth0() { floatingSeed(0, true); }
    @Test public void floatingSeedOrderedDepth1() { floatingSeed(1, true); }
    @Test public void floatingSeedOrderedDepth2() { floatingSeed(2, true); }
    @Test public void scarceBlackAutoDepth0() { scarceBlack(0, false); }
    @Test public void scarceBlackAutoDepth1() { scarceBlack(1, false); }
    @Test public void scarceBlackAutoDepth2() { scarceBlack(2, false); }
    @Test public void scarceBlackOrderedDepth0() { scarceBlack(0, true); }
    @Test public void scarceBlackOrderedDepth1() { scarceBlack(1, true); }
    @Test public void scarceBlackOrderedDepth2() { scarceBlack(2, true); }
    @Test public void unseededColorCycleDepth0() { unseededColorCycle(0); }
    @Test public void unseededColorCycleDepth1() { unseededColorCycle(1); }
    @Test public void unseededColorCycleDepth2() { unseededColorCycle(2); }

    private void previewIsolation(int depth, boolean payable) {
        Game original = setup(payable ? 4 : 3, false, false);
        Game game = copied(original, depth);
        SpellAbility ability = spell(game);
        for (SpellAbility mana : named(game, "Prismite", ZoneType.Battlefield).getManaAbilities()) {
            mana.setActivatingPlayer(null);
            mana.getManaPart().setExpressChoice("R");
        }
        gateFilter(game).setActivatingPlayer(null);
        gateFilter(game).getManaPart().setExpressChoice("W W");
        List<String> before = state(game);
        ManaCostBeingPaid cost = new ManaCostBeingPaid(ability.getPayCosts().getTotalMana());
        String beforeCost = cost.toString();
        long seed = 900L + depth;
        java.util.Random reference = new java.util.Random(seed);
        forge.util.MyRandom.withRandom(new java.util.Random(seed), () -> AiCache.withIsolatedCache(() -> {
            Object token = new Object();
            String cached = AiCache.getCached("chain-preview-sentinel", () -> "preserved", null, token);
            java.util.Random provider = forge.util.MyRandom.getRandom();
            long calls = forge.util.MyRandom.callCount();
            for (int repeat = 0; repeat < 3; repeat++) {
                assertEquals(ComputerUtilMana.assessManaPayment(cost, ability, actor(game), false, Set.of()),
                        payable ? ComputerUtilMana.ManaPaymentAssessment.PAYMENT_FOUND
                                : ComputerUtilMana.ManaPaymentAssessment.NO_PAYMENT_FOUND);
                assertEquals(cost.toString(), beforeCost);
                assertEquals(state(game), before);
                assertEquals(named(game, "Prismite", ZoneType.Battlefield).getManaAbilities().get(0).getManaPart().getExpressChoice(), "R");
                assertNull(named(game, "Prismite", ZoneType.Battlefield).getManaAbilities().get(0).getActivatingPlayer());
                assertEquals(gateFilter(game).getManaPart().getExpressChoice(), "W W");
                assertNull(gateFilter(game).getActivatingPlayer());
                assertEquals(AiCache.getCached("chain-preview-sentinel", () -> "lost", null, token), cached);
                assertSame(forge.util.MyRandom.getRandom(), provider);
                assertEquals(forge.util.MyRandom.callCount(), calls);
                assertTrue(game.getStack().isEmpty());
            }
            assertEquals(provider.nextLong(), reference.nextLong(), "Assessments must not consume caller randomness");
            return true;
        }));
    }

    private void replaceBookkeeping(ManaCostBeingPaid destination, ManaCostBeingPaid source) {
        try {
            ManaCostBeingPaid.class.getMethod("copyFrom", ManaCostBeingPaid.class).invoke(destination, source);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("The native payment bookkeeping replacement must be available", error);
        }
    }

    private void bookkeepingIsolation(int depth) {
        Game original = setup(4, true, false);
        Game game = copied(original, depth);
        activate(game, named(game, "Island", ZoneType.Battlefield).getManaAbilities().get(0), null);
        Card zenith = addCardToZone("Blue Sun's Zenith", actor(game), ZoneType.Hand);
        ManaCostBeingPaid source = new ManaCostBeingPaid(zenith.getFirstSpellAbility().getPayCosts().getTotalMana());
        source.setXManaCostPaid(2, "");
        int generic = 0;
        for (Mana mana : actor(game).getManaPool()) {
            if (mana.isColorless() && generic < 2) { assertTrue(source.payMana(mana, actor(game).getManaPool())); generic++; }
        }
        assertEquals(generic, 2);
        for (Mana mana : actor(game).getManaPool()) {
            if (!mana.isColorless()) { assertTrue(source.payMana(mana, actor(game).getManaPool())); break; }
        }
        assertEquals(source.getSunburst(), 1);
        assertNotNull(source.getXManaCostPaidByColor());
        assertEquals(source.getXManaCostPaidByColor().values().stream().mapToInt(Integer::intValue).sum(), 2);
        ManaCostBeingPaid destination = new ManaCostBeingPaid(forge.card.mana.ManaCost.get(99));
        replaceBookkeeping(destination, source);
        assertEquals(destination.toString(), source.toString());
        assertEquals(destination.getColorsPaid(), source.getColorsPaid());
        assertEquals(destination.getXManaCostPaidByColor(), source.getXManaCostPaidByColor());
        assertNotSame(destination.getXManaCostPaidByColor(), source.getXManaCostPaidByColor());
        String sourceBefore = source.toString();
        destination.decreaseShard(forge.card.mana.ManaCostShard.BLUE, 1);
        destination.getXManaCostPaidByColor().replaceAll((color, value) -> 99);
        assertEquals(source.toString(), sourceBefore);
        assertEquals(source.getXManaCostPaidByColor().values().stream().mapToInt(Integer::intValue).sum(), 2);
        String selfBefore = destination.toString();
        replaceBookkeeping(destination, destination);
        assertEquals(destination.toString(), selfBefore);
        ManaCostBeingPaid plain = new ManaCostBeingPaid(spell(game).getPayCosts().getTotalMana());
        replaceBookkeeping(destination, plain);
        assertNull(destination.getXManaCostPaidByColor());
        assertEquals(destination.getColorsPaid(), (byte) 0);
        assertEquals(destination.toString(), plain.toString());
    }
    @Test public void payablePreviewIsolationDepth0() { previewIsolation(0, true); }
    @Test public void payablePreviewIsolationDepth1() { previewIsolation(1, true); }
    @Test public void payablePreviewIsolationDepth2() { previewIsolation(2, true); }
    @Test public void refusedPreviewIsolationDepth0() { previewIsolation(0, false); }
    @Test public void refusedPreviewIsolationDepth1() { previewIsolation(1, false); }
    @Test public void refusedPreviewIsolationDepth2() { previewIsolation(2, false); }
    @Test public void bookkeepingIsolationDepth0() { bookkeepingIsolation(0); }
    @Test public void bookkeepingIsolationDepth1() { bookkeepingIsolation(1); }
    @Test public void bookkeepingIsolationDepth2() { bookkeepingIsolation(2); }

    /** These are retained AI policies, separate from whether a native payment line is legal. */
    private void preservedSourcePolicy(int depth, boolean lethalTap) {
        Game original = setup(2, !lethalTap, false);
        if (lethalTap) {
            addCardToZone("City of Brass", actor(original), ZoneType.Battlefield);
            actor(original).setLife(1, null);
        }
        Game game = copied(original, depth);
        Card held = lethalTap ? null : named(game, "Island", ZoneType.Battlefield);
        if (held != null) AiCardMemory.rememberCard(actor(game), held, AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_NEXT_SPELL);
        List<String> before = state(game);
        SpellAbility ability = spell(game);
        ManaCostBeingPaid cost = new ManaCostBeingPaid(ability.getPayCosts().getTotalMana());
        String beforeCost = cost.toString();
        for (int repeat = 0; repeat < 3; repeat++) {
            assertEquals(ComputerUtilMana.assessManaPayment(cost, ability, actor(game), false, Set.of()),
                    ComputerUtilMana.ManaPaymentAssessment.NO_PAYMENT_FOUND,
                    "Normal source reservation/lethal-tap policy remains enabled even when a legal line exists");
            assertEquals(cost.toString(), beforeCost);
            assertEquals(state(game), before);
            assertFalse(ComputerUtil.playStack(ability, actor(game), game));
            assertTrue(game.getStack().isEmpty());
            assertEquals(state(game), before);
            if (held != null) assertTrue(AiCardMemory.isRememberedCard(actor(game), held,
                    AiCardMemory.MemorySet.HELD_MANA_SOURCES_FOR_NEXT_SPELL));
        }
    }
    @Test public void heldSourcePolicyDepth0() { preservedSourcePolicy(0, false); }
    @Test public void heldSourcePolicyDepth1() { preservedSourcePolicy(1, false); }
    @Test public void heldSourcePolicyDepth2() { preservedSourcePolicy(2, false); }
    @Test public void lethalTapPolicyDepth0() { preservedSourcePolicy(0, true); }
    @Test public void lethalTapPolicyDepth1() { preservedSourcePolicy(1, true); }
    @Test public void lethalTapPolicyDepth2() { preservedSourcePolicy(2, true); }
}
