package forge.ai;

import forge.LobbyPlayer;
import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.player.Player;
import forge.game.ability.ApiType;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Recording agents instrument real reveal callbacks; paid inspection is validated separately. */
public class CommanderPrivateRevealBoundaryTest extends InformationChoiceTestSupport {
    @Test public void inGameHandRevealReachesBothPlayersAndActingAgent() {
        Roster roster = roster(); Card card = hand(roster);
        roster.pod.game.getAction().revealTo(card, roster.pod.actor);
        assertEquals(roster.original.seen.size(), 1); assertEquals(roster.master.seen.size(), 1); assertEquals(roster.proxy.seen.size(), 1);
        assertTrue(remembered(roster.original, card)); assertTrue(remembered(roster.master, card)); assertTrue(remembered(roster.proxy, card));
    }
    @Test public void explicitAgentCallbackUpdatesThatAgentsOwnMemory() {
        Roster roster = roster(); Card card = hand(roster);
        roster.original.reveal(cards(card), ZoneType.Hand, card.getOwner(), null, false);
        assertTrue(remembered(roster.original, card)); assertFalse(remembered(roster.proxy, card)); assertFalse(remembered(roster.master, card));
    }
    @Test public void outsideGameInspectionDoesNotReachTheController() {
        Roster roster = roster(); Card card = addCardToZone("Sol Ring", roster.pod.actor, ZoneType.Sideboard);
        assertFalse(roster.pod.game.getCardsInGame().contains(card));
        roster.pod.game.getAction().revealTo(card, roster.pod.actor);
        assertEquals(roster.original.seen.size(), 1); assertTrue(roster.proxy.seen.isEmpty()); assertTrue(roster.master.seen.isEmpty());
    }
    @Test public void mixedZonesFilterEachCardForEachRecipient() {
        Roster roster = roster(); Card inside = hand(roster), outside = addCardToZone("Sol Ring", roster.pod.actor, ZoneType.Sideboard);
        roster.pod.game.getAction().revealTo(cards(inside, outside), roster.pod.actor);
        assertEquals(roster.original.seen.size(), 2); assertEquals(roster.proxy.seen, List.of(inside)); assertEquals(roster.master.seen, List.of(inside));
    }
    @Test public void explicitlyNamedRecipientCanStillReceiveOutsideGameInformation() {
        Roster roster = roster(); Card outside = addCardToZone("Sol Ring", roster.pod.actor, ZoneType.Sideboard);
        roster.pod.game.getAction().revealTo(outside, List.of(roster.pod.actor, roster.pod.owner));
        assertEquals(roster.original.seen, List.of(outside)); assertEquals(roster.master.seen, List.of(outside)); assertTrue(roster.proxy.seen.isEmpty());
    }
    @Test public void foreignGameCardsAreNotDelivered() {
        Roster roster = roster(); Pod foreign = pod("Index"); Card card = addCardToZone("Lightning Bolt", foreign.actor, ZoneType.Hand);
        roster.pod.game.getAction().revealTo(card, roster.pod.actor);
        assertTrue(roster.original.seen.isEmpty()); assertTrue(roster.master.seen.isEmpty()); assertTrue(roster.proxy.seen.isEmpty());
    }
    @Test public void revealCallbackCannotRedirectEarlierInformationToANewController() {
        Roster roster = roster(); Card card = hand(roster); Player next = roster.pod.game.getPlayers().get(2);
        Recorder nextMaster = new Recorder(roster.pod.game, next, next.getLobbyPlayer()); next.dangerouslySetController(nextMaster);
        Recorder nextProxy = new Recorder(roster.pod.game, roster.pod.actor, next.getLobbyPlayer());
        roster.original.onReveal = () -> roster.pod.actor.addController(roster.pod.game.getNextTimestamp(), next, nextProxy, true);
        roster.pod.game.getAction().revealTo(card, roster.pod.actor);
        assertSame(roster.pod.actor.getControllingPlayer(), next);
        assertEquals(roster.original.seen.size(), 1); assertEquals(roster.proxy.seen.size(), 1); assertEquals(roster.master.seen.size(), 1);
        assertTrue(nextMaster.seen.isEmpty()); assertTrue(nextProxy.seen.isEmpty()); assertFalse(remembered(nextProxy, card));
    }
    @Test public void authorizedFaceDownRevealIsStillPrivateToItsRecipients() {
        Roster roster = roster(); Card card = addCardToZone("Willbender", roster.pod.game.getPlayers().get(2), ZoneType.Battlefield);
        card.turnFaceDown(); assertTrue(card.isFaceDown());
        roster.pod.game.getAction().revealTo(card, roster.pod.actor);
        assertTrue(remembered(roster.original, card)); assertTrue(remembered(roster.master, card)); assertTrue(remembered(roster.proxy, card));
        assertFalse(AiCardMemory.isRememberedCard(roster.pod.game.getPlayers().get(3), card, AiCardMemory.MemorySet.REVEALED_CARDS));
    }
    @Test public void currentCardViewUpdatesTheExplicitReceivingAgent() {
        Roster roster = roster(); Card card = hand(roster);
        roster.original.reveal(List.of(card.getView()), ZoneType.Hand, card.getOwner().getView(), null, false);
        assertTrue(remembered(roster.original, card)); assertFalse(remembered(roster.proxy, card));
    }
    @Test public void copiedCardViewCannotSelectAPrivateLocalCard() {
        Roster roster = roster(); Card card = hand(roster);
        GameCopier copier = new GameCopier(roster.pod.game); copier.makeCopy(null, roster.pod.actor);
        Card foreign = copier.find(card); assertEquals(foreign.getId(), card.getId()); assertNotSame(foreign.getView(), card.getView());
        roster.original.reveal(List.of(foreign.getView()), ZoneType.Hand, foreign.getOwner().getView(), null, false);
        assertFalse(remembered(roster.original, card)); assertFalse(remembered(roster.proxy, card));
    }

