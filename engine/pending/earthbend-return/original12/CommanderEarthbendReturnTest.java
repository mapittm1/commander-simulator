package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.card.CounterEnumType;
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

/** Paid earthbend and actual removal test delayed return, new objects and copied pending spells. */
public class CommanderEarthbendReturnTest extends AITest {
    private Player seat(Game game, int index) {
        return game.getPlayers().stream().filter(player -> player.getName().equals("earthbend-" + index))
                .findFirst().orElseThrow();
    }

    private Card named(Game game, int owner, String name, ZoneType zone) {
        return seat(game, owner).getCardsIn(zone, false).stream()
                .filter(card -> card.getName().equals(name)).findFirst().orElseThrow();
    }

    private void settle(Game game) {
        GameSimulator.resolveStack(game, seat(game, 0));
    }

    private void fund(Game game, int owner) {
        for (int index = 0; index < 8; index++) {
            addCardToZone("Plains", seat(game, owner), ZoneType.Battlefield);
            addCardToZone("Swamp", seat(game, owner), ZoneType.Battlefield);
            addCardToZone("Forest", seat(game, owner), ZoneType.Battlefield);
            addCardToZone("Island", seat(game, owner), ZoneType.Battlefield);
        }
    }

    private Game setup() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("earthbend-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Paid earthbend returns"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        fund(game, 0);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, seat(game, 0), 5);
        return game;
    }

    private void beginCast(Game game, int owner, String name, Card target, int mana) {
        SpellAbility ability = addCardToZone(name, seat(game, owner), ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(seat(game, owner));
        if (target != null) ability.getTargets().add(target);
        assertTrue(ability.canPlay() && ability.checkRestrictions(ability.getHostCard(), seat(game, owner)));
        assertTrue(ComputerUtil.playStack(ability, seat(game, owner), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
    }

    private void cast(Game game, int owner, String name, Card target, int mana) {
        beginCast(game, owner, name, target, mana);
        settle(game);
    }

    private Game snapshot(Game game, int depth) {
        game.copyLastState();
        for (int index = 0; index < depth; index++) game = new GameCopier(game).withStack(true).makeCopy(null, seat(game, 0));
        return game;
    }

    private Card card(Game game, int id, ZoneType zone) {
        return seat(game, 0).getCardsIn(zone).stream().filter(value -> value.getId() == id).findFirst().orElseThrow();
    }

    private void animated(Card land) {
        assertTrue(land.isLand() && land.isCreature());
        assertEquals(land.getCounters(CounterEnumType.P1P1), 4);
        assertEquals(land.getNetPower(), 4);
        assertTrue(land.hasKeyword("Haste"));
    }

    private void verifyReturn(int depth, boolean exile, boolean pendingEarth, boolean pendingRemoval) {
        Game original = setup();
        Card land = named(original, 0, "Forest", ZoneType.Battlefield);
        int id = land.getId();
        beginCast(original, 0, "Earthbending Lesson", land, 4);
        if (!pendingEarth) {
            settle(original);
            animated(card(original, id, ZoneType.Battlefield));
            named(original, 0, "Earthbending Lesson", ZoneType.Graveyard);
        }
        String removal = exile ? "Swords to Plowshares" : "Murder";
        int cost = exile ? 1 : 3;
        if (pendingRemoval) beginCast(original, 0, removal, card(original, id, ZoneType.Battlefield), cost);
        int originalGraveyard = seat(original, 0).getCardsIn(ZoneType.Graveyard).size();
        Game game = snapshot(original, depth);
        if (pendingEarth) {
            settle(game);
            animated(card(game, id, ZoneType.Battlefield));
            named(game, 0, "Earthbending Lesson", ZoneType.Graveyard);
        }
        long before = card(game, id, ZoneType.Battlefield).getGameTimestamp();
        if (!pendingRemoval) beginCast(game, 0, removal, card(game, id, ZoneType.Battlefield), cost);
        settle(game);
        Card returned = card(game, id, ZoneType.Battlefield);
        assertTrue(returned.isLand());
        assertFalse(returned.isCreature(), "The returned land is a new object without the prior animation");
        assertEquals(returned.getCounters(CounterEnumType.P1P1), 0);
        assertTrue(returned.isTapped(), "The printed delayed effect returns the land tapped");
        assertNotEquals(returned.getGameTimestamp(), before);
        assertSame(returned.getController(), seat(game, 0));
        named(game, 0, removal, ZoneType.Graveyard);
        assertEquals(seat(game, 0).getLife(), exile ? 104 : 100, "Swords uses the exiled creature's last-known four power");
        assertEquals(seat(game, 0).getCardsIn(ZoneType.Library).size(), 24);
        assertTrue(game.getStack().isEmpty());
        if (depth > 0) {
            Card untouched = card(original, id, ZoneType.Battlefield);
            if (pendingEarth) assertFalse(untouched.isCreature()); else animated(untouched);
            assertEquals(seat(original, 0).getLife(), 100);
            assertEquals(seat(original, 0).getCardsIn(ZoneType.Graveyard).size(), originalGraveyard);
            assertEquals(original.getStack().isEmpty(), !pendingEarth && !pendingRemoval);
        }
    }
    @Test public void deathDepth0() { verifyReturn(0, false, false, false); }
    @Test public void exileDepth0() { verifyReturn(0, true, false, false); }
    @Test public void pendingDeathDepth0() { verifyReturn(0, false, false, true); }
    @Test public void pendingEarthDepth0() { verifyReturn(0, true, true, false); }
    @Test public void deathDepth1() { verifyReturn(1, false, false, false); }
    @Test public void exileDepth1() { verifyReturn(1, true, false, false); }
    @Test public void pendingDeathDepth1() { verifyReturn(1, false, false, true); }
    @Test public void pendingEarthDepth1() { verifyReturn(1, true, true, false); }
    @Test public void deathDepth2() { verifyReturn(2, false, false, false); }
    @Test public void exileDepth2() { verifyReturn(2, true, false, false); }
    @Test public void pendingDeathDepth2() { verifyReturn(2, false, false, true); }
    @Test public void pendingEarthDepth2() { verifyReturn(2, true, true, false); }
}
