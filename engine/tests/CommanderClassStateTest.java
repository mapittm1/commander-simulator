package forge.ai;

import org.testng.annotations.Test;

/** Retain all original paid baseline cases after extracting shared setup. */
public class CommanderClassStateTest extends ClassTestSupport {
    @Test public void originalPaidLevelTwo() { paid(2); }
    @Test public void originalPaidLevelThree() { paid(3); }
    @Test public void oneSnapshotRetainsLevelTwo() { copied(2, 1); }
    @Test public void twoSnapshotsRetainLevelTwo() { copied(2, 2); }
    @Test public void oneSnapshotRetainsLevelThree() { copied(3, 1); }
    @Test public void twoSnapshotsRetainLevelThree() { copied(3, 2); }
}
