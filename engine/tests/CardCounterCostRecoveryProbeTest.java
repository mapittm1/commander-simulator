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
import forge.game.cost.CostRemoveCounter;
import forge.game.event.GameEventCardCounters;
import forge.game.event.GameEventCardTapped;
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
public class CardCounterCostRecoveryProbeTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Card counter cost recovery"));
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

    private record Setup(Game game, Player actor, Card engraver, Card discard, SpellAbility ability) {
        int removedOil() {
            return game.getCounterRemovedThisTurn(CounterEnumType.OIL, "Creature.YouCtrl", engraver, actor, ability);
        }
    }

    private Setup engraver() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card engraver = addCardToZone("Axiom Engraver", actor, ZoneType.Battlefield);
        Card discard = addCardToZone("Grizzly Bears", actor, ZoneType.Hand);
        addCardToZone("All Will Be One", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        engraver.setSickness(false);
        engraver.setCounters(CounterEnumType.OIL, 2);
        return new Setup(game, actor, engraver, discard, tapAbility(engraver, actor));
    }

    @Test
    public void failedDiscardRefundsTheCostCounterButKeepsIndependentRemoval() {
        Setup setup = engraver();
        setup.ability.setSVar("CostCountersRemoved", "9");
        DiscardFault fault = new DiscardFault(setup);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertEquals(setup.engraver.getCounters(CounterEnumType.OIL), 1);
        assertEquals(setup.removedOil(), 1);
        assertEquals(setup.ability.getSVar("CostCountersRemoved"), "9");
        assertFalse(setup.engraver.isTapped());
        assertTrue(setup.game.getZoneOf(setup.discard).is(ZoneType.Exile));
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void successfulEngraverConsumesOneOilCounterAndDraws() {
        Setup setup = engraver();

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertEquals(setup.engraver.getCounters(CounterEnumType.OIL), 1);
        assertEquals(setup.removedOil(), 1);
        assertEquals(setup.ability.getSVar("CostCountersRemoved"), "1");
        assertTrue(setup.engraver.isTapped());
        assertTrue(setup.game.getZoneOf(setup.discard).is(ZoneType.Graveyard));
        setup.game.getStack().resolveStack();
        assertEquals(setup.actor.getCardsIn(ZoneType.Hand).size(), 1);
    }

    @Test
    public void engraverCannotPayWithCountersRemovedBeforeItsCost() {
        Setup setup = engraver();
        OilFault fault = new OilFault(setup);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertEquals(setup.engraver.getCounters(CounterEnumType.OIL), 0);
        assertEquals(setup.removedOil(), 2);
        assertTrue(setup.game.getZoneOf(setup.discard).is(ZoneType.Hand));
        assertFalse(setup.engraver.isTapped());
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void paidLoyaltyCostStillTriggersChandrasDamage() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card chandra = addCardToZone("Chandra, Fire Artisan", actor, ZoneType.Battlefield);
        chandra.setCounters(CounterEnumType.LOYALTY, 8);
        game.getAction().checkStateEffects(true);
        SpellAbility ultimate = chandra.getSpellAbilities().stream().filter(SpellAbility::isActivatedAbility)
                .filter(ability -> ability.getPayCosts().getCostPartByType(CostRemoveCounter.class) != null)
                .findFirst().orElseThrow();
        ultimate.setActivatingPlayer(actor);

        assertTrue(ComputerUtil.playStack(ultimate, actor, game));
        assertEquals(chandra.getCounters(CounterEnumType.LOYALTY), 1);
        game.getTriggerHandler().collectTriggerForWaiting();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertEquals(game.getStack().size(), 2);
        SpellAbility damage = game.getStack().peekAbility();
        assertTrue(damage.isTrigger());
        assertEquals(damage.getTriggeringObject(AbilityKey.Amount), 7);
        Player target = game.getPlayers().get(1);
        damage.resetTargets();
        damage.getTargets().add(target);
        game.getStack().resolveStack();
        assertEquals(target.getLife(), 33);
    }

    public static final class OilFault {
        private final Setup setup;
        private boolean fired;

        private OilFault(Setup setup) {
            this.setup = setup;
        }

        @Subscribe
        public void afterTapCost(GameEventCardTapped event) {
            if (!fired && event.tapped() && event.card().getId() == setup.engraver.getId()) {
                fired = true;
                setup.engraver.subtractCounter(CounterEnumType.OIL, 2, setup.actor);
            }
        }
    }

    public static final class DiscardFault {
        private final Setup setup;
        private boolean fired;

        private DiscardFault(Setup setup) {
            this.setup = setup;
        }

        @Subscribe
        public void afterCounterCost(GameEventCardCounters event) {
            if (!fired && event.card().getId() == setup.engraver.getId()
                    && event.type() == CounterEnumType.OIL && event.oldValue() == 2 && event.newValue() == 1) {
                fired = true;
                setup.engraver.subtractCounter(CounterEnumType.OIL, 1, setup.actor);
                setup.game.getAction().moveTo(setup.actor.getZone(ZoneType.Exile), setup.discard, null);
            }
        }
    }
}
