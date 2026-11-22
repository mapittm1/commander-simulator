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
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
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

/** Paid preview boundaries, including nonmana costs and native scripted grant resolution. */
public class CommanderGrantedCostBoundaryTest extends AITest {
    @Test public void aReplacementCostHigherThanPrintedIsNotUndercharged() {
        Pod pod = pod("Last Thoughts", 0, 4);
        preview(pod, grant(pod, "5"), false);
    }
    @Test public void lifeReplacementCostIsPaidOnlyInThePreview() {
        Pod pod = pod("Last Thoughts", 0, 0);
        GameSimulator simulation = preview(pod, grant(pod, "PayLife<3>"), true);
        Player projected = simulation.getSimulatedGameState().getPlayer(pod.actor.getId());
        assertEquals(projected.getLife(), 37);
        assertEquals(projected.getCardsIn(ZoneType.Library).size(), 7);
        assertEquals(pod.actor.getLife(), 40);
    }
    @Test public void anUnaffordableLifeReplacementIsRejected() {
        Pod pod = pod("Last Thoughts", 0, 0);
        pod.actor.setLife(2, null);
        preview(pod, grant(pod, "PayLife<3>"), false);
        assertEquals(pod.actor.getLife(), 2);
    }
    @Test public void discardReplacementCostDoesNotMutateTheLiveHand() {
        Pod pod = pod("Last Thoughts", 0, 0);
        Card payment = addCardToZone("Forest", pod.actor, ZoneType.Hand);
        GameSimulator simulation = preview(pod, grant(pod, "Discard<1/Card>"), true);
        Game projected = simulation.getSimulatedGameState();
        assertTrue(projected.getCardState(simulation.getGameCopier().find(payment)).isInZone(ZoneType.Graveyard));
        assertTrue(payment.isInZone(ZoneType.Hand));
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), 1);
    }
    @Test public void mandatorySacrificeSurvivesReplacingTheManaCost() {
        Pod pod = pod("Altar's Reap", 0, 0);
        Card payment = addCardToZone("Coral Merfolk", pod.actor, ZoneType.Battlefield);
        GameSimulator simulation = preview(pod, grant(pod, "0"), true);
        Game projected = simulation.getSimulatedGameState();
        assertTrue(projected.getCardState(simulation.getGameCopier().find(payment)).isInZone(ZoneType.Graveyard));
        assertEquals(projected.getPlayer(pod.actor.getId()).getCardsIn(ZoneType.Library).size(), 6);
        assertTrue(payment.isInZone(ZoneType.Battlefield));
    }
    @Test public void aZeroReplacementDoesNotWaiveAnUnpayableSacrifice() {
        Pod pod = pod("Altar's Reap", 0, 0);
        Card payment = addCardToZone("Coral Merfolk", pod.actor, ZoneType.Battlefield);
        SpellAbility action = grant(pod, "0");
        pod.game.getAction().moveToGraveyard(payment, null);
        preview(pod, action, false);
        assertTrue(payment.isInZone(ZoneType.Graveyard));
    }
    @Test public void buybackIsPaidExactlyOnceAndReturnsTheOriginalCard() {
        Pod pod = pod("Sprout Swarm", 3, 0);
        SpellAbility action = grant(pod, "0");
        List<OptionalCostValue> optional = GameActionUtil.getOptionalCostValues(action);
        optional.removeIf(cost -> cost.getType() != OptionalCost.Buyback);
        assertEquals(optional.size(), 1);
        action = GameActionUtil.addOptionalCosts(action, optional);
        assertEquals(action.getPayCosts().getTotalMana().getCMC(), 3);
        GameSimulator simulation = preview(pod, action, true);
        Game projected = simulation.getSimulatedGameState();
        Player actor = projected.getPlayer(pod.actor.getId());
        assertTrue(projected.getCardState(simulation.getGameCopier().find(pod.card)).isInZone(ZoneType.Hand));
        assertEquals(actor.getCreaturesInPlay().size(), 1);
        assertEquals(actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 3L);
    }
    @Test public void wizardSpellbookNativeOneManaBranchCastsTheCopy() {
        Pod pod = pod("Last Thoughts", 1, 0);
        ai(pod.actor).setUseSimulation(AIOption.USE_FULL_SIMULATION);
        Card book = addCardToZone("Wizard's Spellbook", pod.actor, ZoneType.Battlefield);
        book.addRemembered(pod.card);
        SpellAbility branch = AbilityFactory.getAbility(book.getSVar("PlayCost1"), book);
        branch.setActivatingPlayer(pod.actor);
        AbilityUtils.resolve(branch);
        for (int i = 0; i < 20 && !pod.game.getStack().isEmpty(); i++) pod.game.getStack().resolveStack();
        assertTrue(pod.game.getStack().isEmpty());
        assertEquals(pod.actor.getSpellsCastThisTurn(), 1);
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 7);
        assertTrue(pod.card.isInZone(ZoneType.Exile));
        assertTrue(pod.actor.getLandsInPlay().getFirst().isTapped());
    }
    private GameSimulator preview(Pod pod, SpellAbility action, boolean available) {
        GameStateEvaluator.Score baseline = GameSimulator.scoreForDecision(pod.game, pod.actor);
        GameSimulator simulation = new GameSimulator(new SimulationController(baseline, 0), pod.game, pod.actor, null);
        GameStateEvaluator.Score result = simulation.simulateSpellAbility(action);
        assertEquals(result.value != Integer.MIN_VALUE, available);
        assertTrue(pod.card.isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 8);
        assertEquals(pod.actor.getSpellsCastThisTurn(), 0);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 0L);
        return simulation;
    }
    private SpellAbility grant(Pod pod, String cost) {
        List<SpellAbility> offered = AbilityUtils.getSpellsFromPlayEffect(pod.card, pod.actor, CardStateName.Original, false, null);
        assertEquals(offered.size(), 1);
        SpellAbility action = offered.get(0).copyWithManaCostReplaced(pod.actor, new Cost(cost, false));
        assertTrue(action.isCastFromPlayEffect());
        return action;
    }
    private AiController ai(Player player) { return ((PlayerControllerAi) player.getController()).getAi(); }
    private Pod pod(String name, int wastes, int islands) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("cost-boundary-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Granted cost boundary contracts"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        for (Player player : game.getPlayers()) {
            player.setLife(40, null);
            for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        }
        for (int i = 0; i < wastes; i++) addCardToZone("Wastes", actor, ZoneType.Battlefield);
        for (int i = 0; i < islands; i++) addCardToZone("Island", actor, ZoneType.Battlefield);
        Card card = addCardToZone(name, actor, ZoneType.Exile);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.copyLastState();
        return new Pod(game, actor, card);
    }
    private record Pod(Game game, Player actor, Card card) { }
}
