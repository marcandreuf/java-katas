# 19. Randomness and numerics

**Java 17, 21** · stage 8 of [the release-ordered track](INDEX.md) · `--release 21`, no build
change

## Where it lands

Five `new Random()` sites, and one of them is a bug:

| File | What is there |
| --- | --- |
| `collections/RandomFileLines.java:32` | `new Random(System.currentTimeMillis())` — **the bug** |
| `concurrency/guardedLock/Producer.java:53` | `new Random()` inside `run()` |
| `concurrency/guardedLock/Consumer.java:48` | `new Random()` inside `run()` |
| `concurrency/Safelock.java:107` | `new Random()` inside `run()` |

New code: **`random/Rolls.java`**, four TODOs — and TODO 4 has nothing to implement.

## The bug, first

    Random rline = new Random(System.currentTimeMillis());

**Seeding from the clock does not add entropy. It removes it.** `new Random()` with no argument
already seeds itself from a source designed for the job — since Java 8 that is a per-instance
`seedUniquifier` mixed with `System.nanoTime()`, specifically so that two instances created in
quick succession diverge. Passing `currentTimeMillis()` *overrides* that with a value that has
millisecond resolution and is trivially guessable.

`RollsTest.same_clock_seed_gives_the_same_sequence` is TODO 4 and it passes as written:

    new Random(1760000000000L).ints(10, 1, 7)   ->  [2, 3, 6, 1, 3, ...]
    new Random(1760000000000L).ints(10, 1, 7)   ->  [2, 3, 6, 1, 3, ...]    identical

On a machine that can run that constructor twice inside one millisecond — which is every machine —
two "random" generators produce **the same sequence**. `RandomFileLines` creates its generator
inside a method, so a caller in a loop gets the same "random" lines every time. Nothing tested it.

Fix it by deleting the argument. That is the entire fix, and the lesson is that the shorter call
was always the better one.

## The exercise

### `RandomGenerator`, and why it made randomness testable

TODO 1 and 2. JEP 356 (Java 17) added an **interface** where there had only ever been a class:

    RandomGenerator g = RandomGeneratorFactory.of("L64X128MixRandom").create(42L);   // seeded
    RandomGenerator g = RandomGenerator.of("L64X128MixRandom");                       // unseeded
    List<Integer> rolls = g.ints(count, 1, 7).boxed().toList();

Three things this buys that `java.util.Random` could not:

1. **It is an interface**, so a method that takes a `RandomGenerator` can be handed a seeded one
   in a test and the default in production. This is the same move as the `Clock` parameter in
   [doc 13](13-java-time.md), and it is the same payoff: `RollsTest` asserts reproducibility with
   no flake and no retry loop.
2. **A choice of algorithm.** `java.util.Random` is a 48-bit LCG from 1995 — statistically poor
   and only 2^48 states. `L64X128MixRandom` and the Xoshiro/Xoroshiro family are better and
   faster. `RandomGeneratorFactory.all()` enumerates what your JDK has.
3. **Splittable and jumpable generators**, which is how you get independent streams per thread
   without sharing one contended generator. `Random` is synchronized internally; under contention
   that is a real cost, and `ThreadLocalRandom` was the 2011 workaround that this API generalises.

**Note the test asserts equality between two seeded runs, not fixed expected values.** Pinning
`[1, 2, 1, 1, 4]` would be testing the algorithm rather than your code, and would break the day
you changed the algorithm name — which is a thing you are meant to be free to do.

### `Math.clamp`

TODO 3. Write it with `Math.min`/`Math.max` first:

    Math.max(0, Math.min(value, size - 1))      // works, and reads as a puzzle
    Math.clamp(value, 0, size - 1)              // Java 21

**Brevity is not what `clamp` bought you.** The nested version silently returns nonsense when the
bounds are inverted — `max(9, min(3, 0))` is `9`, which is neither in range nor an error.
`Math.clamp` throws `IllegalArgumentException` when `min > max`. The test pins that. A function
that refuses impossible input beats one that invents an answer, and that is the general lesson
worth taking out of a two-line method.

`StringBuilder.repeat` (21) is the other small one; `String.repeat` has been there since 11 and
[doc 18](18-files-strings-and-charsets.md) uses it.

### Then fix the other three

`Producer:53`, `Consumer:48` and `Safelock:107` all construct a `Random` inside `run()`. Converting
them to `RandomGenerator` is mechanical. **The interesting question is whether to pass it in**, and
the answer tells you whether those classes were ever testable. They were not —
[doc 07](07-classic-concurrency.md) is where that gets fixed, and this is a hint about how.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: none of these generators is cryptographically secure — what is the question that tells
you that you need `SecureRandom` instead, and what does it cost? If a `RandomGenerator` parameter
makes a class testable, what does it do to every call site that did not want to care? When is a
fixed seed in a test a lie, in the sense that it makes the test pass for a reason the production
code will not reproduce?
