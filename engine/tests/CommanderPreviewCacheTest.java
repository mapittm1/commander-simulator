package forge.ai;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Preview caches must have a bounded lifetime without invalidating the live decision cache. */
public class CommanderPreviewCacheTest {
    private int value(String key, int supplied) {
        return AiCache.getCached(key, () -> supplied, null, "same-argument");
    }
    @Test public void aNestedPreviewRestoresItsParentAndLiveValues() {
        String key = "commander-preview-cache-nesting";
        AiCache.clear();
        assertEquals(value(key, 1), 1);
        AiCache.withIsolatedCache(() -> {
            assertEquals(value(key, 2), 2);
            AiCache.withIsolatedCache(() -> { assertEquals(value(key, 3), 3); return null; });
            assertEquals(value(key, 4), 2);
            return null;
        });
        assertEquals(value(key, 5), 1);
    }
    @Test public void aFailedPreviewStillRestoresTheLiveCache() {
        String key = "commander-preview-cache-failure";
        AiCache.clear();
        assertEquals(value(key, 1), 1);
        try {
            AiCache.withIsolatedCache(() -> {
                assertEquals(value(key, 2), 2);
                throw new IllegalStateException("controlled failed preview");
            });
            fail("Expected the preview failure");
        } catch (IllegalStateException expected) {
            assertEquals(expected.getMessage(), "controlled failed preview");
        }
        assertEquals(value(key, 3), 1);
    }
    @Test public void clearingAPreviewLeavesTheLiveCacheIntact() {
        String key = "commander-preview-cache-clear";
        AiCache.clear();
        assertEquals(value(key, 1), 1);
        AiCache.withIsolatedCache(() -> {
            assertEquals(value(key, 2), 2);
            AiCache.clear();
            assertEquals(value(key, 3), 3);
            return null;
        });
        assertEquals(value(key, 4), 1);
    }
    @Test public void simultaneousPreviewsDoNotShareTheirCachedValues() throws InterruptedException {
        String key = "commander-preview-cache-threads";
        AiCache.clear();
        assertEquals(value(key, 1), 1);
        CountDownLatch ready = new CountDownLatch(2);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread[] workers = new Thread[2];
        for (int i = 0; i < workers.length; i++) {
            final int supplied = i + 2;
            workers[i] = new Thread(() -> {
                try {
                    AiCache.withIsolatedCache(() -> {
                        assertEquals(value(key, supplied), supplied);
                        ready.countDown();
                        try {
                            assertTrue(ready.await(2, TimeUnit.SECONDS));
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(interrupted);
                        }
                        assertEquals(value(key, 9), supplied);
                        return null;
                    });
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                }
            });
            workers[i].start();
        }
        for (Thread worker : workers) { worker.join(4000); assertFalse(worker.isAlive()); }
        if (failure.get() != null) throw new AssertionError(failure.get());
        assertEquals(value(key, 10), 1);
    }
}
