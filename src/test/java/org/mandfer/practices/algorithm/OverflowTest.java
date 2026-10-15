package org.mandfer.practices.algorithm;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Disabled("Exercise: docs/dsa/09-integer-overflow.md. Delete this line to begin.")
class OverflowTest {

    /**
     * What the repo does today, pinned so you can see it before you change anything.
     * NumberReverse has no test and has never had one.
     */
    @Test
    void the_existing_reversal_wraps_silently() {
        NumberReverse existing = new NumberReverse();

        assertEquals(86871, existing.reverseNumber(17868), "small input: correct");
        assertEquals(-1126087180, existing.reverseNumber(Integer.MAX_VALUE), "large input: wrong, and negative");
        assertEquals(1056389759, existing.reverseNumber(1534236469), "large input: wrong, and plausible");
    }

    @Test
    void the_exact_version_agrees_on_values_that_fit() {
        assertEquals(86871, Overflow.reverseExact(17868));
        assertEquals(321, Overflow.reverseExact(123));
        assertEquals(0, Overflow.reverseExact(0));
    }

    @Test
    void the_exact_version_refuses_to_wrap() {
        assertThrows(ArithmeticException.class, () -> Overflow.reverseExact(Integer.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> Overflow.reverseExact(1534236469));
    }

    @Test
    void the_widened_version_just_answers() {
        assertEquals(7463847412L, Overflow.reverseWidened(Integer.MAX_VALUE));
        assertEquals(9646324351L, Overflow.reverseWidened(1534236469));
        assertEquals(86871L, Overflow.reverseWidened(17868));
    }

    @Test
    void sum_exact_agrees_when_it_fits() {
        assertEquals(18, Overflow.sumExact(List.of(1, 2, 3, 3, 4, 5)));
        assertEquals(0, Overflow.sumExact(List.of()));
    }

    /** DuplicateNumber.getSum returns -1 for this input. It does not mention it. */
    @Test
    void sum_exact_refuses_to_wrap() {
        assertEquals(-1, new DuplicateNumber().getSum(List.of(Integer.MAX_VALUE, Integer.MAX_VALUE, 1)),
                "the existing version wraps to -1");

        assertThrows(ArithmeticException.class,
                () -> Overflow.sumExact(List.of(Integer.MAX_VALUE, Integer.MAX_VALUE, 1)));
    }

    @Test
    void the_checked_duplicate_finder_agrees_on_valid_input() {
        assertEquals(3, Overflow.findDuplicateChecked(List.of(1, 2, 3, 3, 4, 5)));
    }

    /**
     * The existing version returns 12 for [5,5,5], which is not an index, not an element, and
     * not an error. Its Gauss trick assumes 1..n-1 with exactly one duplicate and checks
     * nothing.
     */
    @Test
    void the_checked_duplicate_finder_rejects_input_that_breaks_its_assumption() {
        assertEquals(12, new DuplicateNumber().findDuplicateNumber(List.of(5, 5, 5)),
                "the existing version invents an answer");

        assertThrows(IllegalArgumentException.class, () -> Overflow.findDuplicateChecked(List.of(5, 5, 5)));
    }
}
