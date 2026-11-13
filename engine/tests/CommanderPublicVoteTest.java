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

public class CommanderPublicVoteTest extends AITest {
    @Test
    public void capitalPunishmentUsesCurrentTurnOrderAndShowsEarlierPublicVotes() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Public voting order"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player target = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        for (int index = 0; index < 6; index++) {
            addCardToZone("Swamp", actor, ZoneType.Battlefield);
        }
        Card engine = addCardToZone("Aeon Engine", actor, ZoneType.Battlefield);
        SpellAbility reverse = engine.getSpellAbilities().stream()
                .filter(SpellAbility::isActivatedAbility).findFirst().orElseThrow();
        reverse.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.playStack(reverse, actor, game));
        assertTrue(game.getCardState(engine).isInZone(ZoneType.Exile));
        game.getStack().resolveStack();
        assertEquals(game.getNextPlayerAfter(actor), game.getPlayers().get(3));

        Card creature = addCardToZone("Grizzly Bears", target, ZoneType.Battlefield);
        List<Player> voters = new ArrayList<>();
        List<Integer> visibleVoteCounts = new ArrayList<>();
        for (Player player : game.getPlayers()) {
            player.dangerouslySetController(new PlayerControllerAi(game, player, player.getLobbyPlayer()) {
                @Override
                public Object vote(SpellAbility ability, String prompt, List<Object> options,
                        ListMultimap<Object, Player> votes, Player forPlayer, boolean optional) {
                    assertEquals(options.size(), 2);
                    assertFalse(optional);
                    voters.add(forPlayer);
                    visibleVoteCounts.add(votes.size());
                    return options.get(0);
                }
            });
        }

        Card verdict = addCardToZone("Capital Punishment", actor, ZoneType.Hand);
        SpellAbility spell = verdict.getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null));
        game.getStack().resolveStack();

        assertEquals(voters, List.of(actor, game.getPlayers().get(3), game.getPlayers().get(2), target));
        assertEquals(visibleVoteCounts, List.of(0, 1, 2, 3));
        assertTrue(game.getCardState(creature).isInZone(ZoneType.Graveyard));
    }
}
