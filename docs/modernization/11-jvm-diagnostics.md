# 11. JVM production diagnostics

**JDK 7+** (`jcmd`) · **JDK 11** (Flight Recorder free and open) · concept stage 11 of
[the concept order](00-start-here.md) · no build change

**No TODOs and no tests.** The deliverable is the written answers at the end. Everything works
offline, against a process on your own machine.

## What this stage is actually about

Five words, defined once:

- a **pid** is the operating-system id of a running JVM. `jcmd` with no arguments lists every JVM
  you own, with its pid and main class.
- a **thread dump** (`jcmd <pid> Thread.print`) is every thread's name, state and stack at one
  instant. It is the "what is it doing *right now*" tool, and it is the one that finds deadlocks.
- **Flight Recorder** (JFR) records events from inside the running JVM (allocations, locks, GC,
  pinning, CPU samples) into a `.jfr` file, cheaply enough to leave on in production.
- an **allocation sample** is a JFR event recording that some code allocated an object of some
  class, with a stack trace and a weight. Add enough of them up and you know who allocates.
- a **container limit** is a memory or CPU cap set by a cgroup (which is what Docker and
  Kubernetes use). The JVM reads it and sizes itself to the limit, not to the machine.

That gives one rule, and everything below follows from it: **ask the running process before you
believe the source.** The old version of this doc was written from reading `ForkBlur.java`, and
it got two of its five questions wrong. You'll see which below. Each tool here answers one
question, and the skill is knowing which question you're asking.

## The workload, and the two things it needs first

`concurrency/forkjoin/ForkBlur.java` is the subject. Two things stop it from working as the
old doc said:

**There's no input image.** `ForkBlur.main` reads `original.JPG` from the working directory, which
isn't in the repo (verified):

    Exception in thread "main" javax.imageio.IIOException: Can't read input file!

Make one. Save this as `MakeImage.java` **outside the repo** (it's a tool, not exercise code) and
run it as a single-file program:

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

    java MakeImage.java 4000 3000      # 12 megapixels, about 7 MB

**It finishes too fast to look at.** On that image the blur takes **176 ms**, and the whole program
**0.85 s** (verified, 10 cores). "Give it a large enough image that it runs for a minute" would
need billions of pixels. Loop it instead. `ForkBlur.blur(BufferedImage)` is `public static`, so a
small driver, also kept outside the repo, can call it for as long as you need:

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

    mvn -q clean package
    javac -cp target/classes -d /tmp/lab BlurLoop.java
    java -cp target/classes:/tmp/lab BlurLoop 60      # run for 60 s, from where original.JPG is

Then, in a second terminal, `jcmd` to find its pid.

## The command list

Every row verified against the running loop on JDK 26:

| Question | Command |
| --- | --- |
| What is this process doing right now | `jcmd <pid> Thread.print` |
| What is on the heap | `jcmd <pid> GC.heap_info` then `jcmd <pid> GC.class_histogram` |
| Record a flight | `jcmd <pid> JFR.start name=lab settings=profile duration=60s filename=lab.jfr` |
| Check on it | `jcmd <pid> JFR.check` |
| What flags is it running with | `jcmd <pid> VM.flags` |
| What does it think its limits are | `jcmd <pid> VM.info`, look for the container section |
| Heap dump for later | `jcmd <pid> GC.heap_dump /tmp/lab.hprof` |

Open `lab.jfr` in JDK Mission Control if you have it. If not, these ship with the JDK and work
from a terminal:

    jfr summary lab.jfr
    jfr print --events jdk.ObjectAllocationSample --stack-depth 3 lab.jfr

(Or start the recording at launch instead of attaching:
`java -XX:StartFlightRecording=filename=lab.jfr,settings=profile ...`.)

## The worked example: question 4, container limits

Here's one question answered end to end, so you can see what an answer looks like. Then the
other four are yours.

**What does `Runtime.getRuntime().maxMemory()` report, and why?** A three-line program that prints
it, run three ways (verified, on a 10-core, 23 GB machine):

| Where | Memory the JVM sees | `maxMemory()` | `availableProcessors()` |
| --- | --- | --- | --- |
| the host, no limit | 23 GB | **6008 MB** | 10 |
| cgroup limit 1 GB | 1 GB | **255 MB** | 2 |
| cgroup limit 256 MB | 256 MB | **125 MB** | 2 |

The limited runs used `systemd-run --user --scope -p MemoryMax=256M -p CPUQuota=200% java ...`,
which puts the JVM in a cgroup exactly as a container would. **No Docker needed**, and
`-XshowSettings:system` confirms the JVM saw it: `Provider: cgroupv2`, `Memory Limit: 256.00M`.
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

## Write the answers here

1. **Which allocation site dominates in `ForkBlur`?** _(fill in)_ Find it from the JFR allocation
   events, using `--stack-depth`, without reading the source. **A correction to the old version:**
   it said `ForkBlur` "allocates hard". The blur itself, `computeDirectly()`, allocates nothing at
   all. Find out what does, and then look at `jcmd <pid> GC.heap_info`: the word *humongous* in
   its output is G1's name for the answer.
2. **How many fork/join worker threads exist ten seconds into the loop, how many pools are they
   in, and what in the code decided that?** _(fill in)_ **A correction to the old version:** it
   asked about "the common pool". Count the `commonPool` threads in a thread dump: there are
   **none**. Read the names of the threads that *are* there, then read `ForkBlur.java:113`. The
   number will surprise you, and it changes the longer the loop runs. Explain why, and say
   what a long-running service calling `blur()` would eventually do.
3. **Deadlock: what does the thread dump call that state, and where in the output is the
   cycle?** _(fill in)_ **A correction to the old version:** it said to run `MyDeadlock` until it
   hangs. [Doc 08](08-memory-model.md) part two shows it can't: both live threads take the locks
   in the same order. Use the opposite-order version that doc 08 restores. Until doc 08 fixes its
   package, the class is `com.java2novice.algos.MyDeadlock`, not
   `org.mandfer.practices.concurrency.MyDeadlock`.
4. **Container-aware limits.** Answered above. Run it on your own machine and write down your
   numbers, which will differ.
5. **The explain-it version**: answer _"how would you debug a slow service in production"_ in four
   sentences, naming the tool you reach for first and what it would have to show you before you
   reached for the second. _(fill in)_ Use what questions 1 and 2 taught you. Neither answer was
   in the place the source suggested.
