package org.mandfer.practices.equality;

import java.util.List;

/**
 * What a HashMap does when a key breaks its contract, and the 65x nobody mentions.
 *
 * This is the stage the whole DSA track exists for. Breaking equals/hashCode does not throw,
 * does not warn, and does not fail a List-based test -- it silently loses entries in a
 * hash-based one. See docs/dsa/03-equals-and-hashcode.md.
 *
 * TODO 1: predict what hashSetSize() returns for EqualsOnly BEFORE running the test. EqualsOnly
 *         is provided and is deliberately broken: it overrides equals and not hashCode.
 *         DO NOT FIX IT -- it is the control.
 * TODO 2: implement Correct so that it is the same value type done properly. Then compare it
 *         with AsRecord below, which is free, and write down what you typed that the record
 *         did not make you type.
 * TODO 3: implement hashSetSize() and roundTripsInHashMap(). Three lines each.
 * TODO 4: implement ComparableColliding -- Colliding with a compareTo added and NOTHING else
 *         changed. Then run the KeysRace driver in docs/dsa/03 before and after, and
 *         compare the two.
 *
 *         The difference is roughly 65x on 20,000 keys. It is JEP 180: a HashMap bucket that
 *         overflows turns into a red-black tree, a tree needs an ordering, and without
 *         Comparable it cannot build one. The interface has nothing to do with hashing and it
 *         is the whole difference. Write down why this is a SECURITY feature and not a
 *         performance one. JEP 180 replaced the alternative string hashing added after the 2011
 *         hash-flooding disclosures (CVE-2011-4858 is Tomcat's).
 */
public final class Keys {

    private Keys() {
    }

    /** Provided, and deliberately wrong: equals without hashCode. The control. Do not fix. */
    public static final class EqualsOnly {

        private final int id;

        public EqualsOnly(int id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualsOnly key && key.id == id;
        }
    }

    /** TODO 2: the same value type, with both methods, consistent. */
    public static final class Correct {

        private final int id;

        public Correct(int id) {
            this.id = id;
        }

        // TODO 2: equals and hashCode go here.
    }

    /** Provided free, for comparison. A record generates both, consistently, from the components. */
    public record AsRecord(int id) {
    }

    /** Provided: every instance hashes to the same bucket, and it is not Comparable. */
    public static final class Colliding {

        private final int id;

        public Colliding(int id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            return 42;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Colliding key && key.id == id;
        }
    }

    /** TODO 4: exactly Colliding, plus Comparable. Change nothing else. */
    public static final class ComparableColliding {

        private final int id;

        public ComparableColliding(int id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            return 42;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ComparableColliding key && key.id == id;
        }

        // TODO 4: implement Comparable<ComparableColliding> and compareTo on id.
    }

    /** How many distinct elements a HashSet ends up holding after adding all of these. */
    public static int hashSetSize(List<?> items) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** Puts key into a fresh HashMap, then looks it up with a different but equal instance. */
    public static <K> boolean roundTripsInHashMap(K key, K equalButDistinctInstance) {
        throw new UnsupportedOperationException("TODO 3");
    }
}
