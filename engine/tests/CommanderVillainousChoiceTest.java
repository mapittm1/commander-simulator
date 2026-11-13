package forge.ai;

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
import java.util.Map;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CommanderVillainousChoiceTest extends AITest {
    private record Decision(int seat, int life) {}

    @Test
    public void ensnaredResolvesOpponentsInTurnOrderStartingAfterTheActivePlayer() {
        Game game = newGame();
        Player actor = game.getPlayers().get(2);
        List<Decision> decisions = prepareOpponents(game, actor);
        resolveEnsnared(game, actor);

        assertEquals(decisions, List.of(new Decision(3, 40), new Decision(0, 40), new Decision(1, 40)));
        for (Player opponent : actor.getOpponents()) {
            assertEquals(opponent.getLife(), 36);
            assertEquals(opponent.getCardsIn(ZoneType.Exile).size(), 4);
        }
    }

    @Test
    public void valeyardResolvesEachChoiceBeforeAskingThatPlayerAgain() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        addCardToZone("The Valeyard", actor, ZoneType.Battlefield);
        List<Decision> decisions = prepareOpponents(game, actor);
        resolveEnsnared(game, actor);

        assertEquals(decisions, List.of(
                new Decision(1, 40), new Decision(1, 36),
                new Decision(2, 40), new Decision(2, 36),
                new Decision(3, 40), new Decision(3, 36)));
        for (Player opponent : actor.getOpponents()) {
            assertEquals(opponent.getLife(), 32);
            assertEquals(opponent.getCardsIn(ZoneType.Exile).size(), 8);
        }
    }

    private List<Decision> prepareOpponents(Game game, Player actor) {
        List<Decision> decisions = new ArrayList<>();
        for (int index = 0; index < game.getPlayers().size(); index++) {
            Player player = game.getPlayers().get(index);
            if (player.equals(actor)) {
                continue;
            }
            for (String name : List.of("Sol Ring", "Skullclamp", "Pithing Needle", "Manifold Key",
                    "Voltaic Key", "Springleaf Drum", "Wayfarer's Bauble", "Expedition Map")) {
                addCardToZone(name, player, ZoneType.Library);
            }
            int seat = index;
            player.dangerouslySetController(new PlayerControllerAi(game, player, player.getLobbyPlayer()) {
                @Override
                public List<SpellAbility> chooseSpellAbilitiesForEffect(List<SpellAbility> options,
                        SpellAbility ability, String title, int amount, Map<String, Object> params) {
                    if (ability.getHostCard().getName().equals("Ensnared by the Mara")) {
                        assertEquals(options.size(), 2);
                        decisions.add(new Decision(seat, player.getLife()));
                        return List.of(options.get(1));
                    }
                    return super.chooseSpellAbilitiesForEffect(options, ability, title, amount, params);
                }
            });
        }
        return decisions;
    }

    private void resolveEnsnared(Game game, Player actor) {
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        for (int index = 0; index < 4; index++) {
            addCardToZone("Mountain", actor, ZoneType.Battlefield);
        }
        Card ensnared = addCardToZone("Ensnared by the Mara", actor, ZoneType.Hand);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        SpellAbility spell = ensnared.getFirstSpellAbility();
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
        Game game = new Game(seats, rules, new Match(rules, seats, "Villainous choices"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        return game;
    }
}
