package org.mandfer.practices.functional;

import java.util.Comparator;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * Four one-argument string transforms, and the question of how you join them together.
 *
 * Nothing in the old code composes functions, because nothing in the old code has a lambda in
 * it: one file in this repo contains a {@code ->} and this track added it. So this exercise is
 * new code. The rewrite targets for the same concept are named in
 * {@code docs/modernization/12-lambdas-and-method-references.md}.
 *
 * TODO 1: implement cleanAsStep() by chaining the four transforms below using Step.
 *         You will find you cannot, because Step has no andThen. Add one as a *default*
 *         method on Step. That is exactly what Function does, and it is why default methods
 *         and lambdas shipped in the same release.
 * TODO 2: implement clean() with UnaryOperator<String> instead, and delete nothing. Keeping
 *         both side by side is the point: the diff between them is the whole lesson.
 * TODO 3: replace every lambda in clean() with a method reference where one exists. Three of
 *         the four have one. The fourth has one that COMPILES AND IS WRONG -- find it, and
 *         write down what it changes.
 * TODO 4: implement shortestFirst() and longestFirst(). They differ by where reversed() goes,
 *         and the test will tell you if you put it in the wrong place.
 */
public final class TextPipeline {

    /**
     * A hand-rolled functional interface, here so you can feel what java.util.function gives
     * you for free. After TODO 2, write down the one thing this named type buys that
     * UnaryOperator<String> does not.
     */
    @FunctionalInterface
    public interface Step {
        String apply(String text);

        // TODO 1: add a default Step andThen(Step next) here.
    }

    private TextPipeline() {
    }

    static String trimEnds(String text) {
        return text.trim();
    }

    static String collapseSpaces(String text) {
        return text.replaceAll("\\s+", " ");
    }

    static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** Trim, collapse runs of whitespace, lowercase, then capitalize the first letter. */
    public static Step cleanAsStep() {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** The same four transforms, composed with UnaryOperator instead. */
    public static UnaryOperator<String> clean() {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** Shortest string first; ties broken alphabetically, ascending. */
    public static Comparator<String> shortestFirst() {
        throw new UnsupportedOperationException("TODO 4");
    }

    /** Longest string first; ties STILL broken alphabetically ascending, not descending. */
    public static Comparator<String> longestFirst() {
        throw new UnsupportedOperationException("TODO 4");
    }
}
