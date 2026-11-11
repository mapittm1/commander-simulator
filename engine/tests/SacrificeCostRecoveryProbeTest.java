package forge.ai;

import com.google.common.eventbus.Subscribe;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.event.GameEventCardChangeZone;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Native activation payment with a deterministic fault between two costs. */
public class SacrificeCostRecoveryProbeTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Sacrifice cost recovery"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, game.getPlayers().get(0), 1);
        for (Player player : game.getPlayers()) {
            for (int count = 0; count < 8; count++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
        }
        return game;
    }

    private SpellAbility activation(Card host, Player actor) {
        SpellAbility ability = host.getSpellAbilities().stream()
                .filter(candidate -> candidate.isActivatedAbility() && !candidate.isManaAbility())
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(actor);
        return ability;
    }

    private static void collectTriggers(Game game) {
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private record Setup(Game game, Player actor, Card herald, Card blue, Card red, List<Card> lands, SpellAbility ability) {
        void collectTriggers() {
            SacrificeCostRecoveryProbeTest.collectTriggers(game);
        }
    }

    private Setup herald() {
        return herald(false);
    }

    private Setup herald(boolean useToken) {
        return herald(useToken, false);
    }

    private Setup herald(boolean useToken, boolean useStolen) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card herald = addCardToZone("Demon's Herald", actor, ZoneType.Battlefield);
        Player owner = useStolen ? game.getPlayers().get(1) : actor;
        Card blue = useToken ? addToken("u_1_1_merfolk", actor)
                : addCardToZone("Merfolk Looter", owner, ZoneType.Battlefield);
        if (useStolen) {
            blue.setController(actor, game.getNextTimestamp());
            game.getAction().controllerChangeZoneCorrection(blue);
        }
        Card red = addCardToZone("Goblin Piker", actor, ZoneType.Battlefield);
        addCardToZone("Fecundity", actor, ZoneType.Battlefield);
        List<Card> lands = new ArrayList<>();
        for (String name : List.of("Swamp", "Wastes", "Wastes")) {
            lands.add(addCardToZone(name, actor, ZoneType.Battlefield));
        }
        game.getAction().checkStateEffects(true);
        herald.setSickness(false);
        blue.setCounters(CounterEnumType.P1P1, 1);
        return new Setup(game, actor, herald, blue, red, lands, activation(herald, actor));
    }

    @Test
    public void failedLaterSacrificeRestoresEarlierCreatureAndItsCounter() {
        Setup setup = herald();
        SacrificeFault fault = new SacrificeFault(setup);
        setup.game.subscribeToEvents(fault);

        assertTrue(ComputerUtilCost.canPayCost(setup.ability, setup.actor, false));
        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertTrue(setup.game.getZoneOf(setup.red).is(ZoneType.Exile));
        assertTrue(setup.game.getZoneOf(setup.blue).is(ZoneType.Battlefield));
        assertTrue(setup.game.getCardState(setup.blue) == setup.blue);
        assertEquals(setup.blue.getCounters(CounterEnumType.P1P1), 1);
        assertFalse(setup.herald.isTapped());
        assertTrue(setup.lands.stream().noneMatch(Card::isTapped));
        assertEquals(setup.actor.getDescended(), 0);
        setup.collectTriggers();
        if (!setup.game.getStack().isEmpty()) {
            throw new AssertionError("Unexpected trigger: " + setup.game.getStack().peekAbility());
        }
    }

    @Test
    public void successfulHeraldKeepsAllSacrificesAndDeathTriggers() {
        Setup setup = herald();

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertEquals(setup.actor.getCardsIn(ZoneType.Graveyard).size(), 3);
        assertEquals(setup.actor.getDescended(), 3);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 4);
    }

    @Test
    public void failedLaterSacrificeRestoresEquipmentWithoutAnUnattachTrigger() {
        Setup setup = herald();
        Card gear = addCardToZone("Grafted Wargear", setup.actor, ZoneType.Battlefield);
        gear.attachToEntity(setup.blue, null);
        setup.game.getAction().checkStaticAbilities();
        long attachedTimestamp = gear.getLayerTimestamp();
        int equippedPower = setup.blue.getNetPower();
        assertTrue(gear.getEntityAttachedTo() == setup.blue);
        SacrificeFault fault = new SacrificeFault(setup);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertTrue(setup.game.getZoneOf(setup.red).is(ZoneType.Exile));
        assertTrue(gear.getEntityAttachedTo() == setup.blue);
        assertTrue(setup.blue.getAttachedCards().contains(gear));
        assertEquals(gear.getLayerTimestamp(), attachedTimestamp);
        assertEquals(setup.blue.getNetPower(), equippedPower);
        assertTrue(setup.game.getCardState(setup.blue) == setup.blue);
        setup.collectTriggers();
        if (!setup.game.getStack().isEmpty()) {
            throw new AssertionError("Unexpected trigger: " + setup.game.getStack().peekAbility());
        }
    }

    @Test
    public void successfulSacrificeStillDetachesEquipmentAndQueuesItsTrigger() {
        Setup setup = herald();
        Card gear = addCardToZone("Grafted Wargear", setup.actor, ZoneType.Battlefield);
        gear.attachToEntity(setup.blue, null);
        setup.game.getAction().checkStaticAbilities();

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertFalse(gear.isAttachedToEntity());
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 5);
    }

    @Test
    public void failedLaterSacrificeRestoresTheSameToken() {
        Setup setup = herald(true);
        assertTrue(setup.blue.isToken());
        SacrificeFault fault = new SacrificeFault(setup);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertTrue(setup.game.getZoneOf(setup.red).is(ZoneType.Exile));
        assertTrue(setup.actor.getCardsIn(ZoneType.Battlefield).stream().anyMatch(card -> card == setup.blue));
        assertEquals(setup.blue.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(setup.actor.getDescended(), 0);
        setup.collectTriggers();
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void successfulSacrificeConsumesTheTokenAndQueuesItsDeathTrigger() {
        Setup setup = herald(true);
        assertTrue(setup.blue.isToken());
        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertEquals(setup.actor.getCardsIn(ZoneType.Graveyard).size(), 2);
        assertEquals(setup.actor.getDescended(), 2);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 4);
    }

    @Test
    public void failedLaterSacrificeReversesRestInPeaceExile() {
        Setup setup = herald();
        addCardToZone("Rest in Peace", setup.actor, ZoneType.Battlefield);
        setup.game.getAction().checkStaticAbilities();
        SacrificeFault fault = new SacrificeFault(setup, ZoneType.Exile);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertTrue(setup.game.getZoneOf(setup.red).is(ZoneType.Exile));
        assertTrue(setup.game.getZoneOf(setup.blue).is(ZoneType.Battlefield));
        assertTrue(setup.game.getCardState(setup.blue) == setup.blue);
        assertEquals(setup.blue.getCounters(CounterEnumType.P1P1), 1);
        assertTrue(setup.actor.getSacrificedThisTurn().isEmpty());
        assertEquals(setup.actor.getDescended(), 0);
        setup.collectTriggers();
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void successfulSacrificeExilesUnderRestInPeaceWithoutDeathTriggers() {
        Setup setup = herald();
        addCardToZone("Rest in Peace", setup.actor, ZoneType.Battlefield);
        setup.game.getAction().checkStaticAbilities();

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertEquals(setup.actor.getCardsIn(ZoneType.Exile).size(), 3);
        assertEquals(setup.actor.getSacrificedThisTurn().size(), 3);
        assertEquals(setup.actor.getDescended(), 0);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 1);
    }

    @Test
    public void failedExilePaymentKeepsOnlyTheIndependentSoulherderTrigger() {
        Setup setup = herald();
        addCardToZone("Rest in Peace", setup.actor, ZoneType.Battlefield);
        Card soulherder = addCardToZone("Soulherder", setup.game.getPlayers().get(1), ZoneType.Battlefield);
        setup.game.getAction().checkStaticAbilities();
        SacrificeFault fault = new SacrificeFault(setup, ZoneType.Exile);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertTrue(setup.game.getZoneOf(setup.red).is(ZoneType.Exile));
        assertTrue(setup.game.getZoneOf(setup.blue).is(ZoneType.Battlefield));
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 1);
        assertTrue(setup.game.getStack().peekAbility().getHostCard() == soulherder);
        setup.game.getStack().resolveStack();
        assertEquals(soulherder.getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void failedPaymentKeepsTheLibraryMoveAndRefundsTheGraveyardMove() {
        Setup setup = herald();
        Card colossus = addCardToZone("Blightsteel Colossus", setup.actor, ZoneType.Battlefield);
        setup.herald.setColor("Colorless");
        colossus.setColor("Black");
        Card mazirek = addCardToZone("Mazirek, Kraul Death Priest", setup.game.getPlayers().get(1), ZoneType.Battlefield);
        setup.game.getAction().checkStaticAbilities();
        SacrificeFault fault = new SacrificeFault(setup, colossus, ZoneType.Library);
        setup.game.subscribeToEvents(fault);

        assertTrue(ComputerUtilCost.canPayCost(setup.ability, setup.actor, false));
        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertTrue(setup.game.getZoneOf(colossus).is(ZoneType.Library));
        assertTrue(setup.game.getZoneOf(setup.red).is(ZoneType.Exile));
        assertTrue(setup.game.getCardState(setup.blue) == setup.blue);
        assertTrue(setup.game.getZoneOf(setup.blue).is(ZoneType.Battlefield));
        assertFalse(setup.herald.isTapped());
        assertTrue(setup.lands.stream().noneMatch(Card::isTapped));
        assertEquals(setup.actor.getSacrificedThisTurn().size(), 1);
        assertEquals(setup.actor.getSacrificedThisTurn().get(0).getId(), colossus.getId());
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 1);
        assertTrue(setup.game.getStack().peekAbility().getHostCard() == mazirek);
    }

    @Test
    public void failedSacrificeRestoresThePreviousControllerAndOwnersDescendCount() {
        Setup setup = herald(false, true);
        Player owner = setup.blue.getOwner();
        assertTrue(owner != setup.actor);
        assertTrue(setup.blue.getController() == setup.actor);
        assertTrue(setup.actor.getCardsIn(ZoneType.Battlefield).stream().anyMatch(card -> card == setup.blue));
        SacrificeFault fault = new SacrificeFault(setup);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertTrue(setup.actor.getCardsIn(ZoneType.Battlefield).stream().anyMatch(card -> card == setup.blue));
        assertTrue(setup.blue.getController() == setup.actor);
        assertTrue(setup.blue.getOwner() == owner);
        assertEquals(setup.blue.getCounters(CounterEnumType.P1P1), 1);
        assertEquals(owner.getDescended(), 0);
        assertEquals(setup.actor.getDescended(), 0);
        assertTrue(setup.actor.getSacrificedThisTurn().isEmpty());
        setup.collectTriggers();
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void successfulSacrificeMovesAStolenCardToItsOwnersGraveyard() {
        Setup setup = herald(false, true);
        Player owner = setup.blue.getOwner();

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(owner.getCardsIn(ZoneType.Graveyard).contains(setup.blue));
        assertEquals(owner.getDescended(), 1);
        assertEquals(setup.actor.getDescended(), 2);
        assertEquals(setup.actor.getSacrificedThisTurn().size(), 3);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 4);
    }

    private record FoodSetup(Game game, Player actor, Card king, Card camellia, List<Card> food, SpellAbility ability) {
    }

    private FoodSetup trollKing() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card king = addCardToZone("Feasting Troll King", actor, ZoneType.Graveyard);
        Card camellia = addCardToZone("Camellia, the Seedmiser", actor, ZoneType.Battlefield);
        List<Card> food = List.of(addCardToZone("Gingerbrute", actor, ZoneType.Battlefield),
                addCardToZone("Tough Cookie", actor, ZoneType.Battlefield),
                addToken("c_a_food_sac", actor));
        game.getAction().checkStateEffects(true);
        return new FoodSetup(game, actor, king, camellia, food, activation(king, actor));
    }

    @Test
    public void failedGroupedFoodPaymentSuppressesTheGroupedTrigger() {
        FoodSetup setup = trollKing();
        FoodFault fault = new FoodFault(setup);
        setup.game.subscribeToEvents(fault);
        assertTrue(ComputerUtilCost.canPayCost(setup.ability, setup.actor, false));

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.paidFirst != null);
        assertTrue(setup.actor.getCardsIn(ZoneType.Battlefield).stream().anyMatch(card -> card == fault.paidFirst));
        for (Card remaining : setup.food) {
            if (remaining != fault.paidFirst && !remaining.isToken()) {
                assertTrue(setup.game.getZoneOf(remaining).is(ZoneType.Exile));
            }
        }
        assertTrue(setup.actor.getSacrificedThisTurn().isEmpty());
        collectTriggers(setup.game);
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void successfulGroupedFoodPaymentTriggersCamelliaOnce() {
        FoodSetup setup = trollKing();
        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertEquals(setup.actor.getSacrificedThisTurn().size(), 3);
        collectTriggers(setup.game);
        assertEquals(setup.game.getStack().size(), 2);
        assertTrue(setup.game.getStack().peekAbility().getHostCard() == setup.camellia);
        setup.game.getStack().resolveStack();
        assertEquals(setup.actor.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isToken() && card.getType().hasSubtype("Squirrel")).count(), 1L);
        setup.game.getStack().resolveStack();
        assertTrue(setup.game.getZoneOf(setup.king).is(ZoneType.Battlefield));
    }

    public static final class FoodFault {
        private final FoodSetup setup;
        private Card paidFirst;

        private FoodFault(FoodSetup setup) {
            this.setup = setup;
        }

        @Subscribe
        public void afterFirstFood(GameEventCardChangeZone event) {
            if (paidFirst != null || event.to() == null || event.to().zoneType() != ZoneType.Graveyard) {
                return;
            }
            for (Card food : setup.food) {
                if (food.getId() == event.card().getId()) {
                    paidFirst = food;
                    break;
                }
            }
            if (paidFirst != null) {
                for (Card food : setup.food) {
                    if (food != paidFirst) {
                        setup.game.getAction().exile(food, null, AbilityKey.newMap());
                    }
                }
            }
        }
    }

    public static final class SacrificeFault {
        private final Setup setup;
        private final Card watched;
        private final ZoneType firstDestination;
        private boolean fired;

        private SacrificeFault(Setup setup) {
            this(setup, ZoneType.Graveyard);
        }

        private SacrificeFault(Setup setup, ZoneType firstDestination) {
            this(setup, setup.blue, firstDestination);
        }

        private SacrificeFault(Setup setup, Card watched, ZoneType firstDestination) {
            this.setup = setup;
            this.watched = watched;
            this.firstDestination = firstDestination;
        }

        @Subscribe
        public void afterFirstSacrifice(GameEventCardChangeZone event) {
            if (!fired && event.card().getId() == watched.getId() && event.to() != null
                    && event.to().zoneType() == firstDestination) {
                fired = true;
                setup.game.getAction().exile(setup.red, null, AbilityKey.newMap());
            }
        }
    }
}
