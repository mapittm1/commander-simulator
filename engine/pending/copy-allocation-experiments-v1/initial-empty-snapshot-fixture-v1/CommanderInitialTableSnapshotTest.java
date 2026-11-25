package forge.ai;

import forge.util.collect.FSnapshotTable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Public table contracts; initial-token identity between separate tables is unspecified. */
public class CommanderInitialTableSnapshotTest {
    @Test
    public void tablesAndHeldViewsStayIndependent() {
        FSnapshotTable<Long, Long, String> first = FSnapshotTable.createTree();
        FSnapshotTable<Long, Long, String> second = FSnapshotTable.createTree();
        Object secondVersion = second.version();
        Collection<String> heldValues = first.values();
        Map<Long, Map<Long, String>> heldRows = first.rowMap();
        Set<Long> firstKeys = first.rowKeySet();
        Set<Long> secondKeys = second.rowKeySet();
        first.put(1L, 2L, "first");
        assertEquals(firstKeys, Set.of(1L));
        assertTrue(secondKeys.isEmpty());
        assertTrue(heldValues.isEmpty());
        assertTrue(heldRows.isEmpty());
        assertSame(second.version(), secondVersion);
        second.put(3L, 4L, "second");
        assertEquals(first.get(1L, 2L), "first");
        assertNull(first.get(3L, 4L));
        assertEquals(second.get(3L, 4L), "second");
        firstKeys.clear();
        assertTrue(first.isEmpty());
        assertEquals(second.values(), List.of("second"));
    }

    @Test
    public void changedWritesNeverReuseAnEarlierVersion() {
        FSnapshotTable<Long, Long, String> table = FSnapshotTable.createTree();
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        seen.put(table.version(), true);
        for (int index = 0; index < 8; index++) {
            table.put(1L, 2L, "entry");
            assertNull(seen.put(table.version(), true));
            table.clear();
            assertNull(seen.put(table.version(), true));
            assertTrue(table.isEmpty());
        }
    }

    @Test
    public void noOpsRetainTheCurrentVersion() {
        FSnapshotTable<Long, Long, String> table = FSnapshotTable.createTree();
        Object initial = table.version();
        table.clear();
        assertNull(table.remove(1L, 2L));
        table.putAll(FSnapshotTable.<Long, Long, String>createTree());
        assertFalse(table.rowKeySet().remove(1L));
        assertSame(table.version(), initial);
    }

    @Test
    public void distinctGenericTypesKeepTheirOwnContents() {
        FSnapshotTable<Long, String, String> first = FSnapshotTable.createTree();
        FSnapshotTable<String, Long, Integer> second = FSnapshotTable.createTree();
        assertTrue(first.values().isEmpty());
        assertTrue(second.columnMap().isEmpty());
        first.put(2L, "a", "first");
        second.put("b", 3L, 42);
        assertEquals(first.get(2L, "a"), "first");
        assertEquals(second.get("b", 3L), Integer.valueOf(42));
        first.clear();
        assertEquals(second.size(), 1);
    }

    @Test
    public void serializationRetainsIndependentMutableTables() throws Exception {
        FSnapshotTable<Long, Long, String> original = FSnapshotTable.createTree();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        FSnapshotTable<Long, Long, String> restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            @SuppressWarnings("unchecked")
            FSnapshotTable<Long, Long, String> value = (FSnapshotTable<Long, Long, String>) input.readObject();
            restored = value;
        }
        restored.put(1L, 2L, "restored");
        assertTrue(original.isEmpty());
        assertEquals(restored.get(1L, 2L), "restored");
        Object occupied = restored.version();
        restored.clear();
        assertNotSame(restored.version(), occupied);
    }

    @Test
    public void concurrentTablesKeepViewsAndWritesIndependent() throws Exception {
        FSnapshotTable<Long, Long, String> first = FSnapshotTable.createTree();
        FSnapshotTable<Long, Long, String> second = FSnapshotTable.createTree();
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> threads = new ArrayList<>();
        for (FSnapshotTable<Long, Long, String> table : List.of(first, second)) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    assertTrue(table.rowMap().isEmpty());
                    assertTrue(table.columnMap().isEmpty());
                    for (int index = 0; index < 1000; index++) {
                        table.put(1L, 2L, "entry");
                        assertEquals(table.values(), List.of("entry"));
                        table.clear();
                        assertTrue(table.rowKeySet().isEmpty());
                    }
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                }
            });
            thread.setDaemon(true);
            threads.add(thread);
            thread.start();
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join(5000);
            assertFalse(thread.isAlive());
        }
        assertNull(failure.get());
        assertTrue(first.isEmpty());
        assertTrue(second.isEmpty());
    }

    @Test
    public void retainedFreshTablesStayIndependentWithAllocationDiagnostic() {
        int count = 10000;
        List<FSnapshotTable<Long, Long, String>> tables = new ArrayList<>(count);
        com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        boolean measured = bean.isThreadAllocatedMemorySupported() && bean.isThreadAllocatedMemoryEnabled();
        long thread = Thread.currentThread().getId();
        long before = measured ? bean.getThreadAllocatedBytes(thread) : -1;
        for (int index = 0; index < count; index++) {
            tables.add(FSnapshotTable.createTree());
        }
        long allocated = measured ? bean.getThreadAllocatedBytes(thread) - before : -1;
        System.out.println("TABLE_INIT_ALLOCATION tables=" + count + ";bytes=" + allocated);
        assertEquals(new java.util.HashSet<>(tables).size(), 1); // Equal empty contents.
        tables.get(0).put(1L, 2L, "first");
        for (int index = 1; index < count; index++) assertTrue(tables.get(index).isEmpty());
    }
}
