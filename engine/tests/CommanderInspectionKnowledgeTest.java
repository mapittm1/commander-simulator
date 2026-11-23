package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Knowledge utility and no-payment boundaries; score units are a bounded heuristic. */
public class CommanderInspectionKnowledgeTest extends InspectionTestSupport {
    @Test
    public void unseenFacesDoNotEarnValueFromTheirConcealedIdentity() {
        for (String secret : List.of("Willbender", "Fathom Seer")) {
            Pod pod = pod("Smoke Teller", 2, secret);
            assertEquals(InspectionKnowledge.knownFaceDownValue(pod.actor), 0);
            assertTrue(InspectionKnowledge.needsInspection(pod.hidden, pod.actor));
        }
    }

    @Test
    public void paidInspectionCreditsOnlyItsAuthorizedObserver() {
        Pod pod = pod("Smoke Teller", 2);
        inspect(pod, pod.hidden);
        assertTrue(InspectionKnowledge.knownFaceDownValue(pod.actor) > 0);
        for (Player player : pod.game.getPlayers()) {
            if (player != pod.actor) assertEquals(InspectionKnowledge.knownFaceDownValue(player), 0);
        }
    }

    @Test
    public void heuristicDoesNotPayForFreeInspection() {
        freeInspection(null);
    }
    @Test
    public void hybridDoesNotPayForFreeInspection() {
        freeInspection(AIOption.USE_HYBRID_SIMULATION);
    }
    @Test
    public void fullDoesNotPayForFreeInspection() {
        freeInspection(AIOption.USE_FULL_SIMULATION);
    }

