package forge.ai;

import forge.util.MyRandom;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/** Preview randomness must not consume, replace, or count as live-game randomness. */
public class PreviewRandomIsolationTest {
    @Test public void nestedProvidersRestoreAfterExceptions() {
        Random global = MyRandom.getRandom();
        long count = MyRandom.callCount();
        Random outer = new Random(31);
        MyRandom.withRandom(outer, () -> {
            assertSame(MyRandom.getRandom(), outer);
            try {
                MyRandom.withRandom(new Random(41), () -> {
                    MyRandom.setRandom(new Random(51));
                    assertEquals(MyRandom.getRandom().nextInt(), new Random(51).nextInt());
                    throw new IllegalStateException("deliberate preview failure");
                });
                fail("Expected preview exception");
            } catch (IllegalStateException expected) {
                assertEquals(expected.getMessage(), "deliberate preview failure");
            }
            assertSame(MyRandom.getRandom(), outer);
            assertEquals(MyRandom.getRandom().nextInt(), new Random(31).nextInt());
            return null;
        });
        assertSame(MyRandom.getRandom(), global);
        assertEquals(MyRandom.callCount(), count);
    }

    private int runScope(CyclicBarrier barrier, long seed) {
        return MyRandom.withRandom(new Random(seed), () -> {
            try {
                barrier.await(10, TimeUnit.SECONDS);
                MyRandom.setRandom(new Random(seed + 1));
                barrier.await(10, TimeUnit.SECONDS);
                return MyRandom.getRandom().nextInt();
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        });
    }

    @Test public void simultaneousScopesDoNotReplaceEachOtherOrLiveProvider() throws Exception {
        Random global = MyRandom.getRandom();
        long count = MyRandom.callCount();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> runScope(barrier, 61));
            Future<Integer> second = pool.submit(() -> runScope(barrier, 71));
            assertEquals(first.get(15, TimeUnit.SECONDS).intValue(), new Random(62).nextInt());
            assertEquals(second.get(15, TimeUnit.SECONDS).intValue(), new Random(72).nextInt());
            assertSame(pool.submit(MyRandom::getRandom).get(15, TimeUnit.SECONDS), global,
                    "Reused worker must not retain the completed preview scope");
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS));
        }
        assertSame(MyRandom.getRandom(), global);
        assertEquals(MyRandom.callCount(), count);
    }

    @Test public void helpersUseScopedProviderWithoutConsumingLiveDraws() {
        Random global = MyRandom.getRandom();
        long count = MyRandom.callCount();
        int[] expected = new int[5];
        Random reference = new Random(81);
        for (int i = 0; i < 50; i++) expected[reference.nextInt(5)]++;
        int[] observed = MyRandom.withRandom(new Random(81), () -> MyRandom.splitIntoRandomGroups(50, 5));
        assertEquals(observed, expected);
        assertSame(MyRandom.getRandom(), global);
        assertEquals(MyRandom.callCount(), count);
    }
}
