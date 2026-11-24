package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

/** Paid printed spells and actual turn advancement; no phase jumps across expiry. */
public class CommanderSkipPhaseDurationTest extends AITest {
    private Game prepared(String spellName) {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            players.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("skip-duration-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Native skipped-phase duration"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
            for (int index = 0; index < 5; index++) addCardToZone("Plains", player, ZoneType.Battlefield);
        }
        Player caster = seat(game, 0);
        addCardToZone("Grizzly Bears", caster, ZoneType.Battlefield).setSickness(false);
        Card spell = addCardToZone(spellName, caster, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(spellName.equals("Moment of Silence") ? PhaseType.MAIN2 : PhaseType.MAIN1, caster, 5);
        SpellAbility ability = spell.getFirstSpellAbility();
        ability.setActivatingPlayer(caster);
        Player target = spellName.equals("Moment of Silence") ? caster : seat(game, 1);
        assertTrue(ability.canTarget(target));
        ability.getTargets().add(target);
        assertTrue(ComputerUtil.playStack(ability, caster, game), "Use the normal paid casting API");
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 1);
        game.getStack().resolveStack();
        settle(game);
        assertEquals(caster.getLandsInPlay().stream().filter(Card::isTapped).count(), 1L);
        game.copyLastState();
        return game;
    }

    private Player seat(Game game, int number) {
        return game.getPlayers().stream().filter(player -> player.getName().equals("skip-duration-" + number)).findFirst().orElseThrow();
    }

    private void settle(Game game) {
        GameSimulator.resolveStack(game, game.getPlayers().get(0));
    }

    private void nextMain(Game game, int number) {
        for (int turn = 0; turn < 6; turn++) {
            assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
            settle(game);
            if (game.getPhaseHandler().getPlayerTurn() == seat(game, number)) return;
        }
        fail("Native turn horizon did not reach the required player's next main phase");
    }

    private void combat(Game game, boolean skipped) {
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.COMBAT_DECLARE_ATTACKERS, () -> settle(game)));
        settle(game);
        assertEquals(game.getPhaseHandler().getPhase(), skipped ? PhaseType.MAIN2 : PhaseType.COMBAT_DECLARE_ATTACKERS,
                "Printed skip duration must govern the actual combat phase");
    }

    private void timeline(Game game, String name) {
        if (name.equals("Moment of Silence")) {
            // The spell was cast after this turn's combat; its effect must expire
            // at cleanup even though there was no remaining combat to consume it.
            nextMain(game, 0);
            combat(game, false);
        } else {
            nextMain(game, 1);
            combat(game, true);
            // The same player may have combat again on the following turn.
            nextMain(game, 1);
            combat(game, false);
        }
    }

    private void originalControl(String name) {
        timeline(prepared(name), name);
    }

    private void independentCopies(String name, int depth, boolean copyFirst) {
        Game original = prepared(name);
        Game copy = original;
        for (int index = 0; index < depth; index++) {
            copy = new GameCopier(copy).withStack(true).makeCopy(null, seat(copy, 0));
        }
        Game first = copyFirst ? copy : original;
        Game other = copyFirst ? original : copy;
        PhaseType originalPhase = other.getPhaseHandler().getPhase();
        int originalTurn = other.getPhaseHandler().getTurn();
        timeline(first, name);
        assertEquals(other.getPhaseHandler().getPhase(), originalPhase, "Advancing one world cannot move the other");
        assertEquals(other.getPhaseHandler().getTurn(), originalTurn);
        timeline(other, name);
    }
    @Test public void momentOriginalControl() { originalControl("Moment of Silence"); }
    @Test public void momentOneCopyOriginalFirst() { independentCopies("Moment of Silence", 1, false); }
    @Test public void momentOneCopyCopyFirst() { independentCopies("Moment of Silence", 1, true); }
    @Test public void momentTwoCopiesOriginalFirst() { independentCopies("Moment of Silence", 2, false); }
    @Test public void momentTwoCopiesCopyFirst() { independentCopies("Moment of Silence", 2, true); }
    @Test public void falsePeaceOriginalControl() { originalControl("False Peace"); }
    @Test public void falsePeaceOneCopyOriginalFirst() { independentCopies("False Peace", 1, false); }
    @Test public void falsePeaceOneCopyCopyFirst() { independentCopies("False Peace", 1, true); }
    @Test public void falsePeaceTwoCopiesOriginalFirst() { independentCopies("False Peace", 2, false); }
    @Test public void falsePeaceTwoCopiesCopyFirst() { independentCopies("False Peace", 2, true); }
    @Test public void emptyCityOriginalControl() { originalControl("Empty City Ruse"); }
    @Test public void emptyCityOneCopyOriginalFirst() { independentCopies("Empty City Ruse", 1, false); }
    @Test public void emptyCityOneCopyCopyFirst() { independentCopies("Empty City Ruse", 1, true); }
    @Test public void emptyCityTwoCopiesOriginalFirst() { independentCopies("Empty City Ruse", 2, false); }
    @Test public void emptyCityTwoCopiesCopyFirst() { independentCopies("Empty City Ruse", 2, true); }
}
