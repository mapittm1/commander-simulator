package forge.ai;

import forge.deck.Deck;
import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

/** Supplied declared combat/history; native tap payments, triggers and combat damage. */
public class CommanderSwitchBlockAiTest extends AITest {
    @Test
    public void generalNativeSwapPreventsLethalTrample() {
        Pod pod = general(false);
        pod.action.getTargets().add(pod.first);
        pod.action.getTargets().add(pod.second);
        assertTrue(pod.action.isTargetNumberValid());
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.host.isTapped());
        assertTrue(pod.combat.isBlocking(pod.strongBlocker, pod.first));
        assertTrue(pod.combat.isBlocking(pod.weakBlocker, pod.second));
        damage(pod);
        assertEquals(pod.actor.getLife(), 4);
        assertTrue(pod.actor.isInGame());
    }

    @Test
    public void generalAiAdmitsASwapThatPreventsLethal() {
        Pod pod = general(false);
        var random = MyRandom.getRandom();
        assertEquals(ai(pod).canPlaySa(pod.action), AiPlayDecision.WillPlay);
        assertTrue(pod.action.isTargetNumberValid());
        assertEquals(pod.action.getTargets().getTargetCards().size(), 2);
        untouched(pod, 4);
        assertSame(MyRandom.getRandom(), random);
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        settle(pod.game);
        damage(pod);
        assertEquals(pod.actor.getLife(), 4);
    }

    @Test
    public void generalAiDeclinesAHarmfulSwap() {
        Pod pod = general(true);
        assertFalse(ai(pod).canPlaySa(pod.action) == AiPlayDecision.WillPlay);
        untouched(pod, 4);
    }

    @Test
    public void sorrowNativeSwapPaysItsTapTriggerBeforeCombat() {
        Pod pod = sorrow(10);
        pod.action.getTargets().add(pod.strongBlocker);
        pod.action.getTargets().add(pod.weakBlocker);
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.host.isTapped());
        assertEquals(pod.actor.getLife(), 8);
        assertEquals(pod.first.getDamage(), 2);
        assertEquals(pod.second.getDamage(), 2);
        assertTrue(pod.combat.isBlocking(pod.weakBlocker, pod.first));
        assertTrue(pod.combat.isBlocking(pod.strongBlocker, pod.second));
        Player defender = (Player) pod.combat.getDefenderByAttacker(pod.first);
        damage(pod);
        assertTrue(defender.getLife() <= 0);
        assertFalse(defender.isInGame());
    }

    @Test
    public void sorrowAiAdmitsAWinningSwapWithItsRealCost() {
        Pod pod = sorrow(10);
        var random = MyRandom.getRandom();
        assertEquals(ai(pod).canPlaySa(pod.action), AiPlayDecision.WillPlay);
        assertTrue(pod.action.isTargetNumberValid());
        untouched(pod, 10);
        assertEquals(pod.first.getDamage(), 0);
        assertEquals(pod.second.getDamage(), 0);
        assertSame(MyRandom.getRandom(), random);
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        settle(pod.game);
        assertEquals(pod.actor.getLife(), 8);
        Player defender = (Player) pod.combat.getDefenderByAttacker(pod.first);
        damage(pod);
        assertTrue(defender.getLife() <= 0);
        assertFalse(defender.isInGame());
    }

    @Test
    public void sorrowAiDeclinesASelfLethalTapTrigger() {
        Pod pod = sorrow(2);
        assertFalse(ai(pod).canPlaySa(pod.action) == AiPlayDecision.WillPlay);
        untouched(pod, 2);
    }

    @Test
    public void generalHeuristicPickerSelectsTheLifeSavingSwap() { pick(false, null); }
    @Test
    public void generalHybridPickerSelectsTheLifeSavingSwap() { pick(false, AIOption.USE_HYBRID_SIMULATION); }
    @Test
    public void generalFullPickerSelectsTheLifeSavingSwap() { pick(false, AIOption.USE_FULL_SIMULATION); }
    @Test
    public void sorrowHeuristicPickerSelectsTheEliminatingSwap() { pick(true, null); }
    @Test
    public void sorrowHybridPickerSelectsTheEliminatingSwap() { pick(true, AIOption.USE_HYBRID_SIMULATION); }
    @Test
    public void sorrowFullPickerSelectsTheEliminatingSwap() { pick(true, AIOption.USE_FULL_SIMULATION); }

    private void pick(boolean land, AIOption mode) {
        Pod pod = land ? sorrow(10) : general(false);
        ai(pod).setUseSimulation(mode);
        var random = MyRandom.getRandom();
        List<SpellAbility> choices = ai(pod).chooseSpellAbilityToPlay();
        assertTrue(choices != null && !choices.isEmpty(), "The picker must offer a useful combat action in mode " + mode);
        SpellAbility selected = choices.stream().filter(ability -> ability.getApi() == ApiType.SwitchBlock
                && ability.getHostCard().getId() == pod.host.getId()).findFirst().orElseThrow();
        assertSame(selected.getActivatingPlayer(), pod.actor);
        assertTrue(selected.isTargetNumberValid());
        untouched(pod, land ? 10 : 4);
        assertSame(MyRandom.getRandom(), random);
        assertTrue(ComputerUtil.playStack(selected, pod.actor, pod.game));
        settle(pod.game);
        Player defender = (Player) pod.combat.getDefenderByAttacker(pod.first);
        damage(pod);
        if (land) {
            assertEquals(pod.actor.getLife(), 8);
            assertFalse(defender.isInGame());
        } else {
            assertEquals(pod.actor.getLife(), 4);
            assertTrue(pod.actor.isInGame());
        }
    }

    @Test
    public void copiedBlockRecordsAreLastKnownAndIndependent() {
        Pod pod = sorrow(10);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        Card first = copier.find(pod.first);
        Card blocker = copier.find(pod.strongBlocker);
        assertEquals(first.getBlockedByThisTurn().size(), 1);
        assertEquals(blocker.getBlockedThisTurn().size(), 1);
        Card historical = first.getBlockedByThisTurn().get(0);
        assertTrue(historical.isLKI());
        assertSame(historical.getGame(), copy);
        assertEquals(historical.getId(), pod.strongBlocker.getId());
        assertSame(historical.getController(), copier.find(pod.strongBlocker.getController()));
        first.clearBlockedByThisTurn();
        blocker.clearBlockedThisTurn();
        assertEquals(pod.first.getBlockedByThisTurn().size(), 1);
        assertEquals(pod.strongBlocker.getBlockedThisTurn().size(), 1);
    }

    @Test
    public void aCopiedPendingSwapRetainsBlockHistoryAndTapTrigger() {
        Pod pod = sorrow(10);
        pod.action.getTargets().add(pod.strongBlocker);
        pod.action.getTargets().add(pod.weakBlocker);
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 2);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        settle(copy);
        assertEquals(copier.find(pod.actor).getLife(), 8);
        assertEquals(pod.actor.getLife(), 10);
        assertTrue(copy.getCombat().isBlocking(copier.find(pod.weakBlocker), copier.find(pod.first)));
        assertTrue(pod.combat.isBlocking(pod.strongBlocker, pod.first));
        assertEquals(pod.game.getStack().size(), 2);
        settle(pod.game);
        assertEquals(pod.actor.getLife(), 8);
        assertTrue(pod.combat.isBlocking(pod.weakBlocker, pod.first));
    }

    private void queue(Game game) {
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    @Test
    public void generalHeuristicPickerDeclinesTheHarmfulSwap() { noPick(false, null); }
    @Test
    public void generalHybridPickerDeclinesTheHarmfulSwap() { noPick(false, AIOption.USE_HYBRID_SIMULATION); }
    @Test
    public void generalFullPickerDeclinesTheHarmfulSwap() { noPick(false, AIOption.USE_FULL_SIMULATION); }
    @Test
    public void sorrowHeuristicPickerDeclinesSelfLethalActivation() { noPick(true, null); }
    @Test
    public void sorrowHybridPickerDeclinesSelfLethalActivation() { noPick(true, AIOption.USE_HYBRID_SIMULATION); }
    @Test
    public void sorrowFullPickerDeclinesSelfLethalActivation() { noPick(true, AIOption.USE_FULL_SIMULATION); }

    private void noPick(boolean land, AIOption mode) {
        Pod pod = land ? sorrow(2) : general(true);
        ai(pod).setUseSimulation(mode);
        List<SpellAbility> choices = ai(pod).chooseSpellAbilityToPlay();
        assertTrue(choices == null || choices.stream().noneMatch(ability -> ability.getApi() == ApiType.SwitchBlock
                && ability.getHostCard().getId() == pod.host.getId()));
        untouched(pod, land ? 2 : 4);
    }

    @Test
    public void nativeSwitchCannotMoveAGroundBlockerOntoAFlyer() {
        Pod pod = illegalCrossBlock();
        pod.action.getTargets().add(pod.first);
        pod.action.getTargets().add(pod.second);
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.host.isTapped());
        assertTrue(pod.combat.isBlocking(pod.strongBlocker, pod.first));
        assertTrue(pod.combat.isBlocking(pod.weakBlocker, pod.second));
    }

    @Test
    public void aiDeclinesAnIllegalCrossBlockInsteadOfPayingTheTapCost() {
        Pod pod = illegalCrossBlock();
        assertFalse(ai(pod).canPlaySa(pod.action) == AiPlayDecision.WillPlay);
        untouched(pod, 4);
        assertTrue(pod.combat.isBlocking(pod.strongBlocker, pod.first));
        assertTrue(pod.combat.isBlocking(pod.weakBlocker, pod.second));
    }

    private Pod illegalCrossBlock() {
        Game game = game();
        Player attacker = game.getPlayers().get(0);
        Player actor = game.getPlayers().get(1);
        actor.setLife(4, null);
        Card first = addCardToZone("Air Elemental", attacker, ZoneType.Battlefield);
        Card second = addCardToZone("Grizzly Bears", attacker, ZoneType.Battlefield);
        Card strong = addCardToZone("Serra Angel", actor, ZoneType.Battlefield);
        Card weak = addCardToZone("Colossal Dreadmaw", actor, ZoneType.Battlefield);
        Card host = addCardToZone("General Jarkeld", actor, ZoneType.Battlefield);
        host.setSickness(false);
        Combat combat = new Combat(attacker);
        combat.addAttacker(first, actor);
        combat.addAttacker(second, actor);
        block(combat, first, strong);
        block(combat, second, weak);
        assertFalse(CombatUtil.canBlock(first, weak));
        return finish(game, actor, host, first, second, strong, weak, combat);
    }

    private AiController ai(Pod pod) {
        return ((PlayerControllerAi) pod.actor.getController()).getAi();
    }

    private void untouched(Pod pod, int life) {
        assertFalse(pod.host.isTapped());
        assertEquals(pod.actor.getLife(), life);
        assertTrue(pod.game.getStack().isEmpty());
        assertEquals(pod.first.getDamage(), 0);
        assertEquals(pod.second.getDamage(), 0);
    }

    private Pod general(boolean safe) {
        Game game = game();
        Player attacker = game.getPlayers().get(0);
        Player actor = game.getPlayers().get(1);
        actor.setLife(4, null);
        Card first = addCardToZone("Colossal Dreadmaw", attacker, ZoneType.Battlefield);
        Card second = addCardToZone("Grizzly Bears", attacker, ZoneType.Battlefield);
        Card strong = addCardToZone("Colossal Dreadmaw", actor, ZoneType.Battlefield);
        Card weak = addCardToZone("Memnite", actor, ZoneType.Battlefield);
        Card host = addCardToZone("General Jarkeld", actor, ZoneType.Battlefield);
        host.setSickness(false);
        Combat combat = new Combat(attacker);
        combat.addAttacker(first, actor);
        combat.addAttacker(second, actor);
        block(combat, first, safe ? strong : weak);
        block(combat, second, safe ? weak : strong);
        return finish(game, actor, host, first, second, strong, weak, combat);
    }

    private Pod sorrow(int life) {
        Game game = game();
        Player actor = game.getPlayers().get(0);
        Player defender = game.getPlayers().get(1);
        actor.setLife(life, null);
        defender.setLife(4, null);
        Card first = addCardToZone("Colossal Dreadmaw", actor, ZoneType.Battlefield);
        Card second = addCardToZone("Verdant Force", actor, ZoneType.Battlefield);
        Card strong = addCardToZone("Wall of Stone", defender, ZoneType.Battlefield);
        Card weak = addCardToZone("Grizzly Bears", defender, ZoneType.Battlefield);
        Card host = addCardToZone("Sorrow's Path", actor, ZoneType.Battlefield);
        Combat combat = new Combat(actor);
        combat.addAttacker(first, defender);
        combat.addAttacker(second, defender);
        block(combat, first, strong);
        block(combat, second, weak);
        return finish(game, actor, host, first, second, strong, weak, combat);
    }

    private Pod finish(Game game, Player actor, Card host, Card first, Card second,
            Card strong, Card weak, Combat combat) {
        game.getPhaseHandler().setCombat(combat);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        SpellAbility action = host.getSpellAbilities().stream()
                .filter(ability -> ability.getApi() == ApiType.SwitchBlock).findFirst().orElseThrow();
        action.setActivatingPlayer(actor);
        assertTrue(action.canPlay());
        assertTrue(ComputerUtilCost.canPayCost(action, actor, false));
        return new Pod(game, actor, host, first, second, strong, weak, combat, action);
    }

    private void block(Combat combat, Card attacker, Card blocker) {
        attacker.setSickness(false);
        attacker.setTapped(true); // Supplied legal declaration of these nonvigilant attackers.
        assertTrue(CombatUtil.canBlock(attacker, blocker));
        combat.addBlocker(attacker, blocker);
        combat.getBandOfAttacker(attacker).setBlocked(true);
        blocker.addBlockedThisTurn(CardCopyService.getLKICopy(attacker));
        attacker.addBlockedByThisTurn(CardCopyService.getLKICopy(blocker));
        combat.orderBlockersForDamageAssignment(attacker, combat.getBlockers(attacker));
        combat.orderAttackersForDamageAssignment(blocker);
    }

    private Game game() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Switch blockers AI"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, game.getPlayers().get(0), 2);
        return game;
    }

    private void damage(Pod pod) {
        pod.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE, pod.game.getPlayers().get(0), false, 2);
        assertSame(pod.game.getCombat(), pod.combat);
        assertTrue(pod.combat.assignCombatDamage(false));
        pod.combat.dealAssignedDamage();
        pod.game.getAction().checkStateEffects(true);
    }

    private void settle(Game game) {
        for (int step = 0; step < 20; step++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        throw new AssertionError("Native stack did not settle");
    }

    private record Pod(Game game, Player actor, Card host, Card first, Card second,
            Card strongBlocker, Card weakBlocker, Combat combat, SpellAbility action) {}
}
