package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.GameObject;
import forge.game.IEntityMap;
import forge.game.ability.ApiType;
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

/** Paid transitions distinguish exert restrictions from ordinary copiable state. */
public class CommanderExertTransitionsTest extends ExertTestSupport {
    private Player seat(Game game, int index) {
        return game.getPlayers().stream().filter(player -> player.getName().equals("exert-" + index))
                .findFirst().orElseThrow();
    }

    private Card controlledArena(Game game, int controller) {
        return seat(game, controller).getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Arena of Glory")).findFirst().orElseThrow();
    }

    private void fundBlue(Game game) {
        for (int index = 0; index < 8; index++) addCardToZone("Island", actor(game), ZoneType.Battlefield);
    }

    private Game snapshot(Game game, int depth) {
        for (int index = 0; index < depth; index++) {
            game = new GameCopier(game).withStack(true).makeCopy(null, actor(game));
        }
        return game;
    }

    private void targetCast(Game game, String name, Card target, int mana) {
        SpellAbility ability = addCardToZone(name, actor(game), ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(actor(game));
        ability.getTargets().add(target);
        assertTrue(ability.canPlay() && ability.checkRestrictions(ability.getHostCard(), actor(game)));
        assertTrue(ComputerUtil.playStack(ability, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), mana);
        settle(game);
    }

    private void history(int depth) {
        Game original = paid();
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
        Card source = arena(original);
        Card historical = CardCopyService.getLKICopyForGame(source, map);
        assertNotSame(historical, source);
        assertSame(historical.getGame(), target);
        assertSame(historical.getOwner(), actor(target));
        assertEquals(historical.getExertedThisTurn(), 1);
        assertTrue(historical.isExertedBy(actor(target)));
        assertTrue(actor(target).getCardsIn(ZoneType.Battlefield).stream().noneMatch(card -> card == historical));
        historical.removeExertedBy(actor(target));
        assertFalse(historical.isExertedBy(actor(target)));
        checkState(original);
        checkState(target);
    }

    private void castSecondCreature(Game game) {
        Card land = arena(game);
        SpellAbility mana = land.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.Mana && sa.hasParam("AddsKeywords"))
                .findFirst().orElseThrow();
        mana.setActivatingPlayer(actor(game));
        assertTrue(mana.canPlay());
        assertTrue(ComputerUtil.playNoStack(actor(game), mana, game, false));
        Card dragon = addCardToZone("Furnace Whelp", actor(game), ZoneType.Hand);
        SpellAbility spell = dragon.getFirstSpellAbility();
        spell.setActivatingPlayer(actor(game));
        assertTrue(spell.canPlay());
        assertTrue(ComputerUtil.playStack(spell, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 4);
        assertTrue(game.getStack().peekAbility().getPayingMana().stream()
                .anyMatch(value -> value.getSourceCard().getId() == land.getId()));
        settle(game);
        assertEquals(actor(game).getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Furnace Whelp") && card.hasKeyword("Haste")).count(), 2L);
    }

    private void repeated(int depth) {
        Game original = paid();
        fundBlue(original);
        targetCast(original, "Twiddle", arena(original), 1);
        assertFalse(arena(original).isTapped());
        assertTrue(arena(original).isExertedBy(actor(original)));
        castSecondCreature(original);
        assertEquals(arena(original).getExertedThisTurn(), 2);
        Game game = snapshot(original, depth);
        assertEquals(arena(game).getExertedThisTurn(), 2);
        assertTrue(arena(game).isExertedBy(actor(game)));
        checkUntaps(game);
        if (depth > 0) {
            assertEquals(arena(original).getExertedThisTurn(), 2);
            assertTrue(arena(original).isExertedBy(actor(original)));
            assertTrue(arena(original).isTapped());
            checkUntaps(original);
        }
    }

    private void untapEffect(int depth) {
        Game original = paid();
        fundBlue(original);
        Game game = snapshot(original, depth);
        checkState(game);
        targetCast(game, "Twiddle", arena(game), 1);
        assertFalse(arena(game).isTapped(), "An untap spell may untap an exerted permanent");
        assertTrue(arena(game).isExertedBy(actor(game)), "The spell does not consume the next-untap restriction");
        SpellAbility mana = arena(game).getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.Mana && !sa.hasParam("AddsKeywords"))
                .findFirst().orElseThrow();
        mana.setActivatingPlayer(actor(game));
        assertTrue(mana.canPlay());
        assertTrue(ComputerUtil.playNoStack(actor(game), mana, game, false));
        checkState(game);
        checkUntaps(game);
        if (depth > 0) checkState(original);
    }

    private void reentry(int depth) {
        Game original = paid();
        fundBlue(original);
        Game game = snapshot(original, depth);
        checkState(game);
        targetCast(game, "Boomerang", arena(game), 2);
        Card returned = actor(game).getCardsIn(ZoneType.Hand).stream()
                .filter(card -> card.getName().equals("Arena of Glory")).findFirst().orElseThrow();
        assertTrue(actor(game).canPlayLand(returned, false, null));
        Card fresh = actor(game).playLand(returned, null);
        settle(game);
        assertSame(fresh, arena(game));
        assertEquals(fresh.getExertedThisTurn(), 0);
        assertFalse(fresh.isExertedBy(actor(game)), "A new object cannot inherit the previous exert restriction");
        SpellAbility mana = fresh.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.Mana && !sa.hasParam("AddsKeywords"))
                .findFirst().orElseThrow();
        mana.setActivatingPlayer(actor(game));
        assertTrue(mana.canPlay());
        assertTrue(ComputerUtil.playNoStack(actor(game), mana, game, false));
        assertTrue(fresh.isTapped());
        nextOwnerMain(game);
        assertFalse(arena(game).isTapped(), "The new permanent untaps at the next owner untap");
        if (depth > 0) checkState(original);
    }

