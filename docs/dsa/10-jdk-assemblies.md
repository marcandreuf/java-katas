# 10. Structures you assemble from the JDK, not from scratch

**Stage 10 of [the DSA track](INDEX.md)** · new code: `collections/Assemblies.java`, four TODOs ·
test: `collections/AssembliesTest.java`, 7 tests, `@Disabled`

## What this stage is actually about

Four words, defined once:

- an **assembly** is a JDK type plus at most a few lines of yours, standing in for a structure
  people write from scratch. An LRU cache is `LinkedHashMap` plus one overridden method.
- **iteration order** is the order a `for` loop over the structure gives you. It's part of the
  behaviour, whether or not anything documents it.
- **access order** is a `LinkedHashMap` mode where reading an entry moves it to the end.
- a **migration** here means replacing a hand-rolled or legacy structure with the JDK one, under
  code that already calls it.

That gives one rule, and everything below follows from it: **when you swap in the JDK type, the
operations you call will match, and the behaviour you didn't call is where it differs.** Iteration
order, what a read does, whether `null` is allowed: those are what a migration breaks, because
nobody thought to check them.

[`research.md`](research.md) rates "implement an LRU cache" as **interview-only** and then doesn't
say what to do instead. This is the instead.

## The worked example: a top-k, which isn't one of the four

The three highest scores from a stream, without sorting all of them: a `PriorityQueue` (a min-heap)
that you trim back to three after each add.

    var top = new PriorityQueue<Integer>();
    for (int score : scores) {
        top.offer(score);
        if (top.size() > 3) top.poll();      // drop the smallest
    }

Over `40, 95, 12, 77, 63, 88, 5` it ends holding 77, 88 and 95 (verified). Two lines of yours. Now
the part the rule is about, the behaviour you didn't call:

    new PriorityQueue<>(List.of(5, 1, 4, 2, 3))     // iterates [1, 2, 4, 5, 3]
    pq.poll() until empty                           // 1, 2, 3, 4, 5
    pq.offer(null)                                  // NullPointerException

(all verified). **A `PriorityQueue` iterates in heap order, not sorted order.** Only `poll()` gives
you sorted. The javadoc says so, and a `for` loop over a priority queue is still a classic bug,
because on small inputs it often *looks* sorted: the top-3 above happened to iterate as
`[77, 88, 95]` (verified). The same shape repeats in all four parts below.

## Part 1: `LinkedHashMap` is an LRU cache (TODO 1)

The constructor `LinkedHashMap(initialCapacity, loadFactor, accessOrder)` and the `protected`
hook `removeEldestEntry` are the whole cache. The hook is there to be overridden. The test's
scenario, capacity 3: put a, b, c, `get("a")`, put d. The keys are `[c, a, d]` (verified). **`b`
was evicted and `a` survived because it was read.** That's the whole difference between least
recently *used* and least recently *inserted*, and the `accessOrder` flag is what buys it.

**The behaviour you didn't call: `get` is now a write.** The javadoc says it outright: "In
access-ordered linked hash maps, merely querying the map with `get` is a structural modification."
Verified on JDK 26:

    for (String k : cache.keySet()) cache.get(k);          // ConcurrentModificationException
    cache.keySet().stream().map(cache::get).toList();      // ConcurrentModificationException
    for (String k : cache.keySet()) cache.containsKey(k);  // fine: containsKey isn't an access
    cache.getOrDefault("x", 0);                            // moves "x" to the end, like get
    cache.put("y", 9);   // y already present              // moves "y" to the end

So two threads calling only `get` are two threads writing, and that's a data race. (This one is
from the javadoc and the structure, not a run: a race that doesn't show up in a test proves
nothing, as [modernization doc 08](../modernization/08-memory-model.md) shows.)

For a real cache you want Caffeine or Guava: size *and* time eviction, statistics, loading,
concurrency. **The point here is that you've never needed to write the eviction logic.**

## Part 2: `EnumMap` is an ordinal-indexed array (TODO 2)

Not a hash map with enum keys: inside, it's an `Object[]` indexed by `ordinal()` (`EnumMap.java`,
field `vals`). No hashing, no collisions. Insert `SHIP, PLAN, BUILD` and it iterates
`[PLAN, BUILD, SHIP]`: **declaration order, guaranteed by the javadoc** (verified).

**The old version of this doc said a `HashMap` iterates the same keys as `[BUILD, PLAN, SHIP]`
"and may differ between runs".** It's worse than that, because it usually *doesn't* differ between
runs. Enum `hashCode()` is the identity hash, and the identity hash HotSpot hands out depends on
what the program did before. Verified, same three keys, same JDK:

| Program | `HashMap` order, six runs each |
| --- | --- |
| a 3-line class, run with `java H.java` | `[SHIP, PLAN, BUILD]` every time |
| the same class, compiled then run | `[PLAN, BUILD, SHIP]` every time |

