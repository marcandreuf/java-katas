# 09. Integer overflow is an algorithm bug

**Stage 9 of [the DSA track](INDEX.md)** · new code: `algorithm/Overflow.java`, four TODOs ·
test: `algorithm/OverflowTest.java`, `@Disabled`

## What this stage is actually about

Four words, defined once:

- **overflow** is a result too big for its type. Java's `int` holds −2,147,483,648 to
  2,147,483,647; one more than the top wraps round to the bottom, **with no exception and no
  warning**.
- **wrapping** is what Java does instead: it keeps the low 32 bits and throws the rest away.
  `Integer.MAX_VALUE + 1` is `Integer.MIN_VALUE`.
- **`Math.addExact` / `multiplyExact`** (Java 8) do the same arithmetic, but throw
  `ArithmeticException` instead of wrapping.
- **widening** is doing the arithmetic in a bigger type (`long`, or `BigInteger`), so the cliff is
  further away.

That gives one rule, and everything below follows from it: **the arithmetic is correct and the
type is too small, so you can't find it by reading the formula.** You find it by asking where a
value grows, and by running the code on inputs big enough to reach the cliff.

## The worked example

Not this stage's code: the average of two `int`s, which every binary search needs.

    int mid = (low + high) / 2;              // the textbook line
    int mid = low + (high - low) / 2;        // the safe one

With `low = 1_500_000_000` and `high = 2_000_000_000` (verified):

    (low + high) / 2         -> -397483648
    low + (high - low) / 2   ->  1750000000

`low + high` is 3.5 billion, which wraps to a negative number before the division ever runs. The
second form never builds a value bigger than `high`. This exact bug sat in the JDK's own
`Arrays.binarySearch` until 2006, "for nine years or so" by its author's account (Joshua Bloch, Google
Research blog, 2006; not something this repo can verify). `BinarySearch.java:40` in this repo already
uses the safe form (verified), which [stage 05](05-binary-search.md) notes is one of the few things
that file gets right.

Note what made it findable: not reading `(low + high) / 2`, which looks fine, but asking **"how big
can `low + high` get?"** That's the question to carry into the exercise.

## Where it lands

Two files from 2014. Neither has a test of its own (verified: no `NumberReverseTest`, no
`DuplicateNumberTest`). Only the `@Disabled` `OverflowTest` mentions them.

- **`algorithm/NumberReverse.java:13`**: `reverse = (reverse*10)+(number%10)`
- **`algorithm/DuplicateNumber.java`**: `getSum` (lines 24-31) adds the list into an `int`, and
  `findDuplicateNumber` subtracts a Gauss total, `n(n+1)/2`, from it (lines 12-18)

### What `NumberReverse` does today (all verified)

| input | returns | correct answer |
| --- | --- | --- |
| `17868` | 86871 | 86871 |
| `2147483647` | **-1126087180** | 7463847412 |
| `1534236469` | **1056389759** | 9646324351 |
| `1463847412` | 2147483641 | 2147483641, which only just fits |
| `-123` | -321 | -321 |
| `1000` | **1** | 1, but `reverse(1)` is 1, not 1000 |

Rows two and three are the overflow. Row two is **negative**, which at least looks wrong. Row three
is **positive, ten digits, and entirely plausible**. That's the one that gets into a report and
stays there. Row six isn't overflow, but you'll need it for TODO 1 and 2: reversal throws away
trailing zeros, so it never round-trips anyway.

### What `DuplicateNumber` does today (all verified)

The Gauss trick: a list of `1..n` plus one duplicate sums to `n(n+1)/2 + duplicate`, so subtract
and what's left is the duplicate. For `[1, 2, 3, 3, 4, 5]` it returns **3**.

    getSum([MAX_VALUE, MAX_VALUE, 1])     -> -1
    findDuplicateNumber([5, 5, 5])        -> 12

The first is plain wrapping. The second isn't overflow: `[5, 5, 5]` isn't `1..n` plus a duplicate,
nothing checks that, and the answer is twelve. Not an index, not an element, not an error.

**And the one the old version of this doc missed.** Feed it *valid* input, `1..n` with 7 added as
the duplicate, and grow `n` (verified):

| `n` | returns | does the true sum fit in an `int`? |
| --- | --- | --- |
| 1,000 | 7 | yes |
| 46,340 | 7 | yes |
| **46,341** | **-2147483641** | yes |
| 65,535 | -2147483641 | yes |
| 65,536 | -2147483641 | **no** |
| 100,000 | **7** | no |
| 1,000,000 | -2147483641 | no |

