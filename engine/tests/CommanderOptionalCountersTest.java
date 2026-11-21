package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.WrappedAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Partial-state counter-choice witnesses with supplied decisions and native paid spells. */
public class CommanderOptionalCountersTest extends AITest {
    @Test
    public void solemnityPreservesTheOptionalCounterAction() {
        Pod pod = pod("Solemnity");
        assertTrue(!pod.host.canReceiveCounters(CounterEnumType.P1P1, AbilityKey.newMap()));
        bear(pod);
        assertEquals(pod.actor.getLife(), 41);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 0);
        remove(pod);
        bear(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 1,
                "The prohibited counter choice did not consume the allowance");
        bear(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void ordinaryCountersUseTheAction() {
        Pod pod = pod(null);
        bear(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 1);
        bear(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(pod.prompts[0], 1);
    }

    @Test
    public void decliningPreservesTheCounterAction() {
        Pod pod = pod(null);
        pod.decline[0] = true;
        bear(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 0);
        pod.decline[0] = false;
        bear(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 1);
        bear(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(pod.prompts[0], 2);
    }

    @Test
    public void countersReplacedWithZeroStillUseTheAction() {
        Pod pod = pod("Vorinclex, Monstrous Raider");
        assertTrue(pod.host.canReceiveCounters(CounterEnumType.P1P1, AbilityKey.newMap()));
        bear(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 0);
        assertEquals(pod.prompts[0], 1);
        remove(pod);
        bear(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 0);
        assertEquals(pod.prompts[0], 1);
    }

    @Test
    public void eligibleCreaturesReceiveCountersBesideTatterkite() {
        Pod pod = pod(null);
        Card kite = addCardToZone("Tatterkite", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        bear(pod);
        assertEquals(kite.getCounters(CounterEnumType.P1P1), 0);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(pod.prompts[0], 1);
    }

    @Test
    public void counteringAPlacementPreservesTheAllowance() {
        Pod pod = pod(null);
        bearToCounterStack(pod);
        Player responder = pod.game.getPlayers().get(2);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Island", responder, ZoneType.Battlefield);
        }
        SpellAbility spell = addCardToZone("Disallow", responder, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(responder);
        spell.getTargets().add(pod.game.getStack().peekAbility());
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, responder, pod.game));
        settle(pod.game);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 0);
        assertEquals(pod.prompts[0], 0);
        bear(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void copiedPlacementsUseTheAllowanceOnlyOnce() {
        Pod pod = pod(null);
        bearToCounterStack(pod);
        resonate(pod);
        settle(pod.game);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(pod.prompts[0], 1);
    }

    @Test
    public void removalBeforeResolutionMakesTheFirstPlacementLegal() {
        Pod pod = pod("Solemnity");
        bearToCounterStack(pod);
        remove(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 1);
        bear(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void aProhibitionAddedBeforeResolutionPreservesTheAllowance() {
        Pod pod = pod(null);
        Player opponent = pod.game.getPlayers().get(1);
        addCardToZone("Vedalken Orrery", opponent, ZoneType.Battlefield);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Plains", opponent, ZoneType.Battlefield);
        }
        pod.game.getTriggerHandler().resetActiveTriggers();
        bearToCounterStack(pod);
        Card blocker = addCardToZone("Solemnity", opponent, ZoneType.Hand);
        SpellAbility spell = blocker.getFirstSpellAbility();
        spell.setActivatingPlayer(opponent);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, opponent, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(blocker).isInZone(ZoneType.Battlefield));
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 0);
        exile(pod, pod.game.getCardState(blocker));
        bear(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void prohibitedHistoryDoesNotLeakAcrossGameCopies() {
        Pod original = pod("Solemnity");
        bear(original);
        Pod fork = snapshot(original);
        remove(fork);
        bear(fork);
        assertEquals(fork.host.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(original.host.getCounters(CounterEnumType.P1P1), 0);
        assertTrue(original.blocker.isInZone(ZoneType.Battlefield));
        remove(original);
        bear(original);
        assertEquals(original.host.getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void usedHistoryStaysUsedAcrossGameCopies() {
        Pod original = pod(null);
        bear(original);
        Pod fork = snapshot(original);
        bear(fork);
        assertEquals(fork.host.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(fork.prompts[0], 0);
        assertEquals(original.host.getCounters(CounterEnumType.P1P1), 1);
        bear(original);
        assertEquals(original.host.getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void nativeExtraTurnCleanupResetsThePlacementAllowance() {
        Pod pod = pod(null);
        addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        bear(pod);
        SpellAbility warp = addCardToZone("Time Warp", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        warp.setActivatingPlayer(pod.actor);
        warp.getTargets().add(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(warp, pod.actor, pod.game));
        settle(pod.game);
        int turn = pod.game.getPhaseHandler().getTurn();
        assertTrue(pod.game.getPhaseHandler().devAdvanceThroughTurnEnd(
                PhaseType.MAIN1, () -> settle(pod.game)));
        settle(pod.game);
        assertEquals(pod.game.getPhaseHandler().getTurn(), turn + 1);
        assertEquals(pod.game.getPhaseHandler().getPlayerTurn(), pod.actor);
        bear(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 2);
    }

    @Test
    public void leonardoPreservesItsFixedAmountPlacementUnderSolemnity() {
        Pod pod = pod("Leonardo, the Balance", "Solemnity");
        for (int index = 0; index < 5; index++) {
            addCardToZone("Plains", pod.actor, ZoneType.Battlefield);
        }
        raise(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 0);
        remove(pod);
        raise(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(pod.actor.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).count(), 4);
    }

    @Test
    public void anEmptyGroupDoesNotConsumeALaterCopiedPlacement() {
        Pod pod = pod(null);
        addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        bearToCounterStack(pod);
        resonate(pod);
        assertEquals(pod.game.getStack().size(), 2);
        SpellAbility evacuation = addCardToZone("Evacuation", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        evacuation.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(evacuation, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isCreature).count(), 0);
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertEquals(pod.prompts[0], 0);
        Card sailor = addCardToZone("Spectral Sailor", pod.actor, ZoneType.Hand);
        SpellAbility flash = sailor.getFirstSpellAbility();
        flash.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(flash, pod.actor, pod.game));
        settle(pod.game);
        assertEquals(pod.game.getCardState(sailor).getCounters(CounterEnumType.P1P1), 1);
        assertEquals(pod.prompts[0], 1);
    }

    @Test
    public void aCounterPaymentRemainsLegalWhenPlacementIsProhibited() {
        Pod pod = pod("Guiding Hydra", "Solemnity", 2);
        combatTrigger(pod);
        assertEquals(pod.host.getCounters(CounterEnumType.P1P1), 1);
        Card warden = pod.actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Essence Warden")).findFirst().orElseThrow();
        assertEquals(warden.getCounters(CounterEnumType.P1P1), 0);
        assertEquals(pod.prompts[0], 1);
    }

    @Test
    public void aLegalCounterPaymentMayKillItsSource() {
        Pod pod = pod("Guiding Hydra", "Solemnity", 1);
        combatTrigger(pod);
        pod.game.getAction().checkStateEffects(true);
        assertTrue(pod.game.getCardState(pod.host).isInZone(ZoneType.Graveyard));
        assertEquals(pod.prompts[0], 1);
    }

    private void combatTrigger(Pod pod) {
        pod.game.getPhaseHandler().devModeSet(PhaseType.COMBAT_BEGIN, pod.actor, 2);
        pod.game.getTriggerHandler().resetActiveTriggers();
        pod.game.copyLastState();
        pod.game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(pod.actor), false);
        settle(pod.game);
    }

    private void raise(Pod pod) {
        SpellAbility spell = addCardToZone("Raise the Alarm", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        settle(pod.game);
    }

    private void bearToCounterStack(Pod pod) {
        SpellAbility spell = addCardToZone("Grizzly Bears", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 1);
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 1);
        assertEquals(pod.game.getStack().peekAbility().getHostCard().getId(), pod.host.getId());
    }

    private void resonate(Pod pod) {
        Card resonator = addCardToZone("Strionic Resonator", pod.actor, ZoneType.Battlefield);
        SpellAbility ability = resonator.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.CopySpellAbility)
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(pod.actor);
        ability.getTargets().add(pod.game.getStack().peekAbility());
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(ability, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertTrue(resonator.isTapped());
    }

    private Pod snapshot(Pod original) {
        GameCopier copier = new GameCopier(original.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        Card host = copy.getCardsInGame().stream().filter(card -> card.getId() == original.host.getId())
                .findFirst().orElseThrow();
        Card blocker = original.blocker == null ? null : copy.getCardsInGame().stream()
                .filter(card -> card.getId() == original.blocker.getId()).findFirst().orElseThrow();
        Pod fork = new Pod(copy, copy.getPlayer(original.actor.getId()), host, blocker,
                new int[]{0}, new boolean[]{false});
        control(fork);
        copy.getTriggerHandler().resetActiveTriggers();
        return fork;
    }

    private void bear(Pod pod) {
        SpellAbility spell = addCardToZone("Grizzly Bears", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        settle(pod.game);
    }

    private void remove(Pod pod) {
        exile(pod, pod.blocker);
    }

    private void exile(Pod pod, Card blocker) {
        int life = pod.actor.getLife();
        SpellAbility spell = addCardToZone("Anguished Unmaking", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        spell.getTargets().add(blocker);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(blocker).isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getLife(), life - 3);
    }

    private Pod pod(String blockerName) {
        return pod("Nykthos Paragon", blockerName);
    }

    private Pod pod(String hostName, String blockerName) {
        return pod(hostName, blockerName, 0);
    }

    private Pod pod(String hostName, String blockerName, int initialCounters) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Optional counters"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 8; index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        for (String name : List.of("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Swamp", "Plains", "Plains", "Island")) {
            addCardToZone(name, actor, ZoneType.Battlefield);
        }
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        Card blocker = blockerName == null ? null : addCardToZone(blockerName, game.getPlayers().get(1), ZoneType.Battlefield);
        Card host = addCardToZone(hostName, actor, ZoneType.Battlefield);
        if (initialCounters > 0) {
            host.setCounters(CounterEnumType.P1P1, initialCounters);
        }
        addCardToZone("Essence Warden", actor, ZoneType.Battlefield);
        int[] prompts = {0};
        boolean[] decline = {false};
        Pod pod = new Pod(game, actor, host, blocker, prompts, decline);
        control(pod);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        return pod;
    }

    private void control(Pod pod) {
        Game game = pod.game;
        Player actor = pod.actor;
        Card host = pod.host;
        int[] prompts = pod.prompts;
        boolean[] decline = pod.decline;
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public void orderAndPlaySimultaneousSa(List<SpellAbility> abilities) {
                for (SpellAbility ability : abilities) {
                    if (ability.getHostCard().getId() == host.getId()) {
                        assertTrue(ComputerUtil.playStack(ability, actor, game));
                    } else {
                        super.orderAndPlaySimultaneousSa(List.of(ability));
                    }
                }
            }
            @Override
            public boolean confirmTrigger(WrappedAbility ability) {
                if (ability.getHostCard().getId() == host.getId()) {
                    prompts[0]++;
                    return !decline[0];
                }
                return super.confirmTrigger(ability);
            }
        });
    }

    private void queue(Game game) {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private void settle(Game game) {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        for (int step = 0; step < 16 && !game.getStack().isEmpty(); step++) {
            game.getStack().resolveStack();
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
        }
        assertTrue(game.getStack().isEmpty());
    }
    private record Pod(Game game, Player actor, Card host, Card blocker, int[] prompts, boolean[] decline) {}
}
