package forge.ai;

import forge.deck.Deck;
import forge.ai.simulation.GameCopier;
import forge.game.ability.ApiType;
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
    @Test
    public void choosingFiveForAnAlreadyFiveSpellUsesTheAllowance() {
        Pod pod=pod();
        Card card=addCardToZone("Endless One",pod.actor,ZoneType.Hand);
        SpellAbility spell=card.getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);spell.setXManaCostPaid(5);
        pod.game.copyLastState();assertTrue(ComputerUtil.playStack(spell,pod.actor,pod.game));
        settle(pod.game);
        assertEquals(pod.game.getCardState(card).getCounters(CounterEnumType.P1P1),5);
        assertEquals(next(pod).getCounters(CounterEnumType.P1P1),1);
        assertEquals(pod.prompts[0],1);
    }
    @Test
    public void anActivatedXAbilityCanBecomeFive() {
        Pod pod=pod();
        Card pinnacle=addCardToZone("Helix Pinnacle",pod.actor,ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        activate(pod,pinnacle);settle(pod.game);
        assertEquals(pinnacle.getCounters(CounterEnumType.TOWER),5);
        activate(pod,pinnacle,1);settle(pod.game);
        assertEquals(pinnacle.getCounters(CounterEnumType.TOWER),6);
        assertEquals(pod.prompts[0],1);
    }
    @Test
    public void counteringTheActivatedAbilityPreservesTheAllowance() {
        Pod pod=pod();
        Card pinnacle=addCardToZone("Helix Pinnacle",pod.actor,ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        activate(pod,pinnacle);
        SpellAbility incoming=null;
        for (var instance:pod.game.getStack()) {
            if (instance.getSpellAbility().getHostCard().getId()==pinnacle.getId()) incoming=instance.getSpellAbility();
        }
        assertTrue(incoming!=null && incoming.isActivatedAbility());
        Player responder=pod.game.getPlayers().get(1);
        SpellAbility counter=addCardToZone("Disallow",responder,ZoneType.Hand).getFirstSpellAbility();
        counter.setActivatingPlayer(responder);counter.getTargets().add(incoming);
        pod.game.copyLastState();assertTrue(ComputerUtil.playStack(counter,responder,pod.game));
        settle(pod.game);
        assertEquals(pinnacle.getCounters(CounterEnumType.TOWER),0);
        assertEquals(next(pod).getCounters(CounterEnumType.P1P1),5);
    }
    @Test
    public void mandatoryUnboundFlourishingStillDoublesX() {
        Pod pod=pod();pod.decline[0]=true;
        addCardToZone("Unbound Flourishing",pod.actor,ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        Card card=addCardToZone("Endless One",pod.actor,ZoneType.Hand);
        SpellAbility spell=card.getFirstSpellAbility();spell.setActivatingPlayer(pod.actor);spell.setXManaCostPaid(1);
        pod.game.copyLastState();assertTrue(ComputerUtil.playStack(spell,pod.actor,pod.game));
        settle(pod.game);
        assertEquals(pod.game.getCardState(card).getCounters(CounterEnumType.P1P1),2);
        assertEquals(pod.prompts[0],1);
    }
    @Test
    public void removingGlavaDoesNotStopItsQueuedChange() {
        Pod pod = pod();
        Card first = cast(pod);
        Player opponent = pod.game.getPlayers().get(1);
        addCardToZone("Plains", opponent, ZoneType.Battlefield);
        SpellAbility removal = addCardToZone("Swords to Plowshares", opponent, ZoneType.Hand).getFirstSpellAbility();
        removal.setActivatingPlayer(opponent);
        removal.getTargets().add(pod.host);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(removal, opponent, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.host).isInZone(ZoneType.Exile));
        assertEquals(pod.game.getCardState(first).getCounters(CounterEnumType.P1P1), 5);
        assertEquals(pod.prompts[0], 1);
    }

    @Test
    public void returningGlavaGivesTheNewObjectItsOwnAllowance() {
        Pod pod = pod();
        Card first = cast(pod);
        long stamp = pod.host.getGameTimestamp();
        SpellAbility flux = addCardToZone("Essence Flux", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        flux.setActivatingPlayer(pod.actor);
        flux.getTargets().add(pod.host);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(flux, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.host).getGameTimestamp() != stamp);
        assertEquals(pod.game.getCardState(first).getCounters(CounterEnumType.P1P1), 5);
        assertEquals(next(pod).getCounters(CounterEnumType.P1P1), 5);
        assertEquals(next(pod).getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void copiedGlavaTriggersUseTheAllowanceOnlyOnce() {
        Pod pod = pod();
        Card first = cast(pod);
        resonate(pod, pod.game.getStack().peekAbility());
        assertEquals(pod.game.getStack().size(), 3);
        settle(pod.game);
        assertEquals(pod.game.getCardState(first).getCounters(CounterEnumType.P1P1), 5);
        assertEquals(pod.prompts[0], 1);
        assertEquals(next(pod).getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void queuedXChangesRemainIndependentInCopiedGames() {
        Pod pod = pod();
        Card first = cast(pod);
        Pod fork = snapshot(pod);
        settle(fork.game);
        assertEquals(fork.game.getCardState(first).getCounters(CounterEnumType.P1P1), 5);
        assertEquals(pod.prompts[0], 0);
        settle(pod.game);
        assertEquals(pod.game.getCardState(first).getCounters(CounterEnumType.P1P1), 5);
        assertEquals(next(fork).getCounters(CounterEnumType.P1P1), 1);
        assertEquals(next(pod).getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void absentSpellHistoryRemainsUnusedInCopiedGames() {
        Pod pod = pod();
        Card first = cast(pod);
        Player opponent = pod.game.getPlayers().get(1);
        SpellAbility counter = addCardToZone("Counterspell", opponent, ZoneType.Hand).getFirstSpellAbility();
        counter.setActivatingPlayer(opponent);
        counter.getTargets().add(incoming(pod, first));
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(counter, opponent, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertTrue(pod.game.getCardState(first).isInZone(ZoneType.Graveyard));
        Pod fork = snapshot(pod);
        settle(fork.game);
        settle(pod.game);
        assertEquals(next(fork).getCounters(CounterEnumType.P1P1), 5);
        assertEquals(next(pod).getCounters(CounterEnumType.P1P1), 5);
    }

    @Test
    public void usedXHistoryRemainsUsedInCopiedGames() {
        Pod pod = pod();
        assertEquals(next(pod).getCounters(CounterEnumType.P1P1), 5);
        Pod fork = snapshot(pod);
        assertEquals(next(fork).getCounters(CounterEnumType.P1P1), 1);
        assertEquals(next(pod).getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void nativeCleanupResetsTheAllowanceForAnExtraTurn() {
        Pod pod = pod();
        assertEquals(next(pod).getCounters(CounterEnumType.P1P1), 5);
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
        assertEquals(next(pod).getCounters(CounterEnumType.P1P1), 5);
    }

    @Test
    public void copiedOldTriggerCannotFollowARemandedAndRecastSpell() {
        Pod pod = pod();
        Card first = cast(pod);
        SpellAbility oldTrigger = pod.game.getStack().peekAbility();
        Player opponent = pod.game.getPlayers().get(1);
        SpellAbility remand = addCardToZone("Remand", opponent, ZoneType.Hand).getFirstSpellAbility();
        remand.setActivatingPlayer(opponent);
        remand.getTargets().add(incoming(pod, first));
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(remand, opponent, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        Card returned = pod.game.getCardState(first);
        assertTrue(returned.isInZone(ZoneType.Hand));
        addCardToZone("Vedalken Orrery", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        SpellAbility recast = returned.getFirstSpellAbility();
        recast.setActivatingPlayer(pod.actor);
        recast.setXManaCostPaid(1);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(recast, pod.actor, pod.game));
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 3);
        resonate(pod, oldTrigger);
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertEquals(pod.prompts[0], 0, "The copied old trigger has no spell left to change");
        assertEquals(incoming(pod, returned).getXManaCostPaid(), Integer.valueOf(1));
        settle(pod.game);
        assertEquals(pod.game.getCardState(returned).getCounters(CounterEnumType.P1P1), 5);
        assertEquals(pod.prompts[0], 1);
    }

    private SpellAbility incoming(Pod pod, Card card) {
        for (var instance : pod.game.getStack()) {
            SpellAbility ability = instance.getSpellAbility();
            if (ability.isSpell() && ability.getHostCard().getId() == card.getId()) {
                return ability;
            }
        }
        throw new AssertionError("Expected the actual spell on the stack");
    }

    private void resonate(Pod pod, SpellAbility target) {
        Card resonator = addCardToZone("Strionic Resonator", pod.actor, ZoneType.Battlefield);
        SpellAbility ability = resonator.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.CopySpellAbility)
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(pod.actor);
        ability.getTargets().add(target);
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
        Pod fork = new Pod(copy, copy.getPlayer(original.actor.getId()), host, new int[]{0}, new boolean[]{false});
        control(fork);
        copy.getTriggerHandler().resetActiveTriggers();
        return fork;
    }

    private void activate(Pod pod,Card card) { activate(pod,card,2); }
    private void activate(Pod pod,Card card,int expectedStackSize) {
        SpellAbility ability=card.getSpellAbilities().stream().filter(sa -> sa.isActivatedAbility() && !sa.isManaAbility()).findFirst().orElseThrow();
        ability.setActivatingPlayer(pod.actor);ability.setXManaCostPaid(1);
        pod.game.copyLastState();assertTrue(ComputerUtil.playStack(ability,pod.actor,pod.game));
        queue(pod.game);assertEquals(pod.game.getStack().size(),expectedStackSize);
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
