package org.mandfer.practices.patterns;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The underscore, in the places this exercise needs. Java 22 allows it in six declaration forms
 * plus unnamed patterns.
 *
 * This is new code, and the reason is itself the finding: `_` is for things you are FORCED to
 * declare and do not use, and the old code here has almost none of those. What it has instead
 * is variables that are declared, unused, and still named -- which the compiler never
 * complained about. See docs/modernization/21-unnamed-and-primitive-patterns.md.
 *
 * Builds on docs/modernization/02-sealed-types.md and docs/modernization/03-pattern-matching.md. Do those first.
 *
 * TODO 1: area() -- an exhaustive switch with record patterns. Every variant carries a label
 *         you do not need. Bind it to `_`. Then try naming it `label` and leaving it unused,
 *         and note that the compiler is equally happy. Write down what `_` bought.
 * TODO 2: labels() -- the opposite: you need the label and none of the dimensions.
 * TODO 3: parseOrZero() -- catch (NumberFormatException _). The one place `_` is unarguably
 *         better, because `catch (NumberFormatException e)` with an unused `e` is the most
 *         common unused variable in Java.
 * TODO 4: sumValues() -- Map.forEach with an unnamed key: (_, value) -> ...
 * TODO 5: circleRadiusIn() -- a NESTED record pattern with two unnamed components. This is
 *         where the feature stops being cosmetic: without `_` you must invent two names that
 *         a reader then has to check are unused.
 */
public final class Unnamed {

    public sealed interface Shape permits Circle, Rect, Square {
    }

    public record Circle(double radius, String label) implements Shape {
    }

    public record Rect(double width, double height, String label) implements Shape {
    }

    public record Square(double side, String label) implements Shape {
    }

    /** A shape, and who drew it. */
    public record Drawing(Shape shape, String author) {
    }

    private Unnamed() {
    }

    /** The area, however the shape spells its dimensions. */
    public static double area(Shape shape) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** Every shape's label, in order. */
    public static List<String> labels(List<Shape> shapes) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** The number, or zero if it is not one. */
    public static int parseOrZero(String text) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** The sum of the values, ignoring the keys entirely. */
    public static int sumValues(Map<String, Integer> values) {
        throw new UnsupportedOperationException("TODO 4");
    }

    /** The radius, if this drawing is of a circle. */
    public static Optional<Double> circleRadiusIn(Drawing drawing) {
        throw new UnsupportedOperationException("TODO 5");
    }
}
