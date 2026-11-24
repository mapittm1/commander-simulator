package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.GameActionUtil;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Actual prepared spell play options, mana, consumption and natural re-preparation. */
public class CommanderPreparedCastTest extends PreparedTestSupport {
    private void castPrepared(Game game) {
        check(game);
        Card healer = creature(game);
        Card effect = healer.getPrepared();
        Card preparedSpell = healer.getPreparedSpell();
        SpellAbility base = preparedSpell.getFirstSpellAbility();
        base.setActivatingPlayer(actor(game));
        SpellAbility selected = null;
        for (SpellAbility option : GameActionUtil.getAlternativeCosts(base, actor(game), false)) {
            option.setActivatingPlayer(actor(game));
            option.setXManaCostPaid(2);
            if (option.canTarget(actor(game))) option.getTargets().add(actor(game));
            if (option.canPlay() && option.checkRestrictions(option.getHostCard(), actor(game))
                    && ComputerUtilCost.canPayCost(option, actor(game), false)) {
                selected = option;
                break;
            }
        }
        assertNotNull(selected, "Use the native permission-granting play option from exile");
        int life = actor(game).getLife();
        assertTrue(ComputerUtil.playStack(selected, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3, "Actually pay X=2 plus green");
        settle(game);
        assertEquals(actor(game).getLife(), life + 2, "Resolve the printed Stream of Life");
        assertFalse(healer.isPrepared(), "Casting the prepared spell consumes preparation");
        assertNull(healer.getPreparedSpell());
        assertTrue(actor(game).getCardsIn(ZoneType.Command).stream().noneMatch(card -> card == effect));
        assertTrue(actor(game).getCardsIn(ZoneType.Exile).stream().noneMatch(card -> card == preparedSpell));
    }

    private void reprepare(Game game) {
        int oldEffect = creature(game).getPrepared().getId();
        castPrepared(game);
        game.getAction().moveToHand(creature(game), null);
        settle(game);
        boolean reached = false;
        for (int turn = 0; turn < 5; turn++) {
            assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
            settle(game);
            if (game.getPhaseHandler().getPlayerTurn() == actor(game)) {
                reached = true;
                break;
            }
        }
        assertTrue(reached, "Advance through actual untap into the caster's next turn");
        Card healer = actor(game).getCardsIn(ZoneType.Hand).stream().filter(card -> card.getName().equals("Infirmary Healer")).findFirst().orElseThrow();
        SpellAbility action = healer.getFirstSpellAbility();
        action.setActivatingPlayer(actor(game));
        assertTrue(action.canPlay() && action.checkRestrictions(action.getHostCard(), actor(game)));
        assertTrue(ComputerUtil.playStack(action, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
        settle(game);
        check(game);
        assertNotEquals(creature(game).getPrepared().getId(), oldEffect, "A fresh entry creates a fresh effect");
        castPrepared(game);
        assertEquals(actor(game).getLife(), 104);
    }

    private void copiedCast(int depth, boolean copyFirst, boolean repeat) {
        Game original = prepared();
        Game copy = original;
        for (int index = 0; index < depth; index++) copy = new GameCopier(copy).withStack(true).makeCopy(null, actor(copy));
        check(copy);
        if (repeat) {
            reprepare(copy);
            check(original);
            assertEquals(actor(original).getLife(), 100);
            return;
        }
        Game first = copyFirst ? copy : original;
        Game other = copyFirst ? original : copy;
        castPrepared(first);
        check(other);
        assertEquals(actor(other).getLife(), 100, "A prepared spell changes only its own world's life total");
        castPrepared(other);
    }

    @Test public void originalPaidPreparedSpell() { castPrepared(prepared()); }
    @Test public void originalCanPrepareAgainAfterNormalReentry() { reprepare(prepared()); }
    @Test public void oneCopyCastsBeforeOriginal() { copiedCast(1, true, false); }
    @Test public void twoCopiesCastBeforeOriginal() { copiedCast(2, true, false); }
    @Test public void originalCastsBeforeOneCopy() { copiedCast(1, false, false); }
    @Test public void originalCastsBeforeTwoCopies() { copiedCast(2, false, false); }
    @Test public void oneCopyCanPrepareAgainIndependently() { copiedCast(1, true, true); }
    @Test public void twoCopiesCanPrepareAgainIndependently() { copiedCast(2, true, true); }
}
