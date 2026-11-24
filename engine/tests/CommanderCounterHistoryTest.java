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
import com.google.common.collect.Table;
import com.google.common.collect.Multimap;
import forge.game.GameEntity;
import forge.game.card.CounterType;
import org.apache.commons.lang3.tuple.Pair;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Paid counter placements distinguish turn history from current counter totals. */
public class CommanderCounterHistoryTest extends AITest {
    private Player actor(Game game) {
        return game.getPlayers().stream().filter(player -> player.getName().equals("counter-0"))
                .findFirst().orElseThrow();
    }

    private Card named(Game game, String name, ZoneType zone) {
        return actor(game).getCardsIn(zone, false).stream()
                .filter(card -> card.getName().equals(name)).findFirst().orElseThrow();
    }

    private void absent(Game game, String name, ZoneType zone) {
        assertTrue(actor(game).getCardsIn(zone, false).stream().noneMatch(card -> card.getName().equals(name)),
                name + " must not be in " + zone);
    }

    private void beginCast(Game game, String name, Card target, int mana) {
        SpellAbility ability = addCardToZone(name, actor(game), ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(actor(game));
        if (target != null) ability.getTargets().add(target);
        assertTrue(ability.canPlay() && ability.checkRestrictions(ability.getHostCard(), actor(game)));
        assertTrue(ComputerUtil.playStack(ability, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
    }

    private void cast(Game game, String name, Card target, int mana) {
        beginCast(game, name, target, mana);
        GameSimulator.resolveStack(game, actor(game));
    }

    private Game setup() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("counter-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Turn counter history"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        for (int index = 0; index < 8; index++) {
            addCardToZone("Plains", actor(game), ZoneType.Battlefield);
            addCardToZone("Forest", actor(game), ZoneType.Battlefield);
            addCardToZone("Island", actor(game), ZoneType.Battlefield);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        return game;
    }


    private Game snapshot(Game game, int depth) {
        game.copyLastState();
        for (int index = 0; index < depth; index++) game = new GameCopier(game).withStack(true).makeCopy(null, actor(game));
        return game;
    }

    private void history(int depth) {
        Game original = setup();
        cast(original, "Grizzly Bears", null, 2);
        Card bear = named(original, "Grizzly Bears", ZoneType.Battlefield);
        cast(original, "Battlegrowth", bear, 1);
        assertEquals(bear.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(original.getCounterAddedThisTurn(CounterEnumType.P1P1, bear), 1);
        Game game = snapshot(original, depth);
        Card copied = named(game, "Grizzly Bears", ZoneType.Battlefield);
        assertEquals(copied.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(game.getCounterAddedThisTurn(CounterEnumType.P1P1, copied), 1,
                "A simulation snapshot preserves earlier counter-placement history");
        assertEquals(original.getCounterAddedThisTurn(CounterEnumType.P1P1, bear), 1);
    }

    private void insects(int depth) {
        Game original = setup();
        // Keep enough green available after the native payer uses Forests for generic costs.
        for (int index = 0; index < 4; index++) addCardToZone("Forest", actor(original), ZoneType.Battlefield);
        cast(original, "Iridescent Hornbeetle", null, 5);
        cast(original, "Grizzly Bears", null, 2);
        Card bear = named(original, "Grizzly Bears", ZoneType.Battlefield);
        cast(original, "Battlegrowth", bear, 1);
        cast(original, "Battlegrowth", bear, 1);
        assertEquals(bear.getCounters(CounterEnumType.P1P1), 2);
        assertEquals(original.getCounterAddedThisTurn(CounterEnumType.P1P1, bear), 2);
        Game game = snapshot(original, depth);
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.END_OF_TURN,
                () -> GameSimulator.resolveStack(game, actor(game))));
        GameSimulator.resolveStack(game, actor(game));
        List<Card> tokens = actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).toList();
        assertEquals(tokens.size(), 2, "Native Hornbeetle trigger counts both paid placements earlier this turn");
        for (Card token : tokens) {
            assertEquals(token.getNetPower(), 1);
            assertEquals(token.getNetToughness(), 1);
            assertTrue(token.getType().hasCreatureType("Insect"));
        }
        if (depth > 0) assertTrue(actor(original).getCardsIn(ZoneType.Battlefield).stream().noneMatch(Card::isToken));
    }

    private void firstTime(int depth) {
        Game original = setup();
        cast(original, "Danny Pink", null, 4);
        cast(original, "Grizzly Bears", null, 2);
        Card bear = named(original, "Grizzly Bears", ZoneType.Battlefield);
        int library = actor(original).getCardsIn(ZoneType.Library).size();
        cast(original, "Battlegrowth", bear, 1);
        assertEquals(actor(original).getCardsIn(ZoneType.Library).size(), library - 1,
                "The first actual counter placement draws once");
        Game game = snapshot(original, depth);
        Card copied = named(game, "Grizzly Bears", ZoneType.Battlefield);
        cast(game, "Battlegrowth", copied, 1);
        assertEquals(copied.getCounters(CounterEnumType.P1P1), 2);
        assertEquals(actor(game).getCardsIn(ZoneType.Library).size(), library - 1,
                "A second placement on the same creature this turn does not trigger another Danny Pink draw");
        if (depth > 0) {
            assertEquals(bear.getCounters(CounterEnumType.P1P1), 1);
            assertEquals(actor(original).getCardsIn(ZoneType.Library).size(), library - 1);
        }
    }
    @Test public void historyDepth0() { history(0); }
    @Test public void historyDepth1() { history(1); }
    @Test public void historyDepth2() { history(2); }
    @Test public void insectsDepth0() { insects(0); }
    @Test public void insectsDepth1() { insects(1); }
    @Test public void insectsDepth2() { insects(2); }
    @Test public void firstTimeDepth0() { firstTime(0); }
    @Test public void firstTimeDepth1() { firstTime(1); }
    @Test public void firstTimeDepth2() { firstTime(2); }

    private Game counterGame(boolean beetle) {
        Game game = setup();
        for (int index = 0; index < 8; index++) addCardToZone("Forest", actor(game), ZoneType.Battlefield);
        cast(game, "Grizzly Bears", null, 2);
        cast(game, "Battlegrowth", named(game, "Grizzly Bears", ZoneType.Battlefield), 1);
        if (beetle) cast(game, "Iridescent Hornbeetle", null, 5);
        return game;
    }

    @SuppressWarnings("unchecked")
    private Table<CounterType, Player, List<Pair<Card, Integer>>> receipts(Game game) {
        try {
            java.lang.reflect.Field field = Game.class.getDeclaredField("countersAddedThisTurn");
            field.setAccessible(true);
            return (Table<CounterType, Player, List<Pair<Card, Integer>>>) field.get(game);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot inspect fixed native counter-history field", exception);
        }
    }

    private void privateHistory(int depth) {
        Game original = counterGame(false);
        Game game = snapshot(original, depth);
        List<Pair<Card, Integer>> entries = receipts(game).get(CounterEnumType.P1P1, actor(game));
        assertNotNull(entries);
        assertEquals(entries.size(), 1);
        assertEquals(entries.get(0).getValue().intValue(), 1);
        Card historical = entries.get(0).getKey();
        Card live = named(game, "Grizzly Bears", ZoneType.Battlefield);
        assertNotSame(historical, live);
        assertSame(historical.getGame(), game);
        assertSame(historical.getOwner(), actor(game));
        assertSame(historical.getController(), actor(game));
        assertEquals(historical.getCounters(CounterEnumType.P1P1), 1);
        assertTrue(actor(game).getCardsIn(ZoneType.Battlefield).stream().noneMatch(card -> card == historical));
        if (depth > 0) {
            List<Pair<Card, Integer>> old = receipts(original).get(CounterEnumType.P1P1, actor(original));
            assertNotSame(entries, old);
            assertNotSame(historical, old.get(0).getKey());
            historical.setTapped(true);
            assertFalse(old.get(0).getKey().isTapped());
            assertFalse(named(original, "Grizzly Bears", ZoneType.Battlefield).isTapped());
        }
        assertFalse(live.isTapped());
    }

    private void expectInsect(Game game, int count) {
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.END_OF_TURN,
                () -> GameSimulator.resolveStack(game, actor(game))));
        GameSimulator.resolveStack(game, actor(game));
        List<Card> tokens = actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).toList();
        assertEquals(tokens.size(), count);
        for (Card token : tokens) {
            assertEquals(token.getNetPower(), 1);
            assertEquals(token.getNetToughness(), 1);
            assertTrue(token.getType().hasCreatureType("Insect"));
        }
    }

