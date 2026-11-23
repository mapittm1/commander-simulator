package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Subsequent draws, object mapping and replacement timing bound announced knowledge. */
public class CommanderLibraryChoiceBoundaryTest extends LibraryChoiceTestSupport {
    @Test public void scryThenDrawRetainsOnlyTheKnownRemainingPrefix() { prefixDraw(false); }
    @Test public void copiedScryThenDrawRetainsOnlyTheKnownRemainingPrefix() { prefixDraw(true); }
    @Test public void bottomKnowledgeShiftsAfterAnOrdinaryDraw() { bottomDraw(false); }
    @Test public void copiedBottomKnowledgeShiftsAfterAnOrdinaryDraw() { bottomDraw(true); }
    @Test public void resolvedScryKnowledgeCopiesIndependently() { knowledgeCopy(false); }
    @Test public void resolvedSurveilKnowledgeCopiesIndependently() { knowledgeCopy(true); }
    @Test public void surveilReplacementShuffleKnowsOnlyTheKeptPrefix() { replacementShuffle(false); }
    @Test public void copiedSurveilReplacementShuffleKnowsOnlyTheKeptPrefix() { replacementShuffle(true); }
    @Test public void laterSurveilTriggerShuffleRetiresPositions() { triggerShuffle(false); }
    @Test public void copiedLaterSurveilTriggerShuffleRetiresPositions() { triggerShuffle(true); }
    @Test public void surveilExileReplacementPreservesOnlyTheKeptPrefix() { exileReplacement(false); }
    @Test public void copiedSurveilExileReplacementPreservesOnlyTheKeptPrefix() { exileReplacement(true); }

    private void prefixDraw(boolean copied) {
        Scenario scenario = scenario(false, "top");
        SpellAbility action = addCardToZone("Preordain", scenario.pod.actor, ZoneType.Hand).getFirstSpellAbility();
        action.setActivatingPlayer(scenario.pod.actor);
        assertTrue(action.canPlay());
        assertTrue(ComputerUtil.playStack(action, scenario.pod.actor, scenario.pod.game));
        assertEquals(action.getPayingMana().size(), 1);
        Resolved result = settlePaid(scenario.pod, scenario.observed, copied);
        int drawn = 0;
        for (int index = 0; index < 2; index++) {
            Card actual = result.game.getCardState(result.observed.get(index));
            if (actual.isInZone(ZoneType.Hand)) {
                drawn++;
                assertTrue(known(result.actor, actual), "An announced known draw must transfer its identity to the new hand object");
            }
        }
        assertEquals(drawn, 1);
        assertKnownPrefix(result, 1);
    }

    private void bottomDraw(boolean copied) {
        Resolved result = resolve(scenario(false, "bottom"), copied);
        int before = result.actor.getCardsIn(ZoneType.Library).size();
        assertEquals(InformationEffects.knownInformationValue(result.actor), 3);
        for (int index = 0; index < before; index++)
            assertEquals(AiCardMemory.isLibraryPositionKnown(result.actor, result.actor, index), index >= before - 3);
        SpellAbility draw = addCardToZone("Reach Through Mists", result.actor, ZoneType.Hand).getFirstSpellAbility();
        draw.setActivatingPlayer(result.actor);
        assertTrue(draw.canPlay());
        assertTrue(ComputerUtil.playStack(draw, result.actor, result.game));
        assertEquals(draw.getPayingMana().size(), 1);
        settle(result.game);
        int after = result.actor.getCardsIn(ZoneType.Library).size();
        assertEquals(after, before - 1);
        for (int index = 0; index < after; index++)
            assertEquals(AiCardMemory.isLibraryPositionKnown(result.actor, result.actor, index), index >= after - 3);
        assertEquals(InformationEffects.knownInformationValue(result.actor), 3);
        assertPrivate(result);
    }

    private void knowledgeCopy(boolean surveil) {
        Resolved original = resolve(scenario(surveil, "top"), false);
        int before = InformationEffects.knownInformationValue(original.actor);
        assertEquals(before, 3);
        GameCopier copier = new GameCopier(original.game).withStack(true);
        copier.makeCopy(null, original.actor);
        Player observer = copier.find(original.actor);
        assertEquals(InformationEffects.knownInformationValue(observer), before);
        for (int index = 0; index < original.actor.getCardsIn(ZoneType.Library).size(); index++)
            assertEquals(AiCardMemory.isLibraryPositionKnown(observer, observer, index), index < 3);
        AiCardMemory.clearMemorySet(observer, AiCardMemory.MemorySet.REVEALED_CARDS);
        assertEquals(InformationEffects.knownInformationValue(observer), 0);
        assertEquals(InformationEffects.knownInformationValue(original.actor), before);
        assertTrue(AiCardMemory.isLibraryPositionKnown(original.actor, original.actor, 0));
    }

