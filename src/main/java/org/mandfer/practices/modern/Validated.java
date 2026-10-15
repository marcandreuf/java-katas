package org.mandfer.practices.modern;

/**
 * Validating a constructor argument BEFORE the superclass constructor runs, which was illegal
 * until Java 25.
 *
 * Flexible Constructor Bodies (JEP 513) lets statements precede super(...) or this(...), as
 * long as they do not read the instance under construction. Before it, the only ways to reject
 * a bad argument before the super constructor ran were a static helper wrapped around the
 * argument -- super(requireNonNegative(size)) -- or nothing.
 *
 * See docs/modernization/28-flexible-constructor-bodies.md. NOTE: needs --release 25.
 *
 * TODO 1: write NonNegative's constructor so it throws BEFORE calling super(size). The test
 *         proves the ordering by counting how many times the base constructor ran, so a
 *         validate-after-super implementation fails it.
 * TODO 2: write the old workaround -- Legacy, below -- with a static method inside super(...).
 *         Both reject the same input. Write down what the static-helper version cost you in
 *         readability, and whether it was ever actually a problem.
 * TODO 3: try to read a field of `this` before super(). The compiler will stop you. Write
 *         down the error, because that restriction is what makes this feature safe rather
 *         than a hole in object initialisation.
 */
public final class Validated {

    private Validated() {
    }

    /** The base. Counts its own invocations so a test can prove whether it ran. */
    public static class Measured {

        private static int constructions;

        public final int size;

        protected Measured(int size) {
            constructions++;
            this.size = size;
        }

        public static int constructions() {
            return constructions;
        }

        public static void resetConstructions() {
            constructions = 0;
        }
    }

    /** Rejects a negative size before Measured's constructor is reached. */
    public static class NonNegative extends Measured {

        public NonNegative(int size) {
            // TODO 1: validate here, then call super(size).
            super(size);
        }
    }

    /** The pre-25 workaround: validation hidden inside the super(...) argument. */
    public static class Legacy extends Measured {

        public Legacy(int size) {
            super(requireNonNegative(size));
        }

        private static int requireNonNegative(int size) {
            throw new UnsupportedOperationException("TODO 2");
        }
    }
}
