package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Native inspection controls and supplied-target AI admission; not an autonomous picker claim. */
public class CommanderInspectionAiTest extends InspectionTestSupport {
    @Test
    public void smokeHeuristicInspectionHasSupportedAdmission() {
        admission("Smoke Teller", 2, null);
    }
    @Test
    public void avenHeuristicInspectionHasSupportedAdmission() {
        admission("Aven Soulgazer", 3, null);
    }
    @Test
    public void smokeHybridInspectionHasSupportedAdmission() {
        admission("Smoke Teller", 2, AIOption.USE_HYBRID_SIMULATION);
    }
    @Test
    public void avenHybridInspectionHasSupportedAdmission() {
        admission("Aven Soulgazer", 3, AIOption.USE_HYBRID_SIMULATION);
    }
    @Test
    public void smokeFullInspectionHasSupportedAdmission() {
        admission("Smoke Teller", 2, AIOption.USE_FULL_SIMULATION);
    }
    @Test
    public void avenFullInspectionHasSupportedAdmission() {
        admission("Aven Soulgazer", 3, AIOption.USE_FULL_SIMULATION);
    }
    @Test
    public void smokePaidInspectionIsPrivate() {
        paid("Smoke Teller", 2, false, false);
    }
    @Test
    public void avenPaidInspectionIsPrivate() {
        paid("Aven Soulgazer", 3, false, false);
    }
    @Test
    public void smokeCounteredInspectionRevealsNothing() {
        paid("Smoke Teller", 2, false, true);
    }
    @Test
    public void avenCounteredInspectionRevealsNothing() {
        paid("Aven Soulgazer", 3, false, true);
    }
    @Test
    public void smokeCopiedPendingInspectionPreservesOriginalSecrets() {
        paid("Smoke Teller", 2, true, false);
    }
    @Test
    public void avenCopiedPendingInspectionPreservesOriginalSecrets() {
        paid("Aven Soulgazer", 3, true, false);
    }
    @Test
    public void smokeUnaffordableInspectionRevealsNothing() {
        unaffordable("Smoke Teller", 2);
    }
    @Test
    public void avenUnaffordableInspectionRevealsNothing() {
        unaffordable("Aven Soulgazer", 3);
    }

    @Test
    public void smokeHeuristicPickerUsesInspectionOnce() {
        pick("Smoke Teller", 2, null);
    }

    @Test
    public void smokeHybridPickerUsesInspectionOnce() {
        pick("Smoke Teller", 2, AIOption.USE_HYBRID_SIMULATION);
    }

    @Test
    public void smokeFullPickerUsesInspectionOnce() {
        pick("Smoke Teller", 2, AIOption.USE_FULL_SIMULATION);
    }

    @Test
    public void avenHeuristicPickerUsesInspectionOnce() {
        pick("Aven Soulgazer", 3, null);
    }

    @Test
    public void avenHybridPickerUsesInspectionOnce() {
        pick("Aven Soulgazer", 3, AIOption.USE_HYBRID_SIMULATION);
    }

    @Test
    public void avenFullPickerUsesInspectionOnce() {
        pick("Aven Soulgazer", 3, AIOption.USE_FULL_SIMULATION);
    }

