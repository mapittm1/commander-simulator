package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Real activated untaps must pay their source costs and enable a native paid follow-up. */
public class CommanderActivatedUntapTest extends AITest {
    @Test public void fatestitcherPaysItsTapCostAndEnablesHellrider() { paid("Fatestitcher"); }
    @Test public void puppetStringsPaysTwoManaAndItsTapCostBeforeTheElfUntaps() { paid("Puppet Strings"); }
    @Test public void aPlannedSharedApiActivationPaysAndCastsTheFollowup() {
        Pod pod = pod("Mind Over Matter"); AiAbilityDecision planned = decision(pod);
        assertTrue(planned.willingToPlay(), planned.toString()); assertUntouched(pod);
        pod.action.resetTargets(); paid(pod);
        assertFalse(forge.ai.simulation.DiscardPaymentChoices.hasPlan(pod.action));
    }
    @Test public void sharedApiPlansFatestitchersPaidSetup() { admission("Fatestitcher"); }
    @Test public void sharedApiPlansPuppetStringsPaidSetup() { admission("Puppet Strings"); }
    @Test public void sharedApiPlansMindOverMattersDiscardSetup() { admission("Mind Over Matter"); }
    @Test public void aSummoningSickFatestitcherIsNotAdmitted() {
        Pod pod = pod("Fatestitcher"); pod.source.setSickness(true);
        assertFalse(pod.action.canPlay()); assertFalse(decision(pod).willingToPlay()); assertUntouched(pod);
    }
    @Test public void discardingTheOnlyFollowupIsNotAProfitableSetup() {
        Pod pod = pod("Mind Over Matter"); pod.game.getAction().moveToGraveyard(pod.fodder, null);
        assertFalse(decision(pod).willingToPlay());
        assertTrue(pod.followup.isInZone(ZoneType.Hand)); assertTrue(pod.mana.isTapped());
        assertTrue(pod.actor.getDiscardedThisTurn().isEmpty());
    }
    @Test public void puppetStringsCannotIgnoreItsManaCost() {
        Pod pod = pod("Puppet Strings"); for (Card land : pod.actor.getLandsInPlay()) land.setTapped(true);
        assertFalse(decision(pod).willingToPlay()); assertTrue(pod.mana.isTapped());
        assertFalse(pod.source.isTapped()); assertTrue(pod.followup.isInZone(ZoneType.Hand));
    }
    @Test public void fullPilotSelectsTheActivationAndItsFollowup() { pilot("Fatestitcher"); }
    @Test public void fullPilotSelectsTheDiscardAndItsFollowup() { pilot("Mind Over Matter"); }
    @Test public void fullPilotSelectsTheManaAndTapCostSetup() { pilot("Puppet Strings"); }
    @Test public void sharedApiPlansTheCombinedManaTapAndDiscardCost() { admission("Trickster Mage"); }
    @Test public void fullPilotReplaysTheCombinedManaTapAndDiscardCost() { pilot("Trickster Mage"); }
    @Test public void aCopiedPaymentPlanDoesNotConsumeItsSourceChoice() {
        Pod pod = plannedPod(); SpellAbility copy = pod.action.copy();
        Card selected = forge.ai.simulation.DiscardPaymentChoices.takePlannedCard(pod.actor, copy,
                copy.getPayCosts().getCostPartByType(forge.game.cost.CostDiscard.class), false, CardCollection.EMPTY);
        assertEquals(selected, pod.fodder); assertFalse(forge.ai.simulation.DiscardPaymentChoices.hasPlan(copy));
        assertTrue(forge.ai.simulation.DiscardPaymentChoices.hasPlan(pod.action)); assertUntouched(pod);
    }
    @Test public void snapshotPaymentUsesCopiedHandCardsAndLeavesTheOriginalPlanIntact() {
        Pod pod = plannedPod(); forge.ai.simulation.GameCopier copier = new forge.ai.simulation.GameCopier(pod.game);
        Game clone = copier.makeCopy(null, pod.actor); Player actor = copier.find(pod.actor);
        SpellAbility action = copier.find(pod.source).getSpellAbilities().stream().filter(sa -> sa.getApi() == ApiType.TapOrUntap).findFirst().orElseThrow();
        Card chosen = forge.ai.simulation.DiscardPaymentChoices.takePlannedCard(actor, action,
                action.getPayCosts().getCostPartByType(forge.game.cost.CostDiscard.class), false, CardCollection.EMPTY);
        assertNotNull(chosen); assertNotSame(chosen, pod.fodder); assertSame(chosen.getGame(), clone);
        assertEquals(chosen.getId(), pod.fodder.getId());
        assertTrue(forge.ai.simulation.DiscardPaymentChoices.hasPlan(pod.action)); assertUntouched(pod);
    }
    @Test public void aDepartedDiscardDoesNotSilentlySpendAnotherCard() {
        Pod pod = plannedPod(); pod.game.getAction().moveToGraveyard(pod.fodder, null);
        assertFalse(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        assertTrue(pod.followup.isInZone(ZoneType.Hand)); assertTrue(pod.mana.isTapped());
        assertTrue(pod.actor.getDiscardedThisTurn().isEmpty()); assertTrue(pod.game.getStack().isEmpty());
    }
    @Test public void aReturnedDiscardHasANewIdentityForPaymentReplay() {
        Pod pod = plannedPod(); Card moved = pod.game.getAction().moveToGraveyard(pod.fodder, null);
        Card returned = pod.game.getAction().moveToHand(moved, null);
        assertNotEquals(returned.getGameTimestamp(), pod.fodder.getGameTimestamp());
        assertNull(peek(pod)); assertFalse(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        assertTrue(pod.followup.isInZone(ZoneType.Hand)); assertTrue(returned.isInZone(ZoneType.Hand));
        assertTrue(pod.actor.getDiscardedThisTurn().isEmpty());
    }
    @Test public void aDifferentActingPlayerCannotUseThePaymentPlan() {
        Pod pod = plannedPod(); Player other = pod.game.getPlayers().get(1);
        addCardToZone("Forest", other, ZoneType.Hand); pod.action.setActivatingPlayer(other);
        forge.game.cost.CostDiscard cost = pod.action.getPayCosts().getCostPartByType(forge.game.cost.CostDiscard.class);
        assertTrue(cost.canPay(pod.action, other, false));
        assertNull(forge.ai.simulation.DiscardPaymentChoices.peekPlannedCard(other, pod.action, cost, false, CardCollection.EMPTY));
        pod.action.setActivatingPlayer(pod.actor); assertUntouched(pod);
    }
    @Test public void aChangedTargetInvalidatesThePreparedPayment() {
        Pod pod = plannedPod(); pod.action.resetTargets(); pod.action.getTargets().add(pod.actor.getLandsInPlay().get(0));
        assertNull(peek(pod)); assertFalse(ComputerUtil.playStack(pod.action, pod.actor, pod.game)); assertUntouched(pod);
    }
    @Test public void aReturnedTargetCannotKeepAnOldPaymentPlan() {
        Pod pod = plannedPod(); Card moved = pod.game.getAction().moveToGraveyard(pod.mana, null);
        Card returned = pod.game.getAction().moveToPlay(moved, pod.actor, null, null); returned.setTapped(true);
        assertNull(peek(pod)); assertTrue(pod.fodder.isInZone(ZoneType.Hand)); assertTrue(pod.followup.isInZone(ZoneType.Hand));
    }
    @Test public void malformedPrivateMetadataCannotSelectARealDiscard() {
        Pod pod = plannedPod(); forge.ai.simulation.DiscardPaymentChoices.installPlan(pod.action, "invalid-payment-choice");
        assertNull(peek(pod)); assertFalse(ComputerUtil.playStack(pod.action, pod.actor, pod.game)); assertUntouched(pod);
    }
    @Test public void aChangedDiscardCountCannotReuseTheSingleCardPlan() {
        Pod pod = plannedPod(); pod.action.setPayCosts(new forge.game.cost.Cost("Discard<2/Card>", false));
        assertNull(peek(pod)); assertFalse(ComputerUtil.playStack(pod.action, pod.actor, pod.game)); assertUntouched(pod);
    }
    @Test public void theAiDoesNotSacrificeItsUntapTargetToFundTheSetup() {
        Pod pod = pod("Trickster Mage");
        for (Card land : pod.actor.getLandsInPlay()) land.setTapped(true);
        Card petal = addCardToZone("Lotus Petal", pod.actor, ZoneType.Battlefield);
        pod.game.getAction().checkStateEffects(true); pod.game.getTriggerHandler().resetActiveTriggers(); pod.game.copyLastState();
        pod.action.getTargets().add(petal);
        for (SpellAbility candidate : forge.ai.simulation.DiscardPaymentChoices.variants(pod.action)) {
            Card chosen = forge.ai.simulation.DiscardPaymentChoices.peekPlannedCard(pod.actor, candidate,
                    candidate.getPayCosts().getCostPartByType(forge.game.cost.CostDiscard.class), false, CardCollection.EMPTY);
            if (pod.fodder.equals(chosen)) {
                forge.ai.simulation.DiscardPaymentChoices.installPlan(pod.action, forge.ai.simulation.DiscardPaymentChoices.plannedChoice(candidate)); break;
            }
        }
        assertTrue(forge.ai.simulation.DiscardPaymentChoices.hasPlan(pod.action));
        assertFalse(ComputerUtilCost.canPayCost(pod.action, pod.actor, false));
        assertFalse(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        assertTrue(petal.isInZone(ZoneType.Battlefield)); assertTrue(pod.fodder.isInZone(ZoneType.Hand));
        assertTrue(pod.followup.isInZone(ZoneType.Hand)); assertFalse(pod.source.isTapped()); assertTrue(pod.mana.isTapped());
        assertTrue(pod.actor.getDiscardedThisTurn().isEmpty()); assertTrue(pod.game.getStack().isEmpty());
    }
    @Test public void aReturnedSourceCannotKeepItsOldPaymentPlan() {
        Pod pod = plannedPod(); Card moved = pod.game.getAction().moveToGraveyard(pod.source, null);
        Card returned = pod.game.getAction().moveToPlay(moved, pod.actor, null, null);
        assertNotEquals(returned.getGameTimestamp(), pod.source.getGameTimestamp());
        assertNull(peek(pod)); assertTrue(pod.fodder.isInZone(ZoneType.Hand)); assertTrue(pod.followup.isInZone(ZoneType.Hand));
    }
    @Test public void aNewTargetingProhibitionInvalidatesPreparedPayment() {
        Pod pod = plannedPod(); pod.mana.addIntrinsicKeyword("Shroud"); pod.game.getAction().checkStateEffects(true);
        assertFalse(pod.action.canTarget(pod.mana)); assertNull(peek(pod)); assertUntouched(pod);
    }
    @Test public void aRandomDiscardCostCannotReuseAChosenCardPlan() {
        Pod pod = plannedPod(); pod.action.setPayCosts(new forge.game.cost.Cost("Discard<1/Random>", false));
        assertNull(peek(pod)); assertFalse(ComputerUtil.playStack(pod.action, pod.actor, pod.game)); assertUntouched(pod);
    }
    @Test public void oversizedDiscardSearchRetainsTheNativePaymentPolicy() {
        Pod pod = pod("Mind Over Matter");
        for (int i = 0; i < 33; i++) addCardToZone("Forest", pod.actor, ZoneType.Hand);
        Card preferred = addCardToZone("Squee, Goblin Nabob", pod.actor, ZoneType.Hand);
        // One legal target keeps this payment-policy contract independent of target search.
        for (Card permanent : pod.actor.getCardsIn(ZoneType.Battlefield)) if (!permanent.equals(pod.mana)) permanent.addIntrinsicKeyword("Shroud");
        pod.game.getAction().checkStateEffects(true); pod.game.getTriggerHandler().resetActiveTriggers(); pod.game.copyLastState();
        pod.action.getTargets().add(pod.mana);
        CardCollection nativeChoice = ((PlayerControllerAi) pod.actor.getController()).getAi().getCardsToDiscard(
                1, new String[]{"Card"}, pod.action, CardCollection.EMPTY);
        assertEquals(nativeChoice.size(), 1); assertEquals(nativeChoice.get(0).getId(), preferred.getId());
        forge.ai.simulation.GameStateEvaluator.Score initial = new forge.ai.simulation.GameStateEvaluator().getScoreForGameState(pod.game, pod.actor);
        forge.ai.simulation.SimulationController controller = new forge.ai.simulation.SimulationController(initial, 0);
        controller.evaluateSpellAbility(List.of(pod.action), 0);
        forge.ai.simulation.SpellAbilityChoicesIterator choices = new forge.ai.simulation.SpellAbilityChoicesIterator(controller);
        forge.ai.simulation.GameSimulator simulator = new forge.ai.simulation.GameSimulator(controller, pod.game, pod.actor, null);
        simulator.setInterceptor(choices);
        forge.ai.simulation.GameStateEvaluator.Score result = simulator.simulateSpellAbility(pod.action);
        assertNotEquals(result.value, Integer.MIN_VALUE);
        Player projected = simulator.getSimulatedAiPlayer();
        assertEquals(projected.getDiscardedThisTurn().size(), 1);
        assertEquals(projected.getDiscardedThisTurn().get(0).getId(), preferred.getId(), "A bounded search must retain native Squee preference");
        assertFalse(choices.advance(result)); controller.doneEvaluating(result);
        assertTrue(preferred.isInZone(ZoneType.Hand)); assertUntouched(pod);
    }
    private Pod plannedPod() {
        Pod pod = pod("Mind Over Matter"); pod.action.getTargets().add(pod.mana);
        for (SpellAbility candidate : forge.ai.simulation.DiscardPaymentChoices.variants(pod.action)) {
            forge.game.cost.CostDiscard cost = candidate.getPayCosts().getCostPartByType(forge.game.cost.CostDiscard.class);
            Card chosen = forge.ai.simulation.DiscardPaymentChoices.peekPlannedCard(pod.actor, candidate, cost, false, CardCollection.EMPTY);
            if (pod.fodder.equals(chosen)) {
                forge.ai.simulation.DiscardPaymentChoices.installPlan(pod.action, forge.ai.simulation.DiscardPaymentChoices.plannedChoice(candidate)); return pod;
            }
        }
        throw new AssertionError("No legal prepared spare-card payment");
    }
    private Card peek(Pod pod) {
        return forge.ai.simulation.DiscardPaymentChoices.peekPlannedCard(pod.actor, pod.action,
                pod.action.getPayCosts().getCostPartByType(forge.game.cost.CostDiscard.class), false, CardCollection.EMPTY);
    }
    private void pilot(String sourceName) {
        Pod pod = pod(sourceName);
        AiController ai = ((PlayerControllerAi) pod.actor.getController()).getAi();
        ai.setUseSimulation(AIOption.USE_FULL_SIMULATION);
        List<SpellAbility> actions = ai.chooseSpellAbilityToPlay(); assertNotNull(actions);
        SpellAbility chosen = actions.stream().filter(sa -> sa.getHostCard().getId() == pod.source.getId() && sa.getApi() == ApiType.TapOrUntap).findFirst().orElseThrow();
        assertTrue(ComputerUtil.playStack(chosen, pod.actor, pod.game)); settle(pod.game);
        assertEquals(pod.source.isTapped(), pod.action.getPayCosts().hasTapCost()); assertFalse(pod.mana.isTapped());
        if (pod.fodder != null) assertTrue(pod.game.getCardState(pod.fodder).isInZone(ZoneType.Graveyard));
        actions = ai.chooseSpellAbilityToPlay(); assertNotNull(actions);
        chosen = actions.stream().filter(sa -> sa.getHostCard().getId() == pod.followup.getId()).findFirst().orElseThrow();
        assertTrue(ComputerUtil.playStack(chosen, pod.actor, pod.game)); settle(pod.game);
        assertTrue(pod.game.getCardState(pod.followup).isInZone(ZoneType.Battlefield));
        assertEquals(pod.actor.getSpellsCastThisTurn(), 1);
    }
    @Test public void anExplicitLegalDiscardEnablesTheNativeFollowup() {
        Pod pod = pod("Mind Over Matter");
        AiController supplied = new AiController(pod.actor, pod.game) {
            @Override public CardCollection getCardsToDiscard(int number, String[] types, SpellAbility action, CardCollectionView exclude) {
                assertEquals(number, 1); assertTrue(pod.fodder.isInZone(ZoneType.Hand));
                assertFalse(exclude.contains(pod.fodder)); return new CardCollection(pod.fodder);
            }
        };
        PlayerControllerAi controller = new PlayerControllerAi(pod.game, pod.actor, pod.actor.getLobbyPlayer()) {
            @Override public AiController getAi() { return supplied; }
        };
        pod.actor.runWithController(() -> paid(pod), controller);
    }
    @Test public void aPlannedForestDiscardIsConsumedByNativePayment() {
        Pod pod = pod("Mind Over Matter"); pod.action.getTargets().add(pod.mana);
        for (SpellAbility candidate : forge.ai.simulation.DiscardPaymentChoices.variants(pod.action)) {
            if (!forge.ai.simulation.DiscardPaymentChoices.hasPlan(candidate)) continue;
            Card chosen = forge.ai.simulation.DiscardPaymentChoices.takePlannedCard(pod.actor, candidate.copy(),
                    candidate.getPayCosts().getCostPartByType(forge.game.cost.CostDiscard.class), false, CardCollection.EMPTY);
            if (chosen != null && chosen.equals(pod.fodder)) {
                pod.action.setPlannedAiChoice("AIPaidDiscard", candidate.getPlannedAiChoice("AIPaidDiscard"));
                pod.action.resetTargets(); paid(pod);
                assertFalse(forge.ai.simulation.DiscardPaymentChoices.hasPlan(pod.action)); return;
            }
        }
        fail("No legal Forest payment variant");
    }
    @Test public void anAvailableLandPlayDoesNotJustifyTheDiscardSetup() {
        Pod pod = pod("Mind Over Matter"); pod.actor.setLandsPlayedThisTurn(0);
        assertFalse(decision(pod).willingToPlay()); assertUntouched(pod);
    }
    @Test public void fullPilotKeepsTheBetterImmediateLandPlay() {
        Pod pod = pod("Mind Over Matter"); pod.actor.setLandsPlayedThisTurn(0);
        List<SpellAbility> actions = ((PlayerControllerAi) pod.actor.getController()).getAi().chooseSpellAbilityToPlay();
        assertNotNull(actions); assertTrue(actions.stream().anyMatch(sa -> sa.isLandAbility() && sa.getHostCard().getId() == pod.fodder.getId()));
        assertTrue(actions.stream().noneMatch(sa -> sa.getApi() == ApiType.TapOrUntap)); assertUntouched(pod);
    }
    private void admission(String name) {
        Pod pod = pod(name); AiAbilityDecision decision = decision(pod);
        assertTrue(decision.willingToPlay(), name + ": " + decision);
        assertTrue(pod.action.getTargets().getTargetCards().contains(pod.mana)); assertUntouched(pod);
    }
    private AiAbilityDecision decision(Pod pod) {
        return SpellApiToAi.Converter.get(pod.action).canPlayWithSubs(pod.actor, pod.action);
    }
    private void assertUntouched(Pod pod) {
        assertTrue(pod.mana.isTapped()); assertFalse(pod.source.isTapped());
        assertTrue(pod.followup.isInZone(ZoneType.Hand));
        if (pod.fodder != null) assertTrue(pod.fodder.isInZone(ZoneType.Hand));
        assertTrue(pod.actor.getDiscardedThisTurn().isEmpty()); assertEquals(pod.actor.getSpellsCastThisTurn(), 0);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 0L);
        assertTrue(pod.game.getStack().isEmpty());
    }
    private void paid(String name) { paid(pod(name)); }
    private void paid(Pod pod) {
        String name = pod.source.getName(); pod.action.getTargets().add(pod.mana);
        assertTrue(pod.action.canPlay()); assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        settle(pod.game); assertFalse(pod.mana.isTapped()); assertEquals(pod.actor.getSpellsCastThisTurn(), 0);
        if (pod.fodder == null) assertTrue(pod.source.isTapped());
        else {
            assertTrue(pod.game.getCardState(pod.fodder).isInZone(ZoneType.Graveyard));
            assertEquals(pod.actor.getDiscardedThisTurn().size(), 1);
            assertEquals(pod.source.isTapped(), pod.action.getPayCosts().hasTapCost());
        }
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), (long) pod.action.getPayCosts().getTotalMana().getCMC());
        SpellAbility followup = pod.followup.getFirstSpellAbility(); followup.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.playStack(followup, pod.actor, pod.game)); settle(pod.game);
        assertTrue(pod.game.getCardState(pod.followup).isInZone(ZoneType.Battlefield)); assertEquals(pod.actor.getSpellsCastThisTurn(), 1);
    }
    private Pod pod(String sourceName) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("activated-untap-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Paid activated untaps"));
        game.setNoGUIUser(); game.setAge(GameStage.Play); Player actor = game.getPlayers().get(0);
        ((PlayerControllerAi) actor.getController()).getAi().setUseSimulation(AIOption.USE_FULL_SIMULATION);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        boolean strings = sourceName.equals("Puppet Strings");
        for (int i = 0; i < (strings ? 2 : 1); i++) addCardToZone("Wastes", actor, ZoneType.Battlefield);
        if (sourceName.equals("Trickster Mage")) addCardToZone("Island", actor, ZoneType.Battlefield);
        Card source = addCardToZone(sourceName, actor, ZoneType.Battlefield); source.setSickness(false);
        Card mana = addCardToZone(strings ? "Priest of Titania" : "Gilded Lotus", actor, ZoneType.Battlefield); mana.setSickness(false); mana.setTapped(true);
        if (strings) for (int i = 0; i < 3; i++) addCardToZone("Elvish Visionary", actor, ZoneType.Battlefield);
        Card followup = addCardToZone(strings ? "Questing Beast" : "Hellrider", actor, ZoneType.Hand);
        Card fodder = (sourceName.equals("Mind Over Matter") || sourceName.equals("Trickster Mage")) ? addCardToZone("Forest", actor, ZoneType.Hand) : null;
        if (sourceName.equals("Mind Over Matter") || sourceName.equals("Trickster Mage")) actor.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2); game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        SpellAbility action = source.getSpellAbilities().stream().filter(sa -> sa.getApi() == ApiType.TapOrUntap).findFirst().orElseThrow();
        action.setActivatingPlayer(actor); return new Pod(game, actor, source, mana, followup, fodder, action);
    }
    private void settle(Game game) {
        for (int i = 0; i < 40; i++) {
            game.getAction().checkStateEffects(true); game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return; game.getStack().resolveStack();
        }
        throw new AssertionError("Activated untap stack did not settle");
    }
    private record Pod(Game game, Player actor, Card source, Card mana, Card followup, Card fodder, SpellAbility action) { }
}
