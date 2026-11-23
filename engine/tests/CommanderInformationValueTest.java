package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Epistemic boundaries for bounded value; hidden identities are not strategic input. */
public class CommanderInformationValueTest extends InformationChoiceTestSupport {
    @Test public void unknownHandIdentitiesDoNotEarnInformationCredit() {
        for (String name : List.of("Counterspell", "Grizzly Bears")) {
            Pod pod = pod("Spy Network");
            addCardToZone(name, pod.owner, ZoneType.Hand);
            assertEquals(InformationEffects.knownInformationValue(pod.actor), 0);
        }
    }

    @Test public void paidKnowledgeCreditsOnlyItsAuthorizedObserver() {
        Pod pod = pod("Spy Network"); cast(pod, "Spy Network"); settle(pod.game);
        assertTrue(InformationEffects.knownInformationValue(pod.actor) > 5);
        for (Player observer : pod.game.getPlayers()) if (observer != pod.actor)
            assertEquals(InformationEffects.knownInformationValue(observer), 0);
    }

    @Test public void heuristicPlanningDoesNotEarnUnpaidInformation() { planning(null); }
    @Test public void hybridPlanningDoesNotEarnUnpaidInformation() { planning(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullPlanningDoesNotEarnUnpaidInformation() { planning(AIOption.USE_FULL_SIMULATION); }

    @Test public void copiedKnowledgeValueAndClearingStayIndependent() {
        Pod pod = pod("Spy Network"); cast(pod, "Spy Network"); settle(pod.game);
        int before = InformationEffects.knownInformationValue(pod.actor);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        copier.makeCopy(null, pod.actor); Player observer = copier.find(pod.actor);
        assertEquals(InformationEffects.knownInformationValue(observer), before);
        AiCardMemory.clearMemorySet(observer, AiCardMemory.MemorySet.REVEALED_CARDS);
        assertEquals(InformationEffects.knownInformationValue(observer), 0);
        assertEquals(InformationEffects.knownInformationValue(pod.actor), before);
        assertTrue(AiCardMemory.isLibraryPositionKnown(pod.actor, pod.actor, 0));
        assertFalse(AiCardMemory.isLibraryPositionKnown(observer, observer, 0));
    }

    @Test public void removingFreeAccessCannotManufactureKnowledgeValue() {
        Pod pod = pod("Lens of Clarity"); cast(pod, "Lens of Clarity"); settle(pod.game);
        assertTrue(pod.hidden.getView().canFaceDownBeShownTo(pod.actor.getView()));
        assertTrue(pod.ownTop.getView().canBeShownTo(pod.actor.getView()));
        pod.game.getAction().revealTo(pod.hidden, pod.actor);
        pod.game.getAction().revealTo(pod.ownTop, pod.actor);
        pod.actor.getController().observeLibraryTop(pod.actor, Map.of(0, pod.ownTop));
        int before = InformationEffects.knownInformationValue(pod.actor);
        assertTrue(before > 0);
        Card lens = pod.game.getCardState(pod.spell.getHostCard());
        for (int index = 0; index < 2; index++) addCardToZone("Plains", pod.actor, ZoneType.Battlefield);
        SpellAbility destroy = addCardToZone("Disenchant", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        destroy.setActivatingPlayer(pod.actor); destroy.getTargets().add(lens);
        assertTrue(ComputerUtil.playStack(destroy, pod.actor, pod.game));
        assertEquals(destroy.getPayingMana().size(), 2); settle(pod.game);
        assertTrue(pod.game.getCardState(lens).isInZone(ZoneType.Graveyard));
        assertFalse(pod.hidden.getView().canFaceDownBeShownTo(pod.actor.getView()));
        assertFalse(pod.ownTop.getView().canBeShownTo(pod.actor.getView()));
        assertEquals(InformationEffects.knownInformationValue(pod.actor), before);
    }

    @Test public void manyKnownHandCardsHaveBoundedInformationValue() {
        Pod pod = pod("Spy Network");
        for (int index = 0; index < 20; index++) addCardToZone("Island", pod.owner, ZoneType.Hand);
        assertEquals(InformationEffects.knownInformationValue(pod.actor), 0);
        cast(pod, "Spy Network"); settle(pod.game);
        assertTrue(known(pod.actor, pod.hand));
        int value = InformationEffects.knownInformationValue(pod.actor);
        assertTrue(value > 5 && value <= 28, "Knowledge must stay small rather than overwhelm board resources");
    }

    @Test public void identityOnlyDisclosureDoesNotInventLibraryPositionCredit() {
        Pod pod = pod("Spy Network");
        Card host = addCardToZone("Smoke Teller", pod.actor, ZoneType.Battlefield);
        host.addRemembered(pod.top);
        host.setSVar("ObserveIdentity", "DB$ LookAt | Defined$ Remembered");
        SpellAbility action = AbilityFactory.getAbility("AB$ Pump | Cost$ 0 | ValidTgts$ Player | SubAbility$ ObserveIdentity", host);
        action.setActivatingPlayer(pod.actor); action.getTargets().add(pod.owner);
        assertTrue(ComputerUtil.playStack(action, pod.actor, pod.game)); settle(pod.game);
        assertTrue(known(pod.actor, pod.top));
        assertFalse(AiCardMemory.isLibraryPositionKnown(pod.actor, pod.owner, 0));
        assertEquals(InformationEffects.knownInformationValue(pod.actor), 0);
    }

    @Test public void hiddenShuffleRemovesPositionCreditEvenWhenPhysicalTopStays() {
        Pod pod = pod("Spy Network"); cast(pod, "Spy Network"); settle(pod.game);
        int before = InformationEffects.knownInformationValue(pod.actor);
        assertTrue(AiCardMemory.isLibraryPositionKnown(pod.actor, pod.owner, 0));
        List<Card> order = new ArrayList<>();
        for (Card card : pod.owner.getCardsIn(ZoneType.Library)) order.add(card);
        Random previous = MyRandom.getRandom(); boolean sameTop = false;
        try {
            for (int seed = 0; seed < 256; seed++) {
                pod.owner.getZone(ZoneType.Library).setCards(order);
                MyRandom.setRandom(new Random(seed)); pod.owner.shuffle(null);
                if (pod.owner.getCardsIn(ZoneType.Library).get(0).getId() == pod.top.getId()) { sameTop = true; break; }
            }
        } finally { MyRandom.setRandom(previous); }
        assertTrue(sameTop);
        assertFalse(AiCardMemory.isLibraryPositionKnown(pod.actor, pod.owner, 0));
        assertTrue(InformationEffects.knownInformationValue(pod.actor) < before);
    }

    @Test public void forgettingOneInspectedCardRemovesOnlyItsPositionCredit() {
        Pod pod = pod("Spy Network"); cast(pod, "Spy Network"); settle(pod.game);
        Card top = pod.actor.getCardsIn(ZoneType.Library).get(0);
        assertTrue(known(pod.actor, top));
        int before = InformationEffects.knownInformationValue(pod.actor);
        AiCardMemory.forgetCard(pod.actor, top, AiCardMemory.MemorySet.REVEALED_CARDS);
        assertFalse(known(pod.actor, top));
        assertFalse(AiCardMemory.isLibraryPositionKnown(pod.actor, pod.actor, 0), "Forgetting an identity must not leave usable position knowledge");
        assertTrue(InformationEffects.knownInformationValue(pod.actor) < before);
        assertTrue(AiCardMemory.isLibraryPositionKnown(pod.actor, pod.owner, 0));
    }

    private void planning(AIOption mode) {
        Pod pod = pod("Spy Network");
        assertEquals(InformationEffects.knownInformationValue(pod.actor), 0);
        assertNotNull(controller(pod.actor, mode).chooseSpellAbilityToPlay());
        assertEquals(InformationEffects.knownInformationValue(pod.actor), 0);
        assertFalse(known(pod.actor, pod.hand));
        assertFalse(AiCardMemory.isLibraryPositionKnown(pod.actor, pod.actor, 0));
        assertTrue(pod.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertTrue(pod.game.getStack().isEmpty());
    }
}
