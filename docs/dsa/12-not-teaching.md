# 12. What we are deliberately not teaching

**Stage 12 of [the DSA track](INDEX.md)** · no code, no tests — written answers only

## Why this is a stage

A syllabus is judged by what it leaves out. Eleven stages said what to learn; this one says what
**not** to, with a reason for each, so that the omissions are decisions rather than gaps.

It is also the stage to disagree with. Every row below is a judgment, and
[`research.md`](research.md) has the full argument plus the review against the OCP objectives and
*Effective Java* that tested it.

## Not taught, and why

| Topic | Why not |
| --- | --- |
| Red-black tree rotations | `HashMap` and `TreeMap` contain them. Twenty-five years of production use beats your weekend, and [stage 03](03-equals-and-hashcode.md) teaches the part that *is* load-bearing: your key must be `Comparable` or you lose the tree. |
| Implementing a hash table | `HashMap` degrades gracefully on bad hashes **by design** (JEP 180). The contract matters; the table does not. |
| Writing your own hash function | `Objects.hash` and records cover it. A bad hash costs you a constant; a broken `equals`/`hashCode` pair costs you correctness. |
| Implementing a linked list | `CircularList.java` is 112 lines in this repo and proves the hard part is the API, not the pointers. [Stage 01](01-which-collection.md) shows `LinkedList` losing 14x at the one thing it is for. |
| Implementing a growable array | `MyArrayList.java` is unfinished after eleven years. `ArrayList`'s 1.5x growth policy is the useful half and [stage 01](01-which-collection.md) measures it. |
| Dijkstra, A\*, Bellman-Ford | Unweighted graphs need BFS ([stage 11](11-reachability.md)). Weighted graphs need a library and a correctness argument. |
| DP tables, tabulation drills | *Noticing* exponential recursion is load-bearing and is [stage 08](08-recursion-depth.md). Filling in the table is a ritual. |
| Tries, segment trees, union-find, Fenwick trees | Zero appearances in eleven years of commits in this repo. If you need one you will know, and you will reach for a library. |
| Manual balancing, skip lists | Same. |
| Amortised-analysis proofs | The conclusion — "`ArrayList.add` is cheap" — is all you ever use. |
| Big-O recitation | [Stage 07](07-accidental-quadratics.md) is Big-O as a *recognition* tool, which is the useful half. Knowing TimSort is O(n log n) has never changed a line of code. |
| Bit-twiddling tricks | `Integer.bitCount`, `Math.clamp`, `>>>` and `BitSet` cover the real cases. |
| Sorting algorithms by hand | `Arrays.sort` is dual-pivot quicksort for primitives and TimSort for objects, and [stage 04](04-ordering-contracts.md) teaches the part that bites: the comparator contract. |

## The repo files given no stage, and why

Named plainly, because leaving them unlabelled would imply they were worth something:

- **`algorithm/FindMiddleIndex.java`** — 45 lines to find a midpoint. A toy.
- **`algorithm/PascalsTriangle.java`** — the iterative version. Nothing to learn that
  [stage 08](08-recursion-depth.md) does not cover with the recursive one.
- **`src/test/.../algorithm/RotateMatrix90DegreeInPlace.java`** — 56 lines, a `main` in the test
  tree with no `@Test` methods. [Doc 00](../modernization/00-start-here.md) noted in 2026 that it
  "still needs a home". It still does, and the honest answer may be deletion.

Three further files carry **verified bugs** that this track documents but does not own:

- `The100DoorsCyberDojoTest:89-107` asserts **nothing** — it prints. The kata's answer (perfect
  squares) is never checked.
- `algorithm/DecimalToBinary.java` divides by 16 and prints **hex**;
  [modernization doc 03](../modernization/03-pattern-matching.md) owns it.
- `algorithm/SumSearch.java:13` sorts the caller's array; [stage 04](04-ordering-contracts.md)
  asks you to decide what to do about it.

## Write the answers here

1. **Which row above do you disagree with?** Pick one, argue the other side, and say what evidence
   would change your mind. _(fill in)_
2. **Interview reality.** If you are interviewing somewhere that asks for Dijkstra on a whiteboard,
   this track has not prepared you and is not trying to. **Is that the right call for you right
   now?** Write down the honest answer, because it determines whether you need a second, separate
   track. _(fill in)_
3. **The one you will actually hit.** Of everything in the "not taught" table, which do you think
   you are most likely to meet in real work in the next two years, and what would you do when you
   do? _(fill in)_
4. **What did eleven stages miss?** You have now read the whole track. Name something load-bearing
   that is not in it. _(fill in)_
5. **The explain-it version.** _"What data structures should a Java developer know?"_ — answer in
   four sentences, without listing any. _(fill in)_

## What got worse

- _(fill this in)_

Prompts: this track optimises for engineering judgment and explicitly not for interviews — what
does that cost you, and who pays it? If "the JDK already did it" is the reason not to learn five of
these, what happens to your judgment when you hit the case the JDK did not do? Is there a topic
here that is interview-only *today* and becomes load-bearing with one change in your job?