    @Test public void publicRevealReachesTheOriginalActorMemory() {
        Roster roster = roster(); Card card = hand(roster);
        roster.pod.game.getAction().reveal(cards(card), ZoneType.Hand, card.getOwner(), false, null, false);
        assertTrue(remembered(roster.original, card)); assertTrue(remembered(roster.master, card)); assertTrue(remembered(roster.proxy, card));
    }
    @Test public void publicOwnerDisplaySkipDoesNotSuppressKnowledge() {
        Roster roster = roster(); Card card = roster.pod.actor.getCardsIn(ZoneType.Library).getFirst();
        roster.pod.game.getAction().reveal(cards(card), ZoneType.Library, roster.pod.actor, true, null, false);
        assertTrue(remembered(roster.original, card)); assertTrue(remembered(roster.master, card)); assertTrue(remembered(roster.proxy, card));
        assertTrue(roster.proxy.seen.isEmpty(), "The owner display skip remains a presentation choice");
    }
    @Test public void publicReturnRemembersTheNewHandObjectForOriginalActor() {
        Roster roster = roster(); Player owner = roster.pod.game.getPlayers().get(3);
        Card creature = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield); int identity = creature.getId();
        SpellAbility spell = addCardToZone("Unsummon", roster.pod.actor, ZoneType.Hand).getFirstSpellAbility();
        assertEquals(spell.getApi(), ApiType.ChangeZone); spell.setActivatingPlayer(roster.pod.actor); spell.getTargets().add(creature);
        assertTrue(ComputerUtil.playStack(spell, roster.pod.actor, roster.pod.game)); assertEquals(spell.getPayingMana().size(), 1); settle(roster.pod.game);
        Card returned = owner.getCardsIn(ZoneType.Hand).stream().filter(card -> card.getId() == identity).findFirst().orElseThrow();
        assertTrue(remembered(roster.original, returned)); assertTrue(remembered(roster.master, returned)); assertTrue(remembered(roster.proxy, returned));
    }
    @Test public void publicRevealUpdatesSuppressedAgentsWithoutPrivateHistoryCopy() {
        Roster roster = roster(); Card card = hand(roster);
        roster.pod.actor.addController(roster.pod.game.getNextTimestamp(), roster.pod.game.getPlayers().get(2));
        roster.pod.game.getAction().reveal(cards(card), ZoneType.Hand, card.getOwner(), false, null, false);
        assertTrue(remembered(roster.proxy, card)); assertTrue(remembered(roster.original, card)); assertTrue(remembered(roster.master, card));
    }
    @Test public void publicRevealRejectsForeignGameCards() {
        Roster roster = roster(); Pod foreign = pod("Index"); Card card = addCardToZone("Lightning Bolt", foreign.actor, ZoneType.Hand);
        roster.pod.game.getAction().reveal(cards(card), ZoneType.Hand, foreign.actor, false, null, false);
        assertTrue(roster.original.seen.isEmpty()); assertTrue(roster.proxy.seen.isEmpty()); assertTrue(roster.master.seen.isEmpty());
    }

    private Roster roster() {
        Pod pod = pod("Index");
        // Instrument existing player identities; this is not a replacement for the paid control fixture.
        Recorder original = new Recorder(pod.game, pod.actor, pod.actor.getLobbyPlayer()); pod.actor.dangerouslySetController(original);
        Recorder master = new Recorder(pod.game, pod.owner, pod.owner.getLobbyPlayer()); pod.owner.dangerouslySetController(master);
        Recorder proxy = new Recorder(pod.game, pod.actor, pod.owner.getLobbyPlayer());
        pod.actor.addController(pod.game.getNextTimestamp(), pod.owner, proxy, true); return new Roster(pod, original, master, proxy);
    }
    private Card hand(Roster roster) { return addCardToZone("Lightning Bolt", roster.pod.game.getPlayers().get(3), ZoneType.Hand); }
    private CardCollection cards(Card... values) {
        CardCollection cards = new CardCollection(); for (Card value : values) cards.add(value); return cards;
    }
    private boolean remembered(Recorder observer, Card card) {
        return observer.getAi().getCardMemory().isRememberedCard(card, AiCardMemory.MemorySet.REVEALED_CARDS);
    }
    private static final class Recorder extends PlayerControllerAi {
        final List<Card> seen = new ArrayList<>(); Runnable onReveal;
        Recorder(Game game, Player player, LobbyPlayer factory) { super(game, player, factory); }
        @Override public void reveal(CardCollectionView cards, ZoneType zone, Player owner, String message, boolean suffix) {
            for (Card card : cards) seen.add(card);
            Runnable callback = onReveal; onReveal = null; if (callback != null) callback.run();
            super.reveal(cards, zone, owner, message, suffix);
        }
    }
    private record Roster(Pod pod, Recorder original, Recorder master, Recorder proxy) { }
}
