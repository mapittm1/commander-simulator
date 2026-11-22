package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCopyService;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Protector snapshots, native predicates and paid counter-ability witnesses; actions are supplied. */
public class CommanderBattleProtectorTest extends AITest {
    @Test public void paidBattleEstablishesANativeOpponentProtectorAndDefense() {
        Board board = board();
        assertTrue(board.battle.isBattle());
        assertTrue(board.battle.isInZone(ZoneType.Battlefield));
        assertNotNull(board.battle.getProtectingPlayer());
        assertTrue(board.actor.getOpponents().contains(board.battle.getProtectingPlayer()));
        assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE), 3);
        assertSame(board.battle.getView().getProtectingPlayer(), board.battle.getProtectingPlayer().getView());
    }

    @Test public void aLiveCopiedBattleRetainsItsMappedProtector() {
        Board board = board(); Player original = board.battle.getProtectingPlayer();
        GameCopier copier = copy(board); Card battle = copier.find(board.battle);
        assertSame(battle.getProtectingPlayer(), copier.find(original));
        assertSame(battle.getProtectingPlayer().getGame(), copier.getCopiedGame());
        assertSame(battle.getView().getProtectingPlayer(), copier.find(original).getView());
        assertEquals(battle.getCounters(CounterEnumType.DEFENSE), 3);
    }

    @Test public void sameGameLastKnownInformationRetainsTheProtector() {
        Board board = board();
        Card history = CardCopyService.getLKICopy(board.battle);
        assertTrue(history.isBattle());
        assertSame(history.getProtectingPlayer(), board.battle.getProtectingPlayer());
        assertSame(history.getView().getProtectingPlayer(), board.battle.getProtectingPlayer().getView());
        assertEquals(history.getCounters(CounterEnumType.DEFENSE), 3);
    }

    /** Native property evaluation on LKI, not a new spell or a legal attack-admission claim. */
    @Test public void nativeOpponentProtectorPropertyRemainsTrueOnLastKnownInformation() {
        Board board = board();
        assertTrue(board.battle.isValid("Battle.OppProtect", board.actor, board.battle, null));
        Card history = CardCopyService.getLKICopy(board.battle);
        assertTrue(history.isValid("Battle.OppProtect", board.actor, board.battle, null));
    }

    /** Explicit chosen-card history, not an added printed Grizzly Bears ability. */
    @Test public void aHistoricalBattleProtectorMapsIntoTheCopiedWorld() {
        Board board = board(); Player protector = board.battle.getProtectingPlayer();
        Card host = addCardToZone("Grizzly Bears", board.actor, ZoneType.Battlefield);
        host.setChosenCards(new CardCollection(board.battle));
        Card moved = board.game.getAction().moveToGraveyard(board.battle, null);
        assertNotEquals(moved.getGameTimestamp(), board.battle.getGameTimestamp());
        GameCopier copier = copy(board); Card history = copier.find(host).getChosenCard();
        assertNotNull(history); assertNotSame(history, copier.find(moved));
        assertEquals(history.getGameTimestamp(), board.battle.getGameTimestamp());
        assertSame(history.getProtectingPlayer(), copier.find(protector));
        assertSame(history.getProtectingPlayer().getGame(), copier.getCopiedGame());
        assertSame(history.getView().getProtectingPlayer(), copier.find(protector).getView());
    }

    @Test public void ordinaryNonbattleHistoryDoesNotAcquireAProtector() {
        Board board = board(); Card card = addCardToZone("Grizzly Bears", board.actor, ZoneType.Battlefield);
        Card history = CardCopyService.getLKICopy(card);
        assertNull(card.getProtectingPlayer()); assertNull(history.getProtectingPlayer());
        assertNull(history.getView().getProtectingPlayer());
    }

    @Test public void nativeTrackerRemovesACounterWhenAnOpponentProtectsTheBattle() {
        Board board = board(); Tracker tracker = activateTracker(board, board.actor, board.battle);
        settle(board.game);
        assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE), 2);
        assertTrue(tracker.card.isTapped());
    }

    /** Native resolution accepts same-incarnation LKI target metadata and resolves the physical card. */
    @Test public void nativeTrackerWithHistoricalTargetRemovesTheOpponentsCounter() {
        Board board = board(); Card history = CardCopyService.getLKICopy(board.battle);
        activateTracker(board, board.actor, history); settle(board.game);
        assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE), 2);
    }

    @Test public void nativeTrackerAddsACounterWhenItsActivatorProtectsTheBattle() {
        Board board = board(); Player protector = board.battle.getProtectingPlayer();
        activateTracker(board, protector, board.battle); settle(board.game);
        assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE), 4);
    }

    @Test public void historicalSelfProtectedTargetRetainsTheAddCounterBranch() {
        Board board = board(); Player protector = board.battle.getProtectingPlayer();
        Card history = CardCopyService.getLKICopy(board.battle);
        activateTracker(board, protector, history); settle(board.game);
        assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE), 4);
    }

    @Test public void aCopiedPaidHistoricalTargetActivationChangesOnlyTheCopiedBattle() {
        Board board = board(); Card history = CardCopyService.getLKICopy(board.battle);
        Tracker tracker = activateTracker(board, board.actor, history);
        GameCopier copier = new GameCopier(board.game).withStack(true);
        Game game = copier.makeCopy(null, board.actor);
        assertEquals(game.getStack().size(), 1); settle(game);
        assertEquals(copier.find(board.battle).getCounters(CounterEnumType.DEFENSE), 2);
        assertTrue(copier.find(tracker.card).isTapped());
        assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE), 3);
        assertEquals(board.game.getStack().size(), 1); assertTrue(tracker.card.isTapped());
    }

    @Test public void aStifledTrackerKeepsItsTapCostWithoutChangingDefense() {
        Board board = board(); Tracker tracker = activateTracker(board, board.actor,
                CardCopyService.getLKICopy(board.battle));
        Player responder = board.battle.getProtectingPlayer();
        addCardToZone("Island", responder, ZoneType.Battlefield);
        Card card = addCardToZone("Stifle", responder, ZoneType.Hand);
        SpellAbility response = card.getFirstSpellAbility(); response.setActivatingPlayer(responder);
        response.getTargets().add(board.game.getStack().peekAbility());
        assertTrue(ComputerUtil.handlePlayingSpellAbility(responder, response, null)); settle(board.game);
        assertEquals(board.battle.getCounters(CounterEnumType.DEFENSE), 3);
        assertTrue(tracker.card.isTapped()); assertEquals(response.getPayingMana().size(), 1);
        assertTrue(board.game.getCardState(response.getHostCard()).isInZone(ZoneType.Graveyard));
    }

    /** A valid replacement designation is explicit metadata setup, not a printed Battle ability. */
    @Test public void historicalProtectorDoesNotFollowALaterLiveDesignation() {
        Board board = board(); Player old = board.battle.getProtectingPlayer(), replacement = another(board, old);
        Card history = CardCopyService.getLKICopy(board.battle);
        Card host = remember(board, history); board.battle.setProtectingPlayer(replacement);
        GameCopier copier = copy(board);
        assertSame(copier.find(board.battle).getProtectingPlayer(), copier.find(replacement));
        Card oldState = copier.find(host).getChosenCard();
        assertSame(oldState.getProtectingPlayer(), copier.find(old));
        assertSame(oldState.getView().getProtectingPlayer(), copier.find(old).getView());
    }

    @Test public void aDepartedHistoricalProtectorRemainsAMappedParticipant() {
        Board board = board(); Player old = board.battle.getProtectingPlayer();
        Card history = CardCopyService.getLKICopy(board.battle); Card host = remember(board, history);
        old.concede(); settle(board.game);
        assertTrue(old.hasLost()); assertNotSame(board.battle.getProtectingPlayer(), old);
        GameCopier copier = copy(board); Player mapped = copier.find(host).getChosenCard().getProtectingPlayer();
        assertNotNull(mapped); assertSame(mapped, copier.find(old));
        assertSame(mapped.getGame(), copier.getCopiedGame()); assertTrue(mapped.hasLost());
    }

    @Test public void nestedHistoricalCopiesMapTheProtectorTwice() {
        Board board = board(); Player old = board.battle.getProtectingPlayer();
        Card host = remember(board, CardCopyService.getLKICopy(board.battle));
        GameCopier first = copy(board);
        GameCopier second = new GameCopier(first.getCopiedGame()).withStack(true);
        Game game = second.makeCopy(null, first.find(board.actor));
        Card history = second.find(first.find(host)).getChosenCard();
        assertSame(history.getProtectingPlayer(), second.find(first.find(old)));
        assertSame(history.getProtectingPlayer().getGame(), game);
        assertNotSame(history.getProtectingPlayer(), first.find(old));
    }

    @Test public void changingACopiedHistoricalProtectorDoesNotChangeEitherOriginal() {
        Board board = board(); Player old = board.battle.getProtectingPlayer(), other = another(board, old);
        Card history = CardCopyService.getLKICopy(board.battle); Card host = remember(board, history);
        GameCopier copier = copy(board); Card copied = copier.find(host).getChosenCard();
        copied.setProtectingPlayer(copier.find(other));
        assertSame(copied.getView().getProtectingPlayer(), copier.find(other).getView());
        assertSame(history.getProtectingPlayer(), old); assertSame(history.getView().getProtectingPlayer(), old.getView());
        assertSame(board.battle.getProtectingPlayer(), old);
    }

    @Test public void aPendingBattleHasNoProtectorUntilItsCopiedNativeEntry() {
        Board board = board();
        for (int index = 0; index < 2; index++) addCardToZone("Plains", board.actor, ZoneType.Battlefield);
        Card card = addCardToZone("Invasion of Gobakhan", board.actor, ZoneType.Hand);
        SpellAbility action = card.getFirstSpellAbility(); action.setActivatingPlayer(board.actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(board.actor, action, null));
        Card pending = action.getHostCard(); assertTrue(pending.isInZone(ZoneType.Stack));
        assertNull(pending.getProtectingPlayer());
        GameCopier copier = new GameCopier(board.game).withStack(true); Game game = copier.makeCopy(null, board.actor);
        assertEquals(game.getStack().size(), 1); assertNull(copier.find(pending).getProtectingPlayer());
        settle(game); Card entered = game.getCardState(copier.find(pending));
        assertTrue(entered.isInZone(ZoneType.Battlefield)); assertNotNull(entered.getProtectingPlayer());
        assertTrue(copier.find(board.actor).getOpponents().contains(entered.getProtectingPlayer()));
        assertEquals(entered.getCounters(CounterEnumType.DEFENSE), 3);
        assertTrue(pending.isInZone(ZoneType.Stack)); assertNull(pending.getProtectingPlayer());
    }

    /** A matured tracker is an explicit legal board precondition; activation and tap payment are native. */
    private Tracker activateTracker(Board board, Player player, Card target) {
        board.game.getPhaseHandler().devModeSet(PhaseType.MAIN1, player, 3);
        Card tracker = addCardToZone("Portent Tracker", player, ZoneType.Battlefield); tracker.setSickness(false);
        SpellAbility action = tracker.getSpellAbilities().stream()
                .filter(ability -> ability.getApi() == forge.game.ability.ApiType.AddOrRemoveCounter)
                .findFirst().orElseThrow();
        action.setActivatingPlayer(player); action.getTargets().add(target);
        assertTrue(action.canPlay());
        assertTrue(ComputerUtil.handlePlayingSpellAbility(player, action, null)); assertTrue(tracker.isTapped());
        return new Tracker(tracker, action);
    }

    /** Chosen-card storage is a structural history reference, not a printed Grizzly Bears ability. */
    private Card remember(Board board, Card history) {
        Card host = addCardToZone("Grizzly Bears", board.actor, ZoneType.Battlefield);
        host.setChosenCards(new CardCollection(history));
        return host;
    }

    private Player another(Board board, Player protector) {
        return board.actor.getOpponents().stream().filter(player -> player != protector).findFirst().orElseThrow();
    }

    private record Tracker(Card card, SpellAbility action) { }

    private GameCopier copy(Board board) {
        assertTrue(board.game.getStack().isEmpty());
        GameCopier copier = new GameCopier(board.game).withStack(true);
        Game game = copier.makeCopy(null, board.actor);
        assertTrue(game.getStack().isEmpty());
        return copier;
    }

    private Board board() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("battle-protector-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Battle protector research"));
        game.setNoGUIUser(); game.setAge(GameStage.Play); Player actor = game.getPlayers().get(0);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 8; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        for (int index = 0; index < 2; index++) addCardToZone("Plains", actor, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true); game.getTriggerHandler().resetActiveTriggers(); game.copyLastState();
        Card source = addCardToZone("Invasion of Gobakhan", actor, ZoneType.Hand);
        SpellAbility spell = source.getFirstSpellAbility(); spell.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null)); settle(game);
        assertEquals(actor.getSpellsCastThisTurn(), 1);
        assertEquals(spell.getPayingMana().size(), 2);
        assertTrue(actor.getLandsInPlay().stream().allMatch(Card::isTapped));
        Card battle = game.getCardState(spell.getHostCard());
        assertTrue(battle.isInZone(ZoneType.Battlefield));
        assertNotNull(battle.getProtectingPlayer());
        return new Board(game, actor, battle);
    }

    private void settle(Game game) {
        for (int step = 0; step < 40; step++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        throw new AssertionError("Battle research stack did not settle");
    }

    private record Board(Game game, Player actor, Card battle) { }
}
