package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameSnapshot;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.ability.AbilityKey;
import forge.game.card.CounterEnumType;
import forge.game.card.CounterType;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CommanderRinoaReturnTest extends AITest {
    @Test
    public void decliningLeavesTheOncePerTurnReturnAvailable() {
        verifyLimit(false, false);
    }

    @Test
    public void exilingTheDeadCreatureLeavesTheOncePerTurnReturnAvailable() {
        verifyLimit(true, false);
    }

    @Test
    public void anExiledCardReturnedToTheGraveyardIsNotTheOriginalDeadObject() {
        verifyLimit(true, true);
    }

    @Test
    public void aSimultaneousDeathReturnsOnlyOneAttackerStillInTheGraveyard() {
        Pod pod = newPod();
        Card first = attacker(pod, "Grizzly Bears");
        Card second = attacker(pod, "Runeclaw Bear");
        // This nonattacking creature must not be offered by the grouped trigger.
        Card other = addCardToZone("Balduvian Bears", pod.actor, ZoneType.Battlefield);
        other.setCounters(CounterEnumType.M1M1, 1);
        addCardToZone("Mountain", pod.actor, ZoneType.Battlefield);
        addCardToZone("Mountain", pod.actor, ZoneType.Battlefield);
        SpellAbility pyroclasm = addCardToZone("Pyroclasm", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        pyroclasm.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.playStack(pyroclasm, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        pod.game.getAction().checkStateEffects(true);
        queueTriggers(pod.game);
        assertEquals(pod.game.getStack().size(), 1);
        exileInResponse(pod, first, false);
        assertTrue(pod.game.getCardState(first).isInZone(ZoneType.Exile));
        acceptReturns(pod.game, pod.rinoa);
        settle(pod.game);
        assertTrue(pod.game.getCardState(first).isInZone(ZoneType.Exile));
        assertReturned(pod.game, second, pod.actor);
        assertTrue(pod.game.getCardState(other).isInZone(ZoneType.Graveyard));
        Card later = attacker(pod, "Ashcoat Bear");
        queueDeath(pod, later);
        settle(pod.game);
        assertTrue(pod.game.getCardState(later).isInZone(ZoneType.Graveyard));
    }

    @Test
    public void aQueuedReturnKeepsItsObjectsInSnapshotsAndAfterRestore() {
        Pod pod = newPod();
        Card bear = attacker(pod, "Grizzly Bears");
        queueDeath(pod, bear);
        assertTrue(pod.game.getCardState(bear).isInZone(ZoneType.Graveyard));
        GameSnapshot saved = new GameSnapshot(pod.game);
        Game checkpoint = saved.makeCopy();
        Game copy = new GameSnapshot(pod.game).makeCopy();
        assertTrue(checkpoint.getCardState(bear).isInZone(ZoneType.Graveyard));
        acceptReturns(copy, copy.getCardState(pod.rinoa));
        settle(copy);
        assertReturned(copy, bear, copy.getPlayers().get(0));
        assertTrue(pod.game.getCardState(bear).isInZone(ZoneType.Graveyard));

        acceptReturns(pod.game, pod.rinoa);
        settle(pod.game);
        assertReturned(pod.game, bear, pod.actor);
        saved.restoreGameState(pod.game);
        assertTrue(pod.game.getCardState(bear).isInZone(ZoneType.Graveyard));
        SpellAbility restoredTrigger = pod.game.getStack().peekAbility();
        assertTrue(restoredTrigger.getTrigger().checkResolvedLimit(pod.actor),
                "The restored trigger must have an unused return allowance");
        Object restoredCards = restoredTrigger.getTriggeringObject(AbilityKey.fromString("NewCards"));
        assertTrue(restoredCards instanceof Iterable<?>, "The restored trigger must keep its destination objects");
        for (Object entry : (Iterable<?>) restoredCards) {
            Card previous = (Card) entry;
            Card current = pod.game.getCardState(previous);
            assertTrue(previous.equalsWithGameTimestamp(current),
                    "Restored destination identity changed: expected=" + previous.getGameTimestamp()
                            + " current=" + current.getGameTimestamp());
        }
        acceptReturns(pod.game, pod.game.getCardState(pod.rinoa));
        settle(pod.game);
        assertReturned(pod.game, bear, pod.game.getPlayers().get(0));
    }

    @Test
    public void aQueuedReturnKeepsItsObjectsInAnAiCopy() {
        Pod pod = newPod();
        Card bear = attacker(pod, "Grizzly Bears");
        queueDeath(pod, bear);
        Game copy = new GameCopier(pod.game).withStack(true).makeCopy();
        acceptReturns(copy, copy.getCardState(pod.rinoa));
        settle(copy);
        assertReturned(copy, bear, copy.getPlayers().get(0));
        assertTrue(pod.game.getCardState(bear).isInZone(ZoneType.Graveyard));
    }

    @Test
    public void aSnapshotDoesNotRebindADeadCardThatLeftAndReturned() {
        verifyHistoricalReturnInCopy(true);
    }

    @Test
    public void anAiCopyDoesNotRebindADeadCardThatLeftAndReturned() {
        verifyHistoricalReturnInCopy(false);
    }

    private void verifyHistoricalReturnInCopy(boolean snapshot) {
        Pod pod = newPod();
        Card first = attacker(pod, "Grizzly Bears");
        Card second = attacker(pod, "Runeclaw Bear");
        queueDeath(pod, first);
        exileInResponse(pod, first, true);
        Game copy = snapshot ? new GameSnapshot(pod.game).makeCopy()
                : new GameCopier(pod.game).withStack(true).makeCopy();
        Card copiedRinoa = copy.getCardState(pod.rinoa);
        acceptReturns(copy, copiedRinoa);
        settle(copy);
        assertTrue(copy.getCardState(first).isInZone(ZoneType.Graveyard),
                "The copied trigger must not follow the card through another zone change");
        Pod copiedPod = new Pod(copy, copy.getPlayers().get(0), copy.getPlayers().get(1), copiedRinoa);
        queueDeath(copiedPod, copy.getCardState(second));
        settle(copy);
        assertReturned(copy, second, copy.getPlayers().get(0));
        assertTrue(pod.game.getCardState(first).isInZone(ZoneType.Graveyard));
        assertTrue(pod.game.getCardState(second).isInZone(ZoneType.Battlefield));
    }

    @Test
    public void copiesAndRestoresPreserveAnAlreadyUsedReturnAllowance() {
        Pod pod = newPod();
        Card first = attacker(pod, "Grizzly Bears");
        Card second = attacker(pod, "Runeclaw Bear");
        acceptReturns(pod.game, pod.rinoa);
        queueDeath(pod, first);
        settle(pod.game);
        assertReturned(pod.game, first, pod.actor);
        GameSnapshot saved = new GameSnapshot(pod.game);
        Game checkpoint = saved.makeCopy();
        Game copy = new GameSnapshot(pod.game).makeCopy();
        Card copiedRinoa = copy.getCardState(pod.rinoa);
        acceptReturns(copy, copiedRinoa);
        Pod copiedPod = new Pod(copy, copy.getPlayers().get(0), copy.getPlayers().get(1), copiedRinoa);
        queueDeath(copiedPod, copy.getCardState(second));
        settle(copy);
        assertTrue(copy.getCardState(second).isInZone(ZoneType.Graveyard));
        assertTrue(checkpoint.getCardState(second).isInZone(ZoneType.Battlefield));

        // Change usage after the checkpoint so restore must reinstate the saved use.
        pod.rinoa.resetAbilityResolvedThisTurn();
        saved.restoreGameState(pod.game);
        acceptReturns(pod.game, pod.game.getCardState(pod.rinoa));
        queueDeath(pod, pod.game.getCardState(second));
        settle(pod.game);
        assertTrue(pod.game.getCardState(second).isInZone(ZoneType.Graveyard));
    }

    private void verifyLimit(boolean exileFirst, boolean returnToGraveyard) {
        Pod pod = newPod();
        int[] prompts = {0};
        pod.actor.dangerouslySetController(new PlayerControllerAi(pod.game, pod.actor, pod.actor.getLobbyPlayer()) {
            @Override
            public boolean confirmTrigger(WrappedAbility ability) {
                if (ability.getHostCard().equals(pod.rinoa)) {
                    return ++prompts[0] > 1 || exileFirst;
                }
                return super.confirmTrigger(ability);
            }
        });
        Card first = attacker(pod, "Grizzly Bears");
        Card second = attacker(pod, "Runeclaw Bear");
        Card third = attacker(pod, "Balduvian Bears");
        queueDeath(pod, first);
        assertEquals(pod.game.getStack().size(), 1);
        if (exileFirst) {
            exileInResponse(pod, first, returnToGraveyard);
        }
        settle(pod.game);
        ZoneType expected = exileFirst && !returnToGraveyard ? ZoneType.Exile : ZoneType.Graveyard;
        assertTrue(pod.game.getCardState(first).isInZone(expected),
                "A declined or impossible return must not move the first creature");
        int promptsAfterFirst = prompts[0];
        queueDeath(pod, second);
        settle(pod.game);
        assertEquals(prompts[0], promptsAfterFirst + 1);
        assertReturned(pod.game, second, pod.actor);
        queueDeath(pod, third);
        settle(pod.game);
        assertEquals(prompts[0], promptsAfterFirst + 1);
        assertTrue(pod.game.getCardState(third).isInZone(ZoneType.Graveyard));
    }

    private Pod newPod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Rinoa"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_BLOCKERS, actor, 2);
        Card rinoa = addCardToZone("Rinoa, Angel Wing", actor, ZoneType.Battlefield);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Swamp", actor, ZoneType.Battlefield);
        }
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.getPhaseHandler().setCombat(new Combat(actor));
        return new Pod(game, actor, opponent, rinoa);
    }

    private Card attacker(Pod pod, String name) {
        Card bear = addCardToZone(name, pod.actor, ZoneType.Battlefield);
        bear.setCounters(CounterEnumType.M1M1, 1);
        pod.game.getCombat().addAttacker(bear, pod.opponent);
        return bear;
    }

    private void exileInResponse(Pod pod, Card bear, boolean returnToGraveyard) {
        Player thirdPlayer = pod.game.getPlayers().get(2);
        Card vacuum = addCardToZone("Ghost Vacuum", thirdPlayer, ZoneType.Battlefield);
        SpellAbility exile = vacuum.getSpellAbilities().stream().filter(SpellAbility::usesTargeting)
                .findFirst().orElseThrow();
        exile.setActivatingPlayer(thirdPlayer);
        exile.getTargets().add(pod.game.getCardState(bear));
        assertTrue(ComputerUtil.playStack(exile, thirdPlayer, pod.game));
        pod.game.getStack().resolveStack();
        assertTrue(pod.game.getCardState(bear).isInZone(ZoneType.Exile));
        if (returnToGraveyard) {
            addCardToZone("Plains", thirdPlayer, ZoneType.Battlefield);
            SpellAbility pull = addCardToZone("Pull from Eternity", thirdPlayer, ZoneType.Hand).getFirstSpellAbility();
            pull.setActivatingPlayer(thirdPlayer);
            pull.getTargets().add(pod.game.getCardState(bear));
            assertTrue(ComputerUtil.playStack(pull, thirdPlayer, pod.game));
            pod.game.getStack().resolveStack();
            assertTrue(pod.game.getCardState(bear).isInZone(ZoneType.Graveyard));
        }
    }

    private void queueDeath(Pod pod, Card target) {
        SpellAbility cut = addCardToZone("Cut Down", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        cut.setActivatingPlayer(pod.actor);
        cut.getTargets().add(target);
        assertTrue(ComputerUtil.playStack(cut, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queueTriggers(pod.game);
    }

    private void queueTriggers(Game game) {
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private void acceptReturns(Game game, Card rinoa) {
        Player actor = rinoa.getController();
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public boolean confirmTrigger(WrappedAbility ability) {
                return ability.getHostCard().equals(rinoa) || super.confirmTrigger(ability);
            }
        });
    }

    private void assertReturned(Game game, Card previous, Player owner) {
        Card current = game.getCardState(previous);
        assertTrue(current.isInZone(ZoneType.Battlefield));
        assertEquals(current.getController(), owner);
        assertTrue(current.isTapped());
        assertEquals(current.getCounters(CounterType.getType("Flying")), 1);
    }

    private void settle(Game game) {
        for (int index = 0; index < 12 && !game.getStack().isEmpty(); index++) {
            game.getStack().resolveStack();
        }
        assertTrue(game.getStack().isEmpty());
    }

    private record Pod(Game game, Player actor, Player opponent, Card rinoa) {
    }
}
