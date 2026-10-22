# 02. The contract matrix: nulls, order, and refusals

**Stage 2 of [the DSA track](INDEX.md)** · new code: `collections/Contracts.java`, four TODOs ·
test: `collections/ContractsTest.java`, 8 tests, `@Disabled` (verified)

## Where it lands

New code. No old file in the repo depends on a collection's `null` policy (verified: `grep` over
`src/main` for `put(null`, `add(null`, `offer(null` and `.poll()` finds nothing), and that's luck
rather than judgment. [Stage 01](01-which-collection.md) gave you a table to memorise. This stage
makes you *derive* the part of it that causes runtime failures, by writing probes that turn a
thrown exception into a `boolean`.

Each probe is about three lines. The value is the table the tests build.

## What this stage is actually about

Four words, defined once:

- a collection's **null policy** is whether it accepts `null` as a key, a value or an element.
  When it doesn't, it says so by throwing `NullPointerException` at the moment you try.
- a **sentinel** is a special value that means "nothing here": `poll()` returning `null` from an
  empty queue is one.
- a **probe** is a method that tries an operation and reports whether it worked, instead of
  letting the exception escape.
- **key retention** is which key object a map keeps when you `put` a key it already considers
  present.

That gives one rule, and everything below follows from it: **every refusal has a structural reason,
and if you know the reason you don't have to memorise the cell.** A sorted map refuses a `null`
key because it has to compare keys. A concurrent map refuses `null` because `get` returning `null`
has to mean "absent" with no second call to check. A deque refuses `null` because `poll()` uses
`null` as its sentinel.

## The worked example: why a sentinel needs a refusal

`LinkedList` accepts `null`. So (verified):

    var q = new LinkedList<String>();
    q.add(null);
    q.poll()    // null: the element you added
    q.poll()    // null: the queue is empty

Two calls, the same answer, two different meanings, and nothing in the code can tell them apart.
`ArrayDeque.add(null)` throws `NullPointerException` (verified), and that refusal is exactly what
makes `poll()` returning `null` unambiguous. **The refusal is what makes the sentinel work.**

`ConcurrentHashMap` refuses `null` values for the same reason, and there it matters more: in a
`HashMap` you can follow `get(k) == null` with `containsKey(k)` to tell "absent" from "maps to
null", but in a concurrent map another thread can change the answer between the two calls.

## The matrix

Fill it in by writing the probes, not by reading this. Verified on JDK 26, with a throwaway probe
per cell:

| | null key | null value |
| --- | --- | --- |
| `HashMap`, `LinkedHashMap` | accepted | accepted |
| `TreeMap` (natural order) | **NPE** | accepted |
| `ConcurrentHashMap` | **NPE** | **NPE** |
| `Hashtable` | **NPE** | **NPE** |
| `Map.of(...)` | **NPE**, at construction | **NPE**, at construction |
| `HashSet`, `ArrayList`, `LinkedList` | accepted | n/a |
| `TreeSet`, `ArrayDeque` | **NPE** | n/a |

Three of those cells are worth more than the rest.

### `TreeMap` is asymmetric, and the comparator decides

A `null` **key** throws; a `null` **value** is fine. The reason is structural: `TreeMap` has to
*order* its keys, the natural ordering calls `compareTo`, and you can't call a method on `null`.
Values are never compared, so they can be anything.

**A correction to the old version of this doc**, which said "you cannot compare `null`". You can,
if the comparator says how. `new TreeMap<>(Comparator.nullsFirst(Comparator.naturalOrder()))`
accepts a `null` key (verified). The refusal belongs to the natural ordering, not to the tree. That
makes the rule above more useful, not less: the structure needs an ordering, so the ordering's
null policy becomes the map's.

### `ArrayDeque` refusing `null` is a migration hazard

[Stage 01](01-which-collection.md) tells you to prefer `ArrayDeque` over `LinkedList`, and
`LinkedList` accepts `null` while `ArrayDeque` doesn't. **So that swap isn't
behaviour-preserving.** If the old code put `null` into the queue as an end-of-work marker, the
modernised version throws on the first `add`. The worked example above is why `ArrayDeque` refuses.

### `Map.of` rejects at a different *moment*

    Map.of(null, 1)              -> NullPointerException            (at construction)
    Map.of("k", 1).put("x", 2)   -> UnsupportedOperationException   (it's immutable)
    Map.of().put(null, 1)        -> UnsupportedOperationException   (null never examined)

All three verified. **A factory method rejects at construction; a mutable map rejects at
mutation.** A put-based probe can't test `Map.of` at all: the immutability check fires before the
`null` check. A probe that caught `Exception` would report "rejects null keys" for `Map.of`, and
it would be right for the wrong reason. That's why TODO 1 says to catch `NullPointerException`
specifically.

**But the test doesn't enforce it.** `ContractsTest` checks `Map.of` with `assertThrows`
directly, and never passes `Map::of` to a probe. Verified on a scratch copy: probes that catch
`Exception` pass all 8 tests. So TODO 1's warning is advice the suite can't check. Part of this
stage is fixing that.

## The commits

1. **Add the test the suite is missing, first.** One new test in `ContractsTest`: hand `Map::of`
   to `acceptsNullKey`, and assert what a correct probe does. Decide what that is: should the
   `UnsupportedOperationException` escape, or should the probe report something? Write down your
   answer in the test's name. This test is what makes catching `Exception` a failure instead of a
   style point.
2. **Delete the `@Disabled`** and implement TODOs 1 to 3. Predict each cell of the matrix before
   you run it. Note which predictions were wrong.
3. **TODO 4**, as its own commit (below).

## TODO 4: what `put` keeps

Two keys the comparator considers equal, put in order (verified):

    TreeMap<String,Integer> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    m.put("Key", 1);
    m.put("KEY", 2);
    // m is {Key=2}

**The first key object and the second value.** They don't come from the same `put`. `Map.put`'s
javadoc only promises that the old *value* is replaced; it says nothing about replacing the key,
and neither `TreeMap` nor `HashMap` does (verified, both below). Almost nobody's mental model includes that.

It bites when the key carries more than its identity. The same thing happens in a `HashMap`
(verified): a key class whose `equals` and `hashCode` use only an `id`, put twice with a different
`name`, leaves the map holding `Id(1, old) = 2`. The map keeps the *old* key with the *new* value
indefinitely. Nothing is inconsistent by the contract, and everything is inconsistent by intent.
(The old version of this doc used a record for this example. A record's `equals` uses every
component, so two records with different names aren't equal and both would be kept. It has to be
a class with a hand-written `equals`.)

**And whether they collide at all is a property of the comparator, not of the strings.** Under
natural order those same two keys are two entries. [Stage 04](04-ordering-contracts.md) is that
half.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: you now know seven null policies. Is memorising them better than never putting `null` in
a collection, and what stops you doing that? `Objects.requireNonNull` at the boundary versus
letting the collection throw: which failure is easier to debug at 3 a.m.? If `put` keeps the first
key, how would you force a key refresh, and would a reviewer notice it was deliberate? Which of
these cells would you expect to differ in Kotlin or Scala, and what does that tell you about the
Java choices?
