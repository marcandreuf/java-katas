package org.mandfer.practices.patterns;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("Exercise: docs/modernization/21-unnamed-and-primitive-patterns.md. Delete this line to begin.")
class UnnamedTest {

    private static final List<Unnamed.Shape> SHAPES = List.of(
            new Unnamed.Circle(1.0, "c"),
            new Unnamed.Rect(2.0, 3.0, "r"),
            new Unnamed.Square(4.0, "s"));

    @Test
    void area_of_every_variant() {
        assertEquals(Math.PI, Unnamed.area(new Unnamed.Circle(1.0, "c")), 1e-9);
        assertEquals(6.0, Unnamed.area(new Unnamed.Rect(2.0, 3.0, "r")), 1e-9);
        assertEquals(16.0, Unnamed.area(new Unnamed.Square(4.0, "s")), 1e-9);
    }

    @Test
    void labels_ignores_every_dimension() {
        assertEquals(List.of("c", "r", "s"), Unnamed.labels(SHAPES));
    }

    @Test
    void parse_or_zero_parses() {
        assertEquals(42, Unnamed.parseOrZero("42"));
    }

    @Test
    void parse_or_zero_swallows_the_unused_exception() {
        assertEquals(0, Unnamed.parseOrZero("not a number"));
        assertEquals(0, Unnamed.parseOrZero(""));
    }

    @Test
    void sum_values_ignores_the_keys() {
        assertEquals(6, Unnamed.sumValues(Map.of("a", 1, "b", 2, "c", 3)));
    }

    @Test
    void sum_of_an_empty_map_is_zero() {
        assertEquals(0, Unnamed.sumValues(Map.of()));
    }

    @Test
    void nested_pattern_finds_the_circle() {
        Unnamed.Drawing drawing = new Unnamed.Drawing(new Unnamed.Circle(2.5, "c"), "marc");

        assertEquals(Optional.of(2.5), Unnamed.circleRadiusIn(drawing));
    }

    @Test
    void nested_pattern_declines_a_non_circle() {
        Unnamed.Drawing drawing = new Unnamed.Drawing(new Unnamed.Square(4.0, "s"), "marc");

        assertEquals(Optional.empty(), Unnamed.circleRadiusIn(drawing));
    }
}
