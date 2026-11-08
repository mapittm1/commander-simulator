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
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Native activation payment with a deterministic fault between two costs. */
public class FailedGroupTapCostProbeTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Group tap cost recovery"));
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


    private record Setup(Game game, Player actor, Card grove, Card first, Card second, SpellAbility ability) {
    }

    private Setup setup() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card grove = addCardToZone("Grove of the Guardian", actor, ZoneType.Battlefield);
        Card first = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        Card second = addCardToZone("Runeclaw Bear", actor, ZoneType.Battlefield);
        for (String name : List.of("Forest", "Forest", "Plains", "Plains", "Wastes")) {
            addCardToZone(name, actor, ZoneType.Battlefield);
        }
        game.getAction().checkStateEffects(true);
        SpellAbility ability = grove.getSpellAbilities().stream()
                .filter(candidate -> candidate.isActivatedAbility() && !candidate.isManaAbility())
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(actor);
        return new Setup(game, actor, grove, first, second, ability);
    }

    @Test
    public void groveCannotTapACreatureThatLeftDuringPayment() {
        Setup setup = setup();
        GroupTapFault fault = new GroupTapFault(setup.first, setup.second, null);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.tapped != null);
        assertTrue(setup.game.getZoneOf(fault.removed).is(ZoneType.Exile));
        assertFalse(fault.tapped.isTapped());
        assertTrue(setup.game.getZoneOf(setup.grove).is(ZoneType.Battlefield));
        assertFalse(setup.grove.isTapped());
        assertTrue(setup.actor.getManaPool().isEmpty());
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void groveCanTapTwoCreaturesAndSacrificeItself() {
        Setup setup = setup();

        assertTrue(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(setup.first.isTapped());
        assertTrue(setup.second.isTapped());
        assertTrue(setup.game.getZoneOf(setup.grove).is(ZoneType.Graveyard));
        assertFalse(setup.game.getStack().isEmpty());
    }

    @Test
    public void groveCannotTapACreatureControlledByAnotherPlayer() {
        Setup setup = setup();
        Player opponent = setup.game.getPlayers().get(1);
        GroupTapFault fault = new GroupTapFault(setup.first, setup.second, opponent);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.tapped != null);
        assertEquals(fault.removed.getController(), opponent);
        assertFalse(fault.tapped.isTapped());
        assertTrue(setup.game.getZoneOf(setup.grove).is(ZoneType.Battlefield));
        assertFalse(setup.grove.isTapped());
        assertTrue(setup.game.getStack().isEmpty());
    }

    @Test
    public void failedGroveRestoresPriorTapReceiptsAfterBothCreaturesPaid() {
        Setup setup = setup();
        Card marker = addCardToZone("Forest", setup.actor, ZoneType.Hand);
        setup.ability.addCostToHashList(marker, "TappedCards", true);
        setup.ability.addCostToHashList(marker, "Tapped", false);
        FinalTapFault fault = new FinalTapFault(setup.grove, setup.first, setup.second);
        setup.game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(setup.ability, setup.actor, setup.game));
        assertTrue(fault.fired);
        assertFalse(setup.first.isTapped());
        assertFalse(setup.second.isTapped());
        assertTrue(setup.game.getZoneOf(setup.grove).is(ZoneType.Exile));
        assertEquals(List.copyOf(setup.ability.getPaidList("TappedCards", true)), List.of(marker));
        assertEquals(List.copyOf(setup.ability.getPaidList("Tapped", false)), List.of(marker));
        assertTrue(setup.ability.getPaidList("Tapped", true) == null);
        assertTrue(setup.actor.getManaPool().isEmpty());
    }

    public static final class FinalTapFault {
        private final Card grove;
        private final Card first;
        private final Card second;
        private boolean fired;

        private FinalTapFault(Card grove, Card first, Card second) {
            this.grove = grove;
            this.first = first;
            this.second = second;
        }

        @Subscribe
        public void onTap(GameEventCardTapped event) {
            if (!fired && event.tapped() && first.isTapped() && second.isTapped()) {
                fired = true;
                grove.getGame().getAction().moveTo(grove.getOwner().getZone(ZoneType.Exile), grove, null);
            }
        }
    }

    public static final class GroupTapFault {
        private final Card first;
        private final Card second;
        private final Player newController;
        private Card tapped;
        private Card removed;

        private GroupTapFault(Card first, Card second, Player newController) {
            this.first = first;
            this.second = second;
            this.newController = newController;
        }

        @Subscribe
        public void onTap(GameEventCardTapped event) {
            if (tapped != null || !event.tapped()) {
                return;
            }
            if (event.card().getId() == first.getId() || event.card().getId() == second.getId()) {
                tapped = event.card().getId() == first.getId() ? first : second;
                removed = tapped == first ? second : first;
                var game = tapped.getGame();
                if (newController == null) {
                    game.getAction().moveTo(removed.getOwner().getZone(ZoneType.Exile), removed, null);
                } else {
                    removed.setController(newController, game.getNextTimestamp());
                }
            }
        }
    }
}
