package forge.ai;

import forge.ai.simulation.GameSimulator;
import forge.ai.simulation.GameStateEvaluator;
import forge.ai.simulation.SimulationController;
import forge.card.CardStateName;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Compare native actual payment with previews of a grant's dynamic cost modifiers. */
public class CommanderCostModifierPreviewTest extends AITest {
    @Test public void nativePaymentUsesTheGrantedGenericReduction() {
        actual(pod("reduced"), true, 2);
    }
    @Test public void previewUsesTheGrantedGenericReduction() {
        preview(pod("reduced"), true, 2);
    }
    @Test public void nativePaymentRequiresTheRaisedColoredCost() {
        actual(pod("missing-red"), false, 0);
    }
    @Test public void previewRequiresTheRaisedColoredCost() {
        preview(pod("missing-red"), false, 0);
    }
    @Test public void nativePaymentPaysTheAffordableRaisedColoredCost() {
        actual(pod("paid-red"), true, 2);
    }
    @Test public void previewPaysTheAffordableRaisedColoredCost() {
        preview(pod("paid-red"), true, 2);
    }
    private void actual(Pod pod, boolean available, int tapped) {
        assertEquals(ComputerUtil.playStack(pod.action, pod.actor, pod.game), available);
        for (int i = 0; i < 10 && !pod.game.getStack().isEmpty(); i++) pod.game.getStack().resolveStack();
        assertTrue(pod.game.getStack().isEmpty());
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), available ? 7 : 8);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), (long) tapped);
        assertTrue(pod.game.getCardState(pod.card).isInZone(available ? ZoneType.Graveyard : ZoneType.Exile));
    }
    private void preview(Pod pod, boolean available, int tapped) {
        GameStateEvaluator.Score baseline = GameSimulator.scoreForDecision(pod.game, pod.actor);
        GameSimulator simulation = new GameSimulator(new SimulationController(baseline, 0), pod.game, pod.actor, null);
        GameStateEvaluator.Score result = simulation.simulateSpellAbility(pod.action);
        assertEquals(result.value != Integer.MIN_VALUE, available);
        if (available) {
            Player projected = simulation.getSimulatedGameState().getPlayer(pod.actor.getId());
            assertEquals(projected.getCardsIn(ZoneType.Library).size(), 7);
            assertEquals(projected.getLandsInPlay().stream().filter(Card::isTapped).count(), (long) tapped);
        }
        assertTrue(pod.card.isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 8);
        assertEquals(pod.actor.getSpellsCastThisTurn(), 0);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 0L);
    }
    private Pod pod(String kind) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("modifier-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Granted modifier contracts"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        if (kind.equals("reduced")) {
            addCardToZone("Island", actor, ZoneType.Battlefield);
            addCardToZone("Wastes", actor, ZoneType.Battlefield);
        } else {
            for (int i = 0; i < 2; i++) addCardToZone(kind.equals("paid-red") ? "Mountain" : "Wastes", actor, ZoneType.Battlefield);
        }
        Card card = addCardToZone("Last Thoughts", actor, ZoneType.Exile);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.copyLastState();
        List<SpellAbility> offered = AbilityUtils.getSpellsFromPlayEffect(card, actor, CardStateName.Original, false, null);
        assertEquals(offered.size(), 1);
        SpellAbility action = offered.get(0).copyWithManaCostReplaced(actor, new Cost(kind.equals("reduced") ? "3 U" : "0", false));
        action.putParam(kind.equals("reduced") ? "ReduceCost" : "RaiseCost", kind.equals("reduced") ? "2" : "R R");
        assertTrue(action.isCastFromPlayEffect());
        return new Pod(game, actor, card, action);
    }
    private record Pod(Game game, Player actor, Card card, SpellAbility action) { }
}
