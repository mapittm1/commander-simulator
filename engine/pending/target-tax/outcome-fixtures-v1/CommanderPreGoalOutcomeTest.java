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
import forge.game.cost.CostPayment;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerWaiting;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Independent pre-goal win, loss and surviving cast-trigger witnesses. */
public class CommanderPreGoalOutcomeTest extends AITest {
    private Player actor(Game game) {
        return game.getRegisteredPlayers().stream().filter(p -> p.getName().equals("outcome-0")).findFirst().orElseThrow();
    }

    private Map<Integer, String> state(Game game) {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (Card card : game.getCardsInGame()) result.put(card.getId(), card.getZone().getZoneType()+":"+card.isTapped());
        for (Player player : game.getRegisteredPlayers()) result.put(-player.getId(), "life="+player.getLife()+";mana="+player.getManaPool().totalMana()+";outcome="+player.hasWon()+":"+player.hasLost());
        result.put(Integer.MIN_VALUE, "over="+game.isGameOver()+";casts="+game.getStack().getSpellsCastThisTurn().size());
        return result;
    }

    private Game setup(String mode) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i=0; i<(mode.equals("multiplayerLoss") ? 4 : 2); i++) seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("outcome-"+i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Pre-goal native outcomes"));
        game.setNoGUIUser(); game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int i=0; i<24; i++) addCardToZone("Forest", player, ZoneType.Library);
        }
        Player payer = actor(game), opponent = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, payer, 5);
        boolean monument = mode.equals("win") || mode.equals("gain");
        if (monument) {
            opponent.setLife(mode.equals("win") ? 1 : 2, null);
            addCardToZone("Bontu's Monument", payer, ZoneType.Battlefield);
            addCardToZone("Grizzly Bears", payer, ZoneType.Hand);
            addCardToZone("Forest", payer, ZoneType.Battlefield);
            addCardToZone("Forest", payer, ZoneType.Battlefield);
        } else {
            payer.setLife(mode.equals("survive") ? 3 : 1, null);
            addCardToZone("Kambal, Consul of Allocation", opponent, ZoneType.Battlefield);
            addCardToZone("Opt", payer, ZoneType.Hand);
            addCardToZone("Island", payer, ZoneType.Battlefield);
        }
        game.getAction().checkStaticAbilities(false);
        game.getTriggerHandler().resetActiveTriggers();
        return game;
    }

    private void check(String mode, int depth, boolean nativeCast) {
        Game original = setup(mode);
        Map<Integer, String> originalBefore = state(original);
        original.copyLastState();
        Game game = original;
        for (int i=0; i<depth; i++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game)); copier.release();
        }
        Player payer = actor(game);
        SpellAbility spell = payer.getCardsIn(ZoneType.Hand).getFirst().getFirstSpellAbility();
        spell.setActivatingPlayer(payer);
        Map<Integer, String> before = state(game);
        boolean loses = mode.equals("loss") || mode.equals("multiplayerLoss");
        if (!nativeCast) {
            assertEquals(ComputerUtilCost.canPayCost(spell, payer, false), !loses, "A winning cast trigger is a successful forecast; losing the payer is not");
            assertEquals(state(game), before, "Copied game outcomes do not leak into the source");
        } else {
            Card onStack = game.getAction().moveToStack(spell.getHostCard(), spell);
            TriggerWaiting placement = game.getTriggerHandler().holdCastPlacement(onStack);
            spell.setHostCard(onStack);
            assertTrue(new CostPayment(spell.getPayCosts(), spell).payComputerCosts(new AiCostDecision(payer, spell, false, true)));
            game.getTriggerHandler().commitCastPlacement(placement);
            game.getStack().add(spell);
            GameSimulator.resolveStack(game, payer);
            assertEquals(payer.hasWon(), mode.equals("win"));
            assertEquals(payer.hasLost(), loses);
            assertEquals(game.isGameOver(), mode.equals("win") || mode.equals("loss"));
            if (mode.equals("win")) assertTrue(spell.getHostCard().isInZone(ZoneType.Stack), "The cast trigger wins before the creature spell resolves");
            if (mode.equals("gain")) {
                assertTrue(spell.getHostCard().isInZone(ZoneType.Battlefield));
                assertEquals(payer.getLife(), 101);
                assertEquals(game.getPlayers().get(1).getLife(), 1);
            }
            if (mode.equals("survive")) {
                assertEquals(payer.getLife(), 1);
                assertEquals(payer.getCardsIn(ZoneType.Library).size(), 23);
            }
        }
        if (depth > 0) assertEquals(state(original), originalBefore);
    }

    @Test
    public void winPreviewDepth0() {
        check("win", 0, false);
    }

    @Test
    public void winNativeDepth0() {
        check("win", 0, true);
    }

    @Test
    public void winPreviewDepth1() {
        check("win", 1, false);
    }

    @Test
    public void winNativeDepth1() {
        check("win", 1, true);
    }

    @Test
    public void winPreviewDepth2() {
        check("win", 2, false);
    }

    @Test
    public void winNativeDepth2() {
        check("win", 2, true);
    }

    @Test
    public void gainPreviewDepth0() {
        check("gain", 0, false);
    }

    @Test
    public void gainNativeDepth0() {
        check("gain", 0, true);
    }

    @Test
    public void gainPreviewDepth1() {
        check("gain", 1, false);
    }

    @Test
    public void gainNativeDepth1() {
        check("gain", 1, true);
    }

    @Test
    public void gainPreviewDepth2() {
        check("gain", 2, false);
    }

    @Test
    public void gainNativeDepth2() {
        check("gain", 2, true);
    }

    @Test
    public void lossPreviewDepth0() {
        check("loss", 0, false);
    }

    @Test
    public void lossNativeDepth0() {
        check("loss", 0, true);
    }

    @Test
    public void lossPreviewDepth1() {
        check("loss", 1, false);
    }

    @Test
    public void lossNativeDepth1() {
        check("loss", 1, true);
    }

    @Test
    public void lossPreviewDepth2() {
        check("loss", 2, false);
    }

    @Test
    public void lossNativeDepth2() {
        check("loss", 2, true);
    }

    @Test
    public void survivePreviewDepth0() {
        check("survive", 0, false);
    }

    @Test
    public void surviveNativeDepth0() {
        check("survive", 0, true);
    }

    @Test
    public void survivePreviewDepth1() {
        check("survive", 1, false);
    }

    @Test
    public void surviveNativeDepth1() {
        check("survive", 1, true);
    }

    @Test
    public void survivePreviewDepth2() {
        check("survive", 2, false);
    }

    @Test
    public void surviveNativeDepth2() {
        check("survive", 2, true);
    }

    @Test
    public void multiplayerLossPreviewDepth0() {
        check("multiplayerLoss", 0, false);
    }

    @Test
    public void multiplayerLossNativeDepth0() {
        check("multiplayerLoss", 0, true);
    }

    @Test
    public void multiplayerLossPreviewDepth1() {
        check("multiplayerLoss", 1, false);
    }

    @Test
    public void multiplayerLossNativeDepth1() {
        check("multiplayerLoss", 1, true);
    }

    @Test
    public void multiplayerLossPreviewDepth2() {
        check("multiplayerLoss", 2, false);
    }

    @Test
    public void multiplayerLossNativeDepth2() {
        check("multiplayerLoss", 2, true);
    }
}
