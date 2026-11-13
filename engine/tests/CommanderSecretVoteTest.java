package forge.ai;

import com.google.common.collect.ListMultimap;
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

public class CommanderSecretVoteTest extends AITest {
    @Test
    public void mobVerdictKeepsEachBallotPrivateUntilCountingAllVotes() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Secret votes"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player target = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        for (int index = 0; index < 4; index++) {
            addCardToZone("Mountain", actor, ZoneType.Battlefield);
        }
        Card drawn = addCardToZone("Island", actor, ZoneType.Library);
        Card remaining = addCardToZone("Forest", actor, ZoneType.Library);
        List<Integer> visibleVoteCounts = new ArrayList<>();
        for (Player player : game.getPlayers()) {
            player.dangerouslySetController(new PlayerControllerAi(game, player, player.getLobbyPlayer()) {
                @Override
                public Object vote(SpellAbility ability, String prompt, List<Object> options,
                        ListMultimap<Object, Player> votes, Player forPlayer, boolean optional) {
                    assertFalse(options.contains(forPlayer));
                    assertFalse(optional);
                    visibleVoteCounts.add(votes.size());
                    Player choice = forPlayer.equals(target) ? actor : target;
                    assertTrue(options.contains(choice));
                    return choice;
                }
            });
        }

        Card verdict = addCardToZone("Mob Verdict", actor, ZoneType.Hand);
        SpellAbility spell = verdict.getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null));
        game.getStack().resolveStack();

        assertEquals(visibleVoteCounts, List.of(0, 0, 0, 0));
        assertEquals(target.getLife(), 34);
        assertEquals(actor.getLife(), 40);
        assertTrue(actor.getCardsIn(ZoneType.Hand).contains(drawn));
        assertTrue(actor.getCardsIn(ZoneType.Library).contains(remaining));
    }
}
