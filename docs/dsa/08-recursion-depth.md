# 08. Recursion depth, and the exponential nobody measured

**Stage 8 of [the DSA track](INDEX.md)** · new code: `algorithm/Recursion.java`, four TODOs

## Where it lands

Two files, neither with a test:

- **`algorithm/StringRecursiveReversal.java`** — recursion depth **is** the string length, and it
  allocates a fresh substring per frame, so it is also O(n²) in allocation
- **`algorithm/PascalsTriangleRecursive.java:41-46`** — `cellValue` is exponential

## Part one: there is no safe recursion depth

The usual question is "how deep can I recurse?". The answer is that **the question has no stable
answer**, and demonstrating that is TODO 2.

Frames before `StackOverflowError`, measured on this JVM:

| `-Xss` | trivial method | method with 4 `long` parameters |
| --- | --- | --- |
| 256k | 2,617 | 1,031 |
| 512k | 9,200 | 3,426 |
| 1m | 22,839 | 8,957 |
| 4m | 100,462 | 40,998 |
| default | 21,827 | 8,637 |

Two variables, not one: **stack size and frame size**. Four extra `long`s cost you 60% of your
depth. So "recursion is fine up to about 10,000" is not a fact about Java, it is a fact about one
method on one configuration.

**And it is not even stable across runs.** The default-`-Xss` trivial count came out at **13,663**
on one run and **21,827** on another, same machine, no flags changed. Thread stacks are not empty
when your method is called, the JIT inlines differently once warm, and both move the number.

### Which is why TODO 2 is a trap worth walking into

You will write a loop that catches `StackOverflowError` to find the limit. It works. **Then
distrust the number**, for two reasons:

- **Catching `StackOverflowError` is unsound.** It is an `Error`, not an `Exception`, and the JVM
  gives no guarantee about what state you are in — the overflow can land mid-way through anything.
  Code that continues after catching one is working by luck.
- The number is not reproducible, as above.

The test reflects this: it asserts the limit is **greater than 1,000 and finite**, and pins nothing.
A test that pinned 21,827 would be a test that fails on a different machine for no reason.

**The real conclusion: if depth is a function of input size, do not recurse.** TODO 1 is the
iterative version, and the assertion that matters is
`the_iterative_version_handles_an_input_the_recursive_one_cannot` — 500,000 characters. It is not
faster. It is *possible*.

(The JDK has no tail-call elimination, and there is no JEP targeting it. A self-recursive call in
Java is a stack frame, always.)

## Part two: the exponential, and the honest answer

`cellValue(column, row)` recurses twice per call with no memoisation. Calls for a single cell:

| cell | calls | time |
| --- | --- | --- |
| (3, 6) | 29 | — |
| (3, 10) | 89 | — |
| (10, 20) | 335,919 | — |
| (15, 30) | **290,845,349** | ~1 s |
| all of row 30 | **2,147,483,616** | 3,274 ms |

Roughly doubling per row. TODO 3 memoises it and row 30 becomes microseconds.

**TODO 4 is the one with the uncomfortable answer.** The repo's own entry point is
`printPascal(6)`, in `main`. That costs **120 calls and runs instantly.**

So: the code is exponential, and at the only size it is ever used at, **fixing it is worth
nothing.** Both halves of that are true and you should be able to hold them at once. Memoising it
is three lines and harmless; rewriting it iteratively is a change you cannot justify from the
usage. What you *can* justify is knowing the cliff is at about row 25, so that nobody wires this to
a user-supplied number.

This is the counterweight to [stage 07](07-accidental-quadratics.md): there, the honest answer at
n=100 was "it does not matter"; here it is the same answer about an exponential. **Complexity is a
property of the code; cost is a property of the code *and the input*.**

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: a memoised recursion trades stack for heap and the map outlives the call — when is that
the wrong trade? If you cannot catch `StackOverflowError` safely and cannot predict the depth, how
would you make a recursive parser safe against hostile input? `printPascal(6)` costs 120 calls:
what would you write in a code review, given fixing it is unjustified and leaving it is a trap?
Where is the line between "deep recursion" and "recursion whose depth is the input"?
