package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.trigger.WrappedAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Partial-state main-phase witnesses with supplied targets and native mana/cost resolution. */
public class CommanderCarpetManaTest extends AITest {
    @Test
    public void addingZeroLeavesTheSecondMainPhaseAvailable() {
        Pod pod = pod(false);
        phase(pod, PhaseType.MAIN1);
        assertEquals(pod.actor.getManaPool().totalMana(), 0);
        assertEquals(pod.prompts[0], 1);
        harrow(pod);
        phase(pod, PhaseType.MAIN2);
        assertEquals(pod.actor.getManaPool().totalMana(), 1,
                "Adding zero mana did not use the turn's addition");
    }

    @Test
    public void addingManaPreventsASecondAddition() {
        Pod pod = pod(true);
        phase(pod, PhaseType.MAIN1);
        assertEquals(pod.actor.getManaPool().totalMana(), 1);
        int before = pod.actor.getManaPool().totalMana();
        phase(pod, PhaseType.MAIN2);
        assertEquals(pod.actor.getManaPool().totalMana(), before);
        assertEquals(pod.prompts[0], 1);
    }

    @Test
    public void decliningLeavesTheSecondMainPhaseAvailable() {
        Pod pod = pod(true);
        pod.decline[0] = true;
        phase(pod, PhaseType.MAIN1);
        assertEquals(pod.actor.getManaPool().totalMana(), 0);
        pod.decline[0] = false;
        phase(pod, PhaseType.MAIN2);
        assertEquals(pod.actor.getManaPool().totalMana(), 1);
        assertEquals(pod.prompts[0], 2);
    }

