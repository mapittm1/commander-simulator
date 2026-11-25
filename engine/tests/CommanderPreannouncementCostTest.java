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

/** Supplied board states test pre-target feasibility and subsequent paid native casts. */
public class CommanderPreannouncementCostTest extends AITest {
    private Player actor(Game game) { return game.getPlayers().getFirst(); }
    private Map<Integer, String> state(Game game) {
        Map<Integer, String> result = new LinkedHashMap<>();
        for (Card card : game.getCardsInGame()) {
            result.put(card.getId(), card.getZone().getZoneType() + ":" + card.isTapped()
                    + ":" + card.getDamage() + ":" + card.getCounters().toString());
        }
        return result;
    }
    private Game board(String mode, int depth) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("announcement-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Target announcement boundary"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 12; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        Player payer = actor(game);
        Player opponent = game.getPlayers().get(1);
        int lands = mode.equals("nether") ? 4 : mode.equals("ward") ? 3 : 2;
        for (int index = 0; index < lands; index++) {
            addCardToZone(mode.equals("sacrifice") ? "Mountain" : "Plains", payer, ZoneType.Battlefield);
        }
        addCardToZone(mode.equals("sacrifice") ? "Fling" : "Pressure Point", payer, ZoneType.Hand);
        addCardToZone(mode.equals("nether") ? "Grizzly Bears" : "Tolarian Terror", opponent, ZoneType.Battlefield);
        if (mode.equals("nether")) addCardToZone("Nether Void", opponent, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, payer, 5);
        game.getAction().checkStaticAbilities(false);
        game.getTriggerHandler().resetActiveTriggers();
        game.copyLastState();
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(game).withStack(true);
            game = copier.makeCopy(null, actor(game));
            copier.release();
        }
        return game;
    }
    private void affordable(Game game, SpellAbility ability, boolean expected) {
        Map<Integer, String> before = state(game);
        int life = actor(game).getLife();
        int mana = actor(game).getManaPool().totalMana();
        int targets = ability.getTargets().size();
        assertEquals(ComputerUtilCost.canPayCost(ability, actor(game), false), expected);
        assertEquals(state(game), before, "Cost checks do not spend or move live cards");
        assertEquals(actor(game).getLife(), life);
        assertEquals(actor(game).getManaPool().totalMana(), mana);
        assertEquals(ability.getTargets().size(), targets);
    }
    private void check(String mode, int depth) {
        Game game = board(mode, depth);
        Player payer = actor(game);
        Player opponent = game.getPlayers().get(1);
        Card target = opponent.getCreaturesInPlay().getFirst();
        SpellAbility ability = payer.getCardsIn(ZoneType.Hand).getFirst().getFirstSpellAbility();
        ability.setActivatingPlayer(payer);
        assertFalse(game.getStack().hasLegalTargeting(ability));
        if (mode.equals("ward")) {
            affordable(game, ability, true);
            ability.getTargets().add(target);
            affordable(game, ability, false);
            addCardToZone("Plains", payer, ZoneType.Battlefield);
        } else if (mode.equals("nether")) {
            affordable(game, ability, false); // Base two mana plus the known three-mana cast tax.
            addCardToZone("Plains", payer, ZoneType.Battlefield);
            affordable(game, ability, true);
            ability.getTargets().add(target);
        } else {
            affordable(game, ability, false); // Missing sacrifice is still unaffordable before targeting.
            addCardToZone("Memnite", payer, ZoneType.Battlefield);
            affordable(game, ability, true);
            ability.getTargets().add(opponent);
        }
        assertTrue(game.getStack().hasLegalTargeting(ability));
        affordable(game, ability, true);
        Card onStack = game.getAction().moveToStack(ability.getHostCard(), ability);
        TriggerWaiting placement = game.getTriggerHandler().holdCastPlacement(onStack);
        ability.setHostCard(onStack);
        assertTrue(new CostPayment(ability.getPayCosts(), ability).payComputerCosts(
                new AiCostDecision(payer, ability, false, true)));
        game.getTriggerHandler().commitCastPlacement(placement);
        game.getStack().add(ability);
        GameSimulator.resolveStack(game, payer);
        if (mode.equals("sacrifice")) {
            assertEquals(opponent.getLife(), 99);
            assertTrue(payer.getCreaturesInPlay().isEmpty());
        } else {
            assertTrue(target.isTapped());
            assertEquals(payer.getCardsIn(ZoneType.Hand).size(), 1, "The paid spell actually resolves and draws");
        }
        assertEquals(payer.getLife(), 100);
        assertTrue(game.getStack().isEmpty());
    }

    @Test
    public void wardDepth0() {
        check("ward", 0);
    }

    @Test
    public void wardDepth1() {
        check("ward", 1);
    }

    @Test
    public void wardDepth2() {
        check("ward", 2);
    }

    @Test
    public void netherDepth0() {
        check("nether", 0);
    }

    @Test
    public void netherDepth1() {
        check("nether", 1);
    }

    @Test
    public void netherDepth2() {
        check("nether", 2);
    }

    @Test
    public void sacrificeDepth0() {
        check("sacrifice", 0);
    }

    @Test
    public void sacrificeDepth1() {
        check("sacrifice", 1);
    }

    @Test
    public void sacrificeDepth2() {
        check("sacrifice", 2);
    }

}
