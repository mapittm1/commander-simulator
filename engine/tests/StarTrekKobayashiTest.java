package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class StarTrekKobayashiTest extends AITest {
    private record Pod(Game game, Player actor, Player opponent, Card scenario) {
        void upkeep(Player player, int turn) {
            game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, player, turn);
            game.getAction().checkStaticAbilities();
            game.getTriggerHandler().resetActiveTriggers();
            game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(player), false);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
        }
    }

    private Pod pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Kobayashi Maru"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Card scenario = addCardToZone("Kobayashi Maru Scenario", actor, ZoneType.Battlefield);
        return new Pod(game, actor, game.getPlayers().get(1), scenario);
    }

    private void choose(Player player, ApiType mode, Card target, List<List<ApiType>> offered) {
        Game game = player.getGame();
        player.dangerouslySetController(new PlayerControllerAi(game, player, player.getLobbyPlayer()) {
            @Override
            public List<AbilitySub> chooseModeForAbility(SpellAbility ability, List<AbilitySub> choices,
                    int min, int max, boolean allowRepeat) {
                offered.add(choices.stream().map(AbilitySub::getApi).toList());
                AbilitySub selected = choices.stream().filter(choice -> choice.getApi() == mode)
                        .findFirst().orElseThrow();
                if (target != null) {
                    selected.resetTargets();
                    selected.getTargets().add(target);
                }
                return new ArrayList<>(List.of(selected));
            }
        });
    }

    @Test
    public void failedHailRemainsSpentAfterStealEnchantment() {
        Pod pod = pod();
        List<List<ApiType>> offered = new ArrayList<>();
        Card bear = addCardToZone("Grizzly Bears", pod.opponent, ZoneType.Battlefield);
        choose(pod.actor, ApiType.ChangeZone, bear, offered);
        pod.upkeep(pod.actor, 1);
        assertEquals(pod.game.getStack().size(), 1);
        pod.game.getAction().moveToGraveyard(bear, null);
        pod.game.getStack().resolveStack();
        assertTrue(pod.game.getCardState(bear).isInZone(ZoneType.Graveyard));
        assertEquals(pod.actor.getDilemmasFacedThisTurn(), 1);

        pod.game.getPhaseHandler().devModeSet(PhaseType.MAIN1, pod.opponent, 2);
        addCardToZone("Island", pod.opponent, ZoneType.Battlefield);
        addCardToZone("Island", pod.opponent, ZoneType.Battlefield);
        Card aura = addCardToZone("Steal Enchantment", pod.opponent, ZoneType.Hand);
        SpellAbility steal = aura.getFirstSpellAbility();
        steal.setActivatingPlayer(pod.opponent);
        assertTrue(steal.canTarget(pod.scenario));
        steal.getTargets().add(pod.scenario);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(pod.opponent, steal, null));
        pod.game.getStack().resolveStack();
        pod.game.getAction().checkStateEffects(true);
        assertSame(pod.scenario.getController(), pod.opponent);

        choose(pod.opponent, ApiType.Token, null, offered);
        pod.upkeep(pod.opponent, 3);
        assertEquals(offered.size(), 2);
        assertTrue(offered.get(0).contains(ApiType.ChangeZone));
        assertFalse(offered.get(1).contains(ApiType.ChangeZone));
        assertEquals(pod.game.getStack().size(), 1);
        pod.game.getStack().resolveStack();
        assertEquals(pod.opponent.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isToken() && card.getType().hasCreatureType("Officer")).count(), 2L);
    }

    @Test
    public void exhaustedScenarioOffersNoModeAndCreatesNoDilemma() {
        Pod pod = pod();
        addCardToZone("Platinum Angel", pod.actor, ZoneType.Battlefield);
        addCardToZone("Forest", pod.actor, ZoneType.Library);
        addCardToZone("Plains", pod.actor, ZoneType.Library);
        List<List<ApiType>> offered = new ArrayList<>();
        List<ApiType> sequence = List.of(ApiType.ChangeZone, ApiType.Draw, ApiType.Token, ApiType.LosesGame);
        for (int index = 0; index < sequence.size(); index++) {
            choose(pod.actor, sequence.get(index), null, offered);
            pod.upkeep(pod.actor, index + 1);
            assertEquals(pod.game.getStack().size(), 1);
            assertEquals(offered.get(index).size(), sequence.size() - index);
            pod.game.getStack().resolveStack();
        }
        assertEquals(pod.actor.getDilemmasFacedThisTurn(), 4);
        pod.upkeep(pod.actor, 5);
        assertEquals(offered.size(), 4);
        assertTrue(pod.game.getStack().isEmpty());
        assertEquals(pod.actor.getDilemmasFacedThisTurn(), 4);
    }
}
