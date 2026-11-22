package forge.ai;

import forge.ai.simulation.TextChangeChoices;
import forge.card.CardType;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** A plural creature word in a pending spell must remain a legal AI choice. */
public class CommanderPluralTextTest extends AITest {
    @Test
    public void aPaidNativePluralRewriteChangesThePendingSpellsActualBonus() {
        Pod pod = pod();
        pod.actor.dangerouslySetController(new PlayerControllerAi(pod.game, pod.actor, pod.actor.getLobbyPlayer()) {
            @Override
            public String chooseSomeType(String kind, SpellAbility action, Collection<String> options, boolean optional) {
                if (action.getApi() == ApiType.ChangeText) {
                    return options.contains("Elf") ? "Elf" : "Goblin";
                }
                return super.chooseSomeType(kind, action, options, optional);
            }
        });
        assertEquals(pod.target.getNetPower(), 2);
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        assertEquals(pod.pending.getChangedTextTypeWords().get("Elf"), "Goblin");
        assertEquals(pod.target.getNetPower(), 2, "The original pump spell is still pending");
        assertEquals(pod.game.getStack().size(), 1);
        settle(pod.game);
        assertEquals(pod.target.getNetPower(), 5, "Three Goblins must supply the rewritten bonus");
        assertEquals(pod.target.getNetToughness(), 4);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 1L);
    }

    @Test
    public void aPluralOracleWordRemainsInTheOriginalWordDomain() {
        Pod pod = pod();
        List<String> legal = new ArrayList<>(CardType.Constant.CREATURE_TYPES);
        assertTrue(legal.contains("Elf"));
        assertTrue(pod.pending.getOracleText().contains("Elves"));
        assertTrue(TextChangeChoices.resolutionOptions(pod.action, legal, false).contains("Elf"),
                "A natively effective Elf-to-Goblin rewrite must not be pruned because the Oracle word is Elves");
    }

    @Test
    public void aPaidNativeLandPluralRewriteChangesUntapProhibition() {
        Pod pod = landPod();
        Card island = pod.actor.getLandsInPlay().stream().filter(card -> card.getType().hasSubtype("Island")).findFirst().orElseThrow();
        island.setTapped(true);
        assertTrue(pod.target.isTapped());
        assertTrue(!island.untap(pod.actor), "Choke must initially prevent the Island from untapping");
        forceTypes(pod, "Island", "Swamp");
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(island.untap(pod.actor), "Rewriting Islands to Swamps must release the Island");
        assertTrue(!pod.target.untap(pod.actor), "The rewritten prohibition must affect the Swamp");
    }

    @Test
    public void aPluralBasicLandWordRemainsInTheOriginalWordDomain() {
        Pod pod = landPod();
        assertTrue(pod.pending.getOracleText().contains("Islands"));
        assertTrue(TextChangeChoices.resolutionOptions(pod.action,
                new ArrayList<>(CardType.getBasicTypes()), false).contains("Island"));
    }

    @Test
    public void aPaidNativeNegatedColorRewriteMakesTheDestroyTargetIllegal() {
        Pod pod = colorPod();
        pod.actor.dangerouslySetController(new PlayerControllerAi(pod.game, pod.actor, pod.actor.getLobbyPlayer()) {
            @Override public byte chooseColor(String prompt, SpellAbility action, ColorSet options) {
                if (action.getApi() == ApiType.ChangeText) {
                    return (options.getColor() & MagicColor.BLACK) != 0 ? MagicColor.BLACK : MagicColor.BLUE;
                }
                return super.chooseColor(prompt, action, options);
            }
        });
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        assertEquals(pod.pending.getChangedTextColorWords().get("Black"), "Blue");
        settle(pod.game);
        assertTrue(pod.target.isInZone(ZoneType.Battlefield), "The rewritten nonblue target must be illegal");
        assertEquals(pod.target.getNetPower(), 2);
    }

    @Test
    public void aNegatedColorWordRemainsInTheOriginalWordDomain() {
        Pod pod = colorPod();
        assertTrue(pod.pending.getOracleText().contains("nonblack"));
        assertTrue(TextChangeChoices.resolutionOptions(pod.action,
                List.of("White", "Blue", "Black", "Red", "Green"), true).contains("Black"));
    }

    @Test
    public void anIrregularPluralUsesItsCurrentWordAfterAnEarlierRewrite() {
        Pod pod = pod();
        forceTypes(pod, "Elf", "Goblin");
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        assertEquals(pod.pending.getChangedTextTypeWords().get("Elf"), "Goblin");
        SpellAbility next = textAction(pod, "Artificial Evolution", pod.pending);
        List<String> choices = TextChangeChoices.resolutionOptions(next,
                new ArrayList<>(CardType.Constant.CREATURE_TYPES), false);
        assertTrue(choices.contains("Goblin"), "Current Goblins must remain visible to the next rewrite");
        assertTrue(!choices.contains("Elf"), "The original Elves must not remain after replacement");
    }

    @Test public void heuristicPilotRewritesTheNegatedDestroySpell() { pilotColor(null); }
    @Test public void hybridPilotRewritesTheNegatedDestroySpell() { pilotColor(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullPilotRewritesTheNegatedDestroySpell() { pilotColor(AIOption.USE_FULL_SIMULATION); }
    @Test public void heuristicPilotUsesThePluralPendingSpellToSaveAnAttacker() { pilotPlural(null); }
    @Test public void hybridPilotUsesThePluralPendingSpellToSaveAnAttacker() { pilotPlural(AIOption.USE_HYBRID_SIMULATION); }
    @Test public void fullPilotUsesThePluralPendingSpellToSaveAnAttacker() { pilotPlural(AIOption.USE_FULL_SIMULATION); }

    private void pilotColor(AIOption mode) {
        Pod pod = colorPod();
        AiController controller = ((PlayerControllerAi) pod.actor.getController()).getAi();
        controller.setUseSimulation(mode);
        List<SpellAbility> choices = controller.chooseSpellAbilityToPlay();
        assertTrue(choices != null && !choices.isEmpty(), "An effective negated-color response must be available");
        SpellAbility selected = choices.stream().filter(action -> action.getHostCard().getName().equals("Sleight of Mind"))
                .findFirst().orElseThrow();
        assertTrue(ComputerUtil.playStack(selected, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.target.isInZone(ZoneType.Battlefield));
        assertEquals(pod.target.getNetPower(), 2);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 1L);
    }

    private void pilotPlural(AIOption mode) {
        Pod pod = pod(true);
        AiController controller = ((PlayerControllerAi) pod.actor.getController()).getAi();
        controller.setUseSimulation(mode);
        List<SpellAbility> choices = controller.chooseSpellAbilityToPlay();
        assertTrue(choices != null && !choices.isEmpty(), "A plural-word response must save the attacker");
        SpellAbility selected = choices.stream().filter(action -> action.getHostCard().getName().equals("Artificial Evolution"))
                .findFirst().orElseThrow();
        assertTrue(ComputerUtil.playStack(selected, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        assertTrue(pod.pending.getChangedTextTypeWords().containsKey("Elf"));
        settle(pod.game);
        assertTrue(pod.target.getNetPower() >= 3);
        Combat combat = pod.game.getCombat();
        pod.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE, pod.actor, false, 2);
        assertTrue(combat.assignCombatDamage(false));
        combat.dealAssignedDamage();
        pod.game.getAction().checkStateEffects(true);
        assertTrue(pod.target.isInZone(ZoneType.Battlefield));
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 1L);
    }

    @Test
    public void ordinaryMainPhaseStillFiltersSummoningSickCreatureValue() {
        Pod pod = pod();
        settle(pod.game);
        assertTrue(pod.game.getStack().isEmpty());
        assertTrue(pod.target.isSick());
        forge.ai.simulation.GameStateEvaluator.Score score =
                forge.ai.simulation.GameSimulator.scoreForDecision(pod.game, pod.actor);
        assertTrue(score.value > score.availableValue);
    }

    @Test
    public void aNewFlashCreatureRemainsExcludedFromResponseReadiness() {
        Pod pod = colorPod();
        Card memnite = addCardToZone("Memnite", pod.actor, ZoneType.Hand);
        memnite.addIntrinsicKeyword("Flash");
        SpellAbility creature = memnite.getFirstSpellAbility();
        creature.setActivatingPlayer(pod.actor);
        forge.ai.simulation.GameStateEvaluator.Score before =
                forge.ai.simulation.GameSimulator.scoreForDecision(pod.game, pod.actor);
        forge.ai.simulation.GameSimulator simulator = new forge.ai.simulation.GameSimulator(
                new forge.ai.simulation.SimulationController(before, 0), pod.game, pod.actor, null);
        forge.ai.simulation.GameStateEvaluator.Score after = simulator.simulateSpellAbility(creature);
        assertTrue(after.value > before.value, "The new creature has long-term value");
        assertTrue(after.availableValue < before.availableValue,
                "A new sick creature must not become an immediate response benefit");
    }

    @Test
    public void aTemporaryPumpOnAnUnthreatenedSickCreatureAddsNoImmediateValue() {
        Pod pod = colorPod();
        Card goblin = pod.actor.getCreaturesInPlay().stream()
                .filter(card -> card.getName().equals("Goblin Piker")).findFirst().orElseThrow();
        assertTrue(goblin.isSick());
        addCardToZone("Forest", pod.actor, ZoneType.Battlefield);
        SpellAbility pump = addCardToZone("Giant Growth", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        pump.setActivatingPlayer(pod.actor);
        pump.getTargets().add(goblin);
        forge.ai.simulation.GameStateEvaluator.Score before =
                forge.ai.simulation.GameSimulator.scoreForDecision(pod.game, pod.actor);
        forge.ai.simulation.GameSimulator simulator = new forge.ai.simulation.GameSimulator(
                new forge.ai.simulation.SimulationController(before, 0), pod.game, pod.actor, null);
        forge.ai.simulation.GameStateEvaluator.Score after = simulator.simulateSpellAbility(pump);
        Card mapped = simulator.getGameCopier().find(goblin);
        assertTrue(mapped.isSick());
        assertEquals(mapped.getNetPower(), 5);
        assertTrue(after.value > before.value, "The temporary pump is represented in the board score");
        assertTrue(after.availableValue < before.availableValue,
                "An unthreatened sick creature cannot use the pump now: " + before + " -> " + after);
    }

    private void forceTypes(Pod pod, String from, String to) {
        pod.actor.dangerouslySetController(new PlayerControllerAi(pod.game, pod.actor, pod.actor.getLobbyPlayer()) {
            @Override public String chooseSomeType(String kind, SpellAbility action, Collection<String> options, boolean optional) {
                if (action.getApi() == ApiType.ChangeText) {
                    String result = options.contains(from) ? from : to;
                    assertTrue(options.contains(result));
                    return result;
                }
                return super.chooseSomeType(kind, action, options, optional);
            }
        });
    }

    private SpellAbility textAction(Pod pod, String name, Card target) {
        SpellAbility action = addCardToZone(name, pod.actor, ZoneType.Hand).getFirstSpellAbility();
        action.setActivatingPlayer(pod.actor);
        action.getTargets().add(target);
        assertTrue(action.isTargetNumberValid());
        return action;
    }

    private Pod landPod() {
        Pod base = pod();
        Card choke = addCardToZone("Choke", base.game.getPlayers().get(1), ZoneType.Battlefield);
        Card swamp = addCardToZone("Swamp", base.actor, ZoneType.Battlefield);
        swamp.setTapped(true);
        SpellAbility action = textAction(base, "Magical Hack", choke);
        base.game.getAction().checkStateEffects(true);
        base.game.copyLastState();
        return new Pod(base.game, base.actor, swamp, choke, action);
    }

    private Pod colorPod() {
        Pod base = pod();
        Player enemy = base.game.getPlayers().get(1);
        addCardToZone("Swamp", enemy, ZoneType.Battlefield);
        addCardToZone("Swamp", enemy, ZoneType.Battlefield);
        SpellAbility destroy = addCardToZone("Doom Blade", enemy, ZoneType.Hand).getFirstSpellAbility();
        destroy.setActivatingPlayer(enemy);
        destroy.getTargets().add(base.target);
        assertTrue(ComputerUtil.playStack(destroy, enemy, base.game));
        Card pending = base.game.getStack().peekAbility().getHostCard();
        SpellAbility action = textAction(base, "Sleight of Mind", pending);
        base.game.copyLastState();
        return new Pod(base.game, base.actor, base.target, pending, action);
    }

    private Pod pod() { return pod(false); }

    private Pod pod(boolean combatWindow) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("plural" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Plural text choices"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player enemy = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 8; index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        addCardToZone("Island", actor, ZoneType.Battlefield);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        addCardToZone("Forest", enemy, ZoneType.Battlefield);
        Card target = addCardToZone("Coral Merfolk", actor, ZoneType.Battlefield);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Goblin Piker", actor, ZoneType.Battlefield);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        if (combatWindow) {
            Card blocker = addCardToZone("Memnite", enemy, ZoneType.Battlefield);
            // Shroud and blue protection isolate the pending-spell rewrite.
            for (Card creature : game.getCardsIn(ZoneType.Battlefield)) {
                if (creature.isCreature() && creature != target) creature.addIntrinsicKeyword("Shroud");
            }
            target.addIntrinsicKeyword("Protection:Blue");
            target.setSickness(false);
            target.setTapped(true);
            Combat combat = new Combat(actor);
            combat.addAttacker(target, enemy);
            combat.addBlocker(target, blocker);
            combat.getBandOfAttacker(target).setBlocked(true);
            target.addBlockedByThisTurn(CardCopyService.getLKICopy(blocker));
            blocker.addBlockedThisTurn(CardCopyService.getLKICopy(target));
            combat.orderBlockersForDamageAssignment(target, combat.getBlockers(target));
            combat.orderAttackersForDamageAssignment(blocker);
            game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, actor, 2);
            game.getPhaseHandler().setCombat(combat);
        }

        SpellAbility pump = addCardToZone("Wirewood Pride", enemy, ZoneType.Hand).getFirstSpellAbility();
        pump.setActivatingPlayer(enemy);
        pump.getTargets().add(target);
        assertTrue(ComputerUtil.playStack(pump, enemy, game));
        Card pending = game.getStack().peekAbility().getHostCard();
        Card evolution = addCardToZone("Artificial Evolution", actor, ZoneType.Hand);
        SpellAbility action = evolution.getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        action.getTargets().add(pending);
        assertTrue(action.isTargetNumberValid());
        game.getAction().checkStateEffects(true);
        game.copyLastState();
        return new Pod(game, actor, target, pending, action);
    }

    private void settle(Game game) {
        for (int step = 0; step < 20; step++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) {
                return;
            }
            game.getStack().resolveStack();
        }
        throw new AssertionError("Native plural text stack did not settle");
    }

    private record Pod(Game game, Player actor, Card target, Card pending, SpellAbility action) { }
}
