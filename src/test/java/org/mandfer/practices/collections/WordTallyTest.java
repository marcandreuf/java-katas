package org.mandfer.practices.collections;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Exercise: docs/modernization/14-map-defaults-and-collectors.md. Delete this line to begin.")
class WordTallyTest {

    private static final List<String> WORDS =
            List.of("fig", "pear", "fig", "apple", "pear", "fig", "kiwi");

    private static final Map<String, Integer> EXPECTED =
            Map.of("fig", 3, "pear", 2, "apple", 1, "kiwi", 1);

    @Test
    void merge_counts_every_word() {
        assertEquals(EXPECTED, WordTally.tallyWithMerge(WORDS));
    }

    @Test
    void compute_if_absent_counts_every_word() {
        assertEquals(EXPECTED, WordTally.tallyWithComputeIfAbsent(WORDS));
    }

    /** groupingBy + counting() gives you Long. You do not get a say in it. */
    @Test
    void grouping_by_counts_every_word_as_long() {
        assertEquals(
                Map.of("fig", 3L, "pear", 2L, "apple", 1L, "kiwi", 1L),
                WordTally.tallyWithGroupingBy(WORDS));
    }

    @Test
    void empty_input_tallies_to_an_empty_map() {
        assertEquals(Map.of(), WordTally.tallyWithMerge(List.of()));
    }

    /**
     * The toMap trap. Two-argument toMap has no way to combine values, so a repeated key is an
     * IllegalStateException at collection time -- not a silent overwrite, which is what
     * everyone expects it to do.
     */
    @Test
    void strict_to_map_rejects_a_duplicate_key() {
        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> WordTally.lengthsByWordStrict(WORDS));

        assertTrue(thrown.getMessage().contains("Duplicate key"), thrown.getMessage());
    }

    @Test
    void strict_to_map_is_fine_when_keys_are_distinct() {
        assertEquals(
                Map.of("fig", 3, "pear", 4, "apple", 5),
                WordTally.lengthsByWordStrict(List.of("fig", "pear", "apple")));
    }

    @Test
    void lenient_to_map_tolerates_duplicates() {
        assertEquals(
                Map.of("fig", 3, "pear", 4, "apple", 5, "kiwi", 4),
                WordTally.lengthsByWordLenient(WORDS));
    }

    @Test
    void top_words_are_most_frequent_first() {
        assertEquals(List.of("fig", "pear"), WordTally.topWords(WORDS, 2));
    }

    /** apple and kiwi both occur once, so they must come back alphabetically, not reversed. */
    @Test
    void top_words_break_ties_alphabetically_ascending() {
        assertEquals(List.of("fig", "pear", "apple", "kiwi"), WordTally.topWords(WORDS, 4));
    }

    @Test
    void top_words_caps_at_the_number_available() {
        assertEquals(List.of("fig", "pear", "apple", "kiwi"), WordTally.topWords(WORDS, 99));
    }
}
