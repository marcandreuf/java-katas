# 34. Measuring it properly

**Version-independent** (JMH 1.37, checked on JDK 26) · stage 23 of
[the release-ordered track](INDEX.md) · **build change: two test-scope dependencies and one
compiler setting** · the last stage, and it invalidates some of your earlier answers

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. Prove the problem](#exercise-1-prove-the-problem-to-yourself) | A scratch stopwatch loop, run three ways. Record the numbers. | T1–T3 | None: scratch file. |
| [2. Add JMH](#exercise-2-add-jmh-as-its-own-commit) | The pom change, as its own commit. | T6 | All existing tests stay green. |
| [3. Re-answer doc 14](#exercise-3-re-answer-doc-14) | A JMH benchmark of `WordTally`'s two counters. | T2–T7 | The benchmark is the measurement; doc 14's tests must pass first. |
| [4. Write the answers](#exercise-4-write-the-answers-here) | Five written answers. | all | None. |
| [5. What got worse](#exercise-5-fill-in-what-got-worse) | The costs of measuring properly. | all | None. |
| [6. Review questions](#exercise-6-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the eight sections in order: each one builds on the one before. Here is the whole story in
one paragraph, so you know where it's going.

> Earlier docs timed things with a stopwatch: read the clock, run the code, read the clock again.
> That number is weak evidence, because the JVM changes the code while you time it (T1). The
> first runs time the interpreter, not your code (T2). Work whose result nobody uses gets deleted,
> so you time nothing (T3). One run is one sample out of a noisy spread, and garbage collection
> adds to the noise (T4). Even the clock you read matters (T5). JMH is the tool that deals with
> all of this for you (T6), and it also exposes a fourth trap, constant folding, that a stopwatch
> can't even show you (T7). And JMH is only for small pieces of code: a whole program is measured
> differently (T8).

The plain-Java samples are complete programs: save each as its class name plus `.java` and run it
with `java Name.java`. The JMH sample needs the build steps in T6. **Every timing below is one run
on one machine** (JDK 26, `nproc` = 10), marked *verified* because it was actually run, not
because the numbers are guaranteed. Yours will differ. The *shape* (which one is bigger, by
roughly how much) is the claim.

### T1. What a stopwatch actually measures

The idiom in this repo, and in most code, is this:

```java
long start = System.currentTimeMillis();
doTheWork();
long elapsed = System.currentTimeMillis() - start;
```

It looks like it measures `doTheWork()`. It measures whatever the JVM ran between the two clock
reads, and that's a different thing in three ways:

- the JVM **starts in an interpreter**, and compiles hot code to machine code later, while your
  program runs (T2);
- the JIT compiler is allowed to **delete work** whose result nobody uses (T3), and to **compute
  values in advance** when their inputs can't change (T7);
- the machine is **doing other things**: the garbage collector, other processes, the CPU changing
  speed (T4).

A **microbenchmark** times a small piece of code, a method or a loop, in isolation. Its result is
nanoseconds per operation, and almost everything about the JVM conspires to make that number
wrong. That gives one rule, and everything below follows from it:

> **A measurement is only evidence if you know what the JIT did to the code you measured.**

A stopwatch around a loop measures whatever was left after the JIT finished with it, and that may
be nothing.

### T2. Warm-up: the first runs time a different program

The JVM doesn't compile your methods before it runs them. It starts by **interpreting** the
bytecode, counts how often each method runs, and compiles the hot ones: first quickly with **C1**,
later carefully with **C2**. **Warm-up** is the time before the JIT has compiled the code you care
about. Code timed from cold is timing the interpreter, plus the compiler working in the
background.

Watch it happen. The same work, timed eight times in a row in one JVM:

```java
public class Warmup {
    // The work we want to time: count the vowels in a string, many times.
    static int vowels(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            switch (s.charAt(i)) {
                case 'a', 'e', 'i', 'o', 'u' -> n++;
                default -> { }
            }
        }
        return n;
    }

    public static void main(String[] args) {
        String text = "the quick brown fox jumps over the lazy dog ".repeat(100);
        for (int round = 1; round <= 8; round++) {
            long start = System.nanoTime();
            long total = 0;
            for (int i = 0; i < 2_000; i++) {
                total += vowels(text);
            }
            long micros = (System.nanoTime() - start) / 1_000;
            System.out.printf("round %d: %6d us   (total=%d)%n", round, micros, total);
        }
    }
}
```

```text
round 1:  29740 us   (total=2200000)               (verified 2026-11-23; one run, this machine)
round 2:  17851 us   (total=2200000)
round 3:  16821 us   (total=2200000)
round 4:  15829 us   (total=2200000)
round 5:  15776 us   (total=2200000)
round 6:  16374 us   (total=2200000)
round 7:  16328 us   (total=2200000)
round 8:  16962 us   (total=2200000)
```

Round 1 is almost twice as slow as the rest, and nothing about the code changed. Two more runs
gave round 1 at 24478 and 24091 µs against ~14600 µs after. Now turn the JIT off with
`java -Xint Warmup.java`, so everything stays interpreted:

```text
round 1: 1351522 us   (total=2200000)              (verified 2026-11-23; one run, this machine)
round 2: 1349130 us   (total=2200000)              (trimmed: rounds 3 to 7 cut)
...
round 8: 1357487 us   (total=2200000)
```

About 80 times slower, every round, and no warm-up curve, because nothing ever gets compiled. So
"round 1" in the first run was mostly interpreter time, and the later rounds are compiled code. A
single stopwatch reading taken at the start of a program measures the interpreter. A reading
taken later measures compiled code. They are different programs.

### T3. Dead code elimination: the work that isn't there

**Dead code elimination** is the JIT deleting work whose result nobody uses. It's legal: the
program's output can't tell the difference. And it's the most common reason a benchmark reports
an impossible number.

```java
public class Deleted {
    public static void main(String[] args) {
        for (int round = 1; round <= 6; round++) {
            long t0 = System.nanoTime();
            double kept = 0;
            for (int i = 1; i <= 5_000_000; i++) {
                kept += Math.sqrt(i);                 // the result is used below
            }
            long t1 = System.nanoTime();
            for (int i = 1; i <= 5_000_000; i++) {
                Math.sqrt(i);                         // the result goes nowhere
            }
            long t2 = System.nanoTime();
            System.out.printf("round %d: used %6d us   discarded %6d us   (kept=%.0f)%n",
                    round, (t1 - t0) / 1_000, (t2 - t1) / 1_000, kept);
        }
    }
}
```

```text
round 1: used  16135 us   discarded   3794 us   (kept=7453561043)   (verified 2026-11-23; one run)
round 2: used  13692 us   discarded      0 us   (kept=7453561043)
round 3: used  14707 us   discarded      0 us   (kept=7453561043)
round 4: used  13519 us   discarded      0 us   (kept=7453561043)
round 5: used  14037 us   discarded      0 us   (kept=7453561043)
round 6: used  13784 us   discarded      0 us   (kept=7453561043)
```

Five million square roots in **0 µs**. Once the loop is compiled, the JIT sees that its results go
nowhere and deletes it, loop and all. Round 1 still shows 3794 µs only because it hadn't been
compiled yet (T2). Two more runs gave the same pattern: 0 µs from round 2 on.

Notice that both effects appear together, in one program, within two rounds. That's why a
stopwatch number on its own tells you so little: you don't know which of the two you were looking
at, or whether it was both.

The same thing happens inside a benchmark harness if you let it. Here is the worked example, three
JMH benchmarks on `Math.log`, run on JDK 26 (verified, 3 warmup and 3 measured iterations, one
fork; T6 explains the harness):

```java
@Benchmark public void   baseline()  { }
@Benchmark public void   discarded() { Math.log(x); }
@Benchmark public double returned()  { return Math.log(x); }
```

```text
Benchmark           Mode  Cnt   Score   Error  Units
LogBench.baseline   avgt    3   0.370 ± 0.153  ns/op
LogBench.discarded  avgt    3   0.364 ± 0.099  ns/op
LogBench.returned   avgt    3  13.639 ± 0.475  ns/op
```

`discarded` costs the same as an empty method. **The logarithm isn't slow or fast: it isn't
there.** The JIT saw the result go nowhere and deleted the call. Returning the value makes JMH
consume it, and the real cost appears, about 37 times higher. (Three measured iterations is few:
look at the `Error` column before trusting the baseline's third decimal place.)

It isn't only microbenchmarks. While writing [doc 31](31-runtime-defaults.md), a program that
measured heap use before and after filling an array reported **-4.2 bytes per object**. The array
wasn't read after the loop, so the GC was allowed to collect it before the second measurement.
One line that used the array afterwards fixed it (verified). Same mistake, different tool.

### T4. One run is one sample, and the GC adds noise

Even fully warmed-up code doesn't take the same time twice. The OS schedules other work, the CPU
changes its clock speed, caches are warm or cold, and the garbage collector stops or slows your
threads to clean up what the previous runs left behind. A single measurement is one sample from a
spread, and you don't know where in the spread it fell.

```java
import java.util.Arrays;

public class Spread {
    public static void main(String[] args) {
        int[] data = new java.util.Random(7).ints(200_000).toArray();
        long[] micros = new long[30];
        for (int run = 0; run < micros.length; run++) {
            int[] copy = data.clone();                // a fresh 800 KB array every run: garbage
            long t0 = System.nanoTime();
            Arrays.sort(copy);
            micros[run] = (System.nanoTime() - t0) / 1_000;
        }
        long[] lastTwenty = Arrays.copyOfRange(micros, 10, 30);   // skip the warm-up runs
        Arrays.sort(lastTwenty);
        System.out.println("first run:            " + micros[0] + " us");
        System.out.println("last 20, fastest:     " + lastTwenty[0] + " us");
        System.out.println("last 20, median:      " + lastTwenty[10] + " us");
        System.out.println("last 20, slowest:     " + lastTwenty[19] + " us");
    }
}
```

```text
first run:            42385 us                     (verified 2026-11-23; one run, this machine)
last 20, fastest:     1849 us
last 20, median:      2444 us
last 20, slowest:     3318 us
```

Two more runs gave first runs of 27977 and 24756 µs, and last-20 ranges of 1755–1918 and
1712–1847 µs. Three things to read from that:

- the first run was **about 17 times** the median. Warm-up again (T2), plus loading the sort's classes;
- after warm-up, the same sort on the same data still varied by **almost a factor of two** in one
  run, and much less in the next two. The spread itself changes between runs;
- so "I timed it and got 2.4 ms" is not a result. "Median 2.4 ms, range 1.8 to 3.3 ms, over 20
  warm runs, in one JVM" is one, and you still don't know how a second JVM would do.

That last point is why JMH runs each benchmark in a fresh JVM, a **fork**: the JIT can compile the
same code differently in two JVMs, and one benchmark can leave the JIT in a state that flatters or
hurts the next. One JVM's numbers are one sample of *those* too.

### T5. Which clock: `nanoTime`, not `currentTimeMillis`

Java has two clocks, and they answer different questions:

- `System.currentTimeMillis()` is **wall-clock time**: the date and time, as milliseconds since
  1970. Its javadoc says the granularity depends on the OS, and the clock can be adjusted while you
  measure (by NTP, or by someone changing the time).
- `System.nanoTime()` is a **stopwatch**. Its javadoc says it's for measuring elapsed time and
  nothing else: the value means nothing on its own, only the difference between two readings.

```java
public class Clocks {
    public static void main(String[] args) {
        // How small a step can each clock show? Read it until the value changes.
        long m0 = System.currentTimeMillis(), m1;
        while ((m1 = System.currentTimeMillis()) == m0) { }
        long n0 = System.nanoTime(), n1;
        while ((n1 = System.nanoTime()) == n0) { }
        System.out.println("currentTimeMillis smallest step: " + (m1 - m0) + " ms");
        System.out.println("nanoTime smallest step:          " + (n1 - n0) + " ns");

        // A one-line method timed with currentTimeMillis: the answer is usually 0.
        long start = System.currentTimeMillis();
        double r = Math.log(42.0);
        System.out.println("Math.log by currentTimeMillis:   " + (System.currentTimeMillis() - start)
                + " ms   (r=" + String.format("%.3f", r) + ")");
    }
}
```

```text
currentTimeMillis smallest step: 1 ms              (verified 2026-11-23; this machine, Linux)
nanoTime smallest step:          100 ns
Math.log by currentTimeMillis:   0 ms   (r=3.738)
```

A whole millisecond is a million nanoseconds, so `currentTimeMillis` can't see anything that takes
less than one, and `Math.log` takes about 14 ns (T3). For intervals, use `nanoTime`. And note that
even `nanoTime` has a step of its own (100 ns here, 210 ns on a re-run; it depends on the OS and hardware), so it
can't time one call of a 14 ns method either. To get nanoseconds per operation you need many
operations per reading, which is one of the things JMH does for you.

### T6. JMH: a harness that handles all of it

**JMH** (Java Microbenchmark Harness) is the OpenJDK tool for microbenchmarks. It is the one
dependency in this whole track that isn't in the JDK. You write the code to measure as methods
marked `@Benchmark`, and JMH generates the timing loop around them. Each problem so far has an
answer in JMH:

| Problem | Where | What JMH does |
| --- | --- | --- |
| warm-up | T2 | runs **warmup iterations** first and throws their numbers away (`@Warmup`) |
| dead code elimination | T3 | **consumes** whatever a `@Benchmark` method returns, or whatever you pass to a `Blackhole` |
| one run is one sample | T4 | many **measured iterations** (`@Measurement`), reported with an **error** margin |
| one JVM is one sample | T4 | runs each benchmark in a fresh JVM, a **fork** (`@Fork`) |
| clock resolution | T5 | calls the method millions of times per iteration and divides |
| constant folding | T7 | reads inputs from fields of a `@State` object, which the JIT can't assume are constant |

JMH works by **generating code**: an annotation processor reads your `@Benchmark` methods at
compile time and writes the harness classes, plus a list of benchmarks in
`META-INF/BenchmarkList`. That processor has to actually run, which on JDK 23 and later is no
longer automatic (exercise 2 has the Maven side of this).

To try it without touching the repo, build a scratch benchmark by hand. JMH 1.37 and its two
dependencies, `jopt-simple` and `commons-math3`, are in `~/.m2` once the repo has been built with
JMH (or download them). The commands, from a scratch directory with the benchmark in
`src/demo/FoldBench.java` (T7):

```sh
M=~/.m2/repository
CP=$M/org/openjdk/jmh/jmh-core/1.37/jmh-core-1.37.jar:$M/net/sf/jopt-simple/jopt-simple/5.0.4/jopt-simple-5.0.4.jar:$M/org/apache/commons/commons-math3/3.6.1/commons-math3-3.6.1.jar
AP=$M/org/openjdk/jmh/jmh-generator-annprocess/1.37/jmh-generator-annprocess-1.37.jar

javac -proc:full -cp $CP:$AP -d out src/demo/FoldBench.java     # -proc:full runs the processor
java -cp out:$CP org.openjdk.jmh.Main FoldBench -f 1 -wi 2 -i 3 -w 1s -r 1s
```

Leave out `-proc:full` and `javac` compiles happily, with no warning, and writes no
`out/META-INF` at all (verified on JDK 26): JMH then has nothing to run.

The flags make the run short: `-f 1` one fork, `-wi 2` two warmup iterations, `-i 3` three
measured ones, `-w 1s -r 1s` one second each. That's fine for seeing an effect of 37 times. It is
**too little to trust a small difference**: JMH's defaults are five forks of five warmup and five
measured iterations of ten seconds each, several minutes per benchmark, and that's not
excessive.

### T7. Constant folding: the trap a stopwatch can't show

**Constant folding** is the JIT computing a value at compile time when its inputs can't change,
such as a `static final` field. Then the work is done once, when the code is compiled, not once
per call. Like dead code elimination, it's legal, and a benchmark that does it measures nothing.

A stopwatch can't show you this, because a plain program's inputs usually *are* constants, and
there's nothing to compare against. JMH can, because a field of a `@State` object is read on
every call and the JIT may not assume it never changes:

```java
package demo;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class FoldBench {
    static final int LIMIT = 40;            // can never change
    int limit = 40;                         // a field in JMH's state object: read on every call

    static long sumOfSquares(int k) {       // 1² + 2² + ... + k²
        long sum = 0;
        for (int i = 1; i <= k; i++) sum += (long) i * i;
        return sum;
    }

    @Benchmark public long fromConstant() { return sumOfSquares(LIMIT); }
    @Benchmark public long fromState()    { return sumOfSquares(limit); }

    @Benchmark public void viaBlackhole(Blackhole bh) {
        bh.consume(sumOfSquares(limit));    // the other way to make a result escape
    }

    static final double X = 42.0;
    @Benchmark public double logOfConstant() { return Math.log(X); }
}
```

```text
Benchmark                Mode  Cnt   Score    Error  Units     (verified 2026-11-23; -f 1 -wi 2 -i 3,
FoldBench.fromConstant   avgt    3   0.363 ±  0.019  ns/op      1 s each, one run, this machine)
FoldBench.fromState      avgt    3  13.469 ±  0.532  ns/op
FoldBench.logOfConstant  avgt    3  13.570 ±  0.476  ns/op
FoldBench.viaBlackhole   avgt    3  14.214 ± 10.869  ns/op
```

Read it row by row:

- **`fromConstant`** returns its result, so dead code elimination can't delete it, and it still
  costs 0.36 ns, the price of an empty method (compare `LogBench.baseline` in T3). The JIT
  computed the sum of squares up to 40 once, at compile time, and the benchmark returns a stored
  number.
- **`fromState`** does the same work on a field, and costs 13.5 ns, about 37 times more. That's
  the loop, actually running.
- **`logOfConstant`** is the surprise: `Math.log` of a `static final` is **not** folded on JDK 26.
  It costs the same as the real `Math.log` in T3. Whether the JIT folds something depends on what
  it knows about the code, and that changes between methods and between JDK versions. **You can't
  predict it, so don't let it happen**: read inputs from `@State` fields, always, and then the
  question doesn't arise.
- **`viaBlackhole`** is the same work as `fromState`, consumed through a `Blackhole` instead of
  returned. The score is about the same, but look at the `Error` column: ±10.9 ns, almost as big
  as the score. A re-run gave 13.9 ± 2.4 ns, so the margin itself was noise: three iterations of
one second weren't enough for that one. A score without its
  error is half a result.

So the annotations on a JMH benchmark each answer one of the problems above. `@Warmup` handles
warm-up. **Returning the result** (or a `Blackhole`) handles dead code elimination. `@State` with a
non-final field handles constant folding. `@Fork` starts fresh JVMs.

### T8. Microbenchmark or whole program: pick the tool

JMH is for **microbenchmarks**: one method or loop, nanoseconds to microseconds, where the JIT's
tricks dominate the result. It isn't for everything.

[Doc 31](31-runtime-defaults.md) compares garbage collectors on `BlurLoop`, which runs for 15
seconds and does whole-program work. **JMH is for microbenchmarks, and that isn't one.** At that
scale, warm-up is a small part of the total, nothing can be deleted because the program's output
depends on it, and what you want to know is how the whole program behaves. Wall-clock time on the
whole program, repeated enough times to see the spread (T4), is the right tool there.

Knowing which situation you're in is the actual skill. Reaching for JMH to compare collectors is
as wrong as reaching for `currentTimeMillis` to compare two one-line methods.

## Practice

### What's in the repo

`ForkBlur.java:115-117` times the blur with `System.currentTimeMillis()` around one
`pool.invoke`. That's the repo's one stopwatch: one reading (T4), on a wall clock (T5), in a JVM
that may not have warmed up (T2). **A correction to the old version:** it named
`SimpleThreads.java:44` as a second. That line uses `currentTimeMillis()` as a timeout ("give up
after this long"), not to measure anything. And `RandomFileLines.java:32` uses it as a `Random`
seed, which is [doc 19](19-randomness-and-numerics.md)'s problem.

### Exercise 1: prove the problem to yourself

Before any tooling, in a scratch file:

```java
for (int run = 0; run < 5; run++) {
    long t0 = System.nanoTime(); long sum = 0;
    for (int i = 0; i < 1_000_000; i++) sum += i % 7;
    long t1 = System.nanoTime();
    System.out.println("run " + run + ": " + (t1 - t0) / 1000 + " us sum=" + sum);
}
```

Run it. Then take `sum` out of the `println` and run it again. Then run the first version with
`java -Xint`. You should see both effects, warm-up (T2) and deletion (T3), within five runs
(verified). Record your numbers for question 1.

**Done when:** you have all ten numbers (five with `sum` printed, five without) plus the `-Xint`
run written down in answer 1, and you can say which of T2 and T3 each change showed.

### Exercise 2: add JMH, as its own commit

Add both as `test` scope dependencies in `pom.xml`. JMH is the one dependency in this whole track
that isn't in the JDK:

```xml
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-core</artifactId>
    <version>1.37</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.openjdk.jmh</groupId>
    <artifactId>jmh-generator-annprocess</artifactId>
    <version>1.37</version>
    <scope>test</scope>
</dependency>
```

**A correction to the old version:** it stopped there. On JDK 26 that compiles, and then
nothing runs (verified):

```text
java ... org.openjdk.jmh.Main LogBench
java.lang.RuntimeException: ERROR: Unable to find the resource: /META-INF/BenchmarkList
```

The second dependency is an **annotation processor**: it generates the benchmark harness at
compile time (T6). Since JDK 23, `javac` no longer runs processors it merely finds on the class
path, and it doesn't warn you. Turn it back on in the `maven-compiler-plugin` configuration, next
to `<release>`:

```xml
<proc>full</proc>
```

With that, `target/test-classes/META-INF/BenchmarkList` appears, `mvn test` still reports 248
tests and 211 skipped (a benchmark class has no `@Test`, so Surefire ignores it), and the
benchmarks run (all verified on a scratch copy). To run them:

```sh
mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java -cp target/test-classes:target/classes:$(cat target/cp.txt) org.openjdk.jmh.Main -l     # list
java -cp target/test-classes:target/classes:$(cat target/cp.txt) org.openjdk.jmh.Main TallyBenchmark
```

Then run `./scripts/warm-cache.sh` again, so offline builds keep working with the new jars.

Commit the pom change on its own, with every test still green. It's a build change, and nobody
should have to find it inside a benchmark commit.

**Done when:** the pom change is one commit on its own, `mvn test` still reports 248 tests and 211
skipped, and `warm-cache.sh` has been re-run.

### Exercise 3: re-answer doc 14

[Doc 14](14-map-defaults-and-collectors.md) asks at what input size the `groupingBy` counter beats
the `merge` loop. **Finish doc 14 first.** `WordTally.tallyWithMerge` and `tallyWithGroupingBy`
throw `UnsupportedOperationException("TODO ...")` until you do, and JMH will report every
iteration as an error.

The shape, in `src/test/java`, in the same package as `WordTally`:

```java
@BenchmarkMode(Mode.AverageTime) @OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark) @Warmup(iterations = 3) @Measurement(iterations = 5) @Fork(2)
public class TallyBenchmark {
    @Param({"10", "1000", "100000"}) int size;
    List<String> words;
    @Setup public void setUp() { /* build the input: decide how many distinct words */ }
    @Benchmark public Map<String, Integer> mergeLoop()  { return WordTally.tallyWithMerge(words); }
    @Benchmark public Map<String, Long>    groupingBy() { return WordTally.tallyWithGroupingBy(words); }
}
```

Each annotation answers one of the problems in the theory. `@Warmup` handles warm-up (T2).
**Returning the result** handles dead code elimination (T3). `@State` with a non-final field
handles constant folding (T7). `@Fork(2)` starts fresh JVMs, so one benchmark can't leave the JIT
in a shape that flatters the next (T4).

Report the crossover, or report that there isn't one in that range. Either is a real answer.
"The stream is probably fine" isn't one. And say how many distinct words your input had, because
a map with 10 keys and a map with 100,000 are different benchmarks.

**Done when:** `TallyBenchmark` is committed and runs, and answer 2 states the crossover (or its
absence) with the scores, their error margins and the number of distinct words.

### Exercise 4: write the answers here

1. **The naive benchmark, five runs in one JVM**, with `sum` printed and without. All ten numbers.
   How big was the warmup effect, and what did removing `sum` do? _(fill in)_
2. **Doc 14's crossover**, measured, with the input described. _(fill in)_
3. **Go back to the timing claims you made in docs 06, 08, 14, 22 and 31.** Which of them do you
   no longer believe? _(fill in)_
4. **Which claim was right anyway?** Being wrong about the method and right about the answer is
   worth noticing. _(fill in)_
5. **The explain-it version.** Someone shows you a benchmark proving their rewrite is 40% faster.
   Name the first three things you ask. _(fill in)_

### Exercise 5: fill in "What got worse"

- _(fill this in)_

Prompts: JMH makes a measurement take minutes instead of seconds. What does that do to how often
you measure, and is a worse measurement you actually run better than a good one you don't?
`<proc>full</proc>` turns on every annotation processor on the class path, not just JMH's: is
that a risk, and how would you narrow it? `@Fork(2)` on a laptop doing anything else isn't
reproducible: would you gate a merge on a benchmark in CI?

### Exercise 6: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. A stopwatch around `doTheWork()` measures something other than `doTheWork()`. Name the three
   ways it differs. (T1)
2. Why is the first round of a timing loop slower than the rest, and what does `-Xint` show about
   it? (T2)
3. Five million `Math.sqrt` calls took 0 µs. What happened, and why is the JIT allowed to do it?
   (T3)
4. What are the two ways to stop JMH's JIT from deleting a benchmark's work? (T3, T7)
5. After warm-up, the same sort on the same data took between 1.8 and 3.3 ms. Name two causes of
   that spread. (T4)
6. Why does JMH run each benchmark in a fresh JVM? (T4, T6)
7. Why can't `currentTimeMillis` time `Math.log`, and why can't one pair of `nanoTime` readings
   either? (T5)
8. What does `-proc:full` do, and what happens on JDK 23+ without it? (T6)
9. `fromConstant` returned its result and still cost 0.36 ns. What did the JIT do, and how does a
   `@State` field prevent it? (T7)
10. `logOfConstant` *wasn't* folded. What does that tell you about predicting what the JIT does?
    (T7)
11. You want to compare two garbage collectors on a 15-second program. JMH or not, and why? (T8)

**Done when:** you can answer all eleven without looking.
