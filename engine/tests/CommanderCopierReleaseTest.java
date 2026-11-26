package forge.ai;

import forge.GameCommand;
import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.cost.Cost;
import forge.game.cost.CostPayment;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilitySnapshot;
import forge.game.zone.ZoneType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Release must discard source mappings while preserving usable copied worlds. */
public class CommanderCopierReleaseTest extends AITest {
    private final List<GameCopier> copies = new ArrayList<>();
    private static final class Scene {
        Game original;
        Game copied;
        Card source;
        Card mapped;
        SpellAbility sourceAbility;
        SpellAbility mappedAbility;
        GameCopier copier;
    }
    private static Object field(GameCopier copier, String name) throws Exception {
        Field field = GameCopier.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(copier);
    }
    private Scene scene() {
        Scene s = new Scene();
        s.original = CommanderPayloadGraphTest.newBoard();
        Player actor = s.original.getPlayers().getFirst();
        s.source = addCardToZone("Llanowar Elves", actor, ZoneType.Battlefield);
        s.source.setSickness(false);
        s.original.getAction().checkStaticAbilities(false);
        s.original.getTriggerHandler().resetActiveTriggers();
        Combat combat = new Combat(actor);
        s.original.getPhaseHandler().setCombat(combat);
        combat.addAttacker(s.source, s.original.getPlayers().get(1));
        s.original.copyLastState();
        s.sourceAbility = s.source.getManaAbilities().get(0);
        s.copier = new GameCopier(s.original).withStack(true);
        copies.add(s.copier);
        s.copied = s.copier.makeCopy(null, actor);
        s.mapped = s.copier.find(s.source);
        s.mappedAbility = s.copier.find(s.sourceAbility);
        assertNotSame(s.mapped, s.source);
        assertSame(s.mappedAbility.getHostCard(), s.mapped);
        return s;
    }
    @AfterMethod public void releaseCopies() {
        for (GameCopier copier : copies) copier.release();
        copies.clear();
    }
    @Test public void releaseDiscardsAbilitySnapshot() throws Exception {
        Scene s = scene();
        assertNotNull(field(s.copier, "abilitySnapshot"));
        s.copier.release();
        assertNull(field(s.copier, "abilitySnapshot"));
    }
    @Test public void releaseStopsSourceAbilityMapping() {
        Scene s = scene();
        assertSame(s.copier.find(s.sourceAbility), s.mappedAbility);
        s.copier.release();
        try {
            s.copier.find(s.sourceAbility);
            fail("Released source abilities must not remain mappable");
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().startsWith("Couldn't map "));
        }
    }
    @Test public void releaseStopsIndependentCostMapping() {
        Scene s = scene();
        Cost cost = new Cost("1", false);
        assertNotNull(s.copier.findCost(cost));
        s.copier.release();
        try {
            s.copier.findCost(cost);
            fail("Released identity context must not map more costs");
        } catch (IllegalStateException expected) {
            assertEquals(expected.getMessage(), "Cost mapping requires a complete game copy");
        }
    }
    @Test public void releaseDiscardsCombatBandMappings() throws Exception {
        Scene s = scene();
        Map<?, ?> bands = (Map<?, ?>) field(s.copier, "combatBands");
        assertFalse(bands.isEmpty(), "The native attacking band must have been mapped");
        s.copier.release();
        assertTrue(bands.isEmpty());
        assertTrue(s.copied.getCombat().isAttacking(s.mapped));
    }
    @Test public void copiedGameRemainsUsableAfterRelease() {
        Scene s = scene();
        s.copier.release();
        assertSame(s.copier.getCopiedGame(), s.copied);
        assertSame(s.mapped.getGame(), s.copied);
        assertSame(s.mappedAbility.getHostCard(), s.mapped);
        assertTrue(s.copied.getCombat().isAttacking(s.mapped));
        GameCopier next = new GameCopier(s.copied).withStack(true);
        copies.add(next);
        Game second = next.makeCopy(null, s.mapped.getController());
        Card repeated = next.find(s.mapped);
        assertSame(repeated.getGame(), second);
        assertNotSame(repeated, s.mapped);
        assertTrue(second.getCombat().isAttacking(repeated));
        assertSame(next.find(s.mappedAbility).getHostCard(), repeated);
    }
    @Test public void releaseIsIdempotentBeforeAndAfterCopy() throws Exception {
        Scene s = scene();
        s.copier.release(); s.copier.release();
        assertSame(s.copier.getCopiedGame(), s.copied);
        GameCopier unused = new GameCopier(s.original);
        unused.release(); unused.release();
        assertNull(unused.getCopiedGame());
        assertNull(unused.getOriginalGame());
        assertTrue(((Map<?, ?>) field(unused, "combatBands")).isEmpty());
    }
    @Test public void releasePreservesImmutableUnsupportedCallbackDiagnostics() throws Exception {
        Scene s = scene();
        SpellAbilitySnapshot context = (SpellAbilitySnapshot) field(s.copier, "abilitySnapshot");
        GameCommand unknown = new GameCommand() {
            @Override public void run() { fail("Unsupported source callback must not run"); }
        };
        assertNull(context.command(unknown, "release-control"));
        List<String> before = s.copier.getUnmappedSnapshotCommands();
        assertEquals(before.size(), 1);
        s.copier.release(); s.copier.release();
        List<String> after = s.copier.getUnmappedSnapshotCommands();
        assertEquals(after, before);
        try { after.add("mutable"); fail("Diagnostics must remain immutable"); }
        catch (UnsupportedOperationException expected) { assertEquals(after, before); }
    }
    @Test public void copiedManaAbilityPaysAndResolvesAfterRelease() {
        Scene s = scene();
        Player copiedActor = s.mapped.getController();
        int before = copiedActor.getManaPool().totalMana();
        s.copier.release();
        s.mappedAbility.setActivatingPlayer(copiedActor);
        assertTrue(s.mappedAbility.canPlay());
        assertTrue(new CostPayment(s.mappedAbility.getPayCosts(), s.mappedAbility)
                .payComputerCosts(new AiCostDecision(copiedActor, s.mappedAbility, false, true)));
        s.copied.getStack().addAndUnfreeze(s.mappedAbility);
        assertTrue(s.mapped.isTapped());
        assertEquals(copiedActor.getManaPool().totalMana(), before + 1);
        assertFalse(s.source.isTapped());
        assertEquals(s.source.getController().getManaPool().totalMana(), 0);
    }
}
