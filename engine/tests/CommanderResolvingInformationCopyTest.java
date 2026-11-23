package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.card.Card;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Resolving-stack transitions must not turn public spells into private-zone knowledge. */
public class CommanderResolvingInformationCopyTest extends InformationChoiceTestSupport {
    @Test public void pendingPublicSpellDoesNotEarnHandKnowledge() {
        Pod pod = pod("Spy Network");
        pending(pod, ZoneType.Hand);
        assertEquals(InformationEffects.knownInformationValue(pod.actor), 0,
                "A spell on the stack is not known information about an opponent's hand");
    }

    @Test public void resolvingCopyWithoutStackPreservesUnknownHandValue() {
        Pod pod = pod("Spy Network");
        pending(pod, ZoneType.Hand);
        GameCopier copier = new GameCopier(pod.game).withStack(false);
        copier.makeCopy(null, pod.actor);
        int original = InformationEffects.knownInformationValue(pod.actor);
        int copied = InformationEffects.knownInformationValue(copier.find(pod.actor));
        System.out.println("RESOLVING_HAND original=" + original + "; copied=" + copied);
        assertEquals(original, copied, "A resolving copy must preserve information value");
        assertEquals(original, 0);
    }

    @Test public void resolvingCopyRetainsPaidKnowledgeWithoutPublicSpellCredit() {
        Pod pod = pod("Spy Network");
        cast(pod, "Spy Network"); settle(pod.game);
        int paid = InformationEffects.knownInformationValue(pod.actor);
        assertTrue(paid > 0);
        pending(pod, ZoneType.Hand);
        GameCopier copier = new GameCopier(pod.game).withStack(false);
        copier.makeCopy(null, pod.actor);
        assertTrue(known(pod.actor, pod.hand));
        assertTrue(known(copier.find(pod.actor), copier.find(pod.hand)));
        int original = InformationEffects.knownInformationValue(pod.actor);
        int copied = InformationEffects.knownInformationValue(copier.find(pod.actor));
        System.out.println("RESOLVING_PAID paid=" + paid + "; original=" + original + "; copied=" + copied);
        assertEquals(original, paid, "Existing authorized hand and library knowledge remains valuable");
        assertEquals(copied, paid);
    }

    @Test public void pendingPublicSpellDoesNotEarnLibraryPositionKnowledge() {
        Pod pod = pod("Spy Network");
        pending(pod, ZoneType.Library);
        assertEquals(InformationEffects.knownInformationValue(pod.actor), 0,
                "Stack visibility cannot establish the identity of a library slot");
    }

    private void pending(Pod pod, ZoneType formerZone) {
        Card card = addCardToZone("Runeclaw Bear", pod.owner, formerZone);
        SpellAbility spell = card.getFirstSpellAbility();
        spell.setActivatingPlayer(pod.owner);
        // Match the upstream resolving-stack fixture, including its transitional zone collection.
        pod.game.getStackZone().add(card);
        pod.game.getStack().add(spell);
        pod.game.getStack().setResolving(true);
        assertTrue(card.isInZone(ZoneType.Stack));
        assertTrue(pod.owner.getCardsIn(formerZone).stream().anyMatch(entry -> entry == card));
        assertTrue(card.getView().canBeShownTo(pod.actor.getView()));
        System.out.println("RESOLVING_TRANSITION former=" + formerZone + "; actual=" + card.getZone().getZoneType());
    }
}
