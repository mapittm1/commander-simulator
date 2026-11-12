package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class StarTrekRevealTest extends AITest {
    @Test
    public void hitItRevealsAllFiveToEveryOpponentAndMovesOneLand() {
        verifyHitIt(true);
    }

    @Test
    public void hitItRevealsAllFiveEvenWithoutALandAndDoesNotMill() {
        verifyHitIt(false);
    }

    private void verifyHitIt(boolean containsLand) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Hit It reveal"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        List<List<Set<Integer>>> observed = new ArrayList<>();
        for (Player opponent : actor.getOpponents()) {
            List<Set<Integer>> seen = new ArrayList<>();
            observed.add(seen);
            opponent.dangerouslySetController(new PlayerControllerAi(game, opponent, opponent.getLobbyPlayer()) {
                @Override
                public void reveal(CardCollectionView cards, ZoneType zone, Player owner, String prefix, boolean addSuffix) {
                    if (zone == ZoneType.Library && owner == actor) {
                        seen.add(cards.stream().map(Card::getId).collect(Collectors.toSet()));
                    }
                    super.reveal(cards, zone, owner, prefix, addSuffix);
                }
            });
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        addCardToZone("Mountain", actor, ZoneType.Battlefield);
        addCardToZone("Forest", actor, ZoneType.Battlefield);
        addCardToZone("Plains", actor, ZoneType.Battlefield);
        addCardToZone("Zellix, Sanity Flayer", actor, ZoneType.Battlefield);
        List<Card> top = new ArrayList<>();
        Card first = addCardToZone(containsLand ? "Island" : "Grizzly Bears", actor, ZoneType.Library);
        top.add(first);
        for (String name : List.of("Runeclaw Bear", "Silvercoat Lion", "Air Elemental", "Centaur Courser")) {
            top.add(addCardToZone(name, actor, ZoneType.Library));
        }
        Card sixth = addCardToZone("Swamp", actor, ZoneType.Library);
        assertEquals(actor.getCardsIn(ZoneType.Library).get(5).getId(), sixth.getId());
        Card enchantment = addCardToZone("Hit It!", actor, ZoneType.Hand);
        game.getAction().checkStateEffects(true);

        SpellAbility spell = enchantment.getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null));
        game.getStack().resolveStack();
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertEquals(game.getStack().size(), 1);
        assertEquals(game.getStack().peekAbility().getHostCard().getId(), enchantment.getId());
        game.getStack().resolveStack();
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertTrue(game.getStack().isEmpty(), "Moving cards to the graveyard does not trigger Zellix");
        assertEquals(actor.getCardsIn(ZoneType.Library).size(), 1);
        assertEquals(actor.getCardsIn(ZoneType.Library).getFirst().getId(), sixth.getId());
        for (Card card : top) {
            ZoneType expected = containsLand && card == first ? ZoneType.Battlefield : ZoneType.Graveyard;
            assertEquals(game.getZoneOf(game.getCardState(card)).getZoneType(), expected);
        }
        if (containsLand) {
            assertTrue(game.getCardState(first).isTapped());
        }
        Set<Integer> ids = top.stream().map(Card::getId).collect(Collectors.toSet());
        assertEquals(observed.size(), 3);
        for (List<Set<Integer>> seen : observed) {
            assertEquals(seen, List.of(ids));
        }
    }
}
