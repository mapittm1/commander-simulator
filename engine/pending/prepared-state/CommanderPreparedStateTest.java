package forge.ai;

import org.testng.annotations.Test;

public class CommanderPreparedStateTest extends PreparedTestSupport {
    @Test public void originalPreparedState() { prepared(); }
    @Test public void originalLeavesPlayCleanup() { leave(prepared()); }
    @Test public void oneCopyPreparedState() { copies(1, 0); }
    @Test public void twoCopiesPreparedState() { copies(2, 0); }
    @Test public void oneCopyLeavesOriginalPrepared() { copies(1, 1); }
    @Test public void twoCopiesLeaveOriginalPrepared() { copies(2, 1); }
    @Test public void originalLeavesOneCopyPrepared() { copies(1, 2); }
    @Test public void originalLeavesTwoCopiesPrepared() { copies(2, 2); }
}
