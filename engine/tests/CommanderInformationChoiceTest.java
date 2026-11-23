package forge.ai;

import org.testng.annotations.Test;

/** Actual choices, paid privacy and no-repeat contracts in supplied partial pods. */
public class CommanderInformationChoiceTest extends InformationChoiceTestSupport {
    @Test public void heuristicChoosesSpyNetworkForAnUnknownEnemyHand() { choice("Spy Network", null); }
    @Test public void hybridChoosesSpyNetworkForAnUnknownEnemyHand() { choice("Spy Network", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullChoosesSpyNetworkForAnUnknownEnemyHand() { choice("Spy Network", AIOption.USE_FULL_SIMULATION); }
    @Test public void heuristicChoosesLensForAnUnknownFace() { choice("Lens of Clarity", null); }
    @Test public void hybridChoosesLensForAnUnknownFace() { choice("Lens of Clarity", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullChoosesLensForAnUnknownFace() { choice("Lens of Clarity", AIOption.USE_FULL_SIMULATION); }
    @Test public void heuristicRecognizesSpyNetworkAdmission() { admission("Spy Network", null); }
    @Test public void hybridRecognizesSpyNetworkAdmission() { admission("Spy Network", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullRecognizesSpyNetworkAdmission() { admission("Spy Network", AIOption.USE_FULL_SIMULATION); }
    @Test public void heuristicRecognizesLensAdmission() { admission("Lens of Clarity", null); }
    @Test public void hybridRecognizesLensAdmission() { admission("Lens of Clarity", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullRecognizesLensAdmission() { admission("Lens of Clarity", AIOption.USE_FULL_SIMULATION); }
    @Test public void paidSpyNetworkResolvesPrivately() { paid("Spy Network", false, false); }
    @Test public void copiedPendingSpyNetworkKeepsItsOwnDisclosure() { paid("Spy Network", true, false); }
    @Test public void counteredSpyNetworkDisclosesNothing() { paid("Spy Network", false, true); }
    @Test public void unaffordableSpyNetworkDisclosesNothing() { unaffordable("Spy Network"); }
    @Test public void paidLensGrantsOnlyItsAuthorizedAccess() { paid("Lens of Clarity", false, false); }
    @Test public void copiedPendingLensKeepsItsOwnAccess() { paid("Lens of Clarity", true, false); }
    @Test public void counteredLensGrantsNoAccess() { paid("Lens of Clarity", false, true); }
    @Test public void unaffordableLensGrantsNoAccess() { unaffordable("Lens of Clarity"); }
    @Test public void heuristicDoesNotCastARedundantLens() { redundant("Lens of Clarity", null); }
    @Test public void hybridDoesNotCastARedundantLens() { redundant("Lens of Clarity", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullDoesNotCastARedundantLens() { redundant("Lens of Clarity", AIOption.USE_FULL_SIMULATION); }
    @Test public void heuristicDoesNotRepeatAnUnchangedSpyInspection() { redundant("Spy Network", null); }
    @Test public void hybridDoesNotRepeatAnUnchangedSpyInspection() { redundant("Spy Network", AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullDoesNotRepeatAnUnchangedSpyInspection() { redundant("Spy Network", AIOption.USE_FULL_SIMULATION); }
}