    private void donated(int depth) {
        Game original = paid();
        fundBlue(original);
        Card source = arena(original);
        SpellAbility ability = addCardToZone("Donate", actor(original), ZoneType.Hand).getFirstSpellAbility();
        ability.setActivatingPlayer(actor(original));
        ability.getTargets().add(seat(original, 1));
        ability.getSubAbility().getTargets().add(source);
        assertTrue(ability.canPlay() && ability.checkRestrictions(ability.getHostCard(), actor(original)));
        assertTrue(ComputerUtil.playStack(ability, actor(original), original));
        assertEquals(original.getStack().peekAbility().getPayingMana().size(), 3);
        settle(original);
        assertSame(source.getController(), seat(original, 1));
        assertTrue(source.isExertedBy(actor(original)));
        assertFalse(source.isExertedBy(seat(original, 1)));
        Game game = snapshot(original, depth);
        Card mapped = controlledArena(game, 1);
        assertEquals(mapped.getExertedThisTurn(), 1);
        assertSame(mapped.getOwner(), actor(game));
        assertTrue(mapped.isExertedBy(actor(game)));
        assertFalse(mapped.isExertedBy(seat(game, 1)));
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
        settle(game);
        assertSame(game.getPhaseHandler().getPlayerTurn(), seat(game, 1));
        assertFalse(mapped.isTapped(), "A different controller's untap is not the exerting player's next untap");
        assertTrue(mapped.isExertedBy(actor(game)));
        nextOwnerMain(game);
        assertFalse(mapped.isExertedBy(actor(game)), "The exerting player's untap expires the restriction despite the control change");
        if (depth > 0) {
            assertTrue(source.isTapped());
            assertTrue(source.isExertedBy(actor(original)));
        }
    }

