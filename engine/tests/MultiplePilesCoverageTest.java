package forge.ai;

import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.MyRandom;
import java.util.*;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Native three-pile outcomes and paid AI choices; fixed rolls are explicit branch controls. */
public class MultiplePilesCoverageTest extends AITest {
    private Game pod(int actorSeat, boolean allLands) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("piles" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Three piles"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(actorSeat), 1);
        for (int seat = 0; seat < 4; seat++) {
            Player player = game.getPlayers().get(seat);
            for (int count = 0; count < 8; count++) {
                if (allLands || seat == actorSeat) {
                    addCardToZone("Island", player, ZoneType.Battlefield);
                    addCardToZone("Mountain", player, ZoneType.Battlefield);
                }
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        return game;
    }

    private static final class FixedPileRandom extends Random {
        private static final long serialVersionUID = 1L;
        private final int pile;
        private int rolls;
        FixedPileRandom(int pile) { super(0L); this.pile = pile; }
        @Override public int nextInt(int bound) {
            if (bound == 3) { rolls++; return pile; }
            return super.nextInt(bound);
        }
    }

    private static final class ManualPiles extends PlayerControllerAi {
        private final int seat;
        private final boolean third;
        private final List<Integer> order;
        ManualPiles(Player player, int seat, boolean third, List<Integer> order) {
            super(player.getGame(), player, player.getLobbyPlayer());
            this.seat = seat;
            this.third = third;
            this.order = order;
        }
        @Override
        public CardCollectionView chooseCardsForEffect(CardCollectionView pool, SpellAbility action,
                String title, int min, int max, boolean optional, Map<String, Object> params) {
            if (action.getApi() != ApiType.MultiplePiles) {
                return super.chooseCardsForEffect(pool, action, title, min, max, optional, params);
            }
            order.add(seat);
            // Explicit legal pile partitions; not an optimal-partition or AI-admission claim.
            return third ? new CardCollection() : new CardCollection(pool);
        }
    }

    private void settle(Game game) {
        for (int guard = 0; guard < 40; guard++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.isGameOver() || game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Three-pile fixture did not settle");
    }

    private Card current(Game game, Card original) {
        return game.getCardsInGame().stream().filter(card -> card.getId() == original.getId()).findFirst().orElseThrow();
    }

    private SpellAbility paid(Game game, Player actor, String name, int mana) {
        SpellAbility action = addCardToZone(name, actor, ZoneType.Hand).getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.playStack(action, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        return game.getStack().peekAbility();
    }

    private void branch(int pile, boolean third, int actorSeat) {
        Game game = pod(actorSeat, true);
        List<Integer> order = new ArrayList<>();
        List<Card> before = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            Player player = game.getPlayers().get(seat);
            addCardToZone("Grizzly Bears", player, ZoneType.Battlefield);
            before.addAll(player.getCardsIn(ZoneType.Battlefield));
            player.dangerouslySetController(new ManualPiles(player, seat, third, order));
        }
        Player actor = game.getPlayers().get(actorSeat);
        SpellAbility action = paid(game, actor, "Whims of the Fates", 6);
        FixedPileRandom random = new FixedPileRandom(pile);
        MyRandom.withRandom(random, () -> { game.getStack().resolveStack(); return null; });
        settle(game);
        assertEquals(random.rolls, 4);
        List<Integer> expected = new ArrayList<>();
        for (int offset = 0; offset < 4; offset++) {
            expected.add((actorSeat + offset) % 4);
            expected.add((actorSeat + offset) % 4);
        }
        assertEquals(order, expected);
        boolean sacrificed = third ? pile == 2 : pile == 0;
        for (Card card : before) {
            Card after = current(game, card);
            assertEquals(after.getZone().getZoneType(), sacrificed ? ZoneType.Graveyard : ZoneType.Battlefield);
            assertSame(after.getOwner(), card.getOwner());
        }
        assertFalse(action.getHostCard().getRemembered().iterator().hasNext());
        assertEquals(actor.getSpellsCastThisTurn(), 1);
    }

    @Test public void firstPileSacrificesAllOwnersPermanents() { branch(0, false, 0); }
    @Test public void secondPileMayBeEmpty() { branch(1, false, 0); }
    @Test public void thirdPileMayBeEmpty() { branch(2, false, 0); }
    @Test public void allCardsMayBeInThirdPile() { branch(2, true, 0); }
    @Test public void separationStartsWithCaster() { branch(1, false, 2); }

    @Test public void sacrificeRestrictionAppliesBeforeSuitLeaves() {
        Game game = pod(0, true);
        Player actor = game.getPlayers().get(0);
        SpellAbility suitCast = paid(game, actor, "Assault Suit", 4);
        settle(game);
        Card suit = current(game, suitCast.getHostCard());
        Card creature = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        SpellAbility equip = suit.getSpellAbilities().stream().filter(action -> action.getApi() == ApiType.Attach).findFirst().orElseThrow();
        equip.setActivatingPlayer(actor);
        equip.getTargets().add(creature);
        assertTrue(ComputerUtil.playStack(equip, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        settle(game);
        List<Integer> order = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            Player player = game.getPlayers().get(seat);
            player.dangerouslySetController(new ManualPiles(player, seat, false, order));
        }
        paid(game, actor, "Whims of the Fates", 6);
        MyRandom.withRandom(new FixedPileRandom(0), () -> { game.getStack().resolveStack(); return null; });
        settle(game);
        assertEquals(current(game, creature).getZone().getZoneType(), ZoneType.Battlefield);
        assertEquals(current(game, suit).getZone().getZoneType(), ZoneType.Graveyard);
    }

    @Test public void seededRollsUseTheSamePlayerOrderAsSeparation() {
        // Deterministic replay contract; independent uniform outcomes are unchanged.
        for (int actorSeat = 0; actorSeat < 4; actorSeat++) {
            for (long seed : new long[] {0L, 4096L}) {
                Game game = pod(actorSeat, true);
                List<Integer> order = new ArrayList<>();
                int[] expected = new int[4];
                Random rolls = new Random(seed);
                for (int offset = 0; offset < 4; offset++) {
                    int seat = (actorSeat + offset) % 4;
                    Player player = game.getPlayers().get(seat);
                    addCardToZone("Grizzly Bears", player, ZoneType.Battlefield);
                    int count = player.getCardsIn(ZoneType.Battlefield).size();
                    expected[seat] = rolls.nextInt(3) == 0 ? 0 : count;
                    player.dangerouslySetController(new ManualPiles(player, seat, false, order));
                }
                paid(game, game.getPlayers().get(actorSeat), "Whims of the Fates", 6);
                MyRandom.withRandom(new Random(seed), () -> { game.getStack().resolveStack(); return null; });
                settle(game);
                for (int seat = 0; seat < 4; seat++) {
                    assertEquals(game.getPlayers().get(seat).getCardsIn(ZoneType.Battlefield).size(),
                            expected[seat], "Caster=" + actorSeat + " seed=" + seed + " seat=" + seat);
                }
            }
        }
    }

    private void choice(int mode, boolean beneficial) {
        Game game = pod(0, false);
        Player actor = game.getPlayers().get(0);
        if (beneficial) {
            for (int seat = 1; seat < 4; seat++) {
                Player enemy = game.getPlayers().get(seat);
                for (String name : List.of("Craw Wurm", "Colossal Dreadmaw", "Mammoth Spider", "Vastwood Gorger", "Gigantosaurus", "Ancient Brontodon", "Terra Stomper", "Impervious Greatwurm")) {
                    addCardToZone(name, enemy, ZoneType.Battlefield);
                }
            }
        }
        Card source = addCardToZone("Whims of the Fates", actor, ZoneType.Hand);
        ((PlayerControllerAi) actor.getController()).getAi().setUseSimulation(mode == 0 ? null : mode == 1 ? AIOption.USE_HYBRID_SIMULATION : AIOption.USE_FULL_SIMULATION);
        List<SpellAbility> choices = MyRandom.withRandom(new Random(0L), () -> actor.getController().chooseSpellAbilityToPlay());
        SpellAbility action = choices == null || choices.isEmpty() ? null : choices.get(0);
        if (!beneficial) { assertTrue(action == null || action.getHostCard() != source); return; }
        assertNotNull(action);
        assertSame(action.getHostCard(), source);
        assertEquals(actor.getCardsIn(ZoneType.Hand).size(), 1);
        assertTrue(actor.getController().playChosenSpellAbility(action));
        assertFalse(game.getStack().isEmpty());
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 6);
        MyRandom.withRandom(new FixedPileRandom(0), () -> { game.getStack().resolveStack(); return null; });
        settle(game);
        for (Player player : game.getPlayers()) assertTrue(player.getCardsIn(ZoneType.Battlefield).isEmpty());
    }

    @Test public void beneficialHeuristic() { choice(0, true); }
    @Test public void beneficialHybrid() { choice(1, true); }
    @Test public void beneficialFull() { choice(2, true); }
    @Test public void onlyOwnBoardAtRiskIsDeclined() { choice(0, false); }
}
