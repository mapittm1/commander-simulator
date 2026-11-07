package forge.ai;

import com.google.common.eventbus.Subscribe;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.cost.CostPayLife;
import forge.game.event.GameEventPlayerLivesChanged;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Native activation payment with a deterministic fault between two costs. */
public class NonmanaPaymentRecoveryProbeTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Life payment recovery"));
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


    private record Setup(Game game, Player actor, Card victim, Card mastery, SpellAbility ability) {
        void collectTriggers() {
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
        }
    }

    private Setup setup() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card yawgmoth = addCardToZone("Yawgmoth, Thran Physician", actor, ZoneType.Battlefield);
        Card victim = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        Card mastery = addCardToZone("Lich's Mastery", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        SpellAbility ability = yawgmoth.getSpellAbilities().stream().filter(SpellAbility::isActivatedAbility)
                .filter(candidate -> candidate.getPayCosts().getCostPartByType(CostPayLife.class) != null)
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(actor);
        return new Setup(game, actor, victim, mastery, ability);
    }

    @Test
    public void failedSacrificeRefundsEarlierLifeAndItsHistory() {
        Setup setup = setup();
        LateSacrificeFault fault = new LateSacrificeFault(setup.actor, setup.victim, null);
        setup.game.subscribeToEvents(fault);
        int life = setup.actor.getLife();
        int lost = setup.actor.getLifeLostThisTurn();
        int paid = setup.ability.getAmountLifePaid();

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertEquals(setup.actor.getLife(), life);
        assertEquals(setup.actor.getLifeLostThisTurn(), lost);
        assertEquals(setup.ability.getAmountLifePaid(), paid);
        assertTrue(setup.game.getZoneOf(setup.victim).is(ZoneType.Exile));
        setup.collectTriggers();
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void successfulActivationKeepsLifePaymentAndItsTrigger() {
        Setup setup = setup();
        int life = setup.actor.getLife();

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertEquals(setup.actor.getLife(), life - 1);
        assertEquals(setup.actor.getLifeLostThisTurn(), 1);
        assertEquals(setup.ability.getAmountLifePaid(), 1);
        assertTrue(setup.game.getZoneOf(setup.victim).is(ZoneType.Graveyard));
        setup.collectTriggers();
        assertEquals(setup.game.getStack().peekAbility().getHostCard().getId(), setup.mastery.getId());
    }

    @Test
    public void refundKeepsOtherPlayersLifeLossAndTrigger() {
        Setup setup = setup();
        Player other = setup.game.getPlayers().get(1);
        Card otherMastery = addCardToZone("Lich's Mastery", other, ZoneType.Battlefield);
        setup.game.getAction().checkStateEffects(true);
        LateSacrificeFault fault = new LateSacrificeFault(setup.actor, setup.victim, other);
        setup.game.subscribeToEvents(fault);
        int life = other.getLife();

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertEquals(other.getLife(), life - 2);
        assertEquals(other.getLifeLostThisTurn(), 2);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 1);
        assertEquals(setup.game.getStack().peekAbility().getHostCard().getId(), otherMastery.getId());
    }

    @Test
    public void paidLifePublishesEachTriggerFamily() {
        Setup setup = setup();
        Card font = addCardToZone("Font of Agonies", setup.actor, ZoneType.Battlefield);
        Card kingpin = addCardToZone("Ob Nixilis, Captive Kingpin", setup.game.getPlayers().get(1), ZoneType.Battlefield);
        setup.game.getAction().checkStateEffects(true);

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        setup.collectTriggers();
        Set<Integer> sources = StreamSupport.stream(setup.game.getStack().spliterator(), false)
                .map(instance -> instance.getSpellAbility().getHostCard().getId())
                .collect(Collectors.toSet());
        assertEquals(sources, Set.of(setup.ability.getHostCard().getId(), setup.mastery.getId(),
                font.getId(), kingpin.getId()));
        assertEquals(setup.game.getStack().size(), 4);
    }

    @Test
    public void abortedLifePaymentDiscardsEachTriggerFamily() {
        Setup setup = setup();
        addCardToZone("Font of Agonies", setup.actor, ZoneType.Battlefield);
        addCardToZone("Ob Nixilis, Captive Kingpin", setup.game.getPlayers().get(1), ZoneType.Battlefield);
        setup.game.getAction().checkStateEffects(true);
        LateSacrificeFault fault = new LateSacrificeFault(setup.actor, setup.victim, null);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        setup.collectTriggers();
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void failedSacrificeRefundsActualLifeLossAfterReplacement() {
        Setup setup = setup();
        Player other = setup.game.getPlayers().get(1);
        addCardToZone("Bloodletter of Aclazotz", other, ZoneType.Battlefield);
        setup.game.getPhaseHandler().devModeSet(PhaseType.MAIN2, other, 2);
        setup.game.getAction().checkStateEffects(true);
        LateSacrificeFault fault = new LateSacrificeFault(setup.actor, setup.victim, null);
        setup.game.subscribeToEvents(fault);
        int life = setup.actor.getLife();

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertEquals(fault.provisionalLoss, 2);
        assertEquals(setup.actor.getLife(), life);
        assertEquals(setup.actor.getLifeLostThisTurn(), 0);
    }

    private Setup setupZopandrel() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card zopandrel = addCardToZone("Zopandrel, Hunger Dominus", actor, ZoneType.Battlefield);
        Card victim = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        addCardToZone("Runeclaw Bear", actor, ZoneType.Battlefield);
        Card mastery = addCardToZone("Lich's Mastery", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        SpellAbility ability = zopandrel.getSpellAbilities().stream().filter(SpellAbility::isActivatedAbility)
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(actor);
        return new Setup(game, actor, victim, mastery, ability);
    }

    @Test
    public void abortedPhyrexianPaymentRestoresBothLifePayments() {
        Setup setup = setupZopandrel();
        LateSacrificeFault fault = new LateSacrificeFault(setup.actor, setup.victim, null);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertEquals(fault.provisionalLoss, 2);
        assertEquals(setup.actor.getLife(), 40);
        assertEquals(setup.actor.getLifeLostThisTurn(), 0);
        assertEquals(setup.ability.getSpendPhyrexianMana(), 0);
        assertEquals(setup.ability.getAmountLifePaid(), 0);
        setup.collectTriggers();
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void successfulPhyrexianPaymentRetainsBothPaymentsAndTriggers() {
        Setup setup = setupZopandrel();

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertEquals(setup.actor.getLife(), 36);
        assertEquals(setup.actor.getLifeLostThisTurn(), 4);
        assertEquals(setup.ability.getSpendPhyrexianMana(), 4);
        setup.collectTriggers();
        assertEquals(setup.game.getStack().size(), 3);
    }

    public static final class LateSacrificeFault {
        private final Player actor;
        private final Card victim;
        private final Player other;
        private boolean fired;
        private int provisionalLoss;

        private LateSacrificeFault(Player actor, Card victim, Player other) {
            this.actor = actor;
            this.victim = victim;
            this.other = other;
        }

        @Subscribe
        public void afterLifeChanges(GameEventPlayerLivesChanged event) {
            if (!fired && event.player().getId() == actor.getId()) {
                fired = true;
                provisionalLoss = event.oldLives() - event.newLives();
                if (other != null) {
                    other.loseLife(2, false, false, null);
                }
                actor.getGame().getAction().moveTo(victim.getOwner().getZone(ZoneType.Exile), victim, null);
            }
        }
    }
}
