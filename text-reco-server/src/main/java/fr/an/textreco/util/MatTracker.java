package fr.an.textreco.util;

import org.opencv.core.Mat;

import java.util.concurrent.atomic.AtomicLong;

public final class MatTracker {

    private MatTracker() {
    }

    private static final AtomicLong totalAllocs = new AtomicLong();
    private static final AtomicLong totalFrees = new AtomicLong();
    private static final AtomicLong loopAllocs = new AtomicLong();
    private static volatile long nextLogNs = 0;
    private static final long LOG_INTERVAL_NS = 5_000_000_000L;
    private static long lastLoggedTotal = -1;
    private static long lastLoggedLive = -1;

    public static Mat alloc(Mat mat, String tag) {
        totalAllocs.incrementAndGet();
        loopAllocs.incrementAndGet();
        return mat;
    }

    public static void free(Mat mat, String tag) {
        totalFrees.incrementAndGet();
    }

    public static void logIfDue() {
        long now = System.nanoTime();
        if (now < nextLogNs) return;
        nextLogNs = now + LOG_INTERVAL_NS;
        long allocs = loopAllocs.getAndSet(0);
        long total = totalAllocs.get();
        long frees = totalFrees.get();
        long live = total - frees;
        if (allocs == 0 && total == lastLoggedTotal && live == lastLoggedLive) {
            return;
        }
        if (allocs > 0 || live > 5) {
            System.out.printf("[MatTracker] allocs/5s=%-5d  total=%-7d  live=%d%n",
                    allocs, total, live);
            lastLoggedTotal = total;
            lastLoggedLive = live;
        }
    }

    public static long liveCount() {
        return totalAllocs.get() - totalFrees.get();
    }
}
