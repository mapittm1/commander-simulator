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
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Paid printed spells, real successive turns and independent deferred restrictions. */
public class CommanderDeferredRepeatTest extends AITest {
    private Player seat(Game game, int number) {
        return game.getPlayers().stream().filter(p -> p.getName().equals("deferred-" + number)).findFirst().orElseThrow();
    }

    private void settle(Game game) {
        GameSimulator.resolveStack(game, game.getPlayers().get(0));
    }

    private Card hand(Player player, String name) {
        return player.getCardsIn(ZoneType.Hand).stream().filter(card -> card.getName().equals(name)).findFirst().orElseThrow();
    }

    private SpellAbility action(Player player, String name) {
        SpellAbility ability = hand(player, name).getFirstSpellAbility();
        ability.setActivatingPlayer(player);
        return ability;
    }

    private boolean canCast(Player player, String name) {
        SpellAbility ability = action(player, name);
        return ability.canPlay() && ability.checkRestrictions(ability.getHostCard(), player);
    }

    private void cast(Game game, Player player, String name, int mana) {
        SpellAbility ability = action(player, name);
        assertTrue(ability.canPlay() && ability.checkRestrictions(ability.getHostCard(), player), "Native permission to cast " + name);
        assertTrue(ComputerUtil.playStack(ability, player, game), "Normal paid cast of " + name);
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        settle(game);
    }

    private Game prepared(String name) {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            players.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("deferred-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Deferred repeat restrictions"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
            for (String land : List.of("Plains", "Island", "Forest")) {
                for (int index = 0; index < 3; index++) addCardToZone(land, player, ZoneType.Battlefield);
            }
            for (String spell : List.of("Opt", "Ponder", "Grizzly Bears")) addCardToZone(spell, player, ZoneType.Hand);
        }
        Player caster = seat(game, 0);
        addCardToZone(name, caster, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, caster, 5);
        cast(game, caster, name, name.equals("Sphinx's Decree") ? 2 : 6);
        game.copyLastState();
        return game;
    }

    private void nextMain(Game game, int number) {
        for (int step = 0; step < 6; step++) {
            assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
            settle(game);
            if (game.getPhaseHandler().getPlayerTurn() == seat(game, number)) return;
        }
        fail("Native turn sequence did not reach required participant");
    }

    private void timeline(Game game) {
        for (int number = 1; number < 4; number++) {
            nextMain(game, number);
            Player player = seat(game, number);
            assertFalse(canCast(player, "Opt"), "Instant is forbidden during this player's next turn");
            assertFalse(canCast(player, "Ponder"), "Sorcery is forbidden during this player's next turn");
            cast(game, player, "Grizzly Bears", 2);
        }
        for (int number = 1; number < 4; number++) {
            nextMain(game, number);
            Player player = seat(game, number);
            assertTrue(canCast(player, "Opt"), "Restriction expires after the affected turn");
            assertTrue(canCast(player, "Ponder"), "Sorcery permission returns on the following turn");
            cast(game, player, "Opt", 1);
            cast(game, player, "Ponder", 1);
        }
    }

    private void verify(String name, int depth, boolean copyFirst) {
        Game original = prepared(name);
        if (depth == 0) {
            timeline(original);
            return;
        }
        Game copy = original;
        for (int index = 0; index < depth; index++) copy = new GameCopier(copy).withStack(true).makeCopy(null, seat(copy, 0));
        Game first = copyFirst ? copy : original;
        Game other = copyFirst ? original : copy;
        int turn = other.getPhaseHandler().getTurn();
        PhaseType phase = other.getPhaseHandler().getPhase();
        timeline(first);
        assertEquals(other.getPhaseHandler().getTurn(), turn, "Advancing one world cannot advance another");
        assertEquals(other.getPhaseHandler().getPhase(), phase);
        timeline(other);
    }

    @Test public void decreeOriginal() { verify("Sphinx's Decree", 0, false); }
    @Test public void decreeOneCopyOriginalFirst() { verify("Sphinx's Decree", 1, false); }
    @Test public void decreeOneCopyCopyFirst() { verify("Sphinx's Decree", 1, true); }
    @Test public void decreeTwoCopiesOriginalFirst() { verify("Sphinx's Decree", 2, false); }
    @Test public void decreeTwoCopiesCopyFirst() { verify("Sphinx's Decree", 2, true); }
    @Test public void azorOriginal() { verify("Azor, the Lawbringer", 0, false); }
    @Test public void azorOneCopyOriginalFirst() { verify("Azor, the Lawbringer", 1, false); }
    @Test public void azorOneCopyCopyFirst() { verify("Azor, the Lawbringer", 1, true); }
    @Test public void azorTwoCopiesOriginalFirst() { verify("Azor, the Lawbringer", 2, false); }
    @Test public void azorTwoCopiesCopyFirst() { verify("Azor, the Lawbringer", 2, true); }
}
