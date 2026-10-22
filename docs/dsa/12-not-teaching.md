# 12. What we are deliberately not teaching

**Stage 12 of [the DSA track](INDEX.md)** · no code, no tests: written answers only

## What this stage is actually about

Three words, defined once:

- **load-bearing** means you'll meet it in real Java work and getting it wrong costs you:
  correctness, a production incident, or a slow service. Every earlier stage is one of these.
- **interview-only** means you'll meet it on a whiteboard and almost never in a codebase, because
  the JDK or a library already did it.
- an **omission** is a topic a syllabus leaves out. It's either a **decision**, with a reason you
  can argue with, or a **gap**, which nobody noticed.

That gives one rule, and everything below follows from it: **a syllabus is judged by what it leaves
out, so every omission needs a reason, and every reason has to survive being checked.** Eleven
stages said what to learn. This one says what not to, and it's the stage to disagree with.
[`research.md`](research.md) has the full argument, plus the review against the OCP objectives and
*Effective Java* that tested it.

## The worked example: checking one row

The table below says not to implement a hash table, because "`HashMap` degrades gracefully on bad
hashes by design (JEP 180)". Here's what checking that reason looks like.

JEP 180 (Java 8) turns a bucket with too many collisions into a balanced tree, so the worst case is
`O(log n)` instead of `O(n)`. The test: 20,000 keys that all return `hashCode() == 42`, looked up
three times each (verified, six runs on an idle machine):

| Keys | Lookup time |
| --- | --- |
| a record with a normal `hashCode` | under 25 ms |
| all colliding, `Comparable` | under 25 ms |
| all colliding, **not** `Comparable` | **4,400–5,700 ms** |

**The reason is half true.** The tree only works if it can *order* the keys. Without `Comparable`,
a lookup in the tree has to search both sides, and it degrades almost to the linked list it was
meant to replace. [Stage 03](03-equals-and-hashcode.md) is built on exactly this.

**The conclusion survives anyway:** you still shouldn't implement a hash table. But the
justification changes, from "the JDK handles it" to "the JDK handles it **if your key keeps a
contract you have to know about**". That's the shape of a good answer to question 1 below: not
"I disagree", but which part of the reason fails, the evidence, and whether the conclusion still
stands.

## Not taught, and why

| Topic | Why not |
| --- | --- |
| Red-black tree rotations | `TreeMap` contains them, and has since Java 1.2 in 1998. What *is* load-bearing is your side of the contract: the key must be `Comparable`, or you supply a comparator ([stage 04](04-ordering-contracts.md)). |
| Implementing a hash table | `HashMap` protects against collisions by design (JEP 180), **for keys that are `Comparable`**: see the worked example. The contract matters; the table doesn't. |
| Writing your own hash function | `Objects.hash` and records cover it. A poor hash costs you speed; a broken `equals`/`hashCode` pair costs you correctness ([stage 03](03-equals-and-hashcode.md)). |
| Implementing a linked list | `CircularList.java` is 112 lines in this repo (verified), all six of its tests are green, and it still misplaces any value smaller than the head: `insert` 5, 10, 20, 1 gives `5,10,20,1,` (verified; [modernization doc 10](../modernization/10-sequenced-collections-and-httpclient.md)). The hard part is the API and the edge cases, not the pointers. [Stage 01](01-which-collection.md) measures `LinkedList` at the one thing it's meant to be for. |
| Implementing a growable array | `MyArrayList.java` is dated 20/10/2014 and still unfinished: `size()` returns 10 when it's empty, and the 11th `add` throws (verified; [modernization doc 10](../modernization/10-sequenced-collections-and-httpclient.md)). `ArrayList`'s 1.5× growth is the useful half, and [stage 01](01-which-collection.md) has you work it out. |
| Dijkstra, A\*, Bellman-Ford | Unweighted graphs need BFS ([stage 11](11-reachability.md)). Weighted graphs need a library and a correctness argument. |
| DP tables, tabulation drills | *Noticing* exponential recursion is load-bearing, and it's [stage 08](08-recursion-depth.md). Filling in the table is a ritual. |
| Tries, segment trees, union-find, Fenwick trees | None of them appears anywhere in this repo's source (verified by search; the only hit for any of these names is the word "Dijkstra" in a comment in `Maze.java`). If you need one, you'll know, and you'll reach for a library. |
| Manual balancing, skip lists | Same. |
| Amortised-analysis proofs | The conclusion, "`ArrayList.add` is cheap on average", is all you ever use. |
| Big-O recitation | [Stage 07](07-accidental-quadratics.md) is Big-O as a *recognition* tool, which is the useful half. Knowing a sort is `O(n log n)` has never changed a line of code. |
| Bit-twiddling tricks | `Integer.bitCount`, `Math.clamp` (Java 21), `>>>` and `BitSet` cover the real cases. |
| Sorting algorithms by hand | `Arrays.sort` is a dual-pivot quicksort for primitives and a TimSort adapted from Python's for objects (both from the JDK 26 source's implementation notes, verified). [Stage 04](04-ordering-contracts.md) teaches the part that bites: the comparator contract. |

