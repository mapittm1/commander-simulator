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
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Native paid Hidden Strings resolution with independent target validity and stun replacement. */
public class CommanderChainedUntapTest extends AITest {
    @Test public void bothLegalTargetsUntap() {
        Pod pod = pod(); cast(pod); settle(pod.game);
        assertFalse(pod.first.isTapped()); assertFalse(pod.second.isTapped()); assertPaid(pod);
    }
    @Test public void anInvalidFirstTargetDoesNotStopTheSecondUntap() {
        Pod pod = pod(); cast(pod);
        pod.game.getAction().moveToGraveyard(pod.first, null); settle(pod.game);
        assertFalse(pod.second.isTapped()); assertPaid(pod);
    }
    @Test public void anInvalidSecondTargetDoesNotStopTheFirstUntap() {
        Pod pod = pod(); cast(pod);
        pod.game.getAction().moveToGraveyard(pod.second, null); settle(pod.game);
        assertFalse(pod.first.isTapped()); assertPaid(pod);
    }
    @Test public void twoInvalidTargetsDoNotUntapDepartedObjects() {
        Pod pod = pod(); cast(pod);
        Card first = pod.game.getAction().moveToGraveyard(pod.first, null);
        Card second = pod.game.getAction().moveToGraveyard(pod.second, null); settle(pod.game);
        assertTrue(first.isInZone(ZoneType.Graveyard)); assertTrue(second.isInZone(ZoneType.Graveyard));
        assertPaid(pod);
    }
    @Test public void aReturnedFirstTargetIsNewButTheSecondStillUntaps() {
        Pod pod = pod(); cast(pod); Card returned = returnTapped(pod, pod.first); settle(pod.game);
        assertTrue(returned.isTapped()); assertFalse(pod.second.isTapped()); assertPaid(pod);
    }
    @Test public void aReturnedSecondTargetIsNewButTheFirstStillUntaps() {
        Pod pod = pod(); cast(pod); Card returned = returnTapped(pod, pod.second); settle(pod.game);
        assertTrue(returned.isTapped()); assertFalse(pod.first.isTapped()); assertPaid(pod);
    }
    @Test public void stunOnTheFirstTargetDoesNotReplaceTheSecondUntap() {
        Pod pod = pod(); pod.first.setCounters(CounterEnumType.STUN, 1); cast(pod); settle(pod.game);
        assertTrue(pod.first.isTapped()); assertEquals(pod.first.getCounters(CounterEnumType.STUN), 0);
        assertFalse(pod.second.isTapped()); assertPaid(pod);
    }
    @Test public void stunOnTheSecondTargetDoesNotReplaceTheFirstUntap() {
        Pod pod = pod(); pod.second.setCounters(CounterEnumType.STUN, 1); cast(pod); settle(pod.game);
        assertTrue(pod.second.isTapped()); assertEquals(pod.second.getCounters(CounterEnumType.STUN), 0);
        assertFalse(pod.first.isTapped()); assertPaid(pod);
    }
    private Card returnTapped(Pod pod, Card target) {
        Card moved = pod.game.getAction().moveToGraveyard(target, null);
        Card returned = pod.game.getAction().moveToPlay(moved, pod.actor, null, null);
        returned.setTapped(true); return returned;
    }
    private void cast(Pod pod) {
        assertTrue(pod.action.canPlay()); assertTrue(pod.action.isTargetNumberValid());
        assertTrue(pod.action.getSubAbility().isTargetNumberValid());
        assertNotEquals(pod.action.getTargets().getFirstTargetedCard().getId(), pod.action.getSubAbility().getTargets().getFirstTargetedCard().getId());
        assertTrue(ComputerUtil.playStack(pod.action, pod.actor, pod.game));
    }
    private void assertPaid(Pod pod) {
        assertEquals(pod.actor.getSpellsCastThisTurn(), 1);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 2L);
        assertTrue(pod.game.getCardState(pod.spell).isInZone(ZoneType.Graveyard));
    }
    private Pod pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("chained-untap-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Independent chained untap targets"));
        game.setNoGUIUser(); game.setAge(GameStage.Play); Player actor = game.getPlayers().get(0);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        addCardToZone("Island", actor, ZoneType.Battlefield); addCardToZone("Wastes", actor, ZoneType.Battlefield);
        Card first = addCardToZone("Gilded Lotus", actor, ZoneType.Battlefield); first.setTapped(true);
        Card second = addCardToZone("Sol Ring", actor, ZoneType.Battlefield); second.setTapped(true);
        Card spell = addCardToZone("Hidden Strings", actor, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        SpellAbility action = spell.getFirstSpellAbility(); action.setActivatingPlayer(actor);
        action.getTargets().add(first); action.getSubAbility().getTargets().add(second);
        return new Pod(game, actor, first, second, spell, action);
    }
    private void settle(Game game) {
        for (int i = 0; i < 40; i++) {
            game.getAction().checkStateEffects(true); game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers(); game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return; game.getStack().resolveStack();
        }
        throw new AssertionError("Chained untap stack did not settle");
    }
    private record Pod(Game game, Player actor, Card first, Card second, Card spell, SpellAbility action) { }
}
