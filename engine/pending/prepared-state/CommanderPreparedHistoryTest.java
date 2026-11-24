package forge.ai;

import forge.ai.simulation.GameCopier;
import forge.game.Game;
import forge.game.GameObject;
import forge.game.IEntityMap;
import forge.game.card.Card;
import forge.game.card.CardCopyService;
import forge.game.zone.ZoneType;
import java.util.ArrayList;
import java.util.List;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Historical prepared links belong to the target world and have private memories. */
public class CommanderPreparedHistoryTest extends PreparedTestSupport {
    private void historical(int depth) {
        Game original = prepared();
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
        Card history = CardCopyService.getLKICopyForGame(creature(original), map);
        assertNotSame(history, creature(original));
        assertSame(history.getGame(), target);
        assertTrue(history.isPrepared());
        Card effect = history.getPrepared();
        Card spell = history.getPreparedSpell();
        assertNotNull(effect);
        assertNotNull(spell);
        assertSame(effect.getGame(), target, "Historical prepared effect is rebound");
        assertSame(spell.getGame(), target, "Historical prepared spell is rebound");
        assertSame(effect.getOwner(), actor(target));
        assertSame(spell.getOwner(), actor(target));
        assertEquals(spell.getName(), "Stream of Life");
        assertTrue(actor(target).getCardsIn(ZoneType.Command).stream().noneMatch(card -> card == effect));
        assertTrue(actor(target).getCardsIn(ZoneType.Exile).stream().noneMatch(card -> card == spell));
        assertNotNull(creature(original).getCastFrom(), "The actual paid creature has a cast origin");
        assertNotNull(history.getCastFrom());
        assertEquals(history.getCastFrom().getZoneType(), creature(original).getCastFrom().getZoneType());
        assertSame(history.getCastFrom().getPlayer(), actor(target), "Historical cast-origin owner belongs to the target world");
        effect.clearRemembered();
        check(original);
        check(target);
        assertNotNull(creature(original).getPreparedSpell(), "Historical memory mutation cannot clear original preparation");
    }

    @Test public void historyIntoOneCopyHasPrivatePreparedLinks() { historical(1); }
    @Test public void historyIntoTwoCopiesHasPrivatePreparedLinks() { historical(2); }
}
