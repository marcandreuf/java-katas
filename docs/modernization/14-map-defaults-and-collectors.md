# 14. `Map` default methods, comparators and collectors

**Java 8** · stage 3 of [the release-ordered track](INDEX.md) · compiles at `--release 21`, no
build change

## Where it lands

**`collections/FindDups.java`** is the whole document in fourteen lines. It counts distinct words
using a `TreeSet`, an anonymous `Comparator` and a `for` loop, and it is Oracle tutorial code from
before any of this existed. [Doc 09](09-streams-and-optional.md) asks you to turn it into a
`distinct().count()`. **Do that first and then come back**, because the interesting version of the
problem is the one the file does *not* solve: it tells you a word was duplicated but never how
many times.

**`algorithm/FairWorkLoad.java`** is the second target, and the honest answer there may be "leave
it alone" — see below.

New code: **`collections/WordTally.java`**, five TODOs, counting the same words five ways.

## The exercise

### The counter, three ways

TODOs 1–3 all produce `{fig=3, pear=2, apple=1, kiwi=1}`. The point is the differences.

    map.merge(word, 1, Integer::sum);                           // TODO 1
    map.computeIfAbsent(word, k -> new int[1])[0]++;             // TODO 2, roughly
    words.stream().collect(groupingBy(identity(), counting()));  // TODO 3

1. **`merge` is the right answer for counting** and the shortest correct one in the language. The
   thing to understand is its contract: the second argument is used *only* when the key is
   absent, and the function is called *only* when it is present. Nearly everyone reads it the
   other way round the first time.
2. **`computeIfAbsent` is the wrong tool here and that is why it is TODO 2.** Try it and you will
   find you need a mutable value — an `int[1]`, an `AtomicInteger`, something — because the
   function only runs when the key is missing, so it cannot increment. What it is genuinely for is
   `Map<K, List<V>>`: `computeIfAbsent(k, x -> new ArrayList<>()).add(v)` is the multimap idiom
   and the reason you do not need Guava for it. **Reaching for the right one of these two is the
   deliverable of this document.**
3. **`groupingBy(identity(), counting())` hands you `Long` and you cannot ask it for `Integer`.**
   `counting()` is specified to return `Collector<T, ?, Long>`. So the stream version forces a
   type change on every caller, which is a real cost at an API boundary and the sort of thing that
   decides which version ships.

Also know `getOrDefault` — the read-only one, no insertion — and that reaching for
`containsKey` before `get` is almost always one of these three methods spelled long-hand.

### The `toMap` trap

TODO 4, and `WordTallyTest.strict_to_map_rejects_a_duplicate_key` is the one to read before you
write anything:

    IllegalStateException: Duplicate key fig (attempted merging values 3 and 3)

Two-argument `Collectors.toMap` has nowhere to put a collision, so it throws. **Everyone expects
it to overwrite**, because that is what `Map.put` does and what every other language's equivalent
does. It does not. This fails in production the first time real data has a repeated key, having
passed every test written against distinct sample data — which is the same failure shape
[doc 00](00-start-here.md) describes for `ArrayUtils.removeElement`, and the same lesson: *the
sample data is the bug*.

The three-argument form takes a merge function and is what you almost always want. Know that
`(a, b) -> a` is "first wins", `(a, b) -> b` is "last wins", and that writing either of them
without a comment is how the next person loses an afternoon.

### Comparator chaining, again

TODO 5 is the `reversed()` placement trap from [doc 12](12-lambdas-and-method-references.md),
moved onto `Map.Entry` where it is harder to see:

    comparingByValue().reversed().thenComparing(comparingByKey())  // [fig, pear, apple, kiwi]
    comparingByValue().thenComparing(comparingByKey()).reversed()  // [fig, pear, kiwi, apple]

`apple` and `kiwi` both appear once. The second form reverses the tie-breaker along with the
count, so the alphabetical order comes out backwards. **Both compile, both are "sort by count
descending", and only test data containing a tie can tell them apart.** This is the second time
this document set has shown you the same bug; that is deliberate.

While you are here: `Map.Entry.<String,Integer>comparingByValue()` needs that explicit type
witness and will not infer. Knowing why saves you ten minutes of fighting the compiler.

### `FairWorkLoad`, and the right to say no

`algorithm/FairWorkLoad.java` is a binary search over index arithmetic. You *can* express parts of
it with streams and a `groupingBy`. **Decide whether you should, and write the decision down.**
[Doc 09](09-streams-and-optional.md) makes the same point about the same file, which means this is
the second time you are being asked, which means a one-line answer is no longer good enough.

And note its oldest debt: `FairWorkloadTest.testMaxNumPerSixWorkers` has been `@Disabled` since
2014. **Do not touch that file in this document.** Diagnose it in stage 09 first. A rewrite that
makes a twelve-year-old failing test pass by accident has taught you nothing and destroyed the
evidence.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: what does `groupingBy` returning `Long` cost you at a REST or JPA boundary, and would you
re-map it or change the callers? `computeIfAbsent` on a `ConcurrentHashMap` with a mapping function
that touches the same map **deadlocks the bin** — is that documented behaviour, and would you have
found it without being told? At what input size does the stream version of a counter beat the
`merge` loop, and did you measure or guess? `Map.of` throws on duplicate keys and on nulls, and
returns an immutable map — when is that the wrong default? ([Stage 5](INDEX.md) is about exactly
that.)
