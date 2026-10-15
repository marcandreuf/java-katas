# 05. Binary search: the contract, and the bug in this repo

**Stage 5 of [the DSA track](INDEX.md)** · new code: `algorithm/SearchContract.java`, three TODOs ·
plus `algorithm/BinarySearchSweepTest.java`, which **fails on purpose**

## Where it lands

**`algorithm/BinarySearch.java`.** Fifty-five lines, written in 2014, eight tests, all green.

It cannot find five of the twelve elements in its own test fixture.

    sequence   = {0,5,13,19,22,41,55,68,72,81,85,98}
    misses: 5 (idx 1), 19 (idx 3), 41 (idx 5), 72 (idx 8), 85 (idx 10)   -> all return -1

    oddSequence       -> 4 of 11 wrong
    negativeSequence  -> 6 of 17 wrong
    new int[0]        -> ArrayIndexOutOfBoundsException

**Do the contract half first** (`SearchContract.java`), then the bug. The contract is what the
broken one is failing to implement, and knowing it makes the bug obvious instead of mysterious.

## Part one: the `Arrays.binarySearch` contract

Two silent failure modes in one method.

### The return value is not an index

    Arrays.binarySearch(sorted, 55)  ->  6      found: the index
    Arrays.binarySearch(sorted, 42)  ->  -7     absent: -(insertion point) - 1

TODO 1 and 2 make you decode both. The encoding exists so one `int` carries both "where is it" and
"where would it go", and `insertionPointFor` is the genuinely useful half: it is how you find the
band a value falls into, or the position to insert at to keep an array sorted.

**`-1` means "absent, insert at 0"**, not "not found". Code that treats a negative return as a
boolean and stops there has thrown away the useful information and will also mis-handle index 0.

### Unsorted input is undefined, and behaves like it is reliable

TODO 3. `{9, 3, 7, 1, 5}`:

    searchUnsorted(7)  ->   2    which is CORRECT, by luck
    searchUnsorted(9)  ->  -6    "absent, insert at 5" -- 9 is at index 0
    searchUnsorted(1)  ->  -1    1 is at index 3
    searchUnsorted(3)  ->  -1    3 is at index 1

No exception, no warning, and the javadoc says only that the result is undefined. **Sometimes
right, sometimes confidently wrong**, and the signature gives you nothing. This is why
[stage 04](04-ordering-contracts.md)'s `SumSearch` question — sort a copy, document the mutation,
or require sorted input — is a real design decision and not a style preference.

## Part two: the bug

`BinarySearchSweepTest` is `@Disabled` and **deleting that line will turn a green build red.**
That is the deliverable. Expect 15 failures and 3 errors.

The test does exactly one thing differently from `BinarySearchTest`: it asks for **every** element
of **every** fixture, via `@ParameterizedTest`. That is all. Forty-nine cases instead of eight.

### Why eight tests missed it

`BinarySearchTest` probes indices **0, 6 and 11** of the twelve-element fixture, plus one genuine
miss, plus the two boundary fixtures. Every index it does not probe is where the bug lives.

**This is the most important thing in this stage, and it is not about binary search.** The tests
were not weak because nobody tried; they were weak because hand-picked cases cluster on the
boundaries people think about — first, middle, last, absent — and a bug that lives *between* those
is invisible. A parameterised sweep over the whole input costs four lines and finds it immediately.

### The cause

Read it before you fix it. The bound convention is mixed:

    :17   high = sequence.length;     // EXCLUSIVE upper bound
    :52   high = mid - 1;             // the INCLUSIVE convention's update
    :33   return low < high || isFirstElement();   // the EXCLUSIVE convention's test

With an exclusive `high` the update must be `high = mid`. Using `mid - 1` discards index `mid - 1`
without ever testing it, so values at those positions are unreachable.

And `:36-38`:

    private boolean isFirstElement() { return low == 0 && high == 0; }

A special case bolted on to make the single-element fixture pass — which is also why the empty
array throws instead of returning `-1`. **A special case that exists to satisfy a test is a signal
the invariant is wrong**, and that is the line to write down.

### The fix

Pick one convention and apply it in all three places. Both work:

| | init | loop test | update |
| --- | --- | --- | --- |
| exclusive `high` | `length` | `low < high` | `high = mid` |
| inclusive `high` | `length - 1` | `low <= high` | `high = mid - 1` |

With either, `isFirstElement()` deletes itself and the empty array stops being special. Note that
`low + (high - low) / 2` on line 40 is already correct — it is the overflow-safe form, and whoever
wrote this knew that detail while getting the bounds wrong. [Stage 09](INDEX.md) is about the
version that gets it wrong.

Also: `low`, `high` and `mid` are **mutable instance fields** (`:11-13`), so a single
`BinarySearch` instance is not safe to reuse across threads and barely safe to reuse at all. The
sweep test constructs a fresh one per case deliberately. Making them locals is part of the fix.

**Commit the failing test and the fix separately.** The red commit is the evidence that the bug was
real, and it is the only artefact that proves the eight green tests were not enough.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: a parameterised sweep found this in four lines — why is it not the default for every
algorithm test, and what does it cost when the input space is large? `FairWorkloadTest` has been
`@Disabled` since 2014 with the cause never found ([doc 09](../modernization/09-streams-and-optional.md)
owns it) — does what you just learned change how you would approach it? Property-based testing
("for all sorted arrays and all elements, search finds it") would have caught this too: what would
you have to give up to adopt it? The eight original tests were not useless — what did they actually
establish?
