package forge.ai;

import com.google.common.eventbus.Subscribe;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.event.GameEventCardTapped;
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
public class FailedTapCostProbeTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Tap cost recovery"));
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


    private record Setup(Game game, Player actor, Card wight, Card victim, Card aura, Card plan, SpellAbility ability) {
        Set<Integer> stackSources() {
            game.getTriggerHandler().collectTriggerForWaiting();
            game.getTriggerHandler().runWaitingTriggers();
            game.getStack().addAllTriggeredAbilitiesToStack();
            return StreamSupport.stream(game.getStack().spliterator(), false)
                    .map(instance -> instance.getSpellAbility().getHostCard().getId())
                    .collect(Collectors.toSet());
        }
    }

    private Setup setup() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card wight = addCardToZone("Wight of the Reliquary", actor, ZoneType.Battlefield);
        Card victim = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        Card aura = addCardToZone("Lust for War", actor, ZoneType.Battlefield);
        aura.attachToEntity(wight, null);
        Card plan = addCardToZone("Rewrite History", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        wight.setSickness(false);
        SpellAbility ability = wight.getSpellAbilities().stream().filter(SpellAbility::isActivatedAbility)
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(actor);
        return new Setup(game, actor, wight, victim, aura, plan, ability);
    }

    @Test
    public void failedActivationRefundsDirectTapCost() {
        Setup setup = setup();
        TapFault fault = new TapFault(setup.wight, setup.victim, null);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertTrue(setup.game.getZoneOf(setup.victim).is(ZoneType.Exile));
        assertFalse(setup.wight.isTapped());
        assertTrue(setup.stackSources().isEmpty());
    }

    @Test
    public void successfulActivationKeepsTapAndBothTriggerFamilies() {
        Setup setup = setup();

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(setup.wight.isTapped());
        assertTrue(setup.game.getZoneOf(setup.victim).is(ZoneType.Graveyard));
        assertEquals(setup.stackSources(), Set.of(setup.wight.getId(), setup.aura.getId(), setup.plan.getId()));
        assertEquals(setup.game.getStack().size(), 3);
    }

    @Test
    public void tapRefundKeepsUnrelatedTapTrigger() {
        Setup setup = setup();
        Player other = setup.game.getPlayers().get(1);
        Card another = addCardToZone("Runeclaw Bear", other, ZoneType.Battlefield);
        Card aura = addCardToZone("Lust for War", other, ZoneType.Battlefield);
        aura.attachToEntity(another, null);
        setup.game.getAction().checkStateEffects(true);
        TapFault fault = new TapFault(setup.wight, setup.victim, another);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertTrue(another.isTapped());
        assertFalse(setup.wight.isTapped());
        assertEquals(setup.stackSources(), Set.of(aura.getId()));
        assertEquals(setup.game.getStack().size(), 1);
    }

    public static final class TapFault {
        private final Card source;
        private final Card victim;
        private final Card another;
        private boolean fired;

        private TapFault(Card source, Card victim, Card another) {
            this.source = source;
            this.victim = victim;
            this.another = another;
        }

        @Subscribe
        public void onTap(GameEventCardTapped event) {
            if (!fired && event.card().getId() == source.getId() && source.isTapped()) {
                fired = true;
                if (another != null) {
                    another.tap(false, null, another.getController());
                }
                var game = source.getGame();
                game.getAction().moveTo(victim.getOwner().getZone(ZoneType.Exile), victim, null);
            }
        }
    }
}
