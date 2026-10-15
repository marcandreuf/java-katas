package org.mandfer.practices.random;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Exercise: docs/19-randomness-and-numerics.md. Delete this line to begin.")
class RollsTest {

    private static final String ALGORITHM = "L64X128MixRandom";

    /** Reproducibility is the testability. Note this asserts equality, not fixed values. */
    @Test
    void the_same_seed_gives_the_same_sequence() {
        assertEquals(
                Rolls.rolls(Rolls.seeded(ALGORITHM, 42L), 20),
                Rolls.rolls(Rolls.seeded(ALGORITHM, 42L), 20));
    }

    @Test
    void a_different_seed_gives_a_different_sequence() {
        assertNotEquals(
                Rolls.rolls(Rolls.seeded(ALGORITHM, 42L), 20),
                Rolls.rolls(Rolls.seeded(ALGORITHM, 43L), 20));
    }

    @Test
    void every_roll_is_a_die_face() {
        List<Integer> rolls = Rolls.rolls(Rolls.seeded(ALGORITHM, 7L), 500);

        assertEquals(500, rolls.size());
        assertTrue(rolls.stream().allMatch(r -> r >= 1 && r <= 6), "out of range: " + rolls);
    }

    @Test
    void every_face_turns_up_eventually() {
        assertEquals(
                List.of(1, 2, 3, 4, 5, 6),
                Rolls.rolls(Rolls.seeded(ALGORITHM, 7L), 500).stream().distinct().sorted().toList());
    }

    /**
     * TODO 4 -- nothing to implement. This test passes as written, and what it demonstrates
     * is the bug in RandomFileLines.java:32. Two generators seeded from the same millisecond
     * are not two generators. On a fast machine, or in a loop, that is exactly what you get.
     */
    @Test
    void same_clock_seed_gives_the_same_sequence() {
        long sameMillisecond = 1760000000000L;

        assertEquals(
                new Random(sameMillisecond).ints(10, 1, 7).boxed().toList(),
                new Random(sameMillisecond).ints(10, 1, 7).boxed().toList());
    }

    @Test
    void clamp_pulls_values_into_range() {
        assertEquals(0, Rolls.clampedIndex(-5, 10));
        assertEquals(9, Rolls.clampedIndex(20, 10));
        assertEquals(3, Rolls.clampedIndex(3, 10));
    }

    /** Math.clamp refuses an inverted range instead of returning nonsense. Math.min/max do not. */
    @Test
    void clamp_rejects_an_empty_range() {
        assertThrows(IllegalArgumentException.class, () -> Rolls.clampedIndex(3, 0));
    }
}
