package forge.ai;

import forge.ai.simulation.GameSimulator;
import forge.ai.simulation.GameStateEvaluator;
import forge.ai.simulation.SimulationController;
import forge.card.CardStateName;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameActionUtil;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.OptionalCost;
import forge.game.spellability.OptionalCostValue;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Nested variables, colored reductions, composed costs and projected-board evaluation. */
public class CommanderCostModifierExpressionTest extends AITest {
    @Test public void nativePaymentUsesTheNestedReductionExpression() {
        Pod pod = nested();
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        for (int i = 0; i < 10 && !pod.game.getStack().isEmpty(); i++) pod.game.getStack().resolveStack();
        assertTrue(pod.game.getStack().isEmpty());
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 7);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 2L);
    }
    @Test public void previewRetainsNestedReductionVariables() {
        Pod pod = nested();
        verify(pod, pod.action, true, 2);
    }
    @Test public void previewRetainsNestedIncreaseVariables() {
        Pod pod = pod("Last Thoughts", "0", "Wastes", "Wastes");
        pod.action.putParam("RaiseCost", "GrantedIncrease");
        pod.action.setSVar("GrantedIncrease", "SVar$IncreaseBase");
        pod.action.setSVar("IncreaseBase", "Number$2");
        verify(pod, pod.action, true, 2);
    }
    @Test public void previewPreservesTheAmountOfAColoredReduction() {
        Pod pod = pod("Last Thoughts", "3 U", "Wastes", "Wastes", "Wastes");
        pod.action.putParam("ReduceCost", "U");
        pod.action.putParam("ReduceAmount", "GrantedAmount");
        pod.action.setSVar("GrantedAmount", "Number$1");
        verify(pod, pod.action, true, 3);
    }
    @Test public void aReductionAndIncreaseAreEachAppliedOnce() {
        Pod pod = pod("Last Thoughts", "3 U", "Island", "Wastes", "Mountain", "Mountain");
        pod.action.putParam("ReduceCost", "2");
        pod.action.putParam("RaiseCost", "R R");
        verify(pod, pod.action, true, 4);
    }
    @Test public void copyingTheGrantedAbilityPreservesItsPayment() {
        Pod pod = nested();
        verify(pod, pod.action.copy(), true, 2);
    }
    @Test public void theReductionExpressionUsesTheProjectedBoard() {
        Pod pod = pod("Last Thoughts", "4 U", "Island", "Wastes", "Wastes");
        Card mine = addCardToZone("Strip Mine", pod.actor, ZoneType.Battlefield);
        Card target = pod.actor.getLandsInPlay().stream().filter(card -> card.getName().equals("Wastes")).findFirst().orElseThrow();
        SpellAbility destroy = mine.getNonManaAbilities().stream().filter(ability -> ability.getApi() == ApiType.Destroy).findFirst().orElseThrow();
        destroy.setActivatingPlayer(pod.actor);
        destroy.getTargets().add(target);
        pod.action.putParam("ReduceCost", "GrantedReduction");
        pod.action.setSVar("GrantedReduction", "Count$Valid Land.YouCtrl");
        GameSimulator simulation = simulation(pod);
        assertTrue(simulation.applySpellAbility(destroy));
        assertEquals(simulation.getSimulatedGameState().getPlayer(pod.actor.getId()).getLandsInPlay().size(), 2);
        GameStateEvaluator.Score result = simulation.simulateSpellAbility(pod.action);
        assertEquals(result.value, Integer.MIN_VALUE,
                "The reduced cast needs three mana after the projected land destruction, not its original one-mana estimate");
        assertEquals(pod.actor.getLandsInPlay().size(), 4);
        assertTrue(mine.isInZone(ZoneType.Battlefield));
        assertLiveUnchanged(pod);
    }
    @Test public void theReductionCombinesWithBuybackWithoutDuplicatingIt() {
        Pod pod = pod("Sprout Swarm", "1 G", "Forest", "Wastes", "Wastes");
        List<OptionalCostValue> optional = GameActionUtil.getOptionalCostValues(pod.action);
        optional.removeIf(cost -> cost.getType() != OptionalCost.Buyback);
        assertEquals(optional.size(), 1);
        SpellAbility action = GameActionUtil.addOptionalCosts(pod.action, optional);
        action.putParam("ReduceCost", "2");
        GameSimulator simulation = verify(pod, action, true, 3);
        assertTrue(simulation.getSimulatedGameState().getCardState(simulation.getGameCopier().find(pod.card)).isInZone(ZoneType.Hand));
        assertEquals(simulation.getSimulatedGameState().getPlayer(pod.actor.getId()).getCreaturesInPlay().size(), 1);
    }
    private Pod nested() {
        Pod pod = pod("Last Thoughts", "3 U", "Island", "Wastes");
        pod.action.putParam("ReduceCost", "GrantedReduction");
        pod.action.setSVar("GrantedReduction", "SVar$ReductionBase");
        pod.action.setSVar("ReductionBase", "Number$2");
        return pod;
    }
    private GameSimulator verify(Pod pod, SpellAbility action, boolean available, int tapped) {
        GameSimulator simulation = simulation(pod);
        GameStateEvaluator.Score result = simulation.simulateSpellAbility(action);
        assertEquals(result.value != Integer.MIN_VALUE, available);
        if (available) {
            Player projected = simulation.getSimulatedGameState().getPlayer(pod.actor.getId());
            assertEquals(projected.getLandsInPlay().stream().filter(Card::isTapped).count(), (long) tapped);
        }
        assertLiveUnchanged(pod);
        return simulation;
    }
    private void assertLiveUnchanged(Pod pod) {
        assertTrue(pod.card.isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getSpellsCastThisTurn(), 0);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 8);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 0L);
        assertFalse(pod.card.getFirstSpellAbility().hasParam("ReduceCost"));
        assertFalse(pod.card.getFirstSpellAbility().hasParam("RaiseCost"));
    }
    private GameSimulator simulation(Pod pod) {
        GameStateEvaluator.Score baseline = GameSimulator.scoreForDecision(pod.game, pod.actor);
        return new GameSimulator(new SimulationController(baseline, 0), pod.game, pod.actor, null);
    }
    private Pod pod(String name, String cost, String... lands) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("expression-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Granted cost expression contracts"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        for (String land : lands) addCardToZone(land, actor, ZoneType.Battlefield);
        Card card = addCardToZone(name, actor, ZoneType.Exile);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.copyLastState();
        List<SpellAbility> offered = AbilityUtils.getSpellsFromPlayEffect(card, actor, CardStateName.Original, false, null);
        assertEquals(offered.size(), 1);
        SpellAbility action = offered.get(0).copyWithManaCostReplaced(actor, new Cost(cost, false));
        return new Pod(game, actor, card, action);
    }
    private record Pod(Game game, Player actor, Card card, SpellAbility action) { }
}