    private void replacementShuffle(boolean copied) {
        Scenario scenario = shuffleScenario("Darksteel Colossus");
        int before = scenario.pod.actor.getLife();
        Resolved result = resolve(scenario, copied);
        assertEquals(result.actor.getLife(), before - 2, "Psychogenic Probe witnesses the actual replacement shuffle");
        if (copied) assertEquals(scenario.pod.actor.getLife(), before);
        assertTrue(result.game.getCardState(result.observed.get(0)).isInZone(ZoneType.Library));
        assertPrefix(result, 2);
        Card publicCard = result.game.getCardState(result.observed.get(0));
        assertTrue(known(result.actor, publicCard), "Darksteel Colossus is publicly revealed by its replacement");
        for (Player observer : result.game.getPlayers()) {
            if (observer == result.actor) continue;
            for (int index = 0; index < result.actor.getCardsIn(ZoneType.Library).size(); index++) {
                Card card = result.actor.getCardsIn(ZoneType.Library).get(index);
                assertFalse(AiCardMemory.isLibraryPositionKnown(observer, result.actor, index));
                assertFalse(card.getView().canBeShownTo(observer.getView()));
                assertEquals(known(observer, card), card.equalsWithGameTimestamp(publicCard),
                        "Only the publicly revealed identity is remembered; neither private choices nor shuffled positions are disclosed");
            }
        }
        assertFalse(AiCardMemory.isLibraryPositionKnown(result.actor, result.actor, 2));
    }

    private void triggerShuffle(boolean copied) {
        Scenario scenario = shuffleScenario("Kozilek, Butcher of Truth");
        int before = scenario.pod.actor.getLife();
        Resolved result = resolve(scenario, copied);
        assertEquals(result.actor.getLife(), before - 2, "Psychogenic Probe witnesses the actual triggered shuffle");
        if (copied) assertEquals(scenario.pod.actor.getLife(), before);
        assertTrue(result.game.getCardState(result.observed.get(0)).isInZone(ZoneType.Library));
        assertKnownPrefix(result, 0);
    }

    private void exileReplacement(boolean copied) {
        Scenario scenario = scenario(true, "mixed");
        addCardToZone("Rest in Peace", scenario.pod.owner, ZoneType.Battlefield);
        scenario.pod.game.getAction().checkStateEffects(true);
        scenario.pod.game.getTriggerHandler().resetActiveTriggers();
        scenario.pod.game.copyLastState();
        Resolved result = resolve(scenario, copied);
        assertTrue(result.game.getCardState(result.observed.get(0)).isInZone(ZoneType.Exile));
        assertKnownPrefix(result, 2);
    }

    private Scenario shuffleScenario(String name) {
        Pod pod = pod("Spy Network");
        Card firstLand = pod.actor.getCardsIn(ZoneType.Library).get(0);
        Card secondLand = pod.actor.getCardsIn(ZoneType.Library).get(1);
        Card replacement = addCardToZone(name, pod.actor, ZoneType.Library);
        for (int index = 0; index < 3; index++) addCardToZone("Forest", pod.actor, ZoneType.Library);
        List<Card> observed = List.of(replacement, firstLand, secondLand);
        List<Card> order = new ArrayList<>(observed);
        for (Card card : pod.actor.getCardsIn(ZoneType.Library)) if (!order.contains(card)) order.add(card);
        pod.actor.getZone(ZoneType.Library).setCards(order);
        addCardToZone("Psychogenic Probe", pod.owner, ZoneType.Battlefield);
        SpellAbility action = addCardToZone("Otherworldly Gaze", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        action.setActivatingPlayer(pod.actor);
        pod.game.getAction().checkStateEffects(true);
        pod.game.getTriggerHandler().resetActiveTriggers();
        pod.game.copyLastState();
        assertTrue(ComputerUtil.scryWillMoveCardToBottomOfLibrary(pod.actor, replacement), "The supplied native choice must actually send the creature toward the graveyard");
        assertFalse(ComputerUtil.scryWillMoveCardToBottomOfLibrary(pod.actor, firstLand));
        assertFalse(ComputerUtil.scryWillMoveCardToBottomOfLibrary(pod.actor, secondLand));
        return new Scenario(pod, action, observed, true);
    }

    private void assertKnownPrefix(Resolved result, int count) {
        assertPrefix(result, count);
        assertPrivate(result);
    }

    private void assertPrefix(Resolved result, int count) {
        for (int index = 0; index < result.actor.getCardsIn(ZoneType.Library).size(); index++) {
            boolean expected = index < count;
            assertEquals(AiCardMemory.isLibraryPositionKnown(result.actor, result.actor, index), expected);
            if (expected) assertTrue(known(result.actor, result.actor.getCardsIn(ZoneType.Library).get(index)));
        }
        assertEquals(InformationEffects.knownInformationValue(result.actor), count);
    }
}
