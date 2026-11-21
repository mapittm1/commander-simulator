package forge.ai;

import forge.ai.simulation.GameCopier;
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

/** Supplied battlefield/decisions with native paid spells and cast-from-effect resolution. */
public class CommanderOptionalPlayTest extends AITest {
    @Test
    public void anActualGraveyardCastUsesTheAllowance() {
        Pod pod = pod(false);
        shock(pod);
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Exile));
        assertEquals(pod.opponent.getLife(), 35);
        shock(pod);
        assertEquals(pod.opponent.getLife(), 33);
        assertEquals(pod.prompts[0], 1);
    }
    @Test
    public void decliningTheTriggerPreservesTheAllowance() {
        Pod pod = pod(false);
        pod.decline[0] = true;
        shock(pod);
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Graveyard));
        assertEquals(pod.opponent.getLife(), 38);
        pod.decline[0] = false;
        shock(pod);
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Exile));
        assertEquals(pod.opponent.getLife(), 33);
    }
    @Test
    public void teferisCastingRestrictionPreservesALaterLegalCast() {
        Pod pod = pod(true);
        shock(pod);
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Graveyard));
        assertEquals(pod.opponent.getLife(), 38);
        SpellAbility removal = addCardToZone("Anguished Unmaking", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        removal.setActivatingPlayer(pod.actor);
        removal.getTargets().add(pod.blocker);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(removal, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.blocker).isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getLife(), 37);
        shock(pod);
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Exile),
                "The earlier impossible cast must leave a later legal cast available");
        assertEquals(pod.opponent.getLife(), 33);
    }
    @Test
    public void decliningTheCastDialogPreservesTheAllowance() {
        Pod pod = pod(false);
        pod.declineCast[0] = true;
        shock(pod);
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Graveyard));
        assertEquals(pod.opponent.getLife(), 38);
        pod.declineCast[0] = false;
        shock(pod);
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Exile),
                "No spell was cast through the earlier optional dialog");
        assertEquals(pod.opponent.getLife(), 33);
    }
    @Test
    public void counteringAnActuallyCastSpellDoesNotRefundTheAllowance() {
        Pod pod = pod(false);
        toTrigger(pod);
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Stack));
        counterTop(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Exile));
        shock(pod);
        assertEquals(pod.opponent.getLife(), 36);
        assertEquals(pod.prompts[0], 1);
    }
    @Test
    public void counteringTheCastTriggerPreservesTheAllowance() {
        Pod pod = pod(false);
        toTrigger(pod);
        counterTop(pod);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Graveyard));
        shock(pod);
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Exile));
        assertEquals(pod.opponent.getLife(), 33);
    }
    @Test
    public void aFailedManaPaymentPreservesALaterPayableCast() {
        Pod pod = pod(false, 1);
        Card mountain = pod.actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Mountain")).findFirst().orElseThrow();
        shock(pod);
        assertTrue(mountain.isTapped());
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Graveyard));
        assertEquals(pod.opponent.getLife(), 38);
        SpellAbility infuse = addCardToZone("Infuse", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        infuse.setActivatingPlayer(pod.actor);
        infuse.getTargets().add(mountain);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(infuse, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(!mountain.isTapped());
        SpellAbility ritual = addCardToZone("Desperate Ritual", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        ritual.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(ritual, pod.actor, pod.game));
        settle(pod.game);
        shock(pod);
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Exile));
        assertEquals(pod.opponent.getLife(), 33);
    }
    @Test
    public void anActualCastStaysUsedInIndependentCopiedGames() {
        Pod pod = pod(false);
        shock(pod);
        GameCopier copier = new GameCopier(pod.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        Card host = find(copy, pod.host);
        Card bolt = find(copy, pod.bolt);
        Pod fork = new Pod(copy, copy.getPlayer(pod.actor.getId()), copy.getPlayer(pod.opponent.getId()),
                host, bolt, null, new boolean[]{false}, new boolean[]{false}, new int[]{0});
        control(fork);
        copy.getTriggerHandler().resetActiveTriggers();
        shock(fork);
        assertEquals(fork.opponent.getLife(), 33);
        assertEquals(pod.opponent.getLife(), 35);
        shock(pod);
        assertEquals(pod.opponent.getLife(), 33);
        assertEquals(fork.prompts[0], 0);
    }
    @Test
    public void aDeclinedCastLeavesNoReplacementEffect() {
        Pod pod = pod(false);
        int effects = pod.game.getCardsIn(ZoneType.Command).size();
        pod.declineCast[0] = true;
        shock(pod);
        assertEquals(pod.game.getCardsIn(ZoneType.Command).size(), effects,
                "Declining a cast cannot install a replacement for a spell that was never cast");
        Pod fork = snapshot(pod);
        shock(fork);
        assertTrue(fork.game.getCardState(fork.bolt).isInZone(ZoneType.Exile));
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Graveyard));
    }
    @Test
    public void failedPaymentLeavesNoReplacementEffect() {
        Pod pod = pod(false, 1);
        int effects = pod.game.getCardsIn(ZoneType.Command).size();
        shock(pod);
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Graveyard));
        assertEquals(pod.game.getCardsIn(ZoneType.Command).size(), effects);
    }
    @Test
    public void aProhibitedPendingCastRemainsUnusedInCopiedGames() {
        Pod pod = pod(true);
        toTrigger(pod);
        Pod fork = snapshot(pod);
        settle(fork.game);
        settle(pod.game);
        remove(fork, fork.blocker);
        remove(pod, pod.blocker);
        shock(fork);
        shock(pod);
        assertTrue(fork.game.getCardState(fork.bolt).isInZone(ZoneType.Exile));
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Exile));
        assertEquals(fork.opponent.getLife(), 33);
        assertEquals(pod.opponent.getLife(), 33);
    }
    @Test
    public void copiedPendingCastChoicesDoNotChangeTheOriginalGame() {
        Pod pod = pod(false);
        toTrigger(pod);
        Pod fork = snapshot(pod);
        fork.declineCast[0] = true;
        settle(fork.game);
        assertTrue(fork.game.getCardState(fork.bolt).isInZone(ZoneType.Graveyard));
        assertEquals(pod.opponent.getLife(), 38);
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Exile));
        assertEquals(pod.opponent.getLife(), 35);
        fork.declineCast[0] = false;
        shock(fork);
        assertTrue(fork.game.getCardState(fork.bolt).isInZone(ZoneType.Exile));
        assertEquals(fork.opponent.getLife(), 33);
        assertEquals(pod.opponent.getLife(), 35);
    }
    @Test
    public void aQueuedCastSurvivesItsSourceReturningToHand() {
        Pod pod = pod(false);
        toTrigger(pod);
        SpellAbility bounce = addCardToZone("Echoing Truth", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        bounce.setActivatingPlayer(pod.actor);
        bounce.getTargets().add(pod.host);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(bounce, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.host).isInZone(ZoneType.Hand));
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Exile));
        assertEquals(pod.opponent.getLife(), 35);
    }
    @Test
    public void nativeCleanupResetsActualCastHistoryForAnExtraTurn() {
        Pod pod = pod(false);
        shock(pod);
        SpellAbility pull = addCardToZone("Pull from Eternity", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        pull.setActivatingPlayer(pod.actor);
        pull.getTargets().add(pod.game.getCardState(pod.bolt));
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(pull, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Graveyard));
        SpellAbility warp = addCardToZone("Time Warp", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        warp.setActivatingPlayer(pod.actor);
        warp.getTargets().add(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(warp, pod.actor, pod.game));
        settle(pod.game);
        int turn = pod.game.getPhaseHandler().getTurn();
        assertTrue(pod.game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(pod.game)));
        settle(pod.game);
        assertEquals(pod.game.getPhaseHandler().getTurn(), turn + 1);
        assertEquals(pod.game.getPhaseHandler().getPlayerTurn(), pod.actor);
        shock(pod);
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Exile));
        assertEquals(pod.opponent.getLife(), 30);
    }
    @Test
    public void anActualCastPaysItsMandatorySacrificeCost() {
        Pod pod = pod(false, 8, "Altar's Reap");
        Card sacrifice = addCardToZone("Memnite", pod.actor, ZoneType.Battlefield);
        int library = pod.actor.getCardsIn(ZoneType.Library).size();
        shock(pod);
        assertTrue(pod.game.getCardState(sacrifice).isInZone(ZoneType.Graveyard));
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), library - 2);
        assertEquals(pod.opponent.getLife(), 38);
    }
    @Test
    public void aSacrificeCostProhibitionPreservesALaterCast() {
        Pod pod = pod(false, 8, "Altar's Reap");
        Card sacrifice = addCardToZone("Memnite", pod.actor, ZoneType.Battlefield);
        Card yasharn = addCardToZone("Yasharn, Implacable Earth", pod.game.getPlayers().get(2), ZoneType.Battlefield);
        pod.game.getAction().checkStateEffects(true);
        pod.game.getTriggerHandler().resetActiveTriggers();
        shock(pod);
        assertTrue(pod.game.getCardState(sacrifice).isInZone(ZoneType.Battlefield));
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Graveyard));
        remove(pod, yasharn);
        shock(pod);
        assertTrue(pod.game.getCardState(sacrifice).isInZone(ZoneType.Graveyard));
        assertTrue(pod.game.getCardState(pod.bolt).isInZone(ZoneType.Exile));
        assertEquals(pod.opponent.getLife(), 36);
    }
    private Pod snapshot(Pod original) {
        GameCopier copier = new GameCopier(original.game).withStack(true);
        Game copy = copier.makeCopy();
        assertTrue(copier.getUnmappedSnapshotCommands().isEmpty());
        Pod fork = new Pod(copy, copy.getPlayer(original.actor.getId()), copy.getPlayer(original.opponent.getId()),
                find(copy, original.host), find(copy, original.bolt),
                original.blocker == null ? null : find(copy, original.blocker),
                new boolean[]{false}, new boolean[]{false}, new int[]{0});
        control(fork);
        copy.getTriggerHandler().resetActiveTriggers();
        return fork;
    }
    private void remove(Pod pod, Card card) {
        SpellAbility spell = addCardToZone("Anguished Unmaking", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        spell.getTargets().add(card);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(card).isInZone(ZoneType.Exile));
    }
    private Card find(Game game, Card card) {
        return game.getCardsInGame().stream().filter(candidate -> candidate.getId() == card.getId())
                .findFirst().orElseThrow();
    }
    private void toTrigger(Pod pod) {
        SpellAbility spell = addCardToZone("Shock", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        spell.getTargets().add(pod.opponent);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 1);
        assertEquals(pod.game.getStack().peekAbility().getHostCard().getName(), "Emet-Selch of the Third Seat");
    }
    private void counterTop(Pod pod) {
        Player responder = pod.game.getPlayers().get(3);
        for (int index = 0; index < 3; index++) addCardToZone("Island", responder, ZoneType.Battlefield);
        SpellAbility spell = addCardToZone("Disallow", responder, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(responder);
        spell.getTargets().add(pod.game.getStack().peekAbility());
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, responder, pod.game));
    }
    private void queue(Game game) {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }
    private void control(Pod pod) {
        Game game = pod.game;
        Player actor = pod.actor;
        Player opponent = pod.opponent;
        Card host = pod.host;
        Card bolt = pod.bolt;
        boolean[] decline = pod.decline;
        boolean[] declineCast = pod.declineCast;
        int[] prompts = pod.prompts;
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public void orderAndPlaySimultaneousSa(List<SpellAbility> abilities) {
                for (SpellAbility ability : abilities) {
                    if (ability.getHostCard().getId() == host.getId()) {
                        ability.resetTargets();
                        Card target = game.getCardState(bolt);
                        assertTrue(ability.canTarget(target));
                        ability.getTargets().add(target);
                        assertTrue(ComputerUtil.playStack(ability, actor, game));
                    } else super.orderAndPlaySimultaneousSa(List.of(ability));
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
            @Override
            public boolean playSaFromPlayEffect(SpellAbility spell) {
                if (spell.getHostCard().getId() == bolt.getId()) {
                    if (declineCast[0]) return false;
                    spell.resetTargets();
                    if (spell.usesTargeting()) spell.getTargets().add(opponent);
                    return ComputerUtil.playStack(spell, actor, game);
                }
                return super.playSaFromPlayEffect(spell);
            }
        });
    }
    private void shock(Pod pod) {
        SpellAbility spell = addCardToZone("Shock", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        spell.getTargets().add(pod.opponent);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        settle(pod.game);
    }
    private Pod pod(boolean teferi) { return pod(teferi, 8); }
    private Pod pod(boolean teferi, int mountains) { return pod(teferi, mountains, "Lightning Bolt"); }
    private Pod pod(boolean teferi, int mountains, String replayName) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Optional graveyard casts"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        for (Player player : game.getPlayers()) {
            for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        }
        for (int i = 0; i < 8; i++) {
            if (i < mountains) addCardToZone("Mountain", actor, ZoneType.Battlefield);
            addCardToZone("Island", actor, ZoneType.Battlefield);
        }
        for (int i = 0; i < 2; i++) {
            addCardToZone("Plains", actor, ZoneType.Battlefield);
            addCardToZone("Swamp", actor, ZoneType.Battlefield);
        }
        Card host = addCardToZone("Emet-Selch of the Third Seat", actor, ZoneType.Battlefield);
        Card bolt = addCardToZone(replayName, actor, ZoneType.Graveyard);
        Card blocker = teferi ? addCardToZone("Teferi, Time Raveler", game.getPlayers().get(2), ZoneType.Battlefield) : null;
        if (blocker != null) blocker.setCounters(CounterEnumType.LOYALTY, 4);
        boolean[] decline = {false};
        boolean[] declineCast = {false};
        int[] prompts = {0};
        Pod pod = new Pod(game, actor, opponent, host, bolt, blocker, decline, declineCast, prompts);
        control(pod);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        return pod;
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
    private record Pod(Game game, Player actor, Player opponent, Card host, Card bolt, Card blocker,
            boolean[] decline, boolean[] declineCast, int[] prompts) {}
}
