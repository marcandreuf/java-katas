package org.mandfer.practices.collections;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SequencedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Exercise: docs/dsa/10-jdk-assemblies.md. Delete this line to begin.")
class AssembliesTest {

    /**
     * A complete LRU cache. "a" survives the eviction because it was READ, which is the entire
     * difference between least-recently-used and least-recently-inserted.
     */
    @Test
    void the_lru_cache_evicts_the_least_recently_used() {
        Map<String, Integer> cache = Assemblies.lruCache(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);

        cache.get("a");
        cache.put("d", 4);

        assertEquals(List.of("c", "a", "d"), new ArrayList<>(cache.keySet()));
        assertFalse(cache.containsKey("b"), "b was least recently used");
    }

    @Test
    void the_lru_cache_keeps_evicting() {
        Map<String, Integer> cache = Assemblies.lruCache(3);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        cache.get("a");
        cache.put("d", 4);

        cache.get("c");
        cache.put("e", 5);

        assertEquals(List.of("d", "c", "e"), new ArrayList<>(cache.keySet()));
        assertEquals(3, cache.size());
    }

    /** Insertion order is wrong on purpose. The enum's declaration order wins. */
    @Test
    void the_enum_map_iterates_in_declaration_order() {
        Map<Assemblies.Phase, String> labels = Assemblies.phaseLabels(Map.of(
                Assemblies.Phase.SHIP, "shipping",
                Assemblies.Phase.PLAN, "planning",
                Assemblies.Phase.BUILD, "building"));

        assertEquals(
                List.of(Assemblies.Phase.PLAN, Assemblies.Phase.BUILD, Assemblies.Phase.SHIP),
                new ArrayList<>(labels.keySet()));
    }

    /**
     * EnumMap's order is guaranteed and it is still not a SequencedMap, so there is no
     * firstEntry() on it. LinkedHashMap and TreeMap are; EnumMap is the odd one out.
     */
    @Test
    void the_enum_map_is_not_a_sequenced_map_despite_its_guaranteed_order() {
        Map<Assemblies.Phase, String> labels =
                Assemblies.phaseLabels(Map.of(Assemblies.Phase.PLAN, "planning"));

        assertFalse(labels instanceof SequencedMap, "EnumMap does not implement SequencedMap");
        assertTrue(new TreeMap<>() instanceof SequencedMap, "TreeMap does");
    }

    @Test
    void band_for_finds_the_enclosing_band() {
        NavigableMap<Integer, String> thresholds = new TreeMap<>(Map.of(0, "low", 50, "mid", 100, "high"));

        assertEquals("low", Assemblies.bandFor(thresholds, 0));
        assertEquals("low", Assemblies.bandFor(thresholds, 49));
        assertEquals("mid", Assemblies.bandFor(thresholds, 50));
        assertEquals("mid", Assemblies.bandFor(thresholds, 99));
        assertEquals("high", Assemblies.bandFor(thresholds, 100));
        assertEquals("high", Assemblies.bandFor(thresholds, 1_000_000));
    }

    @Test
    void band_for_has_nothing_below_the_lowest_threshold() {
        NavigableMap<Integer, String> thresholds = new TreeMap<>(Map.of(0, "low", 50, "mid"));

        assertNull(Assemblies.bandFor(thresholds, -1));
    }

    /**
     * ArrayDeque iterates head-first, so a stack pushed a, b, c iterates c, b, a. That is the
     * opposite of java.util.Stack, which iterates bottom-first because it extends Vector --
     * the trap when migrating off it.
     */
    @Test
    void the_undo_stack_is_last_in_first_out() {
        Deque<String> stack = Assemblies.undoStack(List.of("a", "b", "c"));

        assertEquals("c", stack.peek());
        assertEquals(List.of("c", "b", "a"), new ArrayList<>(stack));
        assertEquals("c", stack.pop());
        assertEquals("b", stack.pop());
    }
}