    private void pick(String name, int mana, AIOption mode) {
        Pod pod = pod(name, mana);
        pod.game.getPhaseHandler().devModeSet(PhaseType.MAIN2, pod.actor, 2);
        AiController ai = ((PlayerControllerAi) pod.actor.getController()).getAi();
        ai.setUseSimulation(mode);
        List<SpellAbility> choices = ai.chooseSpellAbilityToPlay();
        assertNotNull(choices, "An otherwise idle actor can learn a concealed enemy creature");
        SpellAbility selected = choices.stream().filter(action -> action.getApi() == ApiType.LookAt
                && action.getHostCard().getId() == pod.host.getId()).findFirst().orElseThrow();
        assertEquals(selected.getTargets().getTargetCards().size(), 1);
        assertSame(selected.getTargets().getTargetCards().get(0), pod.hidden);
        assertFalse(known(pod.actor, pod.hidden), "Thinking cannot grant an unpaid inspection");
        assertTrue(pod.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertTrue(pod.game.getStack().isEmpty());
        assertTrue(ComputerUtil.playStack(selected, pod.actor, pod.game));
        assertEquals(selected.getPayingMana().size(), mana);
        settle(pod.game);
        assertTrue(known(pod.actor, pod.hidden));
        for (int index = 0; index < mana; index++) addCardToZone(mana == 3 ? "Plains" : "Island", pod.actor, ZoneType.Battlefield);
        List<SpellAbility> repeated = ai.chooseSpellAbilityToPlay();
        assertTrue(repeated == null || repeated.stream().noneMatch(action -> action.getApi() == ApiType.LookAt),
                "Available new mana must not cause repeated payment for already known information");
        assertPublic(pod.hidden, pod.publicName);
    }

    private void admission(String name, int mana, AIOption mode) {
        Pod pod = pod(name, mana);
        AiController ai = ((PlayerControllerAi) pod.actor.getController()).getAi();
        ai.setUseSimulation(mode);
        pod.action.getTargets().add(pod.hidden);
        assertTrue(pod.action.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(pod.action, pod.actor, false));
        String handler = SpellApiToAi.Converter.get(pod.action).getClass().getName();
        AiAbilityDecision policy = SpellApiToAi.Converter.get(pod.action).canPlayWithSubs(pod.actor, pod.action);
        AiPlayDecision controllerDecision = ai.canPlaySa(pod.action);
        System.out.println("INSPECTION_ADMISSION source=" + name + "; mode=" + mode
                + "; handler=" + handler + "; policy=" + policy.decision() + "; controller=" + controllerDecision);
        assertTrue(pod.game.getStack().isEmpty());
        assertTrue(pod.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertFalse(known(pod.actor, pod.hidden), "Admission must not disclose an uninspected face");
        assertPublic(pod.hidden, pod.publicName);
        assertNotEquals(policy.decision(), AiPlayDecision.CantPlayAi,
                "A legal affordable primary inspection needs supported admission or a supported policy decline");
    }

    private void paid(String name, int mana, boolean copied, boolean countered) {
        Pod pod = pod(name, mana);
        pod.action.getTargets().add(pod.hidden);
        assertTrue(pod.action.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(pod.action, pod.actor, false));
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        assertEquals(pod.action.getPayingMana().size(), mana);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), (long) mana);
        assertFalse(known(pod.actor, pod.hidden));
        if (countered) {
            Player responder = pod.hidden.getController();
            addCardToZone("Island", responder, ZoneType.Battlefield);
            pod.game.getPhaseHandler().setPriority(responder);
            SpellAbility stifle = addCardToZone("Stifle", responder, ZoneType.Hand).getFirstSpellAbility();
            stifle.setActivatingPlayer(responder);
            stifle.getTargets().add(pod.game.getStack().peekAbility());
            assertTrue(stifle.canPlay());
            assertTrue(ComputerUtil.playStack(stifle, responder, pod.game));
            assertEquals(stifle.getPayingMana().size(), 1);
        }
        Game game = pod.game;
        Player actor = pod.actor;
        Card hidden = pod.hidden;
        if (copied) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor);
            actor = copier.find(actor);
            hidden = copier.find(hidden);
        }
        settle(game);
        assertEquals(known(actor, hidden), !countered);
        for (Player observer : game.getPlayers()) {
            if (observer != actor && observer != hidden.getController()) assertFalse(known(observer, hidden));
        }
        assertPublic(hidden, pod.publicName);
        if (copied) {
            assertFalse(known(pod.actor, pod.hidden));
            assertEquals(pod.game.getStack().size(), 1);
        }
    }

    private void unaffordable(String name, int mana) {
        Pod pod = pod(name, mana);
        pod.actor.getLandsInPlay().forEach(card -> card.setTapped(true));
        pod.action.getTargets().add(pod.hidden);
        assertTrue(pod.action.canPlay());
        assertFalse(ComputerUtilCost.canPayCost(pod.action, pod.actor, false));
        assertFalse(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        assertFalse(known(pod.actor, pod.hidden));
        assertTrue(pod.game.getStack().isEmpty());
        assertPublic(pod.hidden, pod.publicName);
    }

}
