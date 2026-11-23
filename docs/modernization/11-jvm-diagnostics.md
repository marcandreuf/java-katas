# 11. JVM production diagnostics

**JDK 7+** (`jcmd`) · **JDK 11** (Flight Recorder free and open) · concept stage 11 of
[the concept order](00-start-here.md) · no build change

**No TODOs and no tests.** The deliverable is the written answers in exercise 2. Everything works
offline, against a process on your own machine.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. The workload](#exercise-1-set-up-the-workload) | Make an input image and a driver that keeps `ForkBlur` running long enough to look at. Both outside the repo. | T1 | None. |
| [2. The five questions](#exercise-2-answer-the-five-questions) | Point the tools at the running loop and write down what they show. | T1–T6 | None. |
| [3. Review questions](#exercise-3-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the six sections in order: each one builds on the one before. Here is the whole story in one
paragraph, so you know where it's going.

> Reading the source tells you what a program *should* do. A running JVM can tell you what it
> *is* doing, and the two often differ: the old version of this doc was written from reading
> `ForkBlur.java` and got two of its five questions wrong. So the rule is to ask the process
> first, and `jcmd` is how you ask (T1). The first question is usually "what is every thread doing
> right now?", and a thread dump answers it (T2). The next is "what is on the heap?" (T3). Both are
> snapshots of one instant; to see what happened *over time*, such as who allocates the most, you
> record a flight (T4). And before you trust any of those numbers, check what limits the JVM thinks
> it has, because inside a container they aren't the machine's (T5). T6 collects every command on
> one page.

The sample programs below are small *targets*: complete programs that do one thing slowly enough
to look at. Save each as the class name plus `.java`, run it with `java Name.java`, then point the
tools at it from a second terminal. Outputs marked *verified* were run on JDK 26, on a 10-core,
23 GB machine. Long outputs are trimmed, and `...` marks where.

### T1. Ask the process: `jcmd` and the pid

Every running JVM is an operating-system process with a **pid** (process id). `jcmd` is the one
tool that talks to all of them. Run it with no arguments and it lists every JVM you own, with its
pid and main class:

```sh
jcmd
```

```text
14007 jdk.jcmd/sun.tools.jcmd.JCmd                                         (verified 2026-11-23)
13944 jdk.compiler/com.sun.tools.javac.launcher.SourceLauncher Busy.java 30
```

Two things to notice. `jcmd` lists itself, because it's a JVM too. And a program started with
`java Name.java` shows up as `SourceLauncher`, the tool that compiled and ran it, with your file
name as its argument.

From then on, every question is `jcmd <pid> <command>`. `jcmd <pid> help` lists the commands that
JVM supports. The ones this doc uses are in T6.

That gives one rule, and everything below follows from it:

> **Ask the running process before you believe the source.**

Each tool answers one question, and the skill is knowing which question you're asking.

### T2. What is it doing right now: the thread dump

A **thread dump** (`jcmd <pid> Thread.print`) is every thread's name, state and stack at one
instant. It's the "what is it doing *right now*" tool, and the first one to reach for when a
program is stuck or slow.

To read one, you need the four states a thread is usually in. This target puts one thread in
each:

```java
public class Busy {
    static final Object LOCK = new Object();
    static final Object SIGNAL = new Object();

    public static void main(String[] args) throws InterruptedException {
        Thread holder = new Thread(() -> {
            synchronized (LOCK) {                       // takes LOCK and keeps it
                long x = 0;
                while (true) x++;                       // computing: RUNNABLE
            }
        }, "holder");
        Thread blocked = new Thread(() -> {
            synchronized (LOCK) { }                     // can't get LOCK: BLOCKED
        }, "blocked");
        Thread waiter = new Thread(() -> {
            synchronized (SIGNAL) {
                try { SIGNAL.wait(); } catch (InterruptedException e) { }   // WAITING
            }
        }, "waiter");
        Thread sleeper = new Thread(() -> {
            try { Thread.sleep(600_000); } catch (InterruptedException e) { } // TIMED_WAITING
        }, "sleeper");

        holder.setDaemon(true);
        blocked.setDaemon(true);
        waiter.setDaemon(true);
        sleeper.setDaemon(true);
        holder.start();
        Thread.sleep(100);                              // let holder take LOCK first
        blocked.start();
        waiter.start();
        sleeper.start();

        System.out.println("Busy running; pid " + ProcessHandle.current().pid());
        Thread.sleep(Long.parseLong(args[0]) * 1000);   // stay alive long enough to look at
    }
}
```

(The empty `catch` blocks are acceptable only in a throwaway target; [doc 07](07-classic-concurrency.md)
T3 says why.)

```sh
java Busy.java 30 &          # stays alive for 30 s
jcmd                         # find its pid
jcmd <pid> Thread.print
```

```text
"holder" #30 [13972] daemon prio=5 os_prio=0 cpu=3903.25ms elapsed=3.90s ... runnable    (verified 2026-11-23)
   java.lang.Thread.State: RUNNABLE
	at Busy.lambda$main$0(Busy.java:9)
	- locked <0x000000068a48bd48> (a java.lang.Object)
...
"blocked" #31 [13973] daemon prio=5 os_prio=0 cpu=0.20ms elapsed=3.80s ... waiting for monitor entry
   java.lang.Thread.State: BLOCKED (on object monitor)
	at Busy.lambda$main$1(Busy.java:13)
	- waiting to lock <0x000000068a48bd48> (a java.lang.Object)
...
"waiter" #32 [13974] daemon prio=5 os_prio=0 cpu=0.16ms elapsed=3.80s ... in Object.wait()
   java.lang.Thread.State: WAITING (on object monitor)
	at java.lang.Object.wait0(java.base@26.0.2.1/Native Method)
	- waiting on <0x000000068a48bd58> (a java.lang.Object)
...
"sleeper" #33 [13975] daemon prio=5 os_prio=0 cpu=0.24ms elapsed=3.80s ... waiting on condition
   java.lang.Thread.State: TIMED_WAITING (sleeping)
	at java.lang.Thread.sleepNanos0(java.base@26.0.2.1/Native Method)
...
```

How to read it:

| State | What the thread is doing | What to look for next |
| --- | --- | --- |
| `RUNNABLE` | running on a CPU, or ready to | `cpu=` keeps growing between two dumps: it's computing |
| `BLOCKED` | waiting to *enter* a `synchronized` block | `waiting to lock <address>`: find who has `locked <address>` |
| `WAITING` | parked in `wait()`, `join()`, `take()` with no timeout | what it's `waiting on`, and who would wake it |
| `TIMED_WAITING` | the same, with a timeout, or in `sleep()` | usually harmless; it'll wake on its own |

The addresses are the useful part. `holder` says `locked <0x...bd48>`, and `blocked` says
`waiting to lock <0x...bd48>`: the same object, so you can follow the chain from the thread that's
stuck to the thread that's in the way. **Name your threads**, too: `"holder"` tells you more than
`"Thread-0"`, and in a real dump there are dozens.

One dump is one instant. Take two a few seconds apart: a thread that's in the same place with the
same `cpu=` in both is stuck; one whose `cpu=` grew is busy.

### T3. What is on the heap: `GC.heap_info` and the class histogram

The thread dump says what the threads are doing. The heap commands say what they've *made*.
Against the same `Busy` target:

```sh
jcmd <pid> GC.heap_info
```

```text
garbage-first heap   total reserved 6152192K, committed 49152K, used 17954K ...          (verified 2026-11-23)
 region size 4M, 4 eden (16M), 1 survivor (4M), 1 old (4M), 0 humongous (0M), 6 free (24M)
```

`garbage-first` is G1, the default collector. It splits the heap into equal **regions** (4 MB
each here) and labels each one by what it holds: **eden** for new objects, **survivor** for ones
that lived through a collection, **old** for long-lived ones, and a few other kinds. *Reserved* is
the most it may ever use; *committed* is what it has taken from the OS so far; *used* is what's
live or not yet collected.

```sh
jcmd <pid> GC.class_histogram | head
```

```text
 num     #instances         #bytes  class name (module)                                   (verified 2026-11-23)
-------------------------------------------------------
   1:         17115         896896  [B (java.base@26.0.2.1)
   2:          3505         451288  java.lang.Class (java.base@26.0.2.1)
   3:         16955         406920  java.lang.String (java.base@26.0.2.1)
   4:          2530         232968  [Ljava.lang.Object; (java.base@26.0.2.1)
   5:          2461         196624  [J (java.base@26.0.2.1)
...
```

The histogram counts every object on the heap by class, biggest first. The names in brackets are
the JVM's spelling of array types: `[B` is `byte[]`, `[J` is `long[]`, `[Ljava.lang.Object;` is
`Object[]`. (A `String`'s characters live in a `byte[]`, which is why those two rows travel
together.)

Both commands are snapshots, like the thread dump. They tell you what's on the heap *now*, not who
put it there. For "who", you need T4. And if you want to dig through the heap object by object
later, `jcmd <pid> GC.heap_dump /tmp/lab.hprof` writes all of it to a file (13 MB for a small
target, verified) that JDK Mission Control or Eclipse MAT can open.

### T4. What happened over time: Flight Recorder

Snapshots miss everything between them. **Flight Recorder** (JFR) is the opposite: it records
events from inside the running JVM (allocations, locks, GC, pinning, CPU samples) into a `.jfr`
file, cheaply enough to leave on in production. It's been free and open since JDK 11.

One event type matters most for "who allocates": an **allocation sample** is a JFR event recording
that some code allocated an object of some class, with a stack trace and a weight. JFR doesn't
record every allocation (that would be far too slow), it samples them. Add enough samples up and
you know who allocates.

Here's a target with two allocation sites you can name in advance:

```java
import java.util.ArrayList;
import java.util.List;

public class Allocator {
    static String label(int i) {
        return "order-" + i;                            // a new String every call
    }

    static List<String> batch(int size) {
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < size; i++) labels.add(label(i));
        return labels;
    }

    public static void main(String[] args) {
        long end = System.nanoTime() + Long.parseLong(args[0]) * 1_000_000_000L;
        long total = 0;
        while (System.nanoTime() < end) {
            total += batch(10_000).size();              // allocate, then throw it all away
        }
        System.out.println("labels made: " + total);
    }
}
```

There are two ways to record. Start the recording with the program:

```sh
java -XX:StartFlightRecording=filename=alloc.jfr,settings=profile Allocator.java 10
```

or attach to one that's already running, which is what you do in production:

```sh
jcmd <pid> JFR.start name=lab settings=profile duration=5s filename=lab.jfr
jcmd <pid> JFR.check
```

```text
Recording 1: name=lab duration=5s (running)                                   (verified 2026-11-23)
```

(A relative `filename` is relative to the *target's* working directory, not yours. After the
duration, `JFR.check` says `No available recordings.` and the file is there.)

`settings=profile` records more detail than the default; it's what you want for a lab. Then read
the file from a terminal: these tools ship with the JDK. Start with what's in it:

```sh
jfr summary alloc.jfr
```

```text
 Duration: 11 s                                                               (verified 2026-11-23)

 Event Type                              Count  Size (bytes)
=============================================================
 jdk.GCPhaseParallel                     29748        780388
 jdk.PromoteObjectInNewPLAB               4174         67039
 jdk.ObjectAllocationSample               3159         49658
...
 jdk.ExecutionSample                       916         10073
...
```

`jfr view` gives a ready-made summary of allocations. Look at what it says, and then at what it
doesn't:

```sh
jfr view allocation-by-site alloc.jfr
```

```text
Method                                                           Allocation Pressure   (verified 2026-11-23)
---------------------------------------------------------------- -------------------
java.lang.StringConcatHelper$Concat1.concat(int)                              38.67%
jdk.internal.misc.Unsafe.allocateUninitializedArray(Class, int)               36.51%
java.util.Arrays.copyOf(Object[], int)                                        24.78%
...
```

**Not one of those methods is in `Allocator`.** A "site" is the top frame of the stack: the method
that executed the `new`. In real code that's almost always inside the JDK: the string
concatenation helper, `Arrays.copyOf` growing the `ArrayList`. The question you care about is
which of *your* methods called it, and that's further down the stack. `--stack-depth` shows more
of it:

```sh
jfr print --events jdk.ObjectAllocationSample --stack-depth 8 alloc.jfr
```

```text
jdk.ObjectAllocationSample {                                                  (verified 2026-11-23)
  startTime = 10:14:33.822 (2026-11-23)
  objectClass = java.lang.String (classLoader = bootstrap)
  weight = 399.2 kB
  eventThread = "main" (javaThreadId = 3)
  stackTrace = [
    java.lang.StringConcatHelper$Concat1.concat(int) line: 111
    Allocator.label(int) line: 6
    Allocator.batch(int) line: 11
    Allocator.main(String[]) line: 19
    ...
  ]
}
...
```

Now the chain is visible: `label` line 6 made the `String`, through the JDK's concat helper.
Counting the first `Allocator` frame of every sample gives the answer you predicted: `label` for
the `String`s and their `byte[]`s (2274 samples), `batch` for the `Object[]` behind the growing
`ArrayList` (700). The **weight** is how many bytes each sample stands for; sum the weights, not
just the counts, when sites allocate objects of very different sizes.

`jfr view allocation-by-class alloc.jfr` gives the same picture by class (`String` 38.67%,
`byte[]` 36.53%, `Object[]` 24.78%). If you have JDK Mission Control, it opens `.jfr` files and
draws all of this; the terminal tools are enough for this doc.

### T5. What limits does it think it has: flags and containers

Every number in T2 to T4 depends on how the JVM sized itself: how big the heap may grow, how many
GC threads it runs, how many threads a default pool gets. You didn't set any of that. The JVM
chose it, from what it could see. `jcmd <pid> VM.flags` shows the choice (trimmed, verified
against `Busy`):

```text
-XX:CICompilerCount=4 -XX:ConcGCThreads=2 ... -XX:MaxHeapSize=6299844608 ... -XX:+UseG1GC ...
```

The catch is the word *see*. A **container limit** is a memory or CPU cap set by a cgroup (which
is what Docker and Kubernetes use). The JVM reads it and sizes itself to the limit, not to the
machine. That's the worked example of this doc, answered end to end.

**What does `Runtime.getRuntime().maxMemory()` report, and why?** A three-line program that prints
it:

```java
public class Limits {
    public static void main(String[] args) {
        Runtime rt = Runtime.getRuntime();
        System.out.println("maxMemory: " + rt.maxMemory() / (1024 * 1024) + " MB");
        System.out.println("availableProcessors: " + rt.availableProcessors());
    }
}
```

Run three ways (verified, on a 10-core, 23 GB machine; re-run 2026-11-23 with the same numbers):

| Where | Memory the JVM sees | `maxMemory()` | `availableProcessors()` |
| --- | --- | --- | --- |
| the host, no limit | 23 GB | **6008 MB** | 10 |
| cgroup limit 1 GB | 1 GB | **255 MB** | 2 |
| cgroup limit 256 MB | 256 MB | **125 MB** | 2 |

```sh
java Limits.java
systemd-run --user --scope -p MemoryMax=1G   -p CPUQuota=200% java Limits.java
systemd-run --user --scope -p MemoryMax=256M -p CPUQuota=200% java Limits.java
```

`systemd-run` puts the JVM in a cgroup exactly as a container would. **No Docker needed**, and
`-XshowSettings:system` confirms the JVM saw it:

```text
Operating System Metrics:                                                     (verified 2026-11-23)
    Provider: cgroupv2
    Effective CPU Count: 2
...
    Memory Limit: 256.00M
...
```

(`docker run --memory=256m --cpus=2` gives the same result if you have a JDK image locally.)

**Why:** with no `-Xmx`, the default maximum heap is a percentage of the memory the JVM *sees*,
and inside a cgroup it sees the limit, not the machine. `java -XX:+PrintFlagsFinal -version` shows
the two percentages: `MaxRAMPercentage = 25` and `MinRAMPercentage = 50`. 25% of 23 GB is about
6 GB, and 25% of 1 GB is 255 MB. At 256 MB the JVM switches to the larger percentage, because 25%
would leave a uselessly small heap, and 125 MB is about half. And `CPUQuota=200%` became
`availableProcessors() == 2`, which also sizes the GC thread count and every default thread
pool, including the fork/join pool `ForkBlur` uses.

**So what?** A service tuned on a laptop and then deployed into a 256 MB container gets a heap
about 50 times smaller, without any configuration having changed. That's the sentence to be able
to say in an interview.

### T6. The command list

Every row verified against a running JVM on JDK 26:

| Question | Command | Section |
| --- | --- | --- |
| Which JVMs are running, and their pids | `jcmd` | T1 |
| What is this process doing right now | `jcmd <pid> Thread.print` | T2 |
| What is on the heap | `jcmd <pid> GC.heap_info` then `jcmd <pid> GC.class_histogram` | T3 |
| Heap dump for later | `jcmd <pid> GC.heap_dump /tmp/lab.hprof` | T3 |
| Record a flight | `jcmd <pid> JFR.start name=lab settings=profile duration=60s filename=lab.jfr` | T4 |
| Check on it | `jcmd <pid> JFR.check` | T4 |
| What flags is it running with | `jcmd <pid> VM.flags` | T5 |
| What does it think its limits are | `jcmd <pid> VM.info`, look for the container section | T5 |

And for reading a recording, all shipped with the JDK:

```sh
jfr summary lab.jfr
jfr view allocation-by-site lab.jfr
jfr print --events jdk.ObjectAllocationSample --stack-depth 3 lab.jfr
```

(Or start the recording at launch instead of attaching:
`java -XX:StartFlightRecording=filename=lab.jfr,settings=profile ...`.) Open `lab.jfr` in JDK
Mission Control if you have it.

## Practice

### Exercise 1: set up the workload

`concurrency/forkjoin/ForkBlur.java` is the subject. Two things stop it from working as the old
doc said.

**There's no input image.** `ForkBlur.main` reads `original.JPG` from the working directory, which
isn't in the repo (verified):

```text
Exception in thread "main" javax.imageio.IIOException: Can't read input file!
```

Make one. Save this as `MakeImage.java` **outside the repo** (it's a tool, not exercise code) and
run it as a single-file program:

```java
import java.awt.image.BufferedImage; import java.io.File; import java.util.Random;
import javax.imageio.ImageIO;
public class MakeImage {
    public static void main(String[] a) throws Exception {
        int w = Integer.parseInt(a[0]), h = Integer.parseInt(a[1]);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random r = new Random(1);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setRGB(x, y, r.nextInt());
        ImageIO.write(img, "jpg", new File("original.JPG"));
    }
}
```

```sh
java MakeImage.java 4000 3000      # 12 megapixels, about 7 MB
```

**It finishes too fast to look at.** On that image the blur takes **176 ms**, and the whole program
**0.85 s** (verified, 10 cores). "Give it a large enough image that it runs for a minute" would
need billions of pixels. Loop it instead. `ForkBlur.blur(BufferedImage)` is `public static`, so a
small driver, also kept outside the repo, can call it for as long as you need:

```java
import java.awt.image.BufferedImage; import java.io.*; import javax.imageio.ImageIO;
import org.mandfer.practices.concurrency.forkjoin.ForkBlur;
public class BlurLoop {
    public static void main(String[] a) throws Exception {
        BufferedImage img = ImageIO.read(new File("original.JPG"));
        long end = System.nanoTime() + Long.parseLong(a[0]) * 1_000_000_000L;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));   // blur() prints
        while (System.nanoTime() < end) ForkBlur.blur(img);
    }
}
```

```sh
mvn -q clean package
javac -cp target/classes -d /tmp/lab BlurLoop.java
java -cp target/classes:/tmp/lab BlurLoop 60      # run for 60 s, from where original.JPG is
```

Then, in a second terminal, `jcmd` to find its pid (T1).

**Done when:** `BlurLoop 60` runs for a minute without an exception, `jcmd` lists it, and neither
helper file is inside the repo.

### Exercise 2: answer the five questions

Point the tools from T6 at the running loop. Question 4 is answered in T5, so you can see what an
answer looks like. The other four are yours.

1. **Which allocation site dominates in `ForkBlur`?** _(fill in)_ Find it from the JFR allocation
   events, using `--stack-depth`, without reading the source (T4). **A correction to the old
   version:** it said `ForkBlur` "allocates hard". The blur itself, `computeDirectly()`, allocates
   nothing at all. Find out what does, and then look at `jcmd <pid> GC.heap_info` (T3): the word
   *humongous* in its output is G1's name for the answer.
2. **How many fork/join worker threads exist ten seconds into the loop, how many pools are they
   in, and what in the code decided that?** _(fill in)_ **A correction to the old version:** it
   asked about "the common pool". Count the `commonPool` threads in a thread dump (T2): there are
   **none**. Read the names of the threads that *are* there, then read `ForkBlur.java:113`. The
   number will surprise you, and it changes the longer the loop runs. Explain why, and say
   what a long-running service calling `blur()` would eventually do.
3. **Deadlock: what does the thread dump call that state, and where in the output is the
   cycle?** _(fill in)_ **A correction to the old version:** it said to run `MyDeadlock` until it
   hangs. [Doc 08](08-memory-model.md) part two shows it can't: both live threads take the locks
   in the same order. Use the opposite-order version that doc 08 restores. Until doc 08 fixes its
   package, the class is `com.java2novice.algos.MyDeadlock`, not
   `org.mandfer.practices.concurrency.MyDeadlock`.
4. **Container-aware limits.** Answered in T5. Run it on your own machine and write down your
   numbers, which will differ.
5. **The explain-it version**: answer _"how would you debug a slow service in production"_ in four
   sentences, naming the tool you reach for first and what it would have to show you before you
   reached for the second. _(fill in)_ Use what questions 1 and 2 taught you. Neither answer was
   in the place the source suggested.

**Done when:** all five have an answer written in, each one based on what a tool showed you, and
your own numbers for question 4.

### Exercise 3: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. You started a program with `java Name.java`. What name does `jcmd` list it under? (T1)
2. Name the four thread states from `Busy`, and say which one means "trying to get into a
   `synchronized` block". (T2)
3. In a thread dump, how do you find which thread a `BLOCKED` thread is waiting for? (T2)
4. One thread dump shows a thread `RUNNABLE` in the same method twice. What else do you compare
   between two dumps to tell "busy" from "stuck"? (T2)
5. What do `[B` and `[Ljava.lang.Object;` mean in a class histogram? (T3)
6. Why can't the class histogram tell you which method allocated the most? What can? (T3, T4)
7. `jfr view allocation-by-site` names only JDK methods. Why, and how do you find your own code?
   (T4)
8. What is the weight of an allocation sample, and when do counts alone mislead you? (T4)
9. A JVM with no `-Xmx` runs in a 1 GB container on a 64 GB host. Roughly what is `maxMemory()`,
   and why? (T5)
10. `CPUQuota=200%` on a 10-core machine. What does `availableProcessors()` return, and name two
    things that number sizes. (T5)

**Done when:** you can answer all ten without looking.