## The repo files given no stage, and why

Named plainly, because leaving them unlabelled would imply they were worth something:

- **`algorithm/PascalsTriangle.java`**: the iterative version, 56 lines (verified). Nothing to
  learn that [stage 08](08-recursion-depth.md) doesn't cover with the recursive one.
- **`src/test/.../algorithm/RotateMatrix90DegreeInPlace.java`**: 56 lines, a `main` in the test
  tree with **zero** `@Test` methods (verified). [Doc 00](../modernization/00-start-here.md) says
  it "still needs a home". It still does, and the honest answer may be deletion.

**A correction to the old version of this doc**, which listed `algorithm/FindMiddleIndex.java`
here as "45 lines to find a midpoint. A toy." It doesn't find a midpoint: it finds the index where
the sum up to and including it equals the sum of everything after it. And it's no longer without a
home: [modernization doc 09](../modernization/09-streams-and-optional.md) part four uses it as the
`OptionalInt` exercise, because "no such index" is an ordinary answer that it currently reports by
throwing a checked `Exception`.

Three further files carry **verified bugs** that this track documents but doesn't own:

- `The100DoorsCyberDojoTest:89-107` asserts **nothing**: it builds a string and prints it
  (verified). The kata's answer, perfect squares, is never checked.
- `algorithm/DecimalToBinary.java` divides by 16 and prints each base-16 digit as a decimal
  number, so 255 comes out as `1515`: **not binary, and not even hex**.
  [Modernization doc 03](../modernization/03-pattern-matching.md) owns it.
- `algorithm/SumSearch.java:13` sorts the caller's array (verified: `Arrays.sort(sequence)` on the
  parameter). [Stage 04](04-ordering-contracts.md) asks you to decide what to do about it.

## Write the answers here

1. **Which row above do you disagree with?** Pick one, check its reason the way the worked example
   does, and say what evidence would change your mind. _(fill in)_
2. **Interview reality.** If you're interviewing somewhere that asks for Dijkstra on a whiteboard,
   this track hasn't prepared you and isn't trying to. **Is that the right call for you right
   now?** Write down the honest answer, because it decides whether you need a second, separate
   track. _(fill in)_
3. **The one you'll actually hit.** Of everything in the table, which are you most likely to meet
   in real work in the next two years, and what would you do when you do? _(fill in)_
4. **What did eleven stages miss?** You've read the whole track now. Name something load-bearing
   that isn't in it. _(fill in)_
5. **The explain-it version.** _"What data structures should a Java developer know?"_ Answer in
   four sentences, without listing any. _(fill in)_

## What got worse

- _(fill this in)_

Prompts: this track optimises for engineering judgment and explicitly not for interviews. What
does that cost you, and who pays it? The worked example found a row whose reason was half true. How
many of the others did you take on trust? If "the JDK already did it" is the reason not to learn
five of these, what happens to your judgment when you hit the case the JDK didn't do? Is there a
topic here that's interview-only *today* and becomes load-bearing with one change in your job?
