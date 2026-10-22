# 01. Which collection, and what it costs

**Stage 1 of [the DSA track](INDEX.md)** · runnable, no tests, no code committed: the deliverable
is measurements and written answers · every number below measured on JDK 26

## Where it lands

**`CircularList.java`** (112 lines, a hand-rolled sorted circular linked list) and
**`algorithm/MyArrayList.java`** (28 lines, an unfinished growable array) (both verified, `wc -l`).
Both reimplement something the JDK already has, and
[modernization doc 10](../modernization/10-sequenced-collections-and-httpclient.md) owns fixing
and replacing them. **This stage does not touch them.** It asks what the JDK versions actually
cost, because that's a decision you make every week, and these two files are what it looks like
when nobody made it.

## What this stage is actually about

Four words, defined once:

- **Big-O** describes how the number of steps grows with the input size *n*. O(1) means the step
  count doesn't grow; O(n) means it grows in proportion. It says nothing about how long one step
  takes.
- the **constant** is how long one step takes. Big-O drops it on purpose.
- the **memory hierarchy** is why the constant varies so much: reading the next element of an array
  is nearly free, because the CPU has already fetched it into cache. Following a pointer to
  wherever a linked-list node happens to live can be a cache miss, which is commonly quoted as
  around a hundred times slower (a textbook figure, not measured here).
- a **contract** is what a collection promises: whether it keeps order, whether it allows `null`,
  what it considers a duplicate. [Stage 02](02-contract-matrix.md) is about contracts.

That gives one rule, and everything below follows from it: **Big-O ranks algorithms; it does not
predict wall-clock time.** It leaves out the constant and the memory hierarchy, and on modern
hardware those are the part that changed. So you choose a collection by its contract, and then
you measure.

## The worked example: the one race `LinkedList` does win

Remove every other element from a 100,000-element list, through its iterator (verified, four
runs on an idle machine):

    LinkedList, iterator.remove()     4.0 to 8.8 ms
    ArrayList,  iterator.remove()     360 to 369 ms      40 to 90 times slower
    ArrayList,  removeIf(...)         9 to 11 ms on the first run of a JVM, 1.1 ms after that

That's the textbook case, and it's real: removing at the iterator's position is O(1) for a linked
list, and for an array it shifts every element after it, so 50,000 removals cost O(n²). **The old
version of this doc said "there is no operation where `LinkedList` is the right answer." The
first two rows say otherwise.**

The third row is why the advice survives anyway. `removeIf` (Java 8) compacts the array in one
pass, O(n), and once warmed up it beats the linked list. The loop that made `LinkedList` win is
a loop nobody needs to write any more. That's what this stage keeps finding: the complexity argument is
correct, and the operation it's about can usually be avoided.

## The claim this stage exists to kill

> "`LinkedList` is better for insertion because it is O(1)."

Inserting at `size()/2`, 100,000 times (verified, six runs over two JVMs, idle machine):

| | Time |
| --- | --- |
| `LinkedList` | 7.1 to 7.7 s |
| `ArrayList` | 0.37 to 0.38 s |

**About 20 times slower, at the one operation it's supposed to win.** (The old version of this
doc said 14x. Your machine will give its own number; it won't be close to even.) The O(1)
insertion is real and irrelevant here: getting *to* the middle is O(n) pointer-following, each step
a possible cache miss, while `ArrayList` does a single `System.arraycopy` over contiguous memory.
Both are O(n) per insert. The constants are what differ.

The worked example and this claim aren't in tension. `LinkedList` wins when you're already *at*
the position, through an iterator. It loses when it has to walk there. Mid-list insertion by index
always walks.

## Run these yourself

Don't take the numbers above on trust. [Doc 34](../modernization/34-measuring-it-properly.md) is
about why. These are whole-program measurements, not microbenchmarks, so wall-clock time is the
right tool. A single-file program (`java Probe.java`) or `jshell` both work.

1. **Mid-list insertion**, `ArrayList` vs `LinkedList`, 100k iterations. Then halve and double
   the count, and see which one grows faster.
2. **`ArrayList` growth.** It grows by **1.5x**, not 2x (`newCapacity = old + old >> 1` in the JDK
   source). Work out how many reallocations it takes to reach a million elements from the default
   capacity of 10, and how many elements get copied along the way. Then measure whether
   `new ArrayList<>(1_000_000)` is faster. (Verified here, ten runs on an idle machine: pre-sizing
   was usually faster, by anywhere from 1.1x to 6x, and once it was *slower*, 57 ms against 13 ms.
   The noise is garbage collection: most of the time goes into boxing a million `Integer`s, which
   pre-sizing doesn't save, and a collection landing in one run swamps the difference. Knowing
   that is the point.)
