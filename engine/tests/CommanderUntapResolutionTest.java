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
import forge.game.player.PlayerController.BinaryChoiceType;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Paid native resolution and per-target controller defaults; supplied choices are explicit controls. */
public class CommanderUntapResolutionTest extends AITest {
    @Test public void aSuppliedUntapChoiceActuallyUntapsTheTarget() {
        Pod pod = pod("Gilded Lotus", false, true);
        UntapController choices = new UntapController(pod);
        pod.actor.runWithController(() -> cast(pod), choices);
        assertEquals(choices.calls, 1);
        assertFalse(pod.target.isTapped());
    }
    @Test public void theDefaultDoesNotTapAnAlreadyReadyOwnCreature() {
        Pod pod = pod("Grizzly Bears", false, false);
        cast(pod);
        assertFalse(pod.target.isTapped());
    }
    @Test public void theDefaultTapsAnOpposingReadyCreature() {
        Pod pod = pod("Grizzly Bears", true, false);
        cast(pod);
        assertTrue(pod.target.isTapped());
    }
    @Test public void tappingAnAlreadyTappedOpponentDoesNotUntapIt() {
        Pod pod = pod("Grizzly Bears", true, true);
        cast(pod);
        assertTrue(pod.target.isTapped());
    }
    @Test public void anOpposingUntapTriggersOnlyItsControllersWakeThrasher() {
        Pod pod = pod("Island", true, true);
        Card ownWake = addCardToZone("Wake Thrasher", pod.actor, ZoneType.Battlefield);
        Card otherWake = addCardToZone("Wake Thrasher", pod.enemy, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        UntapController choices = new UntapController(pod);
        pod.actor.runWithController(() -> cast(pod), choices);
        assertFalse(pod.target.isTapped());
        assertEquals(ownWake.getNetPower(), 1);
        assertEquals(otherWake.getNetPower(), 2);
    }
    @Test public void aStunCounterIsRemovedInsteadOfUntapping() {
        Pod pod = pod("Grizzly Bears", false, true);
        pod.target.setCounters(CounterEnumType.STUN, 1);
        UntapController choices = new UntapController(pod);
        pod.actor.runWithController(() -> cast(pod), choices);
        assertTrue(pod.target.isTapped());
        assertEquals(pod.target.getCounters(CounterEnumType.STUN), 0);
    }
    @Test public void stasisDoesNotStopASpellFromUntapping() {
        Pod pod = pod("Gilded Lotus", false, true);
        addCardToZone("Stasis", pod.enemy, ZoneType.Battlefield);
        pod.game.getAction().checkStateEffects(true);
        UntapController choices = new UntapController(pod);
        pod.actor.runWithController(() -> cast(pod), choices);
        assertFalse(pod.target.isTapped());
    }
    @Test public void aReturnedObjectIsNotTheOriginalTarget() {
        Pod pod = pod("Gilded Lotus", false, true);
        UntapController choices = new UntapController(pod);
        pod.actor.runWithController(() -> {
            assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
            Card moved = pod.game.getAction().moveToGraveyard(pod.target, null);
            Card returned = pod.game.getAction().moveToPlay(moved, pod.actor, null, null);
            returned.setTapped(true);
            settle(pod.game);
            assertTrue(returned.isTapped());
        }, choices);
        assertEquals(choices.calls, 0);
    }
    private void cast(Pod pod) {
        assertTrue(pod.action.canTarget(pod.target));
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
        settle(pod.game);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 1L);
        assertTrue(pod.game.getCardState(pod.spell).isInZone(ZoneType.Graveyard));
    }
    private Pod pod(String targetName, boolean opposing, boolean tapped) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("untap-rules-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Tap-or-untap resolution controls"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        Card target = addCardToZone(targetName, opposing ? enemy : actor, ZoneType.Battlefield);
        target.setTapped(tapped);
        Card spell = addCardToZone("Twiddle", actor, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        SpellAbility action = spell.getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        action.getTargets().add(target);
        return new Pod(game, actor, enemy, target, spell, action);
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
        throw new AssertionError("Untap rules stack did not settle");
    }
    private static class UntapController extends PlayerControllerAi {
        private int calls;
        private UntapController(Pod pod) { super(pod.game, pod.actor, pod.actor.getLobbyPlayer()); }
        @Override public boolean chooseBinary(SpellAbility action, String question, BinaryChoiceType kind, Boolean defaultValue) {
            if (kind == BinaryChoiceType.TapOrUntap) { calls++; return false; }
            return super.chooseBinary(action, question, kind, defaultValue);
        }
    }
    private record Pod(Game game, Player actor, Player enemy, Card target, Card spell, SpellAbility action) { }
}
