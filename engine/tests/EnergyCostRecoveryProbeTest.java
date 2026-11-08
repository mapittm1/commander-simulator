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
import forge.game.event.GameEventPlayerCounters;
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
public class EnergyCostRecoveryProbeTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Energy cost recovery"));
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

    private record Setup(Game game, Player actor, Card era, SpellAbility ability) {
        int spentEnergy() {
            return game.getCounterRemovedThisTurn(CounterEnumType.ENERGY, "You", era, actor, ability);
        }

        void collectTriggers() {
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
        }
    }

    private Setup era() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card era = addCardToZone("Era of Innovation", actor, ZoneType.Battlefield);
        addCardToZone("Fabrication Module", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        actor.setCounters(CounterEnumType.ENERGY, 8, actor, false);
        return new Setup(game, actor, era, tapAbility(era, actor));
    }

    @Test
    public void failedSacrificeReturnsEnergyAndPreservesIndependentEnergyLoss() {
        Setup setup = era();
        EnergyFault fault = new EnergyFault(setup.actor, setup.era);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertEquals(setup.actor.getCounters(CounterEnumType.ENERGY), 7);
        assertEquals(setup.spentEnergy(), 1);
        assertTrue(setup.game.getZoneOf(setup.era).is(ZoneType.Exile));
        setup.collectTriggers();
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void successfulEraRecordsSixEnergyAndDrawsThreeCards() {
        Setup setup = era();

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertEquals(setup.actor.getCounters(CounterEnumType.ENERGY), 2);
        assertEquals(setup.spentEnergy(), 6);
        assertTrue(setup.game.getZoneOf(setup.era).is(ZoneType.Graveyard));
        setup.game.getStack().resolveStack();
        assertEquals(setup.actor.getCardsIn(ZoneType.Hand).size(), 3);
    }

    public static final class EnergyFault {
        private final Player actor;
        private final Card era;
        private boolean fired;

        private EnergyFault(Player actor, Card era) {
            this.actor = actor;
            this.era = era;
        }

        @Subscribe
        public void afterEnergySpent(GameEventPlayerCounters event) {
            if (!fired && event.receiver().getId() == actor.getId()
                    && event.type() == CounterEnumType.ENERGY && event.oldValue() - event.amount() == 6) {
                fired = true;
                actor.loseEnergy(1);
                var game = actor.getGame();
                game.getAction().moveTo(actor.getZone(ZoneType.Exile), era, null);
            }
        }
    }
}
