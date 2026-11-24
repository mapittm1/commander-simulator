package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.card.CardType.Supertype;
import forge.game.Game;
import forge.game.GameObject;
import forge.game.IEntityMap;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.phase.PhaseType;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Real casts distinguish World age from ordinary copying and new objects. */
public class CommanderWorldTransitionsTest extends WorldTestSupport {
    private void fund(Game game, int owner) {
        for (int index = 0; index < 8; index++) {
            addCardToZone("Island", seat(game, owner), ZoneType.Battlefield);
            addCardToZone("Forest", seat(game, owner), ZoneType.Battlefield);
        }
    }

    private Game snapshot(Game game, int depth) {
        for (int index = 0; index < depth; index++) {
            game = new GameCopier(game).withStack(true).makeCopy(null, seat(game, 0));
        }
        return game;
    }

    private void targetCast(Game game, int owner, String name, Card target, int mana) {
        SpellAbility ability = addCardToZone(name, seat(game, owner), ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(seat(game, owner));
        ability.getTargets().add(target);
        assertTrue(ability.canPlay() && ability.checkRestrictions(ability.getHostCard(), seat(game, owner)));
        assertTrue(ComputerUtil.playStack(ability, seat(game, owner), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        settle(game);
    }

    private void history(int depth) {
        Game original = oneWorld();
        long age = named(original, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp();
        Game current = original;
        List<GameCopier> copies = new ArrayList<>();
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(current).withStack(true);
            current = copier.makeCopy(null, seat(current, 0));
            copies.add(copier);
        }
        final Game target = current;
        IEntityMap map = new IEntityMap() {
            @Override public Game getGame() { return target; }
            @Override public GameObject map(GameObject value) {
                if (value == null) return null;
                GameObject result = value;
                for (GameCopier copier : copies) result = copier.find(result);
                return result;
            }
        };
        Card source = named(original, 0, "The Abyss", ZoneType.Battlefield);
        Card historical = CardCopyService.getLKICopyForGame(source, map);
        assertNotSame(historical, source);
        assertSame(historical.getGame(), target);
        assertSame(historical.getOwner(), seat(target, 0));
        assertEquals(historical.getWorldTimestamp(), age);
        assertTrue(seat(target, 0).getCardsIn(ZoneType.Battlefield, false).stream().noneMatch(card -> card == historical));
        historical.setTapped(true);
        assertFalse(source.isTapped());
        assertFalse(named(target, 0, "The Abyss", ZoneType.Battlefield).isTapped());
        assertEquals(source.getWorldTimestamp(), age);
        assertEquals(named(target, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp(), age);
    }

    private void ordinaryCopy(int depth) {
        Game original = oneWorld();
        long age = named(original, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp();
        Game game = snapshot(original, depth);
        assertEquals(named(game, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp(), age);
        fund(game, 1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, seat(game, 1), 6);
        cast(game, 1, "Copy Enchantment", 3);
        Card copied = named(game, 1, "The Abyss", ZoneType.Battlefield);
        assertTrue(copied.getWorldTimestamp() > age, "An ordinary copy entering is a newer World");
        assertNotNull(named(game, 0, "The Abyss", ZoneType.Graveyard));
        assertSame(copied.getOwner(), seat(game, 1));
        if (depth > 0) assertEquals(named(original, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp(), age);
    }

    private void reentry(int depth) {
        Game original = oneWorld();
        long age = named(original, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp();
        Game game = snapshot(original, depth);
        Card old = named(game, 0, "The Abyss", ZoneType.Battlefield);
        assertEquals(old.getWorldTimestamp(), age);
        fund(game, 0);
        targetCast(game, 0, "Boomerang", old, 2);
        assertNotNull(named(game, 0, "The Abyss", ZoneType.Hand));
        Card returned = named(game, 0, "The Abyss", ZoneType.Hand);
        SpellAbility ability = returned.getFirstSpellAbility();
        ability.setActivatingPlayer(seat(game, 0));
        assertTrue(ability.canPlay() && ability.checkRestrictions(returned, seat(game, 0)));
        assertTrue(ComputerUtil.playStack(ability, seat(game, 0), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 4);
        settle(game);
        assertTrue(named(game, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp() > age,
                "Paid re-entry starts a new World age");
        if (depth > 0) assertEquals(named(original, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp(), age);
    }

    private void donated(int depth) {
        Game original = oneWorld();
        long age = named(original, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp();
        fund(original, 0);
        Card source = named(original, 0, "The Abyss", ZoneType.Battlefield);
        SpellAbility ability = addCardToZone("Donate", seat(original, 0), ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(seat(original, 0));
        ability.getTargets().add(seat(original, 1));
        ability.getSubAbility().getTargets().add(source);
        assertTrue(ability.canPlay() && ability.checkRestrictions(ability.getHostCard(), seat(original, 0)));
        assertTrue(ComputerUtil.playStack(ability, seat(original, 0), original));
        assertEquals(original.getStack().peekAbility().getPayingMana().size(), 3);
        settle(original);
        assertSame(source.getOwner(), seat(original, 0));
        assertSame(source.getController(), seat(original, 1));
        assertEquals(source.getWorldTimestamp(), age, "Changing controller does not restart World age");
        Game game = snapshot(original, depth);
        Card copied = named(game, 1, "The Abyss", ZoneType.Battlefield);
        assertEquals(copied.getWorldTimestamp(), age);
        assertSame(copied.getOwner(), seat(game, 0));
        assertSame(copied.getController(), seat(game, 1));
        assertEquals(source.getWorldTimestamp(), age);
    }

    private void masked(int depth) {
        Game original = oneWorld();
        fund(original, 0);
        fund(original, 1);
        long age = named(original, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp();
        gameMain(original, 1);
        targetCast(original, 1, "Song of the Dryads", named(original, 0, "The Abyss", ZoneType.Battlefield), 3);
        assertTrue(named(original, 0, "The Abyss", ZoneType.Battlefield).isLand());
        assertTrue(named(original, 0, "The Abyss", ZoneType.Battlefield).getType().hasSupertype(Supertype.World));
        assertEquals(named(original, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp(), age);
        Game game = snapshot(original, depth);
        Card target = named(game, 0, "The Abyss", ZoneType.Battlefield);
        assertTrue(target.isLand());
        assertEquals(target.getWorldTimestamp(), age);
        gameMain(game, 0);
        targetCast(game, 0, "Naturalize", named(game, 1, "Song of the Dryads", ZoneType.Battlefield), 2);
        assertTrue(target.isEnchantment());
        assertFalse(target.isLand());
        assertEquals(target.getWorldTimestamp(), age, "Suppression/restoration does not restart the World supertype");
        if (depth > 0) {
            assertTrue(named(original, 0, "The Abyss", ZoneType.Battlefield).isLand());
            assertNotNull(named(original, 1, "Song of the Dryads", ZoneType.Battlefield));
        }
    }

    private void gameMain(Game game, int owner) {
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, seat(game, owner), 6);
    }

    private void simultaneous(int depth) {
        Game original = oneWorld();
        targetCast(original, 0, "Disenchant", named(original, 0, "The Abyss", ZoneType.Battlefield), 2);
        assertNotNull(named(original, 0, "The Abyss", ZoneType.Graveyard));
        addCardToZone("Nether Void", seat(original, 0), ZoneType.Graveyard);
        Game game = snapshot(original, depth);
        cast(game, 0, "Replenish", 4);
        assertNotNull(named(game, 0, "The Abyss", ZoneType.Graveyard));
        assertNotNull(named(game, 0, "Nether Void", ZoneType.Graveyard));
        assertTrue(seat(game, 0).getCardsIn(ZoneType.Battlefield).stream()
                .noneMatch(card -> card.getType().hasSupertype(Supertype.World)),
                "Worlds entering together tie in age and are both put into the graveyard");
        if (depth > 0) {
            assertNotNull(named(original, 0, "The Abyss", ZoneType.Graveyard));
            assertNotNull(named(original, 0, "Nether Void", ZoneType.Graveyard));
        }
    }
    @Test public void historyDepth0() { history(0); }
    @Test public void historyDepth1() { history(1); }
    @Test public void historyDepth2() { history(2); }
    @Test public void ordinaryCopyDepth0() { ordinaryCopy(0); }
    @Test public void ordinaryCopyDepth1() { ordinaryCopy(1); }
    @Test public void ordinaryCopyDepth2() { ordinaryCopy(2); }
    @Test public void reentryDepth0() { reentry(0); }
    @Test public void reentryDepth1() { reentry(1); }
    @Test public void reentryDepth2() { reentry(2); }
    @Test public void donatedDepth0() { donated(0); }
    @Test public void donatedDepth1() { donated(1); }
    @Test public void donatedDepth2() { donated(2); }
    @Test public void maskedDepth0() { masked(0); }
    @Test public void maskedDepth1() { masked(1); }
    @Test public void maskedDepth2() { masked(2); }
    @Test public void simultaneousDepth0() { simultaneous(0); }
    @Test public void simultaneousDepth1() { simultaneous(1); }
    @Test public void simultaneousDepth2() { simultaneous(2); }

    private Card mirrorAt(Game game, int identity) {
        return seat(game, 1).getCardsIn(ZoneType.Battlefield, false).stream()
                .filter(card -> card.getId() == identity).findFirst().orElseThrow();
    }

    private void expireWorldCopy(Game game, int identity) {
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
        settle(game);
        Card mirror = mirrorAt(game, identity);
        assertEquals(mirror.getName(), "Mirage Mirror");
        assertTrue(mirror.isArtifact());
        assertFalse(mirror.getType().hasSupertype(Supertype.World));
        assertEquals(mirror.getWorldTimestamp(), -1L, "Losing World clears the old World age");
    }

    private void temporaryWorld(int depth, boolean copyFirst) {
        Game original = oneWorld();
        long oldAge = named(original, 0, "The Abyss", ZoneType.Battlefield).getWorldTimestamp();
        gameMain(original, 1);
        cast(original, 1, "Mirage Mirror", 3);
        Card mirror = named(original, 1, "Mirage Mirror", ZoneType.Battlefield);
        int identity = mirror.getId();
        SpellAbility ability = mirror.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == forge.game.ability.ApiType.Clone)
                .findFirst().orElseThrow();
        ability.setActivatingPlayer(seat(original, 1));
        ability.getTargets().add(named(original, 0, "The Abyss", ZoneType.Battlefield));
        assertTrue(ability.canPlay() && ability.checkRestrictions(mirror, seat(original, 1)));
        assertTrue(ComputerUtil.playStack(ability, seat(original, 1), original));
        assertEquals(original.getStack().peekAbility().getPayingMana().size(), 2);
        settle(original);
        long age = mirrorAt(original, identity).getWorldTimestamp();
        assertTrue(age > oldAge, "Gaining World through an ordinary copy effect starts World age");
        assertNotNull(named(original, 0, "The Abyss", ZoneType.Graveyard));
        Game copy = snapshot(original, depth);
        assertEquals(mirrorAt(copy, identity).getWorldTimestamp(), age);
        Game first = copyFirst ? copy : original;
        Game other = copyFirst ? original : copy;
        expireWorldCopy(first, identity);
        if (depth > 0) {
            assertTrue(mirrorAt(other, identity).getType().hasSupertype(Supertype.World));
            assertEquals(mirrorAt(other, identity).getWorldTimestamp(), age);
            expireWorldCopy(other, identity);
        }
    }
    @Test public void temporaryWorldOriginal() { temporaryWorld(0, false); }
    @Test public void temporaryWorldDepth1OriginalFirst() { temporaryWorld(1, false); }
    @Test public void temporaryWorldDepth1CopyFirst() { temporaryWorld(1, true); }
    @Test public void temporaryWorldDepth2OriginalFirst() { temporaryWorld(2, false); }
    @Test public void temporaryWorldDepth2CopyFirst() { temporaryWorld(2, true); }
}
