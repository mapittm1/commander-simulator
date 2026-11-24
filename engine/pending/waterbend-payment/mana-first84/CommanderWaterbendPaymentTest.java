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
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.OptionalCost;
import forge.game.spellability.OptionalCostValue;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import forge.card.mana.ManaCost;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Actual optional waterbend payments distinguish generic limits, colored mana and copied spell state. */
public class CommanderWaterbendPaymentTest extends AITest {
    private Player seat(Game game, int index) {
        return game.getPlayers().stream().filter(player -> player.getName().equals("waterbend-" + index))
                .findFirst().orElseThrow();
    }

    private Card named(Game game, int owner, String name, ZoneType zone) {
        return seat(game, owner).getCardsIn(zone, false).stream()
                .filter(card -> card.getName().equals(name)).findFirst().orElseThrow();
    }

    private void settle(Game game) {
        GameSimulator.resolveStack(game, seat(game, 0));
    }

    private Game setup(int islands, int forests, int artifacts) {
        return setup(islands, forests, artifacts, false);
    }

    private Game setup(int islands, int forests, int artifacts, boolean nonCreature) {
        return setup(islands, forests, artifacts, nonCreature, "plain");
    }

    private Game setup(int islands, int forests, int artifacts, boolean nonCreature, String sourceMode) {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("waterbend-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Paid waterbend limits"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
        }

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, seat(game, 0), 5);
        for (int index = 0; index < islands; index++) addCardToZone("Island", seat(game, 0), ZoneType.Battlefield);
        for (int index = 0; index < forests; index++) addCardToZone("Forest", seat(game, 0), ZoneType.Battlefield);
        List<String> free = nonCreature
                ? List.of("Welding Jar", "Tormod's Crypt", "Mishra's Bauble", "Urza's Bauble", "Darksteel Relic", "Accorder's Shield", "Bone Saw")
                : List.of("Ornithopter", "Memnite", "Phyrexian Walker", "Shield Sphere", "Welding Jar", "Tormod's Crypt", "Mishra's Bauble");
        free = new ArrayList<>(free);
        if (sourceMode.equals("amber")) free.set(0, "Mox Amber");
        if (sourceMode.equals("sick")) free.set(0, "Ornithopter of Paradise");
        if (sourceMode.equals("drum")) free.set(0, "Springleaf Drum");
        if (sourceMode.equals("activeDrum")) { free.set(0, "Springleaf Drum"); free.set(1, "Ornithopter"); }
        for (int index = 0; index < artifacts; index++) {
            String name = free.get(index);
            int mana = name.equals("Ornithopter of Paradise") ? 2 : name.equals("Springleaf Drum") ? 1 : 0;
            cast(game, 0, name, null, mana);
        }
        addCardToZone("Grizzly Bears", seat(game, 0), ZoneType.Graveyard);
        addCardToZone("Spirit Water Revival", seat(game, 0), ZoneType.Hand);
        assertFalse(seat(game, 0).isUnlimitedHandSize());
        return game;
    }

    private void beginCast(Game game, int owner, String name, Card target, int mana) {
        SpellAbility ability = addCardToZone(name, seat(game, owner), ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(seat(game, owner));
        if (target != null) ability.getTargets().add(target);
        assertTrue(ability.canPlay() && ability.checkRestrictions(ability.getHostCard(), seat(game, owner)));
        assertTrue(ComputerUtil.playStack(ability, seat(game, owner), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
    }

    private void cast(Game game, int owner, String name, Card target, int mana) {
        beginCast(game, owner, name, target, mana);
        settle(game);
    }

    private Game snapshot(Game game, int depth) {
        game.copyLastState();
        for (int index = 0; index < depth; index++) game = new GameCopier(game).withStack(true).makeCopy(null, seat(game, 0));
        return game;
    }

    private SpellAbility revival(Game game, boolean waterbend) {
        SpellAbility base = named(game, 0, "Spirit Water Revival", ZoneType.Hand).getFirstSpellAbility();
        base.setActivatingPlayer(seat(game, 0));
        if (!waterbend) return base;
        List<OptionalCostValue> choices = GameActionUtil.getOptionalCostValues(base).stream()
                .filter(value -> value.getType() == OptionalCost.Generic && "6".equals(value.getCost().getMaxWaterbend())).toList();
        assertEquals(choices.size(), 1);
        SpellAbility chosen = GameActionUtil.addOptionalCosts(base, choices);
        chosen.setActivatingPlayer(seat(game, 0));
        assertTrue(chosen.isOptionalCostPaid(OptionalCost.Generic));
        assertEquals(chosen.getPayCosts().getMaxWaterbend(), "6");
        assertEquals(chosen.getPayCosts().getTotalMana().getCMC(), 9);
        return chosen;
    }

    private Map<Integer, Boolean> tapped(Game game) {
        Map<Integer, Boolean> result = new LinkedHashMap<>();
        for (Card card : seat(game, 0).getCardsIn(ZoneType.Battlefield)) result.put(card.getId(), card.isTapped());
        return result;
    }

    private long tappedArtifacts(Game game) {
        return seat(game, 0).getCardsIn(ZoneType.Battlefield).stream().filter(card -> card.isArtifact() && card.isTapped()).count();
    }

    private void payRevival(Game game, boolean waterbend, int mana) {
        SpellAbility chosen = revival(game, waterbend);
        assertTrue(chosen.canPlay() && chosen.checkRestrictions(chosen.getHostCard(), seat(game, 0)));
        assertTrue(ComputerUtil.playStack(chosen, seat(game, 0), game), "Pay the selected native cost using the actual available resources");
        SpellAbility paid = game.getStack().peekAbility();
        assertEquals(paid.getPayingMana().size(), mana, "Normal mana remains payable separately from waterbend taps; actual mana=" + paid.getPayingMana().size() + ", tapped artifacts=" + tappedArtifacts(game));
        assertEquals(paid.isOptionalCostPaid(OptionalCost.Generic), waterbend);
        assertEquals(tappedArtifacts(game), waterbend ? 6L : 0L, "An artifact creature counts once and the waterbend tap cap is six");
    }

    private void resolved(Game game, boolean waterbend) {
        assertEquals(seat(game, 0).getCardsIn(ZoneType.Hand).size(), waterbend ? 7 : 2);
        assertEquals(seat(game, 0).getCardsIn(ZoneType.Library).size(), waterbend ? 18 : 22);
        assertEquals(seat(game, 0).getCardsIn(ZoneType.Graveyard).size(), waterbend ? 0 : 1);
        assertEquals(seat(game, 0).isUnlimitedHandSize(), waterbend);
        named(game, 0, "Spirit Water Revival", ZoneType.Exile);
        assertEquals(seat(game, 0).getLife(), 100);
        assertTrue(game.getStack().isEmpty());
    }

    private void positive(int depth, String mode) {
        positive(depth, mode, false);
    }

    private void positive(int depth, String mode, boolean nonCreature) {
        boolean waterbend = !mode.equals("basic");
        boolean pending = mode.equals("pending");
        int lands = mode.equals("tax") ? 6 : mode.equals("reduction") ? 4 : 3;
        Game original = setup(lands, 0, 6, nonCreature);
        if (mode.equals("tax")) cast(original, 0, "Sphere of Resistance", null, 2);
        if (mode.equals("reduction")) cast(original, 0, "Sapphire Medallion", null, 2);
        int mana = mode.equals("tax") ? 4 : mode.equals("reduction") ? 2 : 3;
        if (pending) payRevival(original, waterbend, mana);
        Game game = snapshot(original, depth);
        if (!pending) payRevival(game, waterbend, mana);
        else assertEquals(tappedArtifacts(game), 6L, "Copy the paid resources without paying again");
        settle(game);
        resolved(game, waterbend);
        if (depth > 0) {
            assertEquals(seat(original, 0).getCardsIn(ZoneType.Library).size(), 24);
            assertEquals(seat(original, 0).getCardsIn(ZoneType.Hand).size(), pending ? 0 : 1);
            assertFalse(seat(original, 0).isUnlimitedHandSize());
            assertEquals(tappedArtifacts(original), pending ? 6L : 0L);
            if (pending) { settle(original); resolved(original, waterbend); resolved(game, waterbend); }
        }
    }

    private void negative(int depth, String mode) {
        negative(depth, mode, false);
    }

    private void negative(int depth, String mode, boolean nonCreature) {
        Game original = mode.equals("cap") ? setup(5, 0, 7, nonCreature) : mode.equals("colored") ? setup(1, 2, 7, nonCreature) : setup(2, 0, 7, nonCreature);
        if (mode.equals("cap")) cast(original, 0, "Sphere of Resistance", null, 2);
        Game game = snapshot(original, depth);
        SpellAbility chosen = revival(game, !mode.equals("basic"));
        assertTrue(chosen.checkRestrictions(chosen.getHostCard(), seat(game, 0)));
        Map<Integer, Boolean> before = tapped(game);
        assertFalse(ComputerUtilCost.canPayCost(chosen, seat(game, 0), false), "Waterbend cannot replace normal generic or colored mana");
        assertEquals(tapped(game), before, "An affordability preview cannot spend resources");
        assertFalse(ComputerUtil.playStack(chosen, seat(game, 0), game));
        assertEquals(tapped(game), before, "A rejected cast cannot tap resources");
        named(game, 0, "Spirit Water Revival", ZoneType.Hand);
        assertEquals(seat(game, 0).getCardsIn(ZoneType.Library).size(), 24);
        assertEquals(seat(game, 0).getCardsIn(ZoneType.Graveyard).size(), 1);
        assertFalse(seat(game, 0).isUnlimitedHandSize());
        assertTrue(game.getStack().isEmpty());
        if (depth > 0) assertEquals(tapped(original), before);
    }
    @Test public void optionalDepth0() { positive(0, "optional"); }
    @Test public void pendingDepth0() { positive(0, "pending"); }
    @Test public void basicDepth0() { positive(0, "basic"); }
    @Test public void taxDepth0() { positive(0, "tax"); }
    @Test public void reductionDepth0() { positive(0, "reduction"); }
    @Test public void insufficientCapDepth0() { negative(0, "cap"); }
    @Test public void insufficientColoredDepth0() { negative(0, "colored"); }
    @Test public void insufficientBasicDepth0() { negative(0, "basic"); }
    @Test public void optionalDepth1() { positive(1, "optional"); }
    @Test public void pendingDepth1() { positive(1, "pending"); }
    @Test public void basicDepth1() { positive(1, "basic"); }
    @Test public void taxDepth1() { positive(1, "tax"); }
    @Test public void reductionDepth1() { positive(1, "reduction"); }
    @Test public void insufficientCapDepth1() { negative(1, "cap"); }
    @Test public void insufficientColoredDepth1() { negative(1, "colored"); }
    @Test public void insufficientBasicDepth1() { negative(1, "basic"); }
    @Test public void optionalDepth2() { positive(2, "optional"); }
    @Test public void pendingDepth2() { positive(2, "pending"); }
    @Test public void basicDepth2() { positive(2, "basic"); }
    @Test public void taxDepth2() { positive(2, "tax"); }
    @Test public void reductionDepth2() { positive(2, "reduction"); }
    @Test public void insufficientCapDepth2() { negative(2, "cap"); }
    @Test public void insufficientColoredDepth2() { negative(2, "colored"); }
    @Test public void insufficientBasicDepth2() { negative(2, "basic"); }
    @Test public void artifactOptionalDepth0() { positive(0, "optional", true); }
    @Test public void artifactPendingDepth0() { positive(0, "pending", true); }
    @Test public void artifactBasicDepth0() { positive(0, "basic", true); }
    @Test public void artifactTaxDepth0() { positive(0, "tax", true); }
    @Test public void artifactReductionDepth0() { positive(0, "reduction", true); }
    @Test public void artifactInsufficientCapDepth0() { negative(0, "cap", true); }
    @Test public void artifactInsufficientColoredDepth0() { negative(0, "colored", true); }
    @Test public void artifactInsufficientBasicDepth0() { negative(0, "basic", true); }
    @Test public void artifactOptionalDepth1() { positive(1, "optional", true); }
    @Test public void artifactPendingDepth1() { positive(1, "pending", true); }
    @Test public void artifactBasicDepth1() { positive(1, "basic", true); }
    @Test public void artifactTaxDepth1() { positive(1, "tax", true); }
    @Test public void artifactReductionDepth1() { positive(1, "reduction", true); }
    @Test public void artifactInsufficientCapDepth1() { negative(1, "cap", true); }
    @Test public void artifactInsufficientColoredDepth1() { negative(1, "colored", true); }
    @Test public void artifactInsufficientBasicDepth1() { negative(1, "basic", true); }
    @Test public void artifactOptionalDepth2() { positive(2, "optional", true); }
    @Test public void artifactPendingDepth2() { positive(2, "pending", true); }
    @Test public void artifactBasicDepth2() { positive(2, "basic", true); }
    @Test public void artifactTaxDepth2() { positive(2, "tax", true); }
    @Test public void artifactReductionDepth2() { positive(2, "reduction", true); }
    @Test public void artifactInsufficientCapDepth2() { negative(2, "cap", true); }
    @Test public void artifactInsufficientColoredDepth2() { negative(2, "colored", true); }
    @Test public void artifactInsufficientBasicDepth2() { negative(2, "basic", true); }

    private void overCapCast(int depth) {
        Game original = setup(5, 0, 7, true);
        cast(original, 0, "Sphere of Resistance", null, 2);
        Game game = snapshot(original, depth);
        SpellAbility chosen = revival(game, true);
        boolean accepted = ComputerUtil.playStack(chosen, seat(game, 0), game);
        int mana = accepted ? game.getStack().peekAbility().getPayingMana().size() : 0;
        assertFalse(accepted, "An actual cast may not use extra artifacts to cover the normal/tax cost; accepted="
                + accepted + ", mana=" + mana + ", tapped artifacts=" + tappedArtifacts(game));
        assertEquals(tappedArtifacts(game), 0L);
        named(game, 0, "Spirit Water Revival", ZoneType.Hand);
        assertTrue(game.getStack().isEmpty());
    }
    @Test public void actualOverCapRejectedDepth0() { overCapCast(0); }
    @Test public void actualOverCapRejectedDepth1() { overCapCast(1); }
    @Test public void actualOverCapRejectedDepth2() { overCapCast(2); }

    private void dormantMana(int depth, String mode) {
        int lands = mode.equals("sick") ? 5 : (mode.equals("drum") || mode.equals("activeDrum")) ? 4 : 3;
        Game original = setup(lands, 0, 6, true, mode);
        Game game = snapshot(original, depth);
        payRevival(game, true, 3);
        settle(game);
        resolved(game, true);
        String name = mode.equals("sick") ? "Ornithopter of Paradise" : (mode.equals("drum") || mode.equals("activeDrum")) ? "Springleaf Drum" : "Mox Amber";
        assertTrue(named(game, 0, name, ZoneType.Battlefield).isTapped(), "A mana ability cannot disqualify an otherwise eligible waterbend permanent when the remaining mana is independently payable");
        if (depth > 0) {
            assertFalse(named(original, 0, name, ZoneType.Battlefield).isTapped());
            assertEquals(seat(original, 0).getCardsIn(ZoneType.Hand).size(), 1);
            assertEquals(seat(original, 0).getCardsIn(ZoneType.Library).size(), 24);
        }
    }
    @Test public void dormantAmberDepth0() { dormantMana(0, "amber"); }
    @Test public void dormantSickDepth0() { dormantMana(0, "sick"); }
    @Test public void dormantDrumDepth0() { dormantMana(0, "drum"); }
    @Test public void dormantAmberDepth1() { dormantMana(1, "amber"); }
    @Test public void dormantSickDepth1() { dormantMana(1, "sick"); }
    @Test public void dormantDrumDepth1() { dormantMana(1, "drum"); }
    @Test public void dormantAmberDepth2() { dormantMana(2, "amber"); }
    @Test public void dormantSickDepth2() { dormantMana(2, "sick"); }
    @Test public void dormantDrumDepth2() { dormantMana(2, "drum"); }

    private void artifactLandOverlap(int depth) {
        Game original = setup(2, 0, 5, true);
        addCardToZone("Darksteel Citadel", seat(original, 0), ZoneType.Battlefield);
        Game game = snapshot(original, depth);
        SpellAbility chosen = revival(game, true);
        Map<Integer, Boolean> before = tapped(game);
        assertFalse(ComputerUtilCost.canPayCost(chosen, seat(game, 0), false), "The artifact land cannot supply mana and a waterbend tap in the same payment");
        assertEquals(tapped(game), before);
        assertTrue(seat(game, 0).getCardsIn(ZoneType.Battlefield).stream().noneMatch(Card::isUsedToPay));
        assertFalse(ComputerUtil.playStack(chosen, seat(game, 0), game));
        assertEquals(tapped(game), before);
        named(game, 0, "Spirit Water Revival", ZoneType.Hand);
        assertTrue(game.getStack().isEmpty());
    }
    @Test public void activeDrumDepth0() { dormantMana(0, "activeDrum"); }
    @Test public void artifactLandOverlapDepth0() { artifactLandOverlap(0); }
    @Test public void activeDrumDepth1() { dormantMana(1, "activeDrum"); }
    @Test public void artifactLandOverlapDepth1() { artifactLandOverlap(1); }
    @Test public void activeDrumDepth2() { dormantMana(2, "activeDrum"); }
    @Test public void artifactLandOverlapDepth2() { artifactLandOverlap(2); }

    private void auxiliaryTapOverlap(int depth) {
        Game original = setup(3, 0, 7, true, "activeDrum");
        Game game = snapshot(original, depth);
        SpellAbility chosen = revival(game, true);
        Map<Integer, Boolean> before = tapped(game);
        assertFalse(ComputerUtilCost.canPayCost(chosen, seat(game, 0), false),
                "Drum cannot reuse a creature already selected for waterbend; two Islands and these permanents cannot cover the nine-cost spell");
        assertEquals(tapped(game), before);
        assertFalse(ComputerUtil.playStack(chosen, seat(game, 0), game));
        assertEquals(tapped(game), before);
        assertTrue(seat(game, 0).getCardsIn(ZoneType.Battlefield).stream().noneMatch(Card::isUsedToPay));
        named(game, 0, "Spirit Water Revival", ZoneType.Hand);
        assertTrue(game.getStack().isEmpty());
    }
    @Test public void auxiliaryTapOverlapDepth0() { auxiliaryTapOverlap(0); }
    @Test public void auxiliaryTapOverlapDepth1() { auxiliaryTapOverlap(1); }
    @Test public void auxiliaryTapOverlapDepth2() { auxiliaryTapOverlap(2); }

    private void previewMemory(int depth, String mode) {
        Game original = mode.equals("auxiliary") ? setup(3, 0, 7, true, "activeDrum")
                : mode.equals("land") ? setup(2, 0, 5, true) : setup(3, 0, 6, true);
        if (mode.equals("land")) addCardToZone("Darksteel Citadel", seat(original, 0), ZoneType.Battlefield);
        Game game = snapshot(original, depth);
        Player actor = seat(game, 0);
        SpellAbility chosen = revival(game, true);
        Set<Card> tapMemory = AiCardMemory.getMemorySet(actor, AiCardMemory.MemorySet.PAYS_TAP_COST);
        Set<Card> sacrificeMemory = AiCardMemory.getMemorySet(actor, AiCardMemory.MemorySet.PAYS_SAC_COST);
        Set<ManaCostBeingPaid> unpaidMemory = AiCardMemory.getMemorySet(actor, AiCardMemory.MemorySetMana.UNPAID_COSTS);
        tapMemory.add(named(game, 0, "Island", ZoneType.Battlefield));
        sacrificeMemory.add(named(game, 0, "Grizzly Bears", ZoneType.Graveyard));
        unpaidMemory.add(new ManaCostBeingPaid(ManaCost.get(99)));
        Set<Card> tapsBefore = new HashSet<>(tapMemory);
        Set<Card> sacrificesBefore = new HashSet<>(sacrificeMemory);
        Set<ManaCostBeingPaid> unpaidBefore = new HashSet<>(unpaidMemory);
        Map<Integer, Boolean> boardBefore = tapped(game);
        List<Card> eligible = actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> !card.isTapped() && (card.isArtifact() || card.isCreature())).toList();
        for (int attempt = 0; attempt < 3; attempt++) {
            Map<Card, forge.card.mana.ManaCostShard> selected = ((PlayerControllerAi) actor.getController())
                    .chooseCardsForConvokeOrImprovise(chosen, chosen.getPayCosts().getTotalMana(),
                            new forge.game.card.CardCollection(eligible), true, true, 6);
            assertTrue(selected.size() <= 6);
            assertTrue(selected.keySet().stream().allMatch(card -> card.getGame() == game && eligible.contains(card)));
            assertEquals(tapMemory, tapsBefore, "A nested mana preview cannot clear the caller's tap reservations");
            assertEquals(sacrificeMemory, sacrificesBefore);
            assertEquals(unpaidMemory, unpaidBefore, "Failed candidate probes cannot add global unpaid-cost memories");
            assertEquals(tapped(game), boardBefore);
            assertTrue(actor.getCardsIn(ZoneType.Battlefield).stream().noneMatch(Card::isUsedToPay));
            assertEquals(actor.getManaPool().totalMana(), 0);
            assertEquals(chosen.getPayCosts().getTotalMana().getCMC(), 9);
            assertEquals(chosen.getPayCosts().getMaxWaterbend(), "6");
            assertTrue(game.getStack().isEmpty());
            named(game, 0, "Spirit Water Revival", ZoneType.Hand);
        }
    }
    @Test public void previewMemoryPositiveDepth0() { previewMemory(0, "positive"); }
    @Test public void previewMemoryLandDepth0() { previewMemory(0, "land"); }
    @Test public void previewMemoryAuxiliaryDepth0() { previewMemory(0, "auxiliary"); }
    @Test public void previewMemoryPositiveDepth1() { previewMemory(1, "positive"); }
    @Test public void previewMemoryLandDepth1() { previewMemory(1, "land"); }
    @Test public void previewMemoryAuxiliaryDepth1() { previewMemory(1, "auxiliary"); }
    @Test public void previewMemoryPositiveDepth2() { previewMemory(2, "positive"); }
    @Test public void previewMemoryLandDepth2() { previewMemory(2, "land"); }
    @Test public void previewMemoryAuxiliaryDepth2() { previewMemory(2, "auxiliary"); }

    private void preserveCreatures(int depth) {
        Game original = setup(9, 0, 6);
        Game game = snapshot(original, depth);
        SpellAbility chosen = revival(game, true);
        assertTrue(ComputerUtil.playStack(chosen, seat(game, 0), game));
        assertTrue(seat(game, 0).getCardsIn(ZoneType.Battlefield).stream()
                .filter(Card::isCreature).noneMatch(Card::isTapped),
                "When mana covers the remainder, preserve creatures the existing AI kept available for combat");
        assertEquals(game.getStack().peekAbility().getPayingMana().size() + tappedArtifacts(game), 9L);
        settle(game);
        resolved(game, true);
        if (depth > 0) {
            assertEquals(tappedArtifacts(original), 0L);
            assertEquals(seat(original, 0).getCardsIn(ZoneType.Library).size(), 24);
        }
    }
    @Test public void preserveCreaturesDepth0() { preserveCreatures(0); }
    @Test public void preserveCreaturesDepth1() { preserveCreatures(1); }
    @Test public void preserveCreaturesDepth2() { preserveCreatures(2); }

    private void reserveArtifactMana(int depth) {
        Game original = setup(1, 0, 3, true);
        addCardToZone("Seat of the Synod", seat(original, 0), ZoneType.Battlefield);
        Card island = named(original, 0, "Island", ZoneType.Battlefield);
        SpellAbility manaAbility = island.getManaAbilities().get(0);
        manaAbility.setActivatingPlayer(seat(original, 0));
        assertTrue(ComputerUtil.playNoStack(seat(original, 0), manaAbility, original, false));
        assertEquals(seat(original, 0).getManaPool().totalMana(), 1);
        cast(original, 0, "Mox Amber", null, 0);
        cast(original, 0, "Jeweled Amulet", null, 0);
        cast(original, 0, "Springleaf Drum", null, 1);
        assertEquals(seat(original, 0).getManaPool().totalMana(), 0);
        assertFalse(named(original, 0, "Seat of the Synod", ZoneType.Battlefield).isTapped());
        // Finish the starting board with two independent ordinary mana sources.
        addCardToZone("Island", seat(original, 0), ZoneType.Battlefield);
        addCardToZone("Island", seat(original, 0), ZoneType.Battlefield);
        Game game = snapshot(original, depth);
        SpellAbility chosen = revival(game, true);
        assertTrue(ComputerUtil.playStack(chosen, seat(game, 0), game),
                "Keep the usable artifact mana source for the normal cost and waterbend with the other six artifacts");
        SpellAbility paid = game.getStack().peekAbility();
        assertEquals(paid.getPayingMana().size(), 3);
        Set<Card> manaSources = new HashSet<>();
        for (forge.game.mana.Mana mana : paid.getPayingMana()) manaSources.add(mana.getSourceCard());
        assertTrue(manaSources.stream().anyMatch(card -> card.getName().equals("Seat of the Synod")));
        long waterTaps = seat(game, 0).getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isArtifact() && card.isTapped() && !manaSources.contains(card)).count();
        assertEquals(waterTaps, 6L, "Mana-producing artifact taps are separate from waterbend taps");
        settle(game);
        resolved(game, true);
        if (depth > 0) {
            assertFalse(named(original, 0, "Seat of the Synod", ZoneType.Battlefield).isTapped());
            assertEquals(seat(original, 0).getCardsIn(ZoneType.Library).size(), 24);
        }
    }
    @Test public void reserveArtifactManaDepth0() { reserveArtifactMana(0); }
    @Test public void reserveArtifactManaDepth1() { reserveArtifactMana(1); }
    @Test public void reserveArtifactManaDepth2() { reserveArtifactMana(2); }
}
