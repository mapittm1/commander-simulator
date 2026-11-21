package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
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

/** Controlled targets and optional decisions; costs and zone changes are native. */
public class CommanderTargetedReturnTest extends AITest {
    @Test
    public void aProhibitedTargetedReturnPreservesTheAllowance() {
        verifyReturn("Grafdigger's Cage", ZoneType.Graveyard);
    }

    @Test
    public void aReplacedTargetedReturnUsesTheAllowance() {
        verifyReturn("Containment Priest", ZoneType.Exile);
    }

    @Test
    public void anOrdinaryTargetedReturnUsesTheAllowance() {
        verifyReturn(null, ZoneType.Battlefield);
    }

    @Test
    public void kunorosPreservesTheTargetedReturn() {
        verifyReturn("Kunoros, Hound of Athreos", ZoneType.Graveyard);
    }

    @Test
    public void soullessJailerPreservesTheTargetedReturn() {
        verifyReturn("Soulless Jailer", ZoneType.Graveyard);
    }

    @Test
    public void runestonePreservesTheTargetedReturn() {
        verifyReturn("Weathered Runestone", ZoneType.Graveyard);
    }

    @Test
    public void decliningTheTargetedReturnPreservesTheAllowance() {
        Pod pod = newPod();
        pod.target[0] = addCardToZone("Grizzly Bears", pod.actor, ZoneType.Graveyard);
        pod.decline[0] = true;
        sacrifice(pod);
        assertTrue(pod.game.getCardState(pod.target[0]).isInZone(ZoneType.Graveyard));
        pod.decline[0] = false;
        sacrifice(pod);
        assertTrue(pod.game.getCardState(pod.target[0]).isInZone(ZoneType.Battlefield));
    }

