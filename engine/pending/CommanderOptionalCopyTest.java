package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.GameEntityCounterTable;
import forge.game.card.CardDamageTable;
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

/** Research witnesses with supplied decisions and native payment/draw resolution. */
public class CommanderOptionalCopyTest extends AITest {
    @Test
    public void noEligibleEnchantmentPreservesTheCopyAction() {
        Pod pod = pod(null, false, false, "Calix, Guided by Fate");
        damage(pod);
        assertEquals(anthems(pod), 0);
        addCardToZone("Glorious Anthem", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        damage(pod);
        assertEquals(anthems(pod), 2, "The later eligible copy remains available");
        damage(pod);
        assertEquals(anthems(pod), 2);
    }

    @Test
    public void anEligibleCopyUsesTheAction() {
        Pod pod = pod(null, false, false, "Calix, Guided by Fate");
        addCardToZone("Glorious Anthem", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        damage(pod);
        assertEquals(anthems(pod), 2);
        damage(pod);
        assertEquals(anthems(pod), 2);
    }

    @Test
    public void decliningPreservesTheCopyAction() {
        Pod pod = pod(null, false, false, "Calix, Guided by Fate");
        addCardToZone("Glorious Anthem", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        pod.decline[0] = true;
        damage(pod);
        assertEquals(anthems(pod), 1);
        pod.decline[0] = false;
        damage(pod);
        assertEquals(anthems(pod), 2);
    }

    private long anthems(Pod pod) {
        return pod.actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Glorious Anthem")).count();
    }

    private void damage(Pod pod) {
        pod.game.copyLastState();
        CardDamageTable dealt = new CardDamageTable();
        dealt.put(pod.host, pod.game.getPlayers().get(1), 1);
        pod.game.getAction().dealDamage(true, dealt, new CardDamageTable(), new GameEntityCounterTable(), null);
        settle(pod.game);
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
        Game game = new Game(seats, rules, new Match(rules, seats, "Optional copy"));
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
