# 31. Runtime defaults: GC, compact headers, AOT, JFR

**Java 9 → 26** · stage 20 of [the release-ordered track](INDEX.md) · **runnable, no tests and no
code change**: the deliverable is measurements and written answers

Do [doc 11](11-jvm-diagnostics.md) first. It teaches the `jcmd` commands, and it has the two
small programs this stage reuses: `MakeImage` (the repo has no `original.JPG`) and `BlurLoop`.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line. Exercises 1 to 4 are measurements; exercise 5 is
where the answers go.

| Exercise | What you do | Theory it uses | Feeds question |
| --- | --- | --- | --- |
| [1. The workload](#exercise-1-the-workload-and-what-it-can-and-cant-show) | Count blurs in `BlurLoop`, then run four collectors for 15 s each. | T1, T2 | 1, 3, 5 |
| [2. Compact headers](#exercise-2-compact-headers-on-the-workload) | `GC.heap_info` on `BlurLoop`, with and without the flag. | T3 | 2 |
| [3. AOT cache](#exercise-3-aot-cache-on-forkblur) | Train a cache for `ForkBlur`, break it, time it. | T4 | 4 |
| [4. JFR CPU-time sampling](#exercise-4-jfr-cpu-time-sampling-on-forkblur) | Record both samplers on the same `ForkBlur` run. | T5 | none |
| [5. Write the answers here](#exercise-5-write-the-answers-here) | Five written answers. | all | all |
| [6. What got worse](#exercise-6-fill-in-what-got-worse) | Write down what these defaults cost. | all | none |
| [7. Review questions](#exercise-7-review-questions) | Answer without looking, then check against the theory. | all | none |

> **Predict before you measure.** Question 1 asks whether the collector results match what you'd
> have predicted. Write your prediction down before you read T2, and certainly before
> exercise 1. Nothing in the theory runs `BlurLoop`, so it can't give you the answer, but it can
> bias the guess.

## Theory

Read the five sections in order. Here is the whole story in one paragraph, so you know where it's
going.

> Every JVM starts with hundreds of settings you never chose. Most of them are guesses the JVM
> makes from the machine it finds, and a guess is only as good as the workload it was made for
> (T1). The biggest guess is the garbage collector, and collectors trade total work done against
> how long the program stops, so "the best one" only means something for a given workload (T2).
> Some new defaults shrink every object, but whether that saves anything depends on the shape of
> *your* objects (T3). Some make startup faster, if a file recorded on an earlier run still
> matches, and a mismatch is silent (T4). And some exist but are off until you ask for them by
> name (T5). The rule under all five: **a default is a guess about your workload, so measure on a
> workload that would show the difference before you change one.**

Every program below is complete. Run it with `java Name.java` unless the section shows another
command. Outputs marked *verified* were run on JDK 26 (Temurin 26.0.2), on a 10-core machine with
23 GB of memory, unless the block says otherwise. Anything about memory, cores or the collector
picked depends on that machine.

### T1. Ergonomics: the settings the JVM chose for you

You start a JVM with no flags. It still has a heap size, a collector, a number of GC threads and a
JIT configuration. Nobody typed them: the JVM looked at the cores and memory it could see and
picked. That's called **ergonomics**, and `-XX:+PrintFlagsFinal` shows which settings it made:

```sh
java -XX:+PrintFlagsFinal -version | grep -E ' (UseG1GC|UseParallelGC|UseSerialGC|UseZGC|MaxHeapSize|ActiveProcessorCount|UseCompactObjectHeaders) '
```

```text
      int ActiveProcessorCount                     = -1                                        {product} {default}
   size_t MaxHeapSize                              = 6299844608                                {product} {ergonomic}
     bool UseCompactObjectHeaders                  = false                          {product lp64_product} {default}
     bool UseG1GC                                  = true                                      {product} {ergonomic}
     bool UseParallelGC                            = false                                     {product} {default}
     bool UseSerialGC                              = false                                     {product} {default}
     bool UseZGC                                   = false                                     {product} {default}
                                                         (verified 2026-11-23, 10 cores, 23 GB)
```

Read the last column:

- **`{default}`** means nobody changed it. It's the value compiled into the JVM.
- **`{ergonomic}`** means the JVM set it itself, from the machine it found. Here it picked G1, and
  a maximum heap of about 6 GB: a quarter of 23 GB.
- **`{command line}`** means you did.

`{ergonomic}` is the column to worry about, because it changes when the machine changes, and the
machine in production is rarely the one on your desk. [Doc 10, T8](10-sequenced-collections-and-httpclient.md#t8-which-garbage-collector-you-get)
shows one machine shape that makes JDK 26 pick a different collector, and
[doc 11, T5](11-jvm-diagnostics.md#t5-what-limits-does-it-think-it-has-flags-and-containers) shows
how a container's limits change what the JVM sees. Question 3 asks you to find the rest.

### T2. Collectors: throughput against pause time

A **collector** (GC) is the part of the JVM that frees memory. G1, Parallel, Serial, ZGC and
Shenandoah all ship in JDK 26, and they don't compete on one number. They trade two things:

- **throughput**: how much of the machine's time goes to your program rather than to collecting.
- **pause time**: how long your program stops while the collector works.

A batch job that runs for an hour cares about throughput and doesn't mind a 200 ms stop. A web
service that must answer in 50 ms cares about pauses and will pay some throughput to keep them
short. There's no collector that wins both, which is why "which GC is fastest?" has no answer
without a workload.

`-Xlog:gc` prints one line per collection, and that's all you need to measure both:

```java
import java.util.ArrayList;
import java.util.List;

public class Churn {
    public static void main(String[] args) {
        List<int[]> live = new ArrayList<>();
        long garbage = 0;
        for (int i = 0; i < 2_000_000; i++) {
            int[] chunk = new int[256];               // 1 KB, dead almost at once
            garbage += chunk.length;
            if (i % 1_000 == 0) live.add(chunk);      // keep one in a thousand: a small live set
        }
        System.out.println("allocated about " + garbage * 4 / 1_000_000 + " MB, kept " + live.size() + " chunks");
    }
}
```

```sh
java -Xlog:gc Churn.java
```

```text
...
[0.750s][info][gc] GC(13) Pause Young (Normal) (G1 Evacuation Pause) 324M->4M(540M) 2.321ms
[0.799s][info][gc] GC(14) Pause Young (Normal) (G1 Evacuation Pause) 324M->5M(540M) 1.878ms
[0.849s][info][gc] GC(15) Pause Young (Normal) (G1 Evacuation Pause) 325M->5M(540M) 1.632ms
[0.901s][info][gc] GC(16) Pause Young (Normal) (G1 Evacuation Pause) 325M->5M(540M) 1.294ms
allocated about 2048 MB, kept 2000 chunks
                                         (verified 2026-11-23, one run, trimmed; numbers vary)
```

Each line is one collection: its number, what kind (`Pause Young`), heap before → after (total),
and how long the program stopped. **Count the lines and add the last column** and you have the
two numbers exercise 1 asks for. (The source launcher compiles `Churn.java` inside the same JVM
first, so the earliest lines in a full log are the compiler's garbage, not the program's.)

Two flag facts that old tuning advice gets wrong:

```sh
java -XX:-ZGenerational -version                                   # JDK 26
/usr/lib/jvm/java-25-openjdk-amd64/bin/java -XX:-ZGenerational -version   # JDK 25
java -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational -version
```

```text
Unrecognized VM option 'ZGenerational'                   (JDK 26, verified 2026-11-23)
Error: Could not create the Java Virtual Machine.

OpenJDK 64-Bit Server VM warning: Ignoring option ZGenerational; support was removed in 24.0
openjdk version "25.0.5.1" 2026-11-17                    (JDK 25, verified 2026-11-23)

openjdk version "26.0.2.1" 2026-08-18                    (JDK 26, Shenandoah: starts)
```

- **`-XX:-ZGenerational` stops JDK 26 from starting.** Generational ZGC (JEP 439, 21) became the
  only ZGC in 23 (JEP 474). JDK 25 still started, with a warning nobody reads; 26 doesn't accept
  the switch at all (verified). A start script carrying it worked yesterday and fails after the
  upgrade.
- **Generational Shenandoah no longer needs `-XX:+UnlockExperimentalVMOptions`.** It was
  experimental in 24 (JEP 404) (verified: it starts on 26 without the unlock flag).

### T3. Object headers, and where the bytes go

Every object starts with an **object header**: the bookkeeping the JVM needs for it, its class and
its lock and hash bits. On JDK 26 it's 12 bytes by default. **Compact object headers**
(`-XX:+UseCompactObjectHeaders`) shrink it to 8.

Four bytes per object sounds like a free saving. It isn't always, because of one more rule:
**objects are padded to a multiple of 8 bytes.** This program measures it: five million objects in
an array, heap measured after a full GC before and after.

```java
import java.util.function.IntFunction;

public class Headers {
    record One(int x) { }
    record Two(int x, int y) { }

    static final int N = 5_000_000;

    static long usedAfterGc() {
        System.gc();
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    static void measure(String label, IntFunction<Object> make) {
        Object[] keep = new Object[N];                // allocate the array before the baseline
        long before = usedAfterGc();
        for (int i = 0; i < N; i++) keep[i] = make.apply(i);
        long after = usedAfterGc();
        // Read the array afterwards, or the JIT may decide it's dead and the GC frees it early.
        if (keep[N - 1] == null) throw new AssertionError();
        System.out.printf("%-13s %5.1f bytes per object%n", label, (after - before) / (double) N);
    }

    public static void main(String[] args) {
        measure("Object", i -> new Object());
        measure("One(int)", i -> new One(i));
        measure("Two(int,int)", i -> new Two(i, i));
    }
}
```

```sh
java -Xmx2g Headers.java
java -Xmx2g -XX:+UseCompactObjectHeaders Headers.java
```

```text
Object         15.9 bytes per object                     (default, verified 2026-11-23)
One(int)       16.0 bytes per object
Two(int,int)   24.0 bytes per object

Object          8.0 bytes per object                     (-XX:+UseCompactObjectHeaders)
One(int)       16.0 bytes per object
Two(int,int)   16.0 bytes per object
```

That's the table the old version of this doc verified, reproduced:

| Object | default | `-XX:+UseCompactObjectHeaders` |
| --- | --- | --- |
| `new Object()` | 16 bytes | **8 bytes** |
| `record One(int x)` | 16 bytes | **16 bytes** |
| `record Two(int x, int y)` | 24 bytes | **16 bytes** |

The middle row is the lesson. `One` is a 12-byte header plus a 4-byte `int`, which is exactly 16.
With an 8-byte header it's 12, padded back up to 16, so **it saves nothing**. `Two` is 20 padded to
24 by default, and 16 compact: a third smaller. Whether the flag helps depends on the shapes of
*your* objects, which is the rule above. Question 2 asks you to apply it to a service you know.

And a measurement trap from the first version of this program, which
[doc 34](34-measuring-it-properly.md) is about: it reported **-4.2 bytes per object**. The array
wasn't used after the loop, so the JIT treated it as dead and the GC collected it before the second
measurement. One extra line that reads the array afterwards fixed it. That's the line with the
comment above.

On JDK 26 the flag is accepted and off: `UseCompactObjectHeaders = false {product lp64_product}
{default}` (verified, and in T1's output). JDK 25 shows the same line (verified). It was
experimental in 24 (JEP 450) and became a product option in 25 (JEP 519). The INDEX says 27 turns
it on by default (JEP 534). There's no JDK 27 on this machine, so that last step isn't verified
here.

### T4. The AOT cache: startup work done once

Every time a JVM starts, it finds, loads and links the same classes as last time: the JDK's own,
then yours. For a program that runs for hours, that's nothing. For one that starts, does a little
and exits, it can be most of the run.

An **AOT cache** (ahead-of-time) is a file recorded during a **training run**, holding classes
already loaded and linked, so the next start skips that work. JEP 483 (24) added it, and JEP 514
(25) made recording it one step: `-XX:AOTCacheOutput`. The class path has to be **jars**, not
directories (exercise 3 shows the error).

```java
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Words {
    public static void main(String[] args) {
        System.out.println(Stream.of("pear", "fig", "apple")
                .sorted()
                .collect(Collectors.joining(", ")));
    }
}
```

```sh
javac -d classes Words.java && jar cf words.jar -C classes .
java -XX:AOTCacheOutput=words.aot -cp words.jar Words     # training run: records the cache
java -XX:AOTCache=words.aot -Xlog:aot -cp words.jar Words # later runs: use it
```

```text
apple, fig, pear                     (verified 2026-11-23, trimmed; the size varies slightly by run)
...
AOTCache creation is complete: words.aot 10670080 bytes

[0.003s][info][aot] Opened AOT cache words.aot.
...
apple, fig, pear
```

A 10.7 MB file for a three-line program, because most of what it caches is the JDK's own classes.

**The cache is only valid for the exact class path it was trained on.** Change the jar, even just
its timestamp, and the next run can't use it. What happens then is the important part:

```sh
touch words.jar
java -XX:AOTCache=words.aot -cp words.jar Words; echo "exit=$?"
```

```text
[0.005s][warning][aot] This file is not the one used while building the AOT cache: 'words.jar', timestamp has changed
[0.005s][error  ][aot] An error has occurred while processing the AOT cache. Run with -Xlog:aot for details.
[0.005s][error  ][aot] shared class paths mismatch (hint: enable -Xlog:class+path=info to diagnose the failure)
[0.006s][error  ][aot] Unable to map shared spaces
apple, fig, pear
exit=0                                                   (verified 2026-11-23)
```

Three `error` lines, then the program runs anyway and exits 0, without the cache. That's a
deliberate default: a performance feature shouldn't stop a program from starting. It also means a
build that ships a stale cache gets no benefit and no failure. Exercise 3 shows the flag that
changes that, and asks you which a container build should use.

How much a valid cache saves isn't shown here on purpose. Question 4 is about why the answer is
different for different programs, and exercise 3 is where you measure it.

### T5. JFR events that exist but are off

A **JFR event** is one record in a Flight Recorder file ([doc 11, T4](11-jvm-diagnostics.md#t4-what-happened-over-time-flight-recorder)).
A `.jfc` settings file (`default`, `profile`) decides which events are on. New releases add
events, and **a new event is not necessarily a recorded one**: it can be in the JDK and off in both
settings files.

JEP 509 (25) adds `jdk.CPUTimeSample`, a sampler driven by CPU time instead of wall-clock time.
JDK 26's `lib/jfr/profile.jfc` ships it switched off:

```text
    <event name="jdk.CPUTimeSample">
      <setting name="enabled">false</setting>
      <setting name="throttle">10ms</setting>
      <setting name="stackTrace">true</setting>      (verified 2026-11-23, JDK 26 profile.jfc)
```

So you turn it on by name, on top of a settings file. This program keeps one core busy for two
seconds while another thread sleeps:

```java
public class Spin {
    public static void main(String[] args) throws InterruptedException {
        Thread sleeper = Thread.ofPlatform().name("sleeper").start(() -> {
            try { Thread.sleep(2_000); } catch (InterruptedException e) { }
        });
        long end = System.nanoTime() + 2_000_000_000L;   // keep one core busy for 2 s
        double x = 0;
        while (System.nanoTime() < end) x += Math.sqrt(x + 1);
        sleeper.join();
        System.out.println("done " + (x > 0));
    }
}
```

```sh
javac -d spin Spin.java
java '-XX:StartFlightRecording=settings=profile,filename=p.jfr' -cp spin Spin
java '-XX:StartFlightRecording=jdk.CPUTimeSample#enabled=true,settings=profile,filename=spin.jfr' -cp spin Spin
jfr summary p.jfr    | grep -E 'jdk\.(CPUTimeSample|ExecutionSample) '
jfr summary spin.jfr | grep -E 'jdk\.(CPUTimeSample|CPUTimeSamplesLost|ExecutionSample) '
```

```text
 jdk.ExecutionSample                       197          1970     (settings=profile only)
 jdk.CPUTimeSample                           0             0

 jdk.CPUTimeSample                         200          3000     (enabled by name)
 jdk.ExecutionSample                       196          1960
 jdk.CPUTimeSamplesLost                      0             0
                               (verified 2026-11-23, one run each; counts vary by a few)
```

With `settings=profile` alone: zero CPU-time samples. Enabled by name: 200, every one of them on
`main` (verified with `jfr print`); the sleeping thread, which used no CPU, never appears. The
wall-clock sampler, `jdk.ExecutionSample`, ran in both. `jfr metadata` marks `jdk.CPUTimeSample`
`@Experimental` (verified). `jdk.MethodTiming` and `jdk.MethodTrace` (JEP 520) also exist in `jfr
metadata` and are also off unless you configure them.

## Practice

### Exercise 1: the workload, and what it can and can't show

**A correction to the old version:** it said `ForkBlur` "allocates hard, splits across the
common pool" and "runs for a minute". None of the three is true:

- **It runs for under a second.** On a 4000×3000 image the whole program takes 0.84–0.94 s, and the
  blur 158–180 ms (verified, three runs, 10 cores).
- **It doesn't use the common pool.** `blur()` creates a new `ForkJoinPool` on every call
  (`ForkBlur.java:113`). [Doc 11](11-jvm-diagnostics.md) question 2 is about what that does over
  time.
- **It barely collects.** With `-Xlog:gc` (T2), one whole run (verified):

| Collector | collections | total pause |
| --- | --- | --- |
| G1 | 4 | 6 ms |
| Parallel | 3 | 26 ms |
| Serial | 7 | 66 ms |
| ZGC | **0** | none |

ZGC never ran at all. On a sub-second program the GC is a rounding error, and "which collector
won" is answered by noise. So use `BlurLoop` from doc 11, and give it a count: keep
`PrintStream out = System.out;` before the `setOut` line, count the loop, and print the count on
`out` at the end:

```java
out.println("blurs=" + n);
```

Then, for each collector, the same 15 seconds and the same image:

```sh
java -XX:+UseG1GC -Xlog:gc:file=g1.log -cp target/classes:/tmp/lab BlurLoop 15
```

Record blurs, collections and total pause for G1, Parallel, Serial and ZGC. That's question 1. It's
also why the old prediction ("Parallel will probably beat G1 on throughput") is something to
**test**, not repeat. Run it before you read anyone's answer.

Then question 3: G1 was `{ergonomic}` in T1. Find the machine shapes that make it something else.

**Done when:** `BlurLoop` prints a blur count, you have blurs, collections and total pause for all
four collectors from 15-second runs on the same image, and your prediction was written down first.

### Exercise 2: compact headers on the workload

```sh
jcmd <pid> GC.heap_info          # on BlurLoop, with and without the flag
```

**A correction to the old version:** it called this "the most persuasive performance argument in
this whole document". **On `ForkBlur` it changes nothing**: 91 MB live with the flag, 91 MB
without (verified, one blur, after a full GC). The live set is two 12-megapixel `int[]` arrays.
Two headers among 96 MB of pixels can't move the number. T3 shows the workload that would:
millions of small objects. Say which of the services you work on looks more like which.

**Done when:** you have the live heap with and without the flag on `BlurLoop`, and question 2
names one object that would shrink and one that wouldn't, by T3's padding rule.

### Exercise 3: AOT cache on `ForkBlur`

```sh
jar cf /tmp/lab/app.jar -C target/classes .
java -XX:AOTCacheOutput=/tmp/lab/app.aot -cp /tmp/lab/app.jar org.mandfer.practices.concurrency.forkjoin.ForkBlur
java -XX:AOTCache=/tmp/lab/app.aot       -cp /tmp/lab/app.jar org.mandfer.practices.concurrency.forkjoin.ForkBlur
```

**A correction to the old version:** its commands used `-cp target/classes`. That fails (verified):

```text
[error][aot] Error: non-empty directory '.../target/classes'
Cannot have non-empty directory in paths
```

The training run needs **jars** on the class path (T4). With a jar it writes a 13.7 MB cache
(verified). Then the four things to know, all verified:

- **A stale cache doesn't stop the program.** `touch app.jar` and run again: `timestamp has
  changed`, `shared class paths mismatch`, and the program runs with **exit code 0**, slower.
  The same happens when the cache file is missing. Add `-XX:AOTMode=on` and a stale cache becomes
  `Unable to use AOT cache.`, exit code 1. Decide which one a container build should use.
- **The GC is part of the match.** A cache trained under G1 works with Serial and fails under ZGC:
  `The saved state of UseCompressedOops and UseCompressedClassPointers is different from runtime`.
  A cache trained under ZGC works under ZGC. JEP 516 (26) is what lets the cached objects be used
  with any GC. The cache still has to be trained with the same pointer settings.
- **On `ForkBlur` it saves about 130 ms, every run.** Seven runs each: 840–923 ms without the
  cache, 745–763 ms with it (verified). The ranges don't overlap. That's about 15% of the run.
- **On a program that only starts up, it saves about 40%.** A one-line stream program: 36–37 ms
  without, 21–27 ms with (verified, seven runs each). That's only about 14 ms, a tenth of what
  `ForkBlur` saved. Startup is what JEP 483 (24) and JEP 514 (25) are for, and question 4 asks
  which of the two numbers is the one that matters.

**Done when:** you've reproduced the stale-cache run with and without `-XX:AOTMode=on`, timed
`ForkBlur` seven times each way, and question 4 is answered.

### Exercise 4: JFR CPU-time sampling on `ForkBlur`

JEP 509 (25) adds `jdk.CPUTimeSample` (T5). **A correction to the old version:** it implied the
`profile` settings record it. They don't (verified). In JDK 26's `lib/jfr/profile.jfc` the event
is `enabled=false`, and `jfr metadata` marks it `@Experimental`. A `settings=profile` recording of
`ForkBlur` has 0 `jdk.CPUTimeSample` events. You have to enable it by name:

```sh
java '-XX:StartFlightRecording=jdk.CPUTimeSample#enabled=true,filename=cpu.jfr' -cp ... ForkBlur
jfr summary cpu.jfr          # jdk.CPUTimeSample 99, jdk.CPUTimeSamplesLost 3
```

The same recording made with `settings=profile` has 80 `jdk.ExecutionSample` events: the
wall-clock sampler. Compare the two on the same run. `jdk.MethodTiming` and `jdk.MethodTrace`
(JEP 520) exist in `jfr metadata` and are also off unless you configure them.

**Done when:** you have one `ForkBlur` recording with both `jdk.CPUTimeSample` and
`jdk.ExecutionSample` in it, and you've compared their counts.

### Exercise 5: write the answers here

1. **Four collectors on `BlurLoop`, 15 seconds each.** Blurs, collections, total pause. Which
   won on throughput, which on pause, and does either match what you'd have predicted?
   _(fill in)_
2. **Compact headers**: name an object in a service you know that would shrink, and one that
   wouldn't. Use the padding rule from T3. _(fill in)_
3. **G1 is `{ergonomic}` on this machine.** Find the machine shapes that make JDK 26 pick a
   different collector, using `-XX:ActiveProcessorCount` and the `systemd-run` memory limits from
   [doc 11](11-jvm-diagnostics.md). Then check whether `-XX:MaxRAM` does the same thing. (It's
   not obvious.) The INDEX says JEP 523 changes this in 27. _(fill in)_
4. **AOT saved `ForkBlur` more milliseconds, and the trivial program a bigger percentage.** Why
   is that, which number would you put in front of someone deciding whether to adopt it, and what
   kind of service would see the second number? _(fill in)_
5. **The explain-it version.** _"Which GC should we use?"_ Answer in three sentences without
   saying "it depends", naming the one measurement you'd take first. _(fill in)_

### Exercise 6: fill in "What got worse"

- _(fill this in)_

Prompts: compact headers change object layout. What could that break, and who would notice
first? `-XX:-ZGenerational` used to be a valid flag and now stops the JVM from starting: how many
of your service's start scripts carry a flag nobody remembers adding? An AOT cache that silently
doesn't match costs you its benefit with exit code 0. Is that the right default for a
performance feature?

### Exercise 7: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.

1. In `-XX:+PrintFlagsFinal` output, what's the difference between `{default}`, `{ergonomic}`
   and `{command line}`, and which one should worry you before a deploy? (T1)
2. Why can't "which collector is fastest?" be answered without a workload? Name the two things
   collectors trade. (T2)
3. In a `-Xlog:gc` line, where are the pause time and the heap before and after? (T2)
4. What happens on JDK 26 if a start script still passes `-XX:-ZGenerational`? And on JDK 25?
   (T2)
5. A record with one `int` field: how big is it with and without compact headers, and why is the
   answer the same? (T3)
6. A measurement says objects take a negative number of bytes. What's the likely cause? (T3)
7. What does an AOT cache hold, and why must the class path be jars? (T4)
8. A cache doesn't match the jar any more. What does the program do by default, and what exit
   code does it return? (T4)
9. Is `jdk.CPUTimeSample` recorded by `settings=profile` on JDK 26? How do you turn it on? (T5)

**Done when:** you can answer all nine without looking.
