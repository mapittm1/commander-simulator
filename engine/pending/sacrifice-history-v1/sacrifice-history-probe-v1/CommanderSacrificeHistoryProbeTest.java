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

/** Probe native sacrifice actions and Dargo payments through supplied copied boards. */
public class CommanderSacrificeHistoryProbeTest extends AITest {
    private Player actor(Game game) { return game.getPlayers().getFirst(); }

    private Game board() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("sacrifice-history-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Sacrifice history"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setStartingLife(40);
            for (int index = 0; index < 20; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        for (int index = 0; index < 5; index++) {
            addCardToZone("Island", actor(game), ZoneType.Battlefield);
            addCardToZone("Mountain", actor(game), ZoneType.Battlefield);
        }
        addCardToZone("Dargo, the Shipwrecker", actor(game), ZoneType.Hand);
        addCardToZone("Dargo, the Shipwrecker", actor(game), ZoneType.Hand);
        addCardToZone("Dargo, the Shipwrecker", actor(game), ZoneType.Hand);
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

    private void sacrifice(Game game, String name) {
        Player player = actor(game);
        Card card = addCardToZone(name, player, ZoneType.Battlefield);
        game.getAction().checkStaticAbilities(false);
        SpellAbility action = card.getSpellAbilities().stream()
                .filter(ability -> !ability.isSpell()
                        && ability.getApi() == forge.game.ability.ApiType.ChangeZone)
                .findFirst().orElseThrow();
        action.setActivatingPlayer(player);
        assertTrue(action.canPlay());
        int before = player.getSacrificedThisTurn().size();
        assertTrue(new CostPayment(action.getPayCosts(), action).payComputerCosts(
                new AiCostDecision(player, action, false, true)));
        game.getStack().add(action);
        GameSimulator.resolveStack(game, player);
        assertTrue(game.getStack().isEmpty());
        assertTrue(game.getCardState(card).isInZone(ZoneType.Graveyard));
        assertEquals(player.getSacrificedThisTurn().size(), before + 1);
    }

    private void castDargo(Game game, int expectedMana) {
        Player player = actor(game);
        Card card = named(player, ZoneType.Hand, "Dargo, the Shipwrecker");
        SpellAbility spell = card.getFirstSpellAbility();
        spell.setActivatingPlayer(player);
        spell.setXManaCostPaid(0); // Explicit choice: no additional sacrifices during this cast.
        assertTrue(spell.getRestrictions().canPlay(card, spell));
        Card onStack = game.getAction().moveToStack(card, spell);
        TriggerWaiting placement = game.getTriggerHandler().holdCastPlacement(onStack);
        spell.setHostCard(onStack);
        long before = tappedLands(player);
        assertTrue(new CostPayment(spell.getPayCosts(), spell).payComputerCosts(
                new AiCostDecision(player, spell, false, true)));
        assertEquals(tappedLands(player) - before, (long) expectedMana,
                "Native Dargo payment includes prior artifact/creature sacrifices only");
        game.getTriggerHandler().commitCastPlacement(placement);
        game.getStack().add(spell);
        GameSimulator.resolveStack(game, player);
        assertTrue(game.getStack().isEmpty());
        assertTrue(game.getCardState(onStack).isInZone(ZoneType.Battlefield));
    }

    private void check(int depth, int sacrifices, boolean land) {
        Game original = board();
        for (int index = 0; index < sacrifices; index++) {
            sacrifice(original, land ? "Evolving Wilds" : "Sakura-Tribe Elder");
        }
        assertEquals(actor(original).getSacrificedThisTurn().size(), sacrifices);
        Game branch = copy(original, depth);
        castDargo(branch, 7 - (land ? 0 : 2 * sacrifices));
        assertEquals(actor(original).getSacrificedThisTurn().size(), sacrifices);
    }

    @Test public void noHistoryOriginalPaysSeven() { check(0, 0, false); }
    @Test public void noHistoryAfterTwoCopiesPaysSeven() { check(2, 0, false); }
    @Test public void oneCreatureOriginalPaysFive() { check(0, 1, false); }
    @Test public void oneCreatureAfterOneCopyPaysFive() { check(1, 1, false); }
    @Test public void oneCreatureAfterTwoCopiesPaysFive() { check(2, 1, false); }
    @Test public void twoCreaturesOriginalPayThree() { check(0, 2, false); }
    @Test public void twoCreaturesAfterOneCopyPayThree() { check(1, 2, false); }
    @Test public void twoCreaturesAfterTwoCopiesPayThree() { check(2, 2, false); }
    @Test public void landSacrificeOriginalDoesNotDiscountDargo() { check(0, 1, true); }
    @Test public void landSacrificeAfterTwoCopiesDoesNotDiscountDargo() { check(2, 1, true); }
}
