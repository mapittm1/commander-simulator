package forge.ai;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import forge.deck.Deck;
import forge.game.Game;
import forge.ai.simulation.GameCopier;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.card.CounterType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Native rules witnesses; paid casts here do not assert strategic AI admission. */
public class DrawEndingCoverageTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("draw" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Draw ending"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) {
            for (int count = 0; count < 12; count++) {
                for (String land : List.of("Plains", "Island", "Swamp")) {
                    addCardToZone(land, player, ZoneType.Battlefield);
                }
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        return game;
    }

    private Card current(Game game, Card card) {
        return game.getCardsInGame().stream()
                .filter(value -> value.getId() == card.getId()).findFirst().orElseThrow();
    }

    private SpellAbility cast(Game game, Player actor, String name, int mana, Card target) {
        Card card = addCardToZone(name, actor, ZoneType.Hand);
        SpellAbility action = card.getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        if (target != null) action.getTargets().add(target);
        assertTrue(ComputerUtil.playStack(action, actor, game), name);
        SpellAbility pending = game.getStack().peekAbility();
        assertEquals(pending.getPayingMana().size(), mana, name);
        return pending;
    }

    private Card enchantment(Game game, String name, int mana) {
        Player actor = game.getPlayers().get(0);
        Card source = cast(game, actor, name, mana, null).getHostCard();
        game.getStack().resolveStack();
        queue(game);
        assertTrue(game.getStack().isEmpty());
        return current(game, source);
    }

    private void count(Card card, CounterType type, int amount) {
        // Fixed prior counter state; separate tests assert actual ETB placement.
        Multiset<CounterType> values = HashMultiset.create(card.getCounters());
        values.setCount(type, amount);
        card.setCounters(values);
    }

    private void queue(Game game) {
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private void upkeep(Game game, Player player) {
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, player, 2);
        game.getTriggerHandler().resetActiveTriggers();
        game.getTriggerHandler().runTrigger(TriggerType.Phase,
                AbilityKey.mapFromPlayer(player), false);
        queue(game);
    }

    private void settle(Game game) {
        for (int guard = 0; guard < 30; guard++) {
            queue(game);
            if (game.isGameOver() || game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Draw fixture did not settle");
    }

    private void drawPending(Game game) {
        queue(game);
        assertFalse(game.isGameOver(), "The separate draw trigger needs a response window");
        assertFalse(game.getStack().isEmpty(), "The draw trigger must be on the stack");
        assertEquals(game.getStack().peekAbility().getApi(), ApiType.GameDrawn);
    }

    private void assertDraw(Game game) {
        settle(game);
        assertTrue(game.isGameOver());
        assertTrue(game.getOutcome().isDraw());
    }

    private void stifle(Game game, Player actor) {
        SpellAbility target = game.getStack().peekAbility();
        Card card = addCardToZone("Stifle", actor, ZoneType.Hand);
        SpellAbility action = card.getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        action.getTargets().add(target);
        assertTrue(ComputerUtil.playStack(action, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 1);
        game.getStack().resolveStack();
        queue(game);
    }

    @Test public void divineEntersWithTwoAndFirstUpkeepRemovesOnlyOne() {
        Game game = pod();
        Card source = enchantment(game, "Divine Intervention", 8);
        assertEquals(source.getCounters(CounterEnumType.INTERVENTION), 2);
        upkeep(game, game.getPlayers().get(0));
        settle(game);
        assertEquals(source.getCounters(CounterEnumType.INTERVENTION), 1);
        assertFalse(game.isGameOver());
    }

    @Test public void divineLastUpkeepCounterQueuesSeparateDrawTrigger() {
        Game game = pod();
        Card source = enchantment(game, "Divine Intervention", 8);
        count(source, CounterEnumType.INTERVENTION, 1);
        upkeep(game, game.getPlayers().get(0));
        game.getStack().resolveStack();
        assertEquals(source.getCounters(CounterEnumType.INTERVENTION), 0);
        drawPending(game);
        assertDraw(game);
    }

    @Test public void divineEnteringWithoutCountersDoesNotDrawOnUpkeep() {
        Game game = pod();
        enchantment(game, "Solemnity", 3);
        Card source = enchantment(game, "Divine Intervention", 8);
        assertEquals(source.getCounters(CounterEnumType.INTERVENTION), 0);
        upkeep(game, game.getPlayers().get(0));
        settle(game);
        assertFalse(game.isGameOver());
    }

    @Test public void divineOwnPaidAetherSnapQueuesDrawOutsideUpkeep() {
        Game game = pod();
        Card source = enchantment(game, "Divine Intervention", 8);
        cast(game, game.getPlayers().get(0), "Aether Snap", 5, null);
        game.getStack().resolveStack();
        assertEquals(source.getCounters(CounterEnumType.INTERVENTION), 0);
        drawPending(game);
        assertDraw(game);
    }

    @Test public void divineEnemyRemovingLastCounterDoesNotTriggerDraw() {
        Game game = pod();
        Card source = enchantment(game, "Divine Intervention", 8);
        cast(game, game.getPlayers().get(1), "Aether Snap", 5, null);
        settle(game);
        assertEquals(source.getCounters(CounterEnumType.INTERVENTION), 0);
        assertFalse(game.isGameOver());
    }

    @Test public void divineUpkeepRemovalCanBeCountered() {
        Game game = pod();
        Card source = enchantment(game, "Divine Intervention", 8);
        count(source, CounterEnumType.INTERVENTION, 1);
        upkeep(game, game.getPlayers().get(0));
        stifle(game, game.getPlayers().get(1));
        settle(game);
        assertEquals(source.getCounters(CounterEnumType.INTERVENTION), 1);
        assertFalse(game.isGameOver());
    }

    @Test public void divineDrawTriggerCanBeCounteredAfterRemoval() {
        Game game = pod();
        Card source = enchantment(game, "Divine Intervention", 8);
        count(source, CounterEnumType.INTERVENTION, 1);
        upkeep(game, game.getPlayers().get(0));
        game.getStack().resolveStack();
        drawPending(game);
        stifle(game, game.getPlayers().get(1));
        settle(game);
        assertEquals(source.getCounters(CounterEnumType.INTERVENTION), 0);
        assertFalse(game.isGameOver());
    }

    @Test public void divinePendingDrawSurvivesSourceDestruction() {
        Game game = pod();
        Card source = enchantment(game, "Divine Intervention", 8);
        cast(game, game.getPlayers().get(0), "Aether Snap", 5, null);
        game.getStack().resolveStack();
        drawPending(game);
        cast(game, game.getPlayers().get(1), "Disenchant", 2, source);
        game.getStack().resolveStack();
        assertEquals(current(game, source).getZone().getZoneType(), ZoneType.Graveyard);
        assertDraw(game);
    }

    @Test public void divinePendingDrawDoesNotRecheckNewCounter() {
        Game game = pod();
        Card source = enchantment(game, "Divine Intervention", 8);
        cast(game, game.getPlayers().get(0), "Aether Snap", 5, null);
        game.getStack().resolveStack();
        drawPending(game);
        // Fixed state established after the trigger; not a counter-placement witness.
        count(source, CounterEnumType.INTERVENTION, 1);
        assertDraw(game);
    }

    @Test public void divineOpponentUpkeepDoesNotRemoveCounter() {
        Game game = pod();
        Card source = enchantment(game, "Divine Intervention", 8);
        upkeep(game, game.getPlayers().get(1));
        settle(game);
        assertEquals(source.getCounters(CounterEnumType.INTERVENTION), 2);
        assertFalse(game.isGameOver());
    }

    @Test public void divineDrawIgnoresCannotLoseEffects() {
        Game game = pod();
        enchantment(game, "Divine Intervention", 8);
        addCardToZone("Platinum Angel", game.getPlayers().get(0), ZoneType.Battlefield);
        addCardToZone("Abyssal Persecutor", game.getPlayers().get(1), ZoneType.Battlefield);
        cast(game, game.getPlayers().get(0), "Aether Snap", 5, null);
        game.getStack().resolveStack();
        drawPending(game);
        assertDraw(game);
    }

    @Test public void celestialEntersWithSevenAndFirstUpkeepDoesNotEndGame() {
        Game game = pod();
        Card source = enchantment(game, "Celestial Convergence", 4);
        assertEquals(source.getCounters(CounterEnumType.OMEN), 7);
        upkeep(game, game.getPlayers().get(0));
        settle(game);
        assertEquals(source.getCounters(CounterEnumType.OMEN), 6);
        assertFalse(game.isGameOver());
    }

    @Test public void celestialUniqueOpponentHighestWins() {
        Game game = pod();
        Card source = enchantment(game, "Celestial Convergence", 4);
        count(source, CounterEnumType.OMEN, 1);
        Player winner = game.getPlayers().get(2);
        winner.setLife(47, null);
        upkeep(game, game.getPlayers().get(0));
        settle(game);
        assertTrue(game.isGameOver());
        assertFalse(game.getOutcome().isDraw());
        assertTrue(game.getOutcome().isWinner(winner.getLobbyPlayer()));
    }

    @Test public void celestialTiedHighestDraws() {
        Game game = pod();
        Card source = enchantment(game, "Celestial Convergence", 4);
        count(source, CounterEnumType.OMEN, 1);
        upkeep(game, game.getPlayers().get(0));
        assertDraw(game);
    }

    @Test public void celestialNoCountersAtEntryStillChecksUpkeep() {
        Game game = pod();
        enchantment(game, "Solemnity", 3);
        Card source = enchantment(game, "Celestial Convergence", 4);
        assertEquals(source.getCounters(CounterEnumType.OMEN), 0);
        upkeep(game, game.getPlayers().get(0));
        assertDraw(game);
    }

    @Test public void celestialExternalRemovalWaitsForUpkeep() {
        Game game = pod();
        Card source = enchantment(game, "Celestial Convergence", 4);
        cast(game, game.getPlayers().get(0), "Aether Snap", 5, null);
        settle(game);
        assertEquals(source.getCounters(CounterEnumType.OMEN), 0);
        assertFalse(game.isGameOver());
        upkeep(game, game.getPlayers().get(0));
        assertDraw(game);
    }

    @Test public void divineEnemyRemovalDoesNotBecomeDrawAtNextUpkeep() {
        Game game = pod();
        enchantment(game, "Divine Intervention", 8);
        cast(game, game.getPlayers().get(1), "Aether Snap", 5, null);
        settle(game);
        upkeep(game, game.getPlayers().get(0));
        settle(game);
        assertFalse(game.isGameOver());
    }

    @Test public void divineOtherCounterTypeDoesNotTriggerDraw() {
        Game game = pod();
        Card source = enchantment(game, "Divine Intervention", 8);
        count(source, CounterEnumType.INTERVENTION, 0);
        count(source, CounterEnumType.P1P1, 1);
        cast(game, game.getPlayers().get(0), "Aether Snap", 5, null);
        settle(game);
        assertFalse(game.isGameOver());
    }

    @Test public void divineRemovingZeroCountersDoesNotTriggerDraw() {
        Game game = pod();
        enchantment(game, "Divine Intervention", 8);
        cast(game, game.getPlayers().get(1), "Aether Snap", 5, null);
        settle(game);
        cast(game, game.getPlayers().get(0), "Aether Snap", 5, null);
        settle(game);
        assertFalse(game.isGameOver());
    }

    private Card transferred(Game game) {
        Card source = enchantment(game, "Divine Intervention", 8);
        // Fixed control layer; not a claimed paid control-acquisition witness.
        source.addTempController(game.getPlayers().get(1), game.getNextTimestamp());
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        assertSame(source.getOwner(), game.getPlayers().get(0));
        assertSame(source.getController(), game.getPlayers().get(1));
        return source;
    }

    @Test public void divineCurrentControllerCanCauseDrawAfterControlChanges() {
        Game game = pod();
        transferred(game);
        cast(game, game.getPlayers().get(1), "Aether Snap", 5, null);
        game.getStack().resolveStack();
        drawPending(game);
        assertDraw(game);
    }

    @Test public void divineOriginalOwnerCannotCauseDrawAfterControlChanges() {
        Game game = pod();
        transferred(game);
        cast(game, game.getPlayers().get(0), "Aether Snap", 5, null);
        settle(game);
        assertFalse(game.isGameOver());
    }

    @Test public void divineCopiedPendingTriggerEndsOnlyCopiedGame() {
        Game game = pod();
        enchantment(game, "Divine Intervention", 8);
        cast(game, game.getPlayers().get(0), "Aether Snap", 5, null);
        game.getStack().resolveStack();
        drawPending(game);
        Game copy = new GameCopier(game).withStack(true).makeCopy();
        drawPending(copy);
        assertDraw(copy);
        assertFalse(game.isGameOver());
        drawPending(game);
        assertDraw(game);
    }

    @Test public void divineCopiedCounterRemovalDoesNotQueueOriginalTrigger() {
        Game game = pod();
        Card original = enchantment(game, "Divine Intervention", 8);
        Game copy = new GameCopier(game).withStack(true).makeCopy();
        Card copied = current(copy, original);
        assertNotSame(copied, original);
        cast(copy, copy.getPlayers().get(0), "Aether Snap", 5, null);
        copy.getStack().resolveStack();
        drawPending(copy);
        assertDraw(copy);
        assertEquals(original.getCounters(CounterEnumType.INTERVENTION), 2);
        assertTrue(game.getStack().isEmpty());
        assertFalse(game.isGameOver());
    }
}
