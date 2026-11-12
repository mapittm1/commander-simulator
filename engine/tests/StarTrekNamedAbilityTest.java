package forge.ai;

import forge.card.ICardFace;
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
import java.util.function.Predicate;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class StarTrekNamedAbilityTest extends AITest {
    private void collectTriggers(Game game) {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private void cast(Player player, Card card) {
        SpellAbility spell = card.getFirstSpellAbility();
        spell.setActivatingPlayer(player);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(player, spell, null));
    }

    @Test
    public void kaiWinnTracksNamedSpellAndNamedManaAbilityUsedDuringPayment() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Kai Winn names Sol Ring"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public String chooseCardName(SpellAbility ability, Predicate<ICardFace> filter, String valid, String message) {
                return "Sol Ring";
            }
        });
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        addCardToZone("Swamp", actor, ZoneType.Battlefield);
        addCardToZone("Swamp", actor, ZoneType.Battlefield);
        Card kaiWinn = addCardToZone("Kai Winn, Faithless Leader", actor, ZoneType.Hand);
        cast(actor, kaiWinn);
        game.getStack().resolveStack();
        Card entered = game.getCardState(kaiWinn);
        assertEquals(entered.getNamedCard(), "Sol Ring");
        assertTrue(game.getStack().isEmpty());

        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent, 2);
        addCardToZone("Forest", opponent, ZoneType.Battlefield);
        Card ring = addCardToZone("Sol Ring", opponent, ZoneType.Hand);
        int life = opponent.getLife();
        cast(opponent, ring);
        collectTriggers(game);
        assertEquals(game.getStack().size(), 2);
        assertSame(game.getStack().peekAbility().getHostCard(), entered);
        game.getStack().resolveStack();
        assertEquals(opponent.getLife(), life - 3);
        game.getStack().resolveStack();

        Card stone = addCardToZone("Mind Stone", opponent, ZoneType.Hand);
        cast(opponent, stone);
        assertTrue(game.getCardState(ring).isTapped());
        collectTriggers(game);
        assertEquals(game.getStack().size(), 2);
        assertSame(game.getStack().peekAbility().getHostCard(), entered);
        game.getStack().resolveStack();
        assertEquals(opponent.getLife(), life - 6);
        game.getStack().resolveStack();
        assertTrue(game.getCardState(stone).isInPlay());
    }
}
