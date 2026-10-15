package org.mandfer.practices.ffm;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("Exercise: docs/modernization/23-foreign-function-and-memory.md. Delete this line to begin.")
@EnabledOnOs({OS.LINUX, OS.MAC})
class NativeStrlenTest {

    @Test
    void counts_ascii_characters() {
        assertEquals(5L, NativeStrlen.strlen("hello"));
    }

    @Test
    void an_empty_string_is_zero_long() {
        assertEquals(0L, NativeStrlen.strlen(""));
    }

    /**
     * TODO 4. strlen counts BYTES up to the NUL, and Arena.allocateFrom encodes as UTF-8, so
     * an accented character costs two. "café".length() is 4 in Java and 5 in C. Every
     * off-by-one bug at a native boundary is this.
     */
    @Test
    void counts_utf8_bytes_not_java_characters() {
        assertEquals(4, "café".length(), "Java counts chars");
        assertEquals(5L, NativeStrlen.strlen("café"), "C counts bytes");
    }

    @Test
    void a_three_byte_codepoint_costs_three() {
        assertEquals(1, "€".length(), "one char");
        assertEquals(3L, NativeStrlen.strlen("€"), "three UTF-8 bytes");
    }
}
