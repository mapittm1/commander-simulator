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

    private long clues(Game game) {
        return game.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.isToken() && card.getType().hasSubtype("Clue")).count();
    }

    private void counterBoundary(int depth, boolean prevent) {
        Game original = setup();
        if (prevent) cast(original, 0, "Solemnity", null, 3);
        Card land = named(original, 0, "Forest", ZoneType.Battlefield);
        int id = land.getId();
        long timestamp = land.getGameTimestamp();
        beginCast(original, 0, "Earthbending Lesson", land, 4);
        if (!prevent) { settle(original); animated(card(original, id, ZoneType.Battlefield)); }
        Game game = snapshot(original, depth);
        if (prevent) settle(game);
        else cast(game, 0, "Aether Snap", null, 5);
        returned(game, id, timestamp, false, 24, 0);
        named(game, 0, "Earthbending Lesson", ZoneType.Graveyard);
        if (!prevent) named(game, 0, "Aether Snap", ZoneType.Graveyard);
        if (depth > 0) {
            if (prevent) {
                assertFalse(card(original, id, ZoneType.Battlefield).isCreature());
                assertFalse(original.getStack().isEmpty());
            } else animated(card(original, id, ZoneType.Battlefield));
        }
    }

    private void repeated(int depth, boolean exile, boolean afterReturn) {
        Game original = setup();
        cast(original, 0, "Tireless Tracker", null, 3);
        assertEquals(clues(original), 0L);
        Card land = named(original, 0, "Forest", ZoneType.Battlefield);
        int id = land.getId();
        cast(original, 0, "Earthbending Lesson", land, 4);
        if (afterReturn) {
            cast(original, 0, "Murder", card(original, id, ZoneType.Battlefield), 3);
            assertEquals(clues(original), 1L);
            assertFalse(card(original, id, ZoneType.Battlefield).isCreature());
        }
        cast(original, 0, "Earthbending Lesson", card(original, id, ZoneType.Battlefield), 4);
        int power = afterReturn ? 4 : 8;
        assertEquals(card(original, id, ZoneType.Battlefield).getNetPower(), power);
        assertEquals(card(original, id, ZoneType.Battlefield).getCounters(CounterEnumType.P1P1), power);
        Game game = snapshot(original, depth);
        assertEquals(clues(game), afterReturn ? 1L : 0L, "Copying cannot repeat landfall");
        long timestamp = card(game, id, ZoneType.Battlefield).getGameTimestamp();
        cast(game, 0, exile ? "Swords to Plowshares" : "Murder", card(game, id, ZoneType.Battlefield), exile ? 1 : 3);
        Card returned = card(game, id, ZoneType.Battlefield);
        assertFalse(returned.isCreature());
        assertTrue(returned.isTapped());
        assertNotEquals(returned.getGameTimestamp(), timestamp);
        assertEquals(returned.getCounters(CounterEnumType.P1P1), 0);
        assertEquals(clues(game), afterReturn ? 2L : 1L, "Each return enters exactly once despite competing or old delayed triggers");
        assertEquals(seat(game, 0).getLife(), exile ? 100 + power : 100);
        if (depth > 0) {
            assertEquals(clues(original), afterReturn ? 1L : 0L);
            assertEquals(card(original, id, ZoneType.Battlefield).getNetPower(), power);
            assertEquals(seat(original, 0).getLife(), 100);
        }
    }

    private void newObjectNewController(int depth) {
        Game original = setup();
        fund(original, 1);
        Card land = named(original, 0, "Forest", ZoneType.Battlefield);
        int id = land.getId();
        cast(original, 0, "Earthbending Lesson", land, 4);
        cast(original, 0, "Murder", card(original, id, ZoneType.Battlefield), 3);
        assertFalse(card(original, id, ZoneType.Battlefield).isCreature());
        assertTrue(original.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(original)));
        settle(original);
        assertSame(original.getPhaseHandler().getPlayerTurn(), seat(original, 1));
        cast(original, 1, "Confiscate", card(original, id, ZoneType.Battlefield), 6);
        cast(original, 1, "Earthbending Lesson", card(original, id, ZoneType.Battlefield), 4);
        animated(card(original, id, ZoneType.Battlefield));
        assertSame(card(original, id, ZoneType.Battlefield).getController(), seat(original, 1));
        Game game = snapshot(original, depth);
        long timestamp = card(game, id, ZoneType.Battlefield).getGameTimestamp();
        cast(game, 0, "Swords to Plowshares", card(game, id, ZoneType.Battlefield), 1);
        Card returned = card(game, id, ZoneType.Battlefield);
        assertFalse(returned.isCreature());
        assertTrue(returned.isTapped());
        assertEquals(returned.getCounters(CounterEnumType.P1P1), 0);
        assertNotEquals(returned.getGameTimestamp(), timestamp);
        assertSame(returned.getOwner(), seat(game, 0));
        assertSame(returned.getController(), seat(game, 1), "An unused exile trigger for the prior permanent must not reclaim its new battlefield identity");
        assertEquals(seat(game, 0).getLife(), 100);
        assertEquals(seat(game, 1).getLife(), 104);
        if (depth > 0) {
            animated(card(original, id, ZoneType.Battlefield));
            assertSame(card(original, id, ZoneType.Battlefield).getController(), seat(original, 1));
            assertEquals(seat(original, 1).getLife(), 100);
        }
    }
    @Test public void preventedCountersDepth0() { counterBoundary(0, true); }
    @Test public void removedCountersDepth0() { counterBoundary(0, false); }
    @Test public void repeatedEarthDeathDepth0() { repeated(0, false, false); }
    @Test public void repeatedEarthExileDepth0() { repeated(0, true, false); }
    @Test public void reearthAfterReturnDepth0() { repeated(0, true, true); }
    @Test public void newObjectControllerDepth0() { newObjectNewController(0); }
    @Test public void preventedCountersDepth1() { counterBoundary(1, true); }
    @Test public void removedCountersDepth1() { counterBoundary(1, false); }
    @Test public void repeatedEarthDeathDepth1() { repeated(1, false, false); }
    @Test public void repeatedEarthExileDepth1() { repeated(1, true, false); }
    @Test public void reearthAfterReturnDepth1() { repeated(1, true, true); }
    @Test public void newObjectControllerDepth1() { newObjectNewController(1); }
    @Test public void preventedCountersDepth2() { counterBoundary(2, true); }
    @Test public void removedCountersDepth2() { counterBoundary(2, false); }
    @Test public void repeatedEarthDeathDepth2() { repeated(2, false, false); }
    @Test public void repeatedEarthExileDepth2() { repeated(2, true, false); }
    @Test public void reearthAfterReturnDepth2() { repeated(2, true, true); }
    @Test public void newObjectControllerDepth2() { newObjectNewController(2); }

    private void resolveOne(Game game) {
        assertFalse(game.getStack().isEmpty());
        game.getStack().resolveStack();
        game.getAction().checkStateEffects(true);
        game.getTriggerHandler().runWaitingTriggers();
        game.getStack().addAllTriggeredAbilitiesToStack();
    }

    private void interruptReturn(int depth, String response) {
        Game original = setup();
        Card land = named(original, 0, "Forest", ZoneType.Battlefield);
        int id = land.getId();
        cast(original, 0, "Earthbending Lesson", land, 4);
        long timestamp = card(original, id, ZoneType.Battlefield).getGameTimestamp();
        beginCast(original, 0, "Murder", card(original, id, ZoneType.Battlefield), 3);
        resolveOne(original);
        Card graveyardLand = card(original, id, ZoneType.Graveyard);
        assertFalse(original.getStack().isEmpty(), "The real earthbend return trigger must be available to respond to");
        assertTrue(original.getStack().peekAbility().isTrigger());
        if (response.equals("pendingCremate")) beginCast(original, 0, "Cremate", graveyardLand, 1);
        if (response.equals("stifle")) {
            SpellAbility trigger = original.getStack().peekAbility();
            SpellAbility stifle = addCardToZone("Stifle", seat(original, 0), ZoneType.Hand).getFirstSpellAbility();
            stifle.setActivatingPlayer(seat(original, 0));
            stifle.getTargets().add(trigger);
            assertTrue(stifle.canPlay() && stifle.checkRestrictions(stifle.getHostCard(), seat(original, 0)));
            assertTrue(ComputerUtil.playStack(stifle, seat(original, 0), original));
            assertEquals(original.getStack().peekAbility().getPayingMana().size(), 1);
        }
        Game game = snapshot(original, depth);
        if (response.equals("cremate")) cast(game, 0, "Cremate", card(game, id, ZoneType.Graveyard), 1);
        else settle(game);
        if (response.equals("return")) returned(game, id, timestamp, false, 24, 0);
        else {
            ZoneType zone = response.equals("stifle") ? ZoneType.Graveyard : ZoneType.Exile;
            Card stranded = card(game, id, zone);
            assertFalse(stranded.isCreature());
            assertEquals(stranded.getCounters(CounterEnumType.P1P1), 0);
            assertTrue(game.getCardsIn(ZoneType.Battlefield).stream().noneMatch(value -> value.getId() == id));
            assertEquals(seat(game, 0).getCardsIn(ZoneType.Library).size(), response.equals("stifle") ? 24 : 23);
            assertEquals(seat(game, 0).getLife(), 100);
        }
        assertTrue(game.getStack().isEmpty());
        if (depth > 0) {
            card(original, id, ZoneType.Graveyard);
            assertFalse(original.getStack().isEmpty());
            assertEquals(seat(original, 0).getCardsIn(ZoneType.Library).size(), 24);
            settle(original);
            if (response.equals("pendingCremate")) card(original, id, ZoneType.Exile);
            else if (response.equals("stifle")) card(original, id, ZoneType.Graveyard);
            else returned(original, id, timestamp, false, 24, 0);
        }
    }

    private void blinkReturn(int depth, boolean pendingBlink) {
        Game original = setup();
        cast(original, 0, "Tireless Tracker", null, 3);
        Card land = named(original, 0, "Forest", ZoneType.Battlefield);
        int id = land.getId();
        cast(original, 0, "Earthbending Lesson", land, 4);
        beginCast(original, 0, "Ephemerate", card(original, id, ZoneType.Battlefield), 1);
        if (!pendingBlink) {
            resolveOne(original);
            assertFalse(card(original, id, ZoneType.Battlefield).isCreature());
            assertFalse(card(original, id, ZoneType.Battlefield).isTapped());
        }
        Game game = snapshot(original, depth);
        settle(game);
        Card returned = card(game, id, ZoneType.Battlefield);
        assertFalse(returned.isCreature());
        assertFalse(returned.isTapped(), "The stale earthbend exile return must not tap a land already returned by blink");
        assertEquals(returned.getCounters(CounterEnumType.P1P1), 0);
        assertEquals(clues(game), 1L, "Blink causes exactly one native landfall entry");
        assertEquals(seat(game, 0).getLife(), 100);
        if (depth > 0) {
            if (pendingBlink) animated(card(original, id, ZoneType.Battlefield));
            else assertFalse(card(original, id, ZoneType.Battlefield).isCreature());
            assertFalse(original.getStack().isEmpty());
            settle(original);
            assertFalse(card(original, id, ZoneType.Battlefield).isTapped());
            assertEquals(clues(original), 1L);
            assertEquals(clues(game), 1L);
        }
    }
    @Test public void responseReturnDepth0() { interruptReturn(0, "return"); }
    @Test public void responseCremateDepth0() { interruptReturn(0, "cremate"); }
    @Test public void responsePendingCremateDepth0() { interruptReturn(0, "pendingCremate"); }
    @Test public void responseStifleDepth0() { interruptReturn(0, "stifle"); }
    @Test public void blinkReturnedDepth0() { blinkReturn(0, false); }
    @Test public void blinkPendingDepth0() { blinkReturn(0, true); }
    @Test public void responseReturnDepth1() { interruptReturn(1, "return"); }
    @Test public void responseCremateDepth1() { interruptReturn(1, "cremate"); }
    @Test public void responsePendingCremateDepth1() { interruptReturn(1, "pendingCremate"); }
    @Test public void responseStifleDepth1() { interruptReturn(1, "stifle"); }
    @Test public void blinkReturnedDepth1() { blinkReturn(1, false); }
    @Test public void blinkPendingDepth1() { blinkReturn(1, true); }
    @Test public void responseReturnDepth2() { interruptReturn(2, "return"); }
    @Test public void responseCremateDepth2() { interruptReturn(2, "cremate"); }
    @Test public void responsePendingCremateDepth2() { interruptReturn(2, "pendingCremate"); }
    @Test public void responseStifleDepth2() { interruptReturn(2, "stifle"); }
    @Test public void blinkReturnedDepth2() { blinkReturn(2, false); }
    @Test public void blinkPendingDepth2() { blinkReturn(2, true); }
}
