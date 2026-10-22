# 19. Randomness and numerics

**Java 17** (`RandomGenerator`, JEP 356) · **Java 21** (`Math.clamp`, `StringBuilder.repeat`) ·
stage 8 of [the release-ordered track](INDEX.md) · compiles at `--release 21`, no build change

> **No safety net:** `Consumer`, `Producer`, `RandomFileLines`, `Safelock` have no tests. Write
> them first, in a separate commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says
> how, and why the order matters. If you've done [doc 07](07-classic-concurrency.md) and
> [doc 09](09-streams-and-optional.md), `Producer`, `Consumer` and `RandomFileLines` have tests
> already, and this doc builds on them.

## What this stage is actually about

Five words, defined once:

- a **pseudo-random generator** is a deterministic machine: some internal **state**, and a
  function that turns the state into a number and moves it on. Nothing about it is random except
  where it started.
- the **seed** is that starting state. The same seed gives the same sequence, every time, on every
  machine.
- **state bits** is how big the state is. `java.util.Random` has 48 (verified, `stateBits()`
  below), so it can only ever be in 2^48 different places.
- **`RandomGenerator`** (Java 17) is an **interface** over every generator in the JDK, including
  the old ones: `Random`, `SplittableRandom` and `SecureRandom` all implement it (verified).
- **`Math.clamp(value, min, max)`** (Java 21) forces a value into a range, and refuses a range
  where `min > max`.

That gives one rule, and everything below follows from it: **a generator's output is a function of
its seed, so whoever chooses the seed chooses the numbers.** Choose it from the clock and the clock
chooses them. Choose it in a test and the test does. Leave it to the JDK and nobody can predict it,
which is what you want in production.

## The worked example: seeding from the clock

`collections/RandomFileLines.java:32`, from 2014:

    Random rline = new Random(System.currentTimeMillis());

