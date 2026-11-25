package forge.gamesimulationtests.util;

import forge.util.collect.FSnapshotTable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Published table snapshots expose read-only maps at both nesting levels. */
public class CommanderSnapshotViewTest {
    private FSnapshotTable<String, String, Integer> table(boolean sorted) {
        FSnapshotTable<String, String, Integer> table = sorted
                ? FSnapshotTable.createTree() : FSnapshotTable.createHash();
        table.put("r", "c", 1);
        table.put("r", "d", 2);
        table.put("s", "c", 3);
        return table;
    }

    private Map<String, Map<String, Integer>> view(
            FSnapshotTable<String, String, Integer> table, boolean rows) {
        return rows ? table.rowMap() : table.columnMap();
    }

    private Map<String, Map<String, Integer>> detached(Map<String, Map<String, Integer>> view) {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        view.forEach((key, value) -> result.put(key, new LinkedHashMap<>(value)));
        return result;
    }

    private Map<String, Integer> inner(Map<String, Map<String, Integer>> view, String path) {
        if (path.equals("get")) return view.get(view.keySet().iterator().next());
        if (path.equals("values")) return view.values().iterator().next();
        if (path.equals("entries")) return view.entrySet().iterator().next().getValue();
        assertEquals(path, "forEach");
        AtomicReference<Map<String, Integer>> selected = new AtomicReference<>();
        view.forEach((key, value) -> selected.compareAndSet(null, value));
        return selected.get();
    }

    private void rejectMutation(boolean sorted, boolean rows, String path, String operation) {
        FSnapshotTable<String, String, Integer> table = table(sorted);
        Map<String, Map<String, Integer>> published = view(table, rows);
        Map<String, Map<String, Integer>> before = detached(published);
        Map<String, Integer> nested = inner(published, path);
        boolean rejected = false;
        try {
            if (operation.equals("put")) nested.put("added", 99);
            else if (operation.equals("remove")) nested.remove(nested.keySet().iterator().next());
            else { assertEquals(operation, "clear"); nested.clear(); }
        } catch (UnsupportedOperationException expected) {
            rejected = true;
        }
        assertEquals(detached(published), before, "Mutation changed a published nested view");
        assertEquals(detached(view(table, rows)), before, "Mutation changed current table observations");
        assertEquals(table.size(), 3);
        assertEquals(table.get("r", "c"), Integer.valueOf(1));
        assertEquals(table.get("r", "d"), Integer.valueOf(2));
        assertEquals(table.get("s", "c"), Integer.valueOf(3));
        assertTrue(rejected, "Nested snapshot mutation must be rejected");
    }

    private void stableSnapshot(boolean sorted, boolean rows) {
        FSnapshotTable<String, String, Integer> table = table(sorted);
        Map<String, Map<String, Integer>> published = view(table, rows);
        Map<String, Map<String, Integer>> before = detached(published);
        table.put("r", "c", 7);
        table.put("new", "new", 9);
        assertEquals(detached(published), before);
        assertEquals(table.size(), 4);
        assertEquals(table.get("r", "c"), Integer.valueOf(7));
    }

