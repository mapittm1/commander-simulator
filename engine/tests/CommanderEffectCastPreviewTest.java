package forge.ai;

import forge.ai.simulation.GameSimulator;
import forge.ai.simulation.GameStateEvaluator;
import forge.ai.simulation.SimulationController;
import forge.deck.Deck;
import forge.card.CardStateName;
import forge.card.GamePieceType;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityUtils;
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

/** Explicit native granted-card and granted-copy outcomes; live inputs must remain unchanged. */
public class CommanderEffectCastPreviewTest extends AITest {
    @Test public void copiedCardPreviewPreservesTheExiledOriginal() { verify(true, false); }
    @Test public void copiedCardChoiceRetainsItsPreviewIdentity() { verify(true, true); }
    @Test public void grantedOriginalCastActuallyConsumesTheOriginal() { verify(false, false); }
    @Test public void anUnregisteredGeneratedCardCopyCanBeProjected() { verify(true, false, true); }
    private void verify(boolean copy, boolean choiceCopy) { verify(copy, choiceCopy, false); }
    private void verify(boolean copy, boolean choiceCopy, boolean unregistered) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("effect-preview-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Granted copy/original preview"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        Card original = addCardToZone("Last Thoughts", actor, ZoneType.Exile);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.copyLastState();
        Card offeredCard = original;
        if (unregistered) {
            offeredCard = Card.fromPaperCard(original.getPaperCard(), actor);
            offeredCard.setGamePieceType(GamePieceType.TOKEN);
            offeredCard.setCopiedPermanent(original);
            offeredCard.setZone(actor.getZone(ZoneType.None));
            assertFalse(game.getCardsInGame().contains(offeredCard));
        }
        List<SpellAbility> offered = AbilityUtils.getSpellsFromPlayEffect(offeredCard, actor, CardStateName.Original, false, null);
        assertEquals(offered.size(), 1);
        SpellAbility action = offered.get(0).copyWithNoManaCost();
        if (copy && !unregistered) GameSimulator.markCopiedCastFromEffect(action);
        if (choiceCopy) action = action.copy();
        GameStateEvaluator.Score baseline = GameSimulator.scoreForDecision(game, actor);
        GameSimulator simulation = new GameSimulator(new SimulationController(baseline, 0), game, actor, null);
        GameStateEvaluator.Score result = simulation.simulateSpellAbility(action);
        assertNotEquals(result.value, Integer.MIN_VALUE, "The granted cast must actually be paid and resolved");
        Game projected = simulation.getSimulatedGameState();
        Card projectedOriginal = projected.getCardState(simulation.getGameCopier().find(original));
        assertTrue(projectedOriginal.isInZone(copy ? ZoneType.Exile : ZoneType.Graveyard));
        assertEquals(projected.getPlayer(actor.getId()).getCardsIn(ZoneType.Library).size(), 7);
        assertEquals(projected.getPlayer(actor.getId()).getSpellsCastThisTurn(), 1);
        assertTrue(original.isInZone(ZoneType.Exile));
        assertEquals(actor.getCardsIn(ZoneType.Library).size(), 8);
        assertEquals(actor.getSpellsCastThisTurn(), 0);
        assertFalse(original.getFirstSpellAbility().hasParam("WithoutManaCost"));
        assertEquals(original.getFirstSpellAbility().getPayCosts().getTotalMana().getCMC(), 4);
        assertTrue(game.getStack().isEmpty());
    }
}
