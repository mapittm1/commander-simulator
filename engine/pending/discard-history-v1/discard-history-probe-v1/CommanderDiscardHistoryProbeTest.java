package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.game.trigger.TriggerWaiting;
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
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Probe real cycling payments and Hollow One discounts through copied supplied boards. */
public class CommanderDiscardHistoryProbeTest extends AITest {
    private Player actor(Game game) { return game.getPlayers().getFirst(); }

    private Game board() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("discard-history-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Discard history"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setStartingLife(40);
            for (int index = 0; index < 20; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        for (int index = 0; index < 5; index++) {
            addCardToZone("Island", actor(game), ZoneType.Battlefield);
            addCardToZone("Plains", actor(game), ZoneType.Battlefield);
        }
        addCardToZone("Hollow One", actor(game), ZoneType.Hand);
        addCardToZone("Hollow One", actor(game), ZoneType.Hand);
        addCardToZone("Hollow One", actor(game), ZoneType.Hand);
        game.getAction().checkStaticAbilities(false);
        game.getTriggerHandler().resetActiveTriggers();
        return game;
    }

    private Card named(Player player, ZoneType zone, String name) {
        for (Card card : player.getCardsIn(zone)) if (card.getName().equals(name)) return card;
        throw new AssertionError("Missing supplied card: " + name);
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

    private void settle(Game game) {
        for (int guard = 0; guard < 20; guard++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Discard triggers did not settle within the fixed bound");
    }

    private long tappedLands(Player player) {
        return player.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isLand() && card.isTapped()).count();
    }

    private void cycle(Game game) {
        Player player = actor(game);
        Card card = named(player, ZoneType.Hand, "Hollow One");
        SpellAbility action = card.getSpellAbilities().stream()
                .filter(SpellAbility::isCycling).findFirst().orElseThrow();
        action.setActivatingPlayer(player);
        assertTrue(action.canPlay());
        long before = tappedLands(player);
        assertTrue(new CostPayment(action.getPayCosts(), action).payComputerCosts(
                new AiCostDecision(player, action, false, true)));
        assertEquals(tappedLands(player) - before, 2L);
        game.getStack().add(action);
        GameSimulator.resolveStack(game, player);
        assertTrue(game.getStack().isEmpty());
        assertTrue(game.getCardState(card).isInZone(ZoneType.Graveyard));
    }

    private void castHollowOne(Game game, int expectedMana) {
        Player player = actor(game);
        Card card = named(player, ZoneType.Hand, "Hollow One");
        SpellAbility spell = card.getFirstSpellAbility();
        spell.setActivatingPlayer(player);
        assertTrue(spell.getRestrictions().canPlay(card, spell));
        Card onStack = game.getAction().moveToStack(card, spell);
        TriggerWaiting placement = game.getTriggerHandler().holdCastPlacement(onStack);
        spell.setHostCard(onStack);
        long before = tappedLands(player);
        assertTrue(new CostPayment(spell.getPayCosts(), spell).payComputerCosts(
                new AiCostDecision(player, spell, false, true)));
        assertEquals(tappedLands(player) - before, (long) expectedMana,
                "Native Hollow One payment includes cards cycled earlier this turn");
        game.getTriggerHandler().commitCastPlacement(placement);
        game.getStack().add(spell);
        GameSimulator.resolveStack(game, player);
        assertTrue(game.getStack().isEmpty());
        assertTrue(game.getCardState(onStack).isInZone(ZoneType.Battlefield));
    }

    private void check(int depth, int cycles) {
        Game original = board();
        for (int index = 0; index < cycles; index++) cycle(original);
        assertEquals(actor(original).getDiscardedThisTurn().size(), cycles);
        Game branch = copy(original, depth);
        castHollowOne(branch, 5 - 2 * cycles);
        assertEquals(actor(original).getDiscardedThisTurn().size(), cycles);
    }

    @Test public void noHistoryOriginalPaysFive() { check(0, 0); }
    @Test public void noHistoryAfterTwoCopiesPaysFive() { check(2, 0); }
    @Test public void oneCycleOriginalPaysThree() { check(0, 1); }
    @Test public void oneCycleAfterOneCopyPaysThree() { check(1, 1); }
    @Test public void oneCycleAfterTwoCopiesPaysThree() { check(2, 1); }
    @Test public void twoCyclesOriginalPaysOne() { check(0, 2); }
    @Test public void twoCyclesAfterOneCopyPaysOne() { check(1, 2); }
    @Test public void twoCyclesAfterTwoCopiesPaysOne() { check(2, 2); }
}