    @Test
    public void retainedKnowledgeDoesNotGainValueWhenFreeAccessEnds() {
        Pod pod = pod("Smoke Teller", 2);
        Card lens = lens(pod);
        pod.game.getAction().revealTo(pod.hidden, pod.actor); // Authorized by the actual Lens permission.
        assertTrue(known(pod.actor, pod.hidden));
        int before = InspectionKnowledge.knownFaceDownValue(pod.actor);
        assertTrue(before > 0, "Free authorized access is information already available before a paid look");
        for (int index = 0; index < 2; index++) addCardToZone("Plains", pod.actor, ZoneType.Battlefield);
        pod.game.getPhaseHandler().setPriority(pod.actor);
        SpellAbility destroy = addCardToZone("Disenchant", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        destroy.setActivatingPlayer(pod.actor);
        destroy.getTargets().add(lens);
        assertTrue(destroy.canPlay());
        assertTrue(ComputerUtil.playStack(destroy, pod.actor, pod.game));
        assertEquals(destroy.getPayingMana().size(), 2);
        settle(pod.game);
        assertTrue(pod.game.getCardState(lens).isInZone(ZoneType.Graveyard));
        assertFalse(pod.hidden.getView().canFaceDownBeShownTo(pod.actor.getView()));
        assertEquals(InspectionKnowledge.knownFaceDownValue(pod.actor), before,
                "Losing free access cannot create an artificial new knowledge reward");
    }

    @Test
    public void copiedKnowledgeValueAndForgettingRemainIndependent() {
        Pod pod = pod("Smoke Teller", 2);
        inspect(pod, pod.hidden);
        int value = InspectionKnowledge.knownFaceDownValue(pod.actor);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        copier.makeCopy(null, pod.actor);
        Player observer = copier.find(pod.actor);
        Card hidden = copier.find(pod.hidden);
        assertEquals(InspectionKnowledge.knownFaceDownValue(observer), value);
        AiCardMemory.forgetCard(observer, hidden, AiCardMemory.MemorySet.REVEALED_CARDS);
        assertEquals(InspectionKnowledge.knownFaceDownValue(observer), 0);
        assertEquals(InspectionKnowledge.knownFaceDownValue(pod.actor), value);
    }

    @Test
    public void aNewHiddenIncarnationCannotReuseAnEarlierInspectionValue() {
        Pod pod = pod("Smoke Teller", 2);
        inspect(pod, pod.hidden);
        Player owner = pod.hidden.getOwner();
        addCardToZone("Fathom Seer", owner, ZoneType.Hand);
        Card hand = pod.game.getAction().moveToHand(pod.hidden, null);
        for (int index = 0; index < 3; index++) addCardToZone("Island", owner, ZoneType.Battlefield);
        pod.game.getPhaseHandler().devModeSet(PhaseType.MAIN1, owner, 3);
        Card recast = castMorph(pod, hand, owner);
        assertFalse(recast.equalsWithGameTimestamp(pod.hidden));
        assertFalse(known(pod.actor, recast));
        assertEquals(InspectionKnowledge.knownFaceDownValue(pod.actor), 0);
        assertTrue(InspectionKnowledge.needsInspection(recast, pod.actor));
    }

    @Test
    public void informationValueIsBoundedAcrossManyRealInspections() {
        Pod pod = pod("Smoke Teller", 2);
        Player owner = pod.hidden.getOwner();
        List<Card> targets = new ArrayList<>();
        targets.add(pod.hidden);
        for (String name : List.of("Fathom Seer", "Echo Tracer", "Vesuvan Shapeshifter", "Brine Elemental")) {
            for (int index = 0; index < 3; index++) addCardToZone("Island", owner, ZoneType.Battlefield);
            pod.game.getPhaseHandler().devModeSet(PhaseType.MAIN1, owner, 2);
            targets.add(castMorph(pod, addCardToZone(name, owner, ZoneType.Hand), owner));
        }
        int one = 0;
        for (Card target : targets) {
            for (int index = 0; index < 2; index++) addCardToZone("Island", pod.actor, ZoneType.Battlefield);
            inspect(pod, target);
            if (one == 0) one = InspectionKnowledge.knownFaceDownValue(pod.actor);
        }
        int all = InspectionKnowledge.knownFaceDownValue(pod.actor);
        assertTrue(one > 0 && all >= one);
        assertTrue(all <= 8 && all < one * targets.size(), "Information credit must saturate rather than dominate board value");
    }

    @Test
    public void gainingControlMakesInspectionFreeWithoutInventingOpponentKnowledge() {
        Pod pod = pod("Smoke Teller", 2);
        for (int index = 0; index < 3; index++) addCardToZone("Mountain", pod.actor, ZoneType.Battlefield);
        pod.game.getPhaseHandler().setPriority(pod.actor);
        SpellAbility control = addCardToZone("Act of Treason", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        control.setActivatingPlayer(pod.actor);
        control.getTargets().add(pod.hidden);
        assertTrue(control.canPlay());
        assertTrue(ComputerUtil.playStack(control, pod.actor, pod.game));
        assertEquals(control.getPayingMana().size(), 3);
        settle(pod.game);
        Card owned = pod.game.getCardState(pod.hidden);
        assertSame(owned.getController(), pod.actor);
        assertTrue(owned.getView().canFaceDownBeShownTo(pod.actor.getView()));
        assertFalse(InspectionKnowledge.needsInspection(owned, pod.actor));
        assertEquals(InspectionKnowledge.knownFaceDownValue(pod.actor), 0);
    }

    @Test
    public void heuristicSelectsAnUnknownFaceInsteadOfAnInspectedOne() {
        unknownTarget(null);
    }
    @Test
    public void hybridSelectsAnUnknownFaceInsteadOfAnInspectedOne() {
        unknownTarget(AIOption.USE_HYBRID_SIMULATION);
    }
    @Test
    public void fullSelectsAnUnknownFaceInsteadOfAnInspectedOne() {
        unknownTarget(AIOption.USE_FULL_SIMULATION);
    }

    private void unknownTarget(AIOption mode) {
        Pod pod = pod("Smoke Teller", 2);
        inspect(pod, pod.hidden);
        Player owner = pod.hidden.getOwner();
        for (int index = 0; index < 3; index++) addCardToZone("Island", owner, ZoneType.Battlefield);
        pod.game.getPhaseHandler().devModeSet(PhaseType.MAIN1, owner, 2);
        Card second = castMorph(pod, addCardToZone("Fathom Seer", owner, ZoneType.Hand), owner);
        for (int index = 0; index < 2; index++) addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        pod.game.getPhaseHandler().devModeSet(PhaseType.MAIN2, pod.actor, 2);
        AiController ai = ((PlayerControllerAi) pod.actor.getController()).getAi();
        ai.setUseSimulation(mode);
        List<SpellAbility> choices = ai.chooseSpellAbilityToPlay();
        assertNotNull(choices);
        SpellAbility selected = choices.stream().filter(action -> action.getApi() == forge.game.ability.ApiType.LookAt).findFirst().orElseThrow();
        assertSame(selected.getTargets().getTargetCards().get(0), second);
        assertFalse(known(pod.actor, second), "Thinking must not inspect the alternative face for free");
        assertTrue(known(pod.actor, pod.hidden));
        assertTrue(ComputerUtil.playStack(selected, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(known(pod.actor, second));
    }

    private void freeInspection(AIOption mode) {
        Pod pod = pod("Smoke Teller", 2);
        lens(pod);
        for (int index = 0; index < 2; index++) addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        assertFalse(InspectionKnowledge.needsInspection(pod.hidden, pod.actor));
        int before = InspectionKnowledge.knownFaceDownValue(pod.actor);
        assertTrue(before > 0, "Free authorized face access already supplies information");
        pod.action.getTargets().add(pod.hidden);
        assertTrue(ComputerUtilCost.canPayCost(pod.action, pod.actor, false));
        long tapped = pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count();
        AiController ai = ((PlayerControllerAi) pod.actor.getController()).getAi();
        ai.setUseSimulation(mode);
        List<SpellAbility> choices = ai.chooseSpellAbilityToPlay();
        assertTrue(choices == null || choices.stream().noneMatch(action -> action.getApi() == forge.game.ability.ApiType.LookAt));
        assertEquals(InspectionKnowledge.knownFaceDownValue(pod.actor), before);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), tapped);
    }

    private Card lens(Pod pod) {
        pod.game.getPhaseHandler().devModeSet(PhaseType.MAIN2, pod.actor, 2);
        SpellAbility spell = addCardToZone("Lens of Clarity", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        assertTrue(spell.canPlay());
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        assertEquals(spell.getPayingMana().size(), 1);
        settle(pod.game);
        assertTrue(pod.hidden.getView().canFaceDownBeShownTo(pod.actor.getView()));
        return pod.game.getCardState(spell.getHostCard());
    }

    private void inspect(Pod pod, Card target) {
        pod.game.getPhaseHandler().devModeSet(PhaseType.MAIN2, pod.actor, 2);
        SpellAbility action = pod.action.copy();
        action.setActivatingPlayer(pod.actor);
        action.resetTargets();
        action.getTargets().add(target);
        assertTrue(action.canPlay());
        assertTrue(ComputerUtil.playStack(action, pod.actor, pod.game));
        assertEquals(action.getPayingMana().size(), 2);
        settle(pod.game);
        assertTrue(known(pod.actor, target));
    }

    private Card castMorph(Pod pod, Card hand, Player owner) {
        SpellAbility morph = hand.getAllPossibleAbilities(owner, false).stream()
                .filter(action -> action.isSpell() && action.isCastFaceDown()).findFirst().orElseThrow();
        morph.setActivatingPlayer(owner);
        assertTrue(morph.canPlay());
        assertTrue(ComputerUtil.playStack(morph, owner, pod.game));
        assertEquals(morph.getPayingMana().size(), 3);
        settle(pod.game);
        return pod.game.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getId() == hand.getId()).findFirst().orElseThrow();
    }
}
