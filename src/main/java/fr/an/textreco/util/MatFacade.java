package fr.an.textreco.util;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Size;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Single entry point (Facade) for every OpenCV {@link Mat} allocation in the app.
 *
 * <p>OpenCV {@code Mat}s wrap off-heap native memory that the JVM garbage
 * collector does not account for; allocating them inside the per-frame
 * processing loop is the main source of native-memory churn and GC pressure.
 * The intended lifecycle is:
 *
 * <ol>
 *   <li><b>init</b> mode — processors are constructed and pre-allocate all the
 *       scratch / output {@code Mat}s they will reuse. Allocation here is normal.</li>
 *   <li><b>runLoop</b> mode — the pipeline iterates over frames reusing the
 *       Mats allocated during init. Allocation here is <em>abnormal</em>: it
 *       indicates a Mat that should have been pre-allocated.</li>
 * </ol>
 *
 * <p>All {@code new Mat(...)} (and {@code MatOfXxx}) construction in production
 * code should be replaced by a call to one of the {@code alloc*} methods below.
 * When an allocation happens while in {@link Mode#RUN_LOOP}, the facade logs a
 * warning with a stack trace (so the offending call site is easy to find) and
 * counts it, but still returns the Mat so the app keeps running.
 *
 * <p>This class composes with {@link MatTracker}, which keeps the running
 * alloc/free/live statistics.
 */
public final class MatFacade {

    private MatFacade() {
    }

    /** Lifecycle phase of the application w.r.t. Mat allocation. */
    public enum Mode {
        /** Construction / pre-allocation phase. Allocation is expected. */
        INIT,
        /**
         * Warm-up phase: the first few frames of the run loop, during which
         * unavoidable lazy initialisations (e.g. a perspective transform that
         * needs the first frame's dimensions) are tolerated without warning.
         */
        WARMUP,
        /** Steady-state per-frame processing phase. Allocation is abnormal. */
        RUN_LOOP
    }

    private static volatile Mode mode = Mode.INIT;

    /**
     * Number of frame ticks the run loop stays in {@link Mode#WARMUP} before
     * switching to strict {@link Mode#RUN_LOOP}. A handful of frames is enough
     * for all first-frame lazy allocations to settle.
     */
    private static final int WARMUP_FRAMES = 3;

    /** Remaining warm-up frames; decremented by {@link #frameTick()}. */
    private static volatile int warmupFramesLeft = 0;

    /** Number of allocations that happened while in {@link Mode#RUN_LOOP}. */
    private static final AtomicLong runLoopAllocs = new AtomicLong();

    /** Number of allocations tolerated during {@link Mode#WARMUP}. */
    private static final AtomicLong warmupAllocs = new AtomicLong();

    /**
     * If true, a Mat allocated during {@link Mode#RUN_LOOP} additionally logs a
     * stack trace so the offending call site can be located. Allocation is
     * always allowed regardless of this flag.
     */
    private static volatile boolean warnOnRunLoopAlloc = true;

    // -------------------------------------------------------------------------
    // mode transitions
    // -------------------------------------------------------------------------

    /** Enter init mode — allocation is expected (pre-allocation phase). */
    public static void beginInit() {
        mode = Mode.INIT;
    }

    /**
     * Enter run-loop mode via a short warm-up window. The first
     * {@value #WARMUP_FRAMES} frames tolerate (but count) allocations so that
     * unavoidable first-frame lazy inits don't warn; after that, strict mode
     * makes any allocation abnormal. Call once, on the worker thread, just
     * before the per-frame loop starts, and call {@link #frameTick()} once per
     * iteration.
     */
    public static void beginRunLoop() {
        warmupFramesLeft = WARMUP_FRAMES;
        mode = WARMUP_FRAMES > 0 ? Mode.WARMUP : Mode.RUN_LOOP;
    }

    /**
     * Advance the warm-up countdown by one frame. Once the window elapses, the
     * facade transitions from {@link Mode#WARMUP} to strict {@link Mode#RUN_LOOP}.
     * No-op outside warm-up. Safe to call every loop iteration.
     */
    public static void frameTick() {
        if (mode != Mode.WARMUP) {
            return;
        }
        if (--warmupFramesLeft <= 0) {
            mode = Mode.RUN_LOOP;
        }
    }

    public static Mode getMode() {
        return mode;
    }

    public static boolean isInitMode() {
        return mode == Mode.INIT;
    }

    public static long runLoopAllocCount() {
        return runLoopAllocs.get();
    }

    public static long warmupAllocCount() {
        return warmupAllocs.get();
    }

    public static void setWarnOnRunLoopAlloc(boolean warn) {
        warnOnRunLoopAlloc = warn;
    }

    /**
     * Per-thread depth counter for {@link #expectAllocations(Runnable)} scopes.
     * Allocations made while this is &gt; 0 are counted as expected, never warned —
     * regardless of {@link Mode}. Use for deliberate, bounded lazy growth such as
     * pool expansion that cannot be sized up front.
     */
    private static final ThreadLocal<Integer> expectedDepth = ThreadLocal.withInitial(() -> 0);

    /** Number of allocations made inside an {@link #expectAllocations} scope. */
    private static final AtomicLong expectedAllocs = new AtomicLong();

    /**
     * Run {@code body}, marking any Mat allocations made during it (on this
     * thread) as expected: they are counted via {@link #expectedAllocCount()}
     * but never trigger a run-loop warning. Use only for genuinely unavoidable
     * lazy allocations that are bounded and amortised (e.g. growing a reuse
     * pool to a new high-water mark).
     */
    public static void expectAllocations(Runnable body) {
        expectedDepth.set(expectedDepth.get() + 1);
        try {
            body.run();
        } finally {
            expectedDepth.set(expectedDepth.get() - 1);
        }
    }

    public static long expectedAllocCount() {
        return expectedAllocs.get();
    }

    // -------------------------------------------------------------------------
    // allocation entry points
    // -------------------------------------------------------------------------

    /** Allocate an empty {@code Mat}. Equivalent to {@code new Mat()}. */
    public static Mat alloc(String tag) {
        return track(new Mat(), tag);
    }

    /** Allocate a sized, typed {@code Mat}. Equivalent to {@code new Mat(rows, cols, type)}. */
    public static Mat alloc(int rows, int cols, int type, String tag) {
        return track(new Mat(rows, cols, type), tag);
    }

    /** Allocate a sized, typed {@code Mat}. Equivalent to {@code new Mat(size, type)}. */
    public static Mat alloc(Size size, int type, String tag) {
        return track(new Mat(size, type), tag);
    }

    /** Allocate a {@code Mat} of ones. Equivalent to {@code Mat.ones(rows, cols, type)}. */
    public static Mat allocOnes(int rows, int cols, int type, String tag) {
        return track(Mat.ones(rows, cols, type), tag);
    }

    /** Allocate a {@code Mat} of zeros. Equivalent to {@code Mat.zeros(rows, cols, type)}. */
    public static Mat allocZeros(int rows, int cols, int type, String tag) {
        return track(Mat.zeros(rows, cols, type), tag);
    }

    /** Allocate an empty {@code MatOfByte}. */
    public static MatOfByte allocMatOfByte(String tag) {
        MatOfByte m = new MatOfByte();
        track(m, tag);
        return m;
    }

    /** Allocate an empty {@code MatOfPoint2f}. */
    public static MatOfPoint2f allocMatOfPoint2f(String tag) {
        MatOfPoint2f m = new MatOfPoint2f();
        track(m, tag);
        return m;
    }

    /**
     * Adopt a {@code Mat} that was created outside the facade (e.g. returned by
     * an OpenCV call such as {@code Imgcodecs.imread} or {@code Mat.submat}).
     * Records it in the statistics / mode checks without allocating a new one.
     */
    public static <M extends Mat> M adopt(M mat, String tag) {
        return track(mat, tag);
    }

    // -------------------------------------------------------------------------
    // release passthrough (keeps stats balanced)
    // -------------------------------------------------------------------------

    /** Release a Mat (null-safe) and record the free in the statistics. */
    public static void release(Mat mat, String tag) {
        if (mat == null) {
            return;
        }
        MatTracker.free(mat, tag);
        mat.release();
    }

    // -------------------------------------------------------------------------
    // internal
    // -------------------------------------------------------------------------

    private static <M extends Mat> M track(M mat, String tag) {
        MatTracker.alloc(mat, tag);
        if (expectedDepth.get() > 0) {
            expectedAllocs.incrementAndGet();
            return mat;
        }
        switch (mode) {
            case WARMUP -> warmupAllocs.incrementAndGet();
            case RUN_LOOP -> {
                long n = runLoopAllocs.incrementAndGet();
                if (warnOnRunLoopAlloc) {
                    Exception trace = new Exception("Mat allocated during RUN_LOOP: " + tag);
                    System.err.printf(
                            "[MatFacade] WARNING abnormal runLoop allocation #%d (tag=%s) "
                                    + "— this Mat should be pre-allocated during init mode%n",
                            n, tag);
                    trace.printStackTrace();
                }
            }
            case INIT -> { /* expected — no-op */ }
        }
        return mat;
    }
}
