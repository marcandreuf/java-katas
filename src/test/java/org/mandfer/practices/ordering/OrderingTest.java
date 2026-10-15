package org.mandfer.practices.ordering;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Exercise: docs/dsa/04-ordering-contracts.md. Delete this line to begin.")
class OrderingTest {

    private static final List<Integer> EXTREMES =
            List.of(Integer.MIN_VALUE, Integer.MAX_VALUE, 0);

    /** (a, b) -> a - b overflows, and sorts wrong with no exception at all. */
    @Test
    void the_subtraction_comparator_sorts_wrongly() {
        List<Integer> values = new ArrayList<>(EXTREMES);

        values.sort(Ordering.subtracting());

        assertEquals(List.of(0, Integer.MAX_VALUE, Integer.MIN_VALUE), values,
                "MIN_VALUE sorted last: a - b overflowed");
    }

    @Test
    void the_correct_comparator_sorts_correctly() {
        List<Integer> values = new ArrayList<>(EXTREMES);

        values.sort(Ordering.comparing());

        assertEquals(List.of(Integer.MIN_VALUE, 0, Integer.MAX_VALUE), values);
    }

    /** Below the merge threshold TimSort never compares across runs, so it never notices. */
    @Test
    void a_broken_comparator_is_silent_on_a_small_list() {
        assertNull(Ordering.sortFailureMessage(manyTies(8), intransitive()),
                "8 elements: no exception, and the order is still wrong");
    }

    /** Above it, the JDK refuses. Same comparator, same data shape, different size. */
    @Test
    void the_same_comparator_throws_on_a_larger_list() {
        String message = Ordering.sortFailureMessage(manyTies(100), intransitive());

        assertTrue(message != null && message.contains("violates its general contract"),
                "expected the TimSort contract check, got: " + message);
    }

    @Test
    void a_correct_comparator_never_fails_at_any_size() {
        assertNull(Ordering.sortFailureMessage(manyTies(8), Ordering.comparing()));
        assertNull(Ordering.sortFailureMessage(manyTies(1000), Ordering.comparing()));
    }

    /** The comparator decides what a duplicate is. equals is not consulted. */
    @Test
    void a_case_insensitive_comparator_makes_three_keys_into_one() {
        List<String> keys = List.of("Key", "KEY", "key");

        assertEquals(1, Ordering.treeMapSize(String.CASE_INSENSITIVE_ORDER, keys));
        assertEquals(3, Ordering.treeMapSize(Comparator.naturalOrder(), keys));
    }

    private static Comparator<Integer> intransitive() {
        return (x, y) -> x < y ? -1 : 1;
    }

    private static List<Integer> manyTies(int size) {
        List<Integer> values = new ArrayList<>(size);
        Random random = new Random(1);
        for (int i = 0; i < size; i++) {
            values.add(random.nextInt(3));
        }
        return values;
    }
}
