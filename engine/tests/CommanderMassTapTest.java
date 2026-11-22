package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.PlayerController;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Real paid and triggered mass tap/untap decisions with independent branch controls. */
public class CommanderMassTapTest extends AITest {
    @Test public void suppliedLandUntapPaysBeforeRecoveringMana() {
        Pod pod = pod(false); resolve(pod, "Land", false);
        assertTrue(pod.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertTrue(pod.lotus.isTapped()); assertPaid(pod);
        castFollowup(pod);
    }
    @Test public void suppliedArtifactUntapDoesNotUntapLands() {
        Pod pod = pod(false); resolve(pod, "Artifact", false);
        assertFalse(pod.lotus.isTapped()); assertTrue(pod.actor.getLandsInPlay().stream().allMatch(Card::isTapped));
        assertPaid(pod);
    }
    @Test public void suppliedCreatureTapAffectsOnlyTheTargetPlayer() {
        Pod pod = pod(true); resolve(pod, "Creature", true);
        assertTrue(pod.enemyCreature.isTapped()); assertFalse(pod.ownCreature.isTapped()); assertPaid(pod);
    }
    @Test public void aStunCounterReplacesOnlyItsOwnArtifactUntap() {
        Pod pod = pod(false); pod.lotus.setCounters(CounterEnumType.STUN, 1);
        Card ring = addCardToZone("Sol Ring", pod.actor, ZoneType.Battlefield); ring.setTapped(true);
        resolve(pod, "Artifact", false);
        assertTrue(pod.lotus.isTapped()); assertEquals(pod.lotus.getCounters(CounterEnumType.STUN), 0);
        assertFalse(ring.isTapped()); assertPaid(pod);
    }
    @Test public void theDefaultControllerUntapsItsOwnChosenLands() {
        Pod pod = pod(false); resolve(pod, "Land", null);
        assertTrue(pod.actor.getLandsInPlay().stream().noneMatch(Card::isTapped)); assertPaid(pod);
    }
    @Test public void theDefaultControllerUntapsItsOwnChosenCreatures() {
        Pod pod = pod(false); pod.ownCreature.setTapped(true); resolve(pod, "Creature", null);
        assertFalse(pod.ownCreature.isTapped()); assertPaid(pod);
    }
    @Test public void theDefaultControllerTapsAnOpponentsChosenCreatures() {
        Pod pod = pod(true); resolve(pod, "Creature", null);
        assertTrue(pod.enemyCreature.isTapped()); assertFalse(pod.ownCreature.isTapped()); assertPaid(pod);
    }
    @Test public void theSharedApiPlansTheTypeAndDirectionBeforePayment() {
        Pod pod = pod(false); pod.action.getSubAbility().resetTargets();
        AiAbilityDecision decision = SpellApiToAi.Converter.get(pod.action).canPlayWithSubs(pod.actor, pod.action);
        assertTrue(decision.willingToPlay(), decision.toString());
        assertTrue(pod.action.getSubAbility().getTargets().getFirstTargetedPlayer() == pod.actor);
        assertTrue(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count() == 3);
        assertTrue(pod.spell.isInZone(ZoneType.Hand));
        resolve(pod, null, null); castFollowup(pod);
    }
    @Test public void theFullPilotSelectsTheRecoveryAndNativeFollowup() {
        Pod pod = pod(false); pod.action.getSubAbility().resetTargets();
        AiController ai = ((PlayerControllerAi) pod.actor.getController()).getAi();
        List<SpellAbility> actions = ai.chooseSpellAbilityToPlay(); assertNotNull(actions);
        SpellAbility selected = actions.stream().filter(sa -> sa.getHostCard().getId() == pod.spell.getId()).findFirst().orElseThrow();
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, selected, null)); settle(pod.game);
        assertTrue(pod.actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        actions = ai.chooseSpellAbilityToPlay(); assertNotNull(actions);
        selected = actions.stream().filter(sa -> sa.getHostCard().getId() == pod.followup.getId()).findFirst().orElseThrow();
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, selected, null)); settle(pod.game);
        assertTrue(pod.game.getCardState(pod.followup).isInZone(ZoneType.Battlefield));
    }
    @Test public void aNewPlayerTargetProhibitionStopsTheWholeSpell() {
        Pod pod = pod(true);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, pod.action, null));
        addCardToZone("Leyline of Sanctity", pod.enemy, ZoneType.Battlefield); settle(pod.game);
        assertFalse(pod.enemyCreature.isTapped()); assertPaid(pod);
    }
    @Test public void suppliedFacesUntapsTheSurvivingSharedType() { faces(false, false, false); }
    @Test public void theDefaultFacesControllerUntapsItsOwnSharedType() { faces(false, null, false); }
    @Test public void theDefaultFacesControllerTapsTheOpposingSharedType() { faces(true, null, true); }
    @Test public void suppliedFacesTapLeavesNonmatchingCreaturesUntapped() { faces(true, true, true); }

    @Test public void aCounteredRecoverySpellKeepsItsCostsPaid() {
        Pod pod = pod(false);
        for (int i = 0; i < 2; i++) addCardToZone("Island", pod.enemy, ZoneType.Battlefield);
        Card counter = addCardToZone("Counterspell", pod.enemy, ZoneType.Hand);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, pod.action, null));
        SpellAbility response = counter.getFirstSpellAbility(); response.setActivatingPlayer(pod.enemy);
        response.getTargets().add(pod.game.getStack().peekAbility());
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.enemy, response, null)); settle(pod.game);
        assertPaid(pod); assertTrue(pod.actor.getLandsInPlay().stream().allMatch(Card::isTapped));
        assertTrue(pod.enemy.getLandsInPlay().stream().allMatch(Card::isTapped));
        assertTrue(pod.game.getCardState(counter).isInZone(ZoneType.Graveyard));
        assertTrue(pod.lotus.isTapped()); assertTrue(pod.followup.isInZone(ZoneType.Hand));
    }
    @Test public void anUnaffordableRecoveryIsNotAdmitted() {
        Pod pod = pod(false); pod.actor.getLandsInPlay().forEach(land -> land.setTapped(true));
        pod.action.getSubAbility().resetTargets();
        assertFalse(ComputerUtilCost.canPayCost(pod.action, pod.actor, false));
        assertFalse(SpellApiToAi.Converter.get(pod.action).canPlayWithSubs(pod.actor, pod.action).willingToPlay());
        assertTrue(pod.spell.isInZone(ZoneType.Hand)); assertEquals(pod.actor.getSpellsCastThisTurn(), 0);
        assertTrue(pod.actor.getLandsInPlay().stream().allMatch(Card::isTapped));
    }
    @Test public void anAlreadyAvailableFollowupDoesNotJustifyWastingTheRecovery() {
        Pod pod = pod(false); pod.actor.getLandsInPlay().forEach(land -> land.setTapped(false));
        pod.game.getAction().moveToGraveyard(pod.ownCreature, null);
        pod.game.getAction().moveToGraveyard(pod.enemyCreature, null);
        pod.game.getAction().checkStateEffects(true); pod.game.getTriggerHandler().resetActiveTriggers(); pod.game.copyLastState();
        pod.action.getSubAbility().resetTargets();
        assertFalse(SpellApiToAi.Converter.get(pod.action).canPlayWithSubs(pod.actor, pod.action).willingToPlay());
        assertTrue(pod.spell.isInZone(ZoneType.Hand)); assertTrue(pod.followup.isInZone(ZoneType.Hand));
        assertEquals(pod.actor.getSpellsCastThisTurn(), 0);
    }
    @Test public void aDualTypedPermanentUntapsOnceAndTriggersOnce() {
        Pod pod = pod(false);
        Card ornithopter = addCardToZone("Ornithopter", pod.actor, ZoneType.Battlefield); ornithopter.setTapped(true);
        Card thrasher = addCardToZone("Wake Thrasher", pod.actor, ZoneType.Battlefield);
        resolve(pod, "Artifact", false);
        assertFalse(ornithopter.isTapped()); assertFalse(pod.lotus.isTapped());
        assertEquals(thrasher.getNetPower(), 3); assertPaid(pod);
    }
    @Test public void theChosenTypeUsesTheBattlefieldAtResolution() {
        Pod pod = pod(false);
        withChoices(pod, "Artifact", false, () -> {
            assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, pod.action, null));
            addCardToZone("Mycosynth Lattice", pod.enemy, ZoneType.Battlefield); settle(pod.game);
        });
        assertTrue(pod.actor.getCardsIn(ZoneType.Battlefield).stream().noneMatch(Card::isTapped));
        assertFalse(pod.enemyCreature.isTapped()); assertPaid(pod);
    }
    @Test public void aCopiedChoiceStreamIsConsumedIndependently() {
        Pod pod = pod(false); SpellAbility original = landPlan(pod); SpellAbility copy = original.copy();
        castPrepared(pod, copy, action -> { });
        assertEquals(original.getPlannedAiChoice("AITextFullPosition"), "0");
        assertEquals(copy.getPlannedAiChoice("AITextFullPosition"), "2");
        assertTrue(pod.actor.getLandsInPlay().stream().noneMatch(Card::isTapped)); assertPaid(pod);
    }
    @Test public void anIllegalRecordedTypeFallsBackToALegalNativeChoice() {
        Pod pod = pod(false); SpellAbility prepared = landPlan(pod);
        prepared.setPlannedAiChoice("AITextFullWord0", "mass-type:0:NotALegalType");
        castPrepared(pod, prepared, action -> { }); assertPaid(pod);
    }
    @Test public void aChangedPlayerTargetInvalidatesTheChoiceStream() {
        Pod pod = pod(false); SpellAbility prepared = landPlan(pod);
        castPrepared(pod, prepared, action -> {
            SpellAbility mass = action.getSubAbility(); mass.resetTargets(); mass.getTargets().add(pod.enemy);
            try { assertNull(forge.ai.simulation.TextChangeChoices.consumeFullWord(action,
                    forge.ai.simulation.MassTapChoices.typeTokens(action, List.of("Artifact", "Creature", "Land")))); }
            finally { mass.resetTargets(); mass.getTargets().add(pod.actor); }
            assertEquals(action.getPlannedAiChoice("AITextFullPosition"), "0");
        });
        assertTrue(pod.actor.getLandsInPlay().stream().noneMatch(Card::isTapped)); assertPaid(pod);
    }
    @Test public void aChangedActorInvalidatesTheChoiceStream() {
        Pod pod = pod(false); SpellAbility prepared = landPlan(pod);
        castPrepared(pod, prepared, action -> {
            action.setActivatingPlayer(pod.enemy);
            try { assertNull(forge.ai.simulation.TextChangeChoices.consumeFullWord(action,
                    forge.ai.simulation.MassTapChoices.typeTokens(action, List.of("Artifact", "Creature", "Land")))); }
            finally { action.setActivatingPlayer(pod.actor); }
            assertEquals(action.getPlannedAiChoice("AITextFullPosition"), "0");
        });
        assertTrue(pod.actor.getLandsInPlay().stream().noneMatch(Card::isTapped)); assertPaid(pod);
    }
    /** Structural chain contract: two mass-effect nodes have different target roles. */
    @Test public void swappingPlayerRolesCannotReuseTheChoiceStream() {
        Pod pod = pod(false); Card enemyLand = addCardToZone("Mountain", pod.enemy, ZoneType.Battlefield);
        SpellAbility first = pod.action.getSubAbility();
        forge.game.spellability.AbilitySub second = (forge.game.spellability.AbilitySub) first.copy();
        second.resetTargets(); second.getTargets().add(pod.enemy); first.setSubAbility(second);
        assertTrue(forge.ai.simulation.TextChangeChoices.stageFullWords(pod.action,
                List.of("mass-type:0:Land", "mass-direction:1:untap", "mass-direction:2:tap")));
        castPrepared(pod, pod.action, action -> {
            SpellAbility a = action.getSubAbility(), b = a.getSubAbility();
            a.resetTargets(); a.getTargets().add(pod.enemy); b.resetTargets(); b.getTargets().add(pod.actor);
            try { assertNull(forge.ai.simulation.TextChangeChoices.consumeFullWord(action,
                    forge.ai.simulation.MassTapChoices.typeTokens(action, List.of("Artifact", "Creature", "Land"))),
                    "Ordered target roles must not collapse to the same player set"); }
            finally { a.resetTargets(); a.getTargets().add(pod.actor); b.resetTargets(); b.getTargets().add(pod.enemy); }
        });
        assertTrue(pod.actor.getLandsInPlay().stream().noneMatch(Card::isTapped)); assertTrue(enemyLand.isTapped()); assertPaid(pod);
    }
    private SpellAbility landPlan(Pod pod) {
        return forge.ai.simulation.MassTapChoices.variants(pod.action).stream()
                .filter(action -> "mass-type:0:Land".equals(action.getPlannedAiChoice("AITextFullWord0"))
                        && "mass-direction:1:untap".equals(action.getPlannedAiChoice("AITextFullWord1")))
                .findFirst().orElseThrow();
    }
    private void castPrepared(Pod pod, SpellAbility prepared, java.util.function.Consumer<SpellAbility> check) {
        AiController brain = ((PlayerControllerAi) pod.actor.getController()).getAi();
        PlayerControllerAi controller = new PlayerControllerAi(pod.game, pod.actor, pod.actor.getLobbyPlayer()) {
            @Override public AiController getAi() { return brain; }
            @Override public String chooseSomeType(String kind, SpellAbility action, Collection<String> options, boolean optional) {
                check.accept(action); String selected = super.chooseSomeType(kind, action, options, optional);
                assertTrue(options.contains(selected)); return selected;
            }
        };
        pod.actor.runWithController(() -> {
            assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, prepared, null)); settle(pod.game);
        }, controller);
    }

    private void faces(boolean opposing, Boolean choice, boolean expectedTapped) {
        Pod pod = pod(false); addCardToZone("Faces of the Past", pod.actor, ZoneType.Battlefield);
        Card departed = addCardToZone("Coral Merfolk", pod.actor, ZoneType.Battlefield);
        Card survivor = addCardToZone("Coral Merfolk", opposing ? pod.enemy : pod.actor, ZoneType.Battlefield);
        survivor.setTapped(!expectedTapped);
        pod.game.getAction().checkStateEffects(true); pod.game.getTriggerHandler().resetActiveTriggers(); pod.game.copyLastState();
        withChoices(pod, null, choice, () -> {
            pod.game.getAction().moveToGraveyard(departed, null); settle(pod.game);
        });
        assertEquals(survivor.isTapped(), expectedTapped);
        assertFalse(pod.ownCreature.isTapped()); assertFalse(pod.enemyCreature.isTapped());
        assertTrue(pod.game.getCardState(departed).isInZone(ZoneType.Graveyard));
    }
    private void resolve(Pod pod, String type, Boolean tap) {
        withChoices(pod, type, tap, () -> {
            assertTrue(pod.action.canPlay());
            assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, pod.action, null)); settle(pod.game);
        });
    }
    private void withChoices(Pod pod, String type, Boolean tap, Runnable operation) {
        AiController brain = ((PlayerControllerAi) pod.actor.getController()).getAi();
        PlayerControllerAi controller = new PlayerControllerAi(pod.game, pod.actor, pod.actor.getLobbyPlayer()) {
            @Override public AiController getAi() { return brain; }
            @Override public String chooseSomeType(String kind, SpellAbility action, Collection<String> options, boolean optional) {
                if (type != null && action.getApi() == ApiType.ChooseType) {
                    assertTrue(options.contains(type)); return type;
                }
                return super.chooseSomeType(kind, action, options, optional);
            }
            @Override public boolean chooseBinary(SpellAbility action, String prompt, PlayerController.BinaryChoiceType kind, Boolean defaultValue) {
                if (tap != null && kind == PlayerController.BinaryChoiceType.TapOrUntap) return tap;
                return super.chooseBinary(action, prompt, kind, defaultValue);
            }
        };
        pod.actor.runWithController(operation, controller);
    }
    private void castFollowup(Pod pod) {
        SpellAbility spell = pod.followup.getFirstSpellAbility(); spell.setActivatingPlayer(pod.actor);
        assertTrue(spell.canPlay()); assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.actor, spell, null)); settle(pod.game);
        assertTrue(pod.game.getCardState(pod.followup).isInZone(ZoneType.Battlefield));
    }
    private void assertPaid(Pod pod) {
        assertEquals(pod.actor.getSpellsCastThisTurn(), 1);
        assertTrue(pod.game.getCardState(pod.spell).isInZone(ZoneType.Graveyard));
    }
    private Pod pod(boolean opposing) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("mass-tap-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Paid and triggered mass tap choices"));
        game.setNoGUIUser(); game.setAge(GameStage.Play); Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        ((PlayerControllerAi) actor.getController()).getAi().setUseSimulation(AIOption.USE_FULL_SIMULATION);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        for (int i = 0; i < 4; i++) addCardToZone("Island", actor, ZoneType.Battlefield);
        for (int i = 0; i < 3; i++) addCardToZone("Mountain", actor, ZoneType.Battlefield).setTapped(true);
        Card lotus = addCardToZone("Gilded Lotus", actor, ZoneType.Battlefield); lotus.setTapped(true);
        Card ownCreature = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield); ownCreature.setSickness(false);
        Card enemyCreature = addCardToZone("Grizzly Bears", enemy, ZoneType.Battlefield); enemyCreature.setSickness(false);
        Card spell = addCardToZone("Turnabout", actor, ZoneType.Hand);
        Card followup = addCardToZone("Rorix Bladewing", actor, ZoneType.Hand); actor.setLandsPlayedThisTurn(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2); game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        SpellAbility action = spell.getFirstSpellAbility(); action.setActivatingPlayer(actor);
        action.getSubAbility().getTargets().add(opposing ? enemy : actor);
        return new Pod(game, actor, enemy, lotus, ownCreature, enemyCreature, spell, followup, action);
    }
    private void settle(Game game) {
        for (int i = 0; i < 40; i++) {
            game.getAction().checkStateEffects(true); game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return; game.getStack().resolveStack();
        }
        throw new AssertionError("Mass tap stack did not settle");
    }
    private record Pod(Game game, Player actor, Player enemy, Card lotus, Card ownCreature, Card enemyCreature, Card spell, Card followup, SpellAbility action) { }
}
