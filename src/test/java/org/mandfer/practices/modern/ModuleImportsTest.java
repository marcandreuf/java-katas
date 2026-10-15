package org.mandfer.practices.modern;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("Exercise: docs/modernization/27-module-imports-and-compact-sources.md. Delete this line to begin.")
class ModuleImportsTest {

    @Test
    void word_lengths_keeps_insertion_order() {
        Map<String, Integer> lengths = ModuleImports.wordLengths(List.of("pear", "fig", "apple"));

        assertEquals(List.of("pear", "fig", "apple"), List.copyOf(lengths.keySet()));
        assertEquals(Map.of("pear", 4, "fig", 3, "apple", 5), lengths);
    }

    @Test
    void joined_is_comma_separated() {
        assertEquals("a,b,c", ModuleImports.joined(List.of("a", "b", "c")));
    }

    @Test
    void joined_of_nothing_is_empty() {
        assertEquals("", ModuleImports.joined(List.of()));
    }

    /** Same comparator shape as docs/modernization/12 and docs/modernization/14. Third time; it should be automatic by now. */
    @Test
    void longest_first_drops_blanks_and_breaks_ties_alphabetically() {
        assertEquals(
                List.of("apple", "kiwi", "pear", "fig"),
                ModuleImports.longestFirst(List.of("pear", "", "fig", "apple", "  ", "kiwi")));
    }
}