    private void ordinaryCopy(int depth) {
        Game original = paid();
        Game game = snapshot(original, depth);
        checkState(game);
        Player other = seat(game, 1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, other, 6);
        SpellAbility cast = addCardToZone("Mirage Mirror", other, ZoneType.Hand).getFirstSpellAbility();
        cast.setActivatingPlayer(other);
        assertTrue(cast.canPlay() && cast.checkRestrictions(cast.getHostCard(), other));
        assertTrue(ComputerUtil.playStack(cast, other, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        settle(game);
        Card mirror = other.getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Mirage Mirror")).findFirst().orElseThrow();
        SpellAbility copy = mirror.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.Clone).findFirst().orElseThrow();
        copy.setActivatingPlayer(other);
        copy.getTargets().add(arena(game));
        assertTrue(copy.canPlay());
        assertTrue(ComputerUtil.playStack(copy, other, game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 2);
        settle(game);
        assertEquals(mirror.getName(), "Arena of Glory");
        assertEquals(mirror.getExertedThisTurn(), 0);
        assertFalse(mirror.isExertedBy(actor(game)));
        assertFalse(mirror.isExertedBy(other), "An ordinary copy does not inherit an exert restriction");
        checkState(game);
    }
    @Test public void historyDepth0() { history(0); }
    @Test public void historyDepth1() { history(1); }
    @Test public void historyDepth2() { history(2); }
    @Test public void repeatedDepth0() { repeated(0); }
    @Test public void repeatedDepth1() { repeated(1); }
    @Test public void repeatedDepth2() { repeated(2); }
    @Test public void untapEffectDepth0() { untapEffect(0); }
    @Test public void untapEffectDepth1() { untapEffect(1); }
    @Test public void untapEffectDepth2() { untapEffect(2); }
    @Test public void reentryDepth0() { reentry(0); }
    @Test public void reentryDepth1() { reentry(1); }
    @Test public void reentryDepth2() { reentry(2); }
    @Test public void donatedDepth0() { donated(0); }
    @Test public void donatedDepth1() { donated(1); }
    @Test public void donatedDepth2() { donated(2); }
    @Test public void ordinaryCopyDepth0() { ordinaryCopy(0); }
    @Test public void ordinaryCopyDepth1() { ordinaryCopy(1); }
    @Test public void ordinaryCopyDepth2() { ordinaryCopy(2); }

    private Card survivors(Game game) {
        return actor(game).getCardsIn(ZoneType.Battlefield).stream()
                .filter(card -> card.getName().equals("Resolute Survivors")).findFirst().orElseThrow();
    }

    private void creatureUntaps(Game game) {
        nextOwnerMain(game);
        assertTrue(survivors(game).isTapped(), "The exerted attacker skips its first owner untap");
        assertFalse(survivors(game).isExertedBy(actor(game)));
        nextOwnerMain(game);
        assertFalse(survivors(game).isTapped(), "The attack exertion expires at the first untap, not the second");
    }

    private Game paidAttack() {
        Game game = paid();
        for (int index = 0; index < 4; index++) addCardToZone("Plains", actor(game), ZoneType.Battlefield);
        SpellAbility cast = addCardToZone("Resolute Survivors", actor(game), ZoneType.Hand).getFirstSpellAbility();
        cast.setActivatingPlayer(actor(game));
        assertTrue(cast.canPlay() && cast.checkRestrictions(cast.getHostCard(), actor(game)));
        assertTrue(ComputerUtil.playStack(cast, actor(game), game));
        assertEquals(game.getStack().peekAbility().getPayingMana().size(), 3);
        settle(game);
        targetCast(game, "Expedite", survivors(game), 1);
        Card attacker = survivors(game);
        assertTrue(attacker.hasKeyword("Haste"));
        assertTrue(forge.game.combat.CombatUtil.canAttack(attacker, seat(game, 1)));
        // Supply a legal declaration and exert choice, then use native combat-cost payment.
        game.getPhaseHandler().devModeSet(PhaseType.COMBAT_DECLARE_ATTACKERS, actor(game), false);
        forge.game.combat.Combat combat = new forge.game.combat.Combat(actor(game));
        game.getPhaseHandler().setCombat(combat);
        combat.addAttacker(attacker, seat(game, 1));
        assertTrue(forge.game.combat.CombatUtil.validateAttackers(combat));
        forge.game.cost.Cost cost = forge.game.combat.CombatUtil.getAttackCost(game, attacker, seat(game, 1), List.of(attacker));
        assertNotNull(cost);
        assertTrue(cost.getCostParts().stream().anyMatch(forge.game.cost.CostExert.class::isInstance));
        assertTrue(forge.game.combat.CombatUtil.checkPropagandaEffects(game, attacker, combat, List.of(attacker)));
        assertTrue(attacker.tap(true, null, null));
        settle(game);
        assertEquals(attacker.getExertedThisTurn(), 1);
        assertTrue(attacker.isExertedBy(actor(game)));
        assertEquals(actor(game).getLife(), 101, "The printed exert trigger gains exactly one life");
        for (int index = 1; index < 4; index++) assertEquals(seat(game, index).getLife(), 99);
        assertTrue(game.getPhaseHandler().devAdvanceToPhase(PhaseType.MAIN2, () -> settle(game)));
        settle(game);
        assertEquals(seat(game, 1).getLife(), 96, "The declared unblocked attacker deals native combat damage");
        assertEquals(actor(game).getLife(), 101);
        game.copyLastState();
        return game;
    }

    private void attackExert(int depth, boolean copyFirst) {
        Game original = paidAttack();
        Game copy = snapshot(original, depth);
        settle(copy);
        assertEquals(survivors(copy).getExertedThisTurn(), 1);
        assertTrue(survivors(copy).isExertedBy(actor(copy)));
        assertEquals(actor(copy).getLife(), 101, "A snapshot must not perform exert again or duplicate its trigger");
        assertEquals(actor(original).getLife(), 101);
        assertEquals(seat(copy, 1).getLife(), 96);
        assertEquals(seat(copy, 2).getLife(), 99);
        assertEquals(seat(copy, 3).getLife(), 99);
        Game first = copyFirst ? copy : original;
        Game other = copyFirst ? original : copy;
        creatureUntaps(first);
        if (depth > 0) {
            assertEquals(survivors(other).getExertedThisTurn(), 1);
            assertTrue(survivors(other).isExertedBy(actor(other)));
            assertTrue(survivors(other).isTapped());
            creatureUntaps(other);
        }
    }
    @Test public void attackExertOriginal() { attackExert(0, false); }
    @Test public void attackExertDepth1OriginalFirst() { attackExert(1, false); }
    @Test public void attackExertDepth1CopyFirst() { attackExert(1, true); }
    @Test public void attackExertDepth2OriginalFirst() { attackExert(2, false); }
    @Test public void attackExertDepth2CopyFirst() { attackExert(2, true); }

    private void twoPlayersExpire(Game game) {
        Card land = controlledArena(game, 1);
        assertTrue(land.isExertedBy(actor(game)));
        assertTrue(land.isExertedBy(seat(game, 1)));
        nextOwnerMain(game);
        assertFalse(land.isExertedBy(actor(game)), "The first exerting player's untap expires only that restriction");
        assertTrue(land.isExertedBy(seat(game, 1)));
        assertTrue(land.isTapped());
        assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
        settle(game);
        assertSame(game.getPhaseHandler().getPlayerTurn(), seat(game, 1));
        assertTrue(land.isTapped(), "The current controller also exerted it, so this untap skips it");
        assertFalse(land.isExertedBy(actor(game)));
        assertFalse(land.isExertedBy(seat(game, 1)));
        for (int turn = 0; turn < 4; turn++) {
            assertTrue(game.getPhaseHandler().devAdvanceThroughTurnEnd(PhaseType.MAIN1, () -> settle(game)));
            settle(game);
        }
        assertSame(game.getPhaseHandler().getPlayerTurn(), seat(game, 1));
        assertFalse(land.isTapped(), "Once both restrictions expire the next controller untap works");
    }

    private void twoPlayers(int depth) {
        Game original = paid();
        fundBlue(original);
        Card source = arena(original);
        SpellAbility donate = addCardToZone("Donate", actor(original), ZoneType.Hand).getFirstSpellAbility();
        donate.setActivatingPlayer(actor(original));
        donate.getTargets().add(seat(original, 1));
        donate.getSubAbility().getTargets().add(source);
        assertTrue(donate.canPlay() && donate.checkRestrictions(donate.getHostCard(), actor(original)));
        assertTrue(ComputerUtil.playStack(donate, actor(original), original));
        assertEquals(original.getStack().peekAbility().getPayingMana().size(), 3);
        settle(original);
        Player recipient = seat(original, 1);
        addCardToZone("Island", recipient, ZoneType.Battlefield);
        original.getPhaseHandler().devModeSet(PhaseType.MAIN1, recipient, 6);
        SpellAbility untap = addCardToZone("Twiddle", recipient, ZoneType.Hand).getFirstSpellAbility();
        untap.setActivatingPlayer(recipient);
        untap.getTargets().add(source);
        assertTrue(untap.canPlay() && untap.checkRestrictions(untap.getHostCard(), recipient));
        assertTrue(ComputerUtil.playStack(untap, recipient, original));
        assertEquals(original.getStack().peekAbility().getPayingMana().size(), 1);
        settle(original);
        assertFalse(source.isTapped());
        SpellAbility mana = source.getSpellAbilities().stream()
                .filter(sa -> sa.isActivatedAbility() && sa.getApi() == ApiType.Mana && sa.hasParam("AddsKeywords"))
                .findFirst().orElseThrow();
        mana.setActivatingPlayer(recipient);
        assertTrue(mana.canPlay());
        assertTrue(ComputerUtil.playNoStack(recipient, mana, original, false));
        SpellAbility cast = addCardToZone("Furnace Whelp", recipient, ZoneType.Hand).getFirstSpellAbility();
        cast.setActivatingPlayer(recipient);
        assertTrue(cast.canPlay());
        assertTrue(ComputerUtil.playStack(cast, recipient, original));
        assertEquals(original.getStack().peekAbility().getPayingMana().size(), 4);
        assertTrue(original.getStack().peekAbility().getPayingMana().stream()
                .anyMatch(value -> value.getSourceCard().getId() == source.getId()));
        settle(original);
        assertEquals(source.getExertedThisTurn(), 2);
        assertTrue(source.isExertedBy(actor(original)));
        assertTrue(source.isExertedBy(recipient));
        Game game = snapshot(original, depth);
        Card mapped = controlledArena(game, 1);
        assertEquals(mapped.getExertedThisTurn(), 2);
        assertTrue(mapped.isExertedBy(actor(game)));
        assertTrue(mapped.isExertedBy(seat(game, 1)));
        twoPlayersExpire(game);
        if (depth > 0) {
            assertTrue(source.isTapped());
            assertTrue(source.isExertedBy(actor(original)));
            assertTrue(source.isExertedBy(recipient));
            twoPlayersExpire(original);
        }
    }
    @Test public void twoExertingPlayersOriginal() { twoPlayers(0); }
    @Test public void twoExertingPlayersDepth1() { twoPlayers(1); }
    @Test public void twoExertingPlayersDepth2() { twoPlayers(2); }
}
