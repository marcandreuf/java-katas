# 08. Recursion depth, and the exponential nobody measured

**Stage 8 of [the DSA track](INDEX.md)** · new code: `algorithm/Recursion.java`, four TODOs ·
lands on **`algorithm/StringRecursiveReversal.java`** and
**`algorithm/PascalsTriangleRecursive.java`**

> **No safety net:** `StringRecursiveReversal` and `PascalsTriangleRecursive` have no tests. Write
> them first, in a separate commit, asserting what the code does today, bugs included.
> [`../testing-first.md`](../testing-first.md) says how, and why the order matters.

## What this stage is actually about

Five words, defined once:

- a **stack frame** is the memory one method call uses while it runs: its parameters, its locals,
  and where to return to. A recursive call adds one frame, which is freed when the call returns.
- the **thread stack** is the fixed-size block those frames live in. `-Xss` sets its size; the
  default on this JVM is 1 MB (`ThreadStackSize = 1024`, verified).
- **`StackOverflowError`** is what the JVM throws when a call needs a frame and there's no room.
  It's an `Error`, not an `Exception`.
- **tail-call elimination** is a compiler reusing the current frame when a method's last action is
  calling itself. HotSpot doesn't do it: the worked example below is a tail call, and it overflows.
- **memoisation** is caching each result of a pure function by its arguments, so each distinct call
  is computed once.

That gives one rule, and everything below follows from it: **if the recursion depth is a function of
the input size, the input size decides when it crashes, and you can't predict that number.** Recurse
on things whose depth you control, like a balanced tree or a fixed grammar. Use a loop for anything a
user can make longer.

## The worked example: how deep is "too deep"?

Not the exercise. Two methods that do nothing but recurse:

    static void trivial() { depth++; trivial(); }
    static void four(long a, long b, long c, long d) { depth++; four(a+1, b+1, c+1, d+1); }

Frames before `StackOverflowError`, three fresh JVMs per row (verified):

| `-Xss` | `trivial()` | `four(long × 4)` |
| --- | --- | --- |
| 256k | 1,796 · 1,796 · 2,008 | 1,039 · 1,039 · 1,039 |
| 512k | 7,591 · 8,491 · 7,060 | 2,764 · 2,840 · 2,764 |
| 1m | 20,944 · 21,565 · 21,625 | 7,261 · 7,880 · 7,986 |
| 4m | **98,888 · 199,113 · 202,017** | 37,196 · 37,172 · 37,669 |
| default | 20,918 · 21,916 · 20,329 | 7,249 · 7,938 · 6,593 |

Three things to read in it:

- **Two variables, not one: stack size and frame size.** Four `long` parameters cost between
  about 40% (at 256k) and 80% (at 4m) of the depth. "Recursion is fine up to about 10,000" isn't a
  fact about Java. It's a fact about one method on one configuration.
- **The 4m row moved by a factor of two** across three runs, with nothing changed: 98,888, then
  199,113. How deep you get depends on whether the JIT compiled the method before the stack ran
  out, because compiled frames are smaller than interpreted ones, and that timing varies from run
  to run. Take the JIT out of it and the numbers stop moving (verified, three runs each, default
  stack): `-Xint` (interpreter only) gives **10,732 · 6,213** every time, and `-Xcomp` (compile
  before the first call) gives **59,026 · 59,016**. Compiled, the four `long`s cost almost nothing.
- `trivial()` is a tail call: calling itself is the last thing it does. It still overflowed, which
  is the evidence that HotSpot doesn't eliminate tail calls. In Java, a self-recursive call is a
  stack frame, always.

## Part one: `StringRecursiveReversal`, depth is the input

    public String reverseString(String str){
        if(str.isEmpty()) return str;
        return reverseString(str.substring(1)) + str.charAt(0);
    }

One frame per character, and a fresh `substring` per frame, so it's O(n²) in allocation too.
Three facts about it, all verified:

- **Its limit depends on how you look for it.** In the same JVM, doubling the length until it fails
  and then bisecting found **3,839** characters, three runs out of three. Walking upward in steps of
  250 found **19,500**, three out of three. That's a factor of five, from the method you used to
  measure it. Doubling hits the overflow while the method is still interpreted; walking up slowly
  gives the JIT time to compile it first.
- **On a big enough input, it doesn't fail with a stack overflow.** At 500,000 characters, with
  the default heap, it throws **`OutOfMemoryError: Java heap space`**. Every frame still on the
  stack holds its own substring, and together they fill the heap before the stack runs out. With
  `-Xmx16g`, the same call throws `StackOverflowError`. So the error type depends on the heap size.
  At 50,000 and 100,000 characters it's `StackOverflowError`.
