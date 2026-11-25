package forge.ai;

import forge.ai.simulation.GameCopier;
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

/** Native nonstack foretell payments must preserve turn counts and player isolation in copied boards. */
public class CommanderForetellHistoryTest extends AITest {
    private Player actor(Game game) { return game.getPlayers().getFirst(); }

    private Game board(boolean discount) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("foretell-history-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Foretell history"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setStartingLife(40);
            for (int index = 0; index < 20; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Island", actor(game), ZoneType.Battlefield);
            addCardToZone("Plains", actor(game), ZoneType.Battlefield);
        }
        if (discount) addCardToZone("Ranar the Ever-Watchful", actor(game), ZoneType.Battlefield);
        addCardToZone("Behold the Multiverse", actor(game), ZoneType.Hand);
        addCardToZone("Behold the Multiverse", actor(game), ZoneType.Hand);
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
        fail("Foretell triggers did not settle within the fixed bound");
    }

    private long tappedLands(Player player) {
        return player.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isLand() && card.isTapped()).count();
    }

    private long spirits(Player player) {
        return player.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isToken() && card.getType().hasSubtype("Spirit")).count();
    }

    private void foretell(Game game, Player player, int mana) {
        Card card = named(player, ZoneType.Hand, "Behold the Multiverse");
        SpellAbility action = card.getSpellAbilities().stream()
                .filter(SpellAbility::isForetelling).findFirst().orElseThrow();
        action.setActivatingPlayer(player);
        assertTrue(action.canPlay(), "Use the actual native foretell special action");
        long before = tappedLands(player);
        assertTrue(new CostPayment(action.getPayCosts(), action).payComputerCosts(
                new AiCostDecision(player, action, false, true)));
        assertEquals(tappedLands(player) - before, (long) mana,
                "The actual foretell payment follows this player's first-action discount");
        action.resolve();
        settle(game);
        Card exiled = game.getCardState(card);
        assertTrue(exiled.isInZone(ZoneType.Exile));
        assertTrue(exiled.isForetold());
        assertTrue(exiled.isFaceDown());
        assertEquals(player.getSpellsCastThisGame(), 0, "Foretelling does not cast a spell");
        assertTrue(game.getStack().isEmpty());
    }

    private void check(int depth, boolean discount) {
        Game original = board(discount);
        foretell(original, actor(original), discount ? 0 : 2);
        assertEquals(actor(original).getNumForetoldThisTurn(), 1);
        assertEquals(spirits(actor(original)), discount ? 1L : 0L);
        Game branch = copy(original, depth);
        foretell(branch, actor(branch), 2);
        assertEquals(actor(branch).getNumForetoldThisTurn(), 2);
        assertEquals(spirits(actor(branch)), discount ? 2L : 0L);
        if (depth > 0) {
            assertEquals(actor(original).getNumForetoldThisTurn(), 1);
            assertEquals(spirits(actor(original)), discount ? 1L : 0L);
            assertTrue(named(actor(original), ZoneType.Hand, "Behold the Multiverse").isInZone(ZoneType.Hand));
        }
    }

    @Test public void originalDiscountOnlyAppliesToFirstForetell() { check(0, true); }
    @Test public void oneCopyDoesNotRestoreUsedDiscount() { check(1, true); }
    @Test public void twoCopiesDoNotRestoreUsedDiscount() { check(2, true); }
    @Test public void originalWithoutDiscountPaysBothActions() { check(0, false); }
    @Test public void copiedHistorySurvivesWithoutDiscountCard() { check(2, false); }
    @Test public void unusedDiscountSurvivesTwoCopies() {
        Game original = board(true);
        Game branch = copy(original, 2);
        foretell(branch, actor(branch), 0);
        assertEquals(actor(branch).getNumForetoldThisTurn(), 1);
        assertEquals(actor(original).getNumForetoldThisTurn(), 0);
        assertEquals(spirits(actor(original)), 0L);
        foretell(branch, actor(branch), 2);
        assertEquals(actor(branch).getNumForetoldThisTurn(), 2);
        assertEquals(spirits(actor(branch)), 2L);
    }
    @Test public void anotherPlayersUnusedDiscountStaysSeparate() {
        Game original = board(true);
        Player other = original.getPlayers().get(1);
        addCardToZone("Ranar the Ever-Watchful", other, ZoneType.Battlefield);
        addCardToZone("Behold the Multiverse", other, ZoneType.Hand);
        foretell(original, actor(original), 0);
        Game branch = copy(original, 2);
        Player copiedOther = branch.getPlayers().get(1);
        branch.getPhaseHandler().devModeSet(PhaseType.MAIN1, copiedOther, 5);
        foretell(branch, copiedOther, 0);
        assertEquals(copiedOther.getNumForetoldThisTurn(), 1);
        assertEquals(actor(branch).getNumForetoldThisTurn(), 1);
        assertEquals(other.getNumForetoldThisTurn(), 0);
        assertEquals(actor(original).getNumForetoldThisTurn(), 1);
        assertEquals(spirits(copiedOther), 1L);
        assertEquals(spirits(other), 0L);
    }
    @Test public void emptyHistoryDoesNotInventForetellEvents() {
        Game original = board(true);
        Game branch = copy(original, 2);
        assertEquals(actor(branch).getNumForetoldThisTurn(), 0);
        assertEquals(actor(original).getNumForetoldThisTurn(), 0);
        assertEquals(spirits(actor(branch)), 0L);
        assertEquals(actor(branch).getSpellsCastThisGame(), 0);
    }

    private void offTurnDiscount(int depth) {
        Game original = board(true);
        addCardToZone("Cosmos Charger", actor(original), ZoneType.Battlefield);
        original.getAction().checkStaticAbilities(false);
        original.getTriggerHandler().resetActiveTriggers();
        original.getPhaseHandler().devModeSet(PhaseType.MAIN1, original.getPlayers().get(1), 5);
        foretell(original, actor(original), 0);
        assertEquals(actor(original).getNumForetoldThisTurn(), 1);
        Game branch = copy(original, depth);
        foretell(branch, actor(branch), 1);
        assertEquals(actor(branch).getNumForetoldThisTurn(), 2);
        assertEquals(spirits(actor(branch)), 2L);
        if (depth > 0) {
            assertEquals(actor(original).getNumForetoldThisTurn(), 1);
            assertEquals(spirits(actor(original)), 1L);
        }
    }

    @Test public void originalOffTurnForetellCombinesNativeModifiers() { offTurnDiscount(0); }
    @Test public void oneCopyKeepsUsedOffTurnDiscount() { offTurnDiscount(1); }
    @Test public void twoCopiesKeepUsedOffTurnDiscount() { offTurnDiscount(2); }
    @Test public void discountAloneDoesNotPermitOtherPlayersTurn() {
        Game original = board(true);
        Game branch = copy(original, 2);
        branch.getPhaseHandler().devModeSet(PhaseType.MAIN1, branch.getPlayers().get(1), 5);
        Card card = named(actor(branch), ZoneType.Hand, "Behold the Multiverse");
        SpellAbility action = card.getSpellAbilities().stream()
                .filter(SpellAbility::isForetelling).findFirst().orElseThrow();
        action.setActivatingPlayer(actor(branch));
        assertFalse(action.canPlay(), "A cost reduction alone does not grant off-turn permission");
        assertEquals(actor(branch).getNumForetoldThisTurn(), 0);
        assertEquals(tappedLands(actor(branch)), 0L);
        assertEquals(spirits(actor(branch)), 0L);
    }
}
