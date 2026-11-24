package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.ai.simulation.GameSimulator;
import forge.game.Game;
import forge.game.GameObject;
import forge.game.IEntityMap;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Printed phase outcomes and distinctions between snapshots and new permanents. */
public class CommanderHarnessedTransitionsTest extends HarnessedTestSupport {
    private void settle(Game game) {
        GameSimulator.resolveStack(game, actor(game));
    }

    private Card named(Game game, String name, ZoneType zone) {
        return actor(game).getCardsIn(zone).stream().filter(card -> card.getName().equals(name))
                .findFirst().orElseThrow();
    }

    private Game outcomeBoard(boolean soul) {
        Game game = paid(soul);
        addCardToZone(soul ? "Colossal Dreadmaw" : "Wall of Omens", actor(game),
                soul ? ZoneType.Graveyard : ZoneType.Battlefield);
        game.copyLastState();
        return game;
    }

    private void phaseOutcome(Game game, boolean soul) {
        int hand = actor(game).getCardsIn(ZoneType.Hand).size();
        long timestamp = soul ? -1 : named(game, "Wall of Omens", ZoneType.Battlefield).getGameTimestamp();
        int turns = soul ? 4 : 1;
        for (int index = 0; index < turns; index++) {
            assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
            settle(game);
        }
        if (soul) {
            assertSame(named(game, "Colossal Dreadmaw", ZoneType.Battlefield).getController(), actor(game));
            assertTrue(actor(game).getCardsIn(ZoneType.Graveyard).isEmpty());
        } else {
            assertNotEquals(named(game, "Wall of Omens", ZoneType.Battlefield).getGameTimestamp(), timestamp,
                    "The printed end-step ability actually blinks the other permanent");
            assertEquals(actor(game).getCardsIn(ZoneType.Hand).size(), hand + 1,
                    "The returned Wall's printed entry trigger draws one card");
        }
    }

    private void pairedOutcome(boolean soul, int depth, boolean copyFirst) {
        Game original = outcomeBoard(soul);
        Game copy = original;
        for (int index = 0; index < depth; index++) {
            copy = new GameCopier(copy).withStack(true).makeCopy(null, actor(copy));
        }
        Game first = copyFirst ? copy : original;
        Game other = copyFirst ? original : copy;
        int otherHand = actor(other).getCardsIn(ZoneType.Hand).size();
        long otherTimestamp = soul ? -1 : named(other, "Wall of Omens", ZoneType.Battlefield).getGameTimestamp();
        phaseOutcome(first, soul);
        assertEquals(actor(other).getCardsIn(ZoneType.Hand).size(), otherHand);
        if (soul) assertTrue(actor(other).getCardsIn(ZoneType.Graveyard).stream()
                .anyMatch(card -> card.getName().equals("Colossal Dreadmaw")));
        else assertEquals(named(other, "Wall of Omens", ZoneType.Battlefield).getGameTimestamp(), otherTimestamp);
        phaseOutcome(other, soul);
    }

    private void history(boolean soul, int depth) {
        Game original = paid(soul);
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
        Card historical = CardCopyService.getLKICopyForGame(stone(original, soul), map);
        assertSame(historical.getGame(), target);
        assertSame(historical.getOwner(), actor(target));
        assertTrue(historical.isHarnessed());
        assertTrue(actor(target).getCardsIn(ZoneType.Battlefield).stream().noneMatch(card -> card == historical));
        historical.setHarnessed(false);
        assertTrue(stone(original, soul).isHarnessed());
        assertTrue(stone(target, soul).isHarnessed());
    }

