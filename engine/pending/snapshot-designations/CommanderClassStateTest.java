package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.ability.ApiType;
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

/** Actual paid Class progression must survive simulation snapshots. */
public class CommanderClassStateTest extends AITest {
    private Player actor(Game game) {
        return game.getPlayers().stream()
                .filter(player -> player.getName().equals("class-0"))
                .findFirst().orElseThrow();
    }

    private Card enchantment(Game game) {
        return actor(game).getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Wizard Class"))
                .findFirst().orElseThrow();
    }

    private SpellAbility levelAbility(Game game, int level) {
        return enchantment(game).getSpellAbilities().stream()
                .filter(ability -> ability.getApi() == ApiType.ClassLevelUp)
                .filter(ability -> ("EQ" + (level - 1)).equals(ability.getParam("ClassLevel")))
                .findFirst().orElseThrow();
    }

    private boolean permitted(SpellAbility ability, Player player) {
        ability.setActivatingPlayer(player);
        return ability.canPlay() && ability.checkRestrictions(ability.getHostCard(), player);
    }

    private void check(Game game, int level) {
        assertEquals(enchantment(game).getClassLevel(), level);
        assertFalse(permitted(levelAbility(game, 2), actor(game)),
                "A Class cannot gain level2 again after resolving that upgrade");
        assertEquals(permitted(levelAbility(game, 3), actor(game)), level == 2,
                "The next upgrade is permitted at level2 and forbidden at level3");
    }

    private Game paid(int level) {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            players.add(RegisteredPlayer.forCommander(new Deck())
                    .setPlayer(new LobbyPlayerAi("class-" + index, null)));
        }
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Paid Class state"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) {
                addCardToZone("Forest", player, ZoneType.Library);
            }
            for (int index = 0; index < 15; index++) {
                addCardToZone("Island", player, ZoneType.Battlefield);
            }
        }
        Player player = actor(game);
        Card card = addCardToZone("Wizard Class", player, ZoneType.Hand);
        addCardToZone("Grizzly Bears", player, ZoneType.Battlefield);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, player, 5);
        SpellAbility cast = card.getFirstSpellAbility();
        assertTrue(permitted(cast, player));
        assertTrue(ComputerUtil.playStack(cast, player, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 1);
        GameSimulator.resolveStack(game, player);
        assertEquals(enchantment(game).getClassLevel(), 1);
        int hand = player.getCardsIn(ZoneType.Hand).size();
        for (int next = 2; next <= level; next++) {
            SpellAbility upgrade = levelAbility(game, next);
            assertTrue(permitted(upgrade, player));
            assertTrue(ComputerUtilCost.canPayCost(upgrade, player, false));
            assertTrue(ComputerUtil.playStack(upgrade, player, game));
            assertEquals(game.getStack().peekAbility().getPayingMana().size(), next == 2 ? 3 : 5);
            GameSimulator.resolveStack(game, player);
            assertEquals(enchantment(game).getClassLevel(), next);
        }
        assertEquals(player.getCardsIn(ZoneType.Hand).size(), hand + 2,
                "Printed level2 trigger actually draws two cards exactly once");
        assertEquals(player.getLandsInPlay().stream().filter(Card::isTapped).count(),
                level == 2 ? 4L : 9L);
        check(game, level);
        game.copyLastState();
        return game;
    }

    private void copied(int level, int depth) {
        Game original = paid(level);
        Game target = original;
        for (int index = 0; index < depth; index++) {
            target = new GameCopier(target).withStack(true).makeCopy(null, actor(target));
        }
        assertSame(enchantment(target).getGame(), target);
        check(target, level);
        enchantment(target).setClassLevel(1);
        check(original, level);
    }

    @Test public void originalPaidLevelTwo() { paid(2); }
    @Test public void originalPaidLevelThree() { paid(3); }
    @Test public void oneSnapshotRetainsLevelTwo() { copied(2, 1); }
    @Test public void twoSnapshotsRetainLevelTwo() { copied(2, 2); }
    @Test public void oneSnapshotRetainsLevelThree() { copied(3, 1); }
    @Test public void twoSnapshotsRetainLevelThree() { copied(3, 2); }
}
