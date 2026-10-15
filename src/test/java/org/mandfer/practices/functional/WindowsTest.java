package org.mandfer.practices.functional;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("Exercise: docs/modernization/22-stream-gatherers.md. Delete this line to begin.")
class WindowsTest {

    private static final List<Integer> VALUES = List.of(1, 2, 3, 4, 5, 6, 7);

    /** Seven elements in windows of three: the last window is short, not dropped. */
    @Test
    void batches_keeps_the_short_final_window() {
        assertEquals(
                List.of(List.of(1, 2, 3), List.of(4, 5, 6), List.of(7)),
                Windows.batches(VALUES, 3));
    }

    @Test
    void batches_of_one_wraps_every_element() {
        assertEquals(
                List.of(List.of(1), List.of(2), List.of(3)),
                Windows.batches(List.of(1, 2, 3), 1));
    }

    @Test
    void deltas_is_one_shorter_than_the_input() {
        assertEquals(List.of(1, 2, 4), Windows.deltas(List.of(1, 2, 4, 8)));
    }

    /**
     * windowSliding(2) emits [[5]] here -- a window of ONE -- not nothing. So the obvious
     * implementation throws ArrayIndexOutOfBoundsException on this input. The gatherer does not
     * guarantee full windows and its name implies that it does.
     */
    @Test
    void deltas_of_a_single_element_is_empty() {
        assertEquals(List.of(), Windows.deltas(List.of(5)));
    }

    @Test
    void deltas_can_be_negative() {
        assertEquals(List.of(-5, 3), Windows.deltas(List.of(10, 5, 8)));
    }

    /** scan emits one element per input. fold would emit exactly one, the last of these. */
    @Test
    void running_totals_emits_one_per_element() {
        assertEquals(List.of(1, 3, 6, 10, 15, 21, 28), Windows.runningTotals(VALUES));
    }

    /**
     * The custom gatherer. 1+2+3+4 is 10, which does not exceed the limit, so the 4 is kept.
     * Adding 5 would reach 15, so the stream stops there -- and stops SHORT, which is the
     * thing no built-in intermediate operation can express.
     */
    @Test
    void take_until_sum_exceeds_stops_the_stream_short() {
        assertEquals(List.of(1, 2, 3, 4), Windows.takeUntilSumExceeds(VALUES, 10));
    }

    @Test
    void take_until_sum_exceeds_can_stop_immediately() {
        assertEquals(List.of(), Windows.takeUntilSumExceeds(List.of(100, 1), 10));
    }

    @Test
    void take_until_sum_exceeds_can_take_everything() {
        assertEquals(VALUES, Windows.takeUntilSumExceeds(VALUES, 1000));
    }

    /** mapConcurrent runs on virtual threads and still returns input order. */
    @Test
    void map_concurrent_preserves_input_order() {
        assertEquals(List.of(10, 20, 30, 40, 50, 60, 70), Windows.timesTenConcurrently(VALUES, 4));
    }
}
