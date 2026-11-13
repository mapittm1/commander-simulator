package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameSnapshot;
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

public class CommanderReverseTurnOrderTest extends AITest {
    @Test
    public void aeonEngineDirectionSurvivesIndependentAiCopy() {
        verifyCopy(true);
    }

    @Test
    public void aeonEngineDirectionSurvivesIndependentSnapshot() {
        verifyCopy(false);
    }

    private void verifyCopy(boolean aiCopy) {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Player nextClockwise = game.getPlayers().get(1);
        Player nextCounterclockwise = game.getPlayers().get(3);
        assertEquals(game.getNextPlayerAfter(actor), nextClockwise);
        Card engine = addCardToZone("Aeon Engine", actor, ZoneType.Battlefield);
        SpellAbility ability = engine.getSpellAbilities().stream()
                .filter(SpellAbility::isActivatedAbility).findFirst().orElseThrow();
        ability.setActivatingPlayer(actor);
        game.getAction().checkStateEffects(true);
        assertFalse(engine.isTapped());
        assertTrue(ComputerUtil.playStack(ability, actor, game));
        assertTrue(game.getCardState(engine).isInZone(ZoneType.Exile));
        assertEquals(game.getNextPlayerAfter(actor), nextClockwise);
        game.getStack().resolveStack();
        assertEquals(game.getNextPlayerAfter(actor), nextCounterclockwise);

        Game copy = aiCopy ? new GameCopier(game).withStack(true).makeCopy() : new GameSnapshot(game).makeCopy();
        Player copiedActor = copy.getPlayer(actor.getId());
        assertEquals(copy.getNextPlayerAfter(copiedActor).getId(), nextCounterclockwise.getId());
        copy.reverseTurnOrder();
        assertEquals(copy.getNextPlayerAfter(copiedActor).getId(), nextClockwise.getId());
        assertEquals(game.getNextPlayerAfter(actor), nextCounterclockwise);
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
