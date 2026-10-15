package org.mandfer.practices.algorithm;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Exercise: docs/dsa/06-answer-space-search.md. Delete this line to begin.")
class WorkLoadTest {

    /** The fixture from FairWorkloadTest's @Disabled test, which has been failing since 2014. */
    private static final int[] SIX_WORKERS = {950, 650, 250, 250, 350, 100, 650, 150, 150, 700};

    @Test
    void one_worker_takes_everything() {
        assertEquals(60, WorkLoad.smallestAchievableLoad(new int[]{10, 20, 30}, 1));
        assertEquals(1, WorkLoad.workersNeededFor(new int[]{10, 20, 30}, 60));
    }

    @Test
    void a_cap_below_the_largest_cabinet_is_impossible() {
        assertEquals(Integer.MAX_VALUE, WorkLoad.workersNeededFor(SIX_WORKERS, 900),
                "no number of workers can split the 950 cabinet");
        assertFalse(WorkLoad.isAchievable(SIX_WORKERS, 6, 900));
        assertFalse(WorkLoad.isAchievable(SIX_WORKERS, 1000, 900), "not even a thousand workers");
    }

    @Test
    void the_predicate_is_monotonic() {
        int answer = WorkLoad.smallestAchievableLoad(SIX_WORKERS, 6);

        assertFalse(WorkLoad.isAchievable(SIX_WORKERS, 6, answer - 1), "one below must fail");
        for (int cap = answer; cap <= answer + 50; cap++) {
            assertTrue(WorkLoad.isAchievable(SIX_WORKERS, 6, cap), "cap " + cap + " must work");
        }
    }

    @Test
    void known_fixtures() {
        assertEquals(100, WorkLoad.smallestAchievableLoad(new int[]{1, 1, 1, 1, 100}, 5));
        assertEquals(1, WorkLoad.smallestAchievableLoad(new int[]{1, 1}, 2));
        assertEquals(900, WorkLoad.smallestAchievableLoad(new int[]{100, 200, 300, 400, 500}, 2));
    }

    /**
     * The one FairWorkLoad gets wrong. It returns 900, which is not merely suboptimal -- it is
     * impossible, because the 950-folder cabinet cannot be split. The search started below the
     * true lower bound.
     */
    @Test
    void the_fixture_that_has_been_failing_since_2014() {
        assertEquals(950, WorkLoad.smallestAchievableLoad(SIX_WORKERS, 6));
    }

    /**
     * The assertion FairWorkloadTest never made, and the reason the bug survived twelve years.
     * It only ever compared the answer against a hand-computed number. This checks the answer
     * is ACHIEVABLE -- which 900 is not, by any number of workers.
     */
    @Test
    void the_answer_is_always_achievable_and_minimal() {
        int[][] fixtures = {
                {1, 1, 1, 1, 100}, {1, 1}, {100, 200, 300, 400, 500}, {10, 20, 30}, SIX_WORKERS};
        int[] workers = {5, 2, 2, 1, 6};

        for (int i = 0; i < fixtures.length; i++) {
            int answer = WorkLoad.smallestAchievableLoad(fixtures[i], workers[i]);

            assertTrue(WorkLoad.isAchievable(fixtures[i], workers[i], answer),
                    "fixture " + i + ": answer " + answer + " is not achievable");
            assertFalse(WorkLoad.isAchievable(fixtures[i], workers[i], answer - 1),
                    "fixture " + i + ": answer " + answer + " is not minimal");
        }
    }
}
