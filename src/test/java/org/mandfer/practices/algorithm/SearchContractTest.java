package org.mandfer.practices.algorithm;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("Exercise: docs/dsa/05-binary-search.md. Delete this line to begin.")
class SearchContractTest {

    private static final int[] SORTED = {0, 5, 13, 19, 22, 41, 55, 68, 72, 81, 85, 98};

    @Test
    void finds_every_element_that_is_present() {
        for (int i = 0; i < SORTED.length; i++) {
            assertEquals(i, SearchContract.indexOf(SORTED, SORTED[i]), "value " + SORTED[i]);
        }
    }

    @Test
    void reports_minus_one_for_an_absent_value() {
        assertEquals(-1, SearchContract.indexOf(SORTED, 42));
        assertEquals(-1, SearchContract.indexOf(SORTED, -7));
        assertEquals(-1, SearchContract.indexOf(SORTED, 99));
    }

    @Test
    void finds_the_only_element_of_a_single_element_array() {
        assertEquals(0, SearchContract.indexOf(new int[]{7}, 7));
        assertEquals(-1, SearchContract.indexOf(new int[]{7}, 8));
    }

    /** Unlike the hand-rolled one in this repo, an empty array is not a special case. */
    @Test
    void an_empty_array_is_not_a_special_case() {
        assertEquals(-1, SearchContract.indexOf(new int[0], 1));
        assertEquals(0, SearchContract.insertionPointFor(new int[0], 1));
    }

    /** The decode: -(result) - 1. Defined for present and absent values alike. */
    @Test
    void insertion_point_is_defined_everywhere() {
        assertEquals(0, SearchContract.insertionPointFor(SORTED, -1), "before the first");
        assertEquals(2, SearchContract.insertionPointFor(SORTED, 6), "between 5 and 13");
        assertEquals(6, SearchContract.insertionPointFor(SORTED, 42), "between 41 and 55");
        assertEquals(12, SearchContract.insertionPointFor(SORTED, 99), "past the last");
    }

    /**
     * The second silent failure. Arrays.binarySearch on unsorted input does not throw and does
     * not warn -- the javadoc says the result is undefined. These are the values this JDK
     * actually returns for {9, 3, 7, 1, 5}, and the point is how plausible they look:
     *
     *   search(7) -> 2, which is CORRECT, by luck
     *   search(9) -> -6, meaning "absent, insert at 5", for a value sitting at index 0
     *
     * A method that is sometimes right and sometimes confidently wrong is worse than one that
     * throws, and nothing in the signature warns you.
     */
    @Test
    void searching_unsorted_input_is_undefined_and_silent() {
        int[] unsorted = {9, 3, 7, 1, 5};

        assertEquals(2, SearchContract.searchUnsorted(unsorted, 7), "right answer, by accident");
        assertEquals(-6, SearchContract.searchUnsorted(unsorted, 9), "9 is at index 0");
        assertEquals(-1, SearchContract.searchUnsorted(unsorted, 1), "1 is at index 3");
        assertEquals(-1, SearchContract.searchUnsorted(unsorted, 3), "3 is at index 1");
    }
}
