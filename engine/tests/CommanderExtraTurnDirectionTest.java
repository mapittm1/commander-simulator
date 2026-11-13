package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class CommanderExtraTurnDirectionTest extends AITest {
    @Test
    public void reversingDuringAnExtraTurnUsesTheLastNormalPlayerToResume() {
        Game game = newGame();
        Player original = game.getPlayers().get(0);
        Player extra = game.getPlayers().get(2);
        Player resumed = game.getPlayers().get(3);
        preparePlayers(game, original, 5);
        scheduleTurn(game, original, extra);
        assertEquals(game.getPhaseHandler().getNextTurn(), extra);
        nextTurn(game);
        assertEquals(game.getPhaseHandler().getPlayerTurn(), extra);
        assertTrue(extra.isExtraTurn());

        reverseWithAeonEngine(game, extra);

        assertEquals(game.getPhaseHandler().getNextTurn(), resumed);
        nextTurn(game);
        assertEquals(game.getPhaseHandler().getPlayerTurn(), resumed);
        assertFalse(resumed.isExtraTurn());
    }

    @Test
    public void reversalPreservesPendingExtraTurnsAcrossAnAiCopy() {
        Game game = newGame();
        Player original = game.getPlayers().get(0);
        Player firstExtra = game.getPlayers().get(2);
        Player secondExtra = game.getPlayers().get(1);
        Player resumed = game.getPlayers().get(3);
        preparePlayers(game, original, 10);
        scheduleTurn(game, original, secondExtra);
        scheduleTurn(game, original, firstExtra);
        nextTurn(game);
        assertEquals(game.getPhaseHandler().getPlayerTurn(), firstExtra);
        reverseWithAeonEngine(game, firstExtra);

        Game copy = new GameCopier(game).withStack(true).makeCopy();
        assertEquals(copy.getPhaseHandler().getNextTurn().getId(), secondExtra.getId());
        nextTurn(copy);
        assertEquals(copy.getPhaseHandler().getPlayerTurn().getId(), secondExtra.getId());
        assertTrue(copy.getPhaseHandler().getPlayerTurn().isExtraTurn());
        nextTurn(copy);
        assertEquals(copy.getPhaseHandler().getPlayerTurn().getId(), resumed.getId());
        assertFalse(copy.getPhaseHandler().getPlayerTurn().isExtraTurn());
        assertEquals(game.getPhaseHandler().getPlayerTurn(), firstExtra);
        assertEquals(game.getPhaseHandler().getNextTurn(), secondExtra);
    }

    private void preparePlayers(Game game, Player actor, int mana) {
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 5; index++) {
                addCardToZone("Island", player, ZoneType.Library);
            }
        }
        for (int index = 0; index < mana; index++) {
            addCardToZone("Island", actor, ZoneType.Battlefield);
        }
    }

    private void scheduleTurn(Game game, Player actor, Player extra) {
        Card warp = addCardToZone("Time Warp", actor, ZoneType.Hand);
        SpellAbility spell = warp.getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        spell.getTargets().add(extra);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null));
        game.getStack().resolveStack();
    }

    private void nextTurn(Game game) {
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> {
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
        }));
    }

    private void reverseWithAeonEngine(Game game, Player actor) {
        Card engine = addCardToZone("Aeon Engine", actor, ZoneType.Battlefield);
        SpellAbility ability = engine.getSpellAbilities().stream()
                .filter(SpellAbility::isActivatedAbility).findFirst().orElseThrow();
        ability.setActivatingPlayer(actor);
        game.getAction().checkStateEffects(true);
        assertFalse(engine.isTapped());
        assertTrue(ComputerUtil.playStack(ability, actor, game));
        assertTrue(game.getCardState(engine).isInZone(ZoneType.Exile));
        game.getStack().resolveStack();
    }

    private Game newGame() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Reverse turn order"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0), 1);
        return game;
    }
}
