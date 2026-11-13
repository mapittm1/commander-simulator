package forge.ai;

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
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class CommanderSnapshotTransformStackTest extends AITest {
    @Test
    public void snapshotKeepsPendingTransformAndCanRestoreIt() {
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
        game.getPhaseHandler().devModeSet(PhaseType.END_OF_TURN, actor, 2);
        game.getTriggerHandler().resetActiveTriggers();
        game.getTriggerHandler().runTrigger(TriggerType.Phase, AbilityKey.mapFromPlayer(actor), false);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertFalse(game.getStack().isEmpty(), "Homicidal Brute's end step ability must be on the stack");

        Game withoutStack = new GameSnapshot(game).makeCopy(null, false);
        assertTrue(withoutStack.getStack().isEmpty());

        GameSnapshot saved = new GameSnapshot(game);
        Game checkpoint = saved.makeCopy();
        Game independent = new GameSnapshot(game).makeCopy();
        assertBackToScholar(independent, scholar, "independent snapshot");
        assertEquals(checkpoint.getCardState(scholar).getName(), "Homicidal Brute");
        assertBackToScholar(game, scholar, "live game");

        saved.restoreGameState(game);
        assertEquals(game.getCardState(scholar).getName(), "Homicidal Brute");
        assertBackToScholar(game, scholar, "restored snapshot");
    }

    @Test
    public void snapshotRestoresPaidSpellAndCounterspellTargetsWithoutRecasting() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        addCardToZone("Mountain", actor, ZoneType.Battlefield);
        addCardToZone("Island", opponent, ZoneType.Battlefield);
        addCardToZone("Island", opponent, ZoneType.Battlefield);
        Card bolt = addCardToZone("Lightning Bolt", actor, ZoneType.Hand);
        SpellAbility damage = bolt.getFirstSpellAbility();
        damage.setActivatingPlayer(actor);
        damage.getTargets().add(opponent);
        assertTrue(ComputerUtil.playStack(damage, actor, game));
        Card counter = addCardToZone("Counterspell", opponent, ZoneType.Hand);
        SpellAbility response = counter.getFirstSpellAbility();
        response.setActivatingPlayer(opponent);
        response.getTargets().add(game.getStack().peekAbility());
        assertTrue(ComputerUtil.playStack(response, opponent, game));
        int topId = game.getStack().peek().getId();

        GameSnapshot snapshot = new GameSnapshot(game);
        Game checkpoint = snapshot.makeCopy();
        Game independent = new GameSnapshot(game).makeCopy();
        assertPaidResponse(independent, topId);
        independent.getStack().resolveStack();
        assertTrue(independent.getStack().isEmpty());
        assertEquals(independent.getPlayers().get(1).getLife(), 40);
        assertEquals(game.getStack().size(), 2);
        assertEquals(checkpoint.getStack().size(), 2);

        game.getStack().resolveStack();
        assertTrue(game.getStack().isEmpty());
        snapshot.restoreGameState(game);
        assertPaidResponse(game, topId);
        game.getStack().resolveStack();
        assertTrue(game.getStack().isEmpty());
        assertEquals(game.getPlayers().get(1).getLife(), 40);
        assertEquals(checkpoint.getStack().size(), 2);
    }

    private void assertPaidResponse(Game game, int stackId) {
        assertEquals(game.getStack().size(), 2);
        assertEquals(game.getStack().peek().getId(), stackId);
        SpellAbility response = game.getStack().peekAbility();
        var entries = game.getStack().iterator();
        entries.next();
        SpellAbility bolt = entries.next().getSpellAbility();
        assertSame(response.getTargets().getFirstTargetedSpell(), bolt);
        assertSame(bolt.getHostCard().getGame(), game);
        assertSame(bolt.getPayingMana().get(0).getSourceCard().getGame(), game);
        assertSame(bolt.getTargets().getTargetPlayers().iterator().next(), game.getPlayers().get(1));
        assertEquals(game.getPlayers().get(0).getSpellsCastThisTurn(), 1);
        assertEquals(game.getPlayers().get(1).getSpellsCastThisTurn(), 1);
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
