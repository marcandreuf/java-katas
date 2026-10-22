# 34. Measuring it properly

**Version-independent** (JMH 1.37, checked on JDK 26) · stage 23 of
[the release-ordered track](INDEX.md) · **build change: two test-scope dependencies and one
compiler setting** · the last stage, and it invalidates some of your earlier answers

## What this stage is actually about

Five words, defined once:

- a **microbenchmark** times a small piece of code, a method or a loop, in isolation. Its result
  is nanoseconds per operation, and almost everything about the JVM conspires to make that number
  wrong.
- **warmup** is the time before the JIT has compiled the code you care about. The JVM starts in
  the interpreter, then compiles hot code with C1 and later C2. Code timed from cold is timing the
  interpreter.
- **dead code elimination** is the JIT deleting work whose result nobody uses. It's legal, and
  it's the most common reason a benchmark reports an impossible number.
- **constant folding** is the JIT computing a value at compile time when its inputs can't change,
  such as a `static final` field. Then the work is done once, not once per call.
- **JMH** (Java Microbenchmark Harness) is the OpenJDK tool that handles all of the above: it
  warms up, makes results escape so they can't be deleted, reads inputs from non-final fields, and
  runs each benchmark in a fresh JVM (a **fork**).

That gives one rule, and everything below follows from it: **a measurement is only evidence if
you know what the JIT did to the code you measured.** A stopwatch around a loop measures whatever
was left after the JIT finished with it, and that may be nothing.

## The worked example: a benchmark of nothing

Not the exercise. Three JMH benchmarks on `Math.log`, run on JDK 26 (verified, 3 warmup and 3
measured iterations, one fork):

    @Benchmark public void   baseline()  { }
    @Benchmark public void   discarded() { Math.log(x); }
    @Benchmark public double returned()  { return Math.log(x); }

    Benchmark           Mode  Cnt   Score   Error  Units
    LogBench.baseline   avgt    3   0.364 ± 0.082  ns/op
    LogBench.discarded  avgt    3   0.363 ± 0.024  ns/op
    LogBench.returned   avgt    3  13.616 ± 0.255  ns/op

`discarded` costs the same as an empty method. **The logarithm isn't slow or fast: it isn't
there.** The JIT saw the result go nowhere and deleted the call. Returning the value makes JMH
consume it, and the real cost appears, about 37 times higher. (These ran on a machine with other
JVMs busy, so trust the ratio, not the nanoseconds.)

It isn't only microbenchmarks. While writing [doc 31](31-runtime-defaults.md), a program that
measured heap use before and after filling an array reported **-4.2 bytes per object**. The array
wasn't read after the loop, so the GC was allowed to collect it before the second measurement.
One line that used the array afterwards fixed it (verified). Same mistake, different tool.

## Where the old timing idiom is

