package org.mandfer.practices.equality;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Exercise: docs/dsa/03-equals-and-hashcode.md. Delete this line to begin.")
class KeysTest {

    /**
     * Three instances that all say they are equal to each other, in a HashSet, and the set
     * holds THREE. Nothing threw. This is what breaking the contract actually looks like.
     */
    @Test
    void equals_without_hashcode_loses_the_deduplication() {
        List<Object> three = List.of(new Keys.EqualsOnly(1), new Keys.EqualsOnly(1), new Keys.EqualsOnly(1));

        assertEquals(3, Keys.hashSetSize(three), "equal objects, three entries");
    }

    @Test
    void overriding_both_deduplicates() {
        assertEquals(1, Keys.hashSetSize(List.of(new Keys.Correct(1), new Keys.Correct(1), new Keys.Correct(1))));
    }

    /** The record wrote both methods for you, consistently, from the components. */
    @Test
    void a_record_gets_it_right_for_free() {
        assertEquals(1, Keys.hashSetSize(List.of(new Keys.AsRecord(1), new Keys.AsRecord(1), new Keys.AsRecord(1))));
    }

    @Test
    void a_broken_key_cannot_be_looked_up_again() {
        assertFalse(Keys.roundTripsInHashMap(new Keys.EqualsOnly(7), new Keys.EqualsOnly(7)),
                "put it in, asked for an equal key, got nothing back");
    }

    @Test
    void a_correct_key_round_trips() {
        assertTrue(Keys.roundTripsInHashMap(new Keys.Correct(7), new Keys.Correct(7)));
        assertTrue(Keys.roundTripsInHashMap(new Keys.AsRecord(7), new Keys.AsRecord(7)));
    }

    /**
     * The reason this ships. The same broken key works perfectly in a List, because
     * List.contains uses equals and never calls hashCode. A test suite that exercises the
     * value type through a List is green, and the HashMap in production is losing entries.
     */
    @Test
    void the_same_broken_key_works_in_a_list() {
        List<Keys.EqualsOnly> list = new ArrayList<>(List.of(new Keys.EqualsOnly(7)));

        assertTrue(list.contains(new Keys.EqualsOnly(7)), "List.contains only needs equals");
        assertTrue(new Keys.EqualsOnly(7).equals(new Keys.EqualsOnly(7)), "equals itself is fine");
    }

    /** TODO 4: Comparable is the only difference, and it is worth about 65x. See the doc. */
    @Test
    void the_colliding_key_becomes_comparable() {
        assertTrue(Comparable.class.isAssignableFrom(Keys.ComparableColliding.class),
                "ComparableColliding must implement Comparable -- that is the whole exercise");
        assertFalse(Comparable.class.isAssignableFrom(Keys.Colliding.class),
                "Colliding is the control and must stay non-Comparable");
    }

    @Test
    void both_colliding_keys_still_round_trip_correctly() {
        assertTrue(Keys.roundTripsInHashMap(new Keys.Colliding(7), new Keys.Colliding(7)));
        assertTrue(Keys.roundTripsInHashMap(new Keys.ComparableColliding(7), new Keys.ComparableColliding(7)));
    }
}
