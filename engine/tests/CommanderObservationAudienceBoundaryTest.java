package forge.ai;

import forge.game.GameAction;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.player.Player;
import forge.game.player.PlayerController;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Structural control-layer boundaries; real paid control is covered by the companion fixture. */
public class CommanderObservationAudienceBoundaryTest extends InformationChoiceTestSupport {
    @Test public void ordinaryInspectionDoesNotReachOtherPlayers() {
        Pod pod = pod("Index"); inspect(pod, 2);
        assertTrue(known(pod.actor, pod.actor, 1));
        for (Player player : pod.game.getPlayers()) if (player != pod.actor) assertFalse(known(player, pod.actor, 0));
    }
    @Test public void startingControlDoesNotCopyEarlierPrivateObservations() {
        Pod pod = pod("Index"); Player owner = pod.game.getPlayers().get(3);
        pod.game.getAction().observeInspectedLibraryPrefix(owner, pod.actor.getController(), prefix(owner, 1));
        PlayerControllerAi original = (PlayerControllerAi) pod.actor.getController();
        pod.actor.addController(pod.game.getNextTimestamp(), pod.owner);
        assertTrue(original.getAi().getCardMemory().isLibraryPositionKnown(owner, 0));
        assertFalse(known(pod.actor, owner, 0)); assertFalse(known(pod.owner, owner, 0));
    }
    @Test public void suppressedControlLayerDoesNotReceiveFreshInspection() {
        Pod pod = pod("Index"); pod.actor.addController(pod.game.getNextTimestamp(), pod.owner);
        PlayerControllerAi previous = (PlayerControllerAi) pod.actor.getController(); inspect(pod, 1);
        Player next = pod.game.getPlayers().get(2); pod.actor.addController(pod.game.getNextTimestamp(), next); inspect(pod, 2);
        assertTrue(known(next, pod.actor, 1)); assertFalse(known(pod.owner, pod.actor, 1));
        assertTrue(previous.getAi().getCardMemory().isLibraryPositionKnown(pod.actor, 0));
        assertFalse(previous.getAi().getCardMemory().isLibraryPositionKnown(pod.actor, 1));
    }
    @Test public void capturedAudienceDoesNotGrantEarlierOrderToALaterController() {
        Pod pod = pod("Index"); PlayerControllerAi original = (PlayerControllerAi) pod.actor.getController();
        long first = pod.game.getNextTimestamp(); pod.actor.addController(first, pod.owner);
        GameAction.LibraryObservers observers = pod.game.getAction().captureLibraryObservers(pod.actor.getController());
        CardCollection cards = prefix(pod.actor, 1); Card card = cards.getFirst();
        try (Zone.LibraryObservation scope = pod.actor.getZone(ZoneType.Library).beginLibraryObservation(cards)) {
            pod.game.getAction().observeInspectedLibraryPrefixForObservers(pod.actor, observers, cards);
            pod.actor.removeController(first); Player next = pod.game.getPlayers().get(2); pod.actor.addController(pod.game.getNextTimestamp(), next);
            pod.game.getAction().moveToLibrary(card, 0, null);
            pod.game.getAction().observeChosenLibraryOrderForObservers(pod.actor, observers, cards, null, scope);
            assertTrue(original.getAi().getCardMemory().isLibraryPositionKnown(pod.actor, 0));
            assertTrue(known(pod.owner, pod.actor, 0)); assertFalse(known(next, pod.actor, 0)); assertFalse(known(pod.actor, pod.actor, 0));
        }
    }
    @Test public void inactiveAgentCannotCaptureANewAudience() {
        Pod pod = pod("Index"); pod.actor.addController(pod.game.getNextTimestamp(), pod.owner);
        PlayerController previous = pod.actor.getController(); pod.actor.addController(pod.game.getNextTimestamp(), pod.game.getPlayers().get(2));
        GameAction.LibraryObservers stale = pod.game.getAction().captureLibraryObservers(previous);
        pod.game.getAction().observeInspectedLibraryPrefixForObservers(pod.actor, stale, prefix(pod.actor, 2));
        for (Player player : pod.game.getPlayers()) assertFalse(known(player, pod.actor, 0));
    }
    @Test public void foreignGameCaptureCannotDiscloseCards() {
        Pod first = pod("Index"), second = pod("Index");
        GameAction.LibraryObservers foreign = first.game.getAction().captureLibraryObservers(first.actor.getController());
        second.game.getAction().observeInspectedLibraryPrefixForObservers(second.actor, foreign, prefix(second.actor, 1));
        assertFalse(known(first.actor, first.actor, 0)); assertFalse(known(first.actor, second.actor, 0)); assertFalse(known(second.actor, second.actor, 0));
    }
    @Test public void cyclicControlGraphTerminatesWithoutUnrelatedObservers() {
        Pod pod = pod("Index"); PlayerControllerAi first = (PlayerControllerAi) pod.actor.getController(), second = (PlayerControllerAi) pod.owner.getController();
        pod.actor.addController(pod.game.getNextTimestamp(), pod.owner); pod.owner.addController(pod.game.getNextTimestamp(), pod.actor);
        inspect(pod, 2);
        assertTrue(first.getAi().getCardMemory().isLibraryPositionKnown(pod.actor, 1));
        assertTrue(second.getAi().getCardMemory().isLibraryPositionKnown(pod.actor, 1));
        assertTrue(known(pod.actor, pod.actor, 1)); assertTrue(known(pod.owner, pod.actor, 1));
        for (Player player : pod.game.getPlayers()) if (player != pod.actor && player != pod.owner) assertFalse(known(player, pod.actor, 0));
    }
    @Test public void blindDrawStillUpdatesEarlierAgentsWithoutGivingNewPositions() {
        Pod pod = pod("Index"); pod.actor.addController(pod.game.getNextTimestamp(), pod.owner);
        PlayerControllerAi earlier = (PlayerControllerAi) pod.actor.getController(); inspect(pod, 2);
        Player next = pod.game.getPlayers().get(2); pod.actor.addController(pod.game.getNextTimestamp(), next);
        assertEquals(pod.actor.drawCards(1).size(), 1);
        assertTrue(earlier.getAi().getCardMemory().isLibraryPositionKnown(pod.actor, 0));
        assertTrue(known(pod.owner, pod.actor, 0)); assertFalse(known(next, pod.actor, 0)); assertFalse(known(pod.actor, pod.actor, 0));
    }
    @Test public void recipientListCannotBeExtendedByTheCaller() {
        Pod pod = pod("Index"); java.util.List<PlayerController> agents = pod.actor.getInGameObservationControllers(pod.actor.getController());
        assertThrows(UnsupportedOperationException.class, () -> agents.add(pod.owner.getController()));
        inspect(pod, 1); assertFalse(known(pod.owner, pod.actor, 0));
    }
    private void inspect(Pod pod, int count) {
        pod.game.getAction().observeInspectedLibraryPrefix(pod.actor, pod.actor.getController(), prefix(pod.actor, count));
    }
    private CardCollection prefix(Player owner, int count) {
        return new CardCollection(owner.getCardsIn(ZoneType.Library, count));
    }
    private boolean known(Player observer, Player owner, int index) {
        return AiCardMemory.isLibraryPositionKnown(observer, owner, index);
    }
}
