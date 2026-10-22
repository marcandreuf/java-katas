# 08. The memory model

**Java 5** (JSR 133, the memory model as it stands today) · concept stage 08 of
[the concept order](00-start-here.md) · no build change

> **No safety net:** `BadThreads`, `MyDeadlock`, `Safelock` have no tests. Write them first, in
> a separate commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why the
> order matters. All three are concurrent, so its "Concurrency is the exception" section is the
> rule that applies here.

## What this stage is actually about

Five words, defined once:

- **visibility** is whether a write made by one thread is seen by another. Without help, it may
  never be. Each core can keep a value in a register or cache, and the JIT compiler is allowed to
  read a field once and reuse the value.
- **happens-before** is the rule that guarantees visibility. If action A happens-before action B,
  B sees everything A wrote. You get it from a small fixed list: unlocking a monitor
  happens-before the next lock of the same monitor; a `volatile` write happens-before every later
  read of it; `t.start()` happens-before everything `t` does; everything `t` does happens-before
  `t.join()` returns.
- **`volatile`** makes every read of a field see the latest write. It does **not** make
  `count++` safe, because `count++` is three steps (read, add, write), and another thread can run
  between them.
- **atomicity** is that: a compound action no other thread can see half-done. `synchronized` and
  `AtomicInteger` give it; `volatile` doesn't.
- a **deadlock** is two or more threads each holding a lock the other needs. None of them can
  ever proceed.

That gives one rule, and everything below follows from it: **if there is no happens-before edge
between a write and a read, the read is allowed to see anything: the new value, the old one, or
the old one forever.** "It works on my machine" means the edge was missing and you got lucky. And
because the failure depends on the JIT, the core and the timing, **a test that passes is weak
evidence, and a demo that doesn't fail proves nothing.**

## The worked example

Two threads and a `boolean`:

    static boolean stop;                       // not volatile

    Thread worker = new Thread(() -> { while (!stop) n++; });
    worker.start();
    Thread.sleep(1000);
    stop = true;
    worker.join(3000);

Verified, three ways, on JDK 26:

| Run | Worker still alive 3 s after `stop = true`? |
| --- | --- |
| as written | **yes**: it never stops |
| `stop` declared `volatile` | no |
| as written, but with `java -Xint` (interpreter only, no JIT) | no |

The third row is the explanation. In the interpreter, every loop iteration reads `stop` from
memory and eventually sees `true`. Once the JIT compiles the loop, it is allowed to read `stop`
**once**, because nothing in the loop writes it and there's no happens-before edge saying another
thread might. So it turns `while (!stop)` into `while (true)`. **The bug isn't in the hardware.
The compiler made a legal optimisation.** `volatile` is what makes that optimisation illegal.

Now the counter, the same two threads, a million increments each (verified, three runs):

    volatile int:   1990196   1989635   1987351      // ~10,000 lost every run
    synchronized:   2000000   2000000   2000000
    AtomicInteger:  2000000   2000000   2000000

**`volatile` fixes a stop flag and doesn't fix a counter**, because the flag needs only
visibility, and `count++` needs atomicity too: two threads can both read 41, both add 1, and both
write 42. That one sentence is the difference between having read about the memory model and
understanding it.

## Part one: `BadThreads`, a demo that can't fail

`concurrency/BadThreads.java` is the Oracle tutorial's visibility example, after someone has
"fixed" it. The original asks: is this guaranteed to print "Mares do eat oats."?

    static String message;
    // corrector thread:  sleep(1000);  message = "Mares do eat oats.";
    // main:              start it;  message = "Mares do not eat oats.";  sleep(2000);  print

The answer is **no**: there's no happens-before edge between the corrector's write and main's
read. But **you can't see it fail.** Restored exactly as Oracle wrote it, it printed
"Mares do eat oats." **20 times out of 20** (verified). The 2-second sleep gives the write far
longer than any real cache needs. The old version of this doc said to run it "until it hangs or
prints the wrong string". It has no loop, so it can't hang, and in practice it never prints the
wrong string. **That is the lesson of this file**: a race that never shows up when you run it is
still a race. "I ran it and it was fine" isn't evidence.

And look at what the repo's version does now (`BadThreads.java:37-48`):

    correctorThread.start();
    correctorThread.join();                       // waits for the corrector to finish
    message.setMessage("Mares do not eat oats.");  // then overwrites its answer
    System.out.println(message.getMessage());

It prints **"Mares do not eat oats."** every time (verified): the wrong answer, reliably. The
`synchronized` accessors on `EncapsulatedMessage` are doing nothing useful here, because `join()`
already provides the happens-before edge. The edit fixed the visibility and changed the program's
meaning, both by accident.

