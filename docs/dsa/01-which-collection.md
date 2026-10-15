# 01. Which collection, and what it costs

**Stage 1 of [the DSA track](INDEX.md)** · runnable, no tests — the deliverable is measurements
and written answers

## Where it lands

**`CircularList.java`** (112 lines, a hand-rolled sorted circular linked list) and
**`algorithm/MyArrayList.java`** (28 lines, an unfinished growable array). Both are
reimplementations of something the JDK already has, and
[modernization doc 10](../modernization/10-sequenced-collections-and-httpclient.md) owns rewriting
them. **This stage does not rewrite them.** It asks what the JDK versions actually cost, because
that is the decision you make weekly and these two files are the decision nobody made.

## The claim this stage exists to kill

> "`LinkedList` is better for insertion because it is O(1)."

Measured, inserting at `size()/2` a hundred thousand times:

| | Time |
| --- | --- |
| `LinkedList` | **5,204 ms** |
| `ArrayList` | **364 ms** |

**14x worse at the one operation it supposedly wins.** The O(1) is real and irrelevant: getting
*to* the middle is O(n) of dependent pointer loads, each one a potential cache miss, while
`ArrayList` does a single `System.arraycopy` that the CPU prefetches perfectly.

This is the shape of the whole stage. **Big-O ranks algorithms; it does not predict wall-clock**,
because it omits the constant and the memory hierarchy, and on modern hardware those are the
part that moved.

## Run these yourself

Do not take the table above on trust —
[doc 34](../modernization/34-measuring-it-properly.md) is about exactly that, and everything here
is a whole-program measurement rather than a microbenchmark, so wall-clock is the right tool.

    jshell
    jshell> var al = new java.util.ArrayList<Integer>(); var ll = new java.util.LinkedList<Integer>();

1. **Mid-list insertion**, `ArrayList` vs `LinkedList`, 100k iterations.
2. **`ArrayList` growth.** It grows by **1.5x**, not 2x. Work out how many reallocations and
   copies it takes to reach a million elements from the default capacity of 10, then measure
   whether `new ArrayList<>(1_000_000)` is actually faster. The answer is less dramatic than the
   reallocation count suggests, and knowing why is the point.
3. **`PriorityQueue` iteration order.** Add `5, 1, 3` and iterate it. **It is not sorted.** It is a
   binary heap, so only `peek()` is ordered; `toString()` shows you heap order and looks sorted
   often enough to fool a test. The only ordered traversal is repeated `poll()`, which empties it.
4. **`HashMap` treeification.** The folklore is "eight collisions makes a tree". It is wrong twice:
   the check fires on the **ninth** node, and under a table capacity of 64 `treeifyBin` **resizes
   instead of treeifying**. Measured with constant-hash keys, a default `HashMap` needs **eleven**
   colliding keys before a tree appears. Pre-sized to 64 it trees at the ninth.
   [Stage 03](03-equals-and-hashcode.md) is about why that matters far more than it sounds.

## The decision table worth memorising

Not complexities — **contracts**. [Stage 02](02-contract-matrix.md) tests these.

| Need | Reach for | Not |
| --- | --- | --- |
| Indexed access, append, iterate | `ArrayList` | `LinkedList`, ever |
| Queue or stack | `ArrayDeque` | `LinkedList`, `Stack` (it extends `Vector`, synchronised) |
| Key lookup | `HashMap` | anything hand-rolled |
| Keys in sorted order, or range queries | `TreeMap` / `NavigableMap` | sorting a list repeatedly |
| Insertion order preserved | `LinkedHashMap` | `HashMap` plus a list |
| Enum keys | `EnumMap` | `HashMap` |
| "Smallest first", repeatedly | `PriorityQueue` | re-sorting |
| Shared across threads | `ConcurrentHashMap` | `HashMap` plus `synchronized` |

`LinkedList` has no row. **There is no operation where it is the right answer** — it remains in
the JDK for compatibility, and the one thing it offers that `ArrayDeque` does not is the ability to
hold `null`, which [stage 02](02-contract-matrix.md) shows is a trap rather than a feature.

## Write the answers here

1. **Your own mid-list insertion numbers.** Did you reproduce the 14x, and at what size did
   `LinkedList` stop being merely slower and become unusable? _(fill in)_
2. **`ArrayList` pre-sizing**: how much did it actually save, and would you bother? _(fill in)_
3. **`PriorityQueue`**: write the test that would catch someone assuming iteration is sorted.
   _(fill in)_
4. **`CircularList` is 112 lines.** Which JDK type replaces it, how many lines does the
   replacement take, and what did the hand-rolled version provide that the replacement does not?
   (There is an honest answer, and it is not "nothing".) _(fill in)_
5. **The explain-it version.** _"Why not `LinkedList`?"_ — in three sentences, without saying
   "cache". _(fill in)_

## What got worse

- _(fill this in)_

Prompts: every row above is a decision that is expensive to reverse once a type is in a public
signature — which of them would you hide behind an interface, and which is over-engineering? When
is a plain array the right answer over any collection? `ArrayList` grows by 1.5x and never shrinks
— what does that do to a long-lived list that was briefly huge?
