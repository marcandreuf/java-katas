package org.mandfer.practices.functional;

import java.util.List;

/**
 * Stream gatherers: the extension point Stream has been missing since 2014.
 *
 * Streams let you write your own terminal operation (a Collector) from day one, and gave you
 * no way to write your own INTERMEDIATE one. Everything between source and terminal had to be
 * built out of the fixed set -- map, filter, flatMap, limit -- and anything stateful, like
 * "group these into threes", meant leaving the stream and writing a loop.
 *
 * Gatherers (JEP 485) shipped final in Java 24. See docs/modernization/22-stream-gatherers.md.
 *
 * NOTE: needs --release 24. See docs/env-setup.md.
 *
 * TODO 1: batches()  -- Gatherers.windowFixed. Note what happens to the last, short window.
 * TODO 2: deltas()   -- Gatherers.windowSliding(2), then subtract. Write it with an index loop
 *         first so you have something to compare against.
 *         Then run deltas_of_a_single_element_is_empty. The obvious implementation throws
 *         ArrayIndexOutOfBoundsException there, and the reason is a windowSliding behaviour
 *         worth knowing before you rely on it. Find out what it emits for input shorter than
 *         the window before you reach for a fix.
 * TODO 3: runningTotals() -- Gatherers.scan. Then say how it differs from Gatherers.fold,
 *         which is in the same class and returns one element.
 * TODO 4: takeUntilSumExceeds() -- a CUSTOM gatherer. This is the exercise. Use
 *         Gatherer.ofSequential with a state initializer and an integrator, and return false
 *         from the integrator to stop the stream short. Nothing in the built-in set can do
 *         this, which is the entire argument for the feature.
 * TODO 5: timesTenConcurrently() -- Gatherers.mapConcurrent, which runs the mapper on virtual
 *         threads with a concurrency limit and PRESERVES ORDER. Compare it with
 *         docs/modernization/15-completable-future.md and say which you would use for N HTTP calls.
 */
public final class Windows {

    private Windows() {
    }

    /** Consecutive non-overlapping groups of at most size. */
    public static List<List<Integer>> batches(List<Integer> values, int size) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** The difference between each element and the one before it. One shorter than the input. */
    public static List<Integer> deltas(List<Integer> values) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** The running sum: element n is the sum of elements 0..n. Same length as the input. */
    public static List<Integer> runningTotals(List<Integer> values) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** Elements from the start, stopping before the running total would exceed limit. */
    public static List<Integer> takeUntilSumExceeds(List<Integer> values, int limit) {
        throw new UnsupportedOperationException("TODO 4");
    }

    /** Each element times ten, mapped concurrently, in input order. */
    public static List<Integer> timesTenConcurrently(List<Integer> values, int concurrency) {
        throw new UnsupportedOperationException("TODO 5");
    }
}
