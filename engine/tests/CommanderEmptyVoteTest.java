package forge.ai;

import com.google.common.collect.ListMultimap;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameEntityCounterTable;
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

public class CommanderEmptyVoteTest extends AITest {
    @Test
    public void vaultElevenDrawsForEachPlayerWhenThereAreNoCreaturesToVoteFor() {
        verifyVault(false);
    }

    @Test
    public void vaultElevenDrawsWhenAllPlayersAbstainWithACreatureInPlay() {
        verifyVault(true);
    }

    private void verifyVault(boolean includeCreature) {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Card creature = includeCreature ? addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield) : null;
        List<Player> voters = new ArrayList<>();
        Card vault = addCardToZone("Vault 11: Voter's Dilemma", actor, ZoneType.Battlefield);
        for (Player player : game.getPlayers()) {
            addCardToZone("Island", player, ZoneType.Library);
            player.dangerouslySetController(new PlayerControllerAi(game, player, player.getLobbyPlayer()) {
                @Override
                public Object vote(SpellAbility ability, String prompt, List<Object> options,
                        ListMultimap<Object, Player> votes, Player forPlayer, boolean optional) {
                    voters.add(forPlayer);
                    assertEquals(options, List.of(creature));
                    assertTrue(optional);
                    assertTrue(votes.isEmpty());
                    return null;
                }
            });
        }
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        vault.setCounters(CounterEnumType.LORE, 1);
        GameEntityCounterTable table = new GameEntityCounterTable();
        vault.addCounter(CounterEnumType.LORE, 1, actor, table);
        table.replaceCounterEffect(game, null);
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertEquals(game.getStack().size(), 1);
        game.getStack().resolveStack();

        for (Player player : game.getPlayers()) {
            assertEquals(player.getCardsIn(ZoneType.Hand).size(), 1);
            assertEquals(player.getCardsIn(ZoneType.Library).size(), 0);
        }
        assertEquals(voters, includeCreature ? game.getPlayers() : List.of());
        if (includeCreature) {
            assertTrue(game.getCardState(creature).isInPlay());
        }
    }

    private Game newGame() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Empty ballot"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0), 1);
        return game;
    }

}
