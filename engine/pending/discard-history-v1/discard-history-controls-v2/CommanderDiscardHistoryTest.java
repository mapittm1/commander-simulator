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

/** Paid cycling, casting discounts, LKI provenance and isolated copied discard history. */
public class CommanderDiscardHistoryTest extends AITest {
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

    private long tappedLands(Player player) {
        return player.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isLand() && card.isTapped()).count();
    }

    private void cycle(Game game) { cycle(game, actor(game)); }

    private void cycle(Game game, Player player) {
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

    private void copiedLki(int depth) {
        Game original = board();
        cycle(original);
        Card originalReceipt = actor(original).getDiscardedThisTurn().get(0);
        assertTrue(originalReceipt.isLKI());
        Game branch = copy(original, depth);
        assertEquals(actor(branch).getDiscardedThisTurn().size(), 1);
        Card copiedReceipt = actor(branch).getDiscardedThisTurn().get(0);
        assertNotSame(copiedReceipt, originalReceipt);
        assertTrue(copiedReceipt.isLKI());
        assertSame(copiedReceipt.getGame(), branch);
        assertSame(copiedReceipt.getOwner(), actor(branch));
        assertSame(copiedReceipt.getController(), actor(branch));
        assertEquals(copiedReceipt.getName(), "Hollow One");
        assertTrue(copiedReceipt.isCreature());
        assertTrue(copiedReceipt.isArtifact());
        assertTrue(copiedReceipt.equalsWithGameTimestamp(originalReceipt));
        assertNotSame(copiedReceipt, named(actor(branch), ZoneType.Graveyard, "Hollow One"));
        castHollowOne(branch, 3);
    }

    private void independentBranches(int depth) {
        Game original = board();
        cycle(original);
        Game left = copy(original, depth);
        Game right = copy(original, depth);
        cycle(left);
        assertEquals(actor(left).getDiscardedThisTurn().size(), 2);
        assertEquals(actor(right).getDiscardedThisTurn().size(), 1);
        assertEquals(actor(original).getDiscardedThisTurn().size(), 1);
        castHollowOne(left, 1);
        castHollowOne(right, 3);
        castHollowOne(original, 3);
    }

    private void opponentHistory(int depth) {
        Game original = board();
        Player other = original.getPlayers().get(1);
        addCardToZone("Hollow One", other, ZoneType.Hand);
        addCardToZone("Island", other, ZoneType.Battlefield);
        addCardToZone("Island", other, ZoneType.Battlefield);
        cycle(original, other);
        assertEquals(other.getDiscardedThisTurn().size(), 1);
        Game branch = copy(original, depth);
        assertEquals(branch.getPlayers().get(1).getDiscardedThisTurn().size(), 1);
        assertTrue(actor(branch).getDiscardedThisTurn().isEmpty());
        castHollowOne(branch, 5);
    }

    private void cleanupBranch(int depth) {
        Game original = board();
        cycle(original);
        Game branch = copy(original, depth);
        assertEquals(actor(branch).getDiscardedThisTurn().size(), 1);
        actor(branch).onCleanupPhase();
        assertTrue(actor(branch).getDiscardedThisTurn().isEmpty());
        assertEquals(actor(original).getDiscardedThisTurn().size(), 1);
        castHollowOne(branch, 5);
        castHollowOne(original, 3);
    }

    private void ordinaryGraveyardMove(int depth) {
        Game original = board();
        Card card = named(actor(original), ZoneType.Hand, "Hollow One");
        original.getAction().moveToGraveyard(card, null);
        assertTrue(actor(original).getDiscardedThisTurn().isEmpty());
        Game branch = copy(original, depth);
        assertTrue(actor(branch).getDiscardedThisTurn().isEmpty());
        castHollowOne(branch, 5);
    }

    @Test public void discardReceiptAfterOneCopyRetainsLkiAndOwnership() { copiedLki(1); }
    @Test public void discardReceiptAfterTwoCopiesRetainsLkiAndOwnership() { copiedLki(2); }
    @Test public void newDiscardAfterOneCopyDoesNotAffectSiblingOrOriginal() { independentBranches(1); }
    @Test public void newDiscardAfterTwoCopiesDoesNotAffectSiblingOrOriginal() { independentBranches(2); }
    @Test public void opponentDiscardAfterOneCopyDoesNotDiscountActor() { opponentHistory(1); }
    @Test public void opponentDiscardAfterTwoCopiesDoesNotDiscountActor() { opponentHistory(2); }
    @Test public void nativeCleanupAfterOneCopyClearsOnlyBranchHistory() { cleanupBranch(1); }
    @Test public void nativeCleanupAfterTwoCopiesClearsOnlyBranchHistory() { cleanupBranch(2); }
    @Test public void graveyardMoveAfterOneCopyDoesNotBecomeDiscard() { ordinaryGraveyardMove(1); }
    @Test public void graveyardMoveAfterTwoCopiesDoesNotBecomeDiscard() { ordinaryGraveyardMove(2); }
}
