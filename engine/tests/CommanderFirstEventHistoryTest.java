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
import forge.game.card.CounterEnumType;
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

/** Native prior events and paid follow-ups from supplied boards test first-event history. */
public class CommanderFirstEventHistoryTest extends AITest {
    private Player actor(Game game) { return game.getPlayers().getFirst(); }

    private Game board(String family) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("first-event-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "First-event history"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setStartingLife(40); // Setup does not record a life-gain event.
            for (int index = 0; index < 20; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        Player payer = actor(game);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Plains", payer, ZoneType.Battlefield);
            addCardToZone("Island", payer, ZoneType.Battlefield);
            addCardToZone("Swamp", payer, ZoneType.Battlefield);
        }
        if (family.equals("life")) {
            addCardToZone("Cleric of Life's Bond", payer, ZoneType.Battlefield);
            addCardToZone("Revitalize", payer, ZoneType.Hand);
        } else if (family.equals("surveil")) {
            addCardToZone("Whispering Snitch", payer, ZoneType.Battlefield);
            addCardToZone("Consider", payer, ZoneType.Hand);
            addCardToZone("Notion Rain", payer, ZoneType.Hand);
        } else {
            assertEquals(family, "investigate");
            addCardToZone("Erdwal Illuminator", payer, ZoneType.Battlefield);
            addCardToZone("Thraben Inspector", payer, ZoneType.Hand);
            addCardToZone("Novice Inspector", payer, ZoneType.Hand);
        }
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

    private void cast(Game game, String name) {
        Player payer = actor(game);
        Card card = named(payer, ZoneType.Hand, name);
        SpellAbility ability = card.getFirstSpellAbility();
        ability.setActivatingPlayer(payer);
        assertTrue(ability.getRestrictions().canPlay(card, ability));
        int life = payer.getLife();
        int gains = payer.getLifeGainedTimesThisTurn();
        int surveils = payer.getSurveilThisTurn();
        int investigations = payer.getInvestigateNumThisTurn();
        assertTrue(ComputerUtilCost.canPayCost(ability, payer, false));
        assertEquals(payer.getLife(), life);
        assertEquals(payer.getLifeGainedTimesThisTurn(), gains);
        assertEquals(payer.getSurveilThisTurn(), surveils);
        assertEquals(payer.getInvestigateNumThisTurn(), investigations);
        Card onStack = game.getAction().moveToStack(card, ability);
        TriggerWaiting placement = game.getTriggerHandler().holdCastPlacement(onStack);
        ability.setHostCard(onStack);
        assertTrue(new CostPayment(ability.getPayCosts(), ability).payComputerCosts(
                new AiCostDecision(payer, ability, false, true)));
        game.getTriggerHandler().commitCastPlacement(placement);
        game.getStack().add(ability);
        GameSimulator.resolveStack(game, payer);
        assertTrue(game.getStack().isEmpty());
        Card resolved = game.getCardState(onStack);
        assertNotNull(resolved);
        assertTrue(resolved.isInZone(onStack.getType().isCreature()
                ? ZoneType.Battlefield : ZoneType.Graveyard), "The paid spell resolves to its normal destination");
    }

    private int clues(Player player) {
        int result = 0;
        for (Card card : player.getCardsIn(ZoneType.Battlefield)) {
            if (card.getType().hasSubtype("Clue")) result++;
        }
        return result;
    }

    private void life(int depth, boolean prior) {
        Game original = board("life");
        Card cleric = named(actor(original), ZoneType.Battlefield, "Cleric of Life's Bond");
        if (prior) {
            assertTrue(actor(original).gainLife(1, cleric, null));
            GameSimulator.resolveStack(original, actor(original));
            assertEquals(cleric.getCounters(CounterEnumType.P1P1), 1);
            assertEquals(actor(original).loseLife(1, false, false, null), 1);
            assertEquals(actor(original).getLife(), 40, "Net life is unchanged after a real earlier gain/loss");
            assertEquals(actor(original).getLifeGainedTimesThisTurn(), 1);
        }
        Game game = copy(original, depth);
        cast(game, "Revitalize");
        assertEquals(actor(game).getLife(), 43);
        assertEquals(named(actor(game), ZoneType.Battlefield, "Cleric of Life's Bond")
                .getCounters(CounterEnumType.P1P1), 1, "First-life-gain counter occurs once, even after net-zero prior events");
        assertEquals(actor(game).getLifeGainedTimesThisTurn(), prior ? 2 : 1);
        if (depth > 0) {
            assertEquals(actor(original).getLife(), 40);
            assertEquals(cleric.getCounters(CounterEnumType.P1P1), prior ? 1 : 0);
        }
    }

    private void surveil(int depth, boolean prior) {
        Game original = board("surveil");
        if (prior) {
            cast(original, "Consider");
            assertEquals(actor(original).getSurveilThisTurn(), 1);
            for (Player opponent : actor(original).getOpponents()) assertEquals(opponent.getLife(), 39);
        }
        Game game = copy(original, depth);
        cast(game, prior ? "Notion Rain" : "Consider");
        for (Player opponent : actor(game).getOpponents()) {
            assertEquals(opponent.getLife(), 39, "Whispering Snitch drains only on the first surveil");
        }
        assertEquals(actor(game).getLife(), prior ? 39 : 41);
        assertEquals(actor(game).getSurveilThisTurn(), prior ? 2 : 1);
        if (depth > 0) assertEquals(actor(original).getSurveilThisTurn(), prior ? 1 : 0);
    }

    private void investigate(int depth, boolean prior) {
        Game original = board("investigate");
        if (prior) {
            cast(original, "Thraben Inspector");
            assertEquals(clues(actor(original)), 2);
            assertEquals(actor(original).getInvestigateNumThisTurn(), 2);
        }
        Game game = copy(original, depth);
        cast(game, prior ? "Novice Inspector" : "Thraben Inspector");
        assertEquals(clues(actor(game)), prior ? 3 : 2,
                "Erdwal Illuminator adds an investigation only for the first event");
        assertEquals(actor(game).getInvestigateNumThisTurn(), prior ? 3 : 2);
        if (depth > 0) assertEquals(clues(actor(original)), prior ? 2 : 0);
    }

    @Test public void lifeInOriginal() { life(0, true); }
    @Test public void lifeAfterOneCopy() { life(1, true); }
    @Test public void lifeAfterTwoCopies() { life(2, true); }
    @Test public void firstLifeGainStillTriggers() { life(2, false); }
    @Test public void surveilInOriginal() { surveil(0, true); }
    @Test public void surveilAfterOneCopy() { surveil(1, true); }
    @Test public void surveilAfterTwoCopies() { surveil(2, true); }
    @Test public void firstSurveilStillTriggers() { surveil(2, false); }
    @Test public void investigateInOriginal() { investigate(0, true); }
    @Test public void investigateAfterOneCopy() { investigate(1, true); }
    @Test public void investigateAfterTwoCopies() { investigate(2, true); }
    @Test public void firstInvestigationStillTriggers() { investigate(2, false); }
}
