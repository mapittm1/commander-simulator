package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.game.Game;
import forge.game.GameObject;
import forge.game.IEntityMap;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Printed Class progression, draw consequences and noncopiable state boundaries. */
public class CommanderClassTransitionsTest extends ClassTestSupport {
    private void settle(Game game) {
        GameSimulator.resolveStack(game, actor(game));
    }

    private Card bear(Game game) {
        return actor(game).getCreaturesInPlay().stream()
                .filter(card -> card.getName().equals("Grizzly Bears")).findFirst().orElseThrow();
    }

    private Game outcomeBoard(int level) {
        Game game = paid(level);
        addCardToZone("Opt", actor(game), ZoneType.Hand);
        game.copyLastState();
        return game;
    }

    private void drawOutcome(Game game, int level) {
        int hand = actor(game).getCardsIn(ZoneType.Hand).size();
        if (level == 2) {
            SpellAbility upgrade = levelAbility(game, 3);
            assertTrue(permitted(upgrade, actor(game)), "Pay for the next level rather than repeating level2");
            assertTrue(ComputerUtil.playStack(upgrade, actor(game), game));
            assertEquals(game.getStack().peekAbility().getPayingMana().size(), 5);
            settle(game);
            assertEquals(actor(game).getCardsIn(ZoneType.Hand).size(), hand,
                    "Level3 does not repeat the earlier level2 draw");
        }
        Card opt = actor(game).getCardsIn(ZoneType.Hand).stream()
                .filter(card -> card.getName().equals("Opt")).findFirst().orElseThrow();
        SpellAbility cast = opt.getFirstSpellAbility();
        assertTrue(permitted(cast, actor(game)));
        assertTrue(ComputerUtil.playStack(cast, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 1);
        settle(game);
        assertEquals(bear(game).getCounters(CounterEnumType.P1P1), 1,
                "The real Opt draw triggers the level3 ability exactly once");
        assertEquals(actor(game).getCardsIn(ZoneType.Hand).size(), hand);
        check(game, 3);
    }

    private void pairedDraw(int level, int depth, boolean copyFirst) {
        Game original = outcomeBoard(level);
        Game copy = original;
        for (int index = 0; index < depth; index++) copy = new GameCopier(copy).withStack(true).makeCopy(null, actor(copy));
        Game first = copyFirst ? copy : original;
        Game other = copyFirst ? original : copy;
        int hand = actor(other).getCardsIn(ZoneType.Hand).size();
        drawOutcome(first, level);
        assertEquals(enchantment(other).getClassLevel(), level);
        assertEquals(bear(other).getCounters(CounterEnumType.P1P1), 0);
        assertEquals(actor(other).getCardsIn(ZoneType.Hand).size(), hand);
        drawOutcome(other, level);
    }

    private void history(int level, int depth) {
        Game original = paid(level);
        Game current = original;
        List<GameCopier> copies = new ArrayList<>();
        for (int index = 0; index < depth; index++) {
            GameCopier copier = new GameCopier(current).withStack(true);
            current = copier.makeCopy(null, actor(current));
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
        Card historical = CardCopyService.getLKICopyForGame(enchantment(original), map);
        assertSame(historical.getGame(), target);
        assertSame(historical.getOwner(), actor(target));
        assertEquals(historical.getClassLevel(), level);
        assertTrue(actor(target).getCardsIn(ZoneType.Battlefield).stream().noneMatch(card -> card == historical));
        historical.setClassLevel(1);
        check(original, level);
        check(target, level);
    }

    private void ordinaryCopy(int level) {
        Game game = paid(level);
        Player other = game.getPlayers().stream().filter(player -> player.getName().equals("class-1"))
                .findFirst().orElseThrow();
        Card spell = addCardToZone("Mirrormade", other, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, other, 6);
        SpellAbility cast = spell.getFirstSpellAbility();
        assertTrue(permitted(cast, other));
        assertTrue(ComputerUtil.playStack(cast, other, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        settle(game);
        Card copied = other.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Wizard Class")).findFirst().orElseThrow();
        assertEquals(copied.getClassLevel(), 1, "An ordinary copy permanent starts at level1");
        assertEquals(enchantment(game).getClassLevel(), level);
    }

    private void reenter(Game game) {
        game.getAction().moveToHand(enchantment(game), null);
        settle(game);
        Card card = actor(game).getCardsIn(ZoneType.Hand).stream()
                .filter(value -> value.getName().equals("Wizard Class")).findFirst().orElseThrow();
        SpellAbility cast = card.getFirstSpellAbility();
        assertTrue(permitted(cast, actor(game)));
        assertTrue(ComputerUtil.playStack(cast, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 1);
        settle(game);
        assertEquals(enchantment(game).getClassLevel(), 1);
        int hand = actor(game).getCardsIn(ZoneType.Hand).size();
        SpellAbility upgrade = levelAbility(game, 2);
        assertTrue(permitted(upgrade, actor(game)));
        assertTrue(ComputerUtil.playStack(upgrade, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        settle(game);
        check(game, 2);
        assertEquals(actor(game).getCardsIn(ZoneType.Hand).size(), hand + 2);
        assertEquals(bear(game).getCounters(CounterEnumType.P1P1), 0,
                "A fresh level2 permanent cannot retain the previous level3 draw ability");
    }

    private void independentReentry(int level) {
        Game original = paid(level);
        Game copy = new GameCopier(original).withStack(true).makeCopy(null, actor(original));
        copy = new GameCopier(copy).withStack(true).makeCopy(null, actor(copy));
        reenter(copy);
        check(original, level);
        reenter(original);
    }

    private void donated(int level) {
        Game game = paid(level);
        Player recipient = game.getPlayers().stream().filter(player -> player.getName().equals("class-1"))
                .findFirst().orElseThrow();
        Card source = enchantment(game);
        Card spell = addCardToZone("Donate", actor(game), ZoneType.Hand);
        SpellAbility cast = spell.getFirstSpellAbility();
        cast.setActivatingPlayer(actor(game));
        cast.getTargets().add(recipient);
        cast.getSubAbility().getTargets().add(source);
        assertTrue(permitted(cast, actor(game)));
        assertTrue(ComputerUtil.playStack(cast, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        settle(game);
        assertSame(source.getController(), recipient);
        assertEquals(source.getClassLevel(), level);
        Game current = game;
        for (int index = 0; index < 2; index++) current = new GameCopier(current).withStack(true).makeCopy(null, actor(current));
        final Game target = current;
        Player copiedRecipient = target.getPlayers().stream().filter(player -> player.getName().equals("class-1"))
                .findFirst().orElseThrow();
        Card copied = copiedRecipient.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Wizard Class")).findFirst().orElseThrow();
        assertEquals(copied.getClassLevel(), level);
        assertSame(copied.getOwner(), actor(target));
        assertSame(copied.getController(), copiedRecipient);
    }
    @Test public void originalLevel2PrintedDrawConsequence() { drawOutcome(outcomeBoard(2), 2); }
    @Test public void level2DrawDepth1CopyFirstFalse() { pairedDraw(2, 1, false); }
    @Test public void level2DrawDepth1CopyFirstTrue() { pairedDraw(2, 1, true); }
    @Test public void level2DrawDepth2CopyFirstFalse() { pairedDraw(2, 2, false); }
    @Test public void level2DrawDepth2CopyFirstTrue() { pairedDraw(2, 2, true); }
    @Test public void level2PrivateHistoryDepth1() { history(2, 1); }
    @Test public void level2PrivateHistoryDepth2() { history(2, 2); }
    @Test public void level2OrdinaryCopyStartsAtOne() { ordinaryCopy(2); }
    @Test public void level2IndependentPaidReentryResetsLevel() { independentReentry(2); }
    @Test public void level2PaidControlChangeRetainsLevel() { donated(2); }
    @Test public void originalLevel3PrintedDrawConsequence() { drawOutcome(outcomeBoard(3), 3); }
    @Test public void level3DrawDepth1CopyFirstFalse() { pairedDraw(3, 1, false); }
    @Test public void level3DrawDepth1CopyFirstTrue() { pairedDraw(3, 1, true); }
    @Test public void level3DrawDepth2CopyFirstFalse() { pairedDraw(3, 2, false); }
    @Test public void level3DrawDepth2CopyFirstTrue() { pairedDraw(3, 2, true); }
    @Test public void level3PrivateHistoryDepth1() { history(3, 1); }
    @Test public void level3PrivateHistoryDepth2() { history(3, 2); }
    @Test public void level3OrdinaryCopyStartsAtOne() { ordinaryCopy(3); }
    @Test public void level3IndependentPaidReentryResetsLevel() { independentReentry(3); }
    @Test public void level3PaidControlChangeRetainsLevel() { donated(3); }

    private Game maskedBoard(int level) {
        Game game = outcomeBoard(level);
        Player other = game.getPlayers().stream().filter(player -> player.getName().equals("class-1"))
                .findFirst().orElseThrow();
        for (int index = 0; index < 3; index++) addCardToZone("Forest", other, ZoneType.Battlefield);
        for (int index = 0; index < 2; index++) addCardToZone("Forest", actor(game), ZoneType.Battlefield);
        Card target = enchantment(game);
        Card song = addCardToZone("Song of the Dryads", other, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, other, 6);
        SpellAbility cast = song.getFirstSpellAbility();
        cast.setActivatingPlayer(other);
        cast.getTargets().add(target);
        assertTrue(cast.canPlay() && cast.checkRestrictions(cast.getHostCard(), other));
        assertTrue(ComputerUtil.playStack(cast, other, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        settle(game);
        assertTrue(target.isLand());
        assertFalse(target.isClassCard(), "A Class can temporarily have a different card type");
        assertEquals(target.getClassLevel(), level, "Class level remains while the permanent is not a Class");
        addCardToZone("Naturalize", actor(game), ZoneType.Hand);
        game.copyLastState();
        return game;
    }

    private void restoreMasked(Game game, int level) {
        Card target = enchantment(game);
        assertTrue(target.isLand());
        assertFalse(target.isClassCard(), "A Class can temporarily have a different card type");
        assertEquals(target.getClassLevel(), level, "Class level remains while the permanent is not a Class");
        Player other = game.getPlayers().stream().filter(player -> player.getName().equals("class-1"))
                .findFirst().orElseThrow();
        Card aura = other.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Song of the Dryads")).findFirst().orElseThrow();
        Card naturalize = actor(game).getCardsIn(ZoneType.Hand).stream()
                .filter(card -> card.getName().equals("Naturalize")).findFirst().orElseThrow();
        SpellAbility cast = naturalize.getFirstSpellAbility();
        cast.setActivatingPlayer(actor(game));
        cast.getTargets().add(aura);
        assertTrue(cast.canPlay() && cast.checkRestrictions(cast.getHostCard(), actor(game)));
        assertTrue(ComputerUtil.playStack(cast, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
        settle(game);
        assertTrue(target.isClassCard());
        assertEquals(target.getClassLevel(), level, "Class level remains while the permanent is not a Class");
        if (level == 2) {
            boolean reached = false;
            for (int turn = 0; turn < 4; turn++) {
                assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
                settle(game);
                if (game.getPhaseHandler().getPlayerTurn() == actor(game)) { reached = true; break; }
            }
            assertTrue(reached);
        }
        drawOutcome(game, level);
    }

    private void maskedSnapshot(int level, int depth) {
        Game original = maskedBoard(level);
        if (depth == 0) {
            restoreMasked(original, level);
            return;
        }
        Game copy = original;
        for (int index = 0; index < depth; index++) copy = new GameCopier(copy).withStack(true).makeCopy(null, actor(copy));
        restoreMasked(copy, level);
        assertTrue(enchantment(original).isLand(), "Removing an aura in one world cannot restore another world");
        assertEquals(enchantment(original).getClassLevel(), level);
        restoreMasked(original, level);
    }
    @Test public void leveltwoRetainsStateWhileTypeChangedDepth0() { maskedSnapshot(2, 0); }
    @Test public void leveltwoRetainsStateWhileTypeChangedDepth1() { maskedSnapshot(2, 1); }
    @Test public void leveltwoRetainsStateWhileTypeChangedDepth2() { maskedSnapshot(2, 2); }
    @Test public void levelthreeRetainsStateWhileTypeChangedDepth0() { maskedSnapshot(3, 0); }
    @Test public void levelthreeRetainsStateWhileTypeChangedDepth1() { maskedSnapshot(3, 1); }
    @Test public void levelthreeRetainsStateWhileTypeChangedDepth2() { maskedSnapshot(3, 2); }
}
