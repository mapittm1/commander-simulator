package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.HashSet;
import java.util.Set;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Announced inspection/draw transforms must not erase other deducible known positions. */
public class CommanderLibraryKnowledgeTransitionBaselineTest extends InformationChoiceTestSupport {
    @Test public void knownPrefixSurvivesOpt() { transition("prefix", "Opt", false); }
    @Test public void copiedKnownPrefixSurvivesOpt() { transition("prefix", "Opt", true); }
    @Test public void knownPrefixSurvivesConsider() { transition("prefix", "Consider", false); }
    @Test public void copiedKnownPrefixSurvivesConsider() { transition("prefix", "Consider", true); }
    @Test public void knownBottomSurvivesOpt() { transition("bottom", "Opt", false); }
    @Test public void copiedKnownBottomSurvivesOpt() { transition("bottom", "Opt", true); }
    @Test public void knownBottomSurvivesConsider() { transition("bottom", "Consider", false); }
    @Test public void copiedKnownBottomSurvivesConsider() { transition("bottom", "Consider", true); }
    @Test public void knownPrefixSurvivesOrdinaryDraw() { transition("prefix", "Reach Through Mists", false); }
    @Test public void copiedKnownPrefixSurvivesOrdinaryDraw() { transition("prefix", "Reach Through Mists", true); }
    @Test public void fullyReannouncedKnownPrefixStaysKnown() { transition("prefix", "Spy Network", false); }
    @Test public void copiedFullyReannouncedKnownPrefixStaysKnown() { transition("prefix", "Spy Network", true); }

    private void transition(String shape, String spellName, boolean copied) {
        Pod pod = pod(shape.equals("prefix") ? "Spy Network" : "Augury Owl");
        if (shape.equals("prefix")) {
            cast(pod, "Spy Network");
        } else {
            for (int index = 0; index < 6; index++) addCardToZone("Island", pod.actor, ZoneType.Battlefield);
            assertTrue(pod.spell.canPlay());
            assertTrue(ComputerUtil.playStack(pod.spell, pod.actor, pod.game));
            assertEquals(pod.spell.getPayingMana().size(), 2);
        }
        settle(pod.game);
        Set<Integer> initial = knownIds(pod.actor);
        assertTrue(initial.size() > 1, "The native first effect must establish multiple known positions");
        Set<Integer> expected = new HashSet<>(initial);
        if (spellName.equals("Opt") || spellName.equals("Consider")) {
            // This prefix is disclosed by the actual paid one-card scry/surveil, not by an oracle peek.
            expected.add(pod.actor.getCardsIn(ZoneType.Library).get(0).getId());
        }
        SpellAbility action = addCardToZone(spellName, pod.actor, ZoneType.Hand).getFirstSpellAbility();
        action.setActivatingPlayer(pod.actor);
        if (spellName.equals("Spy Network")) action.getTargets().add(pod.owner);
        assertTrue(action.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(action, pod.actor, false));
        assertTrue(ComputerUtil.playStack(action, pod.actor, pod.game));
        assertEquals(action.getPayingMana().size(), 1);
        assertEquals(knownIds(pod.actor), initial, "Payment must not disclose or reorder unresolved library choices");
        Game game = pod.game;
        Player actor = pod.actor;
        if (copied) {
            GameCopier copier = new GameCopier(pod.game).withStack(true);
            game = copier.makeCopy(null, pod.actor);
            actor = copier.find(pod.actor);
        }
        settle(game);
        int required = 0;
        int retained = 0;
        for (int index = 0; index < actor.getCardsIn(ZoneType.Library).size(); index++) {
            Card card = actor.getCardsIn(ZoneType.Library).get(index);
            boolean deducible = expected.contains(card.getId());
            if (deducible) required++;
            if (AiCardMemory.isLibraryPositionKnown(actor, actor, index)) retained++;
        }
        System.out.println("LIBRARY_TRANSITION shape=" + shape + "; spell=" + spellName + "; copied=" + copied
                + "; initial=" + initial.size() + "; required=" + required + "; retained=" + retained);
        assertTrue(required > 1, "A one-card inspection/draw leaves other previously known cards in this supplied library");
        for (int index = 0; index < actor.getCardsIn(ZoneType.Library).size(); index++) {
            Card card = actor.getCardsIn(ZoneType.Library).get(index);
            boolean deducible = expected.contains(card.getId());
            assertEquals(AiCardMemory.isLibraryPositionKnown(actor, actor, index), deducible,
                    "These supplied effects contain no shuffle or unannounced external reorder; the chooser can track previously known cards");
            if (deducible) assertTrue(known(actor, card));
        }
        if (copied) {
            assertEquals(knownIds(pod.actor), initial, "The original pending game retains its original knowledge");
            assertEquals(pod.game.getStack().size(), 1);
        }
        for (Player observer : game.getPlayers()) {
            if (observer == actor) continue;
            for (int index = 0; index < actor.getCardsIn(ZoneType.Library).size(); index++) {
                Card card = actor.getCardsIn(ZoneType.Library).get(index);
                assertFalse(AiCardMemory.isLibraryPositionKnown(observer, actor, index));
                assertFalse(known(observer, card));
                assertFalse(card.getView().canBeShownTo(observer.getView()));
            }
        }
    }

    private Set<Integer> knownIds(Player observer) {
        Set<Integer> result = new HashSet<>();
        for (int index = 0; index < observer.getCardsIn(ZoneType.Library).size(); index++) {
            if (AiCardMemory.isLibraryPositionKnown(observer, observer, index))
                result.add(observer.getCardsIn(ZoneType.Library).get(index).getId());
        }
        return result;
    }
}
