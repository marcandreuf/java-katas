package org.mandfer.practices.collections;

import java.util.List;
import java.util.Map;

/**
 * Counting words five ways, because the five ways are the Java 8 collections API and choosing
 * between them is the skill.
 *
 * The rewrite target in the old code is FindDups.java, which does this job with a TreeSet, an
 * anonymous Comparator and a for loop over args. See docs/14-map-defaults-and-collectors.md.
 *
 * TODO 1: tallyWithMerge  -- map.merge(word, 1, Integer::sum). The shortest correct counter in
 *         the language. Know what the second argument means when the key IS present.
 * TODO 2: tallyWithComputeIfAbsent -- do it with computeIfAbsent instead, and notice you need
 *         a mutable value to make it work. That tells you what computeIfAbsent is actually
 *         for, and it is not counting.
 * TODO 3: tallyWithGroupingBy -- groupingBy + counting(). Note the return type is Long, not
 *         Integer, and that you cannot choose otherwise. Write down what that costs at an API
 *         boundary.
 * TODO 4: lengthsByWordStrict and lengthsByWordLenient. The strict one uses toMap with two
 *         arguments and THROWS on a duplicate key. The test asserts the throw. Then fix it
 *         with the three-argument form.
 * TODO 5: topWords -- the n most frequent, most frequent first, ties broken alphabetically
 *         ascending. Same reversed() placement trap as docs/12, now on Map.Entry.
 */
public final class WordTally {

    private WordTally() {
    }

    /** word -> occurrences, built with Map.merge. */
    public static Map<String, Integer> tallyWithMerge(List<String> words) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** word -> occurrences, built with Map.computeIfAbsent. */
    public static Map<String, Integer> tallyWithComputeIfAbsent(List<String> words) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** word -> occurrences, built with Collectors.groupingBy. Long, not Integer. */
    public static Map<String, Long> tallyWithGroupingBy(List<String> words) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** word -> its length, via two-argument Collectors.toMap. Blows up on duplicates. */
    public static Map<String, Integer> lengthsByWordStrict(List<String> words) {
        throw new UnsupportedOperationException("TODO 4");
    }

    /** word -> its length, tolerating duplicates. */
    public static Map<String, Integer> lengthsByWordLenient(List<String> words) {
        throw new UnsupportedOperationException("TODO 4");
    }

    /** The n most frequent words, most frequent first, ties alphabetical ascending. */
    public static List<String> topWords(List<String> words, int n) {
        throw new UnsupportedOperationException("TODO 5");
    }
}
