package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
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

/** Paid selection spells and a real graveyard activation test supplied-board scry history. */
public class CommanderScryHistoryTest extends AITest {
    private Player actor(Game game) { return game.getPlayers().getFirst(); }

    private Game board(String spell) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("scry-history-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Scry history"));
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
        addCardToZone("Proctor of Potential", actor(game), ZoneType.Graveyard);
        if (spell != null) addCardToZone(spell, actor(game), ZoneType.Hand);
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
        Card onStack = game.getAction().moveToStack(card, ability);
        TriggerWaiting placement = game.getTriggerHandler().holdCastPlacement(onStack);
        ability.setHostCard(onStack);
        assertTrue(new CostPayment(ability.getPayCosts(), ability).payComputerCosts(
                new AiCostDecision(payer, ability, false, true)));
        game.getTriggerHandler().commitCastPlacement(placement);
        game.getStack().add(ability);
        GameSimulator.resolveStack(game, payer);
        assertTrue(game.getStack().isEmpty());
        assertTrue(game.getCardState(onStack).isInZone(ZoneType.Graveyard));
    }

    private SpellAbility returnAbility(Game game) {
        Card card = named(actor(game), ZoneType.Graveyard, "Proctor of Potential");
        for (SpellAbility ability : card.getSpellAbilities()) {
            if (ability.getApi() == ApiType.ChangeZone && ability.hasParam("CheckSVar")) {
                ability.setActivatingPlayer(actor(game));
                return ability;
            }
        }
        throw new AssertionError("Missing native conditional return ability");
    }

    private void check(int depth, String spell) {
        Game original = board(spell);
        if (spell != null) cast(original, spell);
        boolean scry = "Opt".equals(spell);
        boolean surveil = "Consider".equals(spell);
        assertEquals(actor(original).getScryThisTurn(), scry ? 1 : 0);
        assertEquals(actor(original).getSurveilThisTurn(), surveil ? 1 : 0);
        Game game = copy(original, depth);
        SpellAbility ability = returnAbility(game);
        boolean allowed = scry || surveil;
        assertEquals(ability.getRestrictions().canPlay(ability.getHostCard(), ability), allowed,
                "The real condition follows this player's scry or surveil history");
        if (!allowed) return;
        assertTrue(ComputerUtilCost.canPayCost(ability, actor(game), false));
        assertEquals(actor(game).getScryThisTurn(), scry ? 1 : 0,
                "Cost probing does not alter the live history");
        assertTrue(new CostPayment(ability.getPayCosts(), ability).payComputerCosts(
                new AiCostDecision(actor(game), ability, false, true)));
        game.getStack().add(ability);
        GameSimulator.resolveStack(game, actor(game));
        Card returned = named(actor(game), ZoneType.Battlefield, "Proctor of Potential");
        assertEquals(returned.getCounters(CounterEnumType.FINALITY), 1);
        assertEquals(actor(game).getSurveilThisTurn(), surveil ? 2 : 1,
                "The returned creature's real enters trigger resolves");
        assertTrue(game.getStack().isEmpty());
        if (depth > 0) {
            assertTrue(named(actor(original), ZoneType.Graveyard, "Proctor of Potential").isInZone(ZoneType.Graveyard));
            assertEquals(actor(original).getSurveilThisTurn(), surveil ? 1 : 0);
        }
    }

    @Test public void paidScryInOriginal() { check(0, "Opt"); }
    @Test public void paidScryAfterOneCopy() { check(1, "Opt"); }
    @Test public void paidScryAfterTwoCopies() { check(2, "Opt"); }
    @Test public void noHistoryDoesNotInventPermission() { check(2, null); }
    @Test public void surveilStillSatisfiesTheAlternativeCondition() { check(2, "Consider"); }
    @Test public void anotherPlayersHistoryStaysSeparate() {
        Game original = board(null);
        original.getPlayers().get(1).incScryThisTurn();
        Game game = copy(original, 2);
        assertEquals(actor(game).getScryThisTurn(), 0);
        assertEquals(game.getPlayers().get(1).getScryThisTurn(), 1);
        SpellAbility ability = returnAbility(game);
        assertFalse(ability.getRestrictions().canPlay(ability.getHostCard(), ability));
        actor(game).incScryThisTurn();
        assertEquals(actor(original).getScryThisTurn(), 0);
        assertEquals(original.getPlayers().get(1).getScryThisTurn(), 1);
    }
}
