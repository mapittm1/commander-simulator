package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.GameEntityCounterTable;
import forge.game.ability.AbilityKey;
import forge.game.card.CardDamageTable;
import forge.game.trigger.TriggerType;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.GameLossReason;
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

/** Research witnesses with supplied decisions and native payment/draw resolution. */
public class CommanderOptionalDrawTest extends AITest {
    @Test
    public void narsetPreservesAForbiddenOptionalDraw() {
        verifyDraw("Narset, Parter of Veils", true, false);
    }
    @Test
    public void spiritPreservesAForbiddenOptionalDraw() {
        verifyDraw("Spirit of the Labyrinth", true, false);
    }
    @Test
    public void aDrawReplacedByNotionThiefUsesTheAction() {
        verifyDraw("Notion Thief", false, true);
    }
    @Test
    public void anOrdinaryDrawUsesTheAction() {
        verifyDraw(null, false, false);
    }

    @Test
    public void anEmptyLibraryDoesNotMakeChoosingToDrawIllegal() {
        Pod pod = pod(null, true);
        assertTrue(pod.actor.canDraw());
        counter(pod);
        assertEquals(pod.prompts[0], 1);
        pod.game.getAction().checkStateEffects(true);
        assertTrue(pod.actor.hasLost());
        assertEquals(pod.actor.getOutcome().lossState, GameLossReason.Milled);
    }

    @Test
    public void narsetForbidsChoosingTwoCardsWhenOnlyOneCouldBeDrawn() {
        verifyWholeDraw("Narset, Parter of Veils");
    }

    @Test
    public void spiritForbidsChoosingTwoCardsWhenOnlyOneCouldBeDrawn() {
        verifyWholeDraw("Spirit of the Labyrinth");
    }

