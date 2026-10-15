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
 * {@code docs/modernization/12-lambdas-and-method-references.md}, and you should do those FIRST --
 * this file is the second half of the stage, not the first.
 *
 * WHAT YOU ARE BUILDING. The four static methods below each take a String and return a String.
 * "Composing" them means producing ONE object that does all four in order, so that
 *
 *     clean().apply("  The   QUICK\tbrown\nfox  ")   ==   "The quick brown fox"
 *
 * The order is: trim the ends, collapse runs of whitespace to one space, lowercase, then
 * capitalize the first letter. That order matters and the test checks it.
 *
 * WHY YOU CAN ASSIGN A METHOD TO AN INTERFACE. UnaryOperator<String> is a functional interface:
 * it declares exactly one abstract method, {@code String apply(String)}. Any method with a
 * matching shape can stand in for it, so all three of these are the same thing:
 *
 *     UnaryOperator<String> t = text -> trimEnds(text);     // lambda
 *     UnaryOperator<String> t = TextPipeline::trimEnds;     // method reference
 *     UnaryOperator<String> t = text -> text.trim();        // lambda calling String.trim
 *
 * TODO 1: implement clean(). UnaryOperator extends Function, so it has andThen(), and
 *         a.andThen(b) returns a new function that runs a then b. Build the four-step chain
 *         with it. Then try compose() instead of andThen() and notice you have to write the
 *         same four names in the opposite order, which is why andThen() is the one people use.
 *
 * TODO 2: implement cleanAsStep() -- the SAME chain, using the hand-rolled Step interface
 *         below instead of UnaryOperator.
 *
 *         You will get stuck, and getting stuck is the exercise. Step has no andThen(), so
 *         there is nothing to chain with; the only thing you can do with a Step is call
 *         apply() on it. So add one yourself:
 *
 *             default Step andThen(Step next) { ... }
 *
 *         It is one line. The lesson is why it is ALLOWED to be one line: before Java 8 you
 *         could not add a method to a published interface without breaking every implementor,
 *         so java.util.function.Function could not have carried andThen() either. Default
 *         methods are what make combinators possible, which is why default methods and lambdas
 *         shipped in the same release. Java 8's two headline features are one feature.
 *
 * TODO 3: method references. Three of the four transforms can be written as a reference to a
 *         String method directly, skipping the helper below entirely -- String::trim is the
 *         obvious one. Find them.
 *
 *         Then look hard at lower(). There IS a reference for it, String::toLowerCase, and it
 *         COMPILES AND IS WRONG. Work out why before you read the doc. Hint: compare the
 *         signature of the no-argument String.toLowerCase() with what lower() actually calls.
 *
 * TODO 4: implement shortestFirst() and longestFirst().
 *
 *         Comparator has static builders: Comparator.comparingInt(String::length) gives you a
 *         comparator ordering by length, .thenComparing(...) adds a tie-breaker, and
 *         .reversed() flips a comparator. The two methods below need the same three pieces in
 *         a different arrangement, and only one arrangement is right for each. The test says
 *         which; the doc says why.
 */
public final class TextPipeline {

    /**
     * A hand-rolled functional interface: one abstract method, so a lambda or a method
     * reference can stand in for it, exactly like UnaryOperator<String>. It is here so you can
     * feel what java.util.function gives you for free. After TODO 2, write down the one thing
     * this named type buys that UnaryOperator<String> does not. (It is not nothing, and the
     * answer is about reading the code rather than running it.)
     */
    @FunctionalInterface
    public interface Step {
        String apply(String text);

        // TODO 2: add a default Step andThen(Step next) here.
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
        throw new UnsupportedOperationException("TODO 2");
    }

    /** The same four transforms, composed with UnaryOperator instead. */
    public static UnaryOperator<String> clean() {
        throw new UnsupportedOperationException("TODO 1");
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
