package org.mandfer.practices.modern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Disabled("Exercise: docs/modernization/28-flexible-constructor-bodies.md. Delete this line to begin.")
class ValidatedTest {

    @BeforeEach
    void reset() {
        Validated.Measured.resetConstructions();
    }

    @Test
    void a_valid_size_is_accepted() {
        assertEquals(7, new Validated.NonNegative(7).size);
        assertEquals(1, Validated.Measured.constructions());
    }

    @Test
    void a_negative_size_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new Validated.NonNegative(-1));
    }

    /**
     * The assertion that proves the ORDER, which is the whole feature. If validation happens
     * after super(size), the base constructor has already run and this counter is 1. Only a
     * check placed before super(...) keeps it at 0.
     *
     * Before Java 25 this test was impossible to pass without the static-helper trick.
     */
    @Test
    void the_base_constructor_never_runs_for_a_rejected_argument() {
        assertThrows(IllegalArgumentException.class, () -> new Validated.NonNegative(-1));

        assertEquals(0, Validated.Measured.constructions(),
                "super(size) must not have been reached");
    }

    @Test
    void the_legacy_workaround_rejects_the_same_input() {
        assertThrows(IllegalArgumentException.class, () -> new Validated.Legacy(-1));
        assertEquals(0, Validated.Measured.constructions());
    }

    @Test
    void the_legacy_workaround_accepts_the_same_input() {
        assertEquals(7, new Validated.Legacy(7).size);
    }
}
