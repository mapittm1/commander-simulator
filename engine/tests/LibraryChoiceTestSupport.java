package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;

/** Shared native paid library setup and assertions; contains no test methods. */
public class LibraryChoiceTestSupport extends InformationChoiceTestSupport {

    protected void exercise(boolean surveil, boolean copied, String shape) {
        Scenario scenario = scenario(surveil, shape);
        Resolved result = resolve(scenario, copied);
        List<Card> library = new ArrayList<>();
        for (Card card : result.actor.getCardsIn(ZoneType.Library)) library.add(card);
        int retained = 0;
        int toTop = 0;
        int toBottom = 0;
        for (int index = 0; index < library.size(); index++) {
            Card actual = library.get(index);
            boolean observed = result.observed.stream().anyMatch(card -> card.equalsWithGameTimestamp(actual));
            if (observed) {
                retained++;
                if (index < 3) toTop++;
                if (index >= library.size() - 3) toBottom++;
            }
        }
        assertTrue(retained > 0, "This supplied native choice must retain an observed card");
        if (shape.equals("top")) assertEquals(toTop, 3);
        if (shape.equals("bottom")) assertEquals(toBottom, 3);
        if (shape.equals("mixed")) {
            assertTrue(toTop > 0);
            if (surveil) assertTrue(result.observed.stream().anyMatch(card -> result.game.getCardState(card).isInZone(ZoneType.Graveyard)));
            else assertTrue(toBottom > 0);
        }
        System.out.println("LIBRARY_CHOICE surveil=" + surveil + "; copied=" + copied + "; shape=" + shape
                + "; observed=" + result.observed.size() + "; retained=" + retained);
        for (int index = 0; index < library.size(); index++) {
            Card actual = library.get(index);
            boolean observed = result.observed.stream().anyMatch(card -> card.equalsWithGameTimestamp(actual));
            assertEquals(AiCardMemory.isLibraryPositionKnown(result.actor, result.actor, index), observed,
                    "Remember the announced position of each retained inspected card, without inventing other positions");
            if (observed) assertTrue(known(result.actor, actual));
        }
        assertEquals(InformationEffects.knownInformationValue(result.actor), retained);
        assertPrivate(result);
    }

    protected void privacy(boolean surveil) {
        Resolved result = resolve(scenario(surveil, "mixed"), false);
        assertPrivate(result);
    }

    protected void assertPrivate(Resolved result) {
        for (Player observer : result.game.getPlayers()) {
            if (observer == result.actor) continue;
            for (int index = 0; index < result.actor.getCardsIn(ZoneType.Library).size(); index++) {
                Card card = result.actor.getCardsIn(ZoneType.Library).get(index);
                assertFalse(AiCardMemory.isLibraryPositionKnown(observer, result.actor, index));
                assertFalse(known(observer, card));
                assertFalse(card.getView().canBeShownTo(observer.getView()));
            }
        }
    }

    protected void countered(boolean surveil) {
        Scenario scenario = scenario(surveil, "top");
        pay(scenario);
        Pod pod = scenario.pod;
        for (int index = 0; index < 2; index++) addCardToZone("Island", pod.owner, ZoneType.Battlefield);
        pod.game.getPhaseHandler().setPriority(pod.owner);
        SpellAbility response = addCardToZone("Counterspell", pod.owner, ZoneType.Hand).getFirstSpellAbility();
        response.setActivatingPlayer(pod.owner);
        response.getTargets().add(pod.game.getStack().peekAbility());
        assertTrue(response.canPlay());
        assertTrue(ComputerUtil.playStack(response, pod.owner, pod.game));
        assertEquals(response.getPayingMana().size(), 2);
        settle(pod.game);
        assertTrue(pod.game.getCardState(scenario.action.getHostCard()).isInZone(ZoneType.Graveyard));
        for (Card card : scenario.observed) assertFalse(known(pod.actor, card));
        assertEquals(InformationEffects.knownInformationValue(pod.actor), 0);
    }

