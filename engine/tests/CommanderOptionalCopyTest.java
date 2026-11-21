package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEntityCounterTable;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardDamageTable;
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

/** Partial-state copy witnesses with supplied decisions and native damage/payment resolution. */
public class CommanderOptionalCopyTest extends AITest {
    @Test
    public void noEligibleEnchantmentPreservesTheCopyAction() {
        Pod pod = pod("Calix, Guided by Fate");
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
        Pod pod = pod("Calix, Guided by Fate");
        addCardToZone("Glorious Anthem", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        damage(pod);
        assertEquals(anthems(pod), 2);
        damage(pod);
        assertEquals(anthems(pod), 2);
    }

    @Test
    public void decliningPreservesTheCopyAction() {
        Pod pod = pod("Calix, Guided by Fate");
        addCardToZone("Glorious Anthem", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        pod.decline[0] = true;
        damage(pod);
        assertEquals(anthems(pod), 1);
        pod.decline[0] = false;
        damage(pod);
        assertEquals(anthems(pod), 2);
    }

    @Test
    public void anOpponentsEnchantmentIsNotAnEligibleCopy() {
        Pod pod = pod("Calix, Guided by Fate");
        addCardToZone("Glorious Anthem", pod.game.getPlayers().get(1), ZoneType.Battlefield);
        damage(pod);
        assertEquals(anthems(pod), 0);
        addCardToZone("Glorious Anthem", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        damage(pod);
        assertEquals(anthems(pod), 2);
    }

    @Test
    public void aNonenchantmentDoesNotUseTheCopyAction() {
        Pod pod = pod("Calix, Guided by Fate");
        addCardToZone("Grizzly Bears", pod.actor, ZoneType.Battlefield);
        damage(pod);
        addCardToZone("Glorious Anthem", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        damage(pod);
        assertEquals(anthems(pod), 2);
    }

    @Test
    public void removingTheOnlyChoiceBeforeResolutionPreservesTheAction() {
        Pod pod = pod("Calix, Guided by Fate");
        Card anthem = addCardToZone("Glorious Anthem", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        queueDamage(pod, true);
        assertEquals(pod.game.getStack().size(), 1);
        SpellAbility removal = addCardToZone("Anguished Unmaking", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        removal.setActivatingPlayer(pod.actor);
        removal.getTargets().add(anthem);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(removal, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(anthem).isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getLife(), 37);
        addCardToZone("Glorious Anthem", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        damage(pod);
        assertEquals(anthems(pod), 2);
    }

    @Test
    public void aCounteredTriggerPreservesTheCopyAction() {
        Pod pod = pod("Calix, Guided by Fate");
        addCardToZone("Glorious Anthem", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        queueDamage(pod, true);
        assertEquals(pod.game.getStack().size(), 1);
        Player responder = pod.game.getPlayers().get(2);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Island", responder, ZoneType.Battlefield);
        }
        SpellAbility disallow = addCardToZone("Disallow", responder, ZoneType.Hand).getFirstSpellAbility();
        disallow.setActivatingPlayer(responder);
        disallow.getTargets().add(pod.game.getStack().peekAbility());
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(disallow, responder, pod.game));
        settle(pod.game);
        assertEquals(anthems(pod), 1);
        assertEquals(pod.prompts[0], 0);
        damage(pod);
        assertEquals(anthems(pod), 2);
    }

    @Test
    public void doubledTokensStillUseOneCopyAction() {
        Pod pod = pod("Calix, Guided by Fate");
        addCardToZone("Anointed Procession", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        damage(pod);
        assertEquals(processionCount(pod), 3);
        damage(pod);
        assertEquals(processionCount(pod), 3);
        assertEquals(pod.prompts[0], 1);
    }

    @Test
    public void noncombatDamageDoesNotUseTheCopyAction() {
        Pod pod = pod("Calix, Guided by Fate");
        addCardToZone("Glorious Anthem", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        queueDamage(pod, false);
        settle(pod.game);
        assertEquals(anthems(pod), 1);
        assertEquals(pod.prompts[0], 0);
        damage(pod);
        assertEquals(anthems(pod), 2);
    }

    private long processionCount(Pod pod) {
        return pod.actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Anointed Procession")).count();
    }

    private void queueDamage(Pod pod, boolean combat) {
        pod.game.copyLastState();
        CardDamageTable dealt = new CardDamageTable();
        dealt.put(pod.host, pod.game.getPlayers().get(1), 1);
        pod.game.getAction().dealDamage(combat, dealt, new CardDamageTable(), new GameEntityCounterTable(), null);
        queue(pod.game);
    }

    @Test
    public void onduCopiesLastKnownEnchantmentsAfterTheyLeave() {
        Pod pod = pod("Ondu Spiritdancer");
        addCardToZone("Plains", pod.actor, ZoneType.Battlefield);
        Card anthem = addCardToZone("Glorious Anthem", pod.actor, ZoneType.Hand);
        SpellAbility cast = anthem.getFirstSpellAbility();
        cast.setActivatingPlayer(pod.actor);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(cast, pod.actor, pod.game));
        pod.game.getStack().resolveStack();
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 1);
        SpellAbility removal = addCardToZone("Anguished Unmaking", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        removal.setActivatingPlayer(pod.actor);
        removal.getTargets().add(pod.game.getCardState(anthem));
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(removal, pod.actor, pod.game));
        settle(pod.game);
        assertEquals(pod.actor.getLife(), 37);
        assertEquals(anthems(pod), 1, "The departed enchantment remains a legal last-known copy");
        assertEquals(pod.prompts[0], 1);
    }

    @Test
    public void phytohydraCopiesItsLastKnownSelfAfterExile() {
        Pod pod = pod("Sprouting Phytohydra");
        Card source = addCardToZone("Grizzly Bears", pod.game.getPlayers().get(1), ZoneType.Battlefield);
        CardDamageTable dealt = new CardDamageTable();
        dealt.put(source, pod.host, 1);
        pod.game.copyLastState();
        pod.game.getAction().dealDamage(false, dealt, new CardDamageTable(), new GameEntityCounterTable(), null);
        queue(pod.game);
        assertEquals(pod.game.getStack().size(), 1);
        SpellAbility removal = addCardToZone("Anguished Unmaking", pod.actor, ZoneType.Hand).getFirstSpellAbility();
        removal.setActivatingPlayer(pod.actor);
        removal.getTargets().add(pod.host);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(removal, pod.actor, pod.game));
        settle(pod.game);
        assertTrue(pod.game.getCardState(pod.host).isInZone(ZoneType.Exile));
        assertEquals(pod.actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Sprouting Phytohydra") && card.isToken()).count(), 1);
        assertEquals(pod.prompts[0], 1);
    }

    @Test
    public void redirectedTokensStillUseTheCopyAction() {
        Pod pod = pod("Calix, Guided by Fate");
        addCardToZone("Glorious Anthem", pod.actor, ZoneType.Battlefield);
        pod.game.getTriggerHandler().resetActiveTriggers();
        Player thief = pod.game.getPlayers().get(2);
        for (int index = 0; index < 4; index++) {
            addCardToZone("Island", thief, ZoneType.Battlefield);
        }
        SpellAbility cast = addCardToZone("Crafty Cutpurse", thief, ZoneType.Hand).getFirstSpellAbility();
        cast.setActivatingPlayer(thief);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(cast, thief, pod.game));
        settle(pod.game);
        damage(pod);
        assertEquals(anthems(pod), 1);
        assertEquals(thief.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Glorious Anthem") && card.isToken()).count(), 1);
        damage(pod);
        assertEquals(thief.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Glorious Anthem") && card.isToken()).count(), 1);
        assertEquals(pod.prompts[0], 1);
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

    private Pod pod(String hostName) {
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
            for (int index = 0; index < 8; index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        for (String name : List.of("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Swamp", "Plains", "Plains", "Island")) {
            addCardToZone(name, actor, ZoneType.Battlefield);
        }
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
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
        return new Pod(game, actor, host, prompts, decline);
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
    private record Pod(Game game, Player actor, Card host, int[] prompts, boolean[] decline) {}
}
