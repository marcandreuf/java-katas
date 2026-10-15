# 02. The contract matrix: nulls, order, and refusals

**Stage 2 of [the DSA track](INDEX.md)** · new code: `collections/Contracts.java`, four TODOs

## Where it lands

New code, because the repo has no file that gets this wrong — which is luck rather than judgment.
[Stage 01](01-which-collection.md) gave you a table to memorise; this stage makes you *derive* the
part of it that causes runtime failures, by writing probes that turn a thrown contract into a
boolean.

Every method in `Contracts.java` is three lines. The value is the table the tests build.

## The matrix

Fill it in by writing the probes, not by reading this. Verified on JDK 26:

| | null key | null value |
| --- | --- | --- |
| `HashMap`, `LinkedHashMap` | accepted | accepted |
| `TreeMap` | **NPE** | accepted |
| `ConcurrentHashMap` | **NPE** | **NPE** |
| `Map.of` | **NPE**, at construction | **NPE**, at construction |
| `HashSet`, `ArrayList` | accepted | — |
| `TreeSet`, `ArrayDeque` | **NPE** | — |

Three things in there are worth more than the rest.

### `TreeMap` is asymmetric, and the reason is the whole track's thesis

A null **key** throws; a null **value** is fine. That is not an arbitrary choice: `TreeMap` has to
*order* its keys, ordering means calling `compareTo`, and you cannot compare `null`. The contract
falls out of the data structure. `TreeSet` refuses null elements for exactly the same reason, and
`HashMap` accepts a null key because hashing `null` is a special case you can just define.

**Every one of these refusals has a structural reason.** Knowing the reason means you never have
to memorise the cell.

### `ArrayDeque` refusing null is a migration hazard

[Stage 01](01-which-collection.md) tells you to prefer `ArrayDeque` over `LinkedList`, and
`LinkedList` accepts null while `ArrayDeque` does not. **So that swap is not
behaviour-preserving.** If the old code used `null` as an end-of-queue sentinel — which is exactly
what people did, because `poll()` returns `null` when empty — the "obvious" modernisation throws
on the first insert.

`ArrayDeque` refuses precisely so that `poll()` returning `null` is unambiguous. The refusal is
what makes the sentinel work.

### `Map.of` rejects at a different *moment*

**TODO 1's warning exists because of this.** Catch `NullPointerException` specifically, not
`Exception`:

    Map.of(null, 1)            -> NullPointerException            (construction)
    Map.of("k", 1).put(…)      -> UnsupportedOperationException   (it is immutable)

So the put-based probe cannot test `Map.of` at all, and a `catch (Exception e)` would have quietly
reported "rejects null" for the wrong reason. **A factory method rejects at construction; a mutable
map rejects at mutation.** Same contract, two exceptions, two moments. The test pins both.

This is also a small lesson about probe design: a probe that catches too broadly stops being a
measurement and becomes a guess.

## TODO 4: what `put` keeps

Two keys the comparator considers equal, put in order:

    TreeMap<String,Integer> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    m.put("Key", 1);
    m.put("KEY", 2);
    // m is {Key=2}

**The first key object and the second value.** They do not come from the same `put`. This is
`Map.put`'s documented behaviour — it replaces the value and leaves the existing key in place —
and almost nobody's mental model.

It bites when the key carries more than its identity. Two keys that are `equals` but not identical
— a `User` record compared on id while holding a stale `displayName`, say — and the map keeps the
*old* key with the *new* value indefinitely. Nothing is inconsistent by the contract and everything
is inconsistent by intent.

**And whether they collide at all is a property of the comparator, not of the strings.** Under
natural order those same two keys are two entries. [Stage 04](04-ordering-contracts.md) is that
half.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: you now know six null policies — is memorising them better than never putting null in a
collection, and what stops you? `Objects.requireNonNull` at the boundary versus letting the
collection throw: which failure is easier to debug at 3am? If `put` keeps the first key, how would
you force a key refresh, and would a reviewer notice it was deliberate? Which of these cells would
you expect to differ in Kotlin or Scala, and what does that tell you about the Java choices?
