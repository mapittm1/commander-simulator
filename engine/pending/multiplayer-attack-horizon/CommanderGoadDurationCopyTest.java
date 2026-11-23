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
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Separate missed native declarations from the rules outcomes of an explicitly selected attack. */
public class CommanderGoadDurationCopyTest extends AITest {
    private Game board(String kind, boolean copied) {
        List<RegisteredPlayer> seats = new ArrayList<>();
        for (int seat = 0; seat < 4; seat++) {
            seats.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("opportunity-" + seat, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(seats, rules, new Match(rules, seats, "Multiplayer attack opportunity"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        Player actor = game.getPlayers().get(0);
        actor.setLife(80, null);
        for (Player player : game.getPlayers()) {
            player.setLife(player == actor ? 80 : 40, null);
            for (int index = 0; index < 12; index++) addCardToZone("Forest", player, ZoneType.Library);
            for (int index = 0; index < 5; index++) addCardToZone("Forest", player, ZoneType.Battlefield);
        }
        String name = kind.equals("forced") ? "Juggernaut" : kind.equals("group") ? "Mogg Flunkies" : kind.equals("bears") ? "Grizzly Bears" : kind.equals("flying") ? "Serra Angel"
                : kind.equals("defender") ? "Wall of Wood" : "Goreclaw, Terror of Qal Sisma";
        Card creature = addCardToZone(name, actor, ZoneType.Battlefield);
        creature.setSickness(kind.equals("sick"));
        if (kind.equals("group")) addCardToZone("Mogg Flunkies", actor, ZoneType.Battlefield).setSickness(false);
        if (kind.equals("guard")) actor.setLife(7, null);
        if (kind.equals("tax")) {
            addCardToZone("Propaganda", game.getPlayers().get(1), ZoneType.Battlefield);
            for (Card land : actor.getCardsIn(ZoneType.Battlefield)) if (land.isLand()) land.setTapped(true);
        }
        if (kind.equals("peace")) addCardToZone("Peacekeeper", game.getPlayers().get(1), ZoneType.Battlefield);
        if (kind.equals("allblocked")) addCardToZone("Colossal Dreadmaw", game.getPlayers().get(1), ZoneType.Battlefield);
        creature.setTapped(kind.equals("tapped"));
        if (!kind.equals("open")) {
            for (int seat = 2; seat < 4; seat++) {
                addCardToZone(kind.equals("guard") ? "Craw Wurm" : "Colossal Dreadmaw", game.getPlayers().get(seat), ZoneType.Battlefield);
            }
        }
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, actor, 5);
        game.copyLastState();
        if (copied) game = new GameCopier(game).withStack(true).makeCopy(null, actor);
        return game;
    }

    private Player seat(Game game, int index) {
        return game.getPlayers().stream().filter(player -> player.getName().equals("opportunity-" + index)).findFirst().orElseThrow();
    }



    private void paidGoadExpiresIndependently(int depth, boolean copyFirst) {
        Game original = board("goreclaw", false);
        Player actor = seat(original, 0);
        Player goader = seat(original, 1);
        Card creature = actor.getCreaturesInPlay().get(0);
        for (int index = 0; index < 4; index++) addCardToZone("Mountain", goader, ZoneType.Battlefield);
        addCardToZone("Peacekeeper", goader, ZoneType.Battlefield);
        Card spell = addCardToZone("Disrupt Decorum", goader, ZoneType.Hand);
        original.getPhaseHandler().devModeSet(PhaseType.MAIN1, goader, 5);
        forge.game.spellability.SpellAbility ability = spell.getFirstSpellAbility();
        ability.setActivatingPlayer(goader);
        assertTrue(ComputerUtil.playStack(ability, goader, original));
        assertEquals(original.getStack().peekAbility().getPayingMana().size(), 4, "Pay the native sorcery cost");
        original.getStack().resolveStack();
        settle(original, goader);
        assertTrue(creature.isGoadedBy(goader));
        original.copyLastState();
        Game copy = original;
        for (int index = 0; index < depth; index++) copy = new GameCopier(copy).withStack(true).makeCopy(null, seat(copy, 0));
        Card copied = seat(copy, 0).getCreaturesInPlay().get(0);
        assertTrue(copied.isGoadedBy(seat(copy, 1)));
        if (copyFirst) {
            advanceToGoader(copy);
            assertFalse(copied.isGoaded(), "Copied duration expires at its goader's next turn");
            assertTrue(creature.isGoaded(), "Advancing the copied world cannot expire the original");
            advanceToGoader(original);
        } else {
            advanceToGoader(original);
            assertFalse(creature.isGoaded(), "Original duration expires at its goader's next turn");
            assertTrue(copied.isGoaded(), "Advancing the original world cannot expire its copy");
            advanceToGoader(copy);
        }
        assertFalse(creature.isGoaded());
        assertFalse(copied.isGoaded());
    }

    private void advanceToGoader(Game game) {
        Player goader = seat(game, 1);
        for (int turn = 0; turn < 6; turn++) {
            assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game, goader)));
            settle(game, goader);
            if (game.getPhaseHandler().getPlayerTurn() == goader) return;
        }
        fail("Bounded native horizon did not reach the goader's next turn");
    }

    private void settle(Game game, Player player) {
        GameSimulator.resolveStack(game, player);
    }
    // Named entry points keep every fixed case unique in native evidence.
    @Test public void oneCopyOriginalExpiresFirst() { paidGoadExpiresIndependently(1, false); }
    @Test public void oneCopyCopyExpiresFirst() { paidGoadExpiresIndependently(1, true); }
    @Test public void twoCopiesOriginalExpiresFirst() { paidGoadExpiresIndependently(2, false); }
    @Test public void twoCopiesCopyExpiresFirst() { paidGoadExpiresIndependently(2, true); }

}
