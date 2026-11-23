package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCopyService;
import forge.game.player.Player;
import forge.game.player.PlayerController;
import forge.game.spellability.SpellAbility;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import forge.game.ability.ApiType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Scoped API guards plus paid rearrange and real-shuffle retention boundaries. */
public class CommanderLibraryObservationBoundaryTest extends InformationChoiceTestSupport {
    @Test public void exactInspectedObjectCanRefreshTimestamp() {
        Pod pod = knownPod(); Card first = top(pod); long before = first.getGameTimestamp();
        try (Zone.LibraryObservation scope = library(pod).beginLibraryObservation(prefix(pod))) {
            pod.game.getAction().moveToBottomOfLibrary(first, null);
            assertNotEquals(first.getGameTimestamp(), before);
            assertTrue(scope.permits(first)); assertTrue(scope.preservesOutside());
            pod.game.getAction().observeChosenLibraryOrder(pod.actor, pod.actor.getController(), null, cards(first), scope);
            knownPositions(pod.actor, 0, 1, 2, 7);
        }
    }
    @Test public void opaqueIdenticalResetRetiresOutsidePositions() { invalidation("reset"); }
    @Test public void opaqueShuffleRetiresOutsidePositions() { invalidation("shuffle"); }
    @Test public void uninspectedNoopReorderRetiresOutsidePositions() { invalidation("outside"); }
    @Test public void closedScopeCannotRestoreOutsidePositions() { invalidation("closed"); }
    @Test public void repeatedCloseReleasesCapacity() {
        Pod pod = knownPod();
        for (int index = 0; index < 128; index++) {
            Zone.LibraryObservation scope = library(pod).beginLibraryObservation(prefix(pod));
            assertTrue(scope.preservesOutside()); scope.close(); scope.close(); assertFalse(scope.preservesOutside());
        }
    }
    @Test public void staleCopyDoesNotAuthorizePlacement() {
        Pod pod = knownPod(); Card first = top(pod); Card stale = CardCopyService.getLKICopy(first);
        assertNotSame(stale, first);
        try (Zone.LibraryObservation scope = library(pod).beginLibraryObservation(cards(stale))) {
            assertFalse(scope.preservesOutside()); assertFalse(scope.permits(first));
            publishTop(pod, scope, first); knownPositions(pod.actor, 0);
        }
    }
    @Test public void nonPrefixObjectDoesNotAuthorizePlacement() {
        Pod pod = knownPod(); Card first = top(pod);
        try (Zone.LibraryObservation scope = library(pod).beginLibraryObservation(cards(pod.actor.getCardsIn(ZoneType.Library).get(2)))) {
            assertFalse(scope.preservesOutside()); publishTop(pod, scope, first); knownPositions(pod.actor, 0);
        }
    }
    @Test public void foreignZoneScopeCannotRestoreLibraryPositions() {
        Pod pod = knownPod(); Card first = top(pod);
        try (Zone.LibraryObservation scope = pod.actor.getZone(ZoneType.Hand).beginLibraryObservation(prefix(pod))) {
            assertFalse(scope.preservesOutside()); publishTop(pod, scope, first); knownPositions(pod.actor, 0);
        }
    }
    @Test public void foreignGameScopeCannotRestoreLibraryPositions() {
        Pod original = knownPod(); Pod other = knownPod();
        try (Zone.LibraryObservation scope = library(original).beginLibraryObservation(prefix(original))) {
            publishTop(other, scope, top(other)); knownPositions(other.actor, 0);
            knownPositions(original.actor, 0, 1, 2, 3);
        }
    }
    @Test public void overflowInvalidatesEveryActiveScope() {
        Pod pod = knownPod(); List<Zone.LibraryObservation> scopes = new ArrayList<>();
        try {
            for (int index = 0; index < 65; index++) scopes.add(library(pod).beginLibraryObservation(prefix(pod)));
            for (Zone.LibraryObservation scope : scopes) assertFalse(scope.preservesOutside());
            publishTop(pod, scopes.get(0), top(pod)); knownPositions(pod.actor, 0);
        } finally { scopes.forEach(Zone.LibraryObservation::close); }
        try (Zone.LibraryObservation fresh = library(pod).beginLibraryObservation(prefix(pod))) {
            assertTrue(fresh.preservesOutside());
        }
    }
    @Test public void stateRestoreInvalidatesLiveScope() {
        Pod pod = knownPod();
        try (Zone.LibraryObservation scope = library(pod).beginLibraryObservation(prefix(pod))) {
            GameCopier copier = new GameCopier(pod.game).withStack(true);
            copier.makeCopy(null, pod.actor);
            // Restore an empty library history using a native mapped copy, without fabricating moves.
            Zone restored = copier.find(pod.actor).getZone(ZoneType.Library);
            try (Zone.LibraryObservation copiedScope = restored.beginLibraryObservation(new CardCollection(copier.find(pod.actor).getCardsIn(ZoneType.Library, 1)))) {
                restored.copyTurnHistoryFrom(restored, new forge.game.IEntityMap() {
                    @Override public Game getGame() { return copier.find(pod.actor).getGame(); }
                    @Override public forge.game.GameObject map(forge.game.GameObject value) { return value; }
                });
                assertFalse(copiedScope.preservesOutside());
            }
            assertTrue(scope.preservesOutside());
        }
    }
    @Test public void nestedWiderChoiceInvalidatesNarrowerScope() {
        Pod pod = knownPod();
        try (Zone.LibraryObservation outer = library(pod).beginLibraryObservation(prefix(pod));
                Zone.LibraryObservation inner = library(pod).beginLibraryObservation(new CardCollection(pod.actor.getCardsIn(ZoneType.Library, 3)))) {
            Card second = pod.actor.getCardsIn(ZoneType.Library).get(1);
            pod.game.getAction().moveToLibrary(second, 0, null);
            assertFalse(outer.preservesOutside()); assertTrue(inner.preservesOutside());
        }
    }
    @Test public void copiedScopesAndKnowledgeStayIndependent() {
        Pod pod = knownPod();
        try (Zone.LibraryObservation original = library(pod).beginLibraryObservation(prefix(pod))) {
            GameCopier copier = new GameCopier(pod.game).withStack(true); Game game = copier.makeCopy(null, pod.actor);
            Player actor = copier.find(pod.actor); Card first = actor.getCardsIn(ZoneType.Library).get(0);
            try (Zone.LibraryObservation copied = actor.getZone(ZoneType.Library).beginLibraryObservation(cards(first))) {
                game.getAction().moveToBottomOfLibrary(first, null);
                game.getAction().observeChosenLibraryOrder(actor, actor.getController(), null, cards(first), copied);
                knownPositions(actor, 0, 1, 2, 7); knownPositions(pod.actor, 0, 1, 2, 3);
                assertTrue(original.preservesOutside());
            }
        }
    }
    @Test public void malformedObservationCannotRestoreOutsidePairs() { badObservation(true); }
    @Test public void mismatchedRevisionCannotRestoreOutsidePairs() { badObservation(false); }
    @Test public void oversizedDeclaredPlacementCannotInventKnownPositions() {
        Pod pod = knownPod(); Card first = top(pod); long revision = library(pod).getLibraryOrderRevision();
        int before = pod.actor.getCardsIn(ZoneType.Library).size();
        pod.game.getAction().moveToLibrary(first, 0, null);
        addCardToZone("Forest", pod.actor, ZoneType.Library);
        // The resulting size alone matches, but one inspected card cannot announce two placements.
        pod.actor.getController().observeLibraryOrder(new PlayerController.LibraryOrderObservation(pod.actor,
                revision, before, 1, 2, 0, true), Map.of(0, first));
        knownPositions(pod.actor, 0);
    }
    @Test public void paidRearrangeRetainsKnownUninspectedCard() { rearrange(false); }
    @Test public void copiedPaidRearrangeRetainsKnownUninspectedCard() { rearrange(true); }
    @Test public void actualShuffleRetiresPriorOutsidePositions() { realShuffle(false); }
    @Test public void copiedActualShuffleRetiresPriorOutsidePositions() { realShuffle(true); }

