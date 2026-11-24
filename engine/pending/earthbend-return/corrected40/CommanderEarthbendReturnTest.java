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
        return game.getCardsIn(zone).stream().filter(value -> value.getId() == id).findFirst().orElseThrow();
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

    private void returned(Game game, int id, long timestamp, boolean exile, int library, int lifeBeneficiary) {
        Card land = card(game, id, ZoneType.Battlefield);
        assertTrue(land.isLand());
        assertFalse(land.isCreature());
        assertEquals(land.getCounters(CounterEnumType.P1P1), 0);
        assertTrue(land.isTapped());
        assertNotEquals(land.getGameTimestamp(), timestamp);
        assertSame(land.getController(), seat(game, 0), "Return under the original earthbend effect controller");
        assertEquals(seat(game, 0).getLife(), exile && lifeBeneficiary == 0 ? 104 : 100);
        assertEquals(seat(game, 1).getLife(), exile && lifeBeneficiary == 1 ? 104 : 100,
                "Swords rewards the controller immediately before exile, independently of the earthbend controller");
        assertEquals(seat(game, 0).getCardsIn(ZoneType.Library).size(), library);
        assertTrue(game.getStack().isEmpty());
    }

    private void boundary(int depth, boolean exile, String boundary) {
        Game original = setup();
        Card land;
        if (boundary.equals("foreignOwner")) {
            land = addCardToZone("Forest", seat(original, 1), ZoneType.Battlefield);
            cast(original, 0, "Confiscate", land, 6);
            assertSame(land.getOwner(), seat(original, 1));
            assertSame(land.getController(), seat(original, 0));
        } else land = named(original, 0, "Forest", ZoneType.Battlefield);
        int id = land.getId();
        cast(original, 0, "Earthbending Lesson", land, 4);
        animated(card(original, id, ZoneType.Battlefield));
        int library = 24;
        if (boundary.equals("sourceExiled")) {
            cast(original, 0, "Cremate", named(original, 0, "Earthbending Lesson", ZoneType.Graveyard), 1);
            named(original, 0, "Earthbending Lesson", ZoneType.Exile);
            library = 23;
        }
        if (boundary.equals("controllerChanged")) {
            fund(original, 1);
            cast(original, 1, "Ray of Command", card(original, id, ZoneType.Battlefield), 4);
            assertSame(card(original, id, ZoneType.Battlefield).getController(), seat(original, 1));
        }
        int controller = boundary.equals("controllerChanged") ? 1 : 0;
        int originalGraveyard = seat(original, 0).getCardsIn(ZoneType.Graveyard).size();
        Game game = snapshot(original, depth);
        if (boundary.equals("nextTurn")) {
            assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
            settle(game);
            assertSame(game.getPhaseHandler().getPlayerTurn(), seat(game, 1));
            animated(card(game, id, ZoneType.Battlefield));
        }
        long timestamp = card(game, id, ZoneType.Battlefield).getGameTimestamp();
        cast(game, 0, exile ? "Swords to Plowshares" : "Murder", card(game, id, ZoneType.Battlefield), exile ? 1 : 3);
        returned(game, id, timestamp, exile, library, controller);
        assertSame(card(game, id, ZoneType.Battlefield).getOwner(), seat(game, boundary.equals("foreignOwner") ? 1 : 0));
        if (boundary.equals("foreignOwner")) named(game, 0, "Confiscate", ZoneType.Graveyard);
        if (depth > 0) {
            animated(card(original, id, ZoneType.Battlefield));
            assertSame(card(original, id, ZoneType.Battlefield).getController(), seat(original, controller));
            assertEquals(seat(original, 0).getLife(), 100);
            assertEquals(seat(original, 0).getCardsIn(ZoneType.Graveyard).size(), originalGraveyard);
            assertEquals(seat(original, 0).getCardsIn(ZoneType.Library).size(), library);
        }
    }

    private void originalFirst(int depth, boolean exile) {
        Game original = setup();
        Card land = named(original, 0, "Forest", ZoneType.Battlefield);
        int id = land.getId();
        cast(original, 0, "Earthbending Lesson", land, 4);
        long originalTimestamp = card(original, id, ZoneType.Battlefield).getGameTimestamp();
        Game game = snapshot(original, depth);
        String removal = exile ? "Swords to Plowshares" : "Murder";
        cast(original, 0, removal, card(original, id, ZoneType.Battlefield), exile ? 1 : 3);
        returned(original, id, originalTimestamp, exile, 24, 0);
        animated(card(game, id, ZoneType.Battlefield));
        assertEquals(seat(game, 0).getLife(), 100);
        long timestamp = card(game, id, ZoneType.Battlefield).getGameTimestamp();
        cast(game, 0, removal, card(game, id, ZoneType.Battlefield), exile ? 1 : 3);
        returned(game, id, timestamp, exile, 24, 0);
        returned(original, id, originalTimestamp, exile, 24, 0);
    }
    @Test public void sourceExiledDeathDepth0() { boundary(0, false, "sourceExiled"); }
    @Test public void sourceExiledExileDepth0() { boundary(0, true, "sourceExiled"); }
    @Test public void controllerChangedDeathDepth0() { boundary(0, false, "controllerChanged"); }
    @Test public void controllerChangedExileDepth0() { boundary(0, true, "controllerChanged"); }
    @Test public void nextTurnDeathDepth0() { boundary(0, false, "nextTurn"); }
    @Test public void nextTurnExileDepth0() { boundary(0, true, "nextTurn"); }
    @Test public void foreignOwnerDeathDepth0() { boundary(0, false, "foreignOwner"); }
    @Test public void foreignOwnerExileDepth0() { boundary(0, true, "foreignOwner"); }
    @Test public void sourceExiledDeathDepth1() { boundary(1, false, "sourceExiled"); }
    @Test public void sourceExiledExileDepth1() { boundary(1, true, "sourceExiled"); }
    @Test public void controllerChangedDeathDepth1() { boundary(1, false, "controllerChanged"); }
    @Test public void controllerChangedExileDepth1() { boundary(1, true, "controllerChanged"); }
    @Test public void nextTurnDeathDepth1() { boundary(1, false, "nextTurn"); }
    @Test public void nextTurnExileDepth1() { boundary(1, true, "nextTurn"); }
    @Test public void foreignOwnerDeathDepth1() { boundary(1, false, "foreignOwner"); }
    @Test public void foreignOwnerExileDepth1() { boundary(1, true, "foreignOwner"); }
    @Test public void sourceExiledDeathDepth2() { boundary(2, false, "sourceExiled"); }
    @Test public void sourceExiledExileDepth2() { boundary(2, true, "sourceExiled"); }
    @Test public void controllerChangedDeathDepth2() { boundary(2, false, "controllerChanged"); }
    @Test public void controllerChangedExileDepth2() { boundary(2, true, "controllerChanged"); }
    @Test public void nextTurnDeathDepth2() { boundary(2, false, "nextTurn"); }
    @Test public void nextTurnExileDepth2() { boundary(2, true, "nextTurn"); }
    @Test public void foreignOwnerDeathDepth2() { boundary(2, false, "foreignOwner"); }
    @Test public void foreignOwnerExileDepth2() { boundary(2, true, "foreignOwner"); }
    @Test public void originalFirstDeathDepth1() { originalFirst(1, false); }
    @Test public void originalFirstExileDepth1() { originalFirst(1, true); }
    @Test public void originalFirstDeathDepth2() { originalFirst(2, false); }
    @Test public void originalFirstExileDepth2() { originalFirst(2, true); }
}