`ForkBlur.java:115-117` times the blur with `System.currentTimeMillis()` around one
`pool.invoke`. That's the repo's one stopwatch. **A correction to the old version:** it named
`SimpleThreads.java:38` as a second. That line uses `currentTimeMillis()` as a timeout ("give up
after this long"), not to measure anything. And `RandomFileLines.java:32` uses it as a `Random`
seed, which is [doc 19](19-randomness-and-numerics.md)'s problem.

`currentTimeMillis()` is wall-clock time: its javadoc says the granularity depends on the OS, and
the clock can be adjusted while you measure. `System.nanoTime()`'s javadoc says it's for elapsed
time and nothing else. For intervals, use `nanoTime`.

## Part one: prove the problem to yourself

Before any tooling, in a scratch file:

    for (int run = 0; run < 5; run++) {
        long t0 = System.nanoTime(); long sum = 0;
        for (int i = 0; i < 1_000_000; i++) sum += i % 7;
        long t1 = System.nanoTime();
        System.out.println("run " + run + ": " + (t1 - t0) / 1000 + " us sum=" + sum);
    }

Run it. Then take `sum` out of the `println` and run it again. Then run the first version with
`java -Xint`. You should see both effects, warmup and deletion, within five runs (verified). Record
your numbers for question 1.

## Part two: add JMH, as its own commit

Add both as `test` scope dependencies in `pom.xml`. JMH is the one dependency in this whole track
that isn't in the JDK:

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

**A correction to the old version:** it stopped there. On JDK 26 that compiles, and then
nothing runs (verified):

    java ... org.openjdk.jmh.Main LogBench
    java.lang.RuntimeException: ERROR: Unable to find the resource: /META-INF/BenchmarkList

The second dependency is an **annotation processor**: it generates the benchmark harness at
compile time. Since JDK 23, `javac` no longer runs processors it merely finds on the class path,
and it doesn't warn you. Turn it back on in the `maven-compiler-plugin` configuration, next to
`<release>`:

    <proc>full</proc>

With that, `target/test-classes/META-INF/BenchmarkList` appears, `mvn test` still reports 248
tests and 211 skipped (a benchmark class has no `@Test`, so Surefire ignores it), and the
benchmarks run (all verified on a scratch copy). To run them:

    mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
    java -cp target/test-classes:target/classes:$(cat target/cp.txt) org.openjdk.jmh.Main -l     # list
    java -cp target/test-classes:target/classes:$(cat target/cp.txt) org.openjdk.jmh.Main TallyBenchmark

Then run `./scripts/warm-cache.sh` again, so offline builds keep working with the new jars.

Commit the pom change on its own, with every test still green. It's a build change, and nobody
should have to find it inside a benchmark commit.

## Part three: re-answer doc 14

[Doc 14](14-map-defaults-and-collectors.md) asks at what input size the `groupingBy` counter beats
the `merge` loop. **Finish doc 14 first.** `WordTally.tallyWithMerge` and `tallyWithGroupingBy`
throw `UnsupportedOperationException("TODO ...")` until you do, and JMH will report every
iteration as an error.

The shape, in `src/test/java`, in the same package as `WordTally`:

    @BenchmarkMode(Mode.AverageTime) @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @State(Scope.Benchmark) @Warmup(iterations = 3) @Measurement(iterations = 5) @Fork(2)
    public class TallyBenchmark {
        @Param({"10", "1000", "100000"}) int size;
        List<String> words;
        @Setup public void setUp() { /* build the input: decide how many distinct words */ }
        @Benchmark public Map<String, Integer> mergeLoop()  { return WordTally.tallyWithMerge(words); }
        @Benchmark public Map<String, Long>    groupingBy() { return WordTally.tallyWithGroupingBy(words); }
    }

Each annotation answers one of the problems above. `@Warmup` handles warmup. **Returning the
result** handles dead code elimination. `@State` with a non-final field handles constant folding.
`@Fork(2)` starts fresh JVMs, so one benchmark can't leave the JIT in a shape that flatters the
next.

Report the crossover, or report that there isn't one in that range. Either is a real answer.
"The stream is probably fine" isn't one. And say how many distinct words your input had, because
a map with 10 keys and a map with 100,000 are different benchmarks.

## Part four: the one that doesn't need JMH

[Doc 31](31-runtime-defaults.md) compares collectors on `BlurLoop`, which runs for 15 seconds
and does whole-program work. **JMH is for microbenchmarks, and that isn't one.** Wall-clock time
on the whole program, repeated enough times to see the spread, is the right tool there.

Knowing which situation you're in is the actual skill. Reaching for JMH to compare collectors is
as wrong as reaching for `currentTimeMillis` to compare two one-line methods.

## Write the answers here

1. **The naive benchmark, five runs in one JVM**, with `sum` printed and without. All ten numbers.
   How big was the warmup effect, and what did removing `sum` do? _(fill in)_
2. **Doc 14's crossover**, measured, with the input described. _(fill in)_
3. **Go back to the timing claims you made in docs 06, 08, 14, 22 and 31.** Which of them do you
   no longer believe? _(fill in)_
4. **Which claim was right anyway?** Being wrong about the method and right about the answer is
   worth noticing. _(fill in)_
5. **The explain-it version.** Someone shows you a benchmark proving their rewrite is 40% faster.
   Name the first three things you ask. _(fill in)_

## What got worse

- _(fill this in)_

Prompts: JMH makes a measurement take minutes instead of seconds. What does that do to how often
you measure, and is a worse measurement you actually run better than a good one you don't?
`<proc>full</proc>` turns on every annotation processor on the class path, not just JMH's: is
that a risk, and how would you narrow it? `@Fork(2)` on a laptop doing anything else isn't
reproducible: would you gate a merge on a benchmark in CI?
