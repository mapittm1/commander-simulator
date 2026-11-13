package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameSnapshot;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class CommanderTransformHistoryTest extends AITest {
    @Test
    public void aiCopyKeepsSecondTransformationLegal() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Card scholar = addCardToZone("Civilized Scholar", actor, ZoneType.Battlefield);
        addCardToZone("Forest", actor, ZoneType.Battlefield);
        addCardToZone("Forest", actor, ZoneType.Battlefield);
        Card moonmist = addCardToZone("Moonmist", actor, ZoneType.Hand);
        SpellAbility cast = moonmist.getFirstSpellAbility();
        cast.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, cast, null));
        game.getStack().resolveStack();
        assertEquals(scholar.getName(), "Homicidal Brute");
        GameSnapshot saved = new GameSnapshot(game);
        Game checkpoint = saved.makeCopy();
        long transformTimestamp = scholar.getTransformedTimestamp();
        assertEquals(checkpoint.getCardState(scholar).getTransformedTimestamp(), transformTimestamp);

        game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, actor, 2);
        game.getTriggerHandler().resetActiveTriggers();
        game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(actor), false);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertFalse(game.getStack().isEmpty(), "Homicidal Brute's end step ability must be on the stack");

        Game aiCopy = new GameCopier(game).withStack(true).makeCopy();
        assertBackToScholar(aiCopy, scholar, "AI copy");
        assertEquals(scholar.getName(), "Homicidal Brute", "copies must leave the live card alone");
        assertBackToScholar(game, scholar, "live game");
        assertTrue(scholar.getTransformedTimestamp() > transformTimestamp);
        saved.restoreGameState(game);
        Card restored = game.getCardState(scholar);
        assertEquals(restored.getName(), "Homicidal Brute");
        assertEquals(restored.getTransformedTimestamp(), transformTimestamp);
    }

    private void assertBackToScholar(Game game, Card source, String path) {
        assertFalse(game.getStack().isEmpty(), path);
        game.getStack().resolveStack();
        Card scholar = game.getCardState(source);
        assertTrue(scholar.isTapped(), path);
        assertEquals(scholar.getName(), "Civilized Scholar", path);
    }

    private Game newGame() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Transform history"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0), 2);
        return game;
    }
}
