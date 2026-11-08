package forge.ai;

import com.google.common.eventbus.Subscribe;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.card.CounterType;
import forge.game.event.EventValueChangeType;
import forge.game.event.GameEventManaPool;
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
public class GroupedCounterCostProbeTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Grouped counter cost"));
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


    private SpellAbility tapAbility(Card host, Player actor) {
        SpellAbility ability = host.getSpellAbilities().stream()
                .filter(candidate -> candidate.isActivatedAbility() && !candidate.isManaAbility())
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(actor);
        return ability;
    }

    private record Setup(Game game, Player actor, Card tayam, List<Card> creatures, List<Card> wastes,
            SpellAbility ability) {
    }

    private static final CounterType VIGILANCE = CounterType.getType("Vigilance");

    private Setup tayam(CounterType type) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card tayam = addCardToZone("Tayam, Luminous Enigma", actor, ZoneType.Battlefield);
        List<Card> creatures = new ArrayList<>();
        List<Card> wastes = new ArrayList<>();
        for (String name : List.of("Grizzly Bears", "Runeclaw Bear", "Ashcoat Bear")) {
            creatures.add(addCardToZone(name, actor, ZoneType.Battlefield));
            wastes.add(addCardToZone("Wastes", actor, ZoneType.Battlefield));
        }
        game.getAction().checkStateEffects(true);
        for (Card card : creatures) {
            card.clearCounters();
            card.setCounters(type, 1);
        }
        return new Setup(game, actor, tayam, creatures, wastes, tapAbility(tayam, actor));
    }

    @Test
    public void tayamCannotReuseACounterRemovedDuringManaPayment() {
        Setup setup = tayam(VIGILANCE);
        Card third = setup.creatures.get(2);
        CounterFault fault = new CounterFault(setup.actor, third);
        setup.game.subscribeToEvents(fault);

        assertTrue(ComputerUtilCost.canPayCost(setup.ability, setup.actor, false));
        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertEquals(third.getCounters(VIGILANCE), 0);
        assertTrue(setup.creatures.subList(0, 2).stream().allMatch(card -> card.getCounters(VIGILANCE) == 1));
        assertTrue(setup.wastes.stream().noneMatch(Card::isTapped));
        assertTrue(setup.actor.getManaPool().isEmpty());
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void tayamPaysThreeCountersFromThreeDistinctCreatures() {
        Setup setup = tayam(VIGILANCE);

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertEquals(setup.ability.getSVar("CostCountersRemoved"), "3");
        assertTrue(setup.creatures.stream().allMatch(card -> card.getCounters(VIGILANCE) == 0));
        assertTrue(setup.wastes.stream().allMatch(Card::isTapped));
        assertFalse(setup.game.getStack().isEmpty());
    }

    @Test
    public void tayamCanUseThreeCountersOutsideItsPreferredType() {
        Setup setup = tayam(CounterEnumType.OIL);

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertEquals(setup.ability.getSVar("CostCountersRemoved"), "3");
        assertTrue(setup.creatures.stream().allMatch(card -> card.getCounters(CounterEnumType.OIL) == 0));
    }

    @Test
    public void tayamUsesExactlyThreePreferredCountersWhenFourAreAvailable() {
        Setup setup = tayam(CounterEnumType.OIL);
        for (Card card : setup.creatures) {
            card.clearCounters();
        }
        Card primus = addCardToZone("Woodfall Primus", setup.actor, ZoneType.Battlefield);
        Card kelpie = addCardToZone("River Kelpie", setup.actor, ZoneType.Battlefield);
        for (Card card : List.of(primus, kelpie)) {
            card.clearCounters();
            card.setCounters(CounterEnumType.M1M1, 2);
        }
        setup.game.getAction().checkStateEffects(true);

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertEquals(setup.ability.getSVar("CostCountersRemoved"), "3");
        assertEquals(primus.getCounters(CounterEnumType.M1M1) + kelpie.getCounters(CounterEnumType.M1M1), 1);
    }

    @Test
    public void tayamCannotSpendACounterOnACreatureThatChangedControllers() {
        Setup setup = tayam(VIGILANCE);
        Card third = setup.creatures.get(2);
        Player opponent = setup.game.getPlayers().get(1);
        CounterFault fault = new CounterFault(setup.actor, third, opponent);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertEquals(third.getController(), opponent);
        assertTrue(setup.creatures.stream().allMatch(card -> card.getCounters(VIGILANCE) == 1));
        assertTrue(setup.wastes.stream().noneMatch(Card::isTapped));
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void tayamUsesOilAndLeavesProtectedStunCounters() {
        Setup setup = tayam(CounterEnumType.OIL);
        addCardToZone("Fear of Sleep Paralysis", setup.game.getPlayers().get(1), ZoneType.Battlefield);
        for (Card card : setup.creatures) {
            card.setCounters(CounterEnumType.STUN, 1);
        }
        setup.game.getAction().checkStateEffects(true);

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertEquals(setup.ability.getSVar("CostCountersRemoved"), "3");
        assertTrue(setup.creatures.stream().allMatch(card -> card.getCounters(CounterEnumType.OIL) == 0
                && card.getCounters(CounterEnumType.STUN) == 1));
    }

    public static final class CounterFault {
        private final Player actor;
        private final Card card;
        private final Player newController;
        private boolean fired;

        private CounterFault(Player actor, Card card) {
            this(actor, card, null);
        }

        private CounterFault(Player actor, Card card, Player newController) {
            this.actor = actor;
            this.card = card;
            this.newController = newController;
        }

        @Subscribe
        public void afterManaAdded(GameEventManaPool event) {
            if (!fired && event.mode() == EventValueChangeType.Added && event.player().getId() == actor.getId()) {
                fired = true;
                if (newController == null) {
                    card.subtractCounter(VIGILANCE, 1, actor);
                } else {
                    card.setController(newController, actor.getGame().getNextTimestamp());
                }
            }
        }
    }
}
