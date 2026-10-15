package org.mandfer.practices.diagnostics;

import java.util.Optional;

/**
 * A three-link chain with a null in the middle, so you can read what the JVM now tells you
 * about it for free.
 *
 * Helpful NullPointerExceptions (JEP 358) arrived in Java 14 and became the default in 15.
 * Nothing in this repo had to change to get them, which is why this exercise exists: it is
 * the cheapest improvement in the whole 8-to-26 range and most people have never deliberately
 * looked at one. See docs/20-free-diagnostics.md.
 *
 * TODO 1: implement cityOf() as the obvious one-line walk, and let the NPE escape. Do NOT add
 *         null checks. Then run NullPathsTest and read the two messages it asserts.
 * TODO 2: PREDICT both messages before you run the test. Write your prediction down first.
 *         The thing worth learning is which half of the message names the thing that WAS null
 *         and which names the thing you tried to call on it -- people read them backwards.
 * TODO 3: implement cityOrEmpty() with Optional.map so the same walk cannot throw. Then count
 *         the lines and decide whether you would take it. At three links the answer is not
 *         obviously yes, and docs/09 has the rule about isPresent()/get() that applies here.
 */
public final class NullPaths {

    public record Address(String city) {
    }

    public record Company(Address address) {
    }

    public record Person(String name, Company employer) {
    }

    private NullPaths() {
    }

    /** The employer's city, uppercased. Throws if any link is null. That is the point. */
    public static String cityOf(Person person) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** The same walk, empty instead of thrown. */
    public static Optional<String> cityOrEmpty(Person person) {
        throw new UnsupportedOperationException("TODO 3");
    }
}
