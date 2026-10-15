package org.mandfer.practices.classfile;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mandfer.practices.Door;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Exercise: docs/modernization/24-class-file-api.md. Delete this line to begin.")
class InspectTest {

    @Test
    void reads_a_class_off_the_classpath() throws IOException {
        byte[] bytes = Inspect.bytesOf(Door.class);

        assertTrue(bytes.length > 0, "no bytes read");
        assertEquals((byte) 0xCA, bytes[0], "every class file starts 0xCAFEBABE");
        assertEquals((byte) 0xFE, bytes[1]);
    }

    /**
     * Not pinned to an exact number on purpose: it tracks whatever <java.release> says, and
     * major = release + 44. 65 is Java 21, the floor this repo targets.
     */
    @Test
    void major_version_is_at_least_java_21() throws IOException {
        int major = Inspect.majorVersionOf(Inspect.bytesOf(Door.class));

        assertTrue(major >= 65, "expected >= 65 (Java 21), got " + major);
    }

    /** The constructor you did not write is in the list, under the name you cannot type. */
    @Test
    void method_names_include_the_constructor() throws IOException {
        List<String> methods = Inspect.methodNamesOf(Inspect.bytesOf(Door.class));

        assertEquals(List.of("<init>", "isOpen", "toggle", "toString"), methods);
    }

    @Test
    void field_names_are_the_declared_fields() throws IOException {
        assertEquals(List.of("open"), Inspect.fieldNamesOf(Inspect.bytesOf(Door.class)));
    }

    /** Inspecting the inspector. A final class with a private constructor has exactly one method. */
    @Test
    void works_on_itself() throws IOException {
        List<String> methods = Inspect.methodNamesOf(Inspect.bytesOf(Inspect.class));

        assertTrue(methods.contains("<init>"), methods.toString());
        assertTrue(methods.contains("majorVersionOf"), methods.toString());
    }
}
