package org.mandfer.practices.diagnostics;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Exercise: docs/20-free-diagnostics.md. Delete this line to begin.")
class NullPathsTest {

    private static final NullPaths.Person EMPLOYER_NULL =
            new NullPaths.Person("x", null);
    private static final NullPaths.Person ADDRESS_NULL =
            new NullPaths.Person("x", new NullPaths.Company(null));
    private static final NullPaths.Person CITY_NULL =
            new NullPaths.Person("x", new NullPaths.Company(new NullPaths.Address(null)));

    @Test
    void walks_a_complete_chain() {
        NullPaths.Person ok =
                new NullPaths.Person("x", new NullPaths.Company(new NullPaths.Address("Madrid")));

        assertEquals("MADRID", NullPaths.cityOf(ok));
    }

    /**
     * Read the message, not just the assertion. The JVM names BOTH halves: what you tried to
     * call, and which expression returned null. Before JEP 358 this was
     * "NullPointerException" and a line number, on a line with three calls on it.
     */
    @Test
    void the_message_names_the_link_that_was_null() {
        NullPointerException thrown =
                assertThrows(NullPointerException.class, () -> NullPaths.cityOf(EMPLOYER_NULL));

        assertTrue(thrown.getMessage().contains("Person.employer()"),
                "should name the accessor that returned null: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("Company.address()"),
                "should name the call that failed: " + thrown.getMessage());
    }

    /** One link further down the same chain, and the message moves with it. */
    @Test
    void the_message_moves_when_the_null_moves() {
        NullPointerException thrown =
                assertThrows(NullPointerException.class, () -> NullPaths.cityOf(ADDRESS_NULL));

        assertTrue(thrown.getMessage().contains("Company.address()"),
                "should name the accessor that returned null: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("Address.city()"),
                "should name the call that failed: " + thrown.getMessage());
    }

    @Test
    void even_the_last_link_is_named() {
        NullPointerException thrown =
                assertThrows(NullPointerException.class, () -> NullPaths.cityOf(CITY_NULL));

        assertTrue(thrown.getMessage().contains("Address.city()"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("toUpperCase"), thrown.getMessage());
    }

    @Test
    void optional_walk_is_empty_at_every_broken_link() {
        assertEquals(Optional.empty(), NullPaths.cityOrEmpty(EMPLOYER_NULL));
        assertEquals(Optional.empty(), NullPaths.cityOrEmpty(ADDRESS_NULL));
        assertEquals(Optional.empty(), NullPaths.cityOrEmpty(CITY_NULL));
    }

    @Test
    void optional_walk_finds_a_complete_chain() {
        NullPaths.Person ok =
                new NullPaths.Person("x", new NullPaths.Company(new NullPaths.Address("Madrid")));

        assertEquals(Optional.of("MADRID"), NullPaths.cityOrEmpty(ok));
    }
}
