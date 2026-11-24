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
        List<String> free = List.of("Ornithopter", "Memnite", "Phyrexian Walker", "Shield Sphere", "Welding Jar", "Tormod's Crypt", "Mishra's Bauble");
        for (int index = 0; index < artifacts; index++) cast(game, 0, free.get(index), null, 0);
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
        assertEquals(paid.getPayingMana().size(), mana, "Normal mana remains payable separately from waterbend taps");
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
        boolean waterbend = !mode.equals("basic");
        boolean pending = mode.equals("pending");
        int lands = mode.equals("tax") ? 6 : mode.equals("reduction") ? 4 : 3;
        Game original = setup(lands, 0, 6);
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
        Game original = mode.equals("cap") ? setup(5, 0, 7) : mode.equals("colored") ? setup(1, 2, 7) : setup(2, 0, 7);
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
}