3. **`PriorityQueue` iteration order.** Add `5, 1, 3` and iterate it. You get `[1, 5, 3]`
   (verified): **not sorted.** It's a binary heap, so only `peek()` is guaranteed to be the
   smallest. And `toString()` shows heap order, which looks sorted often enough to fool a test:
   `new PriorityQueue<>(List.of(5, 1, 3, 4, 2))` prints `[1, 2, 3, 4, 5]` (verified). The only
   ordered traversal is repeated `poll()`, which empties the queue.
4. **`HashMap` treeification.** The folklore is "eight collisions make a tree". That's wrong twice:
   the check fires when the **ninth** node is added to a bucket, and while the table has fewer
   than 64 slots, `treeifyBin` **resizes the table instead of building a tree**. With keys that
   all share one hash code (verified, by looking at the bucket's node class):

   | | First tree appears at | Table size then |
   | --- | --- | --- |
   | `new HashMap<>()` | key **11** | 64 |
   | `new HashMap<>(64)` | key **9** | 64 |

   You can't see the bucket's node class through the public API. Reading `HashMap.table` by
   reflection needs `--add-opens java.base/java.util=ALL-UNNAMED` on the `java` command.
   [Stage 03](03-equals-and-hashcode.md) is about why treeification matters far more than it
   sounds.

## The decision table worth memorising

Not complexities: **contracts**. [Stage 02](02-contract-matrix.md) tests the `null` column of
these.

| Need | Reach for | Not |
| --- | --- | --- |
| Indexed access, append, iterate | `ArrayList` | `LinkedList` |
| Queue or stack | `ArrayDeque` | `LinkedList`; `Stack` (it extends `Vector`, so every call takes a lock) (verified: `Stack.class.getSuperclass()` is `Vector`) |
| Key lookup | `HashMap` | anything hand-rolled |
| Keys in sorted order, or range queries | `TreeMap` / `NavigableMap` | sorting a list repeatedly |
| Insertion order preserved | `LinkedHashMap` | `HashMap` plus a list |
| Enum keys | `EnumMap` | `HashMap` |
| "Smallest first", repeatedly | `PriorityQueue` | re-sorting |
| Shared across threads | `ConcurrentHashMap` | `HashMap` plus `synchronized` |

`LinkedList` has no row. That's a judgment, not a theorem: the worked example shows the one
operation it does win, and `removeIf` covers that. The other thing it offers that `ArrayDeque`
doesn't is that it holds `null` (verified: `ArrayDeque.add(null)` throws
`NullPointerException`). [Stage 02](02-contract-matrix.md) shows why that's a trap rather than a
feature: a `LinkedList` holding `null` returns `null` from `poll()` both when it hands you the
`null` and when it's empty (verified).

## Write the answers here

1. **Your own mid-list insertion numbers.** What ratio did you get, and at what size did
   `LinkedList` stop being merely slower and become unusable? _(fill in)_
2. **`ArrayList` pre-sizing**: your reallocation count, your copy count, how much pre-sizing
   actually saved, and would you bother? _(fill in)_
3. **`PriorityQueue`**: write the test that would catch someone assuming iteration is sorted.
   Make sure your test data isn't one of the inputs whose heap order happens to look sorted.
   _(fill in)_
4. **`CircularList` is 112 lines.** Which JDK type replaces it, how many lines does the
   replacement take, and what did the hand-rolled version provide that the replacement doesn't?
   (There's an honest answer, and it isn't "nothing". [Modernization doc
   10](../modernization/10-sequenced-collections-and-httpclient.md) part one asks whether anything
   uses it.) _(fill in)_
5. **The explain-it version.** _"Why not `LinkedList`?"_ in three sentences, without saying
   "cache", and covering the case where it does win. _(fill in)_

## What got worse

- _(fill this in)_

Prompts: every row in the table is a decision that's expensive to reverse once the type is in a
public signature. Which of them would you hide behind an interface, and when is that
over-engineering? When is a plain array the right answer over any collection? `ArrayList` grows by
1.5x and never shrinks on its own: what does that do to a long-lived list that was briefly huge,
and which method fixes it?
