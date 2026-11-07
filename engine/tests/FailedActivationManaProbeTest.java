package forge.ai;

import com.google.common.eventbus.Subscribe;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
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
public class FailedActivationManaProbeTest extends AITest {
    private Game pod() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Activation mana recovery"));
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


    @Test
    public void failedActivationUntapsReversibleManaSources() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card rites = addCardToZone("Vampiric Rites", actor, ZoneType.Battlefield);
        Card victim = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        Card swamp = addCardToZone("Swamp", actor, ZoneType.Battlefield);
        Card wastes = addCardToZone("Wastes", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        SpellAbility ability = rites.getSpellAbilities().stream().filter(SpellAbility::isActivatedAbility)
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(actor);
        LateSacrificeFault fault = new LateSacrificeFault(actor, victim);
        game.subscribeToEvents(fault);

        assertFalse(ComputerUtil.playStack(ability, actor, game));
        assertTrue(fault.fired);
        assertTrue(game.getZoneOf(victim).is(ZoneType.Exile));
        assertFalse(swamp.isTapped(), "Refund must undo the Swamp activation");
        assertFalse(wastes.isTapped(), "Refund must undo the Wastes activation");
        assertTrue(actor.getManaPool().isEmpty());
        assertTrue(ability.getPayingMana().isEmpty());
        assertTrue(game.getStack().isEmpty());
    }

    @Test
    public void failedActivationKeepsManaThatWasFloatingBeforePayment() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card rites = addCardToZone("Vampiric Rites", actor, ZoneType.Battlefield);
        Card victim = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        Card swamp = addCardToZone("Swamp", actor, ZoneType.Battlefield);
        Card wastes = addCardToZone("Wastes", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        SpellAbility floatBlack = swamp.getManaAbilities().get(0);
        floatBlack.setActivatingPlayer(actor);
        assertTrue(ComputerUtil.playNoStack(actor, floatBlack, game, false));
        assertTrue(swamp.isTapped());
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.BLACK), 1);

        SpellAbility ability = rites.getSpellAbilities().stream().filter(SpellAbility::isActivatedAbility)
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(actor);
        LateSacrificeFault fault = new LateSacrificeFault(actor, victim);
        game.subscribeToEvents(fault);
        assertFalse(ComputerUtil.playStack(ability, actor, game));

        assertTrue(fault.fired);
        assertTrue(swamp.isTapped());
        assertFalse(wastes.isTapped());
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.BLACK), 1);
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.COLORLESS), 0);
        assertTrue(ability.getPayingMana().isEmpty());
    }

    @Test
    public void failedActivationKeepsNonUndoableManaAndDamage() {
        Game game = pod();
        Player actor = game.getPlayers().get(0);
        Card rites = addCardToZone("Vampiric Rites", actor, ZoneType.Battlefield);
        Card victim = addCardToZone("Grizzly Bears", actor, ZoneType.Battlefield);
        Card swamp = addCardToZone("Swamp", actor, ZoneType.Battlefield);
        addCardToZone("Overabundance", actor, ZoneType.Battlefield);
        game.getAction().checkStateEffects(true);
        SpellAbility ability = rites.getSpellAbilities().stream().filter(SpellAbility::isActivatedAbility)
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(actor);
        LateSacrificeFault fault = new LateSacrificeFault(actor, victim);
        game.subscribeToEvents(fault);
        int life = actor.getLife();

        assertFalse(ComputerUtil.playStack(ability, actor, game));
        assertTrue(fault.fired);
        assertTrue(swamp.isTapped());
        assertEquals(actor.getLife(), life - 1);
        assertEquals(actor.getManaPool().getAmountOfColor(MagicColor.BLACK), 2);
        assertTrue(ability.getPayingMana().isEmpty());
        assertTrue(game.getZoneOf(victim).is(ZoneType.Exile));
    }

    public static final class LateSacrificeFault {
        private final Player actor;
        private final Card victim;
        private boolean fired;

        private LateSacrificeFault(Player actor, Card victim) {
            this.actor = actor;
            this.victim = victim;
        }

        @Subscribe
        public void onMana(GameEventManaPool event) {
            if (!fired && event.player().getId() == actor.getId() && event.mode() == EventValueChangeType.Added) {
                fired = true;
                actor.getGame().getAction().moveTo(actor.getZone(ZoneType.Exile), victim, null);
            }
        }
    }
}
