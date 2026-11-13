package forge.ai;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class CommanderUlalekTest extends AITest {
    @Test
    public void paidTriggerCopiesSpellsBeforeOtherAbilities() {
        verifyCopies(4, true);
    }

    @Test
    public void triggerWithoutTwoColorlessDoesNotCopy() {
        verifyCopies(3, false);
    }

    private void verifyCopies(int wastes, boolean affordable) {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        addCardToZone("Ulalek, Fused Atrocity", actor, ZoneType.Battlefield);
        addCardToZone("Island", actor, ZoneType.Battlefield);
        for (int index = 0; index < wastes; index++) {
            addCardToZone("Wastes", actor, ZoneType.Battlefield);
        }
        Card ballista = addCardToZone("Walking Ballista", actor, ZoneType.Battlefield);
        ballista.setCounters(CounterEnumType.P1P1, 2);
        addCardToZone("Forest", actor, ZoneType.Library);
        addCardToZone("Island", actor, ZoneType.Library);
        game.getAction().checkStaticAbilities();
        game.getTriggerHandler().resetActiveTriggers();

        cast(game, actor, "Opt");
        SpellAbility ping = ballista.getSpellAbilities().stream().filter(ability -> ability.usesTargeting())
                .findFirst().orElseThrow();
        ping.setActivatingPlayer(actor);
        ping.getTargets().add(opponent);
        assertTrue(ComputerUtil.playStack(ping, actor, game));
        cast(game, actor, "Eldrazi Mimic");
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertEquals(game.getStack().size(), 4);
        SpellAbility trigger = game.getStack().peekAbility();
        assertEquals(trigger.getHostCard().getName(), "Ulalek, Fused Atrocity");
        long tappedBefore = actor.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isTapped).count();

        game.getStack().resolveStack();

        long tappedAfter = actor.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isTapped).count();
        assertEquals(tappedAfter - tappedBefore, affordable ? 2L : 0L);

        List<String> copies = new ArrayList<>();
        for (var entry : game.getStack()) {
            SpellAbility ability = entry.getSpellAbility();
            if (ability.isCopied()) {
                copies.add(ability.getHostCard().getName());
            }
        }
        if (affordable) {
            assertEquals(copies.size(), 3, copies.toString());
            assertEquals(copies.get(0), "Walking Ballista");
            assertTrue(copies.containsAll(List.of("Opt", "Eldrazi Mimic")));
        } else {
            assertTrue(copies.isEmpty());
        }
        assertEquals(game.getStack().size(), affordable ? 6 : 3);
        assertEquals(ballista.getCounters(CounterEnumType.P1P1), 1);
    }

    private void cast(Game game, Player player, String name) {
        Card card = addCardToZone(name, player, ZoneType.Hand);
        SpellAbility ability = card.getFirstSpellAbility();
        ability.setActivatingPlayer(player);
        assertTrue(ComputerUtil.playStack(ability, player, game), name);
    }

    private Game newGame() {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            seats.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("seat" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Ulalek"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, game.getPlayers().get(0), 2);
        return game;
    }
}
