# 03. `equals`, `hashCode`, and the `Comparable` surprise

**Stage 3 of [the DSA track](INDEX.md)** · new code: `equality/Keys.java`, four TODOs · test:
`equality/KeysTest.java`, 8 tests, `@Disabled` (verified)

## Where it lands

New code. **This is the stage the track exists for.** Breaking the `equals`/`hashCode` contract
doesn't throw, doesn't warn, and doesn't fail a `List`-based test. It silently loses entries in a
hash-based collection, and nobody notices until somebody asks why the cache never hits.

## What this stage is actually about

Four words, defined once:

- a **bucket** is one slot of a `HashMap`'s internal array. `hashCode()` picks the bucket; only
  then does `equals()` compare the key against the entries already in that bucket.
- a **collision** is two different keys landing in the same bucket. A few are normal. Thousands
  in one bucket is either a terrible `hashCode` or an attack.
- **treeification** is what a `HashMap` does to a bucket that gets too full: it turns the bucket's
  linked list into a red-black tree, so a lookup takes O(log n) steps instead of O(n).
  [Stage 01](01-which-collection.md) measured when it happens.
- the **contract** is the one in `Object`'s javadoc: if `a.equals(b)`, then `a.hashCode() ==
  b.hashCode()`. Not the reverse: unequal objects may share a hash code.

That gives one rule, and everything below follows from it: **a `HashMap` looks only at `hashCode`
first, so two keys that are `equals` but hash differently never meet.** `equals` is never even
called. Everything in part one is that sentence, and part two is what happens when the hash codes
are all the same and the map has to tell keys apart some other way.

## The worked example: a key that changes after you put it

Not the exercise, but the same mechanism from the other side. A key class with a mutable field
that `hashCode` uses (verified):

    var key = new P(1);            // equals and hashCode both use x
    map.put(key, "v");
    key.x = 2;

    map.size()             // 1
    map.get(key)           // null
    map.get(new P(1))      // null
    map.get(new P(2))      // null
    map.keySet()           // [P(2)]: it's in there, and nothing can find it
    map.put(key, "again")  // map is now {P(2)=v, P(2)=again}: two entries, equal keys

The entry is filed in bucket 1. The key now hashes to bucket 2, so every lookup goes to bucket 2
and finds nothing. The map isn't broken. It's doing exactly what the rule says, with a key that
broke the contract *after* insertion. A `HashSet` does the same: `remove(key)` returns `false`
and the size stays 1 (verified).

Keep that picture in mind for part one, which breaks the contract *before* insertion.

## Part one: the silent loss

`Keys.EqualsOnly` is provided and deliberately broken: it overrides `equals` and not `hashCode`.
**Don't fix it; it's the control.** So its hash code is `Object`'s, which is different for every
instance. Three instances that all consider each other equal, put in a `HashSet` (verified):

    new HashSet<>(List.of(EqualsOnly(1), EqualsOnly(1), EqualsOnly(1))).size()   ->  3

Three entries for three equal objects. And put one in a `HashMap`, then ask for it with an equal
instance:

    containsKey(new EqualsOnly(7))   ->  false     (verified)

No exception anywhere. **TODO 1 is to predict both numbers before you run anything.** The rule
above is the whole explanation.

### Why this reaches production

    list.contains(new EqualsOnly(7))              ->  true     (verified)
    new EqualsOnly(7).equals(new EqualsOnly(7))   ->  true

**The broken key works perfectly in a `List`**, because `List.contains` uses `equals` and never
calls `hashCode`. So a test suite that checks the value type through a list, or with a direct
`assertEquals`, is **green**, while the `HashMap` in production loses entries.
`KeysTest.the_same_broken_key_works_in_a_list` pins this, because it's why the bug survives review.

**TODO 2** is `Correct`, and then the comparison that matters: `AsRecord` is provided, a one-line
record that gets both methods right, consistently, from its components. Count what you typed that
the record didn't make you type. Since Java 16, "I forgot `hashCode`" is a problem you can mostly
delete by choosing a record. [Modernization doc 01](../modernization/01-records.md) is the other
side of that, including when a record is the wrong choice.

## Part two: when every key collides

`Keys.Colliding` returns `42` from `hashCode`, so every instance goes into one bucket. TODO 4 is
`ComparableColliding`: the same class, plus `Comparable`, and nothing else changed.

**A correction first:** `Keys.java` says to "run KeysBenchmark (it has a main)". **There's no
`KeysBenchmark` in the repo** (verified, `find src -name 'KeysBenchmark*'`). Here's a driver. Save
it as `KeysRace.java` **outside the repo**, because it's a measuring tool, not exercise code:

    import java.util.*; import java.util.function.IntFunction;
    import org.mandfer.practices.equality.Keys;
    public class KeysRace {
        static long millis(IntFunction<Object> key, int n) {
            long start = System.nanoTime();
            Map<Object, Integer> map = new HashMap<>();
            for (int i = 0; i < n; i++) { map.put(key.apply(i), i); map.get(key.apply(i)); }
            return (System.nanoTime() - start) / 1_000_000;
        }
        public static void main(String[] a) {
            int n = Integer.parseInt(a[0]);
            for (int run = 0; run < 3; run++)
                System.out.println("Colliding " + millis(Keys.Colliding::new, n) + " ms   ComparableColliding "
                        + millis(Keys.ComparableColliding::new, n) + " ms");
        }
    }

    mvn -q compile
    java -cp target/classes KeysRace.java 20000

**Run it before TODO 4 as well as after.** Before, the two classes are identical and so are their
times. That's your control. Verified, 20,000 put-then-get pairs, three runs each:

| | `Colliding` | `ComparableColliding` |
| --- | --- | --- |
| before TODO 4 | 2.7 to 3.4 s | 2.5 to 3.3 s |
| after TODO 4 | 3.3 to 4.1 s | 82 ms, then 11 to 14 ms |

(The machine was busy, so read the ratio, not the times. The first `ComparableColliding` run
includes JIT warm-up.)

**The old version of this doc said "65x", and that number isn't a constant.** The ratio here was
about 40x on the first run and about 300x once warmed up. And it grows with the input: in a
separate run, `Colliding` took 106 ms at 5,000 keys and 463 ms at 10,000 (verified), so doubling
the keys roughly quadrupled the time. That's O(n²) total, O(n) per operation. The `Comparable`
version is O(n log n) total. **A ratio between two different growth rates has no fixed value**,
so the answer to "how much faster?" is "it depends on n, and gets worse." Try 40,000 and see.

### Why `Comparable` matters to a hash map

The mechanism is [JEP 180](https://openjdk.org/jeps/180) (Java 8): when a bucket overflows, it
becomes a red-black tree. **A tree needs an ordering** to decide whether to go left or right.

**A correction to the old version**, which said that without `Comparable` the map "falls back to a
much worse path". It doesn't fall back; it still builds the tree. Verified: after 100 `Colliding`
keys, the bucket's node class is `HashMap$TreeNode`. Without `Comparable`, though, the map has
no way to say which side a key is on. It uses a tie-breaker to *insert*, but to *find* a key it
has to search both subtrees (read `HashMap.TreeNode.find` in the JDK's `lib/src.zip`: when the
hashes are equal and the key isn't `Comparable`, it calls `pr.find(...)` and then carries on down
`pl`), so every lookup visits the whole tree. You pay for the tree and
still get the linear scan. With `Comparable`, `compareTo` picks a side at every node, and a lookup
is O(log n).

The JEP's own wording is that it improves collision performance "for any key type that implements
`Comparable`" (verified, from the JEP text).

### And the security angle

**A correction here too.** The old version said "JEP 180 exists because of CVE-2011-4858". The
JEP never mentions security or that CVE (verified, from the JEP text). Its stated motivation is
performance, and replacing an earlier JDK workaround called *alternative string hashing*. What
follows is background, **not verified here**: that workaround was the JDK's response to the
hash-flooding disclosures of December 2011, where an attacker sends a request whose parameter
names all collide, turning every lookup into a linear scan. CVE-2011-4858 is Tomcat's entry for
that attack. Making such names is easy, and this part is verified: `"Aa"` and `"BB"` both hash to
2112, so every same-length string built from those two blocks collides too. `"AaAa"`, `"AaBB"`,
`"BBAa"` and `"BBBB"` all hash to 2031744.

The part you *can* verify, and the part that matters: `String` implements `Comparable` (verified),
so a flood of colliding `String` keys now lands in a tree that can be searched in O(log n). **A key
class of your own that isn't `Comparable` gets no such protection.** If its hash codes can be
chosen by an attacker, every lookup is a linear scan. That's the best available answer to "why
would I implement `Comparable` on a map key?", and the reason isn't sorting.

[Stage 04](04-ordering-contracts.md) is the correctness half of the same interface: *Effective
Java* Item 14 wants `Comparable` for ordering, and JEP 180 rewards it for collisions. One
interface, two unrelated reasons.

## The commits

1. **TODO 1, before any code.** Write your two predictions in the commit message, delete the
   `@Disabled`, and commit. The suite now fails: every test that calls the helpers throws
   `UnsupportedOperationException("TODO 3")`. (`the_same_broken_key_works_in_a_list` passes
   already: it never calls a helper.)
2. **TODO 3**, the two helpers. After this, 5 of the 8 tests pass and 3 fail (verified):
   `overriding_both_deduplicates` (3, not 1), `a_correct_key_round_trips`, and
   `the_colliding_key_becomes_comparable`. Check that it's exactly those, and that your
   predictions held.
3. **TODO 2**, `Correct`. Its tests go green.
4. **Run `KeysRace`, then TODO 4, then run it again.** Put both sets of numbers in the commit
   message. `KeysTest` only checks that `ComparableColliding` *is* `Comparable`. The speed-up
   isn't something a unit test can assert, so the commit message is where the evidence goes.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: if a record fixes this for free, what's left that's worth a hand-written `equals`? The
worked example's mutable key ended up as two equal entries in one map: is that better or worse
than part one's lost entry, and which would you rather debug? `Objects.hash(a, b, c)` allocates a
varargs array on every call: at what call rate would you care, and how would you find out? Would
you add `Comparable` to a key class purely for part two, with no ordering requirement, and how
would you justify it in review?
