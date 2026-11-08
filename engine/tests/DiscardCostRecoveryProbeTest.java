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
public class DiscardCostRecoveryProbeTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Discard cost recovery"));
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

    private record Setup(Game game, Player actor, Card neonate, Card discarded, Card dying, SpellAbility ability) {
        void collectTriggers() {
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
        }
    }

    private Setup neonate() {
        return neonate("Grizzly Bears");
    }

    private Setup neonate(String discardName) {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card neonate = addCardToZone("Insolent Neonate", actor, ZoneType.Battlefield);
        Card dying = addCardToZone("Dying to Serve", actor, ZoneType.Battlefield);
        Card discarded = addCardToZone(discardName, actor, ZoneType.Hand);
        game.getAction().checkStateEffects(true);
        return new Setup(game, actor, neonate, discarded, dying, tapAbility(neonate, actor));
    }

    @Test
    public void failedSacrificeReturnsTheDiscardedCardAndHistory() {
        Setup setup = neonate();
        SacrificeFault fault = new SacrificeFault(setup.neonate, setup.discarded, null);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertTrue(setup.game.getZoneOf(setup.neonate).is(ZoneType.Exile));
        assertTrue(setup.game.getZoneOf(setup.discarded).is(ZoneType.Hand));
        assertEquals(setup.actor.getCardsIn(ZoneType.Hand).size(), 1);
        assertTrue(setup.actor.getDiscardedThisTurn().isEmpty());
        assertEquals(setup.actor.getDescended(), 0);
        assertTrue(setup.actor.getZone(ZoneType.Graveyard).getCardsAddedThisTurn(ZoneType.Hand).isEmpty());
        setup.collectTriggers();
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void successfulNeonateKeepsDiscardAndItsTrigger() {
        Setup setup = neonate();

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(setup.game.getZoneOf(setup.neonate).is(ZoneType.Graveyard));
        assertTrue(setup.game.getZoneOf(setup.discarded).is(ZoneType.Graveyard));
        assertEquals(setup.actor.getDiscardedThisTurn().size(), 1);
        assertEquals(setup.actor.getDescended(), 2);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().peekAbility().getHostCard().getId(), setup.dying.getId());
    }

    @Test
    public void discardRefundKeepsUnrelatedDiscardAndPriorPaymentRecords() {
        Setup setup = neonate();
        Player other = setup.game.getPlayers().get(1);
        Card independent = addCardToZone("Runeclaw Bear", other, ZoneType.Hand);
        Card marker = addCardToZone("Island", other, ZoneType.Hand);
        Card witness = addCardToZone("Waste Not", setup.actor, ZoneType.Battlefield);
        addCardToZone("Waste Not", other, ZoneType.Battlefield);
        setup.game.getAction().checkStateEffects(true);
        setup.ability.addCostToHashList(marker, "DiscardedCards", false);
        setup.ability.addCostToHashList(marker, "Discarded", true);
        SacrificeFault fault = new SacrificeFault(setup.neonate, setup.discarded, independent);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertTrue(setup.game.getZoneOf(setup.discarded).is(ZoneType.Hand));
        assertTrue(setup.game.getZoneOf(independent).is(ZoneType.Graveyard));
        assertEquals(other.getDiscardedThisTurn().size(), 1);
        assertEquals(other.getDescended(), 1);
        assertEquals(List.copyOf(setup.ability.getPaidList("DiscardedCards", false)), List.of(marker));
        assertTrue(setup.ability.getPaidList("DiscardedCards", true) == null);
        assertEquals(List.copyOf(setup.ability.getPaidList("Discarded", true)), List.of(marker));
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 1);
        assertEquals(setup.game.getStack().peekAbility().getHostCard().getId(), witness.getId());
    }

    @Test
    public void abortedMadnessDiscardReturnsTheCardFromExile() {
        Setup setup = neonate("Fiery Temper");
        SacrificeFault fault = new SacrificeFault(setup.neonate, setup.discarded, null, ZoneType.Exile);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertTrue(setup.game.getZoneOf(setup.neonate).is(ZoneType.Exile));
        assertTrue(setup.game.getZoneOf(setup.discarded).is(ZoneType.Hand));
        assertTrue(setup.actor.getDiscardedThisTurn().isEmpty());
        setup.collectTriggers();
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void successfulMadnessDiscardKeepsTheExileAndTrigger() {
        Setup setup = neonate("Fiery Temper");

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(setup.game.getZoneOf(setup.discarded).is(ZoneType.Exile));
        assertEquals(setup.actor.getDiscardedThisTurn().size(), 1);
        setup.collectTriggers();
        boolean found = false;
        for (var item : setup.game.getStack()) {
            if (item.getSpellAbility().getHostCard().getId() == setup.discarded.getId()) {
                found = true;
            }
        }
        assertTrue(found);
    }

    public static final class SacrificeFault {
        private final Card source;
        private final Card discarded;
        private final Card independent;
        private final ZoneType destination;
        private boolean fired;

        private SacrificeFault(Card source, Card discarded, Card independent) {
            this(source, discarded, independent, ZoneType.Graveyard);
        }

        private SacrificeFault(Card source, Card discarded, Card independent, ZoneType destination) {
            this.source = source;
            this.discarded = discarded;
            this.independent = independent;
            this.destination = destination;
        }

        @Subscribe
        public void afterDiscard(GameEventCardChangeZone event) {
            if (!fired && event.card().getId() == discarded.getId() && event.to() != null && event.to().zoneType() == destination) {
                fired = true;
                if (independent != null) {
                    independent.getOwner().discard(independent, null, true, AbilityKey.newMap());
                }
                var game = source.getGame();
                game.getAction().moveTo(source.getOwner().getZone(ZoneType.Exile), source, null);
            }
        }
    }
}
