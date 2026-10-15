package org.mandfer.practices.functional;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("Exercise: docs/12-lambdas-and-method-references.md. Delete this line to begin.")
class TextPipelineTest {

    private static final String MESSY = "  The   QUICK\tbrown\nfox  ";
    private static final String CLEAN = "The quick brown fox";

    private static final List<String> FRUIT = List.of("pear", "fig", "apple", "kiwi", "plum");

    @Test
    void composes_with_the_hand_rolled_interface() {
        assertEquals(CLEAN, TextPipeline.cleanAsStep().apply(MESSY));
    }

    @Test
    void composes_with_unary_operator() {
        assertEquals(CLEAN, TextPipeline.clean().apply(MESSY));
    }

    /**
     * Pins the ORDER of composition, not just the steps. Lowercasing after capitalizing
     * gives "the quick brown fox" and fails here. Both orders compile.
     */
    @Test
    void capitalize_runs_after_lowercase() {
        assertEquals("Abc", TextPipeline.clean().apply("  ABC  "));
    }

    @Test
    void shortest_first_breaks_ties_alphabetically() {
        assertEquals(
                List.of("fig", "kiwi", "pear", "plum", "apple"),
                FRUIT.stream().sorted(TextPipeline.shortestFirst()).toList());
    }

    /**
     * The reversed() trap. comparing(length).thenComparing(natural).reversed() reverses the
     * tie-breaker too and yields [apple, plum, pear, kiwi, fig], which is wrong here.
     * Only comparing(length).reversed().thenComparing(natural) passes.
     */
    @Test
    void longest_first_keeps_ties_alphabetically_ascending() {
        assertEquals(
                List.of("apple", "kiwi", "pear", "plum", "fig"),
                FRUIT.stream().sorted(TextPipeline.longestFirst()).toList());
    }
}
