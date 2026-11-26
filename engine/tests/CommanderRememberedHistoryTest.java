package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.ability.AbilityFactory;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Supplied-state memory contracts; these do not assert a complete legal history. */
public class CommanderRememberedHistoryTest extends AITest {
    private final List<GameCopier> copies = new ArrayList<>();
    private static final class Scene {
        Game game;
        Player actor;
        Card source;
        Card subject;
        GameCopier copier;
        Card mapped;
    }
    private Scene scene() {
        Scene s = new Scene();
        s.game = CommanderPayloadGraphTest.newBoard();
        s.actor = s.game.getPlayers().getFirst();
        s.source = addCardToZone("Nissa's Revelation", s.actor, ZoneType.Hand);
        s.subject = addCardToZone("Grizzly Bears", s.actor, ZoneType.Battlefield);
        s.game.getAction().checkStaticAbilities(false);
        s.game.getTriggerHandler().resetActiveTriggers();
        s.game.copyLastState();
        return s;
    }
    private void copy(Scene s) {
        s.copier = new GameCopier(s.game).withStack(true);
        copies.add(s.copier);
        s.copier.makeCopy(null, s.actor);
        s.mapped = s.copier.find(s.source);
    }
    private Card detached(Scene s) {
        Card card = Card.fromPaperCard(s.subject.getPaperCard(), s.actor);
        assertNull(card.getZone());
        assertEquals(card.getNetPower(), 2);
        return card;
    }
    private Card history(Scene s) {
        Card history = CardCopyService.getLKICopy(s.subject);
        assertTrue(history.isLKI());
        assertEquals(history.getNetPower(), 2);
        s.subject.setBasePower(7);
        assertEquals(s.subject.getNetPower(), 7);
        assertEquals(history.getNetPower(), 2);
        return history;
    }
    private void gainLife(Scene s, int amount) {
        // The native expression follows current information when the live card still exists.
        // Compare an actual source-game resolution as well as the copied one.
        int originalBefore = s.actor.getLife();
        s.source.setSVar("MemoryPower", "Remembered$CardPower");
        SpellAbility originalAbility = AbilityFactory.getAbility(
                "DB$ GainLife | Defined$ You | LifeAmount$ MemoryPower", s.source);
        originalAbility.setActivatingPlayer(s.actor);
        originalAbility.resolve();
        assertEquals(s.actor.getLife(), originalBefore + amount);
        int originalAfter = s.actor.getLife();
        Player copiedActor = s.copier.find(s.actor);
        int before = copiedActor.getLife();
        s.mapped.setSVar("MemoryPower", "Remembered$CardPower");
        SpellAbility ability = AbilityFactory.getAbility(
                "DB$ GainLife | Defined$ You | LifeAmount$ MemoryPower", s.mapped);
        ability.setActivatingPlayer(copiedActor);
        ability.resolve();
        assertEquals(copiedActor.getLife(), before + amount);
        assertEquals(s.actor.getLife(), originalAfter, "Copied resolution must not affect original life");
    }
    @AfterMethod public void releaseCopies() {
        for (GameCopier copier : copies) copier.release();
        copies.clear();
    }
    @Test public void liveRememberedCardUsesCopiedIdentity() {
        Scene s = scene(); s.source.addRemembered(s.subject); copy(s);
        assertSame(s.mapped.getFirstRemembered(), s.copier.find(s.subject));
    }
    @Test public void rememberedPlayersAndScalarsSurvive() {
        Scene s = scene(); s.source.addRemembered(s.actor); s.source.addRemembered(3); copy(s);
        assertTrue(s.mapped.isRemembered(s.copier.find(s.actor)));
        assertTrue(s.mapped.isRemembered(3));
    }
    @Test public void detachedRememberedCardIsRetainedAndIsolated() {
        Scene s = scene(); Card detached = detached(s); s.source.addRemembered(detached); copy(s);
        assertEquals(s.mapped.getRememberedCount(), 1);
        Card mapped = (Card) s.mapped.getFirstRemembered();
        assertNotSame(mapped, detached);
        assertSame(mapped.getGame(), s.copier.getCopiedGame());
        assertEquals(mapped.getNetPower(), 2);
        mapped.setBasePower(9);
        assertEquals(detached.getNetPower(), 2);
    }
    @Test public void rememberedLkiDoesNotBecomeLiveCard() {
        Scene s = scene(); Card history = history(s); s.source.addRemembered(history); copy(s);
        Card mapped = (Card) s.mapped.getFirstRemembered();
        assertNotSame(mapped, s.copier.find(s.subject));
        assertNotSame(mapped, history);
        assertEquals(mapped.getNetPower(), 2);
        assertEquals(s.copier.find(s.subject).getNetPower(), 7);
    }
    @Test public void detachedMemorySuppliesNativeLifeGain() {
        Scene s = scene(); s.source.addRemembered(detached(s)); copy(s); gainLife(s, 2);
    }
    @Test public void historicalMemorySuppliesNativeLifeGain() {
        Scene s = scene(); s.source.addRemembered(history(s)); copy(s); gainLife(s, 7);
    }
    @Test public void rememberedHistorySurvivesRepeatedCopies() {
        Scene s = scene(); s.source.addRemembered(history(s)); copy(s);
        Card first = (Card) s.mapped.getFirstRemembered();
        assertNotNull(first);
        GameCopier next = new GameCopier(s.copier.getCopiedGame()).withStack(true);
        copies.add(next);
        Game repeated = next.makeCopy(null, s.copier.find(s.actor));
        Card again = (Card) next.find(s.mapped).getFirstRemembered();
        assertNotNull(again);
        assertNotSame(again, first);
        assertSame(again.getGame(), repeated);
        assertEquals(again.getNetPower(), 2);
        assertEquals(next.find(s.copier.find(s.subject)).getNetPower(), 7);
    }
    @Test public void siblingHistoricalMemoriesDoNotAlias() {
        Scene s = scene(); Card detached = detached(s); s.source.addRemembered(detached); copy(s);
        Card first = (Card) s.mapped.getFirstRemembered();
        assertNotNull(first);
        GameCopier sibling = new GameCopier(s.game).withStack(true);
        copies.add(sibling);
        sibling.makeCopy(null, s.actor);
        Card second = (Card) sibling.find(s.source).getFirstRemembered();
        assertNotNull(second);
        assertNotSame(second, first);
        first.setBasePower(9);
        assertEquals(second.getNetPower(), 2);
        assertEquals(detached.getNetPower(), 2);
    }
    @Test public void sharedHistoricalReferenceStaysSharedWithinCopy() {
        Scene s = scene(); Card history = history(s);
        Card other = addCardToZone("Mind Stone", s.actor, ZoneType.Battlefield);
        s.source.addRemembered(history); other.addRemembered(history); copy(s);
        Card first = (Card) s.mapped.getFirstRemembered();
        assertNotNull(first);
        assertSame(s.copier.find(other).getFirstRemembered(), first);
        assertNotSame(first, history);
        assertEquals(first.getNetPower(), 2);
    }
}
