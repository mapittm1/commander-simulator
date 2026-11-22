package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Untaps must enable a real immediate play, and must not take credit for an already available play. */
public class CommanderUntapFollowupTest extends AITest {
    @Test public void fullPilotUntapsAndCastsTheFourManaHasteCreature() { pilot(false); }
    @Test public void fullPilotUsesTwoUniqueUntapsForTheSixManaHasteCreature() { pilot(true); }
    @Test public void sharedApiPlansAProfitableManaSourceUntap() {
        Pod pod = pod(false, false, false);
        AiAbilityDecision decision = SpellApiToAi.Converter.get(pod.action).canPlayWithSubs(pod.actor, pod.action);
        assertTrue(decision.willingToPlay(), "An immediate paid follow-up is available only after the untap: " + decision);
        assertTrue(pod.action.getTargets().getTargetCards().contains(pod.lotus));
        assertLiveUntouched(pod);
    }
    @Test public void anAlreadyAvailableFollowupDoesNotJustifyWastingTwiddle() {
        Pod pod = pod(false, true, false);
        AiAbilityDecision decision = SpellApiToAi.Converter.get(pod.action).canPlayWithSubs(pod.actor, pod.action);
        assertFalse(decision.willingToPlay());
        assertLiveUntouched(pod);
    }
    @Test public void insufficientNetManaDoesNotJustifyTheUntap() {
        Pod pod = pod(false, false, true);
        AiAbilityDecision decision = SpellApiToAi.Converter.get(pod.action).canPlayWithSubs(pod.actor, pod.action);
        assertFalse(decision.willingToPlay());
        assertLiveUntouched(pod);
    }
    @Test public void theFullPilotCastsTheReadyFollowupWithoutTwiddle() {
        Pod pod = pod(false, true, false);
        List<SpellAbility> actions = ai(pod.actor).chooseSpellAbilityToPlay();
        assertNotNull(actions);
        assertTrue(actions.stream().anyMatch(action -> action.getHostCard().getId() == pod.followup.getId()));
        assertTrue(actions.stream().noneMatch(action -> action.getHostCard().getId() == pod.spell.getId()));
        assertTrue(pod.spell.isInZone(ZoneType.Hand));
    }
    @Test public void sharedApiKeepsBothUniqueUntapsForTheRequiredFollowup() {
        Pod pod = pod(true, false, false);
        AiAbilityDecision decision = SpellApiToAi.Converter.get(pod.action).canPlayWithSubs(pod.actor, pod.action);
        assertTrue(decision.willingToPlay(), "Both paid untaps must survive subability validation: " + decision);
        assertEquals(pod.action.getTargets().size(), 1);
        assertEquals(pod.action.getSubAbility().getTargets().size(), 1);
        assertNotEquals(pod.action.getTargets().getFirstTargetedCard().getId(), pod.action.getSubAbility().getTargets().getFirstTargetedCard().getId());
        assertLiveUntouched(pod);
    }
    @Test public void heuristicApiCanPayAndResolveTheProfitableUntap() { sharedPaid(null, false); }
    @Test public void hybridApiCanPayAndResolveTheProfitableUntap() { sharedPaid(AIOption.USE_HYBRID_SIMULATION, false); }
    @Test public void heuristicApiKeepsAndResolvesBothUntaps() { sharedPaid(null, true); }
    @Test public void hybridApiKeepsAndResolvesBothUntaps() { sharedPaid(AIOption.USE_HYBRID_SIMULATION, true); }
    @Test public void sharedApiRejectsAnUnaffordableUntap() {
        Pod pod = pod(false, false, false);
        for (Card land : pod.actor.getLandsInPlay()) land.setTapped(true);
        AiAbilityDecision decision = SpellApiToAi.Converter.get(pod.action).canPlayWithSubs(pod.actor, pod.action);
        assertFalse(decision.willingToPlay());
        assertTrue(pod.lotus.isTapped());
        assertTrue(pod.spell.isInZone(ZoneType.Hand));
        assertEquals(pod.actor.getSpellsCastThisTurn(), 0);
        assertTrue(pod.game.getStack().isEmpty());
    }
    @Test public void fullPilotRejectsAnUnaffordableUntap() {
        Pod pod = pod(false, false, false);
        for (Card land : pod.actor.getLandsInPlay()) land.setTapped(true);
        List<SpellAbility> actions = ai(pod.actor).chooseSpellAbilityToPlay();
        assertTrue(actions == null || actions.stream().noneMatch(action -> action.getHostCard().getId() == pod.spell.getId()));
        assertTrue(pod.lotus.isTapped());
        assertTrue(pod.spell.isInZone(ZoneType.Hand));
        assertEquals(pod.actor.getSpellsCastThisTurn(), 0);
    }
    @Test public void anIncompleteAlternativeSearchDoesNotClaimAnUntapImprovement() {
        // Distinct names keep these controlled copies from hand-name deduplication.
        // This is a search-bound contract, not a claim about these artificial card names.
        Pod pod = pod(false, true, false, 33);
        assertTrue(new forge.ai.simulation.SpellAbilityPicker(pod.actor).getCandidateSpellsAndAbilities().size() > 32);
        AiAbilityDecision decision = SpellApiToAi.Converter.get(pod.action).canPlayWithSubs(pod.actor, pod.action);
        assertFalse(decision.willingToPlay(), "An incomplete baseline cannot justify wasting Twiddle: " + decision);
        assertLiveUntouched(pod);
    }
    private void sharedPaid(AIOption profile, boolean chained) {
        Pod pod = pod(chained, false, false);
        ai(pod.actor).setUseSimulation(profile);
        AiAbilityDecision decision = SpellApiToAi.Converter.get(pod.action).canPlayWithSubs(pod.actor, pod.action);
        assertTrue(decision.willingToPlay(), decision.toString());
        assertLiveUntouched(pod);
        assertTrue(pod.lotus.isTapped());
        if (chained) {
            assertTrue(pod.ring.isTapped());
            assertNotEquals(pod.action.getTargets().getFirstTargetedCard().getId(), pod.action.getSubAbility().getTargets().getFirstTargetedCard().getId());
        }
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        settle(pod.game);
        assertFalse(pod.lotus.isTapped());
        if (chained) assertFalse(pod.ring.isTapped());
        SpellAbility followup = pod.followup.getFirstSpellAbility();
        followup.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.playStack(followup, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.followup).isInZone(ZoneType.Battlefield));
        assertEquals(pod.actor.getSpellsCastThisTurn(), 2);
    }
    private void pilot(boolean chained) {
        Pod pod = pod(chained, false, false);
        List<SpellAbility> actions = ai(pod.actor).chooseSpellAbilityToPlay();
        assertNotNull(actions);
        SpellAbility chosen = actions.stream().filter(action -> action.getHostCard().getId() == pod.spell.getId()).findFirst().orElseThrow();
        if (chained) {
            assertEquals(chosen.getTargets().size(), 1);
            assertNotNull(chosen.getSubAbility());
            assertEquals(chosen.getSubAbility().getTargets().size(), 1);
            assertNotEquals(chosen.getTargets().getFirstTargetedCard().getId(), chosen.getSubAbility().getTargets().getFirstTargetedCard().getId());
        }
        assertTrue(ComputerUtil.playStack(chosen, pod.actor, pod.game));
        settle(pod.game);
        assertFalse(pod.lotus.isTapped());
        if (chained) assertFalse(pod.ring.isTapped());
        List<SpellAbility> followups = ai(pod.actor).chooseSpellAbilityToPlay();
        assertNotNull(followups);
        SpellAbility followup = followups.stream().filter(action -> action.getHostCard().getId() == pod.followup.getId()).findFirst().orElseThrow();
        assertTrue(ComputerUtil.playStack(followup, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.followup).isInZone(ZoneType.Battlefield));
        assertEquals(pod.actor.getSpellsCastThisTurn(), 2);
    }
    private void assertLiveUntouched(Pod pod) {
        assertTrue(pod.spell.isInZone(ZoneType.Hand));
        assertTrue(pod.followup.isInZone(ZoneType.Hand));
        assertEquals(pod.actor.getSpellsCastThisTurn(), 0);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 0L);
        assertTrue(pod.game.getStack().isEmpty());
    }
    private AiController ai(Player actor) { return ((PlayerControllerAi) actor.getController()).getAi(); }
    private Pod pod(boolean chained, boolean ready, boolean insufficient) {
        return pod(chained, ready, insufficient, 0);
    }
    private Pod pod(boolean chained, boolean ready, boolean insufficient, int alternatives) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("untap-followup-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Paid untap and immediate follow-up"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        ai(actor).setUseSimulation(AIOption.USE_FULL_SIMULATION);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        for (int i = 0; i < (chained ? 2 : 1); i++) addCardToZone("Island", actor, ZoneType.Battlefield);
        addCardToZone("Wastes", actor, ZoneType.Battlefield);
        Card lotus = addCardToZone(insufficient ? "Mind Stone" : "Gilded Lotus", actor, ZoneType.Battlefield);
        lotus.setTapped(!ready);
        Card ring = null;
        if (chained) {
            ring = addCardToZone("Sol Ring", actor, ZoneType.Battlefield);
            ring.setTapped(true);
            Card encoder = addCardToZone("Invisible Stalker", actor, ZoneType.Battlefield);
            encoder.setSickness(false);
        }
        for (int i = 0; i < alternatives; i++) {
            Card option = addCardToZone("Opt", actor, ZoneType.Hand);
            option.setName("Controlled alternative " + i);
        }
        Card followup = addCardToZone(chained ? "Rorix Bladewing" : "Hellrider", actor, ZoneType.Hand);
        Card spell = addCardToZone(chained ? "Hidden Strings" : "Twiddle", actor, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        SpellAbility action = spell.getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        return new Pod(game, actor, spell, followup, lotus, ring, action);
    }
    private void settle(Game game) {
        for (int i = 0; i < 40; i++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        throw new AssertionError("Untap follow-up stack did not settle");
    }
    private record Pod(Game game, Player actor, Card spell, Card followup, Card lotus, Card ring, SpellAbility action) { }
}
