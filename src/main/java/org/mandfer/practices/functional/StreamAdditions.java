package org.mandfer.practices.functional;

import java.util.List;
import java.util.Optional;

/**
 * Everything added to Stream and Optional after Java 8, which is where most of the useful
 * parts now live. docs/modernization/09-streams-and-optional.md covers the Java 8 core; this is the
 * accretion from 9, 10, 12 and 16. See docs/modernization/17-stream-and-optional-additions.md.
 *
 * TODO 1: leadingBelow with takeWhile, allBelow with filter. They return different things for
 *         the same input and the test proves it. Being able to say which one you meant is the
 *         point -- "filter" is what everyone writes and it is often not what they meant.
 * TODO 2: afterLeadingBelow with dropWhile. Note it is NOT the complement of takeWhile under
 *         filter semantics.
 * TODO 3: powersOfTwoBelow with the three-argument Stream.iterate (Java 9). Write it with the
 *         two-argument version plus limit() first and say why the three-argument one exists.
 * TODO 4: firstNonEmpty with Optional.or (Java 9). No isPresent(), no get().
 * TODO 5: presentValues with Optional::stream (Java 9) and flatMap. Compare with
 *         filter(Optional::isPresent).map(Optional::get) and say which you would review better.
 * TODO 6: summarize with Collectors.teeing (Java 12) -- min and max in ONE pass over the
 *         stream. Then answer whether one pass was worth it here.
 */
public final class StreamAdditions {

    private StreamAdditions() {
    }

    /** Elements from the start while they are below limit. Stops at the first that is not. */
    public static List<Integer> leadingBelow(List<Integer> values, int limit) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** Every element below limit, wherever it appears. */
    public static List<Integer> allBelow(List<Integer> values, int limit) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** Everything from the first element that is not below limit, onwards. */
    public static List<Integer> afterLeadingBelow(List<Integer> values, int limit) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** 1, 2, 4, 8 ... strictly below limit. */
    public static List<Integer> powersOfTwoBelow(int limit) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** The first of the two that has a value. */
    public static Optional<String> firstNonEmpty(Optional<String> first, Optional<String> second) {
        throw new UnsupportedOperationException("TODO 4");
    }

    /** Just the values that are present, in order. */
    public static List<String> presentValues(List<Optional<String>> options) {
        throw new UnsupportedOperationException("TODO 5");
    }

    /** "min=1 max=10", computed in a single pass. */
    public static String summarize(List<Integer> values) {
        throw new UnsupportedOperationException("TODO 6");
    }
}
