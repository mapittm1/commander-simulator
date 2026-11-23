package forge.ai;

import forge.LobbyPlayer;
import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.player.DelayedReveal;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Instrument delayed callbacks and metadata; real paid controls are validated separately. */
public class CommanderDelayedRevealBoundaryTest extends InformationChoiceTestSupport {
    @Test public void currentAgentDelayedHandInspectionReachesAllRightfulRecipients() {
        Roster roster=roster(); Card card=hand(roster);
        roster.proxy.reveal(delayed(card, ZoneType.Hand));
        for (Recorder agent : List.of(roster.original,roster.master,roster.proxy)) assertEquals(observed(agent),List.of(card));
    }
    @Test public void outsideGameDelayedInspectionReachesOnlyOriginalPlayer() {
        Roster roster=roster(); Card card=outside(roster);
        roster.proxy.reveal(delayed(card,ZoneType.Sideboard));
        assertEquals(observed(roster.original),List.of(card));
        assertTrue(observed(roster.master).isEmpty()); assertTrue(observed(roster.proxy).isEmpty());
    }
    @Test public void mixedInsideOutsideInspectionFiltersEveryRecipient() {
        Roster roster=roster(); Card inside=hand(roster),outside=outside(roster);
        roster.proxy.reveal(new DelayedReveal(cards(inside,outside),Set.of(ZoneType.Hand,ZoneType.Sideboard),roster.pod.actor.getView(),"mixed"));
        assertEquals(observed(roster.original),List.of(inside,outside));
        assertEquals(observed(roster.master),List.of(inside)); assertEquals(observed(roster.proxy),List.of(inside));
        assertMetadata(roster.original); assertMetadata(roster.master); assertMetadata(roster.proxy);
    }
    @Test public void callbackControlChangeCannotRedirectLaterZoneGroups() {
        Roster roster=roster(); Card hand=hand(roster),library=roster.pod.ownTop;
        Replacement next=replacement(roster);
        roster.original.onReveal=()->replaceController(roster,next);
        roster.proxy.reveal(new DelayedReveal(cards(hand,library),Set.of(ZoneType.Hand,ZoneType.Library),roster.pod.actor.getView(),"zones"));
        assertSame(roster.pod.actor.getControllingPlayer(),next.player);
        for (Recorder agent : List.of(roster.original,roster.master,roster.proxy)) {
            assertEquals(observed(agent),List.of(hand,library)); assertMetadata(agent);
        }
        assertTrue(observed(next.master).isEmpty()); assertTrue(observed(next.proxy).isEmpty());
    }
    @Test public void inactiveAgentCannotSendPreparedViewsToALaterController() {
        Roster roster=roster(); Card card=hand(roster); DelayedReveal reveal=delayed(card,ZoneType.Hand);
        Replacement next=replacement(roster); replaceController(roster,next); roster.proxy.reveal(reveal);
        for (Recorder agent : List.of(roster.original,roster.master,roster.proxy,next.master,next.proxy)) assertTrue(observed(agent).isEmpty());
    }
    @Test public void copiedViewCannotSelectALocalCardWithMatchingId() {
        Roster roster=roster(); Card card=hand(roster);
        GameCopier copier=new GameCopier(roster.pod.game); copier.makeCopy(null,roster.pod.actor);
        Card foreign=copier.find(card); assertEquals(foreign.getId(),card.getId()); assertNotSame(foreign.getView(),card.getView());
        roster.proxy.reveal(delayed(foreign,ZoneType.Hand));
        for (Recorder agent : List.of(roster.original,roster.master,roster.proxy)) assertTrue(observed(agent).isEmpty());
    }
    @Test public void foreignGameViewsCannotDiscloseLocalCards() {
        Roster roster=roster(); Pod foreign=pod("Index");
        Card foreignCard=addCardToZone("Lightning Bolt",foreign.owner,ZoneType.Hand);
        roster.proxy.reveal(delayed(foreignCard,ZoneType.Hand));
        for (Recorder agent : List.of(roster.original,roster.master,roster.proxy)) assertTrue(observed(agent).isEmpty());
    }
    @Test public void declaredZoneCannotAuthorizeAnotherZone() {
        Roster roster=roster(); Card card=hand(roster);
        roster.proxy.reveal(delayed(card,ZoneType.Library));
        for (Recorder agent : List.of(roster.original,roster.master,roster.proxy)) assertTrue(observed(agent).isEmpty());
    }
    @Test public void preparedViewMovedOutOfDeclaredZoneIsNotDisclosed() {
        Roster roster=roster(); Card card=hand(roster); DelayedReveal reveal=delayed(card,ZoneType.Hand);
        roster.pod.game.getAction().moveToLibrary(card,-1,null); roster.proxy.reveal(reveal);
        for (Recorder agent : List.of(roster.original,roster.master,roster.proxy)) assertTrue(observed(agent).isEmpty());
    }
    @Test public void sameZoneDifferentOwnersKeepSeparateDisplayMetadata() {
        Roster roster=roster(); Card first=hand(roster);
        Card second=addCardToZone("Sol Ring",roster.pod.game.getPlayers().get(2),ZoneType.Hand);
        roster.proxy.reveal(new DelayedReveal(cards(first,second),ZoneType.Hand,first.getOwner().getView()));
        for (Recorder agent : List.of(roster.original,roster.master,roster.proxy)) {
            assertEquals(agent.deliveries.size(),2); assertEquals(observed(agent),List.of(first,second)); assertMetadata(agent);
        }
    }
    @Test public void singleZoneConstructorRetainsTheDisplayPrefix() {
        Roster roster=roster(); Card card=hand(roster);
        roster.proxy.reveal(new DelayedReveal(cards(card),ZoneType.Hand,card.getOwner().getView(),"single prefix"));
        for (Recorder agent : List.of(roster.original,roster.master,roster.proxy)) {
            assertEquals(agent.deliveries.size(),1); assertEquals(agent.deliveries.get(0).message,"single prefix");
        }
    }
    @Test public void multiZonePrefixAndSuffixSurviveCapturedDelivery() {
        Roster roster=roster(); Card card=hand(roster),library=roster.pod.ownTop;
        roster.proxy.reveal(new DelayedReveal(cards(card,library),Set.of(ZoneType.Hand,ZoneType.Library),roster.pod.actor.getView(),"multi prefix"));
        for (Recorder agent : List.of(roster.original,roster.master,roster.proxy)) {
            assertEquals(agent.deliveries.size(),2); assertMetadata(agent);
            for (Delivery delivery : agent.deliveries) { assertEquals(delivery.message,"multi prefix"); assertTrue(delivery.suffix); }
        }
    }
    @Test public void freshRevealUsesCurrentRightsWithoutCopyingOldHistory() {
        Roster roster=roster(); Card earlier=hand(roster);
        roster.proxy.reveal(delayed(earlier,ZoneType.Hand));
        Card fresh=addCardToZone("Sol Ring",roster.pod.game.getPlayers().get(3),ZoneType.Hand);
        DelayedReveal prepared=delayed(fresh,ZoneType.Hand);
        Replacement next=replacement(roster); replaceController(roster,next); next.proxy.reveal(prepared);
        assertFalse(observed(next.master).contains(earlier)); assertFalse(observed(next.proxy).contains(earlier));
        assertEquals(observed(next.master),List.of(fresh)); assertEquals(observed(next.proxy),List.of(fresh));
        assertEquals(observed(roster.master),List.of(earlier)); assertEquals(observed(roster.proxy),List.of(earlier));
        assertEquals(observed(roster.original),List.of(earlier,fresh));
    }

