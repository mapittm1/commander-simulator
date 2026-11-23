package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Bounded supplied-pod behavior contracts; these do not prescribe every strategic choice. */
public class CommanderInformationOrderingTest extends InformationChoiceTestSupport {




    @Test public void paidSpyRemembersItsResultingOwnTopPosition() {
        Pod pod = pod("Spy Network"); cast(pod, "Spy Network"); settle(pod.game);
        Card top = pod.actor.getCardsIn(ZoneType.Library).get(0);
        assertTrue(known(pod.actor, top), "The actor just looked at and ordered these actual cards");
    }
    @Test public void copiedPendingSpyRemembersOnlyItsOwnRearrangement() {
        Pod pod = pod("Spy Network"); cast(pod, "Spy Network");
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy(null, pod.actor); Player actor = copier.find(pod.actor);
        settle(copy);
        assertTrue(known(actor, actor.getCardsIn(ZoneType.Library).get(0)), "Copied resolution must retain the authorized chosen order");
        assertFalse(known(pod.actor, pod.actor.getCardsIn(ZoneType.Library).get(0)));
        assertEquals(pod.game.getStack().size(), 1);
    }
    @Test public void counteredSpyDoesNotDiscloseOwnOrdering() {
        Pod pod = pod("Spy Network"); cast(pod, "Spy Network");
        for (int index = 0; index < 2; index++) addCardToZone("Island", pod.owner, ZoneType.Battlefield);
        pod.game.getPhaseHandler().setPriority(pod.owner);
        SpellAbility response = addCardToZone("Counterspell", pod.owner, ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(pod.owner); response.getTargets().add(pod.game.getStack().peekAbility());
        assertTrue(ComputerUtil.playStack(response, pod.owner, pod.game));
        assertEquals(response.getPayingMana().size(), 2); settle(pod.game);
        assertFalse(known(pod.actor, pod.actor.getCardsIn(ZoneType.Library).get(0)));
    }
    @Test public void privateRearrangementDoesNotDiscloseOwnLibraryToOpponents() {
        Pod pod = pod("Spy Network"); cast(pod, "Spy Network"); settle(pod.game);
        Card top = pod.actor.getCardsIn(ZoneType.Library).get(0);
        for (Player observer : pod.game.getPlayers()) if (observer != pod.actor) {
            assertFalse(known(observer, top));
            assertFalse(top.getView().canBeShownTo(observer.getView()));
        }
    }

}