    @SuppressWarnings("unchecked")
    private void serialization(boolean sorted, boolean rows) throws Exception {
        FSnapshotTable<String, String, Integer> original = table(sorted);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }
        String wireHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        System.out.println("SNAPSHOT_WIRE sorted=" + sorted + ";rows=" + rows + ";sha256=" + wireHash);
        FSnapshotTable<String, String, Integer> copy;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            copy = (FSnapshotTable<String, String, Integer>) input.readObject();
        }
        assertEquals(detached(view(copy, rows)), detached(view(original, rows)));
        copy.put("new", "new", 9);
        assertEquals(original.size(), 3);
        assertEquals(copy.size(), 4);
    }
    @Test public void nestedHashRowGetPut() { rejectMutation(false, true, "get", "put"); }
    @Test public void nestedHashRowGetRemove() { rejectMutation(false, true, "get", "remove"); }
    @Test public void nestedHashRowGetClear() { rejectMutation(false, true, "get", "clear"); }
    @Test public void nestedHashRowValuesPut() { rejectMutation(false, true, "values", "put"); }
    @Test public void nestedHashRowValuesRemove() { rejectMutation(false, true, "values", "remove"); }
    @Test public void nestedHashRowValuesClear() { rejectMutation(false, true, "values", "clear"); }
    @Test public void nestedHashRowEntriesPut() { rejectMutation(false, true, "entries", "put"); }
    @Test public void nestedHashRowEntriesRemove() { rejectMutation(false, true, "entries", "remove"); }
    @Test public void nestedHashRowEntriesClear() { rejectMutation(false, true, "entries", "clear"); }
    @Test public void nestedHashRowForEachPut() { rejectMutation(false, true, "forEach", "put"); }
    @Test public void nestedHashRowForEachRemove() { rejectMutation(false, true, "forEach", "remove"); }
    @Test public void nestedHashRowForEachClear() { rejectMutation(false, true, "forEach", "clear"); }
    @Test public void stableHashRowSnapshot() { stableSnapshot(false, true); }
    @Test public void stableHashRowSerialization() throws Exception { serialization(false, true); }
    @Test public void nestedHashColumnGetPut() { rejectMutation(false, false, "get", "put"); }
    @Test public void nestedHashColumnGetRemove() { rejectMutation(false, false, "get", "remove"); }
    @Test public void nestedHashColumnGetClear() { rejectMutation(false, false, "get", "clear"); }
    @Test public void nestedHashColumnValuesPut() { rejectMutation(false, false, "values", "put"); }
    @Test public void nestedHashColumnValuesRemove() { rejectMutation(false, false, "values", "remove"); }
    @Test public void nestedHashColumnValuesClear() { rejectMutation(false, false, "values", "clear"); }
    @Test public void nestedHashColumnEntriesPut() { rejectMutation(false, false, "entries", "put"); }
    @Test public void nestedHashColumnEntriesRemove() { rejectMutation(false, false, "entries", "remove"); }
    @Test public void nestedHashColumnEntriesClear() { rejectMutation(false, false, "entries", "clear"); }
    @Test public void nestedHashColumnForEachPut() { rejectMutation(false, false, "forEach", "put"); }
    @Test public void nestedHashColumnForEachRemove() { rejectMutation(false, false, "forEach", "remove"); }
    @Test public void nestedHashColumnForEachClear() { rejectMutation(false, false, "forEach", "clear"); }
    @Test public void stableHashColumnSnapshot() { stableSnapshot(false, false); }
    @Test public void stableHashColumnSerialization() throws Exception { serialization(false, false); }
    @Test public void nestedTreeRowGetPut() { rejectMutation(true, true, "get", "put"); }
    @Test public void nestedTreeRowGetRemove() { rejectMutation(true, true, "get", "remove"); }
    @Test public void nestedTreeRowGetClear() { rejectMutation(true, true, "get", "clear"); }
    @Test public void nestedTreeRowValuesPut() { rejectMutation(true, true, "values", "put"); }
    @Test public void nestedTreeRowValuesRemove() { rejectMutation(true, true, "values", "remove"); }
    @Test public void nestedTreeRowValuesClear() { rejectMutation(true, true, "values", "clear"); }
    @Test public void nestedTreeRowEntriesPut() { rejectMutation(true, true, "entries", "put"); }
    @Test public void nestedTreeRowEntriesRemove() { rejectMutation(true, true, "entries", "remove"); }
    @Test public void nestedTreeRowEntriesClear() { rejectMutation(true, true, "entries", "clear"); }
    @Test public void nestedTreeRowForEachPut() { rejectMutation(true, true, "forEach", "put"); }
    @Test public void nestedTreeRowForEachRemove() { rejectMutation(true, true, "forEach", "remove"); }
    @Test public void nestedTreeRowForEachClear() { rejectMutation(true, true, "forEach", "clear"); }
    @Test public void stableTreeRowSnapshot() { stableSnapshot(true, true); }
    @Test public void stableTreeRowSerialization() throws Exception { serialization(true, true); }
    @Test public void nestedTreeColumnGetPut() { rejectMutation(true, false, "get", "put"); }
    @Test public void nestedTreeColumnGetRemove() { rejectMutation(true, false, "get", "remove"); }
    @Test public void nestedTreeColumnGetClear() { rejectMutation(true, false, "get", "clear"); }
    @Test public void nestedTreeColumnValuesPut() { rejectMutation(true, false, "values", "put"); }
    @Test public void nestedTreeColumnValuesRemove() { rejectMutation(true, false, "values", "remove"); }
    @Test public void nestedTreeColumnValuesClear() { rejectMutation(true, false, "values", "clear"); }
    @Test public void nestedTreeColumnEntriesPut() { rejectMutation(true, false, "entries", "put"); }
    @Test public void nestedTreeColumnEntriesRemove() { rejectMutation(true, false, "entries", "remove"); }
    @Test public void nestedTreeColumnEntriesClear() { rejectMutation(true, false, "entries", "clear"); }
    @Test public void nestedTreeColumnForEachPut() { rejectMutation(true, false, "forEach", "put"); }
    @Test public void nestedTreeColumnForEachRemove() { rejectMutation(true, false, "forEach", "remove"); }
    @Test public void nestedTreeColumnForEachClear() { rejectMutation(true, false, "forEach", "clear"); }
    @Test public void stableTreeColumnSnapshot() { stableSnapshot(true, false); }
    @Test public void stableTreeColumnSerialization() throws Exception { serialization(true, false); }
}
