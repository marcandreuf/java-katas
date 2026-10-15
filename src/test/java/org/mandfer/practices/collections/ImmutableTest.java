package org.mandfer.practices.collections;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Disabled("Exercise: docs/16-collection-factories.md. Delete this line to begin.")
class ImmutableTest {

    @Test
    void defensive_copy_ignores_later_changes_to_the_source() {
        List<String> source = new ArrayList<>(List.of("a", "b"));

        List<String> copy = Immutable.defensiveCopy(source);
        source.add("c");

        assertEquals(List.of("a", "b"), copy, "a copy must not see the later add");
    }

    /**
     * The assertion that earns this document. An unmodifiable VIEW is read-only for you and
     * fully mutable through whoever still holds the source. "Unmodifiable" is a statement
     * about the reference you were handed, not about the data.
     */
    @Test
    void unmodifiable_view_does_see_later_changes_to_the_source() {
        List<String> source = new ArrayList<>(List.of("a", "b"));

        List<String> view = Immutable.unmodifiableView(source);
        source.add("c");

        assertEquals(List.of("a", "b", "c"), view, "a view is a window, not a copy");
    }

    @Test
    void both_refuse_to_be_added_to() {
        List<String> source = new ArrayList<>(List.of("a"));

        assertThrows(UnsupportedOperationException.class, () -> Immutable.defensiveCopy(source).add("x"));
        assertThrows(UnsupportedOperationException.class, () -> Immutable.unmodifiableView(source).add("x"));
    }

    /** Arrays.asList is the third category: fixed length, mutable contents. */
    @Test
    void fixed_size_allows_set_and_refuses_add() {
        List<String> fixed = Immutable.fixedSizeOver("a", "b");

        fixed.set(0, "z");
        assertEquals(List.of("z", "b"), fixed);

        assertThrows(UnsupportedOperationException.class, () -> fixed.add("x"));
    }

    @Test
    void list_of_rejects_a_null_element() {
        assertThrows(NullPointerException.class, () -> List.of("a", null));
    }

    @Test
    void map_of_rejects_a_duplicate_key() {
        assertThrows(IllegalArgumentException.class, () -> Map.of("k", 1, "k", 2));
    }

    /** Stream.toList (16) tolerates nulls. Collectors.toUnmodifiableList (10) does not. */
    @Test
    void the_two_collectors_disagree_about_null() {
        List<String> withNull = Arrays.asList("a", null);

        assertEquals(Arrays.asList("a", null), Immutable.viaStreamToList(withNull));
        assertThrows(NullPointerException.class, () -> Immutable.viaToUnmodifiableList(withNull));
    }

    @Test
    void both_collectors_refuse_to_be_added_to() {
        List<String> source = List.of("a", "b");

        assertThrows(UnsupportedOperationException.class, () -> Immutable.viaStreamToList(source).add("x"));
        assertThrows(UnsupportedOperationException.class, () -> Immutable.viaToUnmodifiableList(source).add("x"));
    }
}