    private Roster roster() {
        Pod pod=pod("Index");
        Recorder original=new Recorder(pod.game,pod.actor,pod.actor.getLobbyPlayer()); pod.actor.dangerouslySetController(original);
        Recorder master=new Recorder(pod.game,pod.owner,pod.owner.getLobbyPlayer()); pod.owner.dangerouslySetController(master);
        Recorder proxy=new Recorder(pod.game,pod.actor,pod.owner.getLobbyPlayer()); long timestamp=pod.game.getNextTimestamp();
        pod.actor.addController(timestamp,pod.owner,proxy,true); return new Roster(pod,original,master,proxy,timestamp);
    }
    private Card hand(Roster roster) { return addCardToZone("Lightning Bolt",roster.pod.game.getPlayers().get(3),ZoneType.Hand); }
    private Card outside(Roster roster) {
        Card card=addCardToZone("Sol Ring",roster.pod.actor,ZoneType.Sideboard);
        assertFalse(roster.pod.game.getCardsInGame().contains(card)); return card;
    }
    private DelayedReveal delayed(Card card,ZoneType zone) { return new DelayedReveal(cards(card),zone,card.getOwner().getView()); }
    private CardCollection cards(Card... values) { CardCollection result=new CardCollection(); for (Card value : values) result.add(value); return result; }
    private List<Card> observed(Recorder agent) { return agent.deliveries.stream().flatMap(delivery->delivery.cards.stream()).toList(); }
    private void assertMetadata(Recorder agent) {
        for (Delivery delivery : agent.deliveries) for (Card card : delivery.cards) {
            assertEquals(delivery.zone,card.getZone().getZoneType()); assertSame(delivery.owner,card.getOwner());
        }
    }
    private Replacement replacement(Roster roster) {
        Player next=roster.pod.game.getPlayers().get(2);
        Recorder master=new Recorder(roster.pod.game,next,next.getLobbyPlayer()); next.dangerouslySetController(master);
        return new Replacement(next,master,new Recorder(roster.pod.game,roster.pod.actor,next.getLobbyPlayer()));
    }
    private void replaceController(Roster roster,Replacement next) {
        roster.pod.actor.removeController(roster.timestamp);
        roster.pod.actor.addController(roster.pod.game.getNextTimestamp(),next.player,next.proxy,true);
    }
    private static final class Recorder extends PlayerControllerAi {
        final List<Delivery> deliveries=new ArrayList<>(); Runnable onReveal;
        Recorder(Game game,Player player,LobbyPlayer factory) { super(game,player,factory); }
        @Override public void reveal(CardCollectionView cards,ZoneType zone,Player owner,String message,boolean suffix) {
            deliveries.add(new Delivery(List.copyOf(cards),zone,owner,message,suffix));
            Runnable callback=onReveal; onReveal=null; if (callback!=null) callback.run();
            super.reveal(cards,zone,owner,message,suffix);
        }
    }
    private record Delivery(List<Card> cards,ZoneType zone,Player owner,String message,boolean suffix) { }
    private record Roster(Pod pod,Recorder original,Recorder master,Recorder proxy,long timestamp) { }
    private record Replacement(Player player,Recorder master,Recorder proxy) { }
}