Stable every time you run it, and different the moment the program around it changes. **An order
that's stable on your machine is the most convincing kind of unspecified order.**

**And `EnumMap` is not a `SequencedMap`**, despite having the most firmly guaranteed order of any
map in the JDK, so it has no `firstEntry()` (verified; the test pins it). `EnumSet` isn't a
`SequencedSet` either, and there it's stronger: `enumSet instanceof SequencedSet` **doesn't
compile** ("incompatible types", verified). `EnumSet` is a `sealed` class whose two permitted
subclasses are both final and neither one implements the interface, so the compiler can prove the
answer is `false`. `EnumMap` isn't sealed, so its `instanceof` compiles and returns `false`. Java
21's sequenced collections were added to the obvious candidates, and these two weren't among them.
See [modernization doc 10](../modernization/10-sequenced-collections-and-httpclient.md).

`EnumSet` is the same idea as a bit set, one `long` for up to 64 constants. Both types are strictly
better than `HashMap`/`HashSet` for enum keys, and both are routinely not used.

## Part 3: `NavigableMap.floorEntry` is the range lookup (TODO 3)

The loop everybody writes:

    if (score >= 100) return "high";
    else if (score >= 50) return "mid";
    else if (score >= 0) return "low";

The version that doesn't need editing when a band is added: `thresholds.floorEntry(value)` on a
`TreeMap<Integer, String>`. Verified: 49 → `low`, 50 → `mid`, 1,000,000 → `high`, and -1 → **no
entry**: below the lowest threshold there is no band.

**A correction to the old version of this doc**, which said deciding what -1 means is TODO 3's
real content and to "make the test say which". The test has already decided:
`band_for_has_nothing_below_the_lowest_threshold` asserts `null`, and the javadoc on `bandFor`
says "or null if there is none". If you'd rather throw or return a sentinel band, that's a
legitimate choice, but it means changing the javadoc and the test in the same commit as the code,
and saying why.

Also know `ceilingEntry`, `higherKey`, `lowerKey`, `headMap`, `tailMap`, `subMap` and
`descendingKeySet`. This is the part of `TreeMap` that justifies choosing it over `HashMap`.
"Keys in sorted order" is the lesser reason.

## Part 4: `ArrayDeque` is the stack (TODO 4)

`java.util.Stack` extends `Vector`, so every operation takes a lock that nobody wants (`pop`,
`peek` and `search` are `synchronized`, and `push` calls the synchronized `addElement`). **A
correction:** the old version of this doc said the `Deque` javadoc recommends `ArrayDeque` over
it. It's **`Stack`'s own javadoc** that says Deque "should be used in preference to this class",
with `new ArrayDeque<Integer>()` as its example.

**The behaviour you didn't call.** Push `a, b, c` onto each (verified):

| | `peek()` | iterates | `push(null)` |
| --- | --- | --- | --- |
| `java.util.Stack` | `c` | `[a, b, c]`, bottom first | allowed |
| `ArrayDeque` | `c` | `[c, b, a]`, top first | `NullPointerException` |

**Swapping `Stack` for `ArrayDeque` silently reverses every iteration over it**, and turns every
`push(null)` into an exception. Meanwhile `push`, `pop` and `peek` behave identically. That's the
worst shape a migration can have: the operations you check still work, and the traversal you
didn't check is backwards. **A correction:** the old version said the test pins this. It pins only
the `ArrayDeque` half, `[c, b, a]`. The `Stack` half is in a comment.

## The commits

New code with no callers, so there's nothing to characterize first.
[`../testing-first.md`](../testing-first.md)'s rule is about changing code that already exists.
Here the tests come written, and you write the code under them.

1. **Delete `AssembliesTest`'s `@Disabled`**: 7 red, all `UnsupportedOperationException`
   (verified). Commit the red build or not, as you prefer, but don't fix anything in this commit.
2. **One TODO per commit**, in order. Each turns its own tests green: TODO 1 two tests, TODO 2
   two, TODO 3 two, TODO 4 one.
3. **Then the test the old doc thought was there**: one that pushes `a, b, c` onto a
   `java.util.Stack` and asserts it iterates `[a, b, c]`, next to the `ArrayDeque` one. It's a
   characterization of the JDK, not of your code. Its job is to make the trap visible in the test
   file, where the next person migrating will see it.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: `accessOrder=true` makes `get` a mutation. How would you make that safe under concurrency
without giving up LRU, and at what point do you just take the Caffeine dependency? `EnumMap` is
indexed by `ordinal()`, so reordering an enum's constants changes its iteration order. What else
in Java breaks when you reorder an enum? If `floorEntry` replaces an if-chain, what has the reader
lost? A stable `HashMap` order passed six runs out of six here. What kind of test would ever have
caught it? Is "use the JDK type" ever the wrong advice?
