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

/** Paid Replenish checks Aura eligibility before simultaneous entry, in both orders. */
public class CommanderSimultaneousAuraTest extends AITest {
    private Player actor(Game game) {
        return game.getPlayers().stream().filter(player -> player.getName().equals("aura-0"))
                .findFirst().orElseThrow();
    }

    private Card named(Game game, String name, ZoneType zone) {
        return actor(game).getCardsIn(zone, false).stream()
                .filter(card -> card.getName().equals(name)).findFirst().orElseThrow();
    }

    private void absent(Game game, String name, ZoneType zone) {
        assertTrue(actor(game).getCardsIn(zone, false).stream().noneMatch(card -> card.getName().equals(name)),
                name + " must not be in " + zone);
    }

    private void beginCast(Game game, String name, Card target, int mana) {
        SpellAbility ability = addCardToZone(name, actor(game), ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(actor(game));
        if (target != null) ability.getTargets().add(target);
        assertTrue(ability.canPlay() && ability.checkRestrictions(ability.getHostCard(), actor(game)));
        assertTrue(ComputerUtil.playStack(ability, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
    }

    private void cast(Game game, String name, Card target, int mana) {
        beginCast(game, name, target, mana);
        GameSimulator.resolveStack(game, actor(game));
    }

    private Game setup() {
        List<RegisteredPlayer> players = new ArrayList<>();
        for (int index = 0; index < 4; index++) players.add(RegisteredPlayer.forCommander(new Deck())
                .setPlayer(new LobbyPlayerAi("aura-" + index, null)));
        GameRules rules = new GameRules(GameType.Commander);
        Game game = new Game(players, rules, new Match(rules, players, "Simultaneous Aura eligibility"));
        game.setNoGUIUser();
        game.setAge(GameStage.Play);
        for (Player player : game.getPlayers()) {
            player.setLife(100, null);
            for (int index = 0; index < 24; index++) addCardToZone("Forest", player, ZoneType.Library);
        }
        for (int index = 0; index < 8; index++) {
            addCardToZone("Plains", actor(game), ZoneType.Battlefield);
            addCardToZone("Forest", actor(game), ZoneType.Battlefield);
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(game), 5);
        return game;
    }

    private void check(int family, int depth, boolean reverse) {
        checkEntry(family, depth, reverse, false);
    }

    private void checkEntry(int family, int depth, boolean reverse, boolean onStack) {
        Game original = setup();
        if (family != 2) cast(original, "Grizzly Bears", null, 2);
        if (family == 1) cast(original, "Spirit Link", named(original, "Grizzly Bears", ZoneType.Battlefield), 1);
        String other = family == 2 ? "Courser of Kruphix" : "Daybreak Coronet";
        addCardToZone(reverse ? other : "Rancor", actor(original), ZoneType.Graveyard);
        addCardToZone(reverse ? "Rancor" : other, actor(original), ZoneType.Graveyard);
        original.copyLastState();
        if (onStack) beginCast(original, "Replenish", null, 4);
        Game game = original;
        for (int index = 0; index < depth; index++) game = new GameCopier(game).withStack(true).makeCopy(null, actor(game));
        if (onStack) GameSimulator.resolveStack(game, actor(game));
        else cast(game, "Replenish", null, 4);
        named(game, "Replenish", ZoneType.Graveyard);
        if (family == 2) {
            Card creature = named(game, other, ZoneType.Battlefield);
            assertEquals(creature.getNetPower(), 2);
            assertEquals(creature.getNetToughness(), 4);
            assertFalse(creature.isEnchanted());
            named(game, "Rancor", ZoneType.Graveyard);
            absent(game, "Rancor", ZoneType.Battlefield);
        } else {
            Card creature = named(game, "Grizzly Bears", ZoneType.Battlefield);
            Card rancor = named(game, "Rancor", ZoneType.Battlefield);
            assertSame(rancor.getAttachedTo(), creature);
            absent(game, "Rancor", ZoneType.Graveyard);
            if (family == 0) {
                named(game, other, ZoneType.Graveyard);
                absent(game, other, ZoneType.Battlefield);
                assertEquals(creature.getNetPower(), 4);
                assertEquals(creature.getNetToughness(), 2);
            } else {
                assertSame(named(game, other, ZoneType.Battlefield).getAttachedTo(), creature);
                assertSame(named(game, "Spirit Link", ZoneType.Battlefield).getAttachedTo(), creature);
                absent(game, other, ZoneType.Graveyard);
                assertEquals(creature.getNetPower(), 7);
                assertEquals(creature.getNetToughness(), 5);
            }
        }
        if (depth > 0) {
            named(original, "Rancor", ZoneType.Graveyard);
            named(original, other, ZoneType.Graveyard);
            absent(original, "Rancor", ZoneType.Battlefield);
            absent(original, other, ZoneType.Battlefield);
            if (family != 2) {
                Card creature = named(original, "Grizzly Bears", ZoneType.Battlefield);
                assertEquals(creature.getNetPower(), 2);
                assertEquals(creature.getNetToughness(), 2);
                assertEquals(creature.isEnchanted(), family == 1);
            }
        }
    }
    @Test public void unenchantedDepth0Forward() { check(0, 0, false); }
    @Test public void unenchantedDepth0Reverse() { check(0, 0, true); }
    @Test public void unenchantedDepth1Forward() { check(0, 1, false); }
    @Test public void unenchantedDepth1Reverse() { check(0, 1, true); }
    @Test public void unenchantedDepth2Forward() { check(0, 2, false); }
    @Test public void unenchantedDepth2Reverse() { check(0, 2, true); }
    @Test public void alreadyEnchantedDepth0Forward() { check(1, 0, false); }
    @Test public void alreadyEnchantedDepth0Reverse() { check(1, 0, true); }
    @Test public void alreadyEnchantedDepth1Forward() { check(1, 1, false); }
    @Test public void alreadyEnchantedDepth1Reverse() { check(1, 1, true); }
    @Test public void alreadyEnchantedDepth2Forward() { check(1, 2, false); }
    @Test public void alreadyEnchantedDepth2Reverse() { check(1, 2, true); }
    @Test public void newCreatureDepth0Forward() { check(2, 0, false); }
    @Test public void newCreatureDepth0Reverse() { check(2, 0, true); }
    @Test public void newCreatureDepth1Forward() { check(2, 1, false); }
    @Test public void newCreatureDepth1Reverse() { check(2, 1, true); }
    @Test public void newCreatureDepth2Forward() { check(2, 2, false); }
    @Test public void newCreatureDepth2Reverse() { check(2, 2, true); }
    @Test public void stackunenchantedDepth0Forward() { checkEntry(0, 0, false, true); }
    @Test public void stackunenchantedDepth0Reverse() { checkEntry(0, 0, true, true); }
    @Test public void stackunenchantedDepth1Forward() { checkEntry(0, 1, false, true); }
    @Test public void stackunenchantedDepth1Reverse() { checkEntry(0, 1, true, true); }
    @Test public void stackunenchantedDepth2Forward() { checkEntry(0, 2, false, true); }
    @Test public void stackunenchantedDepth2Reverse() { checkEntry(0, 2, true, true); }
    @Test public void stackalreadyEnchantedDepth0Forward() { checkEntry(1, 0, false, true); }
    @Test public void stackalreadyEnchantedDepth0Reverse() { checkEntry(1, 0, true, true); }
    @Test public void stackalreadyEnchantedDepth1Forward() { checkEntry(1, 1, false, true); }
    @Test public void stackalreadyEnchantedDepth1Reverse() { checkEntry(1, 1, true, true); }
    @Test public void stackalreadyEnchantedDepth2Forward() { checkEntry(1, 2, false, true); }
    @Test public void stackalreadyEnchantedDepth2Reverse() { checkEntry(1, 2, true, true); }
    @Test public void stacknewCreatureDepth0Forward() { checkEntry(2, 0, false, true); }
    @Test public void stacknewCreatureDepth0Reverse() { checkEntry(2, 0, true, true); }
    @Test public void stacknewCreatureDepth1Forward() { checkEntry(2, 1, false, true); }
    @Test public void stacknewCreatureDepth1Reverse() { checkEntry(2, 1, true, true); }
    @Test public void stacknewCreatureDepth2Forward() { checkEntry(2, 2, false, true); }
    @Test public void stacknewCreatureDepth2Reverse() { checkEntry(2, 2, true, true); }

    private void restriction(int depth, boolean onStack, boolean protection) {
        Game original = setup();
        Player opponent = original.getPlayers().stream().filter(player -> player.getName().equals("aura-1"))
                .findFirst().orElseThrow();
        original.getPhaseHandler().devModeSet(PhaseType.MAIN1, opponent, 5);
        for (int index = 0; index < 4; index++) {
            addCardToZone("Forest", opponent, ZoneType.Battlefield);
            addCardToZone("Swamp", opponent, ZoneType.Battlefield);
        }
        String creatureName = protection ? "Black Knight" : "Gladecover Scout";
        SpellAbility creatureSpell = addCardToZone(creatureName, opponent, ZoneType.Hand).getFirstSpellAbility();
        creatureSpell.setActivatingPlayer(opponent);
        assertTrue(creatureSpell.canPlay() && creatureSpell.checkRestrictions(creatureSpell.getHostCard(), opponent));
        assertTrue(ComputerUtil.playStack(creatureSpell, opponent, original));
        assertEquals(original.getStack().peekAbility().getPayingMana().size(), protection ? 2 : 1);
        GameSimulator.resolveStack(original, opponent);
        original.getPhaseHandler().devModeSet(PhaseType.MAIN1, actor(original), 6);
        String auraName = protection ? "Spirit Link" : "Rancor";
        addCardToZone(auraName, actor(original), ZoneType.Graveyard);
        original.copyLastState();
        if (onStack) beginCast(original, "Replenish", null, 4);
        Game game = original;
        for (int index = 0; index < depth; index++) game = new GameCopier(game).withStack(true).makeCopy(null, actor(game));
        if (onStack) GameSimulator.resolveStack(game, actor(game));
        else cast(game, "Replenish", null, 4);
        Card creature = game.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals(creatureName)).findFirst().orElseThrow();
        if (protection) {
            named(game, auraName, ZoneType.Graveyard);
            absent(game, auraName, ZoneType.Battlefield);
            assertFalse(creature.isEnchanted());
            assertEquals(creature.getNetPower(), 2);
        } else {
            assertSame(named(game, auraName, ZoneType.Battlefield).getAttachedTo(), creature);
            absent(game, auraName, ZoneType.Graveyard);
            assertEquals(creature.getNetPower(), 3);
            assertEquals(creature.getNetToughness(), 1);
        }
        if (depth > 0) {
            named(original, auraName, ZoneType.Graveyard);
            absent(original, auraName, ZoneType.Battlefield);
            Card untouched = original.getCardsIn(ZoneType.Battlefield).stream()
                    .filter(card -> card.getName().equals(creatureName)).findFirst().orElseThrow();
            assertFalse(untouched.isEnchanted());
            assertEquals(untouched.getNetPower(), protection ? 2 : 1);
        }
    }
    @Test public void hexproofDepth0BeforeCast() { restriction(0, false, false); }
    @Test public void hexproofDepth0Stack() { restriction(0, true, false); }
    @Test public void hexproofDepth1BeforeCast() { restriction(1, false, false); }
    @Test public void hexproofDepth1Stack() { restriction(1, true, false); }
    @Test public void hexproofDepth2BeforeCast() { restriction(2, false, false); }
    @Test public void hexproofDepth2Stack() { restriction(2, true, false); }
    @Test public void protectionDepth0BeforeCast() { restriction(0, false, true); }
    @Test public void protectionDepth0Stack() { restriction(0, true, true); }
    @Test public void protectionDepth1BeforeCast() { restriction(1, false, true); }
    @Test public void protectionDepth1Stack() { restriction(1, true, true); }
    @Test public void protectionDepth2BeforeCast() { restriction(2, false, true); }
    @Test public void protectionDepth2Stack() { restriction(2, true, true); }
}