    private void departedCreature(int depth) {
        Game original = counterGame(true);
        cast(original, "Boomerang", named(original, "Grizzly Bears", ZoneType.Battlefield), 2);
        assertEquals(named(original, "Grizzly Bears", ZoneType.Hand).getCounters(CounterEnumType.P1P1), 0);
        Game game = snapshot(original, depth);
        expectInsect(game, 1);
        if (depth > 0) assertTrue(actor(original).getCardsIn(ZoneType.Battlefield).stream().noneMatch(Card::isToken));
    }

    private void changedController(int depth) {
        Game original = counterGame(true);
        Card bear = named(original, "Grizzly Bears", ZoneType.Battlefield);
        Player opponent = original.getPlayers().stream().filter(player -> player.getName().equals("counter-1"))
                .findFirst().orElseThrow();
        SpellAbility donate = addCardToZone("Donate", actor(original), ZoneType.Hand).getFirstSpellAbility();
        donate.setActivatingPlayer(actor(original));
        donate.getTargets().add(opponent);
        donate.getSubAbility().getTargets().add(bear);
        assertTrue(donate.canPlay() && donate.checkRestrictions(donate.getHostCard(), actor(original)));
        assertTrue(ComputerUtil.playStack(donate, actor(original), original));
        assertEquals(original.getStack().peekAbility().getPayingMana().size(), 3);
        GameSimulator.resolveStack(original, actor(original));
        assertSame(bear.getController(), opponent);
        Game game = snapshot(original, depth);
        expectInsect(game, 1);
        if (depth > 0) assertTrue(actor(original).getCardsIn(ZoneType.Battlefield).stream().noneMatch(Card::isToken));
    }

