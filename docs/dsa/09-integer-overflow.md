# 09. Integer overflow is an algorithm bug

**Stage 9 of [the DSA track](INDEX.md)** · new code: `algorithm/Overflow.java`, four TODOs

## Where it lands

Two files, neither with a test, both silently wrong on large input:

- **`algorithm/NumberReverse.java:13`** — `reverse = (reverse*10)+(number%10)`
- **`algorithm/DuplicateNumber.java:24-31`** — `getSum` accumulates into an `int`, and
  `findDuplicateNumber` subtracts a Gauss total from it

This is not a numerics stage. **The arithmetic is correct and the type is too small**, which makes
it an algorithm bug that no amount of staring at the formula will find.

## What the repo does today

Pinned in `OverflowTest` before you change anything, so you see the behaviour rather than read
about it:

| input | returns | correct answer |
| --- | --- | --- |
| `reverseNumber(17868)` | 86871 | 86871 |
| `reverseNumber(2147483647)` | **-1126087180** | 7463847412 |
| `reverseNumber(1534236469)` | **1056389759** | 9646324351 |

Look at the second and third rows. The first is **negative**, which at least looks wrong. The
second is **positive, nine digits, and entirely plausible** — it is the one that gets into a report
and stays there. `1534236469` is a well-known LeetCode overflow case for exactly this reason.

`getSum([MAX_VALUE, MAX_VALUE, 1])` returns **-1**.

## The exercise

### TODO 1 and 2: refuse, or widen

Two fixes, and they are not interchangeable:

    Math.addExact(Math.multiplyExact(reverse, 10), number % 10)   // TODO 1: throws
    long reverse = ...                                            // TODO 2: just works

`Math.*Exact` throws `ArithmeticException` instead of wrapping. Note what you did **not** have to
write: no bounds check, no magic constant, no comparison against `MAX_VALUE / 10`. The JDK has had
these since Java 8 and they are the single highest-value thing in this stage.

**The choice between them is a domain question, not a numeric one.** Is "the reverse of
2147483647" a value that should *fail*, or a value that should *widen*? If the reversed number is
an identifier, failing is right — a reversed id that does not round-trip is a corruption. If it is
a computed quantity, widening is right. Write down which one you picked for *this* method and why,
because that sentence is the deliverable.

### TODO 4: the precondition nobody wrote

`DuplicateNumber` uses the Gauss trick — sum the list, subtract `n(n+1)/2`, what remains is the
duplicate. That is genuinely clever and it rests on an unstated assumption: the list is `1..n-1`
with exactly one duplicate.

Nothing checks it:

    findDuplicateNumber([5, 5, 5])  ->  12

Twelve. Not an index, not an element, not an error. **A clever algorithm with an unchecked
precondition is a function that returns plausible nonsense**, and this is the second time this
track has shown you that shape — [stage 05](05-binary-search.md)'s `binarySearch` on unsorted input
is the same failure with the same silence.

Decide what to throw, and write down why an exception beats a wrong answer here. The short version:
a thrown exception is a bug report with a stack trace; a wrong answer is a bug report in eighteen
months with no stack trace.

## The general shape

Three questions to carry out of this stage:

1. **Where does this accumulate?** Sums, products, running totals and anything `*10` in a loop.
2. **Could an index or size arithmetic overflow?** `(low + high) / 2` is the famous one —
   `BinarySearch.java:40` already uses the safe `low + (high - low) / 2` form, which [stage
   05](05-binary-search.md) notes is the one thing that file gets right.
3. **Would I rather fail or widen?** Answer it once per site, deliberately.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: `Math.addExact` throws, which means a method that could not fail now can — what does that
do to its callers and its signature? `long` only moves the cliff; when do you reach for
`BigInteger`, and what does it cost? Java has no unsigned `int` and no overflow trap — would
`-Xcheck:overflow`, if it existed, be a flag you would enable in CI? If the Gauss trick needs an
unchecked precondition to be fast, is it still the right algorithm?