    protected void unaffordable(boolean surveil) {
        Scenario scenario = scenario(surveil, "top");
        scenario.pod.actor.getLandsInPlay().forEach(card -> card.setTapped(true));
        assertTrue(scenario.action.canPlay());
        assertFalse(ComputerUtilCost.canPayCost(scenario.action, scenario.pod.actor, false));
        assertFalse(ComputerUtil.playStack(scenario.action, scenario.pod.actor, scenario.pod.game));
        assertTrue(scenario.pod.game.getStack().isEmpty());
        for (Card card : scenario.observed) assertFalse(known(scenario.pod.actor, card));
        assertEquals(InformationEffects.knownInformationValue(scenario.pod.actor), 0);
    }

    protected Resolved resolve(Scenario scenario, boolean copied) {
        pay(scenario);
        return settlePaid(scenario.pod, scenario.observed, copied);
    }

    protected Resolved settlePaid(Pod pod, List<Card> inspected, boolean copied) {
        if (!copied) {
            settle(pod.game);
            return new Resolved(pod.game, pod.actor, inspected);
        }
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game game = copier.makeCopy(null, pod.actor);
        List<Card> observed = inspected.stream().map(copier::find).toList();
        settle(game);
        for (Card card : inspected) assertFalse(known(pod.actor, card), "A copied resolution must not disclose the original pending effect");
        assertEquals(pod.game.getStack().size(), 1);
        return new Resolved(game, copier.find(pod.actor), observed);
    }

    protected void pay(Scenario scenario) {
        assertTrue(scenario.action.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(scenario.action, scenario.pod.actor, false));
        assertTrue(ComputerUtil.playStack(scenario.action, scenario.pod.actor, scenario.pod.game));
        assertEquals(scenario.action.getPayingMana().size(), scenario.surveil ? 1 : 2);
        for (Card card : scenario.observed) assertFalse(known(scenario.pod.actor, card), "Paying cannot disclose an effect that has not resolved");
        assertEquals(InformationEffects.knownInformationValue(scenario.pod.actor), 0);
    }

    protected Scenario scenario(boolean surveil, String shape) {
        Pod pod = pod("Spy Network");
        if (!shape.equals("top")) {
            for (int index = 0; index < 6; index++) addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        }
        List<Card> observed = new ArrayList<>();
        if (shape.equals("mixed")) {
            observed.add(pod.actor.getCardsIn(ZoneType.Library).get(0));
            observed.add(addCardToZone("Counterspell", pod.actor, ZoneType.Library));
            observed.add(addCardToZone("Sol Ring", pod.actor, ZoneType.Library));
            if (surveil) for (int index = 0; index < 2; index++) addCardToZone("Forest", pod.actor, ZoneType.Library);
            List<Card> order = new ArrayList<>(observed);
            for (Card card : pod.actor.getCardsIn(ZoneType.Library)) if (!order.contains(card)) order.add(card);
            pod.actor.getZone(ZoneType.Library).setCards(order);
        } else {
            for (int index = 0; index < 3; index++) observed.add(pod.actor.getCardsIn(ZoneType.Library).get(index));
        }
        SpellAbility action = addCardToZone(surveil ? "Otherworldly Gaze" : "Augury Owl", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        action.setActivatingPlayer(pod.actor);
        pod.game.getAction().checkStateEffects(true);
        pod.game.getTriggerHandler().resetActiveTriggers();
        pod.game.copyLastState();
        return new Scenario(pod, action, observed, surveil);
    }

    protected static final class Scenario {
        final Pod pod;
        final SpellAbility action;
        final List<Card> observed;
        final boolean surveil;
        Scenario(Pod pod, SpellAbility action, List<Card> observed, boolean surveil) {
            this.pod = pod; this.action = action; this.observed = observed; this.surveil = surveil;
        }
    }

    protected static final class Resolved {
        final Game game;
        final Player actor;
        final List<Card> observed;
        Resolved(Game game, Player actor, List<Card> observed) {
            this.game = game; this.actor = actor; this.observed = observed;
        }
    }
}
