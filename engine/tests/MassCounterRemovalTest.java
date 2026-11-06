package forge.ai;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import forge.deck.Deck;
import forge.game.*;
import forge.game.ability.ApiType;
import forge.game.ability.effects.CharmEffect;
import forge.game.card.*;
import forge.game.phase.PhaseType;
import forge.game.player.*;
import forge.game.spellability.*;
import forge.game.zone.ZoneType;
import java.util.*;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Paid native mass-counter rules and AI decisions, with explicit setup histories. */
public class MassCounterRemovalTest extends AITest {

    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int i = 0; i < 4; i++) seats.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("massCounters" + i, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Mass counters"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) for (int i = 0; i < 8; i++) {
            for (String land : List.of("Swamp", "Plains", "Forest")) addCardToZone(land, player, ZoneType.Battlefield);
            addCardToZone("Forest", player, ZoneType.Library);
        }
        return game;
    }

    private Card current(Game game, Card card) {
        return game.getCardsInGame().stream().filter(value -> value.getId() == card.getId()).findFirst().orElseThrow();
    }

    private void counters(Card card, CounterType type, int amount) {
        // Fixed previously accumulated counters; not an asserted placement/replacement history.
        Multiset<CounterType> values = HashMultiset.create(card.getCounters());
        values.setCount(type, amount);
        card.setCounters(values);
    }

    private void settle(Game game) {
        for (int guard = 0; guard < 40; guard++) {
            game.getAction().checkStateEffects(true);
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            if (game.isGameOver() || game.getStack().isEmpty()) return;
            game.getStack().resolveStack();
        }
        fail("Mass-counter fixture did not settle");
    }

    private SpellAbility paid(Game game, Card card, int cost) {
        SpellAbility action = card.getFirstSpellAbility();
        action.setActivatingPlayer(card.getController());
        assertTrue(ComputerUtil.playStack(action, action.getActivatingPlayer(), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), cost);
        return game.getStack().peekAbility();
    }

    private void mode(Player actor, int mode) {
        ((PlayerControllerAi) actor.getController()).getAi().setUseSimulation(mode == 0 ? null :
                mode == 1 ? AIOption.USE_HYBRID_SIMULATION : AIOption.USE_FULL_SIMULATION);
    }

    private SpellAbility choose(Player actor, int mode) {
        mode(actor, mode);
        var provider = forge.util.MyRandom.getRandom();
        var actions = actor.getController().chooseSpellAbilityToPlay();
        assertSame(forge.util.MyRandom.getRandom(), provider);
        return actions == null || actions.isEmpty() ? null : actions.get(0);
    }

    private void aetherChoice(int mode, int beneficiary, boolean expectPlay) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card source = addCardToZone("Aether Snap", actor, ZoneType.Hand);
        Card bear = null, walker = null;
        if (beneficiary >= 0) {
            Player owner = game.getPlayers().get(beneficiary);
            bear = addCardToZone("Grizzly Bears", owner, ZoneType.Battlefield);
            counters(bear, CounterEnumType.P1P1, 5);
            walker = addCardToZone("Jace Beleren", owner, ZoneType.Battlefield);
            counters(walker, CounterEnumType.LOYALTY, 8);
        }
        SpellAbility action = choose(actor, mode);
        assertEquals(actor.getCardsIn(ZoneType.Hand).size(), 1);
        if (!expectPlay) {
            assertTrue(action == null || action.getHostCard() != source);
            return;
        }
        assertNotNull(action);
        assertSame(action.getHostCard(), source);
        assertEquals(action.getApi(), ApiType.RemoveCounterAll);
        assertEquals(bear.getCounters(CounterEnumType.P1P1), 5);
        assertEquals(walker.getCounters(CounterEnumType.LOYALTY), 8);
        assertTrue(ComputerUtil.playStack(action, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 5);
        settle(game);
        assertEquals(bear.getCounters(CounterEnumType.P1P1), 0);
        assertEquals(current(game, walker).getZone().getZoneType(), ZoneType.Graveyard);
        assertEquals(current(game, source).getZone().getZoneType(), ZoneType.Graveyard);
    }

    @Test
    public void aetherHeuristic() {
        aetherChoice(0, 1, true);
    }

    @Test
    public void aetherHybrid() {
        aetherChoice(1, 1, true);
    }

    @Test
    public void aetherFull() {
        aetherChoice(2, 1, true);
    }

    @Test
    public void aetherNoBenefit() {
        aetherChoice(0, -1, false);
    }

    @Test
    public void aetherOwnHarm() {
        aetherChoice(0, 0, false);
    }

    @Test
    public void aetherNativeCounterBoundaries() {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        Card creature = addCardToZone("Grizzly Bears", enemy, ZoneType.Battlefield);
        counters(creature, CounterEnumType.P1P1, 3);
        counters(creature, CounterEnumType.CHARGE, 4);
        Card exiled = addCardToZone("Durkwood Baloth", enemy, ZoneType.Exile);
        counters(exiled, CounterEnumType.TIME, 5);
        Multiset<CounterType> playerCounters = HashMultiset.create();
        playerCounters.add(CounterEnumType.POISON, 3);
        enemy.setCounters(playerCounters);
        Card source = addCardToZone("Aether Snap", actor, ZoneType.Hand);
        paid(game, source, 5);
        settle(game);
        assertTrue(creature.getCounters().isEmpty());
        assertEquals(exiled.getCounters(CounterEnumType.TIME), 5);
        assertEquals(enemy.getCounters(CounterEnumType.POISON), 3);
    }

    @Test
    public void aetherNativeTokenExile() {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        Card token = addCardToZone("Grizzly Bears", enemy, ZoneType.Battlefield);
        token.setGamePieceType(forge.card.GamePieceType.TOKEN);
        Card ordinary = addCardToZone("Grizzly Bears", enemy, ZoneType.Battlefield);
        paid(game, addCardToZone("Aether Snap", actor, ZoneType.Hand), 5);
        game.getStack().resolveStack();
        assertEquals(token.getZone().getZoneType(), ZoneType.Exile);
        assertEquals(ordinary.getZone().getZoneType(), ZoneType.Battlefield);
    }

    private SpellAbility dust(Game game, int index) {
        Card source = addCardToZone("Dust of Moments", game.getPlayers().get(0), ZoneType.Hand);
        SpellAbility action = source.getFirstSpellAbility();
        action.setActivatingPlayer(source.getController());
        List<AbilitySub> modes = new ArrayList<>();
        modes.add(action.getAdditionalAbilityList("Choices").get(index));
        action.setChosenList(modes);
        CharmEffect.chainAbilities(action, modes);
        assertTrue(ComputerUtil.playStack(action, source.getController(), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        return game.getStack().peekAbility();
    }

    @Test
    public void dustNativeBothZones() {
        Game game = pod();
        Player owner = game.getPlayers().get(1);
        Card permanent = addCardToZone("Calciderm", owner, ZoneType.Battlefield);
        counters(permanent, CounterEnumType.TIME, 4);
        counters(permanent, CounterEnumType.P1P1, 1);
        Card suspended = addCardToZone("Durkwood Baloth", owner, ZoneType.Exile);
        counters(suspended, CounterEnumType.TIME, 5);
        Card other = addCardToZone("Grizzly Bears", owner, ZoneType.Exile);
        counters(other, CounterEnumType.TIME, 5);
        dust(game, 0);
        settle(game);
        assertEquals(permanent.getCounters(CounterEnumType.TIME), 2);
        assertEquals(permanent.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(suspended.getCounters(CounterEnumType.TIME), 3);
        assertEquals(other.getCounters(CounterEnumType.TIME), 5);
    }

    @Test
    public void dustNativeLastCounterReleasesSuspendedCard() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card suspended = addCardToZone("Durkwood Baloth", actor, ZoneType.Exile);
        counters(suspended, CounterEnumType.TIME, 1);
        dust(game, 0);
        settle(game);
        Card current=game.getCardsInGame().stream().filter(c->c.getId()==suspended.getId()).findFirst().orElseThrow();
        assertEquals(current.getZone().getZoneType(), ZoneType.Battlefield);
        assertEquals(current.getCounters(CounterEnumType.TIME), 0);
    }

    @Test
    public void dustNativeLastCounterSacrificesVanishingPermanent() {
        Game game = pod();
        Player enemy = game.getPlayers().get(1);
        Card victim = addCardToZone("Calciderm", enemy, ZoneType.Battlefield);
        counters(victim, CounterEnumType.TIME, 2);
        dust(game, 0);
        settle(game);
        assertEquals(current(game, victim).getZone().getZoneType(), ZoneType.Graveyard);
    }

    @Test
    public void forecastAdmitsLastCounterTrigger() {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        Card victim = addCardToZone("Calciderm", enemy, ZoneType.Battlefield);
        counters(victim, CounterEnumType.TIME, 2);
        Card source = addCardToZone("Dust of Moments", actor, ZoneType.Hand);
        SpellAbility action = source.getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        List<AbilitySub> modes = new ArrayList<>();
        modes.add(action.getAdditionalAbilityList("Choices").get(0));
        action.setChosenList(modes);
        CharmEffect.chainAbilities(action, modes);
        var start = forge.ai.simulation.GameSimulator.scoreForDecision(game, actor);
        var simulator = new forge.ai.simulation.GameSimulator(new forge.ai.simulation.SimulationController(start, 0), game, actor, null);
        simulator.simulateSpellAbility(action);
        assertEquals(current(simulator.getSimulatedGameState(), victim).getZone().getZoneType(), ZoneType.Graveyard);
        assertEquals(victim.getCounters(CounterEnumType.TIME), 2);
        assertEquals(source.getZone().getZoneType(), ZoneType.Hand);
    }

    private void dustChoice(int mode) {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        Card victim = addCardToZone("Calciderm", enemy, ZoneType.Battlefield);
        counters(victim, CounterEnumType.TIME, 2);
        Card source = addCardToZone("Dust of Moments", actor, ZoneType.Hand);
        SpellAbility action = choose(actor, mode);
        assertNotNull(action);
        assertSame(action.getHostCard(), source);
        assertEquals(victim.getCounters(CounterEnumType.TIME), 2);
        assertTrue(actor.getController().playChosenSpellAbility(action));
        assertFalse(game.getStack().isEmpty());
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        assertEquals(game.getStack().peekAbility().getSubAbility().getApi(), ApiType.RemoveCounterAll);
        settle(game);
        assertEquals(current(game, victim).getZone().getZoneType(), ZoneType.Graveyard);
    }

    @Test
    public void dustHeuristic() {
        dustChoice(0);
    }

    @Test
    public void dustHybrid() {
        dustChoice(1);
    }

    @Test
    public void dustFull() {
        dustChoice(2);
    }

    private void stoneChoice(int mode) {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        Card source = addCardToZone("Oblivion Stone", actor, ZoneType.Battlefield);
        Card protectedCard = addCardToZone("Darksteel Ingot", actor, ZoneType.Battlefield);
        counters(protectedCard, CounterEnumType.FATE, 1);
        for(String name:List.of("Serra Angel", "Shivan Dragon", "Craw Wurm"))addCardToZone(name, enemy, ZoneType.Battlefield);
        SpellAbility action = choose(actor, mode);
        assertNotNull(action);
        assertSame(action.getHostCard(), source);
        assertEquals(action.getApi(), ApiType.DestroyAll);
        assertTrue(ComputerUtil.playStack(action, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 5);
        settle(game);
        assertEquals(current(game, source).getZone().getZoneType(), ZoneType.Graveyard);
        assertEquals(enemy.getCreaturesInPlay().size(), 0);
        assertEquals(protectedCard.getZone().getZoneType(), ZoneType.Battlefield);
        assertEquals(protectedCard.getCounters(CounterEnumType.FATE), 0);
    }

    @Test
    public void stoneHeuristic() {
        stoneChoice(0);
    }

    @Test
    public void stoneHybrid() {
        stoneChoice(1);
    }

    @Test
    public void stoneFull() {
        stoneChoice(2);
    }

    @Test
    public void heartmenderMandatoryUpkeep() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card source = addCardToZone("Heartmender", actor, ZoneType.Battlefield);
        counters(source, CounterEnumType.M1M1, 1);
        Card other = addCardToZone("Craw Wurm", actor, ZoneType.Battlefield);
        counters(other, CounterEnumType.M1M1, 2);
        game.getPhaseHandler().devModeSet(PhaseType.UPKEEP, actor, 2);
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().runTrigger(forge.game.trigger.TriggerType.Phase, forge.game.ability.AbilityKey.mapFromPlayer(actor), false);
        settle(game);
        assertEquals(source.getCounters(CounterEnumType.M1M1), 0);
        assertEquals(other.getCounters(CounterEnumType.M1M1), 1);
    }

    private void hazeChoice(int mode) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card walker = addCardToZone("Jace Beleren", game.getPlayers().get(1), ZoneType.Battlefield);
        counters(walker, CounterEnumType.LOYALTY, 2);
        Card source = addCardToZone("Pestilent Haze", actor, ZoneType.Hand);
        SpellAbility action = choose(actor, mode);
        assertNotNull(action);
        assertSame(action.getHostCard(), source);
        assertTrue(actor.getController().playChosenSpellAbility(action));
        assertFalse(game.getStack().isEmpty());
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        assertEquals(game.getStack().peekAbility().getSubAbility().getApi(), ApiType.RemoveCounterAll);
        settle(game);
        assertEquals(current(game, walker).getZone().getZoneType(), ZoneType.Graveyard);
    }

    @Test
    public void hazeHeuristic() {
        hazeChoice(0);
    }

    @Test
    public void hazeHybrid() {
        hazeChoice(1);
    }

    @Test
    public void hazeFull() {
        hazeChoice(2);
    }

    @Test
    public void hazeNativeLoyaltyBoundaries() {
        Game game = pod();
        Player actor = game.getPlayers().get(0), enemy = game.getPlayers().get(1);
        Card low = addCardToZone("Jace Beleren", enemy, ZoneType.Battlefield);
        Card high = addCardToZone("Chandra Nalaar", enemy, ZoneType.Battlefield);
        Card creature = addCardToZone("Grizzly Bears", enemy, ZoneType.Battlefield);
        counters(low, CounterEnumType.LOYALTY, 1);
        counters(high, CounterEnumType.LOYALTY, 4);
        counters(creature, CounterEnumType.P1P1, 3);
        Card source = addCardToZone("Pestilent Haze", actor, ZoneType.Hand);
        SpellAbility action = source.getFirstSpellAbility();
        action.setActivatingPlayer(actor);
        List<AbilitySub> modes = new ArrayList<>();
        modes.add(action.getAdditionalAbilityList("Choices").get(1));
        action.setChosenList(modes);
        CharmEffect.chainAbilities(action, modes);
        assertTrue(ComputerUtil.playStack(action, actor, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        settle(game);
        assertEquals(current(game, low).getZone().getZoneType(), ZoneType.Graveyard);
        assertEquals(high.getCounters(CounterEnumType.LOYALTY), 2);
        assertEquals(creature.getCounters(CounterEnumType.P1P1), 3);
    }
}
