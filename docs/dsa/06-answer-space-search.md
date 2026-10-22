# 06. Binary search over an answer space

**Stage 6 of [the DSA track](INDEX.md)** · new code: `algorithm/WorkLoad.java`, four TODOs ·
lands on **`algorithm/FairWorkLoad.java`**, shared with
[modernization doc 09](../modernization/09-streams-and-optional.md)

## What this stage is actually about

Five words, defined once:

- the **answer space** is the range of values the answer could possibly take, which is not the
  input. For "smallest busiest-worker load", it runs from one cabinet's worth to everything.
- a **predicate** is a yes/no question about one candidate answer: "can 6 workers keep every load
  at or below 950?"
- a predicate is **monotonic** if, once it's true for some value, it's true for every larger value
  (or, the other way round, false for every larger one). There's one switch-over point, and that's
  what you're looking for.
- a **feasibility check** is a predicate that says whether a candidate answer can be achieved at
  all, not just whether it's the best one.
- a **greedy** pass makes the locally obvious choice at each step (put this cabinet on the current
  worker if it fits, otherwise start a new worker) and never goes back.

That gives one rule, and everything below follows from it: **you may bisect any range whose
predicate is monotonic, sorted array or not, and you may bisect nothing else.** Binary search over
an array is the special case where the predicate is "is `a[i] >= target`?". Answer-space search
uses the same loop, and asks a different question at each midpoint.

## The worked example: the integer square root

Not the exercise. The question is "what's the largest `x` with `x * x <= n`?", and there's no array
anywhere. The answers are `0..n`, and the predicate `x * x <= n` is true up to some point and false
after it:

    static int isqrt(int n) {
        long lo = 0, hi = n;                 // the answer is in [lo, hi]
        while (lo < hi) {
            long mid = lo + (hi - lo + 1) / 2; // round UP, or "lo = mid" can loop forever
            if (mid * mid <= n) lo = mid;      // mid works, so the answer is mid or larger
            else hi = mid - 1;                 // mid fails, and so does everything above it
        }
        return (int) lo;
    }

`isqrt(10) = 3`, `isqrt(16) = 4`, `isqrt(99) = 9`, `isqrt(Integer.MAX_VALUE) = 46340` (all
verified). Three things in it are worth carrying away:

- **Round the midpoint up when the "works" branch is `lo = mid`.** With the usual
  `lo + (hi - lo) / 2`, `n = 10` reaches `lo = 3, hi = 4`, `mid` is 3 again, and it never ends:
  verified, still looping after 1,000 iterations.
- **The bounds are `long` for a reason.** The first version of this example used `int`, and
  `hi - lo + 1` overflowed when `hi` was `Integer.MAX_VALUE`, so `isqrt(Integer.MAX_VALUE)` returned
  **0** (verified). [Stage 09](09-integer-overflow.md) is that bug.
- **Monotonicity is what makes it legal.** Bisect on a predicate that isn't monotonic, for example
  "the smallest x in `[0, 100]` that's a multiple of 7 and greater than 10", and you get **100**,
  which doesn't even pass the predicate (verified; the right answer is 14). No exception: just a
  confident wrong number.

## Where it lands

`FairWorkLoad.java` is the TopCoder "FairWorkload" problem: cabinets of folders in a line, each
worker takes a **contiguous** run of them, and you minimise the busiest worker's load. It's an
answer-space search, and the only piece of non-obvious algorithm in this repo
([`research.md`](research.md) rates the technique **load-bearing** for that reason). It's also
wrong, and `FairWorkloadTest.testMaxNumPerSixWorkers` has been `@Disabled` since 2014 with the
cause never found (verified: eight tests, that one disabled).

    low  = the largest single cabinet    // no cap below this can ever work
    high = the sum of all cabinets       // one worker takes everything
    bisect on: "can `workers` workers keep every load at or below `cap`?"

Three things make it legal, and only the third is about code:

1. **The answer is in `[low, high]`.** Both bounds are facts about the input, not guesses.
2. **The predicate is monotonic**: if a cap works, every larger cap works. **That's the whole
   licence to bisect.** `WorkLoadTest.the_predicate_is_monotonic` asserts it directly, and that's
   worth more than any of the other tests.
3. The predicate is cheap: one greedy pass. (The greedy pass is only right *because* the runs are
   contiguous. See the prompts at the end.)

Do [stage 05](05-binary-search.md) first. This is the same tool pointed somewhere surprising.

## Part one: `WorkLoad`, new code, test first

`WorkLoadTest` has six tests and is `@Disabled`. Delete that line to begin (verified).

1. **TODO 1, `workersNeededFor`.** The feasibility check. Note the early return the javadoc asks for:
   if any single cabinet is bigger than `cap`, return `Integer.MAX_VALUE`, because no number of
   workers can split one cabinet. **`FairWorkLoad` has nothing like that line.** Keep it in mind.
2. **TODO 2, `isAchievable`.** One line. **Spend your time on the sentence, not the code**: why is
   it monotonic? Being able to say "I binary searched the answer space because the feasibility
   predicate is monotonic in the cap" is the whole of this stage. Look out for the shape it fits:
   *minimise the maximum*, *maximise the minimum*, "smallest capacity such that…". You'll meet it
   again, and the loop doesn't change.