    private void invalidation(String kind) {
        Pod pod = knownPod(); Card first = top(pod);
        try (Zone.LibraryObservation scope = library(pod).beginLibraryObservation(prefix(pod))) {
            if (kind.equals("reset")) library(pod).setCards(new CardCollection(pod.actor.getCardsIn(ZoneType.Library)));
            if (kind.equals("shuffle")) library(pod).shuffle();
            if (kind.equals("outside")) library(pod).reorder(pod.actor.getCardsIn(ZoneType.Library).get(4), 4);
            if (kind.equals("closed")) scope.close();
            assertFalse(scope.preservesOutside());
            publishTop(pod, scope, first); knownPositions(pod.actor, 0);
        }
    }
    private void badObservation(boolean malformed) {
        Pod pod = knownPod(); Card first = top(pod); long revision = library(pod).getLibraryOrderRevision();
        int size = pod.actor.getCardsIn(ZoneType.Library).size();
        pod.game.getAction().moveToLibrary(first, 0, null);
        pod.actor.getController().observeLibraryOrder(new PlayerController.LibraryOrderObservation(pod.actor,
                malformed ? revision : revision + 1, size, malformed ? -1 : 1, 1, 0, true), Map.of(0, first));
        knownPositions(pod.actor, 0);
    }
    private void rearrange(boolean copied) {
        Pod pod = knownPod();
        for (int index = 0; index < 2; index++) addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        Card host = addCardToZone("Soothsaying", pod.actor, ZoneType.Battlefield);
        SpellAbility action = host.getNonManaAbilities().stream().filter(sa -> sa.getApi() == ApiType.RearrangeTopOfLibrary).findFirst().orElseThrow();
        action.setActivatingPlayer(pod.actor); action.setXManaCostPaid(3);
        assertTrue(action.canPlay()); assertTrue(ComputerUtil.playStack(action, pod.actor, pod.game));
        assertEquals(action.getPayingMana().size(), 3);
        Player actor = pod.actor; Game game = pod.game;
        if (copied) { GameCopier copier = new GameCopier(game).withStack(true); game = copier.makeCopy(null, actor); actor = copier.find(actor); }
        settle(game); knownPositions(actor, 0, 1, 2, 3);
        if (copied) knownPositions(pod.actor, 0, 1, 2, 3);
    }
    private void realShuffle(boolean copied) {
        Pod pod = pod("Augury Owl");
        for (int index = 0; index < 12; index++) addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        Card firstLand = top(pod), secondLand = pod.actor.getCardsIn(ZoneType.Library).get(1);
        Card colossus = addCardToZone("Darksteel Colossus", pod.actor, ZoneType.Library);
        for (int index = 0; index < 3; index++) addCardToZone("Forest", pod.actor, ZoneType.Library);
        List<Card> order = new ArrayList<>(List.of(colossus, firstLand, secondLand));
        for (Card card : pod.actor.getCardsIn(ZoneType.Library)) if (!order.contains(card)) order.add(card);
        library(pod).setCards(order); addCardToZone("Psychogenic Probe", pod.owner, ZoneType.Battlefield);
        pod.game.getAction().checkStateEffects(true); pod.game.getTriggerHandler().resetActiveTriggers(); pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(pod.spell, pod.actor, pod.game)); assertEquals(pod.spell.getPayingMana().size(), 2); settle(pod.game);
        int size = pod.actor.getCardsIn(ZoneType.Library).size(); knownPositions(pod.actor, 0, size - 2, size - 1);
        List<Card> lands = new ArrayList<>(); for (Card land : pod.actor.getLandsInPlay()) lands.add(land);
        Set<Card> keep = new HashSet<>();
        for (Card land : lands) if (!land.isTapped() && keep.size() < 2) keep.add(land);
        assertEquals(keep.size(), 2);
        for (Card land : lands) if (!keep.contains(land)) pod.game.getAction().moveToGraveyard(land, null);
        assertTrue(ComputerUtil.scryWillMoveCardToBottomOfLibrary(pod.actor, colossus));
        SpellAbility action = addCardToZone("Otherworldly Gaze", pod.actor, ZoneType.Hand).getFirstSpellAbility(); action.setActivatingPlayer(pod.actor);
        int before = pod.actor.getLife(); assertTrue(ComputerUtil.playStack(action, pod.actor, pod.game)); assertEquals(action.getPayingMana().size(), 1);
        Player actor = pod.actor; Game game = pod.game;
        if (copied) { GameCopier copier = new GameCopier(game).withStack(true); game = copier.makeCopy(null, actor); actor = copier.find(actor); }
        settle(game); assertEquals(actor.getLife(), before - 2); knownPositions(actor, 0, 1);
        if (copied) { assertEquals(pod.actor.getLife(), before); knownPositions(pod.actor, 0, size - 2, size - 1); }
        for (Player observer : game.getPlayers()) if (observer != actor)
            for (int index = 0; index < actor.getCardsIn(ZoneType.Library).size(); index++)
                assertFalse(AiCardMemory.isLibraryPositionKnown(observer, actor, index));
    }
    private Pod knownPod() { Pod pod = pod("Spy Network"); cast(pod, "Spy Network"); settle(pod.game); knownPositions(pod.actor, 0, 1, 2, 3); return pod; }
    private Zone library(Pod pod) { return pod.actor.getZone(ZoneType.Library); }
    private Card top(Pod pod) { return pod.actor.getCardsIn(ZoneType.Library).get(0); }
    private CardCollection prefix(Pod pod) { return cards(top(pod)); }
    private CardCollection cards(Card card) { return new CardCollection(List.of(card)); }
    private void publishTop(Pod pod, Zone.LibraryObservation scope, Card first) {
        pod.game.getAction().moveToLibrary(first, 0, null);
        pod.game.getAction().observeChosenLibraryOrder(pod.actor, pod.actor.getController(), cards(first), null, scope);
    }
    private void knownPositions(Player actor, int... indices) {
        Set<Integer> expected = new HashSet<>(); for (int index : indices) expected.add(index);
        for (int index = 0; index < actor.getCardsIn(ZoneType.Library).size(); index++) {
            assertEquals(AiCardMemory.isLibraryPositionKnown(actor, actor, index), expected.contains(index), "Position " + index);
            if (expected.contains(index)) assertTrue(known(actor, actor.getCardsIn(ZoneType.Library).get(index)));
        }
    }
}
