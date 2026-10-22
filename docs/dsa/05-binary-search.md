# 05. Binary search: the contract, and the bug in this repo

**Stage 5 of [the DSA track](INDEX.md)** · new code: `algorithm/SearchContract.java`, three TODOs ·
plus `algorithm/BinarySearchSweepTest.java`, which **fails on purpose** · lands on
**`algorithm/BinarySearch.java`**

## What this stage is actually about

Five words, defined once:

- the **search range** is the part of the array the answer can still be in, written as two
  indices, `low` and `high`.
- an **inclusive** bound is a valid index that is still a candidate. An **exclusive** bound is one
  past the last candidate. `[low, high)` has an exclusive `high`; `[low, high]` has an inclusive one.
- the **loop invariant** is the sentence that is true before every iteration: "if the target is in
  the array, it's in the search range". Every line of a binary search exists to keep it true.
- the **insertion point** is the index where a value *would* go to keep the array sorted, whether
  or not it is there. `Arrays.binarySearch` returns `-(insertion point) - 1` for an absent value.
- a **sweep** is a test that asks for every element of a fixture instead of a few chosen ones.

That gives one rule, and everything below follows from it: **pick one bound convention and keep
the invariant true on every line: the initial value, the loop test and both updates.** Mix two
conventions and the search doesn't crash. It just never looks at some indices, and returns `-1`
for values that are right there.

## The worked example: a lower bound, and the off-by-one that hides in it

Not the exercise: this finds the *first index whose value is ≥ target*, which `BinarySearch`
doesn't do. The invariant is written down first, and each line is checked against it:

    static int lowerBound(int[] a, int target) {
        int lo = 0, hi = a.length;              // the answer is always in [lo, hi]
        while (lo < hi) {
            int mid = lo + (hi - lo) / 2;
            if (a[mid] < target) lo = mid + 1;  // mid is too small, and so is everything left of it
            else hi = mid;                       // mid might BE the answer: keep it in range
        }
        return lo;
    }

Now change `hi = mid` to `hi = mid - 1`, which reads just as plausibly. On `{2, 4, 4, 7, 9}`,
compared against a linear scan for every target from 0 to 11 (verified):

    broken wrong for 2 of 12 targets 0..11
    t=8 broken=3 right=4
    t=9 broken=3 right=4

`hi = mid - 1` throws away `mid`, which the comment says might be the answer. Ten of the twelve
targets still come out right, and that includes 1, 2, 4, 5 and 10, the ones you'd probably have
picked to test it. **The only way the twelve-target loop found it was by not choosing the targets.**
That's the sweep, and it's the whole method of this stage. (And `lowerBound(new int[0], 5)` returns
`0` with no special case (verified): when the invariant is right, the empty array needs no special
handling.)

## Part one: the `Arrays.binarySearch` contract

Do this half first: it's the contract the broken search is failing to implement.
`SearchContractTest` has six tests and is `@Disabled`; delete that line to begin (verified).

### The return value is not an index

    int[] sorted = {0, 5, 13, 19, 22, 41, 55, 68, 72, 81, 85, 98};
    Arrays.binarySearch(sorted, 55)   ->   6    found: the index
    Arrays.binarySearch(sorted, 42)   ->  -7    absent: insertion point 6, encoded as -6 - 1
    Arrays.binarySearch(sorted, -1)   ->  -1    absent: insert at 0
    Arrays.binarySearch(sorted, 99)   -> -13    absent: insert at 12, past the end

(All verified.) TODO 1 and TODO 2 make you decode both cases. The encoding exists so that one `int`
carries both "where is it" and "where would it go". `insertionPointFor` is the useful half: it's
how you find which band a value falls into, or where to insert to keep an array sorted.

**`-1` means "absent, insert at 0"**, not "not found". Code that treats any negative result as a
plain "no" has thrown away the useful half of the answer.

### Unsorted input is undefined, and it looks reliable

TODO 3. On `{9, 3, 7, 1, 5}` (verified):

    searchUnsorted(7)  ->   2    CORRECT, by luck
    searchUnsorted(9)  ->  -6    "absent, insert at 5": 9 is at index 0
    searchUnsorted(1)  ->  -1    1 is at index 3
    searchUnsorted(3)  ->  -1    3 is at index 1
    searchUnsorted(5)  ->  -1    5 is at index 4

No exception and no warning. The javadoc says only that the result is undefined. **Sometimes right,
sometimes confidently wrong**, and nothing in the signature warns you. That's why
[stage 04](04-ordering-contracts.md)'s `SumSearch` question (sort a copy, document the mutation, or
require sorted input) is a design decision and not a matter of style.

## Part two: `BinarySearch`, a bug that eight green tests don't see

