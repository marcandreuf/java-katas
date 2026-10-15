package org.mandfer.practices.algorithm;

import java.util.List;

/**
 * Integer overflow as an algorithm bug: the arithmetic is correct and the type is too small.
 *
 * Two files in this repo have it today, neither has a test, and neither throws:
 *
 *   NumberReverse.reverseNumber(2147483647)  -> -1126087180   (should be 7463847412)
 *   DuplicateNumber.getSum(...)              -> wraps silently on large inputs
 *
 * See docs/dsa/09-integer-overflow.md.
 *
 * TODO 1: reverseExact -- the same digit-reversal as NumberReverse, but it must REFUSE rather
 *         than wrap. Use Math.multiplyExact and Math.addExact and let ArithmeticException out.
 *         Note what you did NOT have to do: no bounds checking, no magic constants.
 * TODO 2: reverseWidened -- the same reversal returning a long. This is the other fix and it is
 *         usually the right one. Then answer the question that decides between them: is
 *         "reversing 2147483647" a value that should FAIL, or a value that should WIDEN? The
 *         answer depends on what a reversed number means, which is a domain question and not a
 *         numeric one.
 * TODO 3: sumExact -- DuplicateNumber.getSum with Math.addExact. Keep the int return type so
 *         you can see that the overflow was always a real possibility and never a reported one.
 * TODO 4: findDuplicateChecked -- DuplicateNumber's Gauss trick, with its PRECONDITION
 *         enforced. The existing version assumes the list is 1..n-1 with exactly one duplicate
 *         and checks nothing, so findDuplicateNumber([5,5,5]) returns 12. Decide what to throw
 *         and write down why a wrong answer is worse than an exception here.
 */
public final class Overflow {

    private Overflow() {
    }

    /** Digits reversed. Throws ArithmeticException rather than wrapping. */
    public static int reverseExact(int number) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** Digits reversed into a long, which cannot overflow for any int input. */
    public static long reverseWidened(int number) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** The sum, throwing ArithmeticException rather than wrapping. */
    public static int sumExact(List<Integer> numbers) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** The duplicate, with the 1..n-1-plus-one-duplicate precondition actually enforced. */
    public static int findDuplicateChecked(List<Integer> numbers) {
        throw new UnsupportedOperationException("TODO 4");
    }
}
