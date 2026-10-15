package org.mandfer.practices.algorithm;

/**
 * The java.util.Arrays.binarySearch contract, which has two silent failure modes.
 *
 * Stage 05 pairs this with BinarySearch.java, the hand-rolled search already in this repo,
 * which is broken and whose eight tests are green. Write this one first: it is the contract the
 * broken one fails to implement. See docs/dsa/05-binary-search.md.
 *
 * TODO 1: indexOf -- Arrays.binarySearch, but return -1 for "absent" rather than the negative
 *         encoding. You have to decode the return value to do that, which is the point.
 * TODO 2: insertionPointFor -- the index at which target WOULD be inserted to keep the array
 *         sorted, whether or not it is already present. The encoding is -(insertion point) - 1,
 *         so the decode is -(result) - 1. Get the sign and the off-by-one right; the test
 *         checks both ends and the middle.
 * TODO 3: searchUnsorted -- call Arrays.binarySearch on an array that is NOT sorted and return
 *         whatever it says. It does not throw and it does not warn. The javadoc says the result
 *         is undefined, and the test pins the specific wrong answer this JDK gives, so that you
 *         have watched an undefined result behave like a reliable one.
 *
 *         That is the failure mode worth remembering: a contract you broke, a plausible number
 *         back, and no exception anywhere.
 */
public final class SearchContract {

    private SearchContract() {
    }

    /** The index of target, or -1 if absent. Requires a sorted array. */
    public static int indexOf(int[] sorted, int target) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** Where target would go to keep the array sorted. Defined whether present or not. */
    public static int insertionPointFor(int[] sorted, int target) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** Whatever Arrays.binarySearch returns for an unsorted array. Undefined by contract. */
    public static int searchUnsorted(int[] unsorted, int target) {
        throw new UnsupportedOperationException("TODO 3");
    }
}