    private void changedType(int depth) {
        Game original = counterGame(true);
        cast(original, "Song of the Dryads", named(original, "Grizzly Bears", ZoneType.Battlefield), 3);
        assertTrue(named(original, "Grizzly Bears", ZoneType.Battlefield).isLand());
        assertFalse(named(original, "Grizzly Bears", ZoneType.Battlefield).isCreature());
        Game game = snapshot(original, depth);
        expectInsect(game, 1);
        if (depth > 0) assertTrue(actor(original).getCardsIn(ZoneType.Battlefield).stream().noneMatch(Card::isToken));
    }

    private void resetHistory(int depth) {
        Game original = counterGame(false);
        Game game = snapshot(original, depth);
        Card bear = named(game, "Grizzly Bears", ZoneType.Battlefield);
        assertEquals(game.getCounterAddedThisTurn(CounterEnumType.P1P1, bear), 1);
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1,
                () -> GameSimulator.resolveStack(game, actor(game))));
        GameSimulator.resolveStack(game, actor(game));
        assertEquals(game.getCounterAddedThisTurn(CounterEnumType.P1P1, bear), 0);
        assertEquals(bear.getCounters(CounterEnumType.P1P1), 1);
        if (depth > 0) assertEquals(original.getCounterAddedThisTurn(CounterEnumType.P1P1,
                named(original, "Grizzly Bears", ZoneType.Battlefield)), 1);
    }

    private void independentHistory(int depth) {
        Game original = counterGame(false);
        Game game = snapshot(original, depth);
        Card bear = named(game, "Grizzly Bears", ZoneType.Battlefield);
        cast(game, "Battlegrowth", bear, 1);
        assertEquals(bear.getCounters(CounterEnumType.P1P1), 2);
        assertEquals(game.getCounterAddedThisTurn(CounterEnumType.P1P1, bear), 2);
        if (depth > 0) assertEquals(original.getCounterAddedThisTurn(CounterEnumType.P1P1,
                named(original, "Grizzly Bears", ZoneType.Battlefield)), 1);
    }

    private void resolveSecondPlacement(Game game) {
        GameSimulator.resolveStack(game, actor(game));
        assertEquals(named(game, "Grizzly Bears", ZoneType.Battlefield).getCounters(CounterEnumType.P1P1), 2);
        assertEquals(actor(game).getCardsIn(ZoneType.Library).size(), 23);
    }

    private void pendingPlacement(int depth, boolean copyFirst) {
        Game original = setup();
        cast(original, "Danny Pink", null, 4);
        cast(original, "Grizzly Bears", null, 2);
        cast(original, "Battlegrowth", named(original, "Grizzly Bears", ZoneType.Battlefield), 1);
        beginCast(original, "Battlegrowth", named(original, "Grizzly Bears", ZoneType.Battlefield), 1);
        Game copy = snapshot(original, depth);
        Game first = copyFirst ? copy : original;
        Game other = copyFirst ? original : copy;
        resolveSecondPlacement(first);
        if (depth > 0) {
            assertEquals(named(other, "Grizzly Bears", ZoneType.Battlefield).getCounters(CounterEnumType.P1P1), 1);
            assertFalse(other.getStack().isEmpty());
            assertEquals(actor(other).getCardsIn(ZoneType.Library).size(), 23);
            resolveSecondPlacement(other);
        }
    }

    private Game spentEnergy() {
        Game game = setup();
        for (int index = 0; index < 8; index++) addCardToZone("Mountain", actor(game), ZoneType.Battlefield);
        cast(game, "Woodweaver's Puzzleknot", null, 2);
        cast(game, "Electrostatic Pummeler", null, 3);
        assertEquals(actor(game).getCounters(CounterEnumType.ENERGY), 6);
        Card pummeler = named(game, "Electrostatic Pummeler", ZoneType.Battlefield);
        for (int index = 0; index < 2; index++) {
            SpellAbility ability = pummeler.getSpellAbilities().stream().filter(sa -> sa.isActivatedAbility()
                    && sa.getApi() == forge.game.ability.ApiType.Pump).findFirst().orElseThrow();
            ability.setActivatingPlayer(actor(game));
            assertTrue(ability.canPlay() && ability.checkRestrictions(pummeler, actor(game)));
            assertTrue(ComputerUtil.playStack(ability, actor(game), game));
            assertEquals(game.getStack().peekAbility().getPayingMana().size(), 0);
            GameSimulator.resolveStack(game, actor(game));
            assertEquals(actor(game).getCounters(CounterEnumType.ENERGY), 3 - 3 * index);
        }
        assertEquals(pummeler.getNetPower(), 4);
        assertEquals(game.getCounterRemovedThisTurn(CounterEnumType.ENERGY, "You", pummeler, actor(game), null), 6);
        return game;
    }

    private void removedHistory(int depth) {
        Game original = spentEnergy();
        Game game = snapshot(original, depth);
        Card pummeler = named(game, "Electrostatic Pummeler", ZoneType.Battlefield);
        assertEquals(game.getCounterRemovedThisTurn(CounterEnumType.ENERGY, "You", pummeler, actor(game), null), 6);
        assertEquals(actor(game).getCounters(CounterEnumType.ENERGY), 0);
    }

    private void reducedHulk(int depth) {
        Game original = spentEnergy();
        Game game = snapshot(original, depth);
        cast(game, "Blaster Hulk", null, 2);
        assertEquals(named(game, "Blaster Hulk", ZoneType.Battlefield).getNetPower(), 8);
        if (depth > 0) absent(original, "Blaster Hulk", ZoneType.Battlefield);
    }

    private void generatorDraw(int depth) {
        Game original = spentEnergy();
        cast(original, "Izzet Generatorium", null, 2);
        Game game = snapshot(original, depth);
        Card generator = named(game, "Izzet Generatorium", ZoneType.Battlefield);
        SpellAbility ability = generator.getSpellAbilities().stream().filter(sa -> sa.isActivatedAbility()
                && sa.getApi() == forge.game.ability.ApiType.Draw).findFirst().orElseThrow();
        ability.setActivatingPlayer(actor(game));
        assertTrue(ability.canPlay() && ability.checkRestrictions(generator, actor(game)),
                "Actual earlier energy payments satisfy the printed activation restriction");
        assertTrue(ComputerUtil.playStack(ability, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 0);
        GameSimulator.resolveStack(game, actor(game));
        assertTrue(generator.isTapped());
        assertEquals(actor(game).getCardsIn(ZoneType.Library).size(), 23);
        if (depth > 0) assertFalse(named(original, "Izzet Generatorium", ZoneType.Battlefield).isTapped());
    }
    @Test public void privateHistoryDepth0() { privateHistory(0); }
    @Test public void privateHistoryDepth1() { privateHistory(1); }
    @Test public void privateHistoryDepth2() { privateHistory(2); }
    @Test public void departedCreatureDepth0() { departedCreature(0); }
    @Test public void departedCreatureDepth1() { departedCreature(1); }
    @Test public void departedCreatureDepth2() { departedCreature(2); }
    @Test public void changedControllerDepth0() { changedController(0); }
    @Test public void changedControllerDepth1() { changedController(1); }
    @Test public void changedControllerDepth2() { changedController(2); }
    @Test public void changedTypeDepth0() { changedType(0); }
    @Test public void changedTypeDepth1() { changedType(1); }
    @Test public void changedTypeDepth2() { changedType(2); }
    @Test public void resetHistoryDepth0() { resetHistory(0); }
    @Test public void resetHistoryDepth1() { resetHistory(1); }
    @Test public void resetHistoryDepth2() { resetHistory(2); }
    @Test public void independentHistoryDepth0() { independentHistory(0); }
    @Test public void independentHistoryDepth1() { independentHistory(1); }
    @Test public void independentHistoryDepth2() { independentHistory(2); }
    @Test public void removedHistoryDepth0() { removedHistory(0); }
    @Test public void removedHistoryDepth1() { removedHistory(1); }
    @Test public void removedHistoryDepth2() { removedHistory(2); }
    @Test public void reducedHulkDepth0() { reducedHulk(0); }
    @Test public void reducedHulkDepth1() { reducedHulk(1); }
    @Test public void reducedHulkDepth2() { reducedHulk(2); }
    @Test public void generatorDrawDepth0() { generatorDraw(0); }
    @Test public void generatorDrawDepth1() { generatorDraw(1); }
    @Test public void generatorDrawDepth2() { generatorDraw(2); }
    @Test public void pendingPlacementOriginal() { pendingPlacement(0, false); }
    @Test public void pendingPlacementDepth1OriginalFirst() { pendingPlacement(1, false); }
    @Test public void pendingPlacementDepth1CopyFirst() { pendingPlacement(1, true); }
    @Test public void pendingPlacementDepth2OriginalFirst() { pendingPlacement(2, false); }
    @Test public void pendingPlacementDepth2CopyFirst() { pendingPlacement(2, true); }

    private Game spentOil() {
        Game game = setup();
        for (int index = 0; index < 8; index++) addCardToZone("Mountain", actor(game), ZoneType.Battlefield);
        cast(game, "Churning Reservoir", null, 1);
        cast(game, "Furnace Strider", null, 5);
        cast(game, "Grizzly Bears", null, 2);
        Card strider = named(game, "Furnace Strider", ZoneType.Battlefield);
        assertEquals(strider.getCounters(CounterEnumType.OIL), 2);
        SpellAbility ability = strider.getSpellAbilities().stream().filter(sa -> sa.isActivatedAbility()
                && sa.getApi() == forge.game.ability.ApiType.Pump).findFirst().orElseThrow();
        ability.setActivatingPlayer(actor(game));
        ability.getTargets().add(named(game, "Grizzly Bears", ZoneType.Battlefield));
        assertTrue(ability.canPlay() && ability.checkRestrictions(strider, actor(game)));
        assertTrue(ComputerUtil.playStack(ability, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 0);
        GameSimulator.resolveStack(game, actor(game));
        assertEquals(strider.getCounters(CounterEnumType.OIL), 1);
        assertEquals(game.getCounterRemovedThisTurn(CounterEnumType.OIL, "Card.YouCtrl+inRealZoneBattlefield",
                strider, actor(game), null), 1);
        return game;
    }

    private void reservoirToken(int depth) {
        Game original = spentOil();
        Game game = snapshot(original, depth);
        Card reservoir = named(game, "Churning Reservoir", ZoneType.Battlefield);
        SpellAbility ability = reservoir.getSpellAbilities().stream().filter(sa -> sa.isActivatedAbility()
                && sa.getApi() == forge.game.ability.ApiType.Token).findFirst().orElseThrow();
        ability.setActivatingPlayer(actor(game));
        assertTrue(ability.canPlay() && ability.checkRestrictions(reservoir, actor(game)),
                "The native earlier oil removal admits the printed token activation");
        assertTrue(ComputerUtil.playStack(ability, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
        GameSimulator.resolveStack(game, actor(game));
        assertTrue(reservoir.isTapped());
        List<Card> tokens = actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(Card::isToken).toList();
        assertEquals(tokens.size(), 1);
        assertEquals(tokens.get(0).getNetPower(), 1);
        assertEquals(tokens.get(0).getNetToughness(), 1);
        assertTrue(tokens.get(0).getType().hasCreatureType("Goblin"));
        assertTrue(tokens.get(0).getType().hasCreatureType("Phyrexian"));
        if (depth > 0) assertTrue(actor(original).getCardsIn(ZoneType.Battlefield).stream().noneMatch(Card::isToken));
    }

    @SuppressWarnings("unchecked")
    private List<Pair<GameEntity, Integer>> oilReceipts(Game game) {
        try {
            java.lang.reflect.Field field = Game.class.getDeclaredField("countersRemovedThisTurn");
            field.setAccessible(true);
            Multimap<CounterType, Pair<GameEntity, Integer>> values =
                    (Multimap<CounterType, Pair<GameEntity, Integer>>) field.get(game);
            return new ArrayList<>(values.get(CounterEnumType.OIL));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot inspect fixed native counter-removal history", exception);
        }
    }

    private void privateRemoval(int depth) {
        Game original = spentOil();
        Game game = snapshot(original, depth);
        List<Pair<GameEntity, Integer>> values = oilReceipts(game);
        assertEquals(values.size(), 1);
        assertEquals(values.get(0).getValue().intValue(), 1);
        Card historical = (Card) values.get(0).getKey();
        assertSame(historical.getGame(), game);
        assertSame(historical.getOwner(), actor(game));
        assertSame(historical.getController(), actor(game));
        Card live = named(game, "Furnace Strider", ZoneType.Battlefield);
        assertNotSame(historical, live);
        assertTrue(actor(game).getCardsIn(ZoneType.Battlefield).stream().noneMatch(card -> card == historical));
        if (depth > 0) {
            Card prior = (Card) oilReceipts(original).get(0).getKey();
            assertNotSame(historical, prior);
            historical.setTapped(true);
            assertFalse(prior.isTapped());
            assertFalse(named(original, "Furnace Strider", ZoneType.Battlefield).isTapped());
        }
        assertFalse(live.isTapped());
    }

    private void resetRemoved(int depth) {
        Game original = spentEnergy();
        Game game = snapshot(original, depth);
        Card pummeler = named(game, "Electrostatic Pummeler", ZoneType.Battlefield);
        assertEquals(game.getCounterRemovedThisTurn(CounterEnumType.ENERGY, "You", pummeler, actor(game), null), 6);
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1,
                () -> GameSimulator.resolveStack(game, actor(game))));
        GameSimulator.resolveStack(game, actor(game));
        assertEquals(game.getCounterRemovedThisTurn(CounterEnumType.ENERGY, "You", pummeler, actor(game), null), 0);
        assertEquals(actor(game).getCounters(CounterEnumType.ENERGY), 0);
        if (depth > 0) assertEquals(original.getCounterRemovedThisTurn(CounterEnumType.ENERGY, "You",
                named(original, "Electrostatic Pummeler", ZoneType.Battlefield), actor(original), null), 6);
    }

    private void castAs(Game game, Player player, String name, Card target, int mana) {
        SpellAbility ability = addCardToZone(name, player, ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(player);
        if (target != null) ability.getTargets().add(target);
        assertTrue(ability.canPlay() && ability.checkRestrictions(ability.getHostCard(), player));
        assertTrue(ComputerUtil.playStack(ability, player, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        GameSimulator.resolveStack(game, player);
    }

    private void filteredPutter(int depth) {
        Game original = counterGame(true);
        Card bear = named(original, "Grizzly Bears", ZoneType.Battlefield);
        Player opponent = original.getPlayers().stream().filter(player -> player.getName().equals("counter-1"))
                .findFirst().orElseThrow();
        for (int index = 0; index < 8; index++) addCardToZone("Forest", opponent, ZoneType.Battlefield);
        original.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent, 5);
        castAs(original, opponent, "Grizzly Bears", null, 2);
        castAs(original, opponent, "Battlegrowth", bear, 1);
        Card otherBear = opponent.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Grizzly Bears")).findFirst().orElseThrow();
        original.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(original), 5);
        cast(original, "Battlegrowth", otherBear, 1);
        Game game = snapshot(original, depth);
        Card beetle = named(game, "Iridescent Hornbeetle", ZoneType.Battlefield);
        assertEquals(game.getCounterAddedThisTurn(CounterEnumType.P1P1, "You", "Creature.YouCtrl", beetle, actor(game), null), 1);
        assertEquals(game.getCounterAddedThisTurn(CounterEnumType.P1P1, "Opponent", "Creature.YouCtrl", beetle, actor(game), null), 1);
        assertEquals(game.getCounterAddedThisTurn(CounterEnumType.P1P1, "You", "Creature.OppCtrl", beetle, actor(game), null), 1);
        assertEquals(game.getCounterAddedThisTurn(CounterEnumType.P1P1, "Player", "Creature", beetle, actor(game), null), 3);
        expectInsect(game, 1);
        if (depth > 0) assertTrue(actor(original).getCardsIn(ZoneType.Battlefield).stream().noneMatch(Card::isToken));
    }
    @Test public void reservoirTokenDepth0() { reservoirToken(0); }
    @Test public void reservoirTokenDepth1() { reservoirToken(1); }
    @Test public void reservoirTokenDepth2() { reservoirToken(2); }
    @Test public void privateRemovalDepth0() { privateRemoval(0); }
    @Test public void privateRemovalDepth1() { privateRemoval(1); }
    @Test public void privateRemovalDepth2() { privateRemoval(2); }
    @Test public void resetRemovedDepth0() { resetRemoved(0); }
    @Test public void resetRemovedDepth1() { resetRemoved(1); }
    @Test public void resetRemovedDepth2() { resetRemoved(2); }
    @Test public void filteredPutterDepth0() { filteredPutter(0); }
    @Test public void filteredPutterDepth1() { filteredPutter(1); }
    @Test public void filteredPutterDepth2() { filteredPutter(2); }
}
