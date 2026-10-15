package org.mandfer.practices.algorithm;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The test BinarySearchTest should have been.
 *
 * BinarySearchTest has eight tests and they are all green. They probe three indices of the
 * twelve-element fixture -- 0, 6 and 11 -- plus one genuine miss. Every index they do not probe
 * is where the bug lives, and there are five of them.
 *
 * This class takes the SAME fixtures and asks for EVERY element. That is the only change.
 * Expect five failures on `sequence`, four on `oddSequence` and six on `negativeSequence`, plus
 * an ArrayIndexOutOfBoundsException on the empty array.
 *
 * Deleting the @Disabled below is how you start docs/dsa/05-binary-search.md. It will turn a
 * green build red, and that is the deliverable -- the fix comes after, as its own commit.
 */
@Disabled("Exercise: docs/dsa/05-binary-search.md. Delete this line to begin. It WILL fail -- that is the point.")
class BinarySearchSweepTest {

    static Stream<Arguments> everyElementOfEveryFixture() {
        List<Arguments> cases = new ArrayList<>();
        addSweep(cases, "sequence", BinarySearchTest.sequence);
        addSweep(cases, "oddSequence", BinarySearchTest.oddSequence);
        addSweep(cases, "negativeSequence", BinarySearchTest.negativeSequence);
        addSweep(cases, "boundaryMinSequence", BinarySearchTest.boundaryMinSequence);
        addSweep(cases, "boundarySequenceSingleElement", BinarySearchTest.boundarySequenceSingleElement);
        return cases.stream();
    }

    private static void addSweep(List<Arguments> cases, String name, int[] fixture) {
        for (int i = 0; i < fixture.length; i++) {
            cases.add(Arguments.of(name, fixture, i, fixture[i]));
        }
    }

    /** search() is documented by its own tests as returning a ONE-BASED index. */
    @ParameterizedTest(name = "{0}[{2}] = {3}")
    @MethodSource("everyElementOfEveryFixture")
    void finds_every_element_of_its_own_fixture(String name, int[] fixture, int index, int value) {
        assertEquals(index + 1, new BinarySearch().search(fixture, value),
                () -> name + ": value " + value + " is at index " + index
                        + ", so search() should return " + (index + 1));
    }

    @ParameterizedTest(name = "absent from {0}")
    @MethodSource("fixturesOnly")
    void reports_minus_one_for_a_value_that_is_absent(String name, int[] fixture) {
        assertEquals(-1, new BinarySearch().search(fixture, 1_000_000), name);
    }

    static Stream<Arguments> fixturesOnly() {
        return Stream.of(
                Arguments.of("sequence", BinarySearchTest.sequence),
                Arguments.of("oddSequence", BinarySearchTest.oddSequence),
                Arguments.of("negativeSequence", BinarySearchTest.negativeSequence));
    }

    /** An empty array is a legal input and currently throws ArrayIndexOutOfBoundsException. */
    @ParameterizedTest(name = "empty array, target {0}")
    @MethodSource("emptyTargets")
    void an_empty_array_reports_minus_one_rather_than_throwing(int target) {
        assertEquals(-1, new BinarySearch().search(new int[0], target));
    }

    static Stream<Integer> emptyTargets() {
        return Stream.of(0, 1, -1);
    }
}
