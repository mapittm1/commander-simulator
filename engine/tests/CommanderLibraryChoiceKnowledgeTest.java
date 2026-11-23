package forge.ai;

import org.testng.annotations.Test;

/** Sixteen retained native paid library-choice contracts. */
public class CommanderLibraryChoiceKnowledgeTest extends LibraryChoiceTestSupport {
    @Test public void paidScryTopRemembersChosenPositions() { exercise(false, false, "top"); }
    @Test public void copiedPendingScryTopRemembersChosenPositions() { exercise(false, true, "top"); }
    @Test public void paidScryBottomRemembersChosenPositions() { exercise(false, false, "bottom"); }
    @Test public void copiedPendingScryBottomRemembersChosenPositions() { exercise(false, true, "bottom"); }
    @Test public void paidMixedScryRemembersChosenPositions() { exercise(false, false, "mixed"); }
    @Test public void copiedPendingMixedScryRemembersChosenPositions() { exercise(false, true, "mixed"); }
    @Test public void paidSurveilTopRemembersChosenPositions() { exercise(true, false, "top"); }
    @Test public void copiedPendingSurveilTopRemembersChosenPositions() { exercise(true, true, "top"); }
    @Test public void paidMixedSurveilRemembersChosenPositions() { exercise(true, false, "mixed"); }
    @Test public void copiedPendingMixedSurveilRemembersChosenPositions() { exercise(true, true, "mixed"); }
    @Test public void counteredCreatureDoesNotScryOrDisclose() { countered(false); }
    @Test public void counteredSurveilDoesNotDisclose() { countered(true); }
    @Test public void scryChoicesDoNotDiscloseToOpponents() { privacy(false); }
    @Test public void surveilChoicesDoNotDiscloseToOpponents() { privacy(true); }
    @Test public void unpaidCreatureCannotScry() { unaffordable(false); }
    @Test public void unpaidSurveilCannotDisclose() { unaffordable(true); }
}
