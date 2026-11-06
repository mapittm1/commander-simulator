package forge.ai;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import forge.ai.simulation.MultiTargetSelector;
import forge.ai.simulation.PaidOutcomePlanner;
import forge.deck.Deck;
import forge.game.*;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.card.CounterType;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Shared paid forecasts must distinguish no targets, no legal targets and unpaid effects. */
public class PaidOutcomePlannerTest extends AITest {
    private Game pod(boolean lands) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("paidOutcome" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Paid outcome contracts"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) {
            for (int count = 0; count < 8; count++) {
                if (lands) addCardToZone("Swamp", player, ZoneType.Battlefield);
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        return game;
    }
    private Card walker(Player owner) {
        Card card = addCardToZone("Jace Beleren", owner, ZoneType.Battlefield);
        Multiset<CounterType> values = HashMultiset.create();
        values.add(CounterEnumType.LOYALTY, 8);
        card.setCounters(values); // Fixed prior loyalty; no claimed counter-placement history.
        return card;
    }
    private SpellAbility spell(Player actor, String name) {
        SpellAbility action = addCardToZone(name, actor, ZoneType.Hand).getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        return action;
    }
    private void untouched(Game game, SpellAbility action, Card walker) {
        assertEquals(action.getHostCard().getZone().getZoneType(), ZoneType.Hand);
        assertEquals(walker.getCounters(CounterEnumType.LOYALTY), 8);
        assertTrue(game.getStack().isEmpty());
        for (Player player : game.getPlayers()) {
            assertEquals(player.getSpellsCastThisTurn(), 0);
            for (Card card : player.getCardsIn(ZoneType.Battlefield)) {
                if (card.isLand()) assertFalse(card.isTapped());
            }
        }
    }
    @Test public void untargetedActionHasOneCandidate() {
        Game game = pod(true);
        Player actor = game.getPlayers().get(0);
        Card enemyWalker = walker(game.getPlayers().get(1));
        SpellAbility action = spell(actor, "Aether Snap");
        MultiTargetSelector selector = new MultiTargetSelector(action, null);
        assertFalse(selector.hasTargeting());
        assertFalse(selector.hasPossibleTargets());
        var provider = MyRandom.getRandom();
        assertTrue(PaidOutcomePlanner.choose(actor, action).willingToPlay());
        assertSame(MyRandom.getRandom(), provider);
        untouched(game, action, enemyWalker);
    }
    @Test public void requiredTargetsCannotBecomeAnUntargetedAction() {
        Game game = pod(true);
        Player actor = game.getPlayers().get(0);
        Card enemyWalker = walker(game.getPlayers().get(1));
        SpellAbility action = spell(actor, "Rancor");
        addCardToZone("Forest", actor, ZoneType.Battlefield);
        assertTrue(ComputerUtilCost.canPayCost(action, actor, false));
        MultiTargetSelector selector = new MultiTargetSelector(action, null);
        assertTrue(selector.hasTargeting());
        assertFalse(selector.hasPossibleTargets());
        assertFalse(PaidOutcomePlanner.choose(actor, action).willingToPlay());
        assertTrue(action.getTargets().isEmpty());
        untouched(game, action, enemyWalker);
    }
    @Test public void unaffordableOutcomeIsNotAFreeSpell() {
        Game game = pod(false);
        Player actor = game.getPlayers().get(0);
        Card enemyWalker = walker(game.getPlayers().get(1));
        SpellAbility action = spell(actor, "Aether Snap");
        assertFalse(PaidOutcomePlanner.choose(actor, action).willingToPlay());
        untouched(game, action, enemyWalker);
    }
    @Test public void controlledDecisionUsesCapturedBeneficiary() {
        Game game = pod(true);
        Player actor = game.getPlayers().get(0);
        Card actorWalker = walker(actor);
        SpellAbility action = spell(actor, "Aether Snap");
        assertFalse(PaidOutcomePlanner.choose(actor, action).willingToPlay());
        // Explicit controller context, not a claimed paid Mindslaver activation.
        actor.addController(game.getNextTimestamp(), game.getPlayers().get(2));
        assertTrue(PaidOutcomePlanner.choose(actor, action).willingToPlay());
        assertSame(action.getActivatingPlayer(), actor);
        untouched(game, action, actorWalker);
    }
    @Test public void arbitraryResolvingParentsAreNotReplayed() {
        Game game = pod(true);
        Player actor = game.getPlayers().get(0);
        Card enemyWalker = walker(game.getPlayers().get(1));
        SpellAbility action = spell(actor, "Aether Snap");
        game.getStack().setResolving(true);
        try {
            assertFalse(PaidOutcomePlanner.choose(actor, action).willingToPlay());
            assertTrue(game.getStack().isResolving());
            untouched(game, action, enemyWalker);
        } finally {
            game.getStack().setResolving(false);
        }
    }
}
