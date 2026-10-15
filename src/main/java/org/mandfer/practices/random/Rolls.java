package org.mandfer.practices.random;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Randomness you can test, which is the thing java.util.Random made hard and JEP 356
 * (Java 17) made easy.
 *
 * Five sites in this repo call new Random(), and one of them is a bug:
 *
 *     RandomFileLines.java:32   new Random(System.currentTimeMillis())
 *
 * Seeding from the wall clock does not add entropy, it removes it. TODO 4 makes that
 * concrete. The others are guardedLock/Producer:53, guardedLock/Consumer:48 and
 * Safelock.java:107. See docs/19-randomness-and-numerics.md.
 *
 * TODO 1: seeded -- RandomGeneratorFactory.of(algorithm).create(seed). Note that
 *         RandomGenerator.of(name) exists too and gives you an UNSEEDED one, which is the
 *         overload you want in production and cannot test.
 * TODO 2: rolls -- count dice rolls in 1..6 from the given generator, using its ints()
 *         stream rather than a loop over nextInt().
 * TODO 3: clampedIndex -- Math.clamp (Java 21). Write it with Math.min/Math.max first and
 *         then say what clamp actually bought you. It is not brevity.
 * TODO 4: The test same_clock_seed_gives_the_same_sequence is already written and it PASSES
 *         against java.util.Random. Nothing for you to implement -- read it, run it, and
 *         write down what it means for RandomFileLines.
 */
public final class Rolls {

    private Rolls() {
    }

    /** A generator of the named algorithm, started from the given seed. Reproducible. */
    public static RandomGenerator seeded(String algorithm, long seed) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** count rolls of a six-sided die, each in 1..6 inclusive. */
    public static List<Integer> rolls(RandomGenerator generator, int count) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** value, forced into 0..size-1. */
    public static int clampedIndex(int value, int size) {
        throw new UnsupportedOperationException("TODO 3");
    }
}
