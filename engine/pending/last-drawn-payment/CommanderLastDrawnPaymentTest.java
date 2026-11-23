package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Jandor's Ring must pay with this turn's latest drawn card in the current world. */
public class CommanderLastDrawnPaymentTest extends AITest {
    @Test public void originalFreshDrawPays() { exercise("fresh", false); }
    @Test public void copiedFreshDrawPays() { exercise("fresh", true); }
    @Test public void originalLatestDrawPays() { exercise("latest", false); }
    @Test public void copiedLatestDrawPays() { exercise("latest", true); }
    @Test public void originalDepartedDrawCannotPay() { exercise("departed", false); }
    @Test public void copiedDepartedDrawCannotPay() { exercise("departed", true); }
    @Test public void originalPreviousTurnDrawCannotPay() { exercise("expired", false); }
    @Test public void copiedPreviousTurnDrawCannotPay() { exercise("expired", true); }
    @Test public void originalNoDrawCannotPay() { exercise("none", false); }
    @Test public void copiedNoDrawCannotPay() { exercise("none", true); }
    @Test public void originalFreshDrawAfterCleanupPays() { exercise("renewed", false); }
    @Test public void copiedFreshDrawAfterCleanupPays() { exercise("renewed", true); }

    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("last-drawn-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Last drawn payment"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            for (int index = 0; index < 8; index++) addCardToZone("Shock", player, ZoneType.Library);
        }
        return game;
    }

    private void exercise(String state, boolean copied) {
        Game game = pod();
        Player actor = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, actor, 2);
        Card ring = addCardToZone("Jandor's Ring", actor, ZoneType.Battlefield);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        addCardToZone("Sol Ring", actor, ZoneType.Hand);
        Card drawn = null;
        if (!state.equals("none")) {
            drawn = actor.drawCards(1).get(0);
            assertSame(actor.getLastDrawnCard(), drawn);
            assertTrue(drawn.getDrawnThisTurn());
        }
        if (state.equals("latest")) drawn = actor.drawCards(1).get(0);
        if (state.equals("departed")) game.getAction().moveTo(ZoneType.Exile, drawn, null, AbilityKey.newMap());
        if (state.equals("expired") || state.equals("renewed")) {
            actor.onCleanupPhase();
            assertFalse(drawn.getDrawnThisTurn());
            assertEquals(actor.getNumDrawnThisTurn(), 0);
            game.getPhaseHandler().devModeSet(PhaseType.MAIN2, actor, 3);
        }
        if (state.equals("renewed")) drawn = actor.drawCards(1).get(0);
        boolean eligible = state.equals("fresh") || state.equals("latest") || state.equals("renewed");
        int drawnId = drawn == null ? -1 : drawn.getId();
        game.copyLastState();
        Game original = game;
        Player originalActor = actor;
        int originalHand = actor.getCardsIn(ZoneType.Hand).size();
        int originalLibrary = actor.getCardsIn(ZoneType.Library).size();
        if (copied) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor);
            actor = copier.find(actor);
            ring = copier.find(ring);
        }
        SpellAbility ability = ring.getSpellAbilities().stream()
                .filter(candidate -> candidate.isActivatedAbility() && candidate.getApi() == ApiType.Draw)
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(actor);
        boolean payable = ComputerUtilCost.canPayCost(ability, actor, false);
        assertEquals(payable, eligible, "Printed last-drawn cost eligibility for " + state + "; copied=" + copied);
        assertFalse(ring.isTapped());
        assertTrue(actor.getLandsInPlay().stream().noneMatch(Card::isTapped));
        assertEquals(game.getStack().size(), 0);
        if (eligible) {
            Card currentDrawn = actor.getLastDrawnCard();
            assertNotNull(currentDrawn);
            assertEquals(currentDrawn.getId(), drawnId);
            assertSame(currentDrawn.getGame(), game);
            int hand = actor.getCardsIn(ZoneType.Hand).size();
            int library = actor.getCardsIn(ZoneType.Library).size();
            assertTrue(ComputerUtil.playStack(ability, actor, game), "Actual mana/tap/last-drawn payment");
            assertEquals(ability.getPayingMana().size(), 2);
            assertTrue(ring.isTapped());
            assertTrue(actor.getCardsIn(ZoneType.Graveyard).stream().anyMatch(card -> card.getId() == drawnId));
            game.getStack().resolveStack();
            game.getAction().checkStateEffects(true);
            assertEquals(actor.getCardsIn(ZoneType.Hand).size(), hand);
            assertEquals(actor.getCardsIn(ZoneType.Library).size(), library - 1);
            assertNotEquals(actor.getLastDrawnCard().getId(), drawnId);
        }
        if (copied) {
            assertNotSame(game, original);
            assertEquals(originalActor.getCardsIn(ZoneType.Hand).size(), originalHand);
            assertEquals(originalActor.getCardsIn(ZoneType.Library).size(), originalLibrary);
            assertTrue(originalActor.getLandsInPlay().stream().noneMatch(Card::isTapped));
            assertEquals(original.getStack().size(), 0);
        }
    }
}
