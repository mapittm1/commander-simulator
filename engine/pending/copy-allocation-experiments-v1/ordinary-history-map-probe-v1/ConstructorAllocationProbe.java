package forge.ai;

import forge.game.card.CardCopyService;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/** Bounded constructor diagnostic, not a printed-card or gameplay correctness test. */
public class ConstructorAllocationProbe {
    public static void main(String[] args) {
        Runtime runtime = Runtime.getRuntime();
        if (runtime.availableProcessors() != 2 || runtime.maxMemory() > 2147483648L) {
            throw new IllegalStateException("Unexpected diagnostic JVM limits");
        }
        List<CardCopyService> warmup = new ArrayList<>(1000);
        for (int index = 0; index < 1000; index++) warmup.add(new CardCopyService(null));
        com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        boolean measured = bean.isThreadAllocatedMemorySupported() && bean.isThreadAllocatedMemoryEnabled();
        int count = 10000;
        List<CardCopyService> retained = new ArrayList<>(count);
        long thread = Thread.currentThread().getId();
        long before = measured ? bean.getThreadAllocatedBytes(thread) : -1;
        for (int index = 0; index < count; index++) retained.add(new CardCopyService(null));
        long allocated = measured ? bean.getThreadAllocatedBytes(thread) - before : -1;
        for (CardCopyService service : retained) {
            if (service.getLKICopy() != null) throw new IllegalStateException("A null source acquired a copy");
        }
        System.out.println("CONSTRUCTOR_ALLOCATION retained=" + count + ";bytes=" + allocated);
        System.out.println("ORIGIN " + CardCopyService.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("LIMITS processors=" + runtime.availableProcessors() + ";heap=" + runtime.maxMemory());
    }
}
