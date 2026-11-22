package forge.ai;

import forge.ai.simulation.GameSimulator;
import forge.ai.simulation.OnePlaySafetyChecker;
import forge.card.GamePieceType;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** A generated hand-zone cast object does not consume its original or earn loss compensation. */
public class CommanderTransientCopySafetyTest extends AITest {
    @Test public void acceptsAnUnregisteredHandCopyWithoutConsumingTheOriginal() { verify(false, true); }
    @Test public void rejectsLifePaymentForAProhibitedDrawCopy() { verify(true, false); }
    private void verify(boolean prohibited, boolean acceptable) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("copy-safety-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Transient hand-copy safety"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        for (Player player : game.getPlayers()) {
            player.setLife(40, null);
            for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        }
        Card original = addCardToZone("Last Thoughts", actor, ZoneType.Hand);
        if (prohibited) {
            addCardToZone("Spirit of the Labyrinth", game.getPlayers().get(1), ZoneType.Battlefield);
            actor.drawCards(1);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        Card copy = Card.fromPaperCard(original.getPaperCard(), actor);
        copy.setGamePieceType(GamePieceType.TOKEN);
        copy.setCopiedPermanent(original);
        copy.setZone(actor.getZone(ZoneType.Hand));
        assertFalse(actor.getCardsIn(ZoneType.Hand).contains(copy));
        SpellAbility action = copy.getFirstSpellAbility().copyWithNoManaCost(actor);
        action.setCastFromPlayEffect(true);
        GameSimulator.markCopiedCastFromEffect(action);
        if (prohibited) action = action.copyWithManaCostReplaced(actor, new Cost("PayLife<10>", false));
        assertEquals(OnePlaySafetyChecker.isAcceptable(actor, action), acceptable);
        assertTrue(original.isInZone(ZoneType.Hand));
        assertFalse(actor.getCardsIn(ZoneType.Hand).contains(copy));
        assertEquals(actor.getCardsIn(ZoneType.Library).size(), prohibited ? 7 : 8);
        assertEquals(actor.getSpellsCastThisTurn(), 0);
        assertEquals(actor.getLife(), 40);
        assertFalse(original.getFirstSpellAbility().hasParam("WithoutManaCost"));
    }
}
