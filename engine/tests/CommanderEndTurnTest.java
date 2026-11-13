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
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CommanderEndTurnTest extends AITest {
    @Test
    public void daysUndoingDrawsSevenAndClearsBowmastersTriggersBeforeCleanup() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        addCardToZone("Orcish Bowmasters", opponent, ZoneType.Battlefield);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 8; index++) {
                addCardToZone("Mountain", player, ZoneType.Library);
            }
        }
        Card undoing = addCardToZone("Day's Undoing", actor, ZoneType.Hand);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        cast(game, actor, undoing);

        game.getStack().resolveStack();

        assertEquals(game.getPhaseHandler().getPhase(), PhaseType.CLEANUP);
        assertTrue(game.getCardState(undoing).isInZone(ZoneType.Exile));
        for (Player player : game.getPlayers()) {
            assertEquals(player.getCardsIn(ZoneType.Hand).size(), 7);
            assertEquals(player.getLife(), 40);
        }
        collectTriggers(game);
        assertTrue(game.getStack().isEmpty());
    }

    @Test
    public void timeStopExilesTheSpellBelowItWithoutResolvingIt() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Card draw = addCardToZone("Opt", actor, ZoneType.Hand);
        Card stop = addCardToZone("Time Stop", actor, ZoneType.Hand);
        addCardToZone("Forest", actor, ZoneType.Library);
        cast(game, actor, draw);
        cast(game, actor, stop);
        assertEquals(game.getStack().size(), 2);

        game.getStack().resolveStack();

        assertEquals(game.getPhaseHandler().getPhase(), PhaseType.CLEANUP);
        assertTrue(game.getStack().isEmpty());
        assertTrue(game.getCardState(stop).isInZone(ZoneType.Exile));
        assertTrue(game.getCardState(draw).isInZone(ZoneType.Exile));
        assertEquals(actor.getCardsIn(ZoneType.Library).size(), 1);
        assertTrue(actor.getCardsIn(ZoneType.Hand).isEmpty());
    }

    @Test
    public void exilingStackCardsDuringTimeStopTriggersWarDoctorInCleanup() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Card doctor = addCardToZone("The War Doctor", actor, ZoneType.Battlefield);
        Card opt = addCardToZone("Opt", actor, ZoneType.Hand);
        Card stop = addCardToZone("Time Stop", actor, ZoneType.Hand);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        cast(game, actor, opt);
        cast(game, actor, stop);

        game.getStack().resolveStack();

        assertEquals(game.getPhaseHandler().getPhase(), PhaseType.CLEANUP);
        assertTrue(game.getCardState(stop).isInZone(ZoneType.Exile));
        assertTrue(game.getCardState(opt).isInZone(ZoneType.Exile));
        collectTriggers(game);
        assertEquals(game.getStack().size(), 1);
        assertEquals(game.getStack().peekAbility().getHostCard().getId(), doctor.getId());
        game.getStack().resolveStack();
        assertEquals(doctor.getCounters(CounterEnumType.TIME), 1);
    }

    private void cast(Game game, Player actor, Card card) {
        SpellAbility spell = card.getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null));
        assertEquals(game.getStack().peekAbility().getHostCard().getId(), card.getId());
    }

    private void collectTriggers(Game game) {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private Game newGame() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "End the turn"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        for (int index = 0; index < 7; index++) {
            addCardToZone("Island", actor, ZoneType.Battlefield);
        }
        return game;
    }
}
