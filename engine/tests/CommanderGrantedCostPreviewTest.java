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

/** Native granted-cost contracts; do not infer broad card support from these selected cases. */
public class CommanderGrantedCostPreviewTest extends AITest {
    @Test public void nativeGrantedOriginalCanPayItsReplacementCost() {
        Pod pod = pod("1");
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        for (int i = 0; i < 10 && !pod.game.getStack().isEmpty(); i++) pod.game.getStack().resolveStack();
        assertTrue(pod.game.getStack().isEmpty());
        assertTrue(pod.game.getCardState(pod.card).isInZone(ZoneType.Graveyard));
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 7);
        assertTrue(pod.land.isTapped());
    }
    @Test public void previewPaysTheAffordableGrantedReplacementCost() { verify("1", true); }
    @Test public void previewRetainsAZeroReplacementCostWithoutAWaiverMarker() { verify("0", true); }
    @Test public void unaffordableGrantedReplacementCostRemainsUnavailable() { verify("5", false); }
    private void verify(String cost, boolean available) {
        Pod pod = pod(cost);
        GameStateEvaluator.Score baseline = GameSimulator.scoreForDecision(pod.game, pod.actor);
        GameSimulator simulation = new GameSimulator(new SimulationController(baseline, 0), pod.game, pod.actor, null);
        GameStateEvaluator.Score result = simulation.simulateSpellAbility(pod.action);
        assertEquals(result.value != Integer.MIN_VALUE, available,
                "The preview must use the actual cost granted by the effect, not the printed cost");
        if (available) {
            Game projected = simulation.getSimulatedGameState();
            assertEquals(projected.getPlayer(pod.actor.getId()).getCardsIn(ZoneType.Library).size(), 7);
            assertTrue(projected.getCardState(simulation.getGameCopier().find(pod.card)).isInZone(ZoneType.Graveyard));
        }
        assertTrue(pod.card.isInZone(ZoneType.Exile));
        assertFalse(pod.land.isTapped());
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 8);
        assertEquals(pod.actor.getSpellsCastThisTurn(), 0);
    }
    private Pod pod(String cost) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("granted-cost-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Granted-cost preview contracts"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        Card land = addCardToZone("Wastes", actor, ZoneType.Battlefield);
        Card card = addCardToZone("Last Thoughts", actor, ZoneType.Exile);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.copyLastState();
        List<SpellAbility> offered = AbilityUtils.getSpellsFromPlayEffect(card, actor, CardStateName.Original, false, null);
        assertEquals(offered.size(), 1);
        SpellAbility action = offered.get(0).copyWithManaCostReplaced(actor, new Cost(cost, false));
        assertTrue(action.isCastFromPlayEffect());
        return new Pod(game, actor, card, land, action);
    }
    private record Pod(Game game, Player actor, Card card, Card land, SpellAbility action) { }
}