    private void ordinaryCopy(boolean soul) {
        Game game = paid(soul);
        Player other = game.getPlayers().stream().filter(player -> player.getName().equals("harness-1"))
                .findFirst().orElseThrow();
        Card spell = addCardToZone("Sculpting Steel", other, ZoneType.Hand);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, other, 6);
        SpellAbility cast = spell.getFirstSpellAbility();
        cast.setActivatingPlayer(other);
        assertTrue(cast.canPlay() && cast.checkRestrictions(cast.getHostCard(), other));
        assertTrue(ComputerUtil.playStack(cast, other, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        settle(game);
        Card copied = other.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals(stone(game, soul).getName())).findFirst().orElseThrow();
        assertFalse(copied.isHarnessed(), "An actual copy permanent does not inherit the designation");
        assertTrue(stone(game, soul).isHarnessed());
    }

    private void recast(Game game, boolean soul) {
        game.getAction().moveToHand(stone(game, soul), null);
        settle(game);
        Card card = named(game, soul ? "The Soul Stone" : "The Mind Stone", ZoneType.Hand);
        SpellAbility cast = card.getFirstSpellAbility();
        cast.setActivatingPlayer(actor(game));
        assertTrue(cast.canPlay() && cast.checkRestrictions(cast.getHostCard(), actor(game)));
        assertTrue(ComputerUtil.playStack(cast, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
        settle(game);
        assertFalse(stone(game, soul).isHarnessed(), "Zone re-entry produces a new unharnessed permanent");
    }

    private void independentReentry(boolean soul) {
        Game original = paid(soul);
        Game copy = new GameCopier(original).withStack(true).makeCopy(null, actor(original));
        copy = new GameCopier(copy).withStack(true).makeCopy(null, actor(copy));
        recast(copy, soul);
        assertTrue(stone(original, soul).isHarnessed());
        recast(original, soul);
    }

    private void donated(boolean soul) {
        Game game = paid(soul);
        Player recipient = game.getPlayers().stream().filter(player -> player.getName().equals("harness-1"))
                .findFirst().orElseThrow();
        for (int index = 0; index < 3; index++) addCardToZone("Island", actor(game), ZoneType.Battlefield);
        Card source = stone(game, soul);
        Card spell = addCardToZone("Donate", actor(game), ZoneType.Hand);
        SpellAbility cast = spell.getFirstSpellAbility();
        cast.setActivatingPlayer(actor(game));
        cast.getTargets().add(recipient);
        cast.getSubAbility().getTargets().add(source);
        assertTrue(cast.canPlay() && cast.checkRestrictions(cast.getHostCard(), actor(game)));
        assertTrue(ComputerUtil.playStack(cast, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        settle(game);
        assertSame(source.getController(), recipient);
        assertTrue(source.isHarnessed(), "Changing control retains the designation");
        Game current = game;
        for (int index = 0; index < 2; index++) current = new GameCopier(current).withStack(true).makeCopy(null, actor(current));
        final Game target = current;
        Player copiedRecipient = target.getPlayers().stream().filter(player -> player.getName().equals("harness-1"))
                .findFirst().orElseThrow();
        Card copied = copiedRecipient.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals(source.getName())).findFirst().orElseThrow();
        assertTrue(copied.isHarnessed());
        assertSame(copied.getOwner(), actor(target));
        assertSame(copied.getController(), copiedRecipient);
    }
    @Test public void originalMindPrintedPhaseOutcome() { phaseOutcome(outcomeBoard(false), false); }
    @Test public void mindPhaseDepth1CopyFirstFalse() { pairedOutcome(false, 1, false); }
    @Test public void mindPhaseDepth1CopyFirstTrue() { pairedOutcome(false, 1, true); }
    @Test public void mindPhaseDepth2CopyFirstFalse() { pairedOutcome(false, 2, false); }
    @Test public void mindPhaseDepth2CopyFirstTrue() { pairedOutcome(false, 2, true); }
    @Test public void mindPrivateHistoryDepth1() { history(false, 1); }
    @Test public void mindPrivateHistoryDepth2() { history(false, 2); }
    @Test public void mindOrdinaryCopyStartsUnharnessed() { ordinaryCopy(false); }
    @Test public void mindIndependentPaidReentryResetsDesignation() { independentReentry(false); }
    @Test public void mindPaidControlChangeRetainsDesignation() { donated(false); }
    @Test public void originalSoulPrintedPhaseOutcome() { phaseOutcome(outcomeBoard(true), true); }
    @Test public void soulPhaseDepth1CopyFirstFalse() { pairedOutcome(true, 1, false); }
    @Test public void soulPhaseDepth1CopyFirstTrue() { pairedOutcome(true, 1, true); }
    @Test public void soulPhaseDepth2CopyFirstFalse() { pairedOutcome(true, 2, false); }
    @Test public void soulPhaseDepth2CopyFirstTrue() { pairedOutcome(true, 2, true); }
    @Test public void soulPrivateHistoryDepth1() { history(true, 1); }
    @Test public void soulPrivateHistoryDepth2() { history(true, 2); }
    @Test public void soulOrdinaryCopyStartsUnharnessed() { ordinaryCopy(true); }
    @Test public void soulIndependentPaidReentryResetsDesignation() { independentReentry(true); }
    @Test public void soulPaidControlChangeRetainsDesignation() { donated(true); }

    private Game maskedBoard(boolean soul) {
        Game game = outcomeBoard(soul);
        Player other = game.getPlayers().stream().filter(player -> player.getName().equals("harness-1"))
                .findFirst().orElseThrow();
        for (int index = 0; index < 3; index++) addCardToZone("Forest", other, ZoneType.Battlefield);
        for (int index = 0; index < 2; index++) addCardToZone("Forest", actor(game), ZoneType.Battlefield);
        Card target = stone(game, soul);
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
        assertFalse(target.isArtifact(), "A type-changing effect suppresses the Stone abilities");
        assertTrue(target.isHarnessed(), "The designation remains on a permanent with a different type");
        addCardToZone("Naturalize", actor(game), ZoneType.Hand);
        game.copyLastState();
        return game;
    }

    private void restoreMasked(Game game, boolean soul) {
        Card target = stone(game, soul);
        assertTrue(target.isLand());
        assertFalse(target.isArtifact(), "A type-changing effect suppresses the Stone abilities");
        assertTrue(target.isHarnessed(), "The designation remains on a permanent with a different type");
        Player other = game.getPlayers().stream().filter(player -> player.getName().equals("harness-1"))
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
        assertTrue(target.isArtifact());
        assertTrue(target.isHarnessed(), "The designation remains on a permanent with a different type");
        boolean reached = false;
        for (int turn = 0; turn < 4; turn++) {
            assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
            settle(game);
            if (game.getPhaseHandler().getPlayerTurn() == actor(game)) { reached = true; break; }
        }
        assertTrue(reached);
        if (soul) assertNotNull(named(game, "Colossal Dreadmaw", ZoneType.Battlefield));
        else phaseOutcome(game, false);
    }

    private void maskedSnapshot(boolean soul, int depth) {
        Game original = maskedBoard(soul);
        if (depth == 0) {
            restoreMasked(original, soul);
            return;
        }
        Game copy = original;
        for (int index = 0; index < depth; index++) copy = new GameCopier(copy).withStack(true).makeCopy(null, actor(copy));
        restoreMasked(copy, soul);
        assertTrue(stone(original, soul).isLand(), "Removing an aura in one world cannot restore another world");
        assertTrue(stone(original, soul).isHarnessed());
        restoreMasked(original, soul);
    }
    @Test public void mindRetainsStateWhileTypeChangedDepth0() { maskedSnapshot(false, 0); }
    @Test public void mindRetainsStateWhileTypeChangedDepth1() { maskedSnapshot(false, 1); }
    @Test public void mindRetainsStateWhileTypeChangedDepth2() { maskedSnapshot(false, 2); }
    @Test public void soulRetainsStateWhileTypeChangedDepth0() { maskedSnapshot(true, 0); }
    @Test public void soulRetainsStateWhileTypeChangedDepth1() { maskedSnapshot(true, 1); }
    @Test public void soulRetainsStateWhileTypeChangedDepth2() { maskedSnapshot(true, 2); }
}
