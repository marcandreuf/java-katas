package org.mandfer.practices.algorithm;

/**
 * Binary search over an ANSWER SPACE rather than over an array. The one genuinely non-obvious
 * technique in this track, and FairWorkLoad.java already is it.
 *
 * The problem: given cabinets of folders and a number of workers, each worker takes a
 * CONTIGUOUS run of cabinets. What is the smallest possible value for the busiest worker's
 * load? You cannot binary search the input -- the input is not sorted and the answer is not in
 * it. What you search is the range of candidate ANSWERS, and the predicate is "is this answer
 * achievable".
 *
 * FairWorkLoad.java does this and is missing the piece that makes it checkable: a feasibility
 * test. Its own tests assert the returned number against a hand-computed expectation, which is
 * why one of them has been @Disabled since 2014 with the cause never found.
 *
 * NOTE: docs/modernization/09-streams-and-optional.md also owns FairWorkLoad.java and asks you
 * to diagnose that test before modernising around it. If you diagnose it here, write the answer
 * down there too. See docs/dsa/06-answer-space-search.md.
 *
 * TODO 1: workersNeededFor -- greedily pack cabinets into workers without exceeding cap, and
 *         return how many workers that took. Return Integer.MAX_VALUE if any single cabinet
 *         exceeds cap, because no number of workers can split one cabinet. That early return
 *         is the whole feasibility predicate and FairWorkLoad has nothing like it.
 * TODO 2: isAchievable -- cap works if it needs no more than `workers` workers.
 *         Convince yourself this predicate is MONOTONIC: if a cap works, every larger cap
 *         works. Monotonicity is the entire licence to binary search; without it the technique
 *         is invalid, and saying why is more valuable than the code.
 * TODO 3: smallestAchievableLoad -- binary search the answer space.
 *         The lower bound is the LARGEST single cabinet, not zero and not the first element:
 *         no cap below it can ever work. The upper bound is the sum: one worker takes
 *         everything. Then bisect on isAchievable.
 * TODO 4: run WorkLoadTest. It checks your answer is both minimal AND achievable. Then compare
 *         against FairWorkLoad on the fixture from its @Disabled test:
 *
 *             {950, 650, 250, 250, 350, 100, 650, 150, 150, 700}, 6 workers
 *
 *         Look hard at FairWorkLoad.max() on line 63 before you decide what is wrong.
 */
public final class WorkLoad {

    private WorkLoad() {
    }

    /** Workers needed to keep every load at or below cap, or MAX_VALUE if cap is impossible. */
    public static int workersNeededFor(int[] cabinetFolders, int cap) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** True if cap can be met with at most this many workers. Monotonic in cap. */
    public static boolean isAchievable(int[] cabinetFolders, int workers, int cap) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** The smallest busiest-worker load achievable with this many workers. */
    public static int smallestAchievableLoad(int[] cabinetFolders, int workers) {
        throw new UnsupportedOperationException("TODO 3");
    }
}
