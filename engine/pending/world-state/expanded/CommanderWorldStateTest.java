package forge.ai;

import org.testng.annotations.Test;

public class CommanderWorldStateTest extends WorldTestSupport {
    @Test public void originalPaidWorldGetsTimestamp() { oneWorld(); }
    @Test public void originalPhasingRetainsLaterWorld() { advanceAndCheckSurvivor(twoWorldsWithOnePhasedOut()); }
    @Test public void oneCopyPreservesWorldTimestamp() { timestampCopy(1); }
    @Test public void twoCopiesPreserveWorldTimestamp() { timestampCopy(2); }
    @Test public void oneCopyOriginalAdvancesFirst() { copiedSurvivor(1, false); }
    @Test public void twoCopiesOriginalAdvancesFirst() { copiedSurvivor(2, false); }
    @Test public void oneCopyAdvancesBeforeOriginal() { copiedSurvivor(1, true); }
    @Test public void twoCopiesAdvanceBeforeOriginal() { copiedSurvivor(2, true); }
}