It looks like extra care. It's the opposite. Here is what `new Random()` with **no** argument does
in JDK 26 (`Random.java:310-311` in the JDK's `src.zip`):

    public Random() {
        this(seedUniquifier() ^ System.nanoTime());
    }

`seedUniquifier()` is a shared counter, stepped with a compare-and-set on every call, so two
generators created at the same instant still start in different places. Passing
`currentTimeMillis()` throws that away and replaces it with a number that changes once a
millisecond and that anyone can guess. Verified, creating 1,000 generators in a loop and keeping
each one's first `nextInt()`, three runs:

    clock-seeded:  2 distinct values   (loop took 0.87 ms)
                   1 distinct value    (0.28 ms)
                   1 distinct value    (0.21 ms)
    new Random():  1000 distinct values, every run

A thousand "random" generators, one sequence. Same seed, same numbers: that's the rule, and
`RollsTest.same_clock_seed_gives_the_same_sequence` (TODO 4) is it written as a test. Seed
`1760000000000L` gives `[2, 3, 6, 1, 3, 2, 1, 2, 3, 6]` twice (verified).

**A correction to the old version:** it said a caller of `RandomFileLines` "in a loop gets the
same random lines every time". Nothing calls it in a loop. `printRandomLines` is private, `main`
calls it once, and it makes one generator for all five lines, so a single run is fine. The bug is
**latent**: two runs started in the same millisecond print the same lines, and the first caller
who puts it in a loop gets the result above. Nothing tests it, because nothing could: the seed is
chosen inside the method.

## Part one: `random/Rolls.java`, four TODOs

New code. Delete the `@Disabled` in `RollsTest`, and **7 tests run: 6 fail, and TODO 4's passes**
(verified, on a scratch copy). TODO 4 has nothing to implement. It's the worked example above.

Facts for TODOs 1 and 2, all verified on JDK 26:

- **Two ways to get an `L64X128MixRandom`, and only one is testable.**
  `RandomGeneratorFactory.of(name).create(seed)` is seeded. `RandomGenerator.of(name)` isn't:
  called twice, it gave `[4, 1, 1, 2, 5]` and `[1, 2, 4, 1, 3]`. `RandomGenerator.getDefault()`
  is `L32X64MixRandom`.
- **What your JDK has**: `RandomGeneratorFactory.all()` lists 13 algorithms. Abridged:

  | Name | Group | State bits | Splittable | Jumpable |
  | --- | --- | --- | --- | --- |
  | `Random` | Legacy | 48 | no | no |
  | `SplittableRandom` | Legacy | 64 | yes | no |
  | `L32X64MixRandom` | LXM | 96 | yes | no |
  | `L64X128MixRandom` | LXM | 192 | yes | no |
  | `L128X1024MixRandom` | LXM | 1152 | yes | no |
  | `Xoshiro256PlusPlus` | Xoshiro | 256 | no | yes |

  **Splittable** means `split()` gives you a new, statistically independent generator, which is
  how you give each thread or task its own. **Jumpable** means it can skip ahead 2^64 or more
  steps at once, which does the same job differently. `Random` does neither.
- **Why `RollsTest` asserts equality between two runs, not fixed values.** With seed 42,
  `L64X128MixRandom` rolls `[1, 2, 1, 1, 4]` and `Random` rolls `[3, 4, 1, 3, 1]` (verified). A test
  that pinned the first list would be testing the algorithm, and it would break the day you
  changed the algorithm's name, which is the thing this API exists to let you do.

For TODO 3, `Math.clamp`. Write it with `Math.min` and `Math.max` first, as the TODO says, and run
the test. Then these, all verified:

    Math.clamp(150, 0, 100)                // 100
    Math.clamp(-7, 0, 100)                 // 0
    Math.max(0, Math.min(3, -1))           // 0:  min/max with an inverted range answers anyway
    Math.clamp(3, 0, -1)                   // IllegalArgumentException: 0 > -1

**Brevity isn't what `clamp` bought you.** An inverted range is what you get when the collection
is empty, and the `min`/`max` version answers with an index into it. `clamp` refuses.
`clamp_rejects_an_empty_range` is that case, and it fails until you use `clamp`.

And one the old version didn't mention: `clamp(long, int, int)` **returns `int`**, so it clamps
before it narrows. Take a `long` of 3,000,000,000 and a range of 0 to 100:
`Math.max(0, Math.min((int) v, 100))` gives **0**, because the cast wrapped it to -1294967296
first. `Math.clamp(v, 0, 100)` gives **100** (verified). The four overloads are `int` (from
`long`), `long`, `double` and `float`.

`StringBuilder.repeat` is the other small Java 21 addition: `new StringBuilder("ab").repeat("-",
3).repeat('x', 2)` is `ab---xx` (verified). `String.repeat` has been there since 11, and
[doc 18](18-files-strings-and-charsets.md) uses it.

## Part two: `RandomFileLines`, the clock seed

If you've done [doc 09](09-streams-and-optional.md), the line selection already takes a `Random`
as a parameter, and `main` is the only place that makes one. If not, do doc 09's part two step 1
first: that extraction is what makes this file testable at all.

1. **Fix the seed**, alone: `new Random()`, with no argument. **No test can show this**: "less
   guessable" isn't something an assertion can check, and the seeded tests from doc 09 don't
   change. Put the loop result above in the commit message as the evidence. This is the rare
   behaviour change that you justify in writing rather than in a test, and you should be able
   to say why.
2. **Widen the parameter** from `Random` to `RandomGenerator`, as its own commit. Every caller
   still compiles, because `Random` implements it (verified), and the tests from doc 09 stay
   green without being edited. That's how you know the commit is a refactor.

## Part three: `Producer`, `Consumer`, `Safelock`

`guardedLock/Producer.java:25`, `guardedLock/Consumer.java:20` and `Safelock.java:79` each make a
`new Random()` inside `run()`, to sleep for a random time.

**A correction to the old version, two of them.** It said "five `new Random()` sites"; there are
**four** in `src/main` (`grep`). And it said `Random` "is synchronized internally". It isn't:
`Random` keeps its state in an `AtomicLong` and steps it with compare-and-set (`Random.java:296`).
Only `setSeed` and `nextGaussian` are `synchronized`. Contention is still real: its own javadoc
warns about it. Eight threads making 10 million `nextInt` calls each took about **45 times as
long** sharing one `Random` as with one each (verified, idle 10-core machine, three runs: about
2,360 ms against 51 ms). But **none of these three sites shares one**: each thread makes its
own. So contention isn't the argument here. Nor is speed: with one generator per thread,
`L64X128MixRandom` took 66 ms to `Random`'s 51 (same runs). What the new algorithms buy is more
state and better statistics, not time.

The argument is the rule. Whoever chooses the seed chooses the delays, and today nobody outside
`run()` can. [Doc 07](07-classic-concurrency.md) part two, step 1, already asks you to make
`Producer` and `Consumer`'s delay injectable. If you chose a `Random` there, widen it to
`RandomGenerator` now, as for `RandomFileLines`. If you chose a maximum delay or a `Sleeper`, say
in writing whether a generator would have been better, and leave it alone.

`Safelock` is the one to think about rather than change. Its `run()` is a `for (;;)` that can't be
stopped ([doc 08](08-memory-model.md) part three), so a seeded generator wouldn't make it
testable: there's nothing to assert on until it can stop. Write down which has to come first.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: none of these generators is cryptographically secure (`Random`'s javadoc says so): what
question tells you that you need `SecureRandom`, and what does it cost? If a `RandomGenerator`
parameter makes a class testable, what does it do to every call site that didn't want to care?
When is a fixed seed in a test a lie, in the sense that it makes the test pass for a reason the
production code won't reproduce?
