package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
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

    private void bear(Pod pod) {
        SpellAbility spell = addCardToZone("Grizzly Bears", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        settle(pod.game);
    }

    private void remove(Pod pod) {
        SpellAbility spell = addCardToZone("Anguished Unmaking", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        spell.getTargets().add(pod.blocker);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.blocker).isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getLife(), 38);
    }

    private Pod pod(String blockerName) {
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
        Card host = addCardToZone("Nykthos Paragon", actor, ZoneType.Battlefield);
        addCardToZone("Essence Warden", actor, ZoneType.Battlefield);
        int[] prompts = {0};
        boolean[] decline = {false};
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
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        return new Pod(game, actor, host, blocker, prompts, decline);
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
