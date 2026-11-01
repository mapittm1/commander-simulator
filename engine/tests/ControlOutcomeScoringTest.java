package forge.ai;

import forge.ai.simulation.GameStateEvaluator.Score;
import forge.ai.simulation.PlayerControlPlanner.Assessment;
import forge.ai.simulation.PlayerControlPlanner.Window;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Discounted terminal results remain terminal, while avoiding a loss is not a claimed win. */
public class ControlOutcomeScoringTest {
    @Test public void discountedLossCannotBecomeUsefulMaterialGain() {
        Score loss = new Score(Integer.MIN_VALUE + 2001);
        Assessment result = new Assessment(new Window(loss, true, false),
                new Window(new Score(-10000), false, false));
        assertFalse(result.useful());
        assertFalse(result.acceptable());
        assertSame(result.relativeTo(new Score(10)), loss);
    }

    @Test public void nativeDiscountedWinIsPreserved() {
        Score win = new Score(Integer.MAX_VALUE - 2000);
        Assessment result = new Assessment(new Window(win, false, true),
                new Window(new Score(100), false, false));
        assertTrue(result.useful());
        assertSame(result.relativeTo(new Score(10)), win);
    }

    @Test public void avertingFutureLossDoesNotFabricateWin() {
        Assessment result = new Assessment(new Window(new Score(-9000), false, false),
                new Window(new Score(Integer.MIN_VALUE + 2001), true, false));
        assertTrue(result.useful());
        assertEquals(result.relativeTo(new Score(100)).value, 100100);
    }

    @Test public void neutralForecastDoesNotSpendResources() {
        Assessment result = new Assessment(new Window(new Score(100), false, false),
                new Window(new Score(100), false, false));
        assertFalse(result.useful());
        assertTrue(result.acceptable());
        assertEquals(result.relativeTo(new Score(500)).value, 500);
    }
}
