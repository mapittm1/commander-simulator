package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.cost.CostPayment;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerWaiting;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Paid spells from supplied boards test cumulative expend thresholds across world copies. */
public class CommanderExpendHistoryTest extends AITest {
    private static final String[] SPELLS = {"Llanowar Elves", "Elvish Mystic", "Fyndhorn Elves",
            "Arbor Elf", "Jaspera Sentinel", "Quirion Ranger", "Elvish Reclaimer", "Skyshroud Ranger"};

    private Player actor(Game game) { return game.getPlayers().getFirst(); }

    private Game board() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("expend-history-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Expend history"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(40, null);
            for (int index = 0; index < 12; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        addCardToZone("Muerra, Trash Tactician", actor(game), ZoneType.Battlefield);
        for (int index = 0; index < 8; index++) addCardToZone("Forest", actor(game), ZoneType.Battlefield);
        for (String name : SPELLS) addCardToZone(name, actor(game), ZoneType.Hand);
        game.getAction().checkStaticAbilities(false);
        game.getTriggerHandler().resetActiveTriggers();
        return game;
    }

    private Game copy(Game game, int depth) {
        for (int index = 0; index < depth; index++) {
            game.getAction().checkStaticAbilities(false);
            game.getTriggerHandler().resetActiveTriggers();
            game.copyLastState();
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game));
            copier.release();
        }
        return game;
    }

    private void cast(Game game, String name) {
        Player payer = actor(game);
        Card card = null;
        for (Card candidate : payer.getCardsIn(ZoneType.Hand)) {
            if (candidate.getName().equals(name)) card = candidate;
        }
        assertNotNull(card);
        SpellAbility ability = card.getFirstSpellAbility();
        ability.setActivatingPlayer(payer);
        assertTrue(ability.getRestrictions().canPlay(card, ability));
        int before = payer.getExpentThisTurn();
        int life = payer.getLife();
        assertTrue(ComputerUtilCost.canPayCost(ability, payer, false));
        assertEquals(payer.getExpentThisTurn(), before, "Cost forecasting does not spend live mana");
        assertEquals(payer.getLife(), life);
        Card onStack = game.getAction().moveToStack(card, ability);
        TriggerWaiting placement = game.getTriggerHandler().holdCastPlacement(onStack);
        ability.setHostCard(onStack);
        assertTrue(new CostPayment(ability.getPayCosts(), ability).payComputerCosts(
                new AiCostDecision(payer, ability, false, true)));
        game.getTriggerHandler().commitCastPlacement(placement);
        game.getStack().add(ability);
        GameSimulator.resolveStack(game, payer);
        assertTrue(game.getStack().isEmpty());
        assertTrue(onStack.isInZone(ZoneType.Battlefield), "The paid creature spell actually resolves");
    }

    private void fourthMana(int depth) {
        Game original = board();
        for (int index = 0; index < 3; index++) cast(original, SPELLS[index]);
        assertEquals(actor(original).getExpentThisTurn(), 3);
        assertEquals(actor(original).getLife(), 40);
        Game game = copy(original, depth);
        cast(game, SPELLS[3]);
        assertEquals(actor(game).getLife(), 43, "The fourth paid mana fires Muerra once");
        assertEquals(actor(game).getExpentThisTurn(), 4);
        assertEquals(game.getPlayers().get(1).getLife(), 40);
        if (depth > 0) {
            assertEquals(actor(original).getLife(), 40);
            assertEquals(actor(original).getExpentThisTurn(), 3);
        }
    }

    @Test public void fourthPaidManaInOriginal() { fourthMana(0); }
    @Test public void fourthPaidManaAfterOneCopy() { fourthMana(1); }
    @Test public void fourthPaidManaAfterTwoCopies() { fourthMana(2); }

    @Test public void firstPaidManaDoesNotInventFourthManaTrigger() {
        Game game = copy(board(), 2);
        cast(game, SPELLS[0]);
        assertEquals(actor(game).getLife(), 40);
        assertEquals(actor(game).getExpentThisTurn(), 1);
    }

    @Test public void eighthPaidManaUsesNextThresholdWithoutRepeatingFourth() {
        Game original = board();
        for (int index = 0; index < 4; index++) cast(original, SPELLS[index]);
        assertEquals(actor(original).getLife(), 43);
        assertEquals(actor(original).getExpentThisTurn(), 4);
        Game game = copy(original, 2);
        for (int index = 4; index < 8; index++) cast(game, SPELLS[index]);
        assertEquals(actor(game).getLife(), 43, "Expend4 does not repeat after a copied history");
        assertEquals(actor(game).getExpentThisTurn(), 8);
        assertEquals(actor(game).getCardsIn(ZoneType.Exile).size(), 2, "The distinct expend8 trigger resolves");
        assertEquals(actor(original).getCardsIn(ZoneType.Exile).size(), 0);
        assertEquals(actor(original).getExpentThisTurn(), 4);
    }

    @Test public void historiesRemainIndependentForEachPlayer() {
        Game original = board();
        actor(original).setExpentThisTurn(3);
        original.getPlayers().get(1).setExpentThisTurn(7);
        Game game = copy(original, 2);
        assertEquals(actor(game).getExpentThisTurn(), 3);
        assertEquals(game.getPlayers().get(1).getExpentThisTurn(), 7);
        assertEquals(game.getPlayers().get(2).getExpentThisTurn(), 0);
        actor(game).setExpentThisTurn(99);
        assertEquals(actor(original).getExpentThisTurn(), 3);
        assertEquals(game.getPlayers().get(1).getExpentThisTurn(), 7);
    }
}
