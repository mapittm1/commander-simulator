package forge.ai;

import com.google.common.eventbus.Subscribe;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameLogEntryType;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.event.GameEventAddLog;
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
public class MixedDiscardRecoveryProbeTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Mixed discard recovery"));
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

    private record Setup(Game game, Player actor, Card source, Card first, Card second, SpellAbility ability) {
        void collectTriggers() {
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
        }
    }

    private Setup reaver(String firstName, String secondName) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card source = addCardToZone("Plague Reaver", actor, ZoneType.Battlefield);
        Card first = addCardToZone(firstName, actor, ZoneType.Hand);
        Card second = addCardToZone(secondName, actor, ZoneType.Hand);
        addCardToZone("Dying to Serve", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        SpellAbility ability = tapAbility(source, actor);
        ability.getTargets().add(game.getPlayers().get(1));
        return new Setup(game, actor, source, first, second, ability);
    }

    @Test
    public void mixedMadnessAndOrdinaryDiscardsReturnToTheirOriginalHandOrder() {
        Setup setup = reaver("Fiery Temper", "Grizzly Bears");
        DiscardFault fault = new DiscardFault(setup);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertEquals(List.copyOf(setup.actor.getCardsIn(ZoneType.Hand)), List.of(setup.first, setup.second));
        assertTrue(setup.actor.getDiscardedThisTurn().isEmpty());
        assertEquals(setup.actor.getDescended(), 0);
        assertEquals(fault.discardLogs, 0);
        setup.collectTriggers();
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void mixedDiscardPreservesTheCardMovedIntoALibrary() {
        Setup setup = reaver("Grizzly Bears", "Darksteel Colossus");
        DiscardFault fault = new DiscardFault(setup);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertTrue(setup.game.getZoneOf(setup.first).is(ZoneType.Hand));
        assertTrue(setup.game.getZoneOf(setup.second).is(ZoneType.Library));
        assertEquals(setup.actor.getDiscardedThisTurn().stream().map(Card::getId).toList(), List.of(setup.second.getId()));
        assertEquals(fault.discardLogs, 1);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 1);
        assertEquals(setup.game.getStack().peekAbility().getHostCard().getName(), "Dying to Serve");
    }

    @Test
    public void restInPeaceDiscardReturnsToHandOnFailedSacrifice() {
        Setup setup = reaver("Grizzly Bears", "Runeclaw Bear");
        addCardToZone("Rest in Peace", setup.game.getPlayers().get(1), ZoneType.Battlefield);
        setup.game.getAction().checkStateEffects(true);
        DiscardFault fault = new DiscardFault(setup);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertEquals(List.copyOf(setup.actor.getCardsIn(ZoneType.Hand)), List.of(setup.first, setup.second));
        assertTrue(setup.actor.getDiscardedThisTurn().isEmpty());
        assertEquals(fault.discardLogs, 0);
        setup.collectTriggers();
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void successfulMixedDiscardEmitsEachLogAndPreservesTheMadnessTrigger() {
        Setup setup = reaver("Fiery Temper", "Grizzly Bears");
        DiscardFault observer = new DiscardFault(setup, false);
        setup.game.subscribeToEvents(observer);

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(setup.game.getZoneOf(setup.first).is(ZoneType.Exile));
        assertTrue(setup.game.getZoneOf(setup.second).is(ZoneType.Graveyard));
        assertEquals(observer.discardLogs, 2);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 3);
    }

    public static final class DiscardFault {
        private final Setup setup;
        private final boolean armed;
        private boolean fired;
        private int discardLogs;

        private DiscardFault(Setup setup) {
            this(setup, true);
        }

        private DiscardFault(Setup setup, boolean armed) {
            this.setup = setup;
            this.armed = armed;
        }

        @Subscribe
        public void onMove(GameEventCardChangeZone event) {
            if (armed && !fired && event.from() != null && event.from().zoneType() == ZoneType.Hand
                    && (event.card().getId() == setup.first.getId() || event.card().getId() == setup.second.getId())) {
                fired = true;
                setup.game.getAction().moveTo(setup.actor.getZone(ZoneType.Exile), setup.source, null);
            }
        }

        @Subscribe
        public void onLog(GameEventAddLog event) {
            if (event.type() == GameLogEntryType.DISCARD && event.sourceCard() != null
                    && (event.sourceCard().getId() == setup.first.getId() || event.sourceCard().getId() == setup.second.getId())) {
                discardLogs++;
            }
        }
    }
}
