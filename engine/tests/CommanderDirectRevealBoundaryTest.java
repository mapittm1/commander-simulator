package forge.ai;

import forge.LobbyPlayer;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Callback instrumentation checks disclosure boundaries; paid control is validated separately. */
public class CommanderDirectRevealBoundaryTest extends InformationChoiceTestSupport {
    @Test public void handInspectionKeepsItsAudienceWhenCallbackChangesControl() {
        Roster roster = roster();
        Player target = roster.pod.game.getPlayers().get(3);
        Card hidden = addCardToZone("Lightning Bolt", target, ZoneType.Hand);
        Replacement replacement = replacement(roster);
        roster.original.onReveal = () -> replaceController(roster, replacement);
        SpellAbility peek = addCardToZone("Peek", roster.pod.actor, ZoneType.Hand).getFirstSpellAbility();
        peek.setActivatingPlayer(roster.pod.actor);
        peek.getTargets().add(target);
        assertTrue(ComputerUtil.playStack(peek, roster.pod.actor, roster.pod.game));
        assertEquals(peek.getPayingMana().size(), 1);
        settle(roster.pod.game);
        assertSame(roster.pod.actor.getControllingPlayer(), replacement.player);
        for (Recorder observer : List.of(roster.original, roster.master, roster.proxy)) {
            assertTrue(remembered(observer, hidden));
        }
        assertFalse(remembered(replacement.master, hidden));
        assertFalse(remembered(replacement.proxy, hidden));
    }

    @Test public void privateLibraryPositionsUseTheOriginalInspectionAudience() {
        Roster roster = roster();
        Replacement replacement = replacement(roster);
        roster.original.onReveal = () -> replaceController(roster, replacement);
        resolveDeceiver(roster, false);
        assertSame(roster.pod.actor.getControllingPlayer(), replacement.player);
        for (Recorder observer : List.of(roster.original, roster.master, roster.proxy)) {
            assertTrue(positionKnown(observer, roster.pod.actor));
            assertFalse(observer.getAi().getCardMemory().isLibraryPositionKnown(roster.pod.actor, 1));
        }
        assertFalse(positionKnown(replacement.master, roster.pod.actor));
        assertFalse(positionKnown(replacement.proxy, roster.pod.actor));
    }

    @Test public void publicLibraryRevealUpdatesSuppressedAgentPositions() {
        Roster roster = roster();
        Replacement replacement = replacement(roster);
        roster.pod.actor.addController(roster.pod.game.getNextTimestamp(), replacement.player, replacement.proxy, true);
        resolveDeceiver(roster, true);
        assertTrue(remembered(roster.proxy, roster.pod.ownTop));
        assertTrue(positionKnown(roster.proxy, roster.pod.actor), "Publicly declared top position also reaches a dormant agent");
        assertTrue(positionKnown(roster.original, roster.pod.actor));
        assertTrue(positionKnown(replacement.proxy, roster.pod.actor));
    }

    @Test public void publicLibraryAudienceIsCapturedBeforeDisplayChangesControl() {
        Roster roster = roster();
        Replacement replacement = replacement(roster);
        roster.proxy.onReveal = () -> replaceController(roster, replacement);
        resolveDeceiver(roster, true);
        assertSame(roster.pod.actor.getControllingPlayer(), replacement.player);
        assertTrue(remembered(roster.proxy, roster.pod.ownTop));
        assertTrue(positionKnown(roster.proxy, roster.pod.actor), "An agent present at public revelation retains its declared position");
        assertTrue(positionKnown(roster.original, roster.pod.actor));
        assertTrue(positionKnown(replacement.master, roster.pod.actor));
    }

    private void resolveDeceiver(Roster roster, boolean publicReveal) {
        Card source = addCardToZone("Brutal Deceiver", roster.pod.actor, ZoneType.Battlefield);
        SpellAbility ability = source.getNonManaAbilities().stream()
                .filter(action -> publicReveal ? action.hasParam("NoPeek") : action.hasParam("NoReveal"))
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(roster.pod.actor);
        assertTrue(ComputerUtil.playStack(ability, roster.pod.actor, roster.pod.game));
        assertEquals(ability.getPayingMana().size(), publicReveal ? 2 : 1);
        settle(roster.pod.game);
    }

    private Roster roster() {
        Pod pod = pod("Index");
        Recorder original = new Recorder(pod.game, pod.actor, pod.actor.getLobbyPlayer());
        pod.actor.dangerouslySetController(original);
        Recorder master = new Recorder(pod.game, pod.owner, pod.owner.getLobbyPlayer());
        pod.owner.dangerouslySetController(master);
        Recorder proxy = new Recorder(pod.game, pod.actor, pod.owner.getLobbyPlayer());
        long timestamp = pod.game.getNextTimestamp();
        pod.actor.addController(timestamp, pod.owner, proxy, true);
        return new Roster(pod, original, master, proxy, timestamp);
    }

    private Replacement replacement(Roster roster) {
        Player next = roster.pod.game.getPlayers().get(2);
        Recorder master = new Recorder(roster.pod.game, next, next.getLobbyPlayer());
        next.dangerouslySetController(master);
        Recorder proxy = new Recorder(roster.pod.game, roster.pod.actor, next.getLobbyPlayer());
        return new Replacement(next, master, proxy);
    }

    private void replaceController(Roster roster, Replacement replacement) {
        roster.pod.actor.removeController(roster.timestamp);
        roster.pod.actor.addController(roster.pod.game.getNextTimestamp(), replacement.player, replacement.proxy, true);
    }

    private boolean remembered(Recorder observer, Card card) {
        return observer.getAi().getCardMemory().isRememberedCard(card, AiCardMemory.MemorySet.REVEALED_CARDS);
    }

    private boolean positionKnown(Recorder observer, Player owner) {
        return observer.getAi().getCardMemory().isLibraryPositionKnown(owner, 0);
    }

    private static final class Recorder extends PlayerControllerAi {
        final List<Card> seen = new ArrayList<>();
        Runnable onReveal;
        Recorder(Game game, Player player, LobbyPlayer factory) { super(game, player, factory); }
        @Override public void reveal(CardCollectionView cards, ZoneType zone, Player owner, String message, boolean suffix) {
            for (Card card : cards) seen.add(card);
            Runnable callback = onReveal;
            onReveal = null;
            if (callback != null) callback.run();
            super.reveal(cards, zone, owner, message, suffix);
        }
    }

    private record Roster(Pod pod, Recorder original, Recorder master, Recorder proxy, long timestamp) { }
    private record Replacement(Player player, Recorder master, Recorder proxy) { }
}
