package org.mandfer.practices.classfile;

import java.io.IOException;
import java.util.List;

/**
 * Reading a .class file with the JDK's own parser instead of ASM.
 *
 * The Class-File API (JEP 484) went final in Java 24. Before it, anything that wanted to read
 * or write bytecode shipped a copy of ASM -- which is why a library that did bytecode tricks
 * broke on every new Java release until its ASM dependency caught up. This API is maintained
 * in lockstep with the class file format, so it cannot lag.
 *
 * See docs/24-class-file-api.md. NOTE: needs --release 24.
 *
 * TODO 1: bytesOf() -- read a class's own bytes back off the classpath. The resource name of
 *         a class is not its fully qualified name; work out what it is. Class.getResourceAsStream
 *         resolves relative to the class's package, which makes this shorter than you expect.
 * TODO 2: majorVersionOf() -- ClassFile.of().parse(bytes) and read the version. The mapping is
 *         major = release + 44, so Java 21 is 65. This is how a tool tells you
 *         "class file version 69.0; this version of the JVM recognizes up to 65".
 * TODO 3: methodNamesOf() -- every method, including the ones you did not write. There will be
 *         at least one you did not write, and knowing its name is the point.
 * TODO 4: fieldNamesOf() -- same for fields.
 */
public final class Inspect {

    private Inspect() {
    }

    /** The class file bytes for the given class, read off the classpath. */
    public static byte[] bytesOf(Class<?> type) throws IOException {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** The class file major version. 65 is Java 21, 69 is Java 25. */
    public static int majorVersionOf(byte[] classBytes) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** Every method name in the class file, in declaration order. */
    public static List<String> methodNamesOf(byte[] classBytes) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** Every field name in the class file, in declaration order. */
    public static List<String> fieldNamesOf(byte[] classBytes) {
        throw new UnsupportedOperationException("TODO 4");
    }
}
