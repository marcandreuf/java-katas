package org.mandfer.practices.ordering;

import java.util.Comparator;
import java.util.List;

/**
 * Three ways a comparator is wrong without throwing, and the size at which the JDK notices.
 *
 * Effective Java Item 14 and the OCP objectives both treat the Comparable/Comparator contract
 * as baseline. This is why: all three failures below pass a small unit test.
 * See docs/dsa/04-ordering-contracts.md.
 *
 * TODO 1: subtracting() -- return the comparator everybody writes, (a, b) -> a - b. It is
 *         WRONG and you are implementing it on purpose. The test sorts
 *         [MIN_VALUE, MAX_VALUE, 0] with it and pins the wrong answer.
 * TODO 2: comparing() -- the correct one. Item 14's advice is explicit: use the static compare
 *         methods, not relational operators. One of these two is three characters shorter and
 *         it is not the correct one.
 * TODO 3: sortFailureMessage() -- sort a COPY with the given comparator; return the exception
 *         message if it throws, or null if it does not. The test calls it with an intransitive
 *         comparator at two sizes and only one of them throws. Find the boundary yourself and
 *         write it down, then say what that means for a test suite.
 * TODO 4: treeMapSize() -- how many keys a TreeMap with this comparator ends up holding.
 *         "Key", "KEY", "key" under CASE_INSENSITIVE_ORDER is ONE entry. Under natural order it
 *         is three. The comparator, not equals, decides what a duplicate is, and
 *         Contracts.keyRetainedOnDuplicate in docs/dsa/02 is the other half of this.
 */
public final class Ordering {

    private Ordering() {
    }

    /** The subtraction comparator. Wrong on purpose. */
    public static Comparator<Integer> subtracting() {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** The correct comparator. */
    public static Comparator<Integer> comparing() {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** Sorts a copy; returns the failure message, or null if the sort completed. */
    public static String sortFailureMessage(List<Integer> values, Comparator<Integer> order) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** The number of keys a TreeMap under this comparator retains. */
    public static int treeMapSize(Comparator<String> order, List<String> keys) {
        throw new UnsupportedOperationException("TODO 4");
    }
}
