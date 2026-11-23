# 08. The memory model

**Java 5** (JSR 133, the memory model as it stands today) · concept stage 08 of
[the concept order](00-start-here.md) · no build change

> **No safety net:** `BadThreads`, `MyDeadlock`, `Safelock` have no tests. Write them first, in
> a separate commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why the
> order matters. All three are concurrent, so its "Concurrency is the exception" section is the
> rule that applies here.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `BadThreads`](#exercise-1-badthreads-a-demo-that-cant-fail) | Characterize it, decide what it's for, and write the stop-flag test. Two commits, plus the test. | T1–T4 | Characterize first: assert today's (wrong) message. |
| [2. `MyDeadlock`](#exercise-2-mydeadlock-which-doesnt-deadlock) | Fix the package, then write a test that finds a real deadlock. | T5 | A deterministic deadlock test. |
| [3. `Safelock`](#exercise-3-safelock-the-other-half) | Run it, read the output, and write down the trade-off. | T5, T6 | None. |
| [4. What got worse](#exercise-4-fill-in-what-got-worse) | Write down what the fixes cost. | all | None. |
| [5. Review questions](#exercise-5-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the six sections in order: each one builds on the one before. Here is the whole story in one
paragraph, so you know where it's going.

> A write made by one thread isn't automatically seen by another: the JIT compiler is allowed to
> read a field once and keep using the value (T1). What guarantees that a write is seen is a
> *happens-before* edge, and there's a short, fixed list of ways to get one (T2). Seeing the
> latest value isn't the same as updating it safely, so `volatile` fixes a stop flag and not a
> counter (T3). And because all of this depends on the JIT, the core and the timing, a race that
> never shows up when you run it is still a race (T4). Locks fix visibility and atomicity, and
> bring their own failure: two threads each holding the lock the other needs, forever (T5). One
> way out is to refuse to wait, which trades the deadlock for failed attempts (T6).

Every code sample below is a complete program unless it says otherwise. Save it as the class name
plus `.java` and run it with `java Name.java`; no build needed. Outputs marked *verified* were run
on JDK 26. **Several of these programs are races, so their output is not guaranteed.** Where that
is the case, the text says "one run printed", and the point is what the program is *allowed* to
do, not what it did once.

### T1. The problem: a write the other thread never sees

Here is the smallest concurrent program that goes wrong. One thread spins until a flag is set;
the main thread sets it a second later.

```java
public class StopFlag {
    static boolean stop;                          // not volatile: no happens-before edge

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            long n = 0;
            while (!stop) n++;                    // nothing in the loop writes `stop`
        });
        worker.setDaemon(true);                   // so the JVM can exit even if it never stops
        worker.start();

        Thread.sleep(1_000);                      // let the JIT compile the loop
        stop = true;
        worker.join(3_000);
        System.out.println("worker alive 3 s after stop = true? " + worker.isAlive());
    }
}
```

```text
worker alive 3 s after stop = true? true                 (verified 2026-11-23, 3 runs of 3)
```

The worker **never stops**, even though `stop` has been `true` for three seconds. Run the same
program with the JIT switched off, and it does:

```sh
java -Xint StopFlag.java
```

```text
worker alive 3 s after stop = true? false                (verified 2026-11-23)
```

That second run is the explanation. In the interpreter, every loop iteration reads `stop` from
memory and eventually sees `true`. Once the JIT compiles the loop, it is allowed to read `stop`
**once**, because nothing in the loop writes it and nothing tells the compiler that another
thread might. So it turns `while (!stop)` into `while (true)`. **The bug isn't in the hardware.
The compiler made a legal optimisation.**

This is **visibility**: whether a write made by one thread is seen by another. Without help, it
may never be. The JIT can hoist a read out of a loop, and each core can keep a value in a register
or cache. None of that is a bug in Java. It's what makes single-threaded code fast, and the
language only promises otherwise when you ask it to.

### T2. Happens-before: the only promise you get

The way you ask is a **happens-before** edge. If action A happens-before action B, B sees
everything A wrote, and the compiler and the CPU aren't allowed to reorder things so that it
doesn't. You can't make one up. You get it from a small, fixed list:

| Edge | Means |
| --- | --- |
| unlocking a monitor → the next lock of the same monitor | whatever a thread wrote inside `synchronized`, the next thread to take that lock sees ([doc 07, T1](07-classic-concurrency.md#t1-the-monitor-the-lock-every-object-carries)) |
| a `volatile` write → every later read of that field | the reader sees that write and everything the writer did before it |
| `t.start()` → everything `t` does | the new thread sees what its starter wrote before starting it |
| everything `t` does → `t.join()` returning | the joiner sees everything the thread wrote |

The last two need no keyword at all. This program shares a plain array with no `volatile` and no
lock, and it is **guaranteed** to print what it prints, because `start()` and `join()` provide the
edges:

```java
public class Edges {
    static int[] data;                            // plain field, plain array: no volatile anywhere

    public static void main(String[] args) throws InterruptedException {
        data = new int[] { 1, 2, 3 };             // written BEFORE start()

        Thread worker = new Thread(() -> {
            // start() happens-before everything this thread does,
            // so it is guaranteed to see the array main wrote above.
            System.out.println("worker sees " + data[0] + data[1] + data[2]);
            data[0] = 42;                         // written by the worker...
        });
        worker.start();
        worker.join();                            // ...and join() happens-after all of it

        System.out.println("main sees data[0] = " + data[0]);
    }
}
```

```text
worker sees 123                                          (verified 2026-11-23)
main sees data[0] = 42
```

And this is the fix for T1. **`volatile`** makes every read of a field see the latest write, which
makes the JIT's "read it once" optimisation illegal:

```java
public class VolatileStopFlag {
    static volatile boolean stop;                 // every read sees the latest write

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            long n = 0;
            while (!stop) n++;
        });
        worker.setDaemon(true);
        worker.start();

        Thread.sleep(1_000);
        stop = true;                              // happens-before the worker's next read
        worker.join(3_000);
        System.out.println("worker alive 3 s after stop = true? " + worker.isAlive());
    }
}
```

```text
worker alive 3 s after stop = true? false                (verified 2026-11-23, 3 runs of 3)
```

All three runs of T1, side by side (verified, JDK 26):

| Run | Worker still alive 3 s after `stop = true`? |
| --- | --- |
| as written | **yes**: it never stops |
| `stop` declared `volatile` | no |
| as written, but with `java -Xint` (interpreter only, no JIT) | no |

That gives one rule, and everything below follows from it:

> **If there is no happens-before edge between a write and a read, the read is allowed to see
> anything: the new value, the old one, or the old one forever.**

### T3. Seeing isn't updating: `volatile` and the counter

`volatile` fixes the stop flag because the flag only needs **visibility**: one thread writes,
another reads. A counter needs more. `count++` looks like one step, but it's three: read the
value, add one, write it back. Another thread can run between any two of them. Two threads can
both read 41, both add 1, and both write 42: one increment is lost, and `volatile` can't prevent
it, because every one of those reads *did* see the latest value.

What's missing is **atomicity**: a compound action that no other thread can see half-done.
`synchronized` gives it (only one thread is inside at a time), and so does `AtomicInteger` (its
`incrementAndGet()` is one atomic hardware operation, not three steps). `volatile` doesn't.

```java
import java.util.concurrent.atomic.AtomicInteger;

public class LostUpdates {
    static volatile int volatileCount;
    static int lockedCount;
    static final Object LOCK = new Object();
    static final AtomicInteger atomicCount = new AtomicInteger();

    static void twice(Runnable increment) throws InterruptedException {
        Runnable million = () -> { for (int i = 0; i < 1_000_000; i++) increment.run(); };
        Thread a = new Thread(million);
        Thread b = new Thread(million);
        a.start(); b.start();
        a.join();  b.join();                      // join: we see everything they wrote
    }

    public static void main(String[] args) throws InterruptedException {
        twice(() -> volatileCount++);                          // read, add, write: three steps
        twice(() -> { synchronized (LOCK) { lockedCount++; } });
        twice(atomicCount::incrementAndGet);                   // one atomic step

        System.out.println("volatile int:  " + volatileCount);
        System.out.println("synchronized:  " + lockedCount);
        System.out.println("AtomicInteger: " + atomicCount.get());
    }
}
```

One run printed (verified 2026-11-23):

```text
volatile int:  1093223
synchronized:  2000000
AtomicInteger: 2000000
```

The last two lines are guaranteed: 2,000,000 every time. The first one isn't anything in
particular. Three runs of this program gave 1093223, 1116615 and 980801. An earlier version of
this doc's counter program, the same two threads and a million increments each, lost about
10,000 per run (verified, three runs):

```text
volatile int:   1990196   1989635   1987351      // ~10,000 lost every run
synchronized:   2000000   2000000   2000000
AtomicInteger:  2000000   2000000   2000000
```

How many get lost depends on the code shape, the JIT and the machine. That they *can* be lost
doesn't.

> **`volatile` fixes a stop flag and doesn't fix a counter**, because the flag needs only
> visibility, and `count++` needs atomicity too.

That one sentence is the difference between having read about the memory model and understanding
it.

### T4. A race that never shows up is still a race

T1's flag failed every time. Most races aren't that obliging. Whether a missing edge shows up
depends on whether the JIT has compiled that code yet, what else the core is doing, and how long
the threads happen to wait. Put a two-second `sleep` between a write and a read, and the write
will almost always have landed by the time the read happens. *Almost always* is not a guarantee;
nothing in the language says so, and a busier machine, a different JIT or a shorter sleep can
change it.

So:

- **"It works on my machine" means the edge was missing and you got lucky.**
- **A test that passes is weak evidence, and a demo that doesn't fail proves nothing.**
- What you *can* assert is the fixed version: "with `volatile`, the worker stops within a second"
  is true every time. "Without it, it may not" can only be observed, not asserted.

The proof that a program is correct comes from reading it for the edges in T2, not from running
it. Exercise 1 is a demo that has never once failed when run, and is still wrong.

### T5. Deadlock: two locks, taken in opposite orders

Locks are the main way to get both edges and atomicity. They bring a failure of their own. A
**deadlock** is two or more threads each holding a lock the other needs. None of them can ever
proceed.

It takes two locks and two threads that take them in **opposite orders**:

```java
public class TwoLocks {
    private static final Object FIRST = new Object();   // private objects, not string literals
    private static final Object SECOND = new Object();

    static Thread locker(String name, Object outer, Object inner) {
        Thread t = new Thread(() -> {
            synchronized (outer) {
                pause();                          // give the other thread time to take its first lock
                synchronized (inner) {
                    System.out.println(name + ": got both");
                }
            }
        }, name);
        t.setDaemon(true);                        // so the JVM can still exit
        return t;
    }

    static void pause() {
        try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread one = locker("one", FIRST, SECOND);   // FIRST, then SECOND
        Thread two = locker("two", SECOND, FIRST);   // SECOND, then FIRST: the opposite order
        one.start();
        two.start();
        Thread.sleep(1_000);
        System.out.println("one: " + one.getState() + ", two: " + two.getState());
    }
}
```

```text
one: BLOCKED, two: BLOCKED                               (verified 2026-11-23)
```

Neither `got both` line ever prints. `one` holds `FIRST` and waits for `SECOND`; `two` holds
`SECOND` and waits for `FIRST`. That **cycle** is what a deadlock is, and it's what you learn to
find. The JVM finds it for you: `jcmd <pid> Thread.print` on a deadlocked program ends with a
section like this (from the repo's `MyDeadlock`, verified):

```text
Found one Java-level deadlock:
"My Thread 1":
  waiting to lock monitor ... (a java.lang.String),
  which is held by "My Thread 2"
"My Thread 2":
  waiting to lock monitor ... (a java.lang.String),
  which is held by "My Thread 1"
```

**Lock ordering is the whole fix.** If every thread takes locks in the same global order (always
`FIRST` before `SECOND`), a cycle can't form: whoever gets `FIRST` will get `SECOND` too, finish,
and let the other in. Change `two` to `locker("two", FIRST, SECOND)` and both lines print.

Two details in the sample are on purpose:

- **It locks on private `new Object()`s, not on string literals.** A literal like `"Java"` is
  interned: every piece of code in the JVM that writes `synchronized ("Java")` shares that one
  lock, whether it meant to or not.
- **The threads are daemons**, so `main` returning lets the JVM exit. A deadlock on non-daemon
  threads keeps the JVM alive forever.

### T6. `tryLock`: refusing to wait

A deadlock needs threads that wait *while holding a lock*. Take that away, and the cycle can't
form. `ReentrantLock.tryLock()` (Java 5) does that: instead of waiting, it answers at once,
`true` if it got the lock and `false` if someone else holds it.

```java
import java.util.concurrent.locks.ReentrantLock;

public class TryBoth {
    public static void main(String[] args) throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();

        Thread holder = new Thread(() -> {
            lock.lock();                          // take it and keep it for a while
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { lock.unlock(); }
        });
        holder.start();
        Thread.sleep(100);                        // make sure the holder has it

        boolean got = lock.tryLock();             // doesn't wait: answers at once
        System.out.println("tryLock while held: " + got);

        holder.join();
        got = lock.tryLock();
        System.out.println("tryLock after release: " + got);
        if (got) lock.unlock();
    }
}
```

```text
tryLock while held: false                                (verified 2026-11-23)
tryLock after release: true
```

The pattern for two locks is: `tryLock` both; if you didn't get both, release whichever one you
did get and give up (or try again later). **Nobody waits while holding a lock, so there's no
cycle.**

The price is that some attempts now fail, and your code has to decide what a failed attempt
means. Under heavy contention, two threads can keep trying, keep backing off at the same moment,
and neither ever gets anywhere. That's **livelock**: no thread is blocked, and still nothing
happens. A deadlock is at least visible in a thread dump; a livelock looks like a busy program.

## Practice

### Exercise 1: `BadThreads`, a demo that can't fail

`concurrency/BadThreads.java` is the Oracle tutorial's visibility example, after someone has
"fixed" it. The original asks: is this guaranteed to print "Mares do eat oats."?

```java
static String message;
// corrector thread:  sleep(1000);  message = "Mares do eat oats.";
// main:              start it;  message = "Mares do not eat oats.";  sleep(2000);  print
```

The answer is **no**: there's no happens-before edge between the corrector's write and main's
read (T2). But **you can't see it fail** (T4). Restored exactly as Oracle wrote it, it printed
"Mares do eat oats." **20 times out of 20** (verified). The 2-second sleep gives the write far
longer than any real cache needs. The old version of this doc said to run it "until it hangs or
prints the wrong string". It has no loop, so it can't hang, and in practice it never prints the
wrong string. **That is the lesson of this file**: a race that never shows up when you run it is
still a race. "I ran it and it was fine" isn't evidence.

And look at what the repo's version does now (`BadThreads.java:37-48`):

```java
correctorThread.start();
correctorThread.join();                       // waits for the corrector to finish
message.setMessage("Mares do not eat oats.");  // then overwrites its answer
System.out.println(message.getMessage());
```

It prints **"Mares do not eat oats."** every time (verified): the wrong answer, reliably. The
`synchronized` accessors on `EncapsulatedMessage` are doing nothing useful here, because `join()`
already provides the happens-before edge (T2's last row). The edit fixed the visibility and
changed the program's meaning, both by accident.

**What to do.** Two commits:

1. **Characterize.** Extract the run into a method that returns the final message, and assert
   `"Mares do not eat oats."`. Say in the commit message that this is the repo's current
   behaviour, not the tutorial's intent.
2. **Decide what it's for, then make it say so.** It can't be a visibility demo, so pick: either a
   *correct* program (join, then read; the corrector's message survives), or delete it in favour of
   the stop-flag demo (T1), which does fail. If you keep it, fix it three ways in a scratch copy
   (`volatile`, `synchronized`, `AtomicReference`), keep one, and say why. For a single reference
   written once, `volatile` is enough, and the other two are heavier ways to get the same
   happens-before edge.

**The stop-flag demo is the test [`../testing-first.md`](../testing-first.md) means** when it says
this is the one place you want non-determinism. Write it as a test with a timeout: with `volatile`
it must stop within a second; without it, it *may* not. You can assert the first. You can only
observe the second (T4). Put the observation in the commit message, not in an assertion.

**Done when:** the characterization commit and the "decide what it's for" commit are in, the
stop-flag test asserts only the `volatile` case, and the commit message records what you
observed without it.

### Exercise 2: `MyDeadlock`, which doesn't deadlock

The old version of this doc said to run `MyDeadlock` until it hangs. **It doesn't hang**
(verified: 4 seconds, 3.7 million lines of output, and `jcmd <pid> Thread.print` reports no
deadlock).

Read `MyDeadlock.java:36-58`. Both live threads are `new MyLockingTread(str1, str2)`. They lock
`str1`, then `str2`, **in the same order**, so one always gets both, finishes, and lets the other
in (T5's fix). The deadlocking pair is lines 11-33, commented out: `trd1` locks `str1` then
`str2`, `trd2` locks `str2` then `str1`. The `TODO` at line 35 says "not locking implementation,
pending to review". It's the fix, left in place of the bug. Uncomment that pair and start them
instead, and it deadlocks after printing **4 lines** (verified), and the thread dump says so
directly: it's the `Found one Java-level deadlock` output shown in T5.

**Lock ordering is the whole fix**: if every thread takes locks in the same global order, a cycle
can't form. The live code already demonstrates that; it just doesn't say so.

Three more things wrong with this file, in order of importance:

- **Its package is `com.java2novice.algos`**, but it lives in `org/mandfer/practices/concurrency/`.
  `javac` accepts this, so the class ends up in `target/classes/com/java2novice/algos/`. **Fix
  the package first**, as its own commit. Nothing else refers to it, so nothing breaks. The
  package name also records where the code came from.
- **It locks on string literals.** `"Java"` and `"UNIX"` are interned, so every piece of code in
  the JVM that writes `synchronized ("Java")` shares that lock (T5). Lock on a private
  `new Object()`.
- **It never stops**: `while (true)` with `println`, on non-daemon threads.

**The test**, and it's deterministic, which is rare for concurrency: start the two opposite-order
threads as **daemon** threads (so the test JVM can still exit), then poll
`ManagementFactory.getThreadMXBean().findDeadlockedThreads()` until it returns non-null, with a
five-second limit. Verified, three runs: **deadlock found, 2 threads, after 12–14 ms.** Assert
that it's found. That's a test of the demo, and it's the same check a production health
endpoint can make.

**Done when:** the package fix is its own commit, and the deadlock test is in and green, with a
time limit so a missed deadlock fails instead of hanging.

### Exercise 3: `Safelock`, the other half

`concurrency/Safelock.java` is Oracle's fix for its own bowing-friends deadlock: each `bow()` uses
`tryLock()` on both locks, and if it can't get both, it releases what it got and gives up (T6). **A
cycle can't form, because nobody waits while holding a lock.**

Run it and read the output: some bows complete; some print "started to bow to me, but saw that I
was already bowing to him". That second line is the price. `tryLock` replaces a deadlock with
**failed attempts**, and under enough contention, with **livelock**: both threads keep trying,
keep backing off, and neither gets anywhere. Write down when you'd rather have each.

It has the doc 07 bug as well: `catch (InterruptedException e) {}` at line 83, inside a `for (;;)`,
so it can't be stopped ([doc 07, T3](07-classic-concurrency.md#t3-interruption-and-where-it-gets-lost)).
And it uses `Boolean` instead of `boolean` for both lock flags (lines 24-25), which works only
because nothing is ever `null`.

**Done when:** you've written down when you'd rather have a deadlock and when failed attempts,
and why.

### Exercise 4: fill in "What got worse"

- _(fill this in)_

Prompts: `synchronized`, `AtomicLong` or `LongAdder` for a counter hit a million times a second,
and what changes your answer? What does each cost when contention is low? ([Doc
34](34-measuring-it-properly.md) measures that properly.) `volatile` made the stop flag correct
and made every read of it slower: on a loop that runs a billion times, does that matter, and how
would you find out?

### Exercise 5: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. `StopFlag` never stops, but it does stop with `-Xint`. What did the JIT do, and why was it
   allowed to? (T1)
2. Name the four happens-before edges in T2's table. (T2)
3. `Edges` uses no `volatile` and no lock. Why is its output guaranteed? (T2)
4. Why does `volatile` fix the stop flag and not `count++`? (T3)
5. What does `AtomicInteger` give you that `volatile int` doesn't? (T3)
6. A program with a missing happens-before edge has passed every run you've tried. What does that
   tell you? What can a test assert, and what can it only observe? (T4)
7. What are the two conditions for the deadlock in `TwoLocks`, and what one-line change fixes it?
   (T5)
8. Why is `synchronized ("Java")` a bad idea, even in code that never deadlocks? (T5)
9. How does `tryLock` prevent a deadlock, and what failure does it trade it for? (T6)

**Done when:** you can answer all nine without looking.
