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
import forge.game.card.CounterEnumType;
import forge.game.combat.Combat;
import java.util.Map;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Actual four-element actions and Avatar Aang transformation distinguish history from card state. */
public class CommanderElementalBendHistoryTest extends AITest {
    private Player seat(Game game, int index) {
        return game.getPlayers().stream().filter(player -> player.getName().equals("elements-" + index))
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
        for (int index = 0; index < 12; index++) {
            addCardToZone("Plains", seat(game, owner), ZoneType.Battlefield);
            addCardToZone("Mountain", seat(game, owner), ZoneType.Battlefield);
            addCardToZone("Island", seat(game, owner), ZoneType.Battlefield);
            addCardToZone("Forest", seat(game, owner), ZoneType.Battlefield);
            addCardToZone("Swamp", seat(game, owner), ZoneType.Battlefield);
        }
    }

    private Game setup() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("elements-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Paid elemental history"));
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


    private Card avatar(Game game) {
        return seat(game, 0).getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Avatar Aang") || card.getName().equals("Aang, Master of Elements"))
                .findFirst().orElseThrow();
    }

    private int library(Game game) {
        return seat(game, 0).getCardsIn(ZoneType.Library).size();
    }

    private Game board() {
        Game game = setup();
        cast(game, 0, "Avatar Aang", null, 4);
        cast(game, 0, "Ruthless Waterbender", null, 2);
        cast(game, 0, "Shivan Dragon", null, 6);
        cast(game, 0, "Expedite", avatar(game), 1);
        assertEquals(library(game), 23);
        return game;
    }

    private void earth(Game game) {
        int before = library(game);
        Card forest = named(game, 0, "Forest", ZoneType.Battlefield);
        cast(game, 0, "Earthbending Lesson", forest, 4);
        assertTrue(forest.isLand() && forest.isCreature());
        assertEquals(forest.getCounters(CounterEnumType.P1P1), 4);
        assertEquals(forest.getNetPower(), 4);
        assertEquals(library(game), before - 1, "Printed Avatar trigger draws once for the native earthbend");
    }

    private void fire(Game game) {
        int before = library(game);
        Card attacker = avatar(game);
        assertEquals(attacker.getNetPower(), 4);
        // Supply the declaration before the native step begins: advancing into
        // declare attackers already performs that turn-based action once.
        ((PlayerControllerAi) seat(game, 0).getController()).getAi().setAttackOverride(Map.of(attacker, seat(game, 1)));
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.COMBAT_DECLARE_ATTACKERS, () -> settle(game)));
        Combat combat = game.getCombat();
        settle(game);
        assertTrue(combat.getAttackers().contains(attacker));
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2, () -> settle(game)));
        settle(game);
        assertEquals(seat(game, 1).getLife(), 96, "Resolve native unblocked flying combat damage");
        assertEquals(library(game), before - 1, "Printed Avatar trigger draws once for firebending resolution");
        assertEquals(seat(game, 0).getManaPool().totalMana(), 0, "Firebending mana expires at end of combat");
    }

    private void water(Game game) {
        int before = library(game);
        Card waterbender = named(game, 0, "Ruthless Waterbender", ZoneType.Battlefield);
        int power = waterbender.getNetPower();
        List<Card> untapped = seat(game, 0).getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> !card.isTapped() && (card.isCreature() || card.isArtifact())).toList();
        SpellAbility ability = waterbender.getSpellAbilities().stream().filter(sa -> sa.isActivatedAbility()
                && sa.getApi() == forge.game.ability.ApiType.Pump).findFirst().orElseThrow();
        ability.setActivatingPlayer(seat(game, 0));
        assertTrue(ability.canPlay() && ability.checkRestrictions(waterbender, seat(game, 0)));
        assertTrue(ComputerUtil.playStack(ability, seat(game, 0), game));
        SpellAbility paid = game.getStack().peekAbility();
        java.util.Set<Card> manaSources = new java.util.HashSet<>();
        for (forge.game.mana.Mana mana : paid.getPayingMana()) manaSources.add(mana.getSourceCard());
        long waterTaps = untapped.stream().filter(Card::isTapped).filter(card -> !manaSources.contains(card)).count();
        assertEquals(paid.getPayingMana().size() + waterTaps, 2L,
                "Native waterbend pays two using mana and/or distinct non-mana-source taps");
        settle(game);
        assertEquals(waterbender.getNetPower(), power + 1);
        assertEquals(library(game), before - 1, "Printed Avatar trigger draws once for the paid waterbend");
    }

    private void air(Game game) {
        int before = library(game);
        cast(game, 0, "Airbending Lesson", named(game, 0, "Shivan Dragon", ZoneType.Battlefield), 3);
        named(game, 0, "Shivan Dragon", ZoneType.Exile);
        assertEquals(library(game), before - 2, "Lesson draw plus one native airbend observer trigger");
    }

    private Game three(boolean airThird) {
        Game game = board();
        earth(game);
        fire(game);
        if (airThird) air(game); else water(game);
        assertFalse(seat(game, 0).hasAllElementBend());
        assertEquals(avatar(game).getName(), "Avatar Aang");
        return game;
    }

    private void finish(int depth, boolean airThird) {
        Game original = three(airThird);
        int before = library(original);
        Game game = snapshot(original, depth);
        if (airThird) water(game); else air(game);
        assertEquals(avatar(game).getName(), "Aang, Master of Elements",
                "The printed fourth-element trigger must transform the real Avatar");
        assertTrue(seat(game, 0).hasAllElementBend());
        assertEquals(avatar(game).getNetPower(), 6);
        if (depth > 0) {
            assertEquals(avatar(original).getName(), "Avatar Aang");
            assertFalse(seat(original, 0).hasAllElementBend());
            assertEquals(library(original), before);
        }
    }

    private void completed(int depth) {
        Game original = three(false);
        air(original);
        assertEquals(avatar(original).getName(), "Aang, Master of Elements");
        assertTrue(seat(original, 0).hasAllElementBend());
        Game game = snapshot(original, depth);
        assertEquals(avatar(game).getName(), "Aang, Master of Elements");
        assertTrue(seat(game, 0).hasAllElementBend(), "A copied completed set is still all four this turn");
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
        settle(game);
        assertFalse(seat(game, 0).hasAllElementBend(), "Native turn reset clears the copied set");
        if (depth > 0) assertTrue(seat(original, 0).hasAllElementBend(), "Clearing the copy cannot mutate the original set");
    }

    private void resetBeforeFourth(int depth) {
        Game original = three(false);
        Game game = snapshot(original, depth);
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
        settle(game);
        assertFalse(seat(game, 0).hasAllElementBend());
        air(game);
        assertEquals(avatar(game).getName(), "Avatar Aang", "Prior-turn bending cannot satisfy this turn's four-element condition");
        assertFalse(seat(game, 0).hasAllElementBend());
    }
    @Test public void airFourthDepth0() { finish(0, false); }
    @Test public void waterFourthDepth0() { finish(0, true); }
    @Test public void completedDepth0() { completed(0); }
    @Test public void resetBeforeFourthDepth0() { resetBeforeFourth(0); }
    @Test public void airFourthDepth1() { finish(1, false); }
    @Test public void waterFourthDepth1() { finish(1, true); }
    @Test public void completedDepth1() { completed(1); }
    @Test public void resetBeforeFourthDepth1() { resetBeforeFourth(1); }
    @Test public void airFourthDepth2() { finish(2, false); }
    @Test public void waterFourthDepth2() { finish(2, true); }
    @Test public void completedDepth2() { completed(2); }
    @Test public void resetBeforeFourthDepth2() { resetBeforeFourth(2); }
}
