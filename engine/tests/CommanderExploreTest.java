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
import forge.game.player.PlayerActionConfirmMode;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class CommanderExploreTest extends AITest {
    @Test
    public void rangerExploresSameNonlandTwiceWhenKeptOnTop() {
        Game game = newGameWithForests();
        Player actor = game.getPlayers().get(0);
        Card nonland = addCardToZone("Sol Ring", actor, ZoneType.Library);
        List<Card> offered = new ArrayList<>();
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public boolean confirmAction(SpellAbility ability, PlayerActionConfirmMode mode,
                    String message, List<String> options, Card card, Map<String, Object> params) {
                if (params != null && params.containsKey("RevealedCard")) {
                    Card ranger = actor.getCardsIn(ZoneType.Battlefield).stream()
                            .filter(permanent -> permanent.getName().equals("Jadelight Ranger"))
                            .findFirst().orElseThrow();
                    assertEquals(ranger.getCounters(CounterEnumType.P1P1), offered.size() + 1,
                            "The counter is placed before the nonland choice");
                    offered.add(card);
                    return false;
                }
                return super.confirmAction(ability, mode, message, options, card, params);
            }
        });

        Card ranger = castCreature(game, actor, "Jadelight Ranger");
        game.getStack().resolveStack();

        assertEquals(offered, List.of(nonland, nonland));
        assertEquals(actor.getNumExploredThisTurn(), 2);
        assertSame(actor.getCardsIn(ZoneType.Library).getFirst(), nonland);
        assertEquals(game.getCardState(ranger).getCounters(CounterEnumType.P1P1), 2);
    }

    @Test
    public void janewayExploresForCreatureThatDiedBeforeTriggerResolved() {
        Game game = newGameWithForests();
        Player actor = game.getPlayers().get(0);
        Card land = addCardToZone("Island", actor, ZoneType.Library);
        Card janeway = addCardToZone("Captain Kathryn Janeway", actor, ZoneType.Battlefield);

        Card bear = castCreature(game, actor, "Grizzly Bears");
        assertSame(game.getStack().peekAbility().getHostCard(), janeway);
        assertEquals(actor.getMaxLandPlays(), 2);
        game.getAction().moveToGraveyard(game.getCardState(bear), null);
        game.getAction().checkStateEffects(true);
        game.getStack().resolveStack();

        assertEquals(actor.getNumExploredThisTurn(), 1);
        assertTrue(actor.getCardsIn(ZoneType.Hand).contains(land));
        assertTrue(game.getCardState(bear).isInZone(ZoneType.Graveyard));
    }

    @Test
    public void janewayExploresUsingCurrentControllerAfterControlChanges() {
        Game game = newGameWithForests();
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        Card originalControllerLand = addCardToZone("Plains", actor, ZoneType.Library);
        Card currentControllerLand = addCardToZone("Island", opponent, ZoneType.Library);
        Card janeway = addCardToZone("Captain Kathryn Janeway", actor, ZoneType.Battlefield);
        for (int index = 0; index < 4; index++) {
            addCardToZone("Island", opponent, ZoneType.Battlefield);
        }

        Card bear = castCreature(game, actor, "Grizzly Bears");
        assertSame(game.getStack().peekAbility().getHostCard(), janeway);
        Card ray = addCardToZone("Ray of Command", opponent, ZoneType.Hand);
        SpellAbility spell = ray.getFirstSpellAbility();
        spell.setActivatingPlayer(opponent);
        spell.getTargets().add(game.getCardState(bear));
        assertTrue(ComputerUtil.handlePlayingSpellAbility(opponent, spell, null));
        game.getStack().resolveStack();
        assertSame(game.getCardState(bear).getController(), opponent);
        assertSame(game.getStack().peekAbility().getHostCard(), janeway);
        game.getStack().resolveStack();

        assertEquals(actor.getNumExploredThisTurn(), 0);
        assertEquals(opponent.getNumExploredThisTurn(), 1);
        assertSame(actor.getCardsIn(ZoneType.Library).getFirst(), originalControllerLand);
        assertTrue(opponent.getCardsIn(ZoneType.Hand).contains(currentControllerLand));
    }

    @Test
    public void rangerGetsOneCounterForEachEmptyLibraryExplore() {
        Game game = newGameWithForests();
        Player actor = game.getPlayers().get(0);
        Card ranger = castCreature(game, actor, "Jadelight Ranger");
        game.getStack().resolveStack();

        assertEquals(actor.getNumExploredThisTurn(), 2);
        assertEquals(game.getCardState(ranger).getCounters(CounterEnumType.P1P1), 2);
    }

    private Card castCreature(Game game, Player actor, String name) {
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        Card card = addCardToZone(name, actor, ZoneType.Hand);
        SpellAbility spell = card.getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null));
        game.getStack().resolveStack();
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertTrue(game.getCardState(card).isInPlay());
        assertEquals(game.getStack().size(), 1);
        return card;
    }

    private Game newGameWithForests() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Commander explore"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Forest", actor, ZoneType.Battlefield);
        }
        return game;
    }
}
