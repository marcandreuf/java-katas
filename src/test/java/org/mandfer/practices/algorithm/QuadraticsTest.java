package org.mandfer.practices.algorithm;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("Exercise: docs/dsa/07-accidental-quadratics.md. Delete this line to begin.")
class QuadraticsTest {

    private static final List<Integer> VALUES = List.of(1, 2, 3, 2, 1, 4);

    /** The rewrite is only a rewrite if it returns the same thing, including the order. */
    @Test
    void both_versions_agree() {
        assertEquals(List.of(2, 1), Quadratics.duplicatesByContains(VALUES));
        assertEquals(List.of(2, 1), Quadratics.duplicatesBySet(VALUES));
        assertEquals(Quadratics.duplicatesByContains(VALUES), Quadratics.duplicatesBySet(VALUES));
    }

    @Test
    void both_versions_agree_on_a_thousand_elements() {
        List<Integer> many = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            many.add(i % 400);
        }

        assertEquals(Quadratics.duplicatesByContains(many), Quadratics.duplicatesBySet(many));
    }

    @Test
    void no_duplicates_is_an_empty_list() {
        assertEquals(List.of(), Quadratics.duplicatesBySet(List.of(1, 2, 3)));
        assertEquals(List.of(), Quadratics.duplicatesByContains(List.of(1, 2, 3)));
    }

    @Test
    void first_repeat_is_an_index_not_a_value() {
        assertEquals(3, Quadratics.firstRepeatIndex(List.of(1, 2, 3, 2)));
        assertEquals(-1, Quadratics.firstRepeatIndex(List.of(1, 2, 3)));
        assertEquals(1, Quadratics.firstRepeatIndex(List.of(7, 7)));
    }

    @Test
    void products_except_self() {
        assertArrayEquals(new int[]{24, 12, 8, 6}, Quadratics.productsExceptSelf(new int[]{1, 2, 3, 4}));
        assertArrayEquals(new int[]{3, 2}, Quadratics.productsExceptSelf(new int[]{2, 3}));
        assertArrayEquals(new int[]{1}, Quadratics.productsExceptSelf(new int[]{5}));
    }

    /** One zero and the division trick breaks. Two zeros and it breaks differently. */
    @Test
    void products_except_self_handles_zeros() {
        assertArrayEquals(new int[]{24, 0, 0, 0}, Quadratics.productsExceptSelf(new int[]{0, 2, 3, 4}));
        assertArrayEquals(new int[]{0, 0, 0, 0}, Quadratics.productsExceptSelf(new int[]{0, 2, 0, 4}));
    }
}
