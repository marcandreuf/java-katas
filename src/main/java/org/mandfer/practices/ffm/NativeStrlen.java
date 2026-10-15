package org.mandfer.practices.ffm;

/**
 * Calling C's strlen from Java with no JNI, no header file and no native build step.
 *
 * The Foreign Function and Memory API (JEP 454) went final in Java 22, replacing two things:
 * JNI, which needed a C compiler and a shared library per platform, and sun.misc.Unsafe, which
 * needed you to be wrong quietly. Nothing in this repo uses either, which is why this is new
 * code. See docs/23-foreign-function-and-memory.md.
 *
 * NOTE: needs --release 22, and --enable-native-access to run without a warning.
 * See docs/env-setup.md.
 *
 * TODO 1: get the Linker and look strlen up in the default lookup, which is libc on Linux and
 *         macOS. Note that find() returns Optional -- the symbol may genuinely not be there.
 * TODO 2: build the downcall handle. FunctionDescriptor.of(JAVA_LONG, ADDRESS) says
 *         "returns size_t, takes a pointer". Getting this descriptor wrong does not fail to
 *         compile and does not throw -- it returns garbage or crashes the VM. That is the
 *         cost of this API and the thing to write down.
 * TODO 3: allocate the string in a confined Arena with try-with-resources, and call it.
 *         The Arena is the whole safety story: when the block exits, the memory is freed and
 *         any MemorySegment that escaped becomes unusable rather than a dangling pointer.
 * TODO 4: run the test and explain why strlen("café") is 5 while "café".length() is 4.
 */
public final class NativeStrlen {

    private NativeStrlen() {
    }

    /** The C strlen of the UTF-8 encoding of text, not counting the terminating NUL. */
    public static long strlen(String text) {
        throw new UnsupportedOperationException("TODO 1");
    }
}
