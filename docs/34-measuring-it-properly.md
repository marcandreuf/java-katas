# 34. Measuring it properly

**Version-independent** · stage 23 of [the release-ordered track](INDEX.md) · **the last stage,
and it invalidates some of your earlier answers**

## Why this is last

Five earlier stages ask you to make a performance claim:

| Stage | The claim it asks for |
| --- | --- |
| [06](06-virtual-threads.md) | a pool of 3 takes `tasks / 3 * blockMillis`, virtual threads take `blockMillis` |
| [08](08-memory-model.md) | `synchronized` vs `AtomicLong` vs `LongAdder` at a million increments a second |
| [14](14-map-defaults-and-collectors.md) | at what input size the stream counter beats the `merge` loop |
| [22](22-stream-gatherers.md) | whether `windowSliding` beats an index loop |
| [31](31-runtime-defaults.md) | four collectors, same workload, which won |

And the only timing idiom anywhere in this repo is this, from `ForkBlur.java:143-145` and
`SimpleThreads.java:38`:

    long startTime = System.currentTimeMillis();
    // ... work ...
    long endTime = System.currentTimeMillis();

**Every number you produced that way is suspect**, and this stage is about why. Go back and
re-read your own "what got worse" sections afterwards.

## Four reasons a hand-timed microbenchmark lies

1. **JIT warmup.** The first few thousand invocations run interpreted, then C1, then C2. A loop
   timed from cold measures the interpreter. This is the big one and it typically moves results by
   an order of magnitude, not a few percent.
2. **Dead code elimination.** If you do not consume the result, C2 is entitled to delete the work
   entirely. The classic outcome is a benchmark proving some operation takes 0.3ns — because it
   does not happen.
3. **Constant folding.** Benchmark a method on a `static final` input and C2 may compute it once at
   compile time. [Stage 22](33-preview-shelf.md)'s `StableValue` exists *because* constant folding
   is this powerful.
4. **On-stack replacement and loop unrolling** make the timed loop structurally different from the
   code you are trying to measure.

Add to that: `currentTimeMillis` has millisecond resolution and is wall-clock, so NTP can step it
backwards mid-measurement. **`System.nanoTime` is the one for intervals** — which is the correction
[doc 13](13-java-time.md) already asks you to make, where the newer `Instant.now()` is the *wrong*
answer.

## The exercise

### 1. Prove the problem to yourself

Before adding any tooling, write the naive benchmark and watch it misbehave:

    long t0 = System.nanoTime();
    long sum = 0;
    for (int i = 0; i < 1_000_000; i++) sum += i % 7;
    long t1 = System.nanoTime();

Run it. Then run it in a loop five times in one JVM and watch the number fall as the JIT warms up.
Then delete `sum` from the output and watch it fall further. **Those two observations are the
entire case for JMH** and they cost you four minutes.

### 2. Add JMH

Add to `pom.xml` — this is the one dependency in this whole track that is not in the JDK:

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

**Run `./scripts/warm-cache.sh` again afterwards** if you want offline builds to keep working —
it is the one thing in this repo that reaches the network.

The shape:

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @State(Scope.Benchmark)
    @Warmup(iterations = 3)
    @Measurement(iterations = 5)
    @Fork(2)
    public class TallyBenchmark {

        @Param({"10", "1000", "100000"})
        int size;

        List<String> words;

        @Setup public void setUp() { /* build the input */ }

        @Benchmark public Map<String, Integer> mergeLoop()  { return WordTally.tallyWithMerge(words); }
        @Benchmark public Map<String, Long>    groupingBy() { return WordTally.tallyWithGroupingBy(words); }
    }

Four annotations carry the weight: **`@Warmup`** (solves 1), **returning the result** or using a
`Blackhole` (solves 2), **`@State`** (solves 3), **`@Fork`** (runs fresh JVMs, so one benchmark
cannot leave the JIT in a shape that flatters the next).

### 3. Re-answer stage 14

[Doc 14](14-map-defaults-and-collectors.md) asks at what input size `groupingBy` beats the `merge`
loop. **Answer it with `@Param({"10", "1000", "100000"})` and report the crossover**, or report
that there isn't one in that range. Either is a real answer. "The stream is probably fine" is not.

### 4. The one that does not need JMH

[Stage 20](31-runtime-defaults.md) compares garbage collectors on `ForkBlur`, which runs for a
minute. **JMH is for microbenchmarks, and a one-minute workload is not one.** Wall-clock on a
whole-program run is the correct tool there, with enough repetitions to see the variance.

Knowing which of the two situations you are in is the actual skill. Reaching for JMH to compare
collectors is as wrong as reaching for `currentTimeMillis` to compare two one-line methods.

## Write the answers here

1. **The naive benchmark, five runs in one JVM.** Record all five numbers. How big was the warmup
   effect? _(fill in)_
2. **Stage 14's crossover**, measured. _(fill in)_
3. **Go back and re-read your "what got worse" sections for stages 06, 08, 14, 22 and 31.** Which
   of those claims do you no longer believe? _(fill in)_
4. **Which claim was right anyway?** Being wrong about the method and right about the answer is
   worth noticing. _(fill in)_
5. **The explain-it version.** Somebody shows you a benchmark proving their rewrite is 40% faster.
   Name the first three things you ask. _(fill in)_

## What got worse

- _(fill this in)_

Prompts: JMH makes a measurement take minutes instead of seconds — what does that do to how often
you measure, and is a worse measurement you actually run better than a good one you do not?
`@Fork(2)` means a benchmark run is not reproducible on a laptop doing anything else — how would
you run these in CI, and would you gate a merge on one? When is "it is obviously faster" good
enough, and how do you tell before you have measured?
