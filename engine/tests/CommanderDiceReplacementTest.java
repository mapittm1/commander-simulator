package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.GameRules;
import forge.game.GameSnapshot;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.effects.RollDiceEffect;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.cost.Cost;
import forge.game.cost.CostPayment;
import forge.game.keyword.Keyword;
import forge.game.phase.PhaseType;
import forge.game.player.DelayedReveal;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import forge.util.collect.FCollectionView;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class CommanderDiceReplacementTest extends AITest {
    private void collectTriggers(Game game) {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private long treasures(Player player, boolean tapped) {
        return player.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isToken() && card.getType().hasSubtype("Treasure") && card.isTapped() == tapped)
                .count();
    }

    @Test
    public void ignoredD20DoesNotChangeTreasureCountOrTriggerChiraTwice() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        assertTrue(RollDiceEffect.rollDiceForPlayer(null, actor, 1, 20) > 0);
        assertEquals(actor.getNumRollsThisTurn(), 1);
        Card goblinson = addCardToZone("The Space Family Goblinson", actor, ZoneType.Battlefield);
        Card dragon = addCardToZone("Ancient Copper Dragon", actor, ZoneType.Battlefield);
        Card chira = addCardToZone("Chira, All In", actor, ZoneType.Battlefield);
        addCardToZone("Barbarian Class", actor, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DAMAGE, actor, 1);
        Combat combat = new Combat(actor);
        combat.addAttacker(dragon, opponent);
        combat.getBandOfAttacker(dragon).setBlocked(false);
        game.getPhaseHandler().setCombat(combat);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        int life = opponent.getLife();
        assertTrue(combat.assignCombatDamage(false));
        combat.dealAssignedDamage();
        assertEquals(opponent.getLife(), life - 6);
        collectTriggers(game);
        assertEquals(game.getStack().size(), 1);
        assertSame(game.getStack().peekAbility().getHostCard(), dragon);

        resolveWithRolls(game, 20, List.of(3, 17));
        assertEquals(actor.getDiceRollsThisTurn().size(), 2);
        assertEquals(actor.getDiceRollsThisTurn().get(1).intValue(), 17);
        assertEquals(treasures(actor, false), 17L);
        collectTriggers(game);
        assertEquals(game.getStack().size(), 2);
        game.getStack().resolveStack();
        game.getStack().resolveStack();
        assertEquals(chira.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(goblinson.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(treasures(actor, true), 1L);
        game.getAction().checkStateEffects(true);
        assertFalse(goblinson.hasKeyword(Keyword.TRAMPLE));
        assertEquals(actor.getNumRollsThisTurn(), 2);
    }

    @Test
    public void paidRerollReplacesBothSelectedDiceAndCountsOnlyKeptResults() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        for (int index = 0; index < 4; index++) {
            addCardToZone("Mountain", actor, ZoneType.Battlefield);
        }
        Card monitor = addCardToZone("Monitor Monitor", actor, ZoneType.Battlefield);
        Card veggiesaur = addCardToZone("Resolute Veggiesaur", actor, ZoneType.Battlefield);
        addCardToZone("Barbarian Class", actor, ZoneType.Battlefield);
        actor.dangerouslySetController(new PlayerControllerAi(game, actor, actor.getLobbyPlayer()) {
            @Override
            public List<Integer> chooseDiceToReroll(List<Integer> rolls) {
                assertEquals(rolls, List.of(2, 2, 5));
                return List.of(2, 2);
            }

            @Override
            public <T extends GameEntity> T chooseSingleEntityForEffect(FCollectionView<T> options,
                    DelayedReveal delayed, SpellAbility ability, String title, boolean optional,
                    Player targetedPlayer, Map<String, Object> params) {
                return options.stream().filter(monitor::equals).findFirst().orElseGet(
                        () -> super.chooseSingleEntityForEffect(
                                options, delayed, ability, title, optional, targetedPlayer, params));
            }

            @Override
            public boolean payCostDuringRoll(Cost cost, SpellAbility ability) {
                ability.setActivatingPlayer(actor);
                return new CostPayment(cost, ability).payComputerCosts(new AiCostDecision(actor, ability, true));
            }
        });
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();

        Card circuits = addCardToZone("Circuits Act", actor, ZoneType.Hand);
        SpellAbility spell = circuits.getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null));
        assertEquals(untappedMountains(actor), 1L);
        resolveWithRolls(game, 6, List.of(1, 2, 2, 5, 1, 4, 4));

        assertEquals(untappedMountains(actor), 0L);
        assertEquals(monitor.getSVar("ModsThisTurn"), "Number$1");
        assertEquals(actor.getNumRollsThisTurn(), 3);
        assertEquals(actor.getDiceRollsThisTurn().stream().sorted().toList(), List.of(4, 4, 5));
        assertEquals(actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isToken() && card.getType().hasSubtype("Clown")).count(), 2L);
        collectTriggers(game);
        assertEquals(game.getStack().size(), 1);
        assertSame(game.getStack().peekAbility().getHostCard(), veggiesaur);
        game.getStack().resolveStack();
        assertEquals(veggiesaur.getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void mastiffKeepsOnlyTheHighestRollWhenAttackingMultipleOpponents() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Player defendingPlayer = game.getPlayers().get(1);
        Player otherAttackedPlayer = game.getPlayers().get(2);
        Player thirdOpponent = game.getPlayers().get(3);
        Card mastiff = addCardToZone("Iron Mastiff", actor, ZoneType.Battlefield);
        Card bear = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, actor, 1);
        Combat combat = new Combat(actor);
        combat.addAttacker(mastiff, defendingPlayer);
        combat.addAttacker(bear, otherAttackedPlayer);
        game.getPhaseHandler().setCombat(combat);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().resetActiveTriggers();
        CombatUtil.checkDeclaredAttacker(game, mastiff, combat, true);
        CombatUtil.checkDeclaredAttacker(game, bear, combat, true);
        collectTriggers(game);
        assertEquals(game.getStack().size(), 1);
        assertSame(game.getStack().peekAbility().getHostCard(), mastiff);

        resolveWithRolls(game, 20, List.of(8, 14));

        assertEquals(actor.getDiceRollsThisTurn(), List.of(14));
        assertEquals(actor.getNumRollsThisTurn(), 1);
        assertEquals(actor.getLife(), 40);
        assertEquals(defendingPlayer.getLife(), 36);
        assertEquals(otherAttackedPlayer.getLife(), 40);
        assertEquals(thirdOpponent.getLife(), 40);
    }

    private long untappedMountains(Player player) {
        return player.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Mountain") && !card.isTapped()).count();
    }

    private void resolveWithRolls(Game game, int sides, List<Integer> faces) {
        Iterator<Integer> results = faces.iterator();
        MyRandom.withRandom(new Random(42) {
            @Override
            public int nextInt(int bound) {
                return bound == sides ? results.next() - 1 : super.nextInt(bound);
            }
        }, () -> {
            game.getStack().resolveStack();
            return null;
        });
        assertFalse(results.hasNext());
    }

    @Test
    public void modifiedDieHistorySurvivesIndependentAiCopy() {
        verifyCopy(true);
    }

    @Test
    public void modifiedDieHistorySurvivesIndependentNativeSnapshot() {
        verifyCopy(false);
    }

    private void verifyCopy(boolean aiCopy) {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor, 1);
        for (int index = 0; index < 3; index++) {
            addCardToZone("Island", actor, ZoneType.Battlefield);
            addCardToZone("Plains", actor, ZoneType.Hand);
        }
        Card card = addCardToZone("Diviner's Portent", actor, ZoneType.Hand);
        SpellAbility spell = card.getFirstSpellAbility();
        spell.setActivatingPlayer(actor);
        spell.setXManaCostPaid(0);
        assertTrue(ComputerUtil.handlePlayingSpellAbility(actor, spell, null));
        resolveWithRolls(game, 20, List.of(3));
        assertEquals(actor.getDiceRollsThisTurn(), List.of(6));
        assertEquals(actor.getNumRollsThisTurn(), 1);
        Game copy = aiCopy ? new GameCopier(game).withStack(true).makeCopy() : new GameSnapshot(game).makeCopy();
        Player copied = copy.getPlayer(actor.getId());
        assertEquals(copied.getDiceRollsThisTurn(), List.of(6));
        assertEquals(copied.getNumRollsThisTurn(), 1);
        assertTrue(RollDiceEffect.rollDiceForPlayer(null, copied, 1, 20) > 0);
        assertEquals(copied.getNumRollsThisTurn(), 2);
        assertEquals(copied.getDiceRollsThisTurn().size(), 2);
        assertEquals(actor.getNumRollsThisTurn(), 1);
        assertEquals(actor.getDiceRollsThisTurn(), List.of(6));
    }

    private Game newGame() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Dice replacement"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        return game;
    }
}
