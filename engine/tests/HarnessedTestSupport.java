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
import static org.testng.Assert.*;

/** Pay printed harness costs before checking original and simulation worlds. */
public class HarnessedTestSupport extends AITest {
    protected Player actor(Game game) {
        return game.getPlayers().stream().filter(p -> p.getName().equals("harness-0")).findFirst().orElseThrow();
    }

    protected Card stone(Game game, boolean soul) {
        String name = soul ? "The Soul Stone" : "The Mind Stone";
        return actor(game).getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow();
    }

    protected Game paid(boolean soul) {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) players.add(RegisteredPlayer.forCommander(new Deck()).setPlayer(new LobbyPlayerAi("harness-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Paid harness state"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
            for (int index = 0; index < 14; index++) addCardToZone(soul ? "Swamp" : "Plains", player, ZoneType.Battlefield);
        }
        Player player = actor(game);
        if (soul) addCardToZone("Grizzly Bears", player, ZoneType.Battlefield);
        Card card = addCardToZone(soul ? "The Soul Stone" : "The Mind Stone", player, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, player, 5);
        SpellAbility cast = card.getFirstSpellAbility();
        cast.setActivatingPlayer(player);
        assertTrue(cast.canPlay() && cast.checkRestrictions(cast.getHostCard(), player));
        assertTrue(ComputerUtil.playStack(cast, player, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
        GameSimulator.resolveStack(game, player);
        Card permanent = stone(game, soul);
        assertFalse(permanent.isHarnessed());
        SpellAbility harness = permanent.getSpellAbilities().stream().filter(a -> a.getApi() == ApiType.AlterAttribute).findFirst().orElseThrow();
        harness.setActivatingPlayer(player);
        assertTrue(harness.canPlay() && harness.checkRestrictions(harness.getHostCard(), player));
        assertTrue(ComputerUtilCost.canPayCost(harness, player, false));
        assertTrue(ComputerUtil.playStack(harness, player, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), soul ? 7 : 6);
        assertTrue(permanent.isTapped());
        GameSimulator.resolveStack(game, player);
        assertTrue(permanent.isHarnessed(), "Actual paid harness activation resolves");
        assertEquals(player.getLandsInPlay().stream().filter(Card::isTapped).count(), soul ? 9L : 8L);
        if (soul) {
            assertTrue(player.getCreaturesInPlay().isEmpty(), "Exile a controlled creature as an actual cost");
            assertTrue(player.getCardsIn(ZoneType.Exile).stream().anyMatch(c -> c.getName().equals("Grizzly Bears")));
        }
        game.copyLastState();
        return game;
    }

    protected void copied(boolean soul, int depth) {
        Game original = paid(soul);
        Game target = original;
        for (int index = 0; index < depth; index++) target = new GameCopier(target).withStack(true).makeCopy(null, actor(target));
        assertSame(stone(target, soul).getGame(), target);
        assertTrue(stone(target, soul).isHarnessed(), "A simulation snapshot preserves the existing designation");
        stone(target, soul).setHarnessed(false);
        assertTrue(stone(original, soul).isHarnessed(), "Snapshot state mutation stays in its own world");
    }

}