    private void verifyWholeDraw(String blocker) {
        Pod pod = pod(blocker, false, false, "Chekov and McCoy");
        assertTrue(pod.actor.canDraw());
        assertTrue(!pod.actor.canDraw(2));
        boon(pod);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), 0, "The optional two-card action is impossible");
        assertEquals(pod.prompts[0], 0);
        removeBlocker(pod);
        counter(pod);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), 1, "The later legal one-card action remains available");
        counter(pod);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), 1);
    }

    @Test
    public void anOrdinaryTwoCardDrawUsesOneAction() {
        verifyTwoCards(null, false);
    }

    @Test
    public void aReplacedTwoCardDrawUsesOneAction() {
        verifyTwoCards("Notion Thief", true);
    }

    private void verifyTwoCards(String blocker, boolean replaced) {
        Pod pod = pod(blocker, false, false, "Chekov and McCoy");
        boon(pod);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), replaced ? 0 : 2);
        if (replaced) {
            assertEquals(pod.game.getPlayers().get(2).getCardsIn(ZoneType.Hand).size(), 2);
        }
        removeBlocker(pod);
        counter(pod);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), replaced ? 0 : 2);
        assertEquals(pod.prompts[0], 1);
    }

    @Test
    public void decliningLeavesTheDrawActionAvailable() {
        Pod pod = pod(null, false);
        pod.decline[0] = true;
        counter(pod);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), 1);
        pod.decline[0] = false;
        counter(pod);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), 2);
        counter(pod);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), 2);
        assertEquals(pod.prompts[0], 2);
    }

    @Test
    public void counteringTheTriggerLeavesTheDrawActionAvailable() {
        Pod pod = pod(null, false);
        SpellAbility veil = addCardToZone("Snakeskin Veil", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        veil.setActivatingPlayer(pod.actor);
        veil.getTargets().add(pod.host);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(veil, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 1);
        assertEquals(pod.game.getStack().peekAbility().getHostCard(), pod.host);
        Player responder = pod.game.getPlayers().get(2);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Island", responder, ZoneType.Battlefield);
        }
        SpellAbility disallow = addCardToZone("Disallow", responder, ZoneType.Hand).getFirstSpellAbility();
        disallow.setActivatingPlayer(responder);
        disallow.getTargets().add(pod.game.getStack().peekAbility());
        assertTrue(ComputerUtil.playStack(disallow, responder, pod.game));
        settle(pod.game);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), 1);
        assertEquals(pod.prompts[0], 0);
        counter(pod);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), 2);
    }

    @Test
    public void tymnaCanPayLifeEvenWhenTheResultingDrawIsForbidden() {
        Pod pod = pod("Narset, Parter of Veils", false, true, "Tymna the Weaver");
        Card attacker = addCardToZone("Grizzly Bears", pod.actor, ZoneType.Battlefield);
        CardDamageTable dealt = new CardDamageTable();
        dealt.put(attacker, pod.game.getPlayers().get(1), 1);
        pod.game.getAction().dealDamage(true, dealt, new CardDamageTable(), new GameEntityCounterTable(), null);
        assertEquals(pod.game.getPlayers().get(1).getLife(), 39);
        pod.game.getPhaseHandler().devModeSet(PhaseType.MAIN2, pod.actor, 2);
        pod.game.getTriggerHandler().resetActiveTriggers();
        pod.game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(pod.actor), false);
        settle(pod.game);
        assertEquals(pod.actor.getLife(), 39, "Choosing to pay life remains legal");
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), 1);
        assertEquals(pod.prompts[0], 1);
    }

    private void removeBlocker(Pod pod) {
        if (pod.blocker == null) {
            return;
        }
        SpellAbility removal = addCardToZone("Anguished Unmaking", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        removal.setActivatingPlayer(pod.actor);
        removal.getTargets().add(pod.blocker);
        assertTrue(ComputerUtil.playStack(removal, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.blocker).isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getLife(), 37);
    }

    private void boon(Pod pod) {
        SpellAbility spell = addCardToZone("Dragonscale Boon", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.actor);
        spell.getTargets().add(pod.host);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.actor, pod.game));
        settle(pod.game);
    }

    private void verifyDraw(String blockerName, boolean prohibited, boolean replaced) {
        Pod pod = pod(blockerName, false);
        assertEquals(pod.actor.canDraw(), !prohibited);
        int firstHand = pod.actor.getCardsIn(ZoneType.Hand).size();
        int thiefHand = pod.game.getPlayers().get(2).getCardsIn(ZoneType.Hand).size();
        counter(pod);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), firstHand + (prohibited || replaced ? 0 : 1));
        if (replaced) {
            assertEquals(pod.game.getPlayers().get(2).getCardsIn(ZoneType.Hand).size(), thiefHand + 1);
        }
        if (pod.blocker != null) {
            SpellAbility removal = addCardToZone("Anguished Unmaking", pod.actor, ZoneType.Hand).getFirstSpellAbility();
            removal.setActivatingPlayer(pod.actor);
            removal.getTargets().add(pod.blocker);
            assertTrue(ComputerUtil.playStack(removal, pod.actor, pod.game));
            settle(pod.game);
            assertTrue(pod.game.getCardState(pod.blocker).isInZone(ZoneType.Exile));
            assertEquals(pod.actor.getLife(), 37);
        }
        assertTrue(pod.actor.canDraw());
        int before = pod.actor.getCardsIn(ZoneType.Hand).size();
        counter(pod);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), before + (prohibited ? 1 : 0),
                "A forbidden draw must not spend the once-per-turn action");
        before = pod.actor.getCardsIn(ZoneType.Hand).size();
        counter(pod);
        assertEquals(pod.actor.getCardsIn(ZoneType.Hand).size(), before);
        assertEquals(pod.game.getCardState(pod.host).getCounters(CounterEnumType.P1P1), 3);
    }

    private Pod pod(String blockerName, boolean emptyLibrary) {
        return pod(blockerName, emptyLibrary, !emptyLibrary, "Chekov and McCoy");
    }

    private Pod pod(String blockerName, boolean emptyLibrary, boolean primeDraw, String hostName) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Optional draw"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < (emptyLibrary && player.equals(actor) ? 0 : 8); index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        for (String name : List.of("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Swamp", "Plains", "Plains", "Island")) {
            addCardToZone(name, actor, ZoneType.Battlefield);
        }
        Card blocker = blockerName == null ? null : addCardToZone(blockerName, game.getPlayers().get(2), ZoneType.Battlefield);
        if (blocker != null && blocker.isPlaneswalker()) {
            blocker.setCounters(CounterEnumType.LOYALTY, 5);
        }
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        if (primeDraw && !emptyLibrary) {
            SpellAbility opt = addCardToZone("Opt", actor, ZoneType.Hand).getFirstSpellAbility();
            opt.setActivatingPlayer(actor);
            assertTrue(ComputerUtil.playStack(opt, actor, game));
            settle(game);
        }
        Card host = addCardToZone(hostName, actor, ZoneType.Battlefield);
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

    private void counter(Pod pod) {
        SpellAbility veil = addCardToZone("Snakeskin Veil", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        veil.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        veil.getTargets().add(pod.game.getCardState(pod.host));
        assertTrue(ComputerUtil.playStack(veil, pod.actor, pod.game));
        settle(pod.game);
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
