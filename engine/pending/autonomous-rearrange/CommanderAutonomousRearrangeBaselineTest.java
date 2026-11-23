package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Actual autonomous selection without supplying X, with paid and decline controls. */
public class CommanderAutonomousRearrangeBaselineTest extends InformationChoiceTestSupport {
    @Test public void hybridChoosesUsefulVariableRearrange() { choose(AIOption.USE_HYBRID_SIMULATION, false); }
    @Test public void fullChoosesUsefulVariableRearrange() { choose(AIOption.USE_FULL_SIMULATION, false); }
    @Test public void copiedHybridChoosesUsefulVariableRearrange() { choose(AIOption.USE_HYBRID_SIMULATION, true); }
    @Test public void copiedFullChoosesUsefulVariableRearrange() { choose(AIOption.USE_FULL_SIMULATION, true); }
    @Test public void hybridDeclinesAlreadyKnownUnchangedOrder() { decline(AIOption.USE_HYBRID_SIMULATION, "known"); }
    @Test public void fullDeclinesAlreadyKnownUnchangedOrder() { decline(AIOption.USE_FULL_SIMULATION, "known"); }
    @Test public void hybridDeclinesEmptyLibraryInspection() { decline(AIOption.USE_HYBRID_SIMULATION, "empty"); }
    @Test public void fullDeclinesEmptyLibraryInspection() { decline(AIOption.USE_FULL_SIMULATION, "empty"); }
    @Test public void hybridWaitsForUsefulEndStep() { decline(AIOption.USE_HYBRID_SIMULATION, "timing"); }
    @Test public void fullWaitsForUsefulEndStep() { decline(AIOption.USE_FULL_SIMULATION, "timing"); }
    @Test public void hybridDeclinesUnaffordableFixedCost() { decline(AIOption.USE_HYBRID_SIMULATION, "budget"); }
    @Test public void fullDeclinesUnaffordableFixedCost() { decline(AIOption.USE_FULL_SIMULATION, "budget"); }
    @Test public void suppliedXResolvesWithRealPayment() { paid(false); }
    @Test public void copiedSuppliedXResolvesWithRealPayment() { paid(true); }

