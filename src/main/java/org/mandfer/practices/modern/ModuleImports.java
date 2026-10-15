package org.mandfer.practices.modern;

import module java.base;

/**
 * One import standing in for every package java.base exports.
 *
 * Module Import Declarations (JEP 511) went final in Java 25. `import module java.base;`
 * imports every public top-level type from every package that module exports, transitively.
 * Note what this file does NOT contain: a single-type import. List, ArrayList, Map,
 * LinkedHashMap, Comparator and Collectors all arrive through that one line.
 *
 * See docs/modernization/27-module-imports-and-compact-sources.md. NOTE: needs --release 25.
 *
 * TODO 1: implement all three methods using only types reached through the module import.
 *         Add no import lines. Count the ones you did not have to write.
 * TODO 2: add `import module java.desktop;` above and recompile. `List` becomes AMBIGUOUS,
 *         because java.desktop exports java.awt.List. Read the error. Then fix it WITHOUT
 *         deleting either module import, and write down what the fix implies about using
 *         this feature in a file that needs two large modules.
 * TODO 3: answer the question the feature raises. An IDE can tell you which module a type
 *         came from; a diff in a browser cannot. Is that the same objection you wrote down
 *         about `var` in docs/modernization/05-var.md, or a different one?
 */
public final class ModuleImports {

    private ModuleImports() {
    }

    /** Each word mapped to its length, keeping insertion order. */
    public static Map<String, Integer> wordLengths(List<String> words) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** The words, comma-separated. */
    public static String joined(List<String> words) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** The words that are not blank, longest first, ties alphabetical ascending. */
    public static List<String> longestFirst(List<String> words) {
        throw new UnsupportedOperationException("TODO 1");
    }
}
