package org.mandfer.practices.collections;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Exercise: docs/dsa/02-contract-matrix.md. Delete this line to begin.")
class ContractsTest {

    @Test
    void hash_maps_accept_null_keys_and_values() {
        assertTrue(Contracts.acceptsNullKey(HashMap::new));
        assertTrue(Contracts.acceptsNullValue(HashMap::new));
        assertTrue(Contracts.acceptsNullKey(LinkedHashMap::new));
    }

    /** The asymmetric one: TreeMap needs to compare keys, so a null key cannot be ordered. */
    @Test
    void tree_map_refuses_a_null_key_but_accepts_a_null_value() {
        assertFalse(Contracts.acceptsNullKey(TreeMap::new));
        assertTrue(Contracts.acceptsNullValue(TreeMap::new));
    }

    @Test
    void concurrent_hash_map_refuses_both() {
        assertFalse(Contracts.acceptsNullKey(ConcurrentHashMap::new));
        assertFalse(Contracts.acceptsNullValue(ConcurrentHashMap::new));
    }

    /**
     * Map.of also refuses nulls, but it cannot be probed the same way and the reason matters:
     * it is immutable, so put() throws UnsupportedOperationException before null is ever
     * considered. A factory method rejects at CONSTRUCTION; a mutable map rejects at
     * MUTATION. Two different exceptions, two different moments, same contract.
     */
    @Test
    void map_of_refuses_nulls_at_construction_not_at_put() {
        assertThrows(NullPointerException.class, () -> Map.of(null, 1));
        assertThrows(NullPointerException.class, () -> Map.of("k", null));

        assertThrows(UnsupportedOperationException.class, () -> Map.of("k", 1).put("other", 2));
    }

    @Test
    void hash_set_and_array_list_accept_null_elements() {
        assertTrue(Contracts.acceptsNullElement(HashSet::new));
        assertTrue(Contracts.acceptsNullElement(ArrayList::new));
    }

    /**
     * The cell that catches people. ArrayDeque is the collection to prefer over LinkedList --
     * and it rejects null, so the swap is not behaviour-preserving if you used null as a
     * sentinel. TreeSet refuses for the same reason TreeMap refuses a null key.
     */
    @Test
    void tree_set_and_array_deque_refuse_null_elements() {
        assertFalse(Contracts.acceptsNullElement(TreeSet::new));
        assertFalse(Contracts.acceptsNullElement(ArrayDeque::new));
    }

    /**
     * Put two keys the comparator calls equal and the map keeps the FIRST key object with the
     * SECOND value. They do not come from the same put, which is Map.put's documented
     * behaviour and almost nobody's mental model.
     */
    @Test
    void a_duplicate_key_keeps_the_first_key_and_the_last_value() {
        Comparator<String> caseInsensitive = String.CASE_INSENSITIVE_ORDER;

        assertEquals("Key", Contracts.keyRetainedOnDuplicate(caseInsensitive, "Key", "KEY"));
        assertEquals(2, Contracts.valueRetainedOnDuplicate(caseInsensitive, "Key", "KEY"));
    }

    /**
     * Same two strings, natural order, and they are not duplicates at all -- so "which key was
     * retained" is not even a question. Whether two keys collide is a property of the
     * comparator, not of the strings. docs/dsa/04 counts the entries.
     */
    @Test
    void whether_they_collide_is_decided_by_the_comparator() {
        assertEquals("Key", Contracts.keyRetainedOnDuplicate(String.CASE_INSENSITIVE_ORDER, "Key", "KEY"));
        assertEquals(1, Contracts.entriesAfterBothPuts(String.CASE_INSENSITIVE_ORDER, "Key", "KEY"));
        assertEquals(2, Contracts.entriesAfterBothPuts(Comparator.naturalOrder(), "Key", "KEY"));
    }
}
