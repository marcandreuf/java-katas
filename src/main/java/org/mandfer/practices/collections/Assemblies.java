package org.mandfer.practices.collections;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * The four structures people implement from scratch and should not: an LRU cache, an
 * enum-keyed map, a range lookup, and a stack.
 *
 * docs/dsa/research.md rates "implement an LRU cache" as interview-only and then does not say
 * what to do instead. This is the instead. All four are JDK types you already have, and the
 * exercise is recognising the shape rather than writing the structure.
 *
 * See docs/dsa/10-jdk-assemblies.md.
 *
 * TODO 1: lruCache -- a Map that evicts the least RECENTLY USED entry past a capacity.
 *         LinkedHashMap has a three-argument constructor whose third parameter is accessOrder,
 *         and a protected removeEldestEntry hook designed to be overridden. That is the whole
 *         cache. Note that accessOrder makes get() a MUTATION, which has consequences the doc
 *         covers.
 * TODO 2: phaseLabels -- a Map keyed by an enum. Use the right type, not HashMap. Its iteration
 *         order is the enum's DECLARATION order and that is guaranteed, which is the property
 *         you are buying. The test inserts in the wrong order on purpose.
 *
 *         Note the return type is Map and not SequencedMap, because EnumMap does NOT implement
 *         SequencedMap even though its order is guaranteed -- so there is no firstEntry() on
 *         it. LinkedHashMap and TreeMap are SequencedMaps; EnumMap is the odd one out.
 * TODO 3: bandFor -- given sorted thresholds and a value, return the label of the band the
 *         value falls into. This is the loop everybody writes and NavigableMap.floorEntry
 *         replaces. Decide what happens below the lowest threshold and make the test say so.
 * TODO 4: undoStack -- a last-in-first-out stack. Use ArrayDeque. java.util.Stack extends
 *         Vector and is synchronised, and the Deque javadoc recommends against it. Note that
 *         ArrayDeque's iteration order is head-first, so pushing a, b, c iterates c, b, a --
 *         which is the opposite of what Stack does and is the trap in migrating.
 */
public final class Assemblies {

    /** Phases of a job, declared in the order they happen. */
    public enum Phase {
        PLAN, BUILD, SHIP
    }

    private Assemblies() {
    }

    /** A map holding at most capacity entries, evicting the least recently accessed. */
    public static <K, V> Map<K, V> lruCache(int capacity) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** A map from Phase to its label, iterating in declaration order. */
    public static Map<Phase, String> phaseLabels(Map<Phase, String> labels) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** The label of the highest threshold that is <= value, or null if there is none. */
    public static String bandFor(NavigableMap<Integer, String> thresholds, int value) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** A LIFO stack seeded with these items, pushed in list order. */
    public static Deque<String> undoStack(List<String> items) {
        throw new UnsupportedOperationException("TODO 4");
    }
}
