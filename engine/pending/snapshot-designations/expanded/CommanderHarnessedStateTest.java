package forge.ai;

import org.testng.annotations.Test;

/** Retain all original paid baseline cases after extracting shared setup. */
public class CommanderHarnessedStateTest extends HarnessedTestSupport {
    @Test public void mindStoneOriginalPaysHarnessCosts() { paid(false); }
    @Test public void soulStoneOriginalPaysHarnessCosts() { paid(true); }
    @Test public void mindStoneOneSimulationCopyRetainsDesignation() { copied(false, 1); }
    @Test public void mindStoneTwoSimulationCopiesRetainDesignation() { copied(false, 2); }
    @Test public void soulStoneOneSimulationCopyRetainsDesignation() { copied(true, 1); }
    @Test public void soulStoneTwoSimulationCopiesRetainDesignation() { copied(true, 2); }
}
