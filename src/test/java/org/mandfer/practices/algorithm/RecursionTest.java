package org.mandfer.practices.algorithm;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Exercise: docs/dsa/08-recursion-depth.md. Delete this line to begin.")
class RecursionTest {

    @Test
    void the_iterative_version_agrees_with_the_recursive_one() {
        StringRecursiveReversal recursive = new StringRecursiveReversal();

        for (String input : new String[]{"", "a", "ab", "Java2novice", "racecar"}) {
            assertEquals(recursive.reverseString(input), Recursion.reverseIteratively(input), input);
        }
    }

    /**
     * Well past where the recursive version dies at a small -Xss. This assertion is the whole
     * reason to write the iterative one: it is not faster, it is possible.
     */
    @Test
    void the_iterative_version_handles_an_input_the_recursive_one_cannot() {
        String huge = "x".repeat(500_000);

        String reversed = Recursion.reverseIteratively(huge);

        assertEquals(500_000, reversed.length());
        assertEquals(huge, Recursion.reverseIteratively(reversed));
    }

    /** Not pinned to a number, because it is not reproducible. See the doc. */
    @Test
    void the_recursive_limit_exists_and_is_findable() {
        int deepest = Recursion.deepestReverseThatWorks();

        assertTrue(deepest > 1000, "expected to survive at least 1000 chars, got " + deepest);
        assertTrue(deepest < 10_000_000, "expected a finite limit, got " + deepest);
    }

    @Test
    void memoized_cells_match_the_recursive_ones() {
        PascalsTriangleRecursive unmemoized = new PascalsTriangleRecursive();

        for (int row = 1; row <= 10; row++) {
            for (int column = 1; column <= row; column++) {
                assertEquals(binomial(row, column), Recursion.cellValueMemoized(column, row),
                        "cell (" + column + "," + row + ")");
            }
        }
        assertTrue(unmemoized != null);
    }

    @Test
    void memoized_cells_are_computable_where_the_recursion_is_not() {
        assertEquals(binomial(30, 15), Recursion.cellValueMemoized(15, 30));
        assertEquals(1, Recursion.cellValueMemoized(1, 30));
    }

    /**
     * The repo's own usage, printPascal(6), costs 120 calls and runs instantly. Row 30 costs
     * over 290 million for a single cell. Both halves of that matter: the exponential is real
     * and it is also, at the only size this code is ever used at, irrelevant.
     */
    @Test
    void the_call_count_is_exponential_in_the_row() {
        assertEquals(29, Recursion.countCellValueCalls(3, 6));
        assertEquals(89, Recursion.countCellValueCalls(3, 10));
        assertEquals(335_919, Recursion.countCellValueCalls(10, 20));
    }

    /** Pascal's triangle cell (column, row), 1-based, which is binomial(row-1, column-1). */
    private static int binomial(int row, int column) {
        int n = row - 1;
        int k = column - 1;
        long result = 1;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return (int) result;
    }
}
