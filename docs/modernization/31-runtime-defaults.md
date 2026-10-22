# 31. Runtime defaults: GC, compact headers, AOT, JFR

**Java 9 → 26** · stage 20 of [the release-ordered track](INDEX.md) · **runnable, no tests and no
code change**: the deliverable is measurements and written answers

Do [doc 11](11-jvm-diagnostics.md) first. It teaches the `jcmd` commands, and it has the two
small programs this stage reuses: `MakeImage` (the repo has no `original.JPG`) and `BlurLoop`.

## What this stage is actually about

Five words, defined once:

- **ergonomic** is how `-XX:+PrintFlagsFinal` labels a flag that the JVM set itself, from the
  machine it found. `{default}` means nobody changed it; `{command line}` means you did.
- a **collector** (GC) is the part of the JVM that frees memory. G1, Parallel, Serial, ZGC and
  Shenandoah all ship in JDK 26, and they trade **throughput** (total work done) against **pause
  time** (how long the application stops).
- an **object header** is the bookkeeping at the front of every object: its class and its lock
  and hash bits. **Compact object headers** shrink it from 12 bytes to 8.
- an **AOT cache** (ahead-of-time) is a file recorded during a training run, holding classes
  already loaded and linked, so the next start skips that work.
- a **JFR event** is one record in a Flight Recorder file ([doc 11](11-jvm-diagnostics.md)). A
  `.jfc` settings file (`default`, `profile`) decides which events are on.

That gives one rule, and everything below follows from it: **a default is a guess about your
workload, so measure on a workload that would show the difference before you change one.** Every
item in this stage arrives with an upgrade whether anyone reads the release notes or not, and the
old version of this doc picked a workload that couldn't show three of the four.

## The worked example: compact object headers, and where the bytes go

Five million objects in an array, heap measured after a full GC (verified, JDK 26):

| Object | default | `-XX:+UseCompactObjectHeaders` |
| --- | --- | --- |
| `new Object()` | 16 bytes | **8 bytes** |
| `record One(int x)` | 16 bytes | **16 bytes** |
| `record Two(int x, int y)` | 24 bytes | **16 bytes** |

The middle row is the lesson. Objects are padded to a multiple of 8 bytes. `One` is a 12-byte
header plus a 4-byte `int`, which is exactly 16. With an 8-byte header it's 12, padded back up to
16, so **it saves nothing**. `Two` is 20 padded to 24 by default, and 16 compact: a third smaller.
Whether the flag helps depends on the shapes of *your* objects, which is the rule above.

And a measurement trap I fell into on the way, which [doc 34](34-measuring-it-properly.md) is
about: the first version of this program reported **-4.2 bytes per object**. The array wasn't
used after the loop, so the JIT treated it as dead and the GC collected it before the second
measurement. One extra line that reads the array afterwards fixed it.

On JDK 26 the flag is accepted and off: `UseCompactObjectHeaders = false {product lp64_product}
{default}` (verified). It was experimental in 24 (JEP 450) and became a product option in 25 (JEP
519). The INDEX says 27 turns it on by default (JEP 534). There's no JDK 27 on this machine, so
that last step isn't verified here.

## Part one: the workload, and what it can and can't show

**A correction to the old version:** it said `ForkBlur` "allocates hard, splits across the
common pool" and "runs for a minute". None of the three is true:

- **It runs for under a second.** On a 4000×3000 image the whole program takes 0.9–1.0 s, and the
  blur 160–220 ms (verified, 10 cores, under load).
- **It doesn't use the common pool.** `blur()` creates a new `ForkJoinPool` on every call
  (`ForkBlur.java:113`). [Doc 11](11-jvm-diagnostics.md) question 2 is about what that does over
  time.
- **It barely collects.** With `-Xlog:gc`, one whole run (verified):

| Collector | collections | total pause |
| --- | --- | --- |
| G1 | 4 | 5 ms |
| Parallel | 3 | 26 ms |
| Serial | 7 | 63 ms |
| ZGC | **0** | none |

ZGC never ran at all. On a sub-second program the GC is a rounding error, and "which collector
won" is answered by noise. So use `BlurLoop` from doc 11, and give it a count: keep
`PrintStream out = System.out;` before the `setOut` line, count the loop, and print the count on
`out` at the end:

    out.println("blurs=" + n);

Then, for each collector, the same 15 seconds and the same image:

    java -XX:+UseG1GC -Xlog:gc:file=g1.log -cp target/classes:/tmp/lab BlurLoop 15

Record blurs, collections and total pause for G1, Parallel, Serial and ZGC. That's question 1. It's
also why the old prediction ("Parallel will probably beat G1 on throughput") is something to
**test**, not repeat. Run it before you read anyone's answer.

Two flag facts verified on JDK 26, both of which old tuning advice gets wrong:

- `-XX:-ZGenerational` is now `Unrecognized VM option`. Generational ZGC (JEP 439, 21) became the
  only ZGC in 23 (JEP 474), and 26 no longer accepts the switch at all.
