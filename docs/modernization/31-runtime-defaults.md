# 31. Runtime defaults: GC, AOT, JFR

**Java 21 → 26** · stage 20 of [the release-ordered track](INDEX.md) · **runnable, no tests** —
the deliverable is measurements and written answers

## Where it lands

**`concurrency/forkjoin/ForkBlur.java`**, the same workload
[doc 11](11-jvm-diagnostics.md) uses: allocates hard, splits across the common pool, CPU-bound.
Do [doc 11](11-jvm-diagnostics.md) first — it teaches the `jcmd` commands this stage assumes.

    mvn -q clean package
    java -cp target/classes org.mandfer.practices.concurrency.forkjoin.ForkBlur

**Nothing in this stage is a code change.** Every item is something that changed, or can change,
without you editing a line — which is exactly why it is worth a stage. These are the changes that
arrive with an upgrade whether or not anyone read the release notes.

## 1. Garbage collectors

What your JVM is actually doing right now:

    java -XX:+PrintFlagsFinal -version | grep -E 'UseG1GC|UseZGC|UseSerialGC|UseParallelGC'

On the JDK 25 in this VM: `UseG1GC = true {ergonomic}`. **"Ergonomic" means nobody chose it** —
the JVM picked it from the machine shape.

| Release | Change |
| --- | --- |
| 9 | G1 becomes the default (JEP 248), replacing Parallel |
| 21 | **Generational ZGC** (JEP 439) |
| 23 | Generational ZGC becomes ZGC's **only** mode (JEP 474) |
| 24 | **Generational Shenandoah** (JEP 404) |
| 26 | G1 throughput improved by reducing synchronization (JEP 522) |

All five collectors are accepted on this JDK — verified. Run the workload under each:

    java -XX:+UseG1GC       -cp target/classes org.mandfer.practices.concurrency.forkjoin.ForkBlur
    java -XX:+UseParallelGC -cp target/classes ...
    java -XX:+UseZGC        -cp target/classes ...
    java -XX:+UseSerialGC   -cp target/classes ...

**ForkBlur is CPU-bound and allocation-heavy, which makes it a genuinely informative subject**:
Parallel GC will probably beat G1 on total throughput, and that is the right result, not a
surprise. G1 optimises for pause time at a throughput cost. If your answer to "which collector"
is "G1, it is the default", this is the experiment that gives you a better one.

## 2. Compact object headers

    java -XX:+UseCompactObjectHeaders -cp target/classes ...

Every object header shrinks from **96 bits to 64**. On a heap full of small objects that is a
double-digit percentage of live-set size, for free.

| Release | Status |
| --- | --- |
| 24 | experimental (JEP 450) |
| 25 | product, opt-in (JEP 519) |
| 27 | **on by default** (JEP 534) |

Verified on this JDK: the flag is accepted and `UseCompactObjectHeaders = false {default}`.
**So it is available and off, here.** Measure it:

    jcmd <pid> GC.heap_info

with and without the flag, on the same image. That number is the most persuasive performance
argument in this whole document and it costs you one flag.

Note the ladder — experimental, product, default — is the **same four-step shape** as the removals
in [stage 19](30-upgrade-hazards.md), run in the opposite direction. The JDK introduces things the
way it retires them.

## 3. Ahead-of-time startup

| Release | Change |
| --- | --- |
| 24 | AOT class loading and linking (JEP 483) |
| 25 | AOT command-line ergonomics (JEP 514), AOT method profiling (JEP 515) |
| 26 | AOT object caching with any GC (JEP 516) |

JEP 514 is what made it usable — one flag instead of a two-step training run:

    java -XX:AOTCacheOutput=app.aot -cp target/classes org.mandfer...ForkBlur
    java -XX:AOTCache=app.aot       -cp target/classes org.mandfer...ForkBlur

Verified: `-XX:AOTMode` and `-XX:AOTCacheOutput` are accepted on this JDK.

**This is Project Leyden's first shipped piece**, and the target is JVM startup — the thing that
made Java a poor fit for serverless and CLI tools. `ForkBlur` runs for a minute, so AOT will
barely register: **the honest finding here is that this workload is the wrong one to measure it
with.** Say so. Then say what workload would show it.

## 4. JFR, since doc 11

[Doc 11](11-jvm-diagnostics.md) has the `jcmd` command table. Three additions since Java 21:

| Release | Change |
| --- | --- |
| 25 | **CPU-time profiling** (JEP 509, Linux) — samples by CPU time, not wall clock |
| 25 | Cooperative sampling (JEP 518) — lower-bias stack walking |
| 25 | Method timing and tracing (JEP 520) |

**JEP 509 is the one to care about.** The old profiler sampled on wall-clock time, so a thread
blocked on I/O looked expensive. CPU-time sampling attributes cost to code that is actually
running. If you have ever profiled a service and found the hot method was a socket read, that
is the bias this removes.

    jcmd <pid> JFR.start name=lab settings=profile duration=60s filename=lab.jfr
    jfr summary lab.jfr
    jfr print --events ObjectAllocationSample lab.jfr

## Write the answers here

1. **Four collectors, same image, same machine.** Record wall-clock for each. Which won, and does
   that match what you would have predicted? _(fill in)_
2. **Compact object headers**: `GC.heap_info` with and without. What was the difference, and would
   it change a hosting bill? _(fill in)_
3. **G1 is "ergonomic" on this machine.** What inputs did the JVM use to pick it, and what machine
   shape would make it choose differently? _(fill in)_
4. **AOT on `ForkBlur` shows almost nothing.** Why, and what would you measure instead? _(fill in)_
5. **The explain-it version.** _"Which GC should we use?"_ — answer in three sentences without
   saying "it depends", naming the one measurement you would take first. _(fill in)_

## What got worse

- _(fill this in)_

Prompts: compact object headers become the default in 27 — what could that break, given it changes
object layout? If generational ZGC replaced non-generational entirely in 23, what does that say
about how much tuning advice written in 2022 is still worth reading? An AOT cache is a file that
must match the JVM and the classpath — what happens in a container build when it does not?
