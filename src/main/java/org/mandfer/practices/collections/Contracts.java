package org.mandfer.practices.collections;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Four probes that make you discover the collection contract matrix rather than read it.
 *
 * docs/dsa/research.md argues that collection choice is load-bearing because of CONTRACTS --
 * null policy, iteration order, what counts as a duplicate -- and not because of Big-O. This is
 * where that claim gets tested. Every method here is three lines. The value is entirely in the
 * table the tests build, and in which cells surprise you.
 *
 * See docs/dsa/02-contract-matrix.md.
 *
 * TODO 1: acceptsNullKey  -- try it, catch NullPointerException, return the answer. Do NOT let
 *         the exception escape: the whole point is to turn a thrown contract into a boolean.
 *         Catch NullPointerException specifically and not Exception, because the test has a
 *         case where a DIFFERENT exception means something else entirely -- Map.of is
 *         immutable, so put() throws UnsupportedOperationException before null is considered.
 *         A factory rejects at construction; a mutable map rejects at mutation.
 * TODO 2: acceptsNullValue -- same shape. One map in the test differs between key and value,
 *         and that asymmetry is the interesting cell.
 * TODO 3: acceptsNullElement -- the Collection version. One of the four in the test rejects
 *         null, and it is the one docs/dsa/01 tells you to prefer. Swapping to it is therefore
 *         not behaviour-preserving.
 * TODO 4: keyRetainedOnDuplicate -- put two keys the comparator considers equal, then report
 *         which key OBJECT the map kept. Predict it before you run it. Then do the same with
 *         the value and notice they do not come from the same put.
 */
public final class Contracts {

    private Contracts() {
    }

    /** True if a map from this factory tolerates a null key. */
    public static boolean acceptsNullKey(Supplier<Map<String, Integer>> factory) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** True if a map from this factory tolerates a null value. */
    public static boolean acceptsNullValue(Supplier<Map<String, Integer>> factory) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** True if a collection from this factory tolerates a null element. */
    public static boolean acceptsNullElement(Supplier<Collection<String>> factory) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /**
     * Puts first then second into a TreeMap ordered by the comparator, and returns the key the
     * map ended up holding.
     */
    public static String keyRetainedOnDuplicate(Comparator<String> order, String first, String second) {
        throw new UnsupportedOperationException("TODO 4");
    }

    /** The value the same map ended up holding. Not necessarily from the same put as the key. */
    public static int valueRetainedOnDuplicate(Comparator<String> order, String first, String second) {
        throw new UnsupportedOperationException("TODO 4");
    }

    /** How many entries the map holds after both puts. 1 if the comparator called them equal. */
    public static int entriesAfterBothPuts(Comparator<String> order, String first, String second) {
        throw new UnsupportedOperationException("TODO 4");
    }
}
