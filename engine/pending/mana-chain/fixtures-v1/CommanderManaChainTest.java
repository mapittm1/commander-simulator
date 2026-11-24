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
        addCardToZone("Concentrate", actor(game), ZoneType.Hand);
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
}
