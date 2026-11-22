package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Actual first-draw/reveal/copy chain with native optional decisions and payment. */
public class CommanderKefnetCostTest extends AITest {
    @Test public void anAffordablePrintedSpellStillPaysTheGrantedReduction() { draw(3, false, true); }
    @Test public void aCopyCanUseTheReductionWhenThePrintedCostIsUnaffordable() { draw(1, false, true); }
    @Test public void aGenericReductionCannotRemoveTheBlueManaRequirement() { draw(1, true, false); }
    @Test public void anotherDrawInTheSameTurnCannotGrantAnotherCopy() {
        Pod pod = draw(3, false, true);
        for (Card land : pod.actor.getLandsInPlay()) land.setTapped(false);
        Card next = addCardToZone("Opt", pod.actor, ZoneType.Hand);
        next = pod.game.getAction().moveToLibrary(next, 0, null);
        pod.actor.drawCards(1);
        settle(pod.game);
        assertEquals(pod.actor.getSpellsCastThisTurn(), 1);
        assertTrue(pod.game.getCardState(next).isInZone(ZoneType.Hand));
        assertEquals(pod.actor.getCardsIn(ZoneType.Library).size(), 6);
        assertEquals(pod.actor.getLandsInPlay().stream().filter(Card::isTapped).count(), 0L);
    }
    @Test public void heuristicAdmissionUsesTheGrantedDiscount() { draw(1, false, true, null); }
    @Test public void hybridAdmissionUsesTheGrantedDiscount() { draw(1, false, true, AIOption.USE_HYBRID_SIMULATION); }
    private Pod draw(int mana, boolean colorless, boolean shouldCast) {
        return draw(mana, colorless, shouldCast, AIOption.USE_FULL_SIMULATION);
    }
    private Pod draw(int mana, boolean colorless, boolean shouldCast, AIOption mode) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("kefnet-" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Native Kefnet grant research"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        ((PlayerControllerAi) actor.getController()).getAi().setUseSimulation(mode);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) addCardToZone("Forest", player, ZoneType.Library);
        for (int i = 0; i < mana; i++) addCardToZone(colorless ? "Wastes" : "Island", actor, ZoneType.Battlefield);
        addCardToZone("God-Eternal Kefnet", actor, ZoneType.Battlefield);
        Card study = addCardToZone("Quick Study", actor, ZoneType.Hand);
        study = game.getAction().moveToLibrary(study, 0, null);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 2);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        assertEquals(actor.getCardsIn(ZoneType.Library).size(), 9);
        actor.drawCards(1);
        settle(game);
        assertEquals(actor.getSpellsCastThisTurn(), shouldCast ? 1 : 0,
                "Native optional selection must consider the effect-granted reduction");
        assertEquals(actor.getCardsIn(ZoneType.Library).size(), shouldCast ? 6 : 8);
        assertTrue(game.getCardState(study).isInZone(ZoneType.Hand));
        assertEquals(actor.getLandsInPlay().stream().filter(Card::isTapped).count(), shouldCast ? 1L : 0L);
        return new Pod(game, actor);
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
        throw new AssertionError("Native draw/copy chain did not settle");
    }
    private record Pod(Game game, Player actor) { }
}
