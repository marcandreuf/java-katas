package org.mandfer.practices.algorithm;

/**
 * Recursion depth is a function of the input, and the exponential nobody measured.
 *
 * Two existing files:
 *
 *   StringRecursiveReversal.reverseString  -- recursion depth IS the string length, and it
 *                                             allocates a substring per frame, so it is O(n^2)
 *                                             in allocation as well
 *   PascalsTriangleRecursive.cellValue     -- exponential. 219 calls at the repo's own
 *                                             printPascal(6) (120 for row 6 alone); 2,147,483,616 at row 30.
 *
 * Neither has a test. See docs/dsa/08-recursion-depth.md.
 *
 * TODO 1: reverseIteratively -- same result, constant stack. The test asserts it agrees with
 *         the recursive version on short inputs and then goes well past the recursive one's
 *         limit, which is the only assertion that matters.
 * TODO 2: deepestReverseThatWorks -- find, by trying, the longest string the RECURSIVE version
 *         survives on this JVM. Catch StackOverflowError to do it.
 *
 *         Then read the doc, because catching StackOverflowError is unsound and the number you
 *         get is not reproducible -- in one JVM it came out at 3,839 when found by doubling and
 *         bisecting, and 19,500 when found by stepping up 250 at a time, on the same machine with
 *         no flags changed. The exercise is to get a number and then distrust it, which is
 *         the real lesson about recursion limits.
 * TODO 3: cellValueMemoized -- Pascal's triangle cell, memoised. Same answers as
 *         PascalsTriangleRecursive.cellValue, and row 30 in about 4 milliseconds instead of
 *         3 seconds.
 * TODO 4: countCellValueCalls -- instrument the UNMEMOISED recursion and return the call count.
 *         Row 6 is 120 and row 30 is over two billion. Then answer the honest question the doc
 *         asks: at the repo's own usage, printPascal(6), is any of this worth fixing?
 */
public final class Recursion {

    private Recursion() {
    }

    /** The string reversed, without recursion. */
    public static String reverseIteratively(String text) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** The longest input StringRecursiveReversal survives here. Not reproducible; see the doc. */
    public static int deepestReverseThatWorks() {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** Pascal's triangle cell, memoised. Same contract as PascalsTriangleRecursive.cellValue. */
    public static int cellValueMemoized(int column, int row) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** How many calls the unmemoised recursion makes to compute one cell. */
    public static long countCellValueCalls(int column, int row) {
        throw new UnsupportedOperationException("TODO 4");
    }
}
