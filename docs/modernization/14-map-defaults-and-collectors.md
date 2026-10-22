# 14. `Map` default methods, comparators and collectors

**Java 8** · stage 3 of [the release-ordered track](INDEX.md) · compiles at `--release 21`, no
build change

> **No safety net:** `FindDups` has no test. Write one first, in a separate commit, asserting
> what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how,
> and why the order matters. `WordTally` is new code with its tests already written.

## What this stage is actually about

Five words, defined once:

- a **default method** is an interface method with a body. Java 8 used them to add methods to
  `Map` without breaking every class that implements it: `getOrDefault`, `putIfAbsent`, `merge`,
  `computeIfAbsent`, `compute`, `forEach`, `replaceAll`.
- **`merge(key, value, fn)`** puts `value` if the key is absent, and otherwise stores
  `fn(old, value)`.
- **`computeIfAbsent(key, fn)`** calls `fn(key)` only if the key is absent, stores the result, and
  returns whatever is now mapped.
- a **downstream collector** is the collector inside another one: in `groupingBy(f, counting())`,
  `counting()` decides what each group becomes.
- a **comparator combinator** builds a comparator out of smaller ones: `comparing`,
  `thenComparing`, `reversed`.

That gives one rule, and everything below follows from it: **each of these methods' contracts
says exactly when your function runs and when it doesn't. Read the contract, not the name.**
Every trap in this doc is a method doing what its javadoc says, which wasn't what its name
suggested.

## The worked example

Five `Map` calls, and what each one does with a key that is absent, present, or mapped to `null`
(verified, on a `HashMap`):

    m.put("x", null);
    m.getOrDefault("x", 0)              // null: the key is present, so no default
    m.putIfAbsent("x", 1)               // returns null and STORES 1: a null value counts as absent

    m.put("k", 1);
    m.merge("k", 100, (o, n) -> o + n)  // fn(1, 100) is called: 101
    m.merge("j", 100, (o, n) -> o + n)  // fn is never called: 100 is stored as it is
    m.merge("y", 1, (o, n) -> null)     // returns null and REMOVES "y"
    m.merge("z", null, Integer::sum)    // NullPointerException

    m.computeIfAbsent("q", k -> null)   // stores nothing: the map stays as it was

Not one of those is what you'd guess from the name alone. `getOrDefault` and `putIfAbsent`
disagree about whether a `null` value means "absent". `merge`'s second argument is only used when
the key is **absent**; its function is only called when the key is **present**. Nearly everyone
reads it the other way round the first time.

And here's what `computeIfAbsent` is actually for, the **multimap** (verified):

    Map<Character, List<String>> byLetter = new TreeMap<>();
    for (String w : words)
        byLetter.computeIfAbsent(w.charAt(0), k -> new ArrayList<>()).add(w);
    // {a=[apple], f=[fig, fig, fig], k=[kiwi], p=[pear, pear]}

The function only runs once per key, to create the list. Every later call returns the same list,
and `.add(w)` mutates it. That's the idiom that makes Guava's `Multimap` unnecessary for most code.

## Part one: `WordTally`, five ways to count

`collections/WordTally.java` is new code. `WordTallyTest` has ten tests on
`fig pear fig apple pear fig kiwi`. Delete its `@Disabled` to begin. They all pass against a
scratch implementation (verified), so a failure is yours, not the test's.

**TODOs 1–3 all produce `{fig=3, pear=2, apple=1, kiwi=1}`.** The point is the differences.

1. **TODO 1, `merge`.** Given the worked example, this is one line in a loop.
2. **TODO 2, `computeIfAbsent`.** It's the wrong tool for counting, and that's why it's a TODO.
   Its function only runs when the key is missing, so it can't increment anything. Make it work
   anyway and notice what you had to add. **Choosing between TODO 1 and TODO 2 for a given job is
   the deliverable of this part.**
3. **TODO 3, `groupingBy` with `counting()`.** The test expects `Map<String, Long>`, because
   `counting()` returns a `Long`. **A correction to the old version of this doc:** it said you
   "cannot ask it for `Integer`". You can: `groupingBy(w -> w, summingInt(w -> 1))` gives
   `Map<String, Integer>` (verified). The signature here fixes `Long`, so the question is which
   you'd choose at an API boundary, and why `counting()` returns `Long` in the first place.

**TODO 4, the `toMap` trap.** Read `strict_to_map_rejects_a_duplicate_key` before you write
anything. Two-argument `Collectors.toMap` throws on a repeated key (verified):

    IllegalStateException: Duplicate key fig (attempted merging values 3 and 3)