    private void choose(AIOption mode, boolean copied) {
        Setup setup = setup("Soothsaying", copied);
        assertNull(setup.action.getXManaCostPaid(), "Do not preselect X in autonomous checks");
        assertTrue(setup.game.getPhaseHandler().isPlayerNextTurn(setup.actor));
        assertTrue(setup.action.canPlay());
        assertEquals(knownCount(setup.actor), 0);
        int mana = setup.actor.getLandsInPlay().size();
        List<SpellAbility> choices = controller(setup.actor, mode).chooseSpellAbilityToPlay();
        System.out.println("AUTONOMOUS_REARRANGE mode=" + mode + "; copied=" + copied + "; choices=" + choices);
        assertEquals(knownCount(setup.actor), 0, "Planning must not reveal unpaid library identities");
        assertTrue(setup.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertTrue(setup.game.getStack().isEmpty());
        SpellAbility selected = choices == null ? null : choices.stream()
                .filter(sa -> sa.getApi() == ApiType.RearrangeTopOfLibrary && sa.getHostCard().getId() == setup.host.getId())
                .findFirst().orElse(null);
        assertNotNull(selected, "The supplied idle end-step pod has unknown library order and affordable positive information gain");
        Integer x = selected.getXManaCostPaid();
        System.out.println("AUTONOMOUS_X mode=" + mode + "; copied=" + copied + "; x=" + x + "; available=" + mana);
        assertNotNull(x, "Autonomous selection must announce X before payment");
        assertTrue(x > 0 && x <= mana && x <= setup.actor.getCardsIn(ZoneType.Library).size());
        assertTrue(ComputerUtil.playStack(selected, setup.actor, setup.game));
        assertEquals(selected.getPayingMana().size(), x.intValue());
        settle(setup.game);
        assertEquals(knownCount(setup.actor), x.intValue());
        if (copied) assertEquals(knownCount(setup.original), 0, "Copied planning and resolution cannot alter original private knowledge");
    }

    private void decline(AIOption mode, String reason) {
        Setup setup = setup(reason.equals("budget") ? "Aven Fateshaper" : "Soothsaying", false);
        if (reason.equals("known")) {
            Map<Integer, Card> positions = new LinkedHashMap<>();
            for (int index = 0; index < setup.actor.getCardsIn(ZoneType.Library).size(); index++)
                positions.put(index, setup.actor.getCardsIn(ZoneType.Library).get(index));
            ((PlayerControllerAi) setup.actor.getController()).getAi().getCardMemory().rememberLibraryPositions(setup.actor, positions);
        }
        if (reason.equals("empty")) {
            for (Card card : List.copyOf(setup.actor.getCardsIn(ZoneType.Library)))
                setup.game.getAction().exile(card, null, null);
        }
        if (reason.equals("timing")) setup.game.getPhaseHandler().devModeSet(PhaseType.MAIN1, setup.actor, 2);
        if (reason.equals("budget")) for (Card land : setup.actor.getLandsInPlay()) land.setTapped(true);
        setup.game.copyLastState();
        int before = knownCount(setup.actor);
        List<SpellAbility> choices = controller(setup.actor, mode).chooseSpellAbilityToPlay();
        System.out.println("AUTONOMOUS_DECLINE mode=" + mode + "; reason=" + reason + "; choices=" + choices);
        assertTrue(choices == null || choices.stream().noneMatch(sa -> sa.getApi() == ApiType.RearrangeTopOfLibrary
                && sa.getHostCard().getId() == setup.host.getId()), "No useful affordable rearrange should be selected in this control");
        assertEquals(knownCount(setup.actor), before);
        assertTrue(setup.game.getStack().isEmpty());
    }

    private void paid(boolean copied) {
        Setup setup = setup("Soothsaying", copied);
        setup.action.setXManaCostPaid(3); // Explicit payment control, not autonomous selection.
        assertTrue(setup.action.canPlay());
        assertTrue(ComputerUtil.playStack(setup.action, setup.actor, setup.game));
        assertEquals(setup.action.getPayingMana().size(), 3);
        settle(setup.game);
        assertEquals(knownCount(setup.actor), 3);
        if (copied) assertEquals(knownCount(setup.original), 0);
    }

    private Setup setup(String name, boolean copied) {
        Pod pod = pod("Index");
        pod.game.getAction().exile(pod.spell.getHostCard(), null, null);
        for (int index = 0; index < 2; index++) addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        Card host = addCardToZone(name, pod.actor, ZoneType.Battlefield);
        pod.game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, pod.game.getPlayers().get(3), 2);
        pod.game.getPhaseHandler().setPriority(pod.actor);
        pod.game.getAction().checkStateEffects(true); pod.game.getTriggerHandler().resetActiveTriggers(); pod.game.copyLastState();
        Game game = pod.game; Player actor = pod.actor;
        if (copied) {
            GameCopier copier = new GameCopier(game); game = copier.makeCopy(null, actor);
            actor = copier.find(actor); host = copier.find(host);
        }
        SpellAbility action = host.getNonManaAbilities().stream().filter(sa -> sa.getApi() == ApiType.RearrangeTopOfLibrary).findFirst().orElseThrow();
        action.setActivatingPlayer(actor);
        return new Setup(game, actor, host, action, pod.actor);
    }

    private int knownCount(Player actor) {
        int count = 0;
        for (int index = 0; index < actor.getCardsIn(ZoneType.Library).size(); index++)
            if (AiCardMemory.isLibraryPositionKnown(actor, actor, index)) count++;
        return count;
    }

    private record Setup(Game game, Player actor, Card host, SpellAbility action, Player original) { }
}
