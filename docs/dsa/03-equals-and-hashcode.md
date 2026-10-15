# 03. `equals`, `hashCode`, and the 65x `Comparable` surprise

**Stage 3 of [the DSA track](INDEX.md)** · new code: `equality/Keys.java`, four TODOs

## Where it lands

New code. **This is the stage the track exists for.** Breaking the `equals`/`hashCode` contract
does not throw, does not warn, and does not fail a `List`-based test. It silently loses entries in
a hash-based one, and the loss is invisible until somebody notices the cache never hits.

## Part one: the silent loss

`Keys.EqualsOnly` is provided and is deliberately broken — it overrides `equals` and not
`hashCode`. **Do not fix it; it is the control.** Three instances that all consider each other
equal, put in a `HashSet`:

    hashSetSize([EqualsOnly(1), EqualsOnly(1), EqualsOnly(1)])  ->  3

Three entries for three equal objects. And:

    roundTripsInHashMap(new EqualsOnly(7), new EqualsOnly(7))   ->  false

Put it in, ask for an equal key, get nothing back. No exception anywhere.

**TODO 1 is to predict both numbers before you run the test.** The mechanism is one sentence:
`HashMap` uses `hashCode` to pick the bucket and only then uses `equals` within it. Unequal hashes
send equal objects to different buckets, and `equals` is never consulted.

### Why this reaches production

    list.contains(new EqualsOnly(7))   ->  true
    new EqualsOnly(7).equals(new EqualsOnly(7))  ->  true

**The broken key works perfectly in a `List`**, because `List.contains` uses `equals` and never
calls `hashCode`. `equals` itself is fine. So a test suite that exercises the value type through a
list, or through direct `assertEquals`, is **green**, while the `HashMap` in production loses
entries. The test pins this explicitly, because it is the reason the bug survives review.

**TODO 2** is `Correct`, and then the comparison that matters: `AsRecord` is provided and is a
one-line record that gets both methods right, consistently, derived from its components. Count what
you typed that the record did not make you type. Since Java 16, "I forgot `hashCode`" is a problem
you can mostly delete by choosing a record —
[modernization doc 01](../modernization/01-records.md) is the other side of that.

## Part two: the 65x nobody mentions

**TODO 4 is the finding this stage contributes to the track.** Two keys, both with
`hashCode() { return 42; }` so everything collides into one bucket. 20,000 put-then-get pairs. The
*only* difference is whether the key implements `Comparable`:

| Key | Time |
| --- | --- |
| `Colliding` (not `Comparable`) | **2,735 ms** |
| `ComparableColliding` | **42 ms** |

**65x, for implementing an interface that has nothing to do with hashing.**

The mechanism is [JEP 180](https://openjdk.org/jeps/180). When a `HashMap` bucket overflows it
converts from a linked list to a red-black tree, taking the worst case from O(n) to O(log n). **A
tree needs an ordering.** If the key is `Comparable`, `compareTo` provides it. If not, `HashMap`
falls back to a much worse path, and you pay the linear scan you thought treeification saved you
from.

### And it is a security feature, not a performance one

JEP 180 exists because of **CVE-2011-4858**: hash-flooding denial of service. An attacker sends a
POST body whose parameter names all collide into one bucket, every lookup becomes a linear scan,
and one request burns a core. Treeification is the mitigation, and the JEP says it works for "any
key type that implements `Comparable`".

So **a key class without `compareTo` is not just slower under collision — it is still vulnerable to
the attack the JDK already fixed.** Write that down. It is the best available answer to "why would
I implement `Comparable` on a map key", and the reason is not sorting.

[Stage 04](04-ordering-contracts.md) is the correctness half of the same interface. *Effective
Java* Item 14 wants `Comparable` for ordering; JEP 180 wants it for the 65x. One interface, two
unrelated reasons, and a key class that omits it pays both.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: if a record fixes this for free, what is left that is worth a hand-written `equals`?
Mutable keys: what happens to a `HashMap` entry when a field used by `hashCode` changes after
insertion, and is that worse or better than losing the entry outright? `Objects.hash(a, b, c)`
allocates a varargs array on every call — at what call rate would you care, and how would you find
out? Would you add `Comparable` to a key class purely for the 65x, with no ordering requirement,
and how would you justify it in review?
