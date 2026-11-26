package forge.ai;

import forge.game.Game;
import forge.game.card.CardCollectionView;
import forge.game.zone.ZoneType;
import java.lang.management.ManagementFactory;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Escaping allocation counts for fixed supplied worlds; no elapsed-speed claim. */
public class CommanderEnumCardScanAllocationTest extends AITest {
    private static volatile CardCollectionView sink;
    private static final Map<String, Long> BYTES = new LinkedHashMap<>();
    public static Map<String, Long> allocationBytes() { return new LinkedHashMap<>(BYTES); }

    @Test public void fixedEscapingQueriesReportAllocatedBytes() {
        com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        for (int count : new int[]{0, 8, 80, 400}) {
            Game game = CommanderEnumCardScanCapacityTest.newBoard();
            for (int index = 0; index < count; index++) {
                addCardToZone("Forest", game.getPlayers().get(index % 4), ZoneType.Battlefield);
            }
            EnumSet<ZoneType> zones = EnumSet.copyOf(ZoneType.STATIC_ABILITIES_SOURCE_ZONES);
            for (int index = 0; index < 1000; index++) sink = game.getCardsIn(zones);
            long before = bean.getThreadAllocatedBytes(thread);
            for (int index = 0; index < 2000; index++) {
                sink = game.getCardsIn(zones);
                assertEquals(sink.size(), count);
            }
            long bytes = bean.getThreadAllocatedBytes(thread) - before;
            assertTrue(bytes >= 0);
            BYTES.put(Integer.toString(count), bytes);
        }
        assertEquals(BYTES.size(), 4);
    }
}
