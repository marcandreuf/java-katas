package org.mandfer.practices.functional;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("Exercise: docs/17-stream-and-optional-additions.md. Delete this line to begin.")
class StreamAdditionsTest {

    /** The 10 in the middle is the whole point of this fixture. */
    private static final List<Integer> VALUES = List.of(1, 2, 3, 10, 4, 5);

    @Test
    void take_while_stops_at_the_first_failure() {
        assertEquals(List.of(1, 2, 3), StreamAdditions.leadingBelow(VALUES, 5));
    }

    /** Same predicate, same input, different answer. filter does not stop. */
    @Test
    void filter_keeps_looking_after_the_first_failure() {
        assertEquals(List.of(1, 2, 3, 4), StreamAdditions.allBelow(VALUES, 5));
    }

    @Test
    void drop_while_is_not_the_complement_of_filter() {
        assertEquals(List.of(10, 4, 5), StreamAdditions.afterLeadingBelow(VALUES, 5));
    }

    @Test
    void three_argument_iterate_is_bounded_without_limit() {
        assertEquals(List.of(1, 2, 4, 8, 16, 32, 64), StreamAdditions.powersOfTwoBelow(100));
    }

    @Test
    void or_falls_through_to_the_second() {
        assertEquals(Optional.of("b"), StreamAdditions.firstNonEmpty(Optional.empty(), Optional.of("b")));
    }

    @Test
    void or_keeps_the_first_when_it_is_present() {
        assertEquals(Optional.of("a"), StreamAdditions.firstNonEmpty(Optional.of("a"), Optional.of("b")));
    }

    @Test
    void or_is_empty_when_neither_has_a_value() {
        assertEquals(Optional.empty(), StreamAdditions.firstNonEmpty(Optional.empty(), Optional.empty()));
    }

    @Test
    void optional_stream_drops_the_empties() {
        assertEquals(
                List.of("a", "c"),
                StreamAdditions.presentValues(List.of(Optional.of("a"), Optional.empty(), Optional.of("c"))));
    }

    @Test
    void teeing_computes_min_and_max_in_one_pass() {
        assertEquals("min=1 max=10", StreamAdditions.summarize(VALUES));
    }
}