Sampling every 997th size up to 200,000: **90 of 201 wrong** (verified). Three things to notice
before you touch any code:

1. **It breaks at 46,341, while the sum still fits.** `getSum` doesn't overflow until 65,536. So
   the old version of this doc, which put the bug in `getSum`, had the wrong line. Find the right
   one.
2. **Past 65,536 the sum wraps, and that alone does no harm.** Wrapping is arithmetic modulo 2³²,
   and a sum minus a total is still right modulo 2³² if the true answer fits. Something else is
   breaking it.
3. **Sometimes it's right again** (100,000). An overflow bug that passes some large tests is the
   same "sometimes right, confidently wrong" shape as [stage 05](05-binary-search.md)'s
   `binarySearch` on unsorted input.

Work out why `-2147483641` is exactly `7 - 2³¹`. That number is a hint.

## The commits

### 1. Characterize, in a test that isn't `@Disabled`

`OverflowTest` already pins some of today's behaviour: its first test, and the first assertion in
two others. Delete its `@Disabled` now and you get **1 green, 7 red** (verified), because the
TODOs throw `UnsupportedOperationException`. So the characterization goes somewhere that can be
green today: a `NumberReverseTest` and a `DuplicateNumberTest`. Assert the tables above as they
are, `-1126087180`, `12`, and **one valid list, `1..46341` plus a duplicate 7, that returns `-2147483641`**. That
last assertion is the one nothing in the repo has.

### 2. Write `Overflow`, TODO by TODO

Delete `OverflowTest`'s `@Disabled`. Each TODO turns some of the red tests green, so give each one
its own commit:

- **TODO 1, `reverseExact`**: refuse to wrap, with `Math.multiplyExact` and `Math.addExact`. Note
  what you did **not** have to write: no bounds check, no magic constant, no comparison against
  `MAX_VALUE / 10`.
- **TODO 2, `reverseWidened`**: the same reversal, returning a `long`. Every reversed `int` fits in
  a `long`, because ten digits is at most 9,999,999,999.
- **TODO 3, `sumExact`**: `getSum` with `Math.addExact`, keeping the `int` return type.
- **TODO 4, `findDuplicateChecked`**: the Gauss trick, with its precondition enforced. Decide what
  "enforced" means: duplicates exist? exactly one? every value in `1..n`? Each check costs
  something, and the cheapest correct one is the interesting design.

**TODO 1 against TODO 2 is a domain question, not a numeric one.** Is "the reverse of 2147483647"
a value that should *fail*, or one that should *widen*? Write down which one you'd pick for this
method and why. That sentence is the deliverable. And before you argue "it's an identifier, so it
must round-trip", look at row six of the table.

**TODO 3 has a trap, and point 2 above is it.** `sumExact` throws on valid input from n = 65,536
up, where a wrapping sum, paired with a correctly computed Gauss total, still gives the right
duplicate. So if `findDuplicateChecked` uses it, it refuses inputs it could have answered.
Decide whether that's acceptable, and say so in the commit message.
(Verified at n = 65,536, 100,000 and 1,000,000.)

### 3. Fix the 2014 files, as their own commit

Make `NumberReverse` and `DuplicateNumber` correct: delegate to `Overflow`, or fix them in place.
The step 1 assertions that record wrong answers **flip in this commit**, and that's the evidence.
`OverflowTest`'s first test asserts `-1126087180` too, so it flips as well. Change it in the same
commit and say why.

## The general shape

Three questions to carry out of this stage:

1. **Where does this accumulate?** Sums, products, running totals, anything `*10` in a loop, and
   **any formula with a multiply before a divide**.
2. **Could index or size arithmetic overflow?** `(low + high) / 2` is the famous one.
3. **Would I rather fail or widen?** Answer it once per site, deliberately.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: `Math.addExact` throws, so a method that could not fail now can. What does that do to its
callers and its signature? `long` only moves the cliff. When do you reach for `BigInteger`, and
what does it cost? Java has no unsigned `int` and no overflow trap. Would a flag that trapped on
overflow be one you'd turn on in CI? `sumExact` refuses inputs the wrapping version got right, so
which one is "safer"? If the Gauss trick needs an unchecked precondition to be fast, is it still
the right algorithm, or is a `HashSet` the honest answer?