Two commits:

1. **Characterize.** Extract the run into a method that returns the final message, and assert
   `"Mares do not eat oats."`. Say in the commit message that this is the repo's current
   behaviour, not the tutorial's intent.
2. **Decide what it's for, then make it say so.** It can't be a visibility demo, so pick: either a
   *correct* program (join, then read; the corrector's message survives), or delete it in favour of
   the stop-flag demo above, which does fail. If you keep it, fix it three ways in a scratch copy
   (`volatile`, `synchronized`, `AtomicReference`), keep one, and say why. For a single reference
   written once, `volatile` is enough, and the other two are heavier ways to get the same
   happens-before edge.

**The stop-flag demo is the test [`../testing-first.md`](../testing-first.md) means** when it says
this is the one place you want non-determinism. Write it as a test with a timeout: with `volatile`
it must stop within a second; without it, it *may* not. You can assert the first. You can only
observe the second. Put the observation in the commit message, not in an assertion.

## Part two: `MyDeadlock`, which doesn't deadlock

The old version of this doc said to run `MyDeadlock` until it hangs. **It doesn't hang**
(verified: 4 seconds, 3.7 million lines of output, and `jcmd <pid> Thread.print` reports no
deadlock).

Read `MyDeadlock.java:36-58`. Both live threads are `new MyLockingTread(str1, str2)`. They lock
`str1`, then `str2`, **in the same order**, so one always gets both, finishes, and lets the other
in. The deadlocking pair is lines 11-33, commented out: `trd1` locks `str1` then `str2`, `trd2`
locks `str2` then `str1`. The `TODO` at line 35 says "not locking implementation, pending to
review". It's the fix, left in place of the bug. Uncomment that pair and start them instead, and
it deadlocks after printing **4 lines** (verified), and the thread dump says so directly:

    Found one Java-level deadlock:
    "My Thread 1":
      waiting to lock monitor ... (a java.lang.String),
      which is held by "My Thread 2"
    "My Thread 2":
      waiting to lock monitor ... (a java.lang.String),
      which is held by "My Thread 1"

That **cycle** is what you are learning to find. **Lock ordering is the whole fix**: if every
thread takes locks in the same global order, a cycle can't form. The live code already
demonstrates that; it just doesn't say so.

Three more things wrong with this file, in order of importance:

- **Its package is `com.java2novice.algos`**, but it lives in `org/mandfer/practices/concurrency/`.
  `javac` accepts this, so the class ends up in `target/classes/com/java2novice/algos/`. **Fix
  the package first**, as its own commit. Nothing else refers to it, so nothing breaks. The
  package name also records where the code came from.
- **It locks on string literals.** `"Java"` and `"UNIX"` are interned, so every piece of code in
  the JVM that writes `synchronized ("Java")` shares that lock. Lock on a private `new Object()`.
- **It never stops**: `while (true)` with `println`, on non-daemon threads.

The test, and it's deterministic, which is rare for concurrency: start the two opposite-order
threads as **daemon** threads (so the test JVM can still exit), then poll
`ManagementFactory.getThreadMXBean().findDeadlockedThreads()` until it returns non-null, with a
five-second limit. Verified, three runs: **deadlock found, 2 threads, after 12–14 ms.** Assert
that it's found. That's a test of the demo, and it's the same check a production health
endpoint can make.

## Part three: `Safelock`, the other half

`concurrency/Safelock.java` is Oracle's fix for its own bowing-friends deadlock: each `bow()` uses
`tryLock()` on both locks, and if it can't get both, it releases what it got and gives up. **A
cycle can't form, because nobody waits while holding a lock.**

Run it and read the output: some bows complete; some print "started to bow to me, but saw that I
was already bowing to him". That second line is the price. `tryLock` replaces a deadlock with
**failed attempts**, and under enough contention, with **livelock**: both threads keep trying,
keep backing off, and neither gets anywhere. Write down when you'd rather have each.

It has the doc 07 bug as well: `catch (InterruptedException e) {}` at line 83, inside a `for (;;)`,
so it can't be stopped. And it uses `Boolean` instead of `boolean` for both lock flags (lines
24-25), which works only because nothing is ever `null`.

## What got worse

- _(fill this in)_

Prompts: `synchronized`, `AtomicLong` or `LongAdder` for a counter hit a million times a second,
and what changes your answer? What does each cost when contention is low? ([Doc
34](34-measuring-it-properly.md) measures that properly.) `volatile` made the stop flag correct
and made every read of it slower: on a loop that runs a billion times, does that matter, and how
would you find out?
