package org.mandfer.practices.algorithm;

import java.util.List;

/**
 * The same answer computed two ways, so the test can prove they agree and the measurement can
 * prove they do not cost the same.
 *
 * docs/dsa/research.md calls accidental quadratics the single most common real performance bug.
 * It is also the one that is invisible at test sizes: measured here, a contains-in-a-loop is
 * 2.6x slower than a set at n=100 and 259x slower at n=100,000. You test at the first number
 * and deploy at the second.
 *
 * The old-code target is ProductsOfArrayElementsTest, whose productsArray() is O(n^2) by
 * construction -- it calls product(otherValues(...)) once per element, and each call walks the
 * whole array. See docs/dsa/07-accidental-quadratics.md.
 *
 * TODO 1: duplicatesByContains -- the quadratic version, on purpose. A loop with a
 *         List.contains inside it. This is what the bug looks like in the wild and you should
 *         be able to write it from memory, because that is how it gets written.
 * TODO 2: duplicatesBySet -- the linear version. The test asserts both return the SAME answer,
 *         including the same ORDER, because a rewrite that changes the order is not the same
 *         rewrite.
 * TODO 3: firstRepeatIndex -- return the index of the first element that already appeared.
 *         One pass, one Set. Note it must be the index and not the value.
 * TODO 4: productsExceptSelf -- the thing ProductsOfArrayElements does quadratically, done in
 *         linear time with a prefix pass and a suffix pass. This is the "prefix sums" idea the
 *         research doc rates load-bearing-but-narrow, and it is the whole technique.
 *         Do it WITHOUT division, because a single zero in the input breaks the division trick
 *         and the test has one.
 */
public final class Quadratics {

    private Quadratics() {
    }

    /** Values appearing more than once, in order of first repeat. Quadratic, on purpose. */
    public static List<Integer> duplicatesByContains(List<Integer> values) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** The same list, same order, in one pass. */
    public static List<Integer> duplicatesBySet(List<Integer> values) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** Index of the first element that had already appeared, or -1. */
    public static int firstRepeatIndex(List<Integer> values) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** result[i] is the product of every element except values[i]. No division. */
    public static int[] productsExceptSelf(int[] values) {
        throw new UnsupportedOperationException("TODO 4");
    }
}
