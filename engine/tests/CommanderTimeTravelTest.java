package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.DelayedReveal;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollectionView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class CommanderTimeTravelTest extends AITest {
    @Test
    public void removingTheLastTimeCounterQueuesSuspendAndLeavesOtherCardsAlone() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        Card vision = addCardToZone("Ancestral Vision", actor, ZoneType.Exile);
        vision.setCounters(CounterEnumType.TIME, 1);
        Card foretold = addCardToZone("As Foretold", actor, ZoneType.Battlefield);
        foretold.setCounters(CounterEnumType.TIME, 2);
        Card untouched = addCardToZone("Arc Blade", opponent, ZoneType.Exile);
        untouched.setCounters(CounterEnumType.TIME, 2);
        Card nonsuspended = addCardToZone("Sol Ring", actor, ZoneType.Exile);
        nonsuspended.setCounters(CounterEnumType.TIME, 2);
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public <T extends GameEntity> List<T> chooseEntitiesForEffect(FCollectionView<T> options,
                    int min, int max, DelayedReveal delayed, SpellAbility ability, String title,
                    Player targetedPlayer, Map<String, Object> params) {
                if (ability.getHostCard().getName().equals("All of History, All at Once")) {
                    assertEquals(options.size(), 2);
                    assertTrue(options.contains(vision));
                    assertTrue(options.contains(foretold));
                    return options.stream().filter(vision::equals).toList();
                }
                return super.chooseEntitiesForEffect(options, min, max, delayed, ability,
                        title, targetedPlayer, params);
            }

            @Override
            public boolean chooseBinary(SpellAbility ability, String question, BinaryChoiceType kind,
                    Map<String, Object> params) {
                assertEquals(kind, BinaryChoiceType.AddOrRemove);
                assertSame(params.get("Target"), vision);
                return false;
            }
        });

        castTimeTravel(game, actor);

        assertEquals(vision.getCounters(CounterEnumType.TIME), 0);
        assertEquals(foretold.getCounters(CounterEnumType.TIME), 2);
        assertEquals(untouched.getCounters(CounterEnumType.TIME), 2);
        assertEquals(nonsuspended.getCounters(CounterEnumType.TIME), 2);
        assertTrue(vision.isInZone(ZoneType.Exile));
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        long suspendTriggers = 0;
        for (var instance : game.getStack()) {
            if (instance.getSpellAbility().getHostCard().equals(vision)) {
                suspendTriggers++;
            }
        }
        assertEquals(suspendTriggers, 1L);
    }

    @Test
    public void spellResolvesWithTheDefaultControllerAndNoEligibleObjects() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        castTimeTravel(game, actor);
        assertTrue(actor.getCardsIn(ZoneType.Graveyard).stream()
                .anyMatch(card -> card.getName().equals("All of History, All at Once")));
    }

    private void castTimeTravel(Game game, Player actor) {
        Card history = addCardToZone("All of History, All at Once", actor, ZoneType.Hand);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        SpellAbility spell = history.getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null));
        game.getStack().resolveStack();
    }

    private Game newGame() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Time travel"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        for (int index = 0; index < 4; index++) {
            addCardToZone("Island", actor, ZoneType.Battlefield);
        }
        return game;
    }
}
