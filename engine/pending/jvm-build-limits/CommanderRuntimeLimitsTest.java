package forge.gamesimulationtests.util;

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/** Verify actual JVM resources rather than inferring them from wrapper metadata. */
public class CommanderRuntimeLimitsTest {
    @Test public void nativeJvmUsesConfiguredProcessorsAndHeap() {
        Runtime runtime = Runtime.getRuntime();
        int processors = runtime.availableProcessors();
        long heap = runtime.maxMemory();
        System.out.println("RUNTIME_LIMITS processors=" + processors + ";max_heap_bytes=" + heap);
        assertEquals(processors, 2, "Native tests must use the configured JVM processor count");
        assertTrue(heap > 0 && heap <= 2_048L * 1_024L * 1_024L,
                "Native tests must keep the configured maximum Java heap");
    }
}
