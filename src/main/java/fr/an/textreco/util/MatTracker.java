package fr.an.textreco.util;

import org.opencv.core.Mat;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight instrumentation for OpenCV Mat lifecycle.
 *
 * <p>Call {@link #alloc(Mat, String)} immediately after {@code new Mat()} and
 * {@link #free(Mat, String)} immediately before {@code .release()}.
 * Statistics are printed periodically by {@link #logIfDue()}.
 */
public final class MatTracker {

    private MatTracker() {
    }

    private static final AtomicLong totalAllocs = new AtomicLong();
    private static final AtomicLong totalFrees = new AtomicLong();
    private static final AtomicLong loopAllocs = new AtomicLong(); // allocs since last log
    private static volatile long nextLogNs = 0;
    private static final long LOG_INTERVAL_NS = 5_000_000_000L; // 5 s

    // last printed values — used to suppress repeated identical (invariant) lines
    private static long lastLoggedTotal = -1;
    private static long lastLoggedLive = -1;

    /**
     * Record an allocation. Returns the Mat for fluent use.
     */
    public static Mat alloc(Mat mat, String tag) {
        long n = totalAllocs.incrementAndGet();
        loopAllocs.incrementAndGet();
        return mat;
    }

    /**
     * Record a release.
     */
    public static void free(Mat mat, String tag) {
        totalFrees.incrementAndGet();
    }

    /**
     * Call once per pipeline loop iteration.
     * Prints a one-line summary every 5 s if there were any allocs since the last print.
     */
    public static void logIfDue() {
        long now = System.nanoTime();
        if (now < nextLogNs) return;
        nextLogNs = now + LOG_INTERVAL_NS;
        long allocs = loopAllocs.getAndSet(0);
        long total = totalAllocs.get();
        long frees = totalFrees.get();
        long live = total - frees;
        if (allocs == 0 && total == lastLoggedTotal && live == lastLoggedLive) {
            // nothing changed since the last printed line — stay quiet
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