3. **TODO 3, `smallestAchievableLoad`.** The bisection. Decide whether the midpoint rounds down or
   up, using the worked example's rule.

`the_answer_is_always_achievable_and_minimal` is the test `FairWorkloadTest` never had. The old
tests only compare the result with a hand-computed number:

    assertTrue(result == expectedResult);

**They never ask whether the answer can actually be achieved.** The new test checks both halves:
the answer works, and one less doesn't.

## Part two: TODO 4, compare with `FairWorkLoad`

Run both on the disabled test's fixture, `{950, 650, 250, 250, 350, 100, 650, 150, 150, 700}` with 6
workers (verified):

| | Result |
| --- | --- |
| `WorkLoad.smallestAchievableLoad` (any correct version) | **950**, and the greedy pass needs only 5 workers |
| `FairWorkLoad.getMostWork` | **900**, and 900 is **impossible** |

900 isn't merely suboptimal. The 950 cabinet can't be split, so *no* number of workers gets the
maximum load down to 900. The function returned an answer outside the feasible set. **A binary
search whose bounds are wrong doesn't fail: it returns a confident wrong answer**, the same lesson
as the worked example's non-monotonic predicate.

Commits, in this order. [Doc 09](../modernization/09-streams-and-optional.md) part three describes
the same three, so do them once, from whichever doc you're in:

1. **Diagnose, without changing the code.** Write the cause down in two or three sentences: which
   line, and why the *other seven* tests pass anyway. Two hints. First, run `getMostWork` on that
   fixture and **read the trace it prints**: lines 52-55 print the split it chose, and it shows you
   the answer. Second, the fixture is the only one whose answer is exactly its largest cabinet.
2. **Fix it as its own commit**: the smallest change, still a loop, with the `@Disabled` deleted in
   the same commit, so the diff carries the evidence.
3. **Only then** refactor (doc 09 converts the helpers to streams), with every test green before and
   after.

Don't fix it by rewriting. Doc 09 explains why: rewriting one of the helpers as a stream makes
the test pass **by accident** (verified: 900 becomes 950), and then nobody knows what the bug was.

---

**Try step 1 before reading on.** The rest of this doc is the diagnosis.

---

## The diagnosis

Look at `FairWorkLoad.java:61-65`:

    private int max(int[] sequence){
        int max = sequence[0];
        for(int value:sequence) if(value>0) max = value;
        return max;
    }

It assigns instead of comparing, so it returns **the last positive element**, not the largest. For
this fixture that's 700 instead of 950, so `low` starts below the true lower bound. Replace it with a
real maximum (in a scratch copy) and `getMostWork` returns **950** (verified).

But a wrong `low` only matters because of the second half: **the greedy pass has no feasibility
check.** The trace at cap 900 (verified) is:

    For median: 900
    | 950| 650 250| 250 350 100| 650 150| 150 700

Read the first `|`. The 950 didn't fit on the empty first worker, so the loop started a second
worker, set `currentLoad = 950`, which is over the cap, and carried on. Worker 1 is **empty** and
worker 2 is **over the cap**, and the count of six workers was accepted as fine. That's exactly the
early return `WorkLoad`'s TODO 1 asks for, and the commented-out "strange case" at lines 33-42 is
someone finding this symptom in 2014 and backing away from it. Either the right `max()` or the
missing check would have stopped the search going below 950: with the buggy `max()` and only the
check added, the result is also **950** (verified). The bug needs both to be wrong.

**Why it hid for twelve years. The old version of this doc got this wrong.** It said every other
fixture has its largest element last, so `max()` happens to be right. That isn't true:
`testLargerCabinets` has 980 in the middle and 782 last, and it passes. Verified on all eight
fixtures, with the buggy `max()` and with a correct one:

| Fixture | workers | last | largest | answer |
| --- | --- | --- | --- | --- |
| `{10 … 90}` | 3 | 90 | 90 | 170 |
| `{10 … 90}` | 5 | 90 | 90 | 110 |
| `{568, 712, … 980, 23, 782}` | 4 | 782 | **980** | 1785 |
| `{1 × 14, 1000}` | 2 | 1000 | 1000 | 1000 |
| `{50 × 7}` | 2 | 50 | 50 | 200 |
| `{1, 1, 1, 1, 100}` | 5 | 100 | 100 | 100 |
| `{950, … 700}` | 6 | 700 | **950** | **950** |
| `{1, 1}` | 2 | 1 | 1 | 1 |

The real reason: **a wrong lower bound only matters when the true answer is the lower bound.** In
`testLargerCabinets` the answer is 1785, far above both 782 and 980, so the search never gets near
the bottom of the range. The disabled fixture is the only one where the best you can do is give
the biggest cabinet a worker of its own. That makes the answer exactly 950, and the wrong bound
lets the search go below it.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: the greedy pass in TODO 1 is only correct because workers take *contiguous* runs. What
breaks if they can take any subset, and what problem does that become? Could the property "the
answer is achievable and one less isn't" have replaced all eight of `FairWorkloadTest`'s
hand-computed cases, and what would you lose? `max()` is three lines and was wrong for twelve
years: what review or tool would have caught it, and why did neither? The trace in `getMostWork`
printed the bug on every run of the disabled test. Why didn't anyone see it?