    @Test
    public void counteringTheTriggerPreservesTheAllowance() {
        Pod pod = newPod();
        pod.target[0] = addCardToZone("Grizzly Bears", pod.actor, ZoneType.Graveyard);
        sacrificeToStack(pod);
        assertEquals(pod.game.getStack().size(), 1);
        Player responder = pod.game.getPlayers().get(2);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Island", responder, ZoneType.Battlefield);
        }
        SpellAbility counter = addCardToZone("Disallow", responder, ZoneType.Hand).getFirstSpellAbility();
        counter.setActivatingPlayer(responder);
        counter.getTargets().add(pod.game.getStack().peekAbility());
        assertTrue(ComputerUtil.playStack(counter, responder, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.target[0]).isInZone(ZoneType.Graveyard));
        assertEquals(pod.prompts[0], 0);
        sacrifice(pod);
        assertTrue(pod.game.getCardState(pod.target[0]).isInZone(ZoneType.Battlefield));
    }

    @Test
    public void exilingTheOnlyTargetPreservesTheAllowance() {
        verifyInvalidTarget(false);
    }

    @Test
    public void aTargetThatLeavesAndReturnsDoesNotResolveAsTheOldObject() {
        verifyInvalidTarget(true);
    }

    private void verifyInvalidTarget(boolean roundTrip) {
        Pod pod = newPod();
        Card oldTarget = addCardToZone("Grizzly Bears", pod.actor, ZoneType.Graveyard);
        pod.target[0] = oldTarget;
        sacrificeToStack(pod);
        assertEquals(pod.game.getStack().size(), 1);
        Player responder = pod.game.getPlayers().get(2);
        addCardToZone("Forest", responder, ZoneType.Battlefield);
        Card vacuum = addCardToZone("Ghost Vacuum", responder, ZoneType.Battlefield);
        SpellAbility exile = vacuum.getSpellAbilities().stream().filter(SpellAbility::usesTargeting)
                .findFirst().orElseThrow();
        exile.setActivatingPlayer(responder);
        exile.getTargets().add(oldTarget);
        assertTrue(ComputerUtil.playStack(exile, responder, pod.game));
        pod.game.getStack().resolveStack();
        Card current = pod.game.getCardState(oldTarget);
        assertTrue(current.isInZone(ZoneType.Exile));
        if (roundTrip) {
            SpellAbility pull = addCardToZone("Pull from Eternity", pod.actor, ZoneType.Hand)
                    .getFirstSpellAbility();
            pull.setActivatingPlayer(pod.actor);
            pull.getTargets().add(current);
            assertTrue(ComputerUtil.playStack(pull, pod.actor, pod.game));
            pod.game.getStack().resolveStack();
            current = pod.game.getCardState(oldTarget);
            assertTrue(current.isInZone(ZoneType.Graveyard));
            assertTrue(!current.equalsWithGameTimestamp(oldTarget));
        }
        settle(pod.game);
        assertTrue(pod.game.getCardState(oldTarget).isInZone(roundTrip ? ZoneType.Graveyard : ZoneType.Exile));
        assertEquals(pod.prompts[0], 0);
        pod.target[0] = roundTrip ? pod.game.getCardState(oldTarget)
                : addCardToZone("Runeclaw Bear", pod.actor, ZoneType.Graveyard);
        sacrifice(pod);
        assertTrue(pod.game.getCardState(pod.target[0]).isInZone(ZoneType.Battlefield));
    }

    @Test
    public void krileReturnsToHandThroughGrafdiggersCageAndUsesItsAllowance() {
        Pod pod = newPod("Krile Baldesion");
        addCardToZone("Grafdigger's Cage", pod.game.getPlayers().get(2), ZoneType.Battlefield);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Plains", pod.actor, ZoneType.Battlefield);
        }
        Card first = addCardToZone("Grizzly Bears", pod.actor, ZoneType.Graveyard);
        pod.target[0] = first;
        castDisenchant(pod);
        assertTrue(pod.game.getCardState(first).isInZone(ZoneType.Hand));
        Card next = addCardToZone("Runeclaw Bear", pod.actor, ZoneType.Graveyard);
        pod.target[0] = next;
        castDisenchant(pod);
        assertTrue(pod.game.getCardState(next).isInZone(ZoneType.Graveyard));
    }

    private void castDisenchant(Pod pod) {
        Card stone = addCardToZone("Mind Stone", pod.actor, ZoneType.Battlefield);
        SpellAbility spell = addCardToZone("Disenchant", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        spell.getTargets().add(stone);
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        queueTriggers(pod.game);
        settle(pod.game);
        assertTrue(pod.game.getCardState(stone).isInZone(ZoneType.Graveyard));
    }

    @Test
    public void pucaReturnsToHandThroughGrafdiggersCageAndUsesItsAllowance() {
        Pod pod = newPod("Puca's Covenant");
        addCardToZone("Grafdigger's Cage", pod.game.getPlayers().get(2), ZoneType.Battlefield);
        Card first = addCardToZone("Grizzly Bears", pod.actor, ZoneType.Graveyard);
        pod.target[0] = first;
        sacrifice(pod);
        assertTrue(pod.game.getCardState(first).isInZone(ZoneType.Hand));
        Card next = addCardToZone("Runeclaw Bear", pod.actor, ZoneType.Graveyard);
        pod.target[0] = next;
        sacrifice(pod);
        assertTrue(pod.game.getCardState(next).isInZone(ZoneType.Graveyard));
    }

    @Test
    public void aLibrarySearchStillUsesItsAllowanceWhenThePutIsProhibited() {
        Pod pod = newPod("Deep Gnome Terramancer");
        Card plains = addCardToZone("Plains", pod.actor, ZoneType.Library);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Swamp", pod.actor, ZoneType.Battlefield);
            addCardToZone("Plains", pod.actor, ZoneType.Battlefield);
        }
        Player opponent = pod.game.getPlayers().get(1);
        for (int index = 0; index < 5; index++) {
            addCardToZone("Forest", opponent, ZoneType.Battlefield);
        }
        for (int index = 0; index < 3; index++) {
            addCardToZone("Forest", opponent, ZoneType.Library);
        }
        castRampantGrowth(pod, opponent);
        assertEquals(pod.game.getStack().size(), 1);
        SpellAbility worms = addCardToZone("Worms of the Earth", pod.actor, ZoneType.Hand)
                .getFirstSpellAbility();
        worms.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.playStack(worms, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        assertTrue(worms.getHostCard().isInZone(ZoneType.Battlefield));
        settle(pod.game);
        assertTrue(pod.game.getCardState(plains).isInZone(ZoneType.Library));
        assertEquals(pod.actor.getLibrarySearched(), 1, "The search itself is legal");
        SpellAbility removal = addCardToZone("Anguished Unmaking", pod.actor, ZoneType.Hand)
                .getFirstSpellAbility();
        removal.setActivatingPlayer(pod.actor);
        removal.getTargets().add(worms.getHostCard());
        assertTrue(ComputerUtil.playStack(removal, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(worms.getHostCard()).isInZone(ZoneType.Exile));
        castRampantGrowth(pod, opponent);
        settle(pod.game);
        assertEquals(pod.actor.getLibrarySearched(), 1, "The search already used this turn's action");
        assertEquals(pod.prompts[0], 1);
        assertTrue(pod.game.getCardState(plains).isInZone(ZoneType.Library));
    }

    private void castRampantGrowth(Pod pod, Player player) {
        SpellAbility ramp = addCardToZone("Rampant Growth", player, ZoneType.Hand).getFirstSpellAbility();
        ramp.setActivatingPlayer(player);
        assertTrue(ComputerUtil.playStack(ramp, player, pod.game));
        pod.game.getStack().resolveStack();
        queueTriggers(pod.game);
    }

    @Test
    public void aMixedTargetedReturnCanMoveTheUnprohibitedCreature() {
        Pod pod = newPod("Sepulchral Primordial");
        addCardToZone("Worms of the Earth", pod.game.getPlayers().get(3), ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", pod.game.getPlayers().get(1), ZoneType.Graveyard);
        Card arbor = addCardToZone("Dryad Arbor", pod.game.getPlayers().get(2), ZoneType.Graveyard);
        pod.target[0] = bear;
        pod.target[1] = arbor;
        int lifeBefore = pod.actor.getLife();
        SpellAbility reanimate = addCardToZone("Reanimate", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        reanimate.setActivatingPlayer(pod.actor);
        reanimate.getTargets().add(pod.host);
        assertTrue(ComputerUtil.playStack(reanimate, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queueTriggers(pod.game);
        assertEquals(pod.game.getStack().size(), 1);
        settle(pod.game);
        assertTrue(pod.game.getCardState(bear).isInZone(ZoneType.Battlefield));
        assertEquals(pod.game.getCardState(bear).getController(), pod.actor);
        assertTrue(pod.game.getCardState(arbor).isInZone(ZoneType.Graveyard));
        assertEquals(pod.actor.getLife(), lifeBefore - 7);
        assertEquals(pod.prompts[0], 1);
    }

    private void verifyReturn(String blockerName, ZoneType firstZone) {
        Pod pod = newPod();
        Card blocker = blockerName == null ? null : addCardToZone(blockerName,
                pod.game.getPlayers().get(2), ZoneType.Battlefield);
        Card target = addCardToZone("Grizzly Bears", pod.actor, ZoneType.Graveyard);
        pod.target[0] = target;
        sacrifice(pod);
        assertTrue(pod.game.getCardState(target).isInZone(firstZone), "First return outcome");
        assertEquals(pod.prompts[0], firstZone == ZoneType.Graveyard ? 0 : 1);
        if (blocker != null) {
            SpellAbility removal = addCardToZone("Anguished Unmaking", pod.actor, ZoneType.Hand)
                    .getFirstSpellAbility();
            removal.setActivatingPlayer(pod.actor);
            removal.getTargets().add(blocker);
            assertTrue(ComputerUtil.playStack(removal, pod.actor, pod.game));
            settle(pod.game);
            assertTrue(pod.game.getCardState(blocker).isInZone(ZoneType.Exile));
        }
        Card next = firstZone == ZoneType.Graveyard ? pod.game.getCardState(target)
                : addCardToZone("Runeclaw Bear", pod.actor, ZoneType.Graveyard);
        pod.target[0] = next;
        sacrifice(pod);
        assertTrue(pod.game.getCardState(next).isInZone(firstZone == ZoneType.Graveyard
                ? ZoneType.Battlefield : ZoneType.Graveyard),
                "Only an impossible optional return preserves the allowance");
        if (firstZone == ZoneType.Graveyard) {
            assertTrue(pod.game.getCardState(next).isTapped());
            assertEquals(pod.game.getCardState(next).getController(), pod.actor);
        }
        Card third = addCardToZone("Balduvian Bears", pod.actor, ZoneType.Graveyard);
        pod.target[0] = third;
        sacrifice(pod);
        assertTrue(pod.game.getCardState(third).isInZone(ZoneType.Graveyard));
        assertEquals(pod.prompts[0], 1);
    }

    private Pod newPod() {
        return newPod("Riveteers Ascendancy");
    }

    private Pod newPod(String hostName) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Targeted return"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        Card host = addCardToZone(hostName, actor, hostName.equals("Sepulchral Primordial")
                ? ZoneType.Graveyard : ZoneType.Battlefield);
        Card[] target = {null, null};
        boolean[] decline = {false};
        int[] prompts = {0};
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public void orderAndPlaySimultaneousSa(List<SpellAbility> abilities) {
                for (SpellAbility ability : abilities) {
                    if (ability.getHostCard().equals(host)) {
                        if (ability.usesTargeting()) {
                            ability.resetTargets();
                            for (Card card : target) {
                                if (card != null) {
                                    assertTrue(ability.canTarget(card), "The chosen target is legal");
                                    ability.getTargets().add(card);
                                }
                            }
                        }
                        assertTrue(ComputerUtil.playStack(ability, actor, game));
                    } else {
                        super.orderAndPlaySimultaneousSa(List.of(ability));
                    }
                }
            }

            @Override
            public boolean confirmTrigger(WrappedAbility ability) {
                if (ability.getHostCard().equals(host)) {
                    prompts[0]++;
                    return !decline[0];
                }
                return super.confirmTrigger(ability);
            }
        });
        for (String name : List.of("Swamp", "Plains", "Plains")) {
            addCardToZone(name, actor, ZoneType.Battlefield);
        }
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        return new Pod(game, actor, host, target, decline, prompts);
    }

    private void sacrifice(Pod pod) {
        sacrificeToStack(pod);
        settle(pod.game);
    }

    private void sacrificeToStack(Pod pod) {
        Card fodder = addCardToZone("Blood Vassal", pod.actor, ZoneType.Battlefield);
        fodder.setCounters(CounterEnumType.P1P1, 2);
        pod.game.getAction().checkStateEffects(true);
        SpellAbility mana = fodder.getManaAbilities().get(0);
        mana.setActivatingPlayer(pod.actor);
        assertTrue(ComputerUtil.playNoStack(pod.actor, mana, pod.game, false));
        assertTrue(pod.game.getCardState(fodder).isInZone(ZoneType.Graveyard));
        queueTriggers(pod.game);
    }

    private void queueTriggers(Game game) {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private void settle(Game game) {
        for (int step = 0; step < 12 && !game.getStack().isEmpty(); step++) {
            game.getStack().resolveStack();
        }
        assertTrue(game.getStack().isEmpty());
    }

    private record Pod(Game game, Player actor, Card host, Card[] target, boolean[] decline, int[] prompts) {
    }
}