Everyone expects it to overwrite, because `Map.put` does. It doesn't: it has nowhere to put a
collision. This kind of bug passes every test written against distinct sample data, then fails in
production the first time a key repeats. It's the same failure as `ArrayUtils.removeElement` in
[doc 09](09-streams-and-optional.md): *the sample data is the bug*. The lenient version takes a
merge function. Whichever one you pass, say in a comment which value wins.

**TODO 5, `topWords`.** Most frequent first, ties alphabetical ascending. It's the `reversed()`
placement trap from [doc 12](12-lambdas-and-method-references.md), moved onto `Map.Entry`, where
it's harder to see. `apple` and `kiwi` both occur once. One natural chain gives
`[fig, pear, apple, kiwi]` and another, with `reversed()` in a different place, gives
`[fig, pear, kiwi, apple]` (verified). Both compile. Only a test with a tie in it can tell them
apart, which is why `top_words_break_ties_alphabetically_ascending` exists.

On the way you'll meet a type-inference error. **A correction to the old version:** it said
`Map.Entry.comparingByValue()` "needs that explicit type witness and will not infer". Assigned
directly to a `Comparator<Map.Entry<String, Long>>`, it infers fine (verified). It fails only when
you chain a method onto it, because Java infers type arguments from the target type, and
`.reversed()` is called before there is a target (verified):

    l.sort(Map.Entry.comparingByValue().reversed());
    // error: ... found: Comparator<Entry<Object,V#1>>

`Map.Entry.<String, Long>comparingByValue()` is the fix, and now you know why it's needed.

## Part two: `FindDups`, the question it never answered

`collections/FindDups.java:20-25` reports "Duplicate detected: cat" but never how many times.
[Doc 09](09-streams-and-optional.md) left exactly that question here.

**A correction to the old version:** it said doc 09 asks you to turn `FindDups` into
`distinct().count()`. Doc 09 now shows that `distinct()` is wrong for this file, because it's
case-sensitive and the original isn't. **The same trap is waiting here.** Group
`Cat dog cat Dog bird` by the word itself and you get **five** groups of one (verified), while the
original reports three distinct words:

    groupingBy(w -> w, counting())             // {bird=1, cat=1, Cat=1, Dog=1, dog=1}
    groupingBy(w -> w, TreeMap::new, counting()) // sorted, but still 5 keys

Commits, in this order:

1. **Characterize**, as [`../testing-first.md`](../testing-first.md) describes. If you did doc 09
   first, the test exists. Make sure it has the mixed-case input.
2. **Add the counts**: a method returning word → occurrences, case-insensitive, matching the
   original's idea of "the same word". Decide which spelling is kept as the key and test it. That
   decision is a behaviour you choose, not syntax you translate.
3. **Decide what `main` prints**, now that it can say "cat ×2" instead of "Duplicate detected".

## Part three: `FairWorkLoad`, and the right to say no

[Doc 09](09-streams-and-optional.md) part three already covers this file: its inner loop carries
state from one element to the next, so it isn't a stream, and converting its two helpers before
diagnosing `FairWorkloadTest.testMaxNumPerSixWorkers` would fix a twelve-year-old bug by accident.
**Don't touch it in this doc.** Nothing in it is a counting or grouping problem: the old version
suggested "a `groupingBy`", and there's nothing to group. Write one line saying why, and move on.
[DSA stage 06](../dsa/06-answer-space-search.md) has the diagnosis.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: what does `groupingBy` returning `Long` cost at a REST or JPA boundary, and would you
re-map it or change the callers? `merge` with a function returning `null` deletes the key: is that
a feature or a trap? The `ConcurrentHashMap.computeIfAbsent` javadoc forbids a mapping function
that updates the same map. **A correction to the old version:** it said doing it "deadlocks the
bin". On JDK 26, recursing on the same key throws `IllegalStateException: Recursive update`
(verified), and a plain `HashMap` throws `ConcurrentModificationException` (verified). A
different key in the same bin returned normally in my test (keys 0 and 16), which proves only that
it can work, not that it's allowed. Would you have found the rule without being told? At what input
size does the stream counter beat the `merge` loop, and did you measure or guess?

Three smaller Java 8 additions to try in `jshell`, all verified: `StringJoiner` with
`setEmptyValue("(none)")` (compare with `CircularList.toString`'s trailing comma in
[doc 10](10-sequenced-collections-and-httpclient.md)); `Base64.getEncoder()` vs
`getUrlEncoder()`, which turn the same three bytes into `+//+` and `-__-`; and
`Arrays.parallelSort`, which is worth timing against `Arrays.sort` on an array of ten thousand
before you believe it helps. `Map.of` throws on duplicate keys and nulls, and returns an immutable
map: when is that the wrong default? [Doc 16](16-collection-factories.md) is about exactly that.
