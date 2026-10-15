# 10. Structures you assemble from the JDK, not from scratch

**Stage 10 of [the DSA track](INDEX.md)** · new code: `collections/Assemblies.java`, four TODOs

## Where it lands

New code. [`research.md`](research.md) rates "implement an LRU cache" as **interview-only** and
then does not say what to do instead. This is the instead.

Four structures people reimplement, all of which are a JDK type plus at most four lines. The
exercise is **recognising the shape**, not writing the structure.

## 1. `LinkedHashMap` is an LRU cache

    new LinkedHashMap<>(16, 0.75f, true) {          // third argument: accessOrder
        protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
            return size() > capacity;
        }
    }

That is a complete LRU cache. The hook is `protected` precisely so you can override it — it exists
for this.

Verified: capacity 3, put a/b/c, `get("a")`, put d, and the contents are `[c, a, d]`. **`b` was
evicted and `a` survived because it was read.** That is the whole difference between
least-recently-*used* and least-recently-*inserted*, and the `accessOrder` flag is what buys it.

**The consequence worth writing down: `accessOrder=true` makes `get` a mutation.** Which means:

- it is not thread-safe even for pure reads, so two threads calling `get` concurrently is a data
  race and you need `Collections.synchronizedMap` or a different structure
- iterating while reading throws `ConcurrentModificationException`
- a `get` inside a stream over the same map is a bug

For a real cache you want Caffeine or Guava — size *and* time eviction, statistics, loading,
concurrency. **The point here is that you have never needed to write the eviction logic**, and the
eight-line hand-rolled LRU in a code review should be this instead.

## 2. `EnumMap` is an ordinal-indexed array

Not a hash map with enum keys — an array indexed by `ordinal()`, no hashing, no collisions, no
`hashCode` call. Insert `SHIP, PLAN, BUILD` and it iterates `[PLAN, BUILD, SHIP]`: **declaration
order, guaranteed by the javadoc.** The same keys in a `HashMap` iterate `[BUILD, PLAN, SHIP]`,
which is unspecified and may differ between runs.

One oddity the test pins: **`EnumMap` is not a `SequencedMap`**, despite having the most
thoroughly guaranteed order of any map in the JDK. `LinkedHashMap` and `TreeMap` are;
`EnumMap` is not, so there is no `firstEntry()` on it. (`EnumSet` is likewise not a
`SequencedSet` — the `instanceof` does not even compile.) Java 21's sequenced collections
retrofitted the interfaces onto the obvious candidates and `EnumMap` was not one of them; see
[modernization doc 10](../modernization/10-sequenced-collections-and-httpclient.md).

`EnumSet` is the same idea as a bitset. Both are strictly better than `HashMap`/`HashSet` for enum
keys and both are routinely not used.

## 3. `NavigableMap.floorEntry` is the range lookup

The loop everybody writes:

    if (score >= 100) return "high";
    else if (score >= 50) return "mid";
    else if (score >= 0) return "low";

The version that does not need editing when a band is added:

    thresholds.floorEntry(value)      // TreeMap<Integer, String>

Verified: `bandFor(49)` is `low`, `bandFor(50)` is `mid`, `bandFor(1_000_000)` is `high`, and
`bandFor(-1)` is `null` — **below the lowest threshold there is no band**, and deciding what that
means is TODO 3's real content. Returning `null` is one answer; a sentinel band or an exception are
others. Make the test say which.

Also know `ceilingEntry`, `higherKey`, `lowerKey`, `headMap`, `tailMap`, `subMap` and
`descendingKeySet`. This is the part of `TreeMap` that justifies choosing it over `HashMap`, and
"keys in sorted order" is the lesser reason.

## 4. `ArrayDeque` is the stack

`java.util.Stack` extends `Vector`: every method synchronised, for a lock nobody wants. The `Deque`
javadoc recommends `ArrayDeque` over it.

**The migration trap**, which the test pins: `ArrayDeque` iterates **head-first**, so a stack
pushed `a, b, c` iterates `c, b, a`. `java.util.Stack` iterates *bottom-first* — `a, b, c` — because
it is a `Vector` and iteration follows the array. **So swapping `Stack` for `ArrayDeque` silently
reverses every iteration over it**, while `push`/`pop`/`peek` behave identically. That is the worst
possible shape for a migration: the operations you check still work and the traversal you did not
check is backwards.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: `accessOrder=true` makes `get` a mutation — how would you make that safe under
concurrency without giving up LRU, and at what point do you just take the Caffeine dependency?
`EnumMap` is array-backed and `ordinal()`-indexed, so reordering an enum's constants changes its
internals — does that matter, and what else in Java breaks when you reorder an enum? If
`floorEntry` replaces an if-chain, what has the reader lost, and is the `TreeMap` construction
cost ever the deciding factor? Is "use the JDK type" ever the wrong advice here?
