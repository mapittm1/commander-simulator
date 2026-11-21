package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.ability.AbilityKey;
import forge.game.trigger.TriggerType;
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

/** Partial-state main-phase witnesses with supplied targets and native mana/cost resolution. */
public class CommanderCarpetManaTest extends AITest {
    @Test
    public void addingZeroLeavesTheSecondMainPhaseAvailable() {
        Pod pod = pod(false);
        phase(pod, PhaseType.MAIN1);
        assertEquals(pod.actor.getManaPool().totalMana(), 0);
        assertEquals(pod.prompts[0], 1);
        harrow(pod);
        phase(pod, PhaseType.MAIN2);
        assertEquals(pod.actor.getManaPool().totalMana(), 1,
                "Adding zero mana did not use the turn's addition");
    }

    @Test
    public void addingManaPreventsASecondAddition() {
        Pod pod = pod(true);
        phase(pod, PhaseType.MAIN1);
        assertEquals(pod.actor.getManaPool().totalMana(), 1);
        int before = pod.actor.getManaPool().totalMana();
        phase(pod, PhaseType.MAIN2);
        assertEquals(pod.actor.getManaPool().totalMana(), before);
        assertEquals(pod.prompts[0], 1);
    }

    @Test
    public void decliningLeavesTheSecondMainPhaseAvailable() {
        Pod pod = pod(true);
        pod.decline[0] = true;
        phase(pod, PhaseType.MAIN1);
        assertEquals(pod.actor.getManaPool().totalMana(), 0);
        pod.decline[0] = false;
        phase(pod, PhaseType.MAIN2);
        assertEquals(pod.actor.getManaPool().totalMana(), 1);
        assertEquals(pod.prompts[0], 2);
    }

    private void phase(Pod pod, PhaseType phase) {
        pod.game.getPhaseHandler().devModeSet(phase, pod.actor, 2);
        pod.game.getTriggerHandler().resetActiveTriggers();
        pod.game.copyLastState();
        pod.game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(pod.actor), false);
        settle(pod.game);
    }

    private void harrow(Pod pod) {
        SpellAbility spell = addCardToZone("Harrow", pod.opponent, ZoneType.Hand).getFirstSpellAbility();
        spell.setActivatingPlayer(pod.opponent);
        pod.game.copyLastState();
        assertTrue(ComputerUtil.playStack(spell, pod.opponent, pod.game));
        settle(pod.game);
        assertEquals(pod.opponent.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getType().hasSubtype("Island")).count(), 1);
        assertEquals(pod.game.getCardState(spell.getHostCard()).getZone().getZoneType(), ZoneType.Graveyard);
    }

    private Pod pod(boolean initialIsland) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Carpet mana"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        for (Player player : game.getPlayers()) {
            if (player.equals(opponent)) {
                addCardToZone("Island", player, ZoneType.Library);
                addCardToZone("Forest", player, ZoneType.Library);
            } else {
                for (int index = 0; index < 8; index++) {
                    addCardToZone("Forest", player, ZoneType.Library);
                }
            }
        }
        for (int index = 0; index < 3; index++) {
            addCardToZone("Forest", opponent, ZoneType.Battlefield);
        }
        if (initialIsland) {
            addCardToZone("Island", opponent, ZoneType.Battlefield);
        }
        for (String name : List.of("Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Forest", "Swamp", "Plains", "Plains", "Island")) {
            addCardToZone(name, actor, ZoneType.Battlefield);
        }
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        Card host = addCardToZone("Carpet of Flowers", actor, ZoneType.Battlefield);
        int[] prompts = {0};
        boolean[] decline = {false};
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public void orderAndPlaySimultaneousSa(List<SpellAbility> abilities) {
                for (SpellAbility ability : abilities) {
                    if (ability.getHostCard().getId() == host.getId()) {
                        if (ability.usesTargeting()) {
                            ability.resetTargets();
                            assertTrue(ability.canTarget(opponent));
                            ability.getTargets().add(opponent);
                        }
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
        return new Pod(game, actor, opponent, host, prompts, decline);
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
    private record Pod(Game game, Player actor, Player opponent, Card host, int[] prompts, boolean[] decline) {}
}
