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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/** Native activation payment with a deterministic fault between two costs. */
public class NestedManaRollbackProbeTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Nested mana rollback"));
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

    @Test
    public void failedDrumPaymentRestoresItsTapAndReleasesItsReservation() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card grove = addCardToZone("Grove of the Guardian", actor, ZoneType.Battlefield);
        Card drum = addCardToZone("Springleaf Drum", actor, ZoneType.Battlefield);
        Card elf = addCardToZone("Llanowar Elves", actor, ZoneType.Battlefield);
        Card first = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        Card second = addCardToZone("Runeclaw Bear", actor, ZoneType.Battlefield);
        for (String name : List.of("Forest", "Forest", "Wastes", "Wastes")) {
            addCardToZone(name, actor, ZoneType.Battlefield);
        }
        game.getAction().checkStateEffects(true);
        SpellAbility ability = tapAbility(grove, actor);
        HelperFault fault = new HelperFault(drum, elf);
        game.subscribeToEvents(fault);

        assertTrue(ComputerUtilCost.canPayCost(ability, actor, false));
        assertFalse(ComputerUtil.playStack(ability, actor, game));
        assertTrue(fault.fired);
        assertTrue(game.getZoneOf(elf).is(ZoneType.Exile));
        assertFalse(grove.isTapped());
        assertFalse(drum.isTapped());
        assertFalse(first.isTapped());
        assertFalse(second.isTapped());
        assertFalse(AiCardMemory.isRememberedCard(actor, drum, AiCardMemory.MemorySet.PAYS_TAP_COST));
        assertTrue(actor.getManaPool().isEmpty());
        assertTrue(game.getStack().isEmpty());
    }

    public static final class HelperFault {
        private final Card drum;
        private final Card helper;
        private boolean fired;

        private HelperFault(Card drum, Card helper) {
            this.drum = drum;
            this.helper = helper;
        }

        @Subscribe
        public void afterDrumTaps(GameEventCardTapped event) {
            if (!fired && event.tapped() && event.card().getId() == drum.getId()) {
                fired = true;
                drum.getGame().getAction().moveTo(helper.getOwner().getZone(ZoneType.Exile), helper, null);
            }
        }
    }
}
