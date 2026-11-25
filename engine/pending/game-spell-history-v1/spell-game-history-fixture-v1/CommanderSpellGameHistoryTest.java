package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameActionUtil;
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

/** Paid spells followed by a native stack turn boundary test game-long cast history. */
public class CommanderSpellGameHistoryTest extends AITest {
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
        addCardToZone("Once Upon a Time", actor(game), ZoneType.Hand);
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

    private void assertFreeChoice(Game game, boolean expected) {
        Player player = actor(game);
        Card card = named(player, ZoneType.Hand, "Once Upon a Time");
        SpellAbility spell = card.getFirstSpellAbility();
        spell.setActivatingPlayer(player);
        boolean found = GameActionUtil.getAlternativeCosts(spell, player, true).stream()
                .anyMatch(option -> option.getPayCosts().getTotalMana().isZero()
                        && option.getRestrictions().canPlay(card, option));
        assertEquals(found, expected, "The native first-spell alternative uses this player's game history");
    }

    private void check(int depth, boolean priorSpell, boolean nextTurn) {
        Game original = board(priorSpell ? "Opt" : null);
        if (priorSpell) cast(original, "Opt");
        assertEquals(actor(original).getSpellsCastThisGame(), priorSpell ? 1 : 0);
        if (nextTurn) {
            original.getStack().onNextTurn();
            original.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(original), 6);
            assertEquals(actor(original).getSpellsCastThisTurn(), 0);
            assertEquals(actor(original).getSpellsCastThisGame(), priorSpell ? 1 : 0);
        }
        assertFreeChoice(original, !priorSpell);
        Game game = copy(original, depth);
        assertFreeChoice(game, !priorSpell);
        assertEquals(actor(game).getSpellsCastThisGame(), priorSpell ? 1 : 0);
        assertEquals(actor(original).getSpellsCastThisGame(), priorSpell ? 1 : 0);
    }

    @Test public void firstSpellInOriginalIsFree() { check(0, false, false); }
    @Test public void firstSpellAfterTwoCopiesIsFree() { check(2, false, false); }
    @Test public void earlierSpellThisTurnInOriginalBlocksFreeChoice() { check(0, true, false); }
    @Test public void earlierSpellThisTurnAfterTwoCopiesBlocksFreeChoice() { check(2, true, false); }
    @Test public void earlierTurnInOriginalBlocksFreeChoice() { check(0, true, true); }
    @Test public void earlierTurnAfterOneCopyBlocksFreeChoice() { check(1, true, true); }
    @Test public void earlierTurnAfterTwoCopiesBlocksFreeChoice() { check(2, true, true); }
    @Test public void firstSpellAfterTurnBoundaryAndCopiesIsFree() { check(2, false, true); }
}
