# 06. Binary search over an answer space

**Stage 6 of [the DSA track](INDEX.md)** · new code: `algorithm/WorkLoad.java`, four TODOs ·
lands on **`algorithm/FairWorkLoad.java`**

## Where it lands

`FairWorkLoad.java` is the one piece of genuinely non-obvious algorithm in this repo, and
[`research.md`](research.md) rates the technique **load-bearing** for that reason. It is also
wrong, and the test that proves it has been `@Disabled` since 2014 with the cause never found.

Do [stage 05](05-binary-search.md) first. This is the same tool pointed somewhere surprising.

## The technique

Binary search over an array needs the array sorted. **Binary search over an answer space needs
nothing sorted at all** — the input can be in any order, and the answer is usually not even in the
input.

The problem: cabinets of folders, each worker takes a *contiguous* run of them, minimise the
busiest worker's load.

    low  = the largest single cabinet    // no cap below this can ever work
    high = the sum of all cabinets       // one worker takes everything
    bisect on: "can `workers` workers keep every load at or below `cap`?"

Three things make it legal, and only the third is about code:

1. **The answer lies in `[low, high]`.** Both bounds are facts about the input, not guesses.
2. **The predicate is monotonic**: if a cap works, every larger cap works. **This is the entire
   licence to bisect** — without monotonicity there is no "half" to discard, and the technique is
   simply invalid. `WorkLoadTest.the_predicate_is_monotonic` asserts it directly, which is worth
   more than any of the other tests.
3. The predicate is cheap: one greedy pass.

**TODO 2 is where to spend your time.** Being able to say "I binary searched the answer space
because the feasibility predicate is monotonic in the cap" is the whole of this stage. Recognising
that shape — *minimise the maximum*, *maximise the minimum*, "smallest capacity such that…" — is a
technique you will reuse; the code never changes.

## The piece `FairWorkLoad` is missing

**TODO 1 is a feasibility predicate, and `FairWorkLoad` has nothing like it.** Note the early
return:

    if (folders > cap) return Integer.MAX_VALUE;   // no number of workers can split one cabinet

That single line is why `WorkLoadTest` can assert something `FairWorkloadTest` never could. The old
tests only ever compared the returned number against a hand-computed expectation:

    assertTrue(result == expectedResult);

**They never asked whether the answer was achievable.** `the_answer_is_always_achievable_and_minimal`
is that missing test, and it checks both halves: the answer works, and one less does not.

## The bug

**TODO 4.** Run `WorkLoad` and `FairWorkLoad` on the fixture from the `@Disabled` test:

    {950, 650, 250, 250, 350, 100, 650, 150, 150, 700}, 6 workers

| | Result |
| --- | --- |
| `WorkLoad.smallestAchievableLoad` | **950** — needs 5 workers, so it fits |
| `FairWorkLoad.getMostWork` | **900** — and 900 is **impossible** |

900 is not merely suboptimal. The 950-folder cabinet cannot be split, so *no* number of workers
achieves a maximum load of 900. The function returned an answer outside the feasible set.

**Now look at `FairWorkLoad.java:61-65`:**

    private int max(int[] sequence){
        int max = sequence[0];
        for(int value:sequence) if(value>0) max = value;
        return max;
    }

It assigns instead of comparing, so it returns **the last positive element**, not the maximum. For
this fixture that is 700 instead of 950, so `low` starts below the true lower bound and the search
converges on an infeasible answer.

**And here is why it hid for twelve years:** in every other fixture in `FairWorkloadTest`, the last
element happens to be the largest, so `max()` returns the right answer by accident. Verified —
`{1,1,1,1,100}`, `{1,1}`, `{100,200,300,400,500}`, `{10,20,30}` all pass either way. One fixture
has its maximum in the middle, and that is the one that was switched off.

> **Cross-track note.** [Modernization doc 09](../modernization/09-streams-and-optional.md) also
> owns `FairWorkLoad.java` and asks you to diagnose that test *before* modernising around it. If
> you finish the diagnosis here, write it down there too, and delete the `@Disabled` in the same
> commit as the fix so the diff carries the evidence.

The lesson generalises past this file: **a binary search whose bounds are wrong does not fail, it
returns a confident wrong answer.** Same shape as stage 05's unsorted input.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: the greedy packing in TODO 1 is only correct because workers take *contiguous* runs —
what breaks if they can take any subset, and what problem does that become? Could a property-based
test ("the answer is always achievable and one less is not") have replaced all eight of
`FairWorkloadTest`'s hand-written cases? `max()` is three lines and was wrong for twelve years —
what review or tool would have caught it, and why did neither?
