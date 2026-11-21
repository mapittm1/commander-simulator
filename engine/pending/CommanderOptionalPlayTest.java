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
    private void shock(Pod pod) {
        SpellAbility spell = addCardToZone("Shock", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        spell.getTargets().add(pod.opponent);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        settle(pod.game);
    }
    private Pod pod(boolean teferi) {
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
            addCardToZone("Mountain", actor, ZoneType.Battlefield);
            addCardToZone("Island", actor, ZoneType.Battlefield);
        }
        for (int i = 0; i < 2; i++) {
            addCardToZone("Plains", actor, ZoneType.Battlefield);
            addCardToZone("Swamp", actor, ZoneType.Battlefield);
        }
        Card host = addCardToZone("Emet-Selch of the Third Seat", actor, ZoneType.Battlefield);
        Card bolt = addCardToZone("Lightning Bolt", actor, ZoneType.Graveyard);
        Card blocker = teferi ? addCardToZone("Teferi, Time Raveler", game.getPlayers().get(2), ZoneType.Battlefield) : null;
        if (blocker != null) blocker.setCounters(CounterEnumType.LOYALTY, 4);
        boolean[] decline = {false};
        boolean[] declineCast = {false};
        int[] prompts = {0};
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
                    spell.getTargets().add(opponent);
                    return ComputerUtil.playStack(spell, actor, game);
                }
                return super.playSaFromPlayEffect(spell);
            }
        });
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        return new Pod(game, actor, opponent, host, bolt, blocker, decline, declineCast, prompts);
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
