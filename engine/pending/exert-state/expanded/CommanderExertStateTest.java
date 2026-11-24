package forge.ai;

import org.testng.annotations.Test;

public class CommanderExertStateTest extends ExertTestSupport {
    @Test public void originalPaysExertManaAndCastsCreature() { checkState(paid()); }
    @Test public void originalSkipsOnlyFirstOwnerUntap() { checkUntaps(paid()); }
    @Test public void oneCopyRetainsExertState() { stateCopy(1); }
    @Test public void twoCopiesRetainExertState() { stateCopy(2); }
    @Test public void oneCopyOriginalAdvancesFirst() { pairedUntaps(1, false); }
    @Test public void twoCopiesOriginalAdvancesFirst() { pairedUntaps(2, false); }
    @Test public void oneCopyAdvancesBeforeOriginal() { pairedUntaps(1, true); }
    @Test public void twoCopiesAdvanceBeforeOriginal() { pairedUntaps(2, true); }
}
