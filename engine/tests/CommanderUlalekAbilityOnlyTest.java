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

public class CommanderUlalekAbilityOnlyTest extends AITest {
    @Test
    public void ulalekCanCopyAnAbilityAfterTheEldraziSpellIsCountered() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        addCardToZone("Ulalek, Fused Atrocity", actor, ZoneType.Battlefield);
        Card ballista = addCardToZone("Walking Ballista", actor, ZoneType.Battlefield);
        ballista.setCounters(CounterEnumType.P1P1, 2);
        for (int i = 0; i < 4; i++) {
            addCardToZone("Wastes", actor, ZoneType.Battlefield);
        }
        addCardToZone("Island", opponent, ZoneType.Battlefield);
        addCardToZone("Island", opponent, ZoneType.Battlefield);
        Card counterspell = addCardToZone("Counterspell", opponent, ZoneType.Hand);
        game.getAction().checkStaticAbilities();
        game.getTriggerHandler().resetActiveTriggers();

        SpellAbility ping = ballista.getSpellAbilities().stream().filter(SpellAbility::usesTargeting)
                .findFirst().orElseThrow();
        ping.setActivatingPlayer(actor);
        ping.getTargets().add(opponent);
        assertTrue(ComputerUtil.playStack(ping, actor, game));
        cast(game, actor, "Eldrazi Mimic");
        SpellAbility eldrazi = game.getStack().peekAbility();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertEquals(game.getStack().size(), 3);
        assertEquals(game.getStack().peekAbility().getHostCard().getName(), "Ulalek, Fused Atrocity");

        SpellAbility counter = counterspell.getFirstSpellAbility();
        counter.setActivatingPlayer(opponent);
        counter.getTargets().add(eldrazi);
        assertTrue(ComputerUtil.playStack(counter, opponent, game));
        game.getStack().resolveStack();
        assertEquals(game.getStack().size(), 2);

        game.getStack().resolveStack();

        assertEquals(game.getStack().size(), 2, "Ulalek copies the remaining ability");
        assertTrue(game.getStack().peekAbility().isCopied());
        assertEquals(game.getStack().peekAbility().getHostCard().getName(), "Walking Ballista");
        assertEquals(ballista.getCounters(CounterEnumType.P1P1), 1);
    }

    @Test
    public void ulalekDoesNotPayWhenTheOnlySpellCannotBeCopied() {
        Game game = newGame();
        Player actor = game.getPlayers().get(0);
        Player opponent = game.getPlayers().get(1);
        addCardToZone("Ulalek, Fused Atrocity", actor, ZoneType.Battlefield);
        for (int i = 0; i < 3; i++) {
            addCardToZone("Mountain", actor, ZoneType.Battlefield);
        }
        for (int i = 0; i < 4; i++) {
            addCardToZone("Wastes", actor, ZoneType.Battlefield);
        }
        addCardToZone("Island", opponent, ZoneType.Battlefield);
        addCardToZone("Island", opponent, ZoneType.Battlefield);
        Card counterspell = addCardToZone("Counterspell", opponent, ZoneType.Hand);
        game.getAction().checkStaticAbilities();
        game.getTriggerHandler().resetActiveTriggers();

        cast(game, actor, "Display of Power");
        assertTrue(game.getStack().peekAbility().cantBeCopied());
        cast(game, actor, "Eldrazi Mimic");
        SpellAbility eldrazi = game.getStack().peekAbility();
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();

        SpellAbility counter = counterspell.getFirstSpellAbility();
        counter.setActivatingPlayer(opponent);
        counter.getTargets().add(eldrazi);
        assertTrue(ComputerUtil.playStack(counter, opponent, game));
        game.getStack().resolveStack();
        assertEquals(game.getStack().size(), 2);
        long tappedBefore = actor.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isTapped).count();

        game.getStack().resolveStack();

        long tappedAfter = actor.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isTapped).count();
        assertEquals(game.getStack().size(), 1);
        assertEquals(tappedAfter, tappedBefore, "There is nothing legal to copy");
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
