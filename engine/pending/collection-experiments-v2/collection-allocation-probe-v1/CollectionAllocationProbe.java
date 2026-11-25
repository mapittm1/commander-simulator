package forge.gamesimulationtests.util;

import forge.util.collect.FCollection;
import java.lang.management.ManagementFactory;

/** Fixed escaping allocations measure collection objects, not retained memory or rules support. */
public final class CollectionAllocationProbe {
    private static volatile Object keepAlive;
    private static final int COUNT = 100000;

    private static void fill(Object[] target, String mode, FCollection<String> source) {
        for (int index = 0; index < target.length; index++) {
            target[index] = mode.equals("construct_empty") ? new FCollection<String>() : source.shareCopy();
        }
        keepAlive = target;
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 0) throw new IllegalArgumentException("This probe has no user-selected modes");
        Runtime runtime = Runtime.getRuntime();
        if (runtime.availableProcessors() != 2 || runtime.maxMemory() != 2147483648L)
            throw new AssertionError("Expected the unchanged two-processor, two-GiB heap policy");
        com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) throw new AssertionError("Allocation accounting unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        System.out.println("RUNTIME processors=" + runtime.availableProcessors() + ";max_heap_bytes=" + runtime.maxMemory());
        System.out.println("COLLECTION_ORIGIN " + FCollection.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        System.out.println("PROBE_ORIGIN " + CollectionAllocationProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        for (String mode : new String[] {"share_empty", "share_populated", "construct_empty"}) {
            FCollection<String> source = new FCollection<>();
            if (mode.equals("share_populated")) { source.add("alpha"); source.add("beta"); }
            fill(new Object[10000], mode, source);
            Object[] result = new Object[COUNT];
            long before = bean.getThreadAllocatedBytes(thread);
            fill(result, mode, source);
            long bytes = bean.getThreadAllocatedBytes(thread) - before;
            for (Object item : result) {
                FCollection<?> copy = (FCollection<?>) item;
                int expected = mode.equals("share_populated") ? 2 : 0;
                if (copy.size() != expected) throw new AssertionError("Unexpected copied collection size");
            }
            if (keepAlive != result) throw new AssertionError("Escaping allocation sink was not retained");
            System.out.println("ALLOCATION mode=" + mode + ";count=" + COUNT + ";bytes=" + bytes);
        }
    }
}
