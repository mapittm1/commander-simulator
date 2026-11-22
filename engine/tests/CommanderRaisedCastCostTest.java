package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityFactory;
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

/** Native Ogre Battlecaster branch with a supplied legal target; does not test attack selection. */
public class CommanderRaisedCastCostTest extends AITest {
    @Test public void theRaisedColoredCostIsPaidAndTheCastSpellIsExiled() { resolve(true, false); }
    @Test public void genericManaCannotPayTheRaisedRedRequirement() { resolve(false, false); }
    @Test public void aStaticTaxIsAdditionalToTheGrantedIncrease() { resolve(true, true); }
    private void resolve(boolean red, boolean tax) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("raised-grant-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Raised grant native branch"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        ((PlayerControllerAi) actor.getController()).getAi().setUseSimulation(AIOption.USE_FULL_SIMULATION);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        for (int i = 0; i < 3; i++) addCardToZone("Island", actor, ZoneType.Battlefield);
        for (int i = 0; i < 2; i++) addCardToZone(red ? "Mountain" : "Wastes", actor, ZoneType.Battlefield);
        Card caster = addCardToZone("Ogre Battlecaster", actor, ZoneType.Battlefield);
        Card study = addCardToZone("Quick Study", actor, ZoneType.Graveyard);
        if (tax) addCardToZone("Thalia, Guardian of Thraben", enemy, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        SpellAbility branch = AbilityFactory.getAbility(caster.getSVar("TrigPlay"), caster);
        branch.setActivatingPlayer(actor);
        assertTrue(branch.canTarget(study));
        branch.getTargets().add(study);
        AbilityUtils.resolve(branch);
        settle(game);
        boolean cast = red && !tax;
        assertEquals(actor.getSpellsCastThisTurn(), cast ? 1 : 0);
        assertEquals(actor.getCardsIn(ZoneType.Library).size(), cast ? 6 : 8);
        assertEquals(actor.getLandsInPlay().stream().filter(Card::isTapped).count(), cast ? 5L : 0L);
        assertTrue(game.getCardState(study).isInZone(cast ? ZoneType.Exile : ZoneType.Graveyard));
        assertEquals(caster.getNetPower(), cast ? 6 : 3);
    }
    private void settle(Game game) {
        for (int i = 0; i < 40; i++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        throw new AssertionError("Native raised-cost branch did not settle");
    }
}