- `-XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational` starts without
  `-XX:+UnlockExperimentalVMOptions`. It was experimental in 24 (JEP 404).

## Part two: compact headers on the workload

    jcmd <pid> GC.heap_info          # on BlurLoop, with and without the flag

**A correction to the old version:** it called this "the most persuasive performance argument in
this whole document". **On `ForkBlur` it changes nothing**: 91 MB live with the flag, 91 MB
without (verified, one blur, after a full GC). The live set is two 12-megapixel `int[]` arrays.
Two headers among 96 MB of pixels can't move the number. The worked example above shows the
workload that would: millions of small objects. Say which of the services you work on looks more
like which.

## Part three: AOT cache

    jar cf /tmp/lab/app.jar -C target/classes .
    java -XX:AOTCacheOutput=/tmp/lab/app.aot -cp /tmp/lab/app.jar org.mandfer.practices.concurrency.forkjoin.ForkBlur
    java -XX:AOTCache=/tmp/lab/app.aot       -cp /tmp/lab/app.jar org.mandfer.practices.concurrency.forkjoin.ForkBlur

**A correction to the old version:** its commands used `-cp target/classes`. That fails (verified):

    [error][aot] Error: non-empty directory '.../target/classes'
    Cannot have non-empty directory in paths

The training run needs **jars** on the class path. With a jar it writes a 13.7 MB cache (verified).
Then the four things to know, all verified:

- **A stale cache doesn't stop the program.** `touch app.jar` and run again: `timestamp has
  changed`, `shared class paths mismatch`, and the program runs with **exit code 0**, slower.
  The same happens when the cache file is missing. Add `-XX:AOTMode=on` and a stale cache becomes
  `Unable to use AOT cache.`, exit code 1. Decide which one a container build should use.
- **The GC is part of the match.** A cache trained under G1 works with Serial and fails under ZGC:
  `The saved state of UseCompressedOops and UseCompressedClassPointers is different from runtime`.
  A cache trained under ZGC works under ZGC. JEP 516 (26) is what lets the cached objects be used
  with any GC. The cache still has to be trained with the same pointer settings.
- **On `ForkBlur` the gain is small, but not zero.** Five runs each: 865–930 ms without the
  cache, 760–905 ms with it (under load, so read it as roughly 10–15%).
- **On a program that only starts up, it's large.** A one-line stream program: 36–39 ms without,
  22–24 ms with (verified, seven runs each). Startup is what JEP 483 (24) and JEP 514 (25) are
  for. Question 4 asks you to say why the blur shows less.

## Part four: JFR, CPU-time sampling

JEP 509 (25) adds `jdk.CPUTimeSample`: samples by CPU time, so a thread blocked in a socket read
stops looking expensive. **A correction to the old version:** it implied the `profile` settings
record it. They don't (verified). In JDK 26's `lib/jfr/profile.jfc` the event is
`enabled=false`, and `jfr metadata` marks it `@Experimental`. A `settings=profile` recording of
`ForkBlur` has 0 `jdk.CPUTimeSample` events. You have to enable it by name:

    java '-XX:StartFlightRecording=jdk.CPUTimeSample#enabled=true,filename=cpu.jfr' -cp ... ForkBlur
    jfr summary cpu.jfr          # jdk.CPUTimeSample 99, jdk.CPUTimeSamplesLost 3

The same recording made with `settings=profile` has 80 `jdk.ExecutionSample` events: the
wall-clock sampler. Compare the two on the same run. `jdk.MethodTiming` and `jdk.MethodTrace`
(JEP 520) exist in `jfr metadata` and are also off unless you configure them.

## Write the answers here

1. **Four collectors on `BlurLoop`, 15 seconds each.** Blurs, collections, total pause. Which
   won on throughput, which on pause, and does either match what you'd have predicted?
   _(fill in)_
2. **Compact headers**: name an object in a service you know that would shrink, and one that
   wouldn't. Use the padding rule from the worked example. _(fill in)_
3. **G1 is `{ergonomic}` on this machine.** Find the machine shapes that make JDK 26 pick a
   different collector, using `-XX:ActiveProcessorCount` and the `systemd-run` memory limits from
   [doc 11](11-jvm-diagnostics.md). Then check whether `-XX:MaxRAM` does the same thing. (It's
   not obvious.) The INDEX says JEP 523 changes this in 27. _(fill in)_
4. **AOT on `ForkBlur` shows a little; on a trivial program it shows a lot.** Why, and what
   kind of service would see the second number? _(fill in)_
5. **The explain-it version.** _"Which GC should we use?"_ Answer in three sentences without
   saying "it depends", naming the one measurement you'd take first. _(fill in)_

## What got worse

- _(fill this in)_

Prompts: compact headers change object layout. What could that break, and who would notice
first? `-XX:-ZGenerational` used to be a valid flag and now stops the JVM from starting: how many
of your service's start scripts carry a flag nobody remembers adding? An AOT cache that silently
doesn't match costs you its benefit with exit code 0. Is that the right default for a
performance feature?
