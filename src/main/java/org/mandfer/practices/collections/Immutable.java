package org.mandfer.practices.collections;

import java.util.List;

/**
 * Five things called "immutable list" in Java, three of which are not.
 *
 * The rewrite target is ArgsRandom.java:12 -- new ArrayList<String>(Arrays.asList(args)) --
 * which wraps an array in a fixed-size list and then copies it into a growable one, two
 * conversions to get back to where it started. The100DoorsCyberDojoTest:46 looks like a second
 * site, but it builds 100 distinct mutable Doors; see the doc.
 * See docs/modernization/16-collection-factories.md.
 *
 * TODO 1: defensiveCopy  -- the caller must not be able to change the result afterwards, even
 *         by mutating the list they handed in. One method call does this since Java 10.
 * TODO 2: unmodifiableView -- Collections.unmodifiableList. The test asserts that mutating the
 *         SOURCE changes the result. Read that test before you write this, and then write
 *         down the difference between "unmodifiable" and "immutable", because the JDK javadoc
 *         means two different things by them and this is the bug it causes.
 * TODO 3: fixedSizeOver -- Arrays.asList. set() works, add() does not. Name the third
 *         category that makes.
 * TODO 4: viaStreamToList and viaToUnmodifiableList. They differ on exactly one input, and
 *         the test uses it.
 */
public final class Immutable {

    private Immutable() {
    }

    /** A copy the caller cannot affect, before or after this returns. */
    public static List<String> defensiveCopy(List<String> source) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** A read-only window onto source. Not a copy. */
    public static List<String> unmodifiableView(List<String> source) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** Fixed length, mutable elements. */
    public static List<String> fixedSizeOver(String... items) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** Collected with Stream.toList(), which arrived in Java 16. */
    public static List<String> viaStreamToList(List<String> source) {
        throw new UnsupportedOperationException("TODO 4");
    }

    /** Collected with Collectors.toUnmodifiableList(), which arrived in Java 10. */
    public static List<String> viaToUnmodifiableList(List<String> source) {
        throw new UnsupportedOperationException("TODO 4");
    }
}
