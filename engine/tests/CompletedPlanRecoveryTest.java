package forge.ai;

import forge.ai.simulation.GameStateEvaluator.Score;
import forge.ai.simulation.Plan;
import forge.ai.simulation.SimulationController;
import forge.game.Game;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Interrupted search can publish completed improving decisions, never unfinished work. */
public class CompletedPlanRecoveryTest extends AITest {
    private List<SpellAbility> actions() {
        Game game = initAndCreateGame();
        return List.of(addCardToZone("Shock", game.getPlayers().get(1), ZoneType.Hand).getFirstSpellAbility());
    }

    @Test public void unfinishedFirstBranchCannotProduceFallback() {
        SimulationController controller = new SimulationController(new Score(100));
        controller.evaluateSpellAbility(actions(), 0);
        assertNull(controller.getBestCompletedPlan());
    }

    @Test public void completedImprovementSurvivesUnfinishedLaterBranch() {
        SimulationController controller = new SimulationController(new Score(100));
        List<SpellAbility> actions = actions();
        controller.evaluateSpellAbility(actions, 0);
        controller.doneEvaluating(new Score(200));
        controller.evaluateSpellAbility(actions, 0);
        Plan recovered = controller.getBestCompletedPlan();
        assertNotNull(recovered);
        assertEquals(recovered.getFinalScore().value, 200);
        assertEquals(recovered.getDecisions().size(), 1);
        assertTrue(recovered.hasNextDecision());
    }

    @Test public void unavailableOnlyGainDoesNotProduceFallback() {
        SimulationController controller = new SimulationController(new Score(100));
        controller.evaluateSpellAbility(actions(), 0);
        controller.doneEvaluating(new Score(200, 100));
        assertNull(controller.getBestCompletedPlan());
    }

    @Test public void nonImprovingCompletedBranchDoesNotProduceFallback() {
        SimulationController controller = new SimulationController(new Score(100));
        controller.evaluateSpellAbility(actions(), 0);
        controller.doneEvaluating(new Score(99));
        assertNull(controller.getBestCompletedPlan());
    }
}
