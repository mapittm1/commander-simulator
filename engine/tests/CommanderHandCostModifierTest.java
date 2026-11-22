package forge.ai;

import forge.ai.simulation.GameSimulator;
import forge.ai.simulation.GameStateEvaluator;
import forge.ai.simulation.SimulationController;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Native reveal admission can assess a modified hand spell before its effect grant exists. */
public class CommanderHandCostModifierTest extends AITest {
    @Test public void nativePaymentUsesTheChosenHandSpellReduction() {
        Pod pod = pod();
        assertFalse(pod.action.isCastFromPlayEffect());
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        for (int i = 0; i < 10 && !pod.game.getStack().isEmpty(); i++) pod.game.getStack().resolveStack();
        assertTrue(pod.game.getStack().isEmpty());
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 6);
        assertTrue(pod.actor.getLandsInPlay().getFirst().isTapped());
    }
    @Test public void previewUsesTheChosenHandSpellReduction() {
        Pod pod = pod();
        assertFalse(pod.action.isCastFromPlayEffect());
        GameStateEvaluator.Score baseline = GameSimulator.scoreForDecision(pod.game, pod.actor);
        GameSimulator simulation = new GameSimulator(new SimulationController(baseline, 0), pod.game, pod.actor, null);
        assertNotEquals(simulation.simulateSpellAbility(pod.action).value, Integer.MIN_VALUE);
        Player projected = simulation.getSimulatedGameState().getPlayer(pod.actor.getId());
        assertEquals(projected.getCardsIn(ZoneType.Library).size(), 6);
        assertTrue(projected.getLandsInPlay().getFirst().isTapped());
        assertTrue(pod.card.isInZone(ZoneType.Hand));
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 8);
        assertEquals(pod.actor.getSpellsCastThisTurn(), 0);
        assertFalse(pod.actor.getLandsInPlay().getFirst().isTapped());
        assertFalse(pod.card.getFirstSpellAbility().hasParam("ReduceCost"));
    }
    private Pod pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("hand-modifier-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Hand-spell modifier contract"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        Card card = addCardToZone("Quick Study", actor, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.copyLastState();
        SpellAbility action = card.getFirstSpellAbility().copy(actor);
        action.putParam("ReduceCost", "2");
        return new Pod(game, actor, card, action);
    }
    private record Pod(Game game, Player actor, Card card, SpellAbility action) { }
}