- **It breaks characters outside the Basic Multilingual Plane.** `"a😀b"` reverses to the `char`s
  `0062 DE00 D83D 0061`. The emoji's two halves (a *surrogate pair*) come out in the wrong order,
  which isn't a valid string. `new StringBuilder(s).reverse()` gives `0062 D83D DE00 0061` and keeps
  the pair together. `RecursionTest`'s agreement test uses only ASCII, so it can't tell the two
  apart.

`RecursionTest` has six tests and is `@Disabled`. Delete that line to begin (verified). Commits, in
this order:

1. **Characterize `reverseString`**: `""`, `"a"`, `"Java2novice"`, and `"a😀b"`, asserting today's
   broken output for the last one. Green, and it records a bug.
2. **TODO 1, `reverseIteratively`.** Same results with constant stack depth.
   `the_iterative_version_handles_an_input_the_recursive_one_cannot` (500,000 characters) is the
   assertion that matters. It isn't faster. **It's possible.** Then decide about the emoji. Agreeing
   with the recursive version means copying its bug. Getting it right means the two versions
   disagree, and the step 1 assertion flips. That's a behaviour change, so give it its own commit and
   say so in the commit message.
3. **TODO 2, `deepestReverseThatWorks`.** You'll write a loop that catches `StackOverflowError`.
   It works. **Then distrust the number**, for three reasons. Catching `StackOverflowError` is
   unsound: the JVM makes no promise about the state you're in when it's thrown, because it can
   happen partway through anything, including inside a JDK method. The number depends on your search
   strategy, as shown above. And on a large enough input the error is `OutOfMemoryError`, which your
   `catch` won't catch. That's why the test asserts only `> 1000` and `< 10,000,000`, and pins
   nothing.

## Part two: `PascalsTriangleRecursive`, the exponential and the honest answer

`cellValue(column, row)` (lines 41-46) recurses twice per call, with no memoisation. Calls for one
cell, and for whole triangles (verified, three runs):

| what | calls | time |
| --- | --- | --- |
| cell (3, 6) | 29 | — |
| cell (3, 10) | 89 | — |
| cell (10, 20) | 335,919 | — |
| cell (15, 30) | **290,845,349** | 0.44–0.45 s |
| row 30, every cell | **2,147,483,616** | 3.25–3.29 s |
| rows 1–25 | 134,217,349 | 0.23–0.24 s |
| rows 1–27 | 536,870,476 | 0.93–0.95 s |
| rows 1–30 | 4,294,966,767 | 7.0–7.1 s |

The count roughly doubles with each extra row. The first three rows are what
`the_call_count_is_exponential_in_the_row` asserts.

Commits, in this order:

1. **Characterize first.** `cellValue` is private, so pin `printPascal(6)`'s output (capture
   `System.out`). Those are the only values the repo actually uses.
2. **TODO 4, `countCellValueCalls`**, so you can measure before you change anything.
3. **TODO 3, `cellValueMemoized`.** Row 30 drops from 3.3 seconds to **3.5–4.1 ms** in a fresh
   JVM (verified, a `HashMap` memo, three runs). **The old version of this doc said
   microseconds**, and the javadoc in `Recursion.java` still does. There are only a few hundred
   distinct cells, so the milliseconds are a fresh JVM's start-up costs (most likely class loading,
   interpreted code and boxing), not the arithmetic. That's still about 800 times faster.

**Then TODO 4's question, which has the uncomfortable answer.** The repo's only caller is `main`,
which calls `printPascal(6)`. **That's 219 calls** (verified), and it runs instantly. (The old
version of this doc said 120. That's the count for row 6 alone; `printPascal(6)` computes rows 1 to
6.)

So the code is exponential, and at the only size it's ever used at, **fixing it is worth nothing.**
Both halves of that are true, and you should be able to hold both at once. Memoising is a few lines
and harmless. Rewriting it iteratively is a change the usage can't justify. What you *can* justify
is knowing where the cliff is: about a quarter of a second for 25 rows, a second at 27, and doubling
from there. Then nobody wires this to a number a user supplies.

This is the counterweight to [stage 07](07-accidental-quadratics.md). There, the honest answer at
n = 100 was "it doesn't matter", and here it's the same answer about an exponential. **Complexity is
a property of the code. Cost is a property of the code and its input.**

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: a memoised recursion trades stack for heap, and the map outlives the call. When is that the
wrong trade? If you can't catch `StackOverflowError` safely and can't predict the depth, how would
you make a recursive parser safe against hostile input? `printPascal(6)` costs 219 calls: what would
you write in a code review, given that fixing it isn't justified and leaving it is a trap? Your
iterative reverse either copies the emoji bug or disagrees with the code it replaced. Which one
did you choose, and who else would need to know? Where's the line between "deep recursion" and
"recursion whose depth is the input"?
