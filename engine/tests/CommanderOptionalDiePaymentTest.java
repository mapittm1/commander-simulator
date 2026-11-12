package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.cost.CostPayment;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class CommanderOptionalDiePaymentTest extends AITest {
    @Test
    public void decliningNightShiftPaymentLeavesRollAndTriggersIntact() {
        verifyDeclinedPayment(false);
    }

    @Test
    public void decliningMonitorPaymentKeepsOriginalDiceWithoutSpendingTheAbility() {
        verifyDeclinedPayment(true);
    }

    @Test
    public void paidNightShiftLifeCostTurnsFiveIntoSixAndCreatesOneZombie() {
        Game game = newGameWithMountains();
        Player actor = game.getPlayers().get(0);
        Card nightShift = addCardToZone("Night Shift of the Living Dead", actor, ZoneType.Battlefield);
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public Integer chooseRollToModify(List<Integer> rolls) {
                assertEquals(rolls, List.of(2, 4, 5));
                return 5;
            }

            @Override
            public boolean payCostDuringRoll(Cost cost, SpellAbility ability) {
                ability.setActivatingPlayer(actor);
                return new CostPayment(cost, ability).payComputerCosts(new AiCostDecision(actor, ability, true));
            }

            @Override
            public boolean chooseBinary(SpellAbility ability, String question, BinaryChoiceType kindOfChoice,
                    Boolean defaultChoice) {
                assertEquals(kindOfChoice, BinaryChoiceType.IncreaseOrDecrease);
                return true;
            }
        });
        castCircuitsAct(game, actor, List.of(2, 4, 5));
        assertEquals(actor.getLife(), 39);
        assertEquals(nightShift.getSVar("ModsThisTurn"), "Number$1");
        assertEquals(actor.getDiceRollsThisTurn().stream().sorted().toList(), List.of(2, 4, 6));
        assertEquals(actor.getNumRollsThisTurn(), 3);
        assertEquals(tokens(actor, "Clown"), 3L);
        collectTriggers(game);
        assertEquals(game.getStack().size(), 1);
        game.getStack().resolveStack();
        assertEquals(tokens(actor, "Zombie"), 1L);
    }

    private void verifyDeclinedPayment(boolean reroll) {
        Game game = newGameWithMountains();
        Player actor = game.getPlayers().get(0);
        String cardName = reroll ? "Monitor Monitor" : "Night Shift of the Living Dead";
        Card modifier = addCardToZone(cardName, actor, ZoneType.Battlefield);
        int expectedAttempts = reroll ? 1 : 3;
        int[] paymentAttempts = {0};
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public List<Integer> chooseDiceToReroll(List<Integer> rolls) {
                return reroll ? List.of(rolls.get(0)) : super.chooseDiceToReroll(rolls);
            }

            @Override
            public boolean payCostDuringRoll(Cost cost, SpellAbility ability) {
                assertTrue(++paymentAttempts[0] <= expectedAttempts, "Same declined payment repeated within one roll");
                return super.payCostDuringRoll(cost, ability);
            }
        });
        castCircuitsAct(game, actor, List.of(2, 4, 6));
        assertEquals(actor.getLife(), 40);
        assertEquals(paymentAttempts[0], expectedAttempts);
        assertEquals(modifier.getSVar("ModsThisTurn"), "Number$0");
        assertEquals(actor.getDiceRollsThisTurn().stream().sorted().toList(), List.of(2, 4, 6));
        assertEquals(actor.getNumRollsThisTurn(), 3);
        assertEquals(tokens(actor, "Clown"), 3L);
        collectTriggers(game);
        assertEquals(game.getStack().size(), reroll ? 0 : 1);
        if (!reroll) {
            game.getStack().resolveStack();
            assertEquals(tokens(actor, "Zombie"), 1L);
        }
    }

    private void castCircuitsAct(Game game, Player actor, List<Integer> rolls) {
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        Card circuits = addCardToZone("Circuits Act", actor, ZoneType.Hand);
        SpellAbility spell = circuits.getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null));

        Iterator<Integer> faces = rolls.iterator();
        MyRandom.withRandom(new Random(42) {
            @Override
            public int nextInt(int bound) {
                return bound == 6 ? faces.next() - 1 : super.nextInt(bound);
            }
        }, () -> {
            game.getStack().resolveStack();
            return null;
        });

        assertFalse(faces.hasNext());
    }

    private void collectTriggers(Game game) {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private long tokens(Player player, String subtype) {
        return player.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isToken() && card.getType().hasSubtype(subtype)).count();
    }

    private Game newGameWithMountains() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Optional die payments"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Mountain", actor, ZoneType.Battlefield);
        }
        return game;
    }
}
