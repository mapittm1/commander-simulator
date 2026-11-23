package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Separate missed native declarations from the rules outcomes of an explicitly selected attack. */
public class CommanderGoadCopyBoundaryTest extends AITest {
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


    private void copiedGoadRequirementsAreIndependent(int depth, int sources, boolean mutateCopy) {
        Game original = board("goreclaw", false);
        Player actor = seat(original, 0);
        Card creature = actor.getCreaturesInPlay().get(0);
        List<Long> timestamps = new ArrayList<>();
        for (int source = 1; source <= sources; source++) {
            long timestamp = original.getNextTimestamp();
            timestamps.add(timestamp);
            creature.addGoad(timestamp, seat(original, source));
        }
        original.copyLastState();
        Game copy = original;
        for (int index = 0; index < depth; index++) copy = new GameCopier(copy).withStack(true).makeCopy(null, seat(copy, 0));
        Card copied = seat(copy, 0).getCreaturesInPlay().get(0);
        assertEquals(copied.getGoadMap().keySet(), creature.getGoadMap().keySet(), "Preserve every timestamp");
        for (int source = 1; source <= sources; source++) {
            assertTrue(copied.isGoadedBy(seat(copy, source)));
            Player mapped = copied.getGoadMap().get(timestamps.get(source - 1));
            assertSame(mapped, seat(copy, source), "Use the exact player in the copied world");
            assertNotSame(mapped, seat(original, source), "No player object from the original world");
        }
        Combat legal = new Combat(seat(copy, 0));
        copy.getPhaseHandler().setCombat(legal);
        legal.addAttacker(copied, seat(copy, 3));
        assertTrue(CombatUtil.validateAttackers(legal));
        Combat forbidden = new Combat(seat(copy, 0));
        copy.getPhaseHandler().setCombat(forbidden);
        forbidden.addAttacker(copied, seat(copy, 1));
        assertFalse(CombatUtil.validateAttackers(forbidden), "Another legal opponent makes the goading source an invalid choice");
        if (mutateCopy) {
            copied.removeGoad(timestamps.get(0));
            assertTrue(creature.isGoadedBy(seat(original, 1)), "Removing a copied goad cannot mutate the source");
            assertFalse(copied.isGoadedBy(seat(copy, 1)));
        } else {
            creature.removeGoad(timestamps.get(0));
            assertTrue(copied.isGoadedBy(seat(copy, 1)), "Removing a source goad cannot mutate the copy");
            assertFalse(creature.isGoadedBy(seat(original, 1)));
        }
    }
    // Named entry points keep every fixed case unique in native evidence.
    @Test public void oneCopyOneSourceRemovingOriginal() { copiedGoadRequirementsAreIndependent(1, 1, false); }
    @Test public void oneCopyOneSourceRemovingCopy() { copiedGoadRequirementsAreIndependent(1, 1, true); }
    @Test public void oneCopyTwoSourcesRemovingOriginal() { copiedGoadRequirementsAreIndependent(1, 2, false); }
    @Test public void oneCopyTwoSourcesRemovingCopy() { copiedGoadRequirementsAreIndependent(1, 2, true); }
    @Test public void twoCopiesOneSourceRemovingOriginal() { copiedGoadRequirementsAreIndependent(2, 1, false); }
    @Test public void twoCopiesOneSourceRemovingCopy() { copiedGoadRequirementsAreIndependent(2, 1, true); }
    @Test public void twoCopiesTwoSourcesRemovingOriginal() { copiedGoadRequirementsAreIndependent(2, 2, false); }
    @Test public void twoCopiesTwoSourcesRemovingCopy() { copiedGoadRequirementsAreIndependent(2, 2, true); }

}