`BinarySearch.java` is 55 lines from 2014. **Note its contract first, because it isn't the JDK's:**
`search` returns a **one-based** position (`return mid + oneBased`, line 22), so finding `sequence[6]`
returns 7. `BinarySearchTest` asserts exactly that, and so does the sweep. It isn't the bug, but if
you compare it with `Arrays.binarySearch` you'll be off by one everywhere.

`BinarySearchTest` has eight tests, all green (verified). They ask for indices 0, 6 and 11 of
`sequence`, index 0 of `oddSequence`, indices 0 and 6 of `negativeSequence`, both ends of the two
tiny fixtures, and one absent value. **Every index in between is untested**, and that's where the
bug is.

`BinarySearchSweepTest` uses the same fixtures and asks for every element. That's its only
difference: 49 cases instead of 8. It is `@Disabled`. Delete that line in your copy and run it
(verified):

    Tests run: 49, Failures: 15, Errors: 3

    sequence          misses 5, 19, 41, 72, 85          (5 of 12)
    oddSequence       misses 5, 22, 68, 81              (4 of 11)
    negativeSequence  misses -30, -4, 13, 22, 68, 81    (6 of 17)
    new int[0]        ArrayIndexOutOfBoundsException    (the 3 errors)

Every miss returns `-1`. Nothing crashes on a non-empty array, and nothing warns.

**This is the most important thing in this stage, and it isn't about binary search.** The tests
weren't weak because nobody tried. They were weak because hand-picked cases cluster on the
boundaries people think about (first, middle, last, absent), and a bug that lives *between* those
is invisible. A parameterised sweep over the whole fixture is about ten lines, and it finds the bug
straight away.

Commits, in this order:

1. **Delete the `@Disabled` and commit the red build.** That commit is the evidence. It's the only
   thing that proves the eight green tests weren't enough, so it goes in on its own, before any fix.
   (Your build is red between this commit and the next. That's intended, and it's what the commit
   message should say.)
2. **Diagnose, then fix**, in one commit that turns the sweep green. Use the rule above. Write down
   which convention `high` starts in, which one each update assumes, and which one the loop test
   assumes. Then choose one and make all of them agree. Check yourself on the worked example: it
   was one line, and the cause here is the same kind.
3. **Refactor, separately.** `low`, `high` and `mid` are instance fields (lines 11-13). They're
   reset at the top of every `search`, so reusing one instance sequentially is fine, but two threads
   sharing an instance would overwrite each other's bounds. Make them locals, with everything green
   before and after.

---

**Try steps 1 and 2 before reading on.** The rest of this section is the diagnosis.

---

### The diagnosis

The bounds are mixed:

    :17   high = sequence.length;                 // starts EXCLUSIVE
    :52   high = mid - 1;                         // an INCLUSIVE convention's update
    :33   return low < high || isFirstElement();  // an EXCLUSIVE convention's loop test

With an exclusive `high`, setting `high = mid - 1` discards index `mid - 1` without ever comparing
it, which is the worked example's bug.

Then there's `isFirstElement()` (lines 36-38): `low == 0 && high == 0`. **The old version of this
doc said it was bolted on to make the single-element fixture pass. It isn't.** `{0}` is found
without it. Delete it in a scratch copy and the test that fails is
**`testNegativeSeqLowerBoundTargetBinarySearch`**, the search for `-52` (verified). That's the one
case where the skipped index is index 0 and `high` lands on 0. It's also the one test with a
`System.out.println("index neg: "+index)` left in it, which suggests someone debugged that case
and patched it. It's also why the empty array throws: with `low == high == 0`, the patch lets the
loop run and read `sequence[0]`. **A special case that exists to make one test pass is a sign that
the invariant is wrong**, and that's the line to write down.

Removing only the special case (verified) gets rid of the 3 errors and turns two more finds into
misses: 17 failures. It's a symptom, not the cause.

`low + (high - low) / 2` on line 40 is already correct: it's the form that can't overflow. Whoever
wrote this knew that detail and still got the bounds wrong. [Stage 09](09-integer-overflow.md) is
about the version that gets it wrong.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: a sweep found this in about ten lines. Why isn't it the default for every algorithm test,
and what does it cost when the input space is too big to sweep? `BinarySearch` returns a one-based
position: which callers does that break, and where would you write the contract down? Property-based
testing ("for every sorted array and every element in it, search finds it") would have caught this
too. What would you have to give up to adopt it? The eight original tests weren't useless: what did
they actually establish? And `FairWorkloadTest` has had a test `@Disabled` since 2014 with the cause
never found ([stage 06](06-answer-space-search.md)). Does what you just learned change how you'd
approach it?
