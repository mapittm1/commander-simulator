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

/** Supplied state and choices; native paid casts and responses. */
public class CommanderOptionalXTest extends AITest {
    @Test
    public void ordinaryChoiceUsesTheAllowance() {
        Pod pod=pod(); Card first=cast(pod); settle(pod.game);
        assertEquals(pod.game.getCardState(first).getCounters(CounterEnumType.P1P1),5);
        assertEquals(next(pod).getCounters(CounterEnumType.P1P1),1);
        assertEquals(pod.prompts[0],1);
    }
    @Test
    public void decliningPreservesTheAllowance() {
        Pod pod=pod(); pod.decline[0]=true;
        Card first=cast(pod); settle(pod.game);
        assertEquals(pod.game.getCardState(first).getCounters(CounterEnumType.P1P1),1);
        pod.decline[0]=false;
        assertEquals(next(pod).getCounters(CounterEnumType.P1P1),5);
        assertEquals(pod.prompts[0],2);
    }
    @Test
    public void counteringTheSpellPreservesTheAllowance() {
        Pod pod=pod(); Card first=cast(pod);
        Player responder=pod.game.getPlayers().get(1);
        SpellAbility counter=addCardToZone("Counterspell",responder,ZoneType.Hand).getFirstSpellAbility();
        counter.setActivatingPlayer(responder);
        SpellAbility incoming=null;
        for (var instance:pod.game.getStack()) {
            if (instance.getSpellAbility().isSpell() && instance.getSpellAbility().getHostCard().getId()==first.getId()) {
                incoming=instance.getSpellAbility();
            }
        }
        assertTrue(incoming != null && incoming.isSpell());
        counter.getTargets().add(incoming);
        pod.game.copyLastState(); assertTrue(ComputerUtil.playStack(counter,responder,pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(first).isInZone(ZoneType.Graveyard));
        assertEquals(next(pod).getCounters(CounterEnumType.P1P1),5,
                "An absent spell cannot have X changed and must preserve the optional allowance");
    }
    @Test
    public void counteringTheTriggerPreservesTheAllowance() {
        Pod pod=pod(); Card first=cast(pod);
        Player responder=pod.game.getPlayers().get(1);
        SpellAbility counter=addCardToZone("Disallow",responder,ZoneType.Hand).getFirstSpellAbility();
        counter.setActivatingPlayer(responder); counter.getTargets().add(pod.game.getStack().peekAbility());
        pod.game.copyLastState(); assertTrue(ComputerUtil.playStack(counter,responder,pod.game));
        settle(pod.game);
        assertEquals(pod.game.getCardState(first).getCounters(CounterEnumType.P1P1),1);
        assertEquals(next(pod).getCounters(CounterEnumType.P1P1),5);
    }
    private Pod pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat=0; seat<4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("seat"+seat,null)));
        }
        GameRules rules=new GameRules(GameType.Commander);
        Game game=new Game(seats,rules,new Match(rules,seats,"Optional X"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        Player actor=game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1,actor,2);
        for (Player player:game.getPlayers()) {
            for (int i=0;i<8;i++) addCardToZone("Forest",player,ZoneType.Library);
            for (int i=0;i<12;i++) addCardToZone("Island",player,ZoneType.Battlefield);
        }
        Card host=addCardToZone("Glava, Five-Advents Mage",actor,ZoneType.Battlefield);
        Pod pod=new Pod(game,actor,host,new int[]{0},new boolean[]{false});
        control(pod); game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers(); game.copyLastState(); return pod;
    }
    private Card cast(Pod pod) {
        Card card=addCardToZone("Endless One",pod.actor,ZoneType.Hand);
        SpellAbility spell=card.getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor); spell.setXManaCostPaid(1);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell,pod.actor,pod.game));
        queue(pod.game);
        assertEquals(pod.game.getStack().size(),2);
        return card;
    }
    private Card next(Pod pod) {
        Card card=addCardToZone("Endless One",pod.actor,ZoneType.Hand);
        SpellAbility spell=card.getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor); spell.setXManaCostPaid(1);
        pod.game.copyLastState(); assertTrue(ComputerUtil.playStack(spell,pod.actor,pod.game));
        settle(pod.game); return pod.game.getCardState(card);
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
    private record Pod(Game game,Player actor,Card host,int[] prompts,boolean[] decline) {}
}
