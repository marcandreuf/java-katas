# 19. Randomness and numerics

**Java 17** (`RandomGenerator`, JEP 356) · **Java 21** (`Math.clamp`, `StringBuilder.repeat`) ·
stage 8 of [the release-ordered track](INDEX.md) · compiles at `--release 21`, no build change

> **No safety net:** `Consumer`, `Producer`, `RandomFileLines`, `Safelock` have no tests. Write
> them first, in a separate commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says
> how, and why the order matters. If you've done [doc 07](07-classic-concurrency.md) and
> [doc 09](09-streams-and-optional.md), `Producer`, `Consumer` and `RandomFileLines` have tests
> already, and this doc builds on them.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `random/Rolls`](#exercise-1-randomrolls-four-todos) | Four TODOs: a seeded generator, dice rolls, a clamped index, and one test to read. | T1–T5 | `RollsTest` exists: delete its `@Disabled`. |
| [2. `RandomFileLines`](#exercise-2-randomfilelines-the-clock-seed) | Fix the clock seed, then widen `Random` to `RandomGenerator`. Two commits. | T2, T3, T4 | Doc 09's tests stay green; the seed fix is argued in writing. |
| [3. `Producer`, `Consumer`, `Safelock`](#exercise-3-producer-consumer-safelock) | Decide whether each `new Random()` should be passed in, and change only what earns it. | T4 | Builds on doc 07's tests. |
| [4. What got worse](#exercise-4-fill-in-what-got-worse) | Write down what the new API costs. | all | None. |
| [5. Review questions](#exercise-5-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the five sections in order: each one builds on the one before. Here is the whole story in one
paragraph, so you know where it's going.

> A "random" number generator in Java isn't random at all: it's a small machine that starts from
> a number called the seed and computes every value after it, so the same seed always gives the
> same numbers (T1). That makes seeding from the clock a mistake that looks like care: generators
> created in the same millisecond produce the same sequence (T2). Java 17 put one interface,
> `RandomGenerator`, over every generator the JDK has, old and new (T3). And once code takes a
> `RandomGenerator` instead of making its own, the caller chooses the seed: a test can make the
> numbers repeat, and production can leave them unpredictable (T4). Java 21 added two small
> helpers that sit next to all this: `Math.clamp` and `StringBuilder.repeat` (T5).

Every code sample below is a complete program. Save it as the class name plus `.java` and run it
with `java Name.java`; no build needed. Outputs marked *verified* were run on JDK 26. Every sample
compiles at `--release 21`.

### T1. A generator is a machine, and the seed is where it starts

A **pseudo-random generator** is a deterministic machine: some internal **state**, and a function
that turns the state into a number and moves the state on. Nothing about it is random except
where it started. That starting state is the **seed**.

So the same seed gives the same sequence, every time:

```java
import java.util.Arrays;
import java.util.Random;

public class Seeds {
    static int[] firstFive(Random generator) {
        int[] values = new int[5];
        for (int i = 0; i < values.length; i++) {
            values[i] = generator.nextInt(100);       // 0..99
        }
        return values;
    }

    public static void main(String[] args) {
        System.out.println("new Random(42): " + Arrays.toString(firstFive(new Random(42))));
        System.out.println("new Random(42): " + Arrays.toString(firstFive(new Random(42))));
        System.out.println("new Random():   " + Arrays.toString(firstFive(new Random())));
        System.out.println("new Random():   " + Arrays.toString(firstFive(new Random())));
    }
}
```

```text
new Random(42): [30, 63, 48, 84, 70]                     (verified 2026-11-23)
new Random(42): [30, 63, 48, 84, 70]
new Random():   [72, 76, 49, 48, 61]
new Random():   [52, 60, 51, 82, 47]
```

The two seeded lines are identical, and they'll be identical on your machine too: `Random`'s
javadoc spells out its exact algorithm and requires every Java implementation to use it, so
`new Random(42)` gives those five numbers on any JDK. The two unseeded lines differ from each other
and from run to run.

The size of the state matters too. **State bits** is how big the state is. `java.util.Random` has
48 (verified, with `stateBits()` in T3), so it can only ever be in 2^48 different places, and
its whole output is one long cycle through them.

That gives the rule this doc is built on:

> **A generator's output is a function of its seed, so whoever chooses the seed chooses the
> numbers.**

Choose it from the clock and the clock chooses them. Choose it in a test and the test does. Leave
it to the JDK and nobody can predict it, which is what you want in production.

### T2. Seeding from the clock: care that makes it worse

`collections/RandomFileLines.java:32`, from 2014:

```java
Random rline = new Random(System.currentTimeMillis());
```

It looks like extra care. It's the opposite. Here is what `new Random()` with **no** argument does
in JDK 26 (`Random.java:310-311` in the JDK's `src.zip`):

```java
public Random() {
    this(seedUniquifier() ^ System.nanoTime());
}
```

`seedUniquifier()` is a shared counter, stepped with a compare-and-set on every call, so two
generators created at the same instant still start in different places. Passing
`currentTimeMillis()` throws that away and replaces it with a number that changes once a
millisecond and that anyone can guess. Here's the difference, as a program:

```java
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class ClockSeed {
    public static void main(String[] args) {
        Set<Integer> fromClock = new HashSet<>();
        Set<Integer> fromJdk = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            fromClock.add(new Random(System.currentTimeMillis()).nextInt());
            fromJdk.add(new Random().nextInt());
        }
        System.out.println("1000 clock-seeded generators: " + fromClock.size() + " distinct first values");
        System.out.println("1000 new Random() generators: " + fromJdk.size() + " distinct first values");
    }
}
```

```text
1000 clock-seeded generators: 2 distinct first values    (verified 2026-11-23)
1000 new Random() generators: 1000 distinct first values
```

Three more runs gave 2, 4 and 4 clock-seeded values: the loop spans a few milliseconds at most, and
each millisecond is one seed. `new Random()` gave 1000 every time. The earlier measurement for this
doc, with only the clock-seeded loop, was the same story (verified, three runs):

```text
clock-seeded:  2 distinct values   (loop took 0.87 ms)
               1 distinct value    (0.28 ms)
               1 distinct value    (0.21 ms)
new Random():  1000 distinct values, every run
```

A thousand "random" generators, one sequence. Same seed, same numbers: that's the rule, and
`RollsTest.same_clock_seed_gives_the_same_sequence` (TODO 4) is it written as a test. Seed
`1760000000000L` gives `[2, 3, 6, 1, 3, 2, 1, 2, 3, 6]` twice (verified).

**A correction to the old version:** it said a caller of `RandomFileLines` "in a loop gets the
same random lines every time". Nothing calls it in a loop. `printRandomLines` is private, `main`
calls it once, and it makes one generator for all five lines, so a single run is fine. The bug is
**latent**: two runs started in the same millisecond print the same lines, and the first caller
who puts it in a loop gets the result above. Nothing tests it, because nothing could: the seed is
chosen inside the method.

### T3. `RandomGenerator`: one interface over every generator

Before Java 17, the JDK's generators didn't share a type. `Random`, `SplittableRandom`,
`ThreadLocalRandom` and `SecureRandom` all had `nextInt`, but a method that wanted "a generator"
had to pick one class. **`RandomGenerator`** (Java 17, JEP 356) is an **interface** over every
generator in the JDK, including the old ones: `Random`, `SplittableRandom` and `SecureRandom`
all implement it (verified). It also brought a family of new algorithms, and a factory that
lists them all:

```java
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public class OneInterface {
    // Written once, against the interface. Any generator in the JDK can be passed in.
    static String coin(RandomGenerator generator) {
        return generator.nextBoolean() ? "heads" : "tails";
    }

    public static void main(String[] args) {
        System.out.println("Random:           " + coin(new Random(1)));
        System.out.println("SplittableRandom: " + coin(new SplittableRandom(1)));
        System.out.println("SecureRandom:     " + (new SecureRandom() instanceof RandomGenerator));
        System.out.println("default:          " + RandomGenerator.getDefault().getClass().getSimpleName());

        System.out.println();
        System.out.println("algorithms in this JDK:");
        RandomGeneratorFactory.all()
                .sorted(Comparator.comparing(RandomGeneratorFactory::name))
                .forEach(f -> System.out.printf("  %-22s %-9s %5d state bits%n",
                        f.name(), f.group(), f.stateBits()));
    }
}
```

```text
Random:           heads                                  (verified 2026-11-23)
SplittableRandom: tails
SecureRandom:     true
default:          L32X64MixRandom

algorithms in this JDK:
  L128X1024MixRandom     LXM        1152 state bits
  L128X128MixRandom      LXM         256 state bits
  L128X256MixRandom      LXM         384 state bits
  L32X64MixRandom        LXM          96 state bits
  L64X1024MixRandom      LXM        1088 state bits
  L64X128MixRandom       LXM         192 state bits
  L64X128StarStarRandom  LXM         192 state bits
  L64X256MixRandom       LXM         320 state bits
  Random                 Legacy       48 state bits
  SecureRandom           Legacy    2147483647 state bits
  SplittableRandom       Legacy       64 state bits
  Xoroshiro128PlusPlus   Xoroshiro   128 state bits
  Xoshiro256PlusPlus     Xoshiro     256 state bits
```

`coin` doesn't know or care which generator it got. Thirteen algorithms, and `Random`'s 48 bits is
the smallest state of any of them. (`SecureRandom`'s 2147483647 is `Integer.MAX_VALUE`, standing in
for "not a fixed-size state": it draws on the operating system's entropy.)

Two capabilities separate the new algorithms from `Random`:

- **Splittable** means `split()` gives you a new, statistically independent generator, which is
  how you give each thread or task its own.
- **Jumpable** means it can skip ahead 2^64 or more steps at once, which does the same job
  differently.

`Random` does neither. Exercise 1 has the table of which algorithm does which.

**One caution on reproducibility.** `Random` is the only generator whose javadoc makes its exact
algorithm part of the contract (T1). For the others, a seed reproduces a sequence on the JDK you
ran it on, and a test that pins the actual values is testing the algorithm, not your code.
Exercise 1 explains why `RollsTest` compares two runs instead.

### T4. Pass the generator in, and the caller chooses the seed

T1's rule says whoever chooses the seed chooses the numbers. In code that makes its own
generator (`new Random()` inside a method), the code chose, and nobody outside can change it. A
test can't make the output repeat, so it can only assert things that are true for *every*
possible output.

The fix is the same move as for any other dependency: **take the generator as a parameter**, typed
as the interface from T3. Then the caller chooses:

```java
import java.util.Random;
import java.util.random.RandomGenerator;

public class Forecast {
    private final double chanceOfRain;
    private final RandomGenerator generator;           // chosen by whoever builds the Forecast

    Forecast(double chanceOfRain, RandomGenerator generator) {
        this.chanceOfRain = chanceOfRain;
        this.generator = generator;
    }

    String tomorrow() {
        return generator.nextDouble() < chanceOfRain ? "rain" : "sun";
    }

    public static void main(String[] args) {
        // A test chooses the seed, so it can run the same week twice and compare.
        Forecast first = new Forecast(0.3, new Random(2026));
        Forecast again = new Forecast(0.3, new Random(2026));
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        for (int day = 0; day < 7; day++) {
            a.append(first.tomorrow()).append(' ');
            b.append(again.tomorrow()).append(' ');
        }
        System.out.println("seeded:  " + a.toString().trim());
        System.out.println("seeded:  " + b.toString().trim());

        // Production leaves the seed to the JDK: nobody can predict it, which is the point.
        Forecast live = new Forecast(0.3, RandomGenerator.getDefault());
        System.out.println("live:    " + live.tomorrow() + " (varies by run)");
    }
}
```

```text
seeded:  sun sun sun sun sun rain sun                    (verified 2026-11-23)
seeded:  sun sun sun sun sun rain sun
live:    sun (varies by run)
```

`Forecast` has no idea whether it's in a test. The seeded pair is identical because the caller
made it so; the live one is unpredictable because the caller made *that* so. One class, two
policies, and the policy lives where it belongs: with whoever builds the object.

That's the whole argument for the injection in exercises 2 and 3, and it's the same one
[doc 07, exercise 1](07-classic-concurrency.md#exercise-1-fix-the-interrupt-bugs-in-guardedlock)
uses for `Producer` and `Consumer`'s delays. Note what the parameter type buys you: because it's
`RandomGenerator` and not `Random`, a caller can pass any of T3's thirteen algorithms without the
class changing.

### T5. Two small Java 21 additions: `Math.clamp` and `StringBuilder.repeat`

**`Math.clamp(value, min, max)`** (Java 21) forces a value into a range, and refuses a range where
`min > max`. You could always write it as `Math.max(min, Math.min(value, max))`. Two things make
`clamp` more than a shorter spelling, and this program shows both:

```java
public class Clamp {
    public static void main(String[] args) {
        long big = 3_000_000_000L;                         // too big for an int

        // The hand-written way narrows first, and the cast wraps the value round.
        System.out.println("(int) big                   = " + (int) big);
        System.out.println("max(0, min((int) big, 100)) = " + Math.max(0, Math.min((int) big, 100)));
        // clamp(long, int, int) clamps first, then returns an int.
        System.out.println("clamp(big, 0, 100)          = " + Math.clamp(big, 0, 100));

        System.out.println("clamp(2.75, 0.0, 1.0)       = " + Math.clamp(2.75, 0.0, 1.0));

        // An inverted range: min/max still answers, clamp refuses.
        System.out.println("max(5, min(7, 4))           = " + Math.max(5, Math.min(7, 4)));
        try {
            Math.clamp(7, 5, 4);
        } catch (IllegalArgumentException e) {
            System.out.println("clamp(7, 5, 4)              -> IllegalArgumentException: " + e.getMessage());
        }

        // The other small Java 21 addition.
        System.out.println("StringBuilder.repeat        = " + new StringBuilder("ab").repeat("-", 3).repeat('x', 2));
    }
}
```

```text
(int) big                   = -1294967296                (verified 2026-11-23)
max(0, min((int) big, 100)) = 0
clamp(big, 0, 100)          = 100
clamp(2.75, 0.0, 1.0)       = 1.0
max(5, min(7, 4))           = 5
clamp(7, 5, 4)              -> IllegalArgumentException: 5 > 4
StringBuilder.repeat        = ab---xx
```

- **It clamps before it narrows.** `clamp(long, int, int)` **returns `int`**. The hand-written
  version has to cast the `long` to `int` first, and 3,000,000,000 wraps to -1294967296, which
  then clamps to 0. `clamp` gives 100. The four overloads are `int` (from `long`), `long`,
  `double` and `float`.
- **It refuses an inverted range.** With `min > max` there's no right answer, and the min/max
  version gives one anyway (here, 5). `clamp` throws. Exercise 1, TODO 3, is about when an
  inverted range turns up in real code, and why that refusal is the point.

`StringBuilder.repeat` is the other small Java 21 addition. `String.repeat` has been there since
11, and [doc 18](18-files-strings-and-charsets.md) uses it.

## Practice

### Exercise 1: `random/Rolls`, four TODOs

New code. Delete the `@Disabled` in `RollsTest`, and **7 tests run: 6 fail, and TODO 4's passes**
(verified, on a scratch copy). TODO 4 has nothing to implement. It's T2's clock-seed result,
written as a test: read it, run it, and write down what it means for `RandomFileLines`.

Facts for TODOs 1 and 2, all verified on JDK 26:

- **Two ways to get an `L64X128MixRandom`, and only one is testable.**
  `RandomGeneratorFactory.of(name).create(seed)` is seeded. `RandomGenerator.of(name)` isn't:
  called twice, it gave `[4, 1, 1, 2, 5]` and `[1, 2, 4, 1, 3]`. `RandomGenerator.getDefault()`
  is `L32X64MixRandom`.
- **What your JDK has**: `RandomGeneratorFactory.all()` lists 13 algorithms (T3 prints them all).
  Abridged, with the two capabilities from T3:

  | Name | Group | State bits | Splittable | Jumpable |
  | --- | --- | --- | --- | --- |
  | `Random` | Legacy | 48 | no | no |
  | `SplittableRandom` | Legacy | 64 | yes | no |
  | `L32X64MixRandom` | LXM | 96 | yes | no |
  | `L64X128MixRandom` | LXM | 192 | yes | no |
  | `L128X1024MixRandom` | LXM | 1152 | yes | no |
  | `Xoshiro256PlusPlus` | Xoshiro | 256 | no | yes |

- **Why `RollsTest` asserts equality between two runs, not fixed values.** With seed 42,
  `L64X128MixRandom` rolls `[1, 2, 1, 1, 4]` and `Random` rolls `[3, 4, 1, 3, 1]` (verified). A test
  that pinned the first list would be testing the algorithm, and it would break the day you
  changed the algorithm's name, which is the thing this API exists to let you do (T3).

For TODO 2, the TODO comment asks for the generator's `ints()` stream rather than a loop over
`nextInt()`. Look at which `ints` overloads exist, and which bound is exclusive.

For TODO 3, `Math.clamp`. Write it with `Math.min` and `Math.max` first, as the TODO says, and run
the test. Then these, all verified:

```java
Math.clamp(150, 0, 100)                // 100
Math.clamp(-7, 0, 100)                 // 0
Math.max(0, Math.min(3, -1))           // 0:  min/max with an inverted range answers anyway
Math.clamp(3, 0, -1)                   // IllegalArgumentException: 0 > -1
```

**Brevity isn't what `clamp` bought you.** An inverted range is what you get when the collection
is empty, and the `min`/`max` version answers with an index into it. `clamp` refuses.
`clamp_rejects_an_empty_range` is that case, and it fails until you use `clamp`.

And one the old version didn't mention: `clamp(long, int, int)` **returns `int`**, so it clamps
before it narrows. Take a `long` of 3,000,000,000 and a range of 0 to 100:
`Math.max(0, Math.min((int) v, 100))` gives **0**, because the cast wrapped it to -1294967296
first. `Math.clamp(v, 0, 100)` gives **100** (verified; T5 runs it). The four overloads are `int`
(from `long`), `long`, `double` and `float`.

`StringBuilder.repeat` is the other small Java 21 addition: `new StringBuilder("ab").repeat("-",
3).repeat('x', 2)` is `ab---xx` (verified). `String.repeat` has been there since 11, and
[doc 18](18-files-strings-and-charsets.md) uses it.

**Done when:** the `@Disabled` line is gone, all 7 `RollsTest` tests pass, and you've written down
what `clamp` bought you (TODO 3) and what TODO 4 means for `RandomFileLines`.

### Exercise 2: `RandomFileLines`, the clock seed

If you've done [doc 09](09-streams-and-optional.md), the line selection already takes a `Random`
as a parameter, and `main` is the only place that makes one. If not, do
[doc 09, exercise 2](09-streams-and-optional.md#exercise-2-randomfilelines), commit 1, first: that
extraction is what makes this file testable at all.

1. **Fix the seed**, alone: `new Random()`, with no argument. **No test can show this**: "less
   guessable" isn't something an assertion can check, and the seeded tests from doc 09 don't
   change. Put T2's loop result in the commit message as the evidence. This is the rare
   behaviour change that you justify in writing rather than in a test, and you should be able
   to say why.
2. **Widen the parameter** from `Random` to `RandomGenerator`, as its own commit. Every caller
   still compiles, because `Random` implements it (verified, T3), and the tests from doc 09 stay
   green without being edited. That's how you know the commit is a refactor.

**Done when:** the two commits are in, in that order, the first one's message carries the
evidence, and doc 09's tests passed unedited after the second.

### Exercise 3: `Producer`, `Consumer`, `Safelock`

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

The argument is T1's rule. Whoever chooses the seed chooses the delays, and today nobody outside
`run()` can (T4). [Doc 07, exercise 1](07-classic-concurrency.md#exercise-1-fix-the-interrupt-bugs-in-guardedlock),
step 1, already asks you to make `Producer` and `Consumer`'s delay injectable. If you chose a
`Random` there, widen it to `RandomGenerator` now, as for `RandomFileLines`. If you chose a maximum
delay or a `Sleeper`, say in writing whether a generator would have been better, and leave it
alone.

`Safelock` is the one to think about rather than change. Its `run()` is a `for (;;)` that can't be
stopped ([doc 08, exercise 3](08-memory-model.md#exercise-3-safelock-the-other-half)), so a seeded
generator wouldn't make it testable: there's nothing to assert on until it can stop. Write down
which has to come first.

**Done when:** `Producer` and `Consumer` either take a `RandomGenerator` or you've written down why
your doc 07 choice is better, and you've written down what has to change in `Safelock` before a
seed could help.

### Exercise 4: fill in "What got worse"

- _(fill this in)_
- _(fill this in)_

Prompts: none of these generators is cryptographically secure (`Random`'s javadoc says so): what
question tells you that you need `SecureRandom`, and what does it cost? If a `RandomGenerator`
parameter makes a class testable, what does it do to every call site that didn't want to care?
When is a fixed seed in a test a lie, in the sense that it makes the test pass for a reason the
production code won't reproduce?

### Exercise 5: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. What is a seed, and why does the same seed always give the same numbers? (T1)
2. `new Random(42)` gives the same five numbers on every JDK. Why can you count on that for
   `Random` and not for the other algorithms? (T1, T3)
3. Why does `new Random(System.currentTimeMillis())` give *less* variety than `new Random()`?
   What does the no-argument constructor use that the clock seed throws away? (T2)
4. A thousand clock-seeded generators gave between 1 and 4 distinct first values. What decides how
   many? (T2)
5. Name three classes that implement `RandomGenerator`. Which one does `getDefault()` give you on
   JDK 26? (T3)
6. What do "splittable" and "jumpable" let you do, and which does `Random` support? (T3)
7. Why does `RollsTest` compare two runs instead of asserting a fixed list of rolls? (T3,
   exercise 1)
8. A class makes `new Random()` inside a method. What can't a test of it do, and what's the
   change that fixes that? (T4)
9. Why type that parameter as `RandomGenerator` and not `Random`? (T3, T4)
10. `Math.max(0, Math.min((int) v, 100))` with `v = 3_000_000_000L` gives 0. What does
    `Math.clamp(v, 0, 100)` give, and why the difference? (T5)
11. When does `Math.clamp` throw, and why is that better than an answer? (T5, exercise 1)

**Done when:** you can answer all eleven without looking.