    @Test
    public void counteringTheFirstTriggerPreservesTheAddition() {
        Pod pod = pod(true);
        queuePhase(pod, PhaseType.MAIN1);
        assertEquals(pod.game.getStack().size(), 1);
        Player responder = responder(pod);
        SpellAbility counter = addCardToZone("Disallow", responder, ZoneType.Hand).getFirstSpellAbility();
        counter.setActivatingPlayer(responder);
        counter.getTargets().add(pod.game.getStack().peekAbility());
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(counter, responder, pod.game));
        settle(pod.game);
        assertEquals(pod.actor.getManaPool().totalMana(), 0);
        assertEquals(pod.prompts[0], 0);
        phase(pod, PhaseType.MAIN2);
        assertEquals(pod.actor.getManaPool().totalMana(), 1);
    }

    @Test
    public void drainingAddedManaDoesNotReopenTheAddition() {
        Pod pod = pod(true);
        phase(pod, PhaseType.MAIN1);
        assertEquals(pod.actor.getManaPool().totalMana(), 1);
        drain(pod);
        phase(pod, PhaseType.MAIN2);
        assertEquals(pod.actor.getManaPool().totalMana(), 0);
        assertEquals(pod.prompts[0], 1);
    }

    @Test
    public void copiedPositiveTriggersAddOnlyOnce() {
        Pod pod = pod(true);
        queuePhase(pod, PhaseType.MAIN1);
        resonate(pod);
        assertEquals(pod.actor.getManaPool().totalMana(), 1);
        phase(pod, PhaseType.MAIN2);
        assertEquals(pod.actor.getManaPool().totalMana(), 1);
    }

    @Test
    public void copiedZeroTriggersLeaveALaterAdditionAvailable() {
        Pod pod = pod(false);
        queuePhase(pod, PhaseType.MAIN1);
        resonate(pod);
        assertEquals(pod.actor.getManaPool().totalMana(), 0);
        harrow(pod);
        phase(pod, PhaseType.MAIN2);
        assertEquals(pod.actor.getManaPool().totalMana(), 1);
    }

    @Test
    public void zeroHistoryStaysIndependentAcrossGameCopies() {
        Pod original = pod(false);
        phase(original, PhaseType.MAIN1);
        Pod fork = snapshot(original);
        harrow(fork);
        phase(fork, PhaseType.MAIN2);
        assertEquals(fork.actor.getManaPool().totalMana(), 1);
        assertEquals(original.actor.getManaPool().totalMana(), 0);
        assertEquals(original.opponent.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getType().hasSubtype("Island")).count(), 0);
        harrow(original);
        phase(original, PhaseType.MAIN2);
        assertEquals(original.actor.getManaPool().totalMana(), 1);
    }

    @Test
    public void positiveHistorySurvivesGameCopyAndManaDrain() {
        Pod original = pod(true);
        phase(original, PhaseType.MAIN1);
        Pod fork = snapshot(original);
        drain(fork);
        phase(fork, PhaseType.MAIN2);
        assertEquals(fork.actor.getManaPool().totalMana(), 0);
        assertEquals(fork.prompts[0], 0);
        assertEquals(original.actor.getManaPool().totalMana(), 1);
        phase(original, PhaseType.MAIN2);
        assertEquals(original.actor.getManaPool().totalMana(), 1);
    }

    @Test
    public void replacedWhiteManaStillRecordsTheAddition() {
        Pod pod = pod(true);
        pod.decline[0] = true;
        phase(pod, PhaseType.MAIN1);
        SpellAbility dawn = addCardToZone("False Dawn", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        dawn.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(dawn, pod.actor, pod.game));
        settle(pod.game);
        assertEquals(pod.actor.getManaPool().totalMana(), 0);
        pod.decline[0] = false;
        phase(pod, PhaseType.MAIN2);
        assertEquals(pod.actor.getManaPool().getAmountOfColor(MagicColor.WHITE), 1);
        assertEquals(pod.actor.getManaPool().getAmountOfColor(MagicColor.BLUE), 0);
        assertEquals(pod.actor.getManaPool().totalMana(), 1);
        assertEquals(pod.prompts[0], 2);
    }

    @Test
    public void nativeExtraTurnCleanupResetsTheManaHistory() {
        Pod pod = pod(true);
        addCardToZone("Island", pod.actor, ZoneType.Battlefield);
        phase(pod, PhaseType.MAIN1);
        warp(pod);
        nextExtraTurn(pod);
        assertEquals(pod.actor.getManaPool().totalMana(), 1);
        assertEquals(pod.prompts[0], 2);
    }

    @Test
    public void copiedExtraTurnsResetHistoryAndExpireReplacementIndependently() {
        Pod original = pod(true);
        original.decline[0] = true;
        phase(original, PhaseType.MAIN1);
        SpellAbility dawn = addCardToZone("False Dawn", original.actor, ZoneType.Hand).getFirstSpellAbility();
        dawn.setActivatingPlayer(original.actor);
        original.game.copyLastState();
        assertTrue(ComputerUtil.playStack(dawn, original.actor, original.game));
        settle(original.game);
        warp(original);
        original.decline[0] = false;
        phase(original, PhaseType.MAIN2);
        assertEquals(original.actor.getManaPool().getAmountOfColor(MagicColor.WHITE), 1);
        int sourceTurn = original.game.getPhaseHandler().getTurn();
        Pod fork = snapshot(original);
        nextExtraTurn(fork);
        assertEquals(fork.actor.getManaPool().getAmountOfColor(MagicColor.BLUE), 1);
        assertEquals(fork.actor.getManaPool().getAmountOfColor(MagicColor.WHITE), 0);
        assertEquals(original.game.getPhaseHandler().getTurn(), sourceTurn);
        assertEquals(original.actor.getManaPool().getAmountOfColor(MagicColor.WHITE), 1);
        nextExtraTurn(original);
        assertEquals(original.actor.getManaPool().getAmountOfColor(MagicColor.BLUE), 1);
        assertEquals(original.actor.getManaPool().getAmountOfColor(MagicColor.WHITE), 0);
    }

    private void warp(Pod pod) {
        SpellAbility spell = addCardToZone("Time Warp", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        spell.getTargets().add(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        settle(pod.game);
    }

    private void nextExtraTurn(Pod pod) {
        int before = pod.game.getPhaseHandler().getTurn();
        assertTrue(pod.game.getPhaseHandler().devAdvanceThroughTurnEnd(
                PhaseType.MAIN1, () -> settle(pod.game)), "Native phase, cleanup and extra-turn hooks");
        settle(pod.game);
        assertEquals(pod.game.getPhaseHandler().getTurn(), before + 1);
        assertEquals(pod.game.getPhaseHandler().getPlayerTurn(), pod.actor);
        assertEquals(pod.game.getPhaseHandler().getPhase(), PhaseType.MAIN1);
    }

    private Player responder(Pod pod) {
        Player player = pod.game.getPlayers().get(2);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Island", player, ZoneType.Battlefield);
        }
        return player;
    }

    private void drain(Pod pod) {
        Player responder = responder(pod);
        SpellAbility spell = addCardToZone("Mana Short", responder, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(responder);
        spell.getTargets().add(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, responder, pod.game));
        settle(pod.game);
        assertEquals(pod.actor.getManaPool().totalMana(), 0);
    }

    private void resonate(Pod pod) {
        assertEquals(pod.game.getStack().size(), 1);
        Card resonator = addCardToZone("Strionic Resonator", pod.actor, ZoneType.Battlefield);
        SpellAbility ability = resonator.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.CopySpellAbility)
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(pod.actor);
        ability.getTargets().add(pod.game.getStack().peekAbility());
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(ability, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(resonator.isTapped());
    }

    private Pod snapshot(Pod original) {
        GameCopier copier = new GameCopier(original.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        Card host = copy.getCardsInGame().stream().filter(card -> card.getId() == original.host.getId())
                .findFirst().orElseThrow();
        Pod fork = new Pod(copy, copy.getPlayer(original.actor.getId()),
                copy.getPlayer(original.opponent.getId()), host, new int[]{0}, new boolean[]{false});
        control(fork);
        copy.getTriggerHandler().resetActiveTriggers();
        return fork;
    }

    private void phase(Pod pod, PhaseType phase) {
        queuePhase(pod, phase);
        settle(pod.game);
    }

    private void queuePhase(Pod pod, PhaseType phase) {
        pod.game.getPhaseHandler().devModeSet(phase, pod.actor, pod.game.getPhaseHandler().getTurn());
        pod.game.getTriggerHandler().resetActiveTriggers();
        pod.game.copyLastState();
        pod.game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(pod.actor), false);
        queue(pod.game);
    }

    private void harrow(Pod pod) {
        SpellAbility spell = addCardToZone("Harrow", pod.opponent, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.opponent);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.opponent, pod.game));
        settle(pod.game);
        assertEquals(pod.opponent.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getType().hasSubtype("Island")).count(), 1);
        assertEquals(pod.game.getCardState(spell.getHostCard()).getZone().getZoneType(), ZoneType.Graveyard);
    }

    private Pod pod(boolean initialIsland) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Carpet mana"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        for (Player player : game.getPlayers()) {
            if (player.equals(opponent)) {
                addCardToZone("Island", player, ZoneType.Library);
                addCardToZone("Forest", player, ZoneType.Library);
            } else {
                for (int index = 0; index < 8; index++) {
                    addCardToZone("Forest", player, ZoneType.Library);
                }
            }
        }
        for (int index = 0; index < 3; index++) {
            addCardToZone("Forest", opponent, ZoneType.Battlefield);
        }
        if (initialIsland) {
            addCardToZone("Island", opponent, ZoneType.Battlefield);
        }
        for (String name : List.of("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Swamp", "Plains", "Plains", "Island")) {
            addCardToZone(name, actor, ZoneType.Battlefield);
        }
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        Card host = addCardToZone("Carpet of Flowers", actor, ZoneType.Battlefield);
        int[] prompts = {0};
        boolean[] decline = {false};
        Pod pod = new Pod(game, actor, opponent, host, prompts, decline);
        control(pod);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        return pod;
    }

    private void control(Pod pod) {
        Game game = pod.game;
        Player actor = pod.actor;
        Player opponent = pod.opponent;
        Card host = pod.host;
        int[] prompts = pod.prompts;
        boolean[] decline = pod.decline;
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public void orderAndPlaySimultaneousSa(List<SpellAbility> abilities) {
                for (SpellAbility ability : abilities) {
                    if (ability.getHostCard().getId() == host.getId()) {
                        if (ability.usesTargeting()) {
                            ability.resetTargets();
                            assertTrue(ability.canTarget(opponent));
                            ability.getTargets().add(opponent);
                        }
                        assertTrue(ComputerUtil.playStack(ability, actor, game));
                    } else {
                        super.orderAndPlaySimultaneousSa(List.of(ability));
                    }
                }
            }
            @Override
            public byte chooseColor(String title, SpellAbility ability, ColorSet colors) {
                if (ability.getHostCard().getId() == host.getId()) {
                    return MagicColor.BLUE;
                }
                return super.chooseColor(title, ability, colors);
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
    private record Pod(Game game, Player actor, Player opponent, Card host, int[] prompts, boolean[] decline) {}
}
