# 06. Virtual threads

**Java 21** (preview in 19 and 20) · **JDK 24** changed what pins (JEP 491) · concept stage 06 of
[the concept order](00-start-here.md) · no build change

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line. Exercise 1 is done apart from two loose ends;
start at exercise 2.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `SumWordsLength`](#exercise-1-modernise-sumwordslength) | **Done** (`6ef7a4c`), two optional loose ends. Fix two bugs, then move it to virtual threads. | T1, T2, T6 | Tighten the existing test first. |
| [2. `SimpleThreads` naming](#exercise-2-name-the-simplethreads-worker) | Fix the thread name that `a17baf4` broke. One commit. | T4, T5 | `SimpleThreadsTest` stays green; the naming is checked by running it. |
| [3. `OrdersProcessingSystem`](#exercise-3-build-ordersprocessingsystem-a-producerconsumer) | Build a producer/consumer on virtual threads from a stub. Five commits. | T1, T4, T5, T6 | Write them first: producer, consumer, then the whole system. |
| [4. What got worse](#exercise-4-fill-in-what-got-worse) | Write down two costs of what you just did. | all | None. |
| [5. Review questions](#exercise-5-review-questions) | Answer without looking, then check against the theory. | all | None. |

> **Safety net:** exercise 1 started by tightening `SumWordsLength`'s existing test, because the
> code worked and the test had to pin it down before the change. Exercise 3 is new code,
> so there's nothing to pin down: you write each test before the code that passes it.
> [`../testing-first.md`](../testing-first.md) has the rules for concurrent code.

## Theory

Read the seven sections in order: each one builds on the one before. Here is the whole story in
one paragraph, so you know where it's going.

> Threads used to be expensive, so we shared a few of them through pools, and the size of the
> pool quietly capped how many tasks could be *waiting* at once. A virtual thread is cheap enough
> to create one per task, because while it waits it steps off the real operating-system thread
> and lets another task use it (T1). That makes waiting work fast and leaves computing work
> exactly as fast as it was (T2). The one thing that spoils it, a virtual thread that *can't* step
> off, is called pinning, and JDK 24 moved where it happens (T3). Switching a real program over
> changes two things you'll notice straight away: thread names and daemon status (T4). Naming a
> thread properly means understanding the builder (T5). And stopping a thread, virtual or not,
> means handling the interrupt flag correctly (T6). Structured concurrency comes later (T7).

Every code sample below is a complete program unless it says otherwise. Save it as the class name
plus `.java` and run it with `java Name.java`; no build needed. Outputs marked *verified* were
run on JDK 26.

### T1. What a virtual thread is, and the one rule

**The problem it solves.** Before Java 21, `new Thread(...)` always gave you a **platform thread**:
one Java thread backed by one operating-system thread. Those are expensive: about a megabyte of
stack each, and the OS schedules them. You can't have a hundred thousand of them, so we share a
few through a pool:

```java
ExecutorService pool = Executors.newFixedThreadPool(3);   // three threads, shared by every task
```

A pool is fine when tasks compute. But most real tasks spend their time *waiting*: for a database,
an HTTP response, a file. A pool thread that's waiting does nothing, and it can't be lent to
anyone else while it waits. So the number 3, chosen to save memory, also became the number of
things the program can wait for at the same time.

**The idea.** A **virtual thread** is a Java thread that the JVM schedules, not the OS. It runs on
top of a small pool of platform threads called **carrier threads**, one per CPU core by default.
When a virtual thread blocks (on `sleep`, I/O, a lock, a queue), the JVM takes it off its carrier
and parks it on the heap: that's **unmounting**. The carrier is now free to run another virtual
thread. When the blocking call returns, the virtual thread is **mounted** again, possibly on a
different carrier.

```text
10 carriers (one per core)        virtual threads
─────────────────────────         ───────────────────────────────────────────────
carrier-1  ── runs ──>  vt-7      (computing: holds its carrier)
carrier-2  ── runs ──>  vt-3      (computing: holds its carrier)
carrier-3  ── idle                 vt-1, vt-2, vt-4 ... vt-9999
...                               (all asleep: parked on the heap, no carrier at all)
```

From your code's side, it's the same `Thread` class with the same methods (`sleep`, `join`,
`interrupt`). Only the way you create it changes:

```java
Thread platform = new Thread(task);                  // platform thread, as always
Thread virtual  = Thread.ofVirtual().start(task);    // virtual thread, already started

ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
                                                     // a new virtual thread for every task you submit
```

Because they're cheap, you don't pool virtual threads. You create one per task and throw it away.
This program starts ten thousand of them, each sleeping one second:

```java
import java.util.ArrayList;
import java.util.List;

public class Cheap {
    public static void main(String[] args) throws InterruptedException {
        long start = System.nanoTime();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            threads.add(Thread.ofVirtual().start(() -> {
                try { Thread.sleep(1_000); } catch (InterruptedException e) { }
            }));
        }
        for (Thread t : threads) t.join();
        System.out.printf("10000 virtual threads, each asleep 1 s: %d ms%n",
                (System.nanoTime() - start) / 1_000_000);
    }
}
```

```text
10000 virtual threads, each asleep 1 s: 1050 ms          (verified 2026-11-23; varies by run)
```

Ten thousand one-second waits took one second, because all of them waited at once and none of
them held a carrier while waiting. (The empty `catch` is acceptable only in a throwaway demo; T6
says why.)

**The one rule.** Everything else in this doc follows from it:

> **A virtual thread is cheap only while it is blocked.**

While it waits, it gives its carrier to someone else, so ten thousand waiting tasks cost almost
nothing. While it computes, it holds a carrier like any other thread, and there are only as many
carriers as cores. Blocking work scales; CPU work doesn't.

There is one more word you need. **Pinning** is when a virtual thread blocks but *can't* be
unmounted, so its carrier is stuck waiting with it. Pinning is what happens when blocking work
starts behaving like CPU work. T3 covers it.

| Word | Meaning in one line |
| --- | --- |
| platform thread | a Java thread on its own OS thread; what `new Thread(...)` gives you |
| virtual thread | a Java thread the JVM schedules; cheap, one per task |
| carrier thread | the platform thread a virtual thread is running on right now |
| mount / unmount | putting a virtual thread on a carrier / taking it off when it blocks |
| pinning | a blocked virtual thread that can't unmount, so its carrier waits too |

### T2. The rule in numbers

Here is one experiment that shows the rule at work. Take thirty tasks, each lasting 100 ms, and run
them two ways. In the first column, the task *waits*:

```java
Runnable blocks = () -> {
    try { Thread.sleep(100); } catch (InterruptedException e) { throw new RuntimeException(e); }
};
```

In the second, it *works*: it keeps a core busy for the same 100 ms.

```java
Runnable computes = () -> {
    long end = System.nanoTime() + 100_000_000;   // 100 ms from now
    while (System.nanoTime() < end) { }           // spin: keep the CPU busy
};
```

Each task set runs once on a fixed pool of 3 and once on virtual threads, on a 10-core machine
(verified, `nproc` = 10). **Before you look at the table, use T1's rule to guess the four
numbers.**

| Executor | Task blocks (`Thread.sleep(100)`) | Task computes (spins for 100 ms) |
| --- | --- | --- |
| `newFixedThreadPool(3)` | **1005 ms** | **1003 ms** |
| `newVirtualThreadPerTaskExecutor()` | **109 ms** | **309 ms** |

Read it by the rule:

- **Pool of 3, either column.** Three tasks at a time, ten rounds: `30 / 3 × 100 ms` = 1000 ms.
  The pool doesn't care whether its threads wait or work: a waiting pool thread is still taken.
- **Virtual, blocking.** All thirty go to sleep at once, each one off its carrier, so the total is
  about one task's time: ~100 ms.
- **Virtual, computing.** Each task holds a carrier for its whole 100 ms. Ten carriers, thirty
  tasks, three rounds: 300 ms. That's exactly what a fixed pool of 10 would give you. **Virtual
  threads made no difference to CPU-bound work.** All they did was remove the pool of 3, which was
  too small.

The code change in a real program is one line, plus try-with-resources. `ExecutorService` has been
`AutoCloseable` since Java 19: `close()` waits for every submitted task, then shuts down.

```java
// before: three shared threads, and nothing ever shuts them down
ExecutorService executor = Executors.newFixedThreadPool(3);

// after: one virtual thread per task, and close() at the end of the block waits for them
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (String word : words) {
        futures.add(executor.submit(() -> slowLookup(word)));
    }
}
```

(A fragment, not a full program. Exercise 1's scratch run asks you to write the timing program
yourself, so the predicting is yours.)

### T3. Pinning, and what changed in JDK 24

T1 says a blocked virtual thread unmounts. Pinning is the exception: the thread blocks, but the JVM
can't take it off its carrier, so the carrier waits too. If every carrier gets pinned, nothing
else runs.

**The old story.** Every tutorial written before 2025 says that blocking inside `synchronized`
pins the virtual thread. **That was true through JDK 23, and it isn't true on the JDK this repo
uses.** JEP 491 (JDK 24) let virtual threads unmount while holding a monitor.

**The experiment that shows it.** Limit the JVM to **one** carrier, so any pinning is impossible
to miss, and run thirty tasks that each sleep 100 ms inside `synchronized`. Once with a lock per
task, once with one lock shared by all of them:

```java
import java.util.concurrent.Executors;

public class Locks {
    static final Object SHARED = new Object();

    static long run(boolean shared) {
        long start = System.nanoTime();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 30; i++) {
                executor.submit(() -> {
                    Object lock = shared ? SHARED : new Object();
                    synchronized (lock) {
                        try { Thread.sleep(100); } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    public static void main(String[] args) {
        System.out.println("own lock:    " + run(false) + " ms");
        System.out.println("shared lock: " + run(true) + " ms");
    }
}
```

```sh
java -Djdk.virtualThreadScheduler.parallelism=1 Locks.java
```

| Lock | Time | What it means |
| --- | --- | --- |
| each task locks its own `new Object()` | **112 ms** | not pinned: all thirty unmounted while asleep, one carrier was enough |
| all tasks lock one shared object | **3016 ms** | **not pinning either**: they queue for the lock, one at a time |

The second row is the trap. It looks like pinning, but it's just a lock doing its job: only one
thread may hold it, so thirty 100 ms holds take three seconds on *any* number of carriers. Think of
a single key to a single room: people queue because there's one key, not because the corridor is
blocked.

On JDK 21 *both* rows would take about 3000 ms with one carrier, and there the first row is the
pinning one: thirty threads that could have slept together, stuck behind one pinned carrier. (That
comes from JEP 491's description of the old behaviour, not a run: only JDK 25 and 26 are
installed here.) **Telling "pinned" apart from "waiting for a lock" is the point of this
section**, and timing alone can't do it. You need a tool.

**The tool.** `-Djdk.tracePinnedThreads=full`, which most articles tell you to use, is
**silently ignored** on JDK 26: no output, no warning. The way to see pinning now is Flight
Recorder:

```sh
java -XX:StartFlightRecording=filename=pin.jfr,settings=profile ...
jfr print --events jdk.VirtualThreadPinned pin.jfr
```

For the experiment above it records **zero** `jdk.VirtualThreadPinned` events, for either row.
Zero events and 3016 ms together prove it: the slowness is the lock, not pinning.

**What still pins on JDK 26** is a virtual thread blocking while a **native frame** is on its
stack, that is, Java code called back *from* C. Calling C's `qsort` through the foreign function
API with a Java comparator that sleeps (one carrier, ten tasks):

```text
10 virtual threads, each sleeping 100 ms inside a qsort callback: 1043 ms
```

That's one after another, and JFR records ten events with
`pinnedReason = "Native or VM frame on stack"`. The foreign function API is
[doc 23](23-foreign-function-and-memory.md)'s subject, and that's where to try it if you want to.

The point to take from here is simpler: **pinning still exists, it moved, and the old advice to
avoid `synchronized` is now wrong.**

### T4. Switching a real program: `SimpleThreads` on a virtual thread

Now apply all this to a program in this repo. `concurrency/SimpleThreads.java` starts one worker
that prints four messages, four seconds apart, while `main` waits for it and gives up after a
timeout. Switching the worker to a virtual thread is a one-line change:

```diff
-    Thread t = new Thread(new MessageLoop());
+    Thread t = Thread.ofVirtual().unstarted(new MessageLoop());
     t.start();
```

Run with `5` (give up after five seconds), this prints (verified):

```text
main: Starting MessageLoop thread
main: Waiting for MessageLoop thread to finish
: Mares eat oats
main: Tired of waiting!
: I wasn't done!
main: Finally!
```

The program still works, but three things changed or didn't, in order of how likely they are to
bite:

- **The name is empty.** A platform thread gets a default name like `Thread-0`; a virtual thread
  gets no name at all. The program's whole logging scheme is `threadName + ": " + message`, so the
  worker's lines now start with a bare colon. The fix is to give the thread a name where it's
  created (T5, and exercise 2). Before you switch a production service over, check what your real
  logging framework prints for an unnamed thread.
- **Interruption works exactly as before.** `t.interrupt()` wakes the sleeping virtual thread, and
  the `catch (InterruptedException e)` runs: that's the `I wasn't done!` line.
- **Virtual threads are always daemon threads.** The JVM exits when the last non-daemon thread
  ends, and it doesn't wait for daemon threads. `SimpleThreads` joins its worker, so it doesn't
  matter there. But look at what happens when `main` doesn't join:

  ```java
  public class Daemon {
      public static void main(String[] args) {
          Thread.ofVirtual().start(() -> {
              try { Thread.sleep(100); } catch (InterruptedException e) { return; }
              System.out.println("worker: done");
          });
          System.out.println("main: returning");
      }
  }
  ```

  ```text
  main: returning                                        (verified 2026-11-23)
  ```

  `worker: done` never prints. `main` returned, no non-daemon thread was left, and the JVM exited
  with the worker still asleep. No error, no warning. With `new Thread(...)` instead, the JVM
  would have waited and printed both lines. That's the opposite of the `SumWordsLength` leak from
  exercise 1 (threads that kept the JVM alive), and just as quiet. Exercise 3 runs straight into
  it: its `start()` has to wait for every thread it started.

### T5. Making and naming a thread: the builder

`Thread.ofVirtual()` and `Thread.ofPlatform()` don't give you a thread. They give you a
**builder**: an object whose job is to make threads. You set it up first, then ask it for threads,
and every thread it makes carries the settings. Think of it as a stamp: you set the stamp once,
then each thread it stamps comes out the same way.

| Call | Returns | What it is |
| --- | --- | --- |
| `Thread.ofVirtual()` | `Thread.Builder.OfVirtual` | a builder for virtual threads |
| `.name("reporter")` | the same builder | the builder, set so the threads it makes get that name |
| `.name("worker-", 0)` | the same builder | numbered names: `worker-0`, `worker-1`, ... one per thread made |
| `.unstarted(runnable)` | `Thread` | a thread, not started yet: call `start()` on it |
| `.start(runnable)` | `Thread` | a thread, already started |
| `.factory()` | `ThreadFactory` | for executors: `Executors.newThreadPerTaskExecutor(factory)` gives named virtual threads |

So `.name(...)` is a setting, not a value. It returns the builder so you can keep chaining.

**The name lives on a thread, not on the builder.** It exists only once the builder has made a
thread. You read it from *inside* that thread with `Thread.currentThread().getName()`, which asks
"who is running me right now?". The same line of code gives `main` when the main thread runs it,
and the worker's name when the worker runs it. That splits the job across two places:

- **Where a thread is created** is where you choose its name.
- **Where you print or log** is where you read the name of whoever is running.

Here are both halves in one program, first for a single thread, then for an executor:

```java
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class Names {
    // Where you print: ask who is running this line. Knows nothing about builders.
    static void log(String message) {
        System.out.println(Thread.currentThread().getName() + ": " + message);
    }

    public static void main(String[] args) throws InterruptedException {
        // Where you create: the name is chosen here, once.
        Thread reporter = Thread.ofVirtual()
                .name("reporter")
                .unstarted(() -> log("writing the report"));
        log("starting " + reporter.getName());
        reporter.start();
        reporter.join();

        // The same idea for an executor: a factory that numbers the threads it makes.
        ThreadFactory factory = Thread.ofVirtual().name("worker-", 0).factory();
        try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
            for (int i = 0; i < 3; i++) {
                executor.submit(() -> log("working"));
            }
        }
    }
}
```

```text
main: starting reporter                                  (verified 2026-11-23)
reporter: writing the report
worker-0: working
worker-1: working
worker-2: working
```

`log` is called from three kinds of thread and prints the right name each time, because it reads
the name at the moment it runs. (The three `worker-` lines can come out in any order: they run
concurrently.)

**The mistake to recognise** (from `a17baf4`, verified 2026-11-20). It's tempting to write the
name into the print method instead:

```java
String threadName = Thread.ofVirtual().name("MessageLoop").toString();   // wrong
```

That creates a brand-new builder on every call and prints it. Calling `.toString()` on a builder
doesn't give a name. It gives Java's default text for any object, the class name plus a hash:

```text
java.lang.ThreadBuilders$VirtualThreadBuilder@7344699f: Starting MessageLoop thread
java.lang.ThreadBuilders$VirtualThreadBuilder@3d4eac69: Waiting for MessageLoop thread to finish
```

Each line has a different hash because each call made a new throwaway builder. No thread was ever
made from any of them, and `main`'s lines are mislabelled too: the print method was supposed to
*ask* who is running, and instead it *claims* a name.

### T6. The interrupt flag: propagate it or restore it

You can't safely force a thread to stop from outside. Java's answer is cooperative:
`t.interrupt()` doesn't stop `t`. It sets a **flag** on it, a polite request to stop, like a tap on
the shoulder. The thread has to notice the tap and decide to stop. Three things read the flag:

- **Blocking calls** (`sleep`, `join`, `BlockingQueue.take`, `Future.get`) throw
  `InterruptedException` when the flag is set, and **clear the flag** as they throw. From then on,
  the exception is the only record that someone asked the thread to stop.
- `Thread.currentThread().isInterrupted()` reads the flag and leaves it alone.
- `Thread.interrupted()` (static) reads the flag **and clears it**. That's why exercise 1 uses it
  at the end of a test: to stop the flag leaking into the next test.

You can watch all three on the main thread:

```java
public class Flag {
    public static void main(String[] args) {
        Thread.currentThread().interrupt();                       // tap our own shoulder
        System.out.println("isInterrupted: " + Thread.currentThread().isInterrupted());
        System.out.println("isInterrupted: " + Thread.currentThread().isInterrupted());

        try {
            Thread.sleep(10);                                     // sees the flag, throws at once
        } catch (InterruptedException e) {
            System.out.println("sleep threw, flag now: " + Thread.currentThread().isInterrupted());
        }

        Thread.currentThread().interrupt();
        System.out.println("interrupted(): " + Thread.interrupted());
        System.out.println("interrupted(): " + Thread.interrupted());
    }
}
```

```text
isInterrupted: true                                      (verified 2026-11-23)
isInterrupted: true
sleep threw, flag now: false
interrupted(): true
interrupted(): false
```

`isInterrupted()` can be asked twice and says the same thing. `sleep` throws and wipes the flag.
`interrupted()` answers once and then wipes it.

So whoever catches `InterruptedException` is holding the cancellation request, and it's the only
copy. There are two right moves and one wrong one:

| What you do | Flag afterwards | Right? |
| --- | --- | --- |
| **Propagate**: declare `throws InterruptedException` and let it go | cleared, but the exception carries the request up | yes |
| **Restore**: catch it, call `Thread.currentThread().interrupt()`, then return or throw something else | set again | yes |
| **Swallow**: catch it and carry on, or return a default | cleared, and nobody knows | no: a cancelled program keeps running |

In code (fragments):

```java
// Propagate: the method can throw, so let the caller decide.
int total(Future<Integer> f) throws InterruptedException, ExecutionException {
    return f.get();
}

// Restore: run() can't throw a checked exception, so put the flag back and stop.
Runnable heartbeat = () -> {
    while (true) {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // the request survives for whoever looks next
            return;                               // and we actually stop
        }
        System.out.println("still alive");
    }
};

// Swallow: wrong. The request is gone and the loop goes round again, forever.
try { Thread.sleep(1_000); } catch (InterruptedException e) { }
```

Use propagate whenever the method's signature allows it; it's the simplest. Use restore when it
doesn't: `Runnable.run()` is the usual case, which is why the consumer in exercise 3 has to catch
the exception, restore the flag, then return.

`SumWordsLength.calculate` propagates, so after the interrupt test the flag is **cleared**
(verified 2026-11-20). That's correct, not a leak: the exception carried the request instead.

### T7. Structured concurrency: not here

Structured concurrency is **still a preview API**, and its shape changed after JDK 21: the
`ShutdownOnFailure` subclass in most tutorials isn't how JDK 25 or 26 spell it. It's taught, with
the current API, in [doc 26](26-scoped-values-and-structured-concurrency.md), which needs the
preview flag.

## Practice

### Exercise 1: modernise `SumWordsLength`

> **Status: done** in `6ef7a4c` (one commit, not the four below; checked 2026-11-20).
>
> - Step 1, tighten the test: done. Asserts 22 exactly; the `try/catch` around `call()` is gone;
>   an interrupt test exists.
> - Step 2, stop swallowing: done. `calculate` declares `ExecutionException` and
>   `InterruptedException` and lets them propagate. The interrupt test asserts the throw.
> - Steps 3 and 4, close the executor and swap it: done. `newVirtualThreadPerTaskExecutor()` in
>   try-with-resources. A `main` calling `calculate` exits on its own (checked by hand).
>
> **Still to do (optional):**
>
> - **A test for a failing task.** Nothing tests the `ExecutionException` path. `calculate`
>   creates its own `WordLengthCallable`s, so a test can't make one fail without a seam. Decide
>   whether the seam is worth adding for this kata, and write down why either way.
> - **The scratch timing run** at the end of this exercise, if you haven't done it: predict both
>   columns of T2's table, then run them.
>
> The steps below are kept as the record of what was changed and why.

`concurrency/SumWordsLength.java:25-46` splits a string, submits one `Callable` per word to a
fixed pool of three, collects the `Future`s, and adds up `get()`. The work is `word.length()`, so
the pool is pure overhead, but the *shape* is the one virtual threads are for: many small tasks,
submitted, then awaited.

It has two defects, and both are invisible to the test that exists today (verified):

- **The `ExecutorService` is never shut down.** Call `calculate` from a `main` and the JVM doesn't
  exit: `main` returns, and the three pool threads keep it alive until `timeout` kills it. The test
  suite doesn't notice, because the test run ends its own JVM when it finishes (verified: the
  test passes in about five seconds).
- **`catch (Exception e)` at line 41 swallows everything.** Interrupt the calling thread before
  `calculate("hello goodbye this is marc")`, and `get()` throws `InterruptedException`. The catch
  prints `null` (the exception has no message) and **returns 0**, as though the sum of those words
  were zero. Any `ExecutionException` would go the same way.

And the test that exists is weaker than it looks. `SumWordsLengthCallableResultsSampleTest`
checks only that the sum is between 10 and 30. The true answer is 22. `calcWordLength` wraps its
assertion in `try { ... } catch (Exception e)`, so if `call()` threw, the test would print a
message and pass.

**What to do.** Four commits, in this order:

1. **Tighten the test.** Assert 22 exactly. Add a test that interrupts the thread, calls
   `calculate`, and asserts the current (wrong) result of 0. Remove the `try/catch` from
   `calcWordLength`. Clear the interrupt flag at the end of your test (`Thread.interrupted()`) so
   it doesn't leak into the next one.
2. **Fix the swallowed exception.** Decide what `calculate` should do when a task fails or the
   thread is interrupted, and write it down: throw, probably. Either let `InterruptedException`
   propagate, or catch it, restore the flag (`Thread.currentThread().interrupt()`) and throw
   something else (T6). Swallowing it is how a cancelled program keeps running. The interrupt
   test's assertion changes in this commit, because this is the behaviour fix.
3. **Fix the leak** with try-with-resources around the existing fixed pool. One line moves; the
   tests stay green. Check by hand that a `main` calling `calculate` now exits.
4. **Swap the executor.** `Executors.newFixedThreadPool(3)` becomes
   `Executors.newVirtualThreadPerTaskExecutor()`. With the try-with-resources from step 3, that is
   the whole change. Tests stay green, which tells you the swap was correct, and also that it made
   no difference: the work never blocked.

Then, **in a scratch copy, not a commit**, make the task actually block (`Thread.sleep(100)`
inside `call()`), give it thirty words, and reproduce the left column of T2's table with
both executors. Then make it spin instead of sleep, and reproduce the right column. **Being able
to predict both columns before you run them is the deliverable.**
[Doc 34](34-measuring-it-properly.md) says why a stopwatch around one run is weak evidence, and
how to do better.

**Done when:** the four commits are in, a `main` calling `calculate` exits on its own, and you
predicted both columns of T2's table before running them.

### Exercise 2: name the `SimpleThreads` worker

**Status (checked 2026-11-20):** `a17baf4` switched the worker to a virtual thread, which is the
right move, but broke the names. `printMsgWithCurrentThreadsName` now prints
`Thread.ofVirtual().name("MessageLoop").toString()` instead of the current thread's name, so every
line starts with `java.lang.ThreadBuilders$VirtualThreadBuilder@...` (T5 shows the output).

**What to do.** Read T5 first. Then put each half of the job in its place: the name is chosen
where the worker thread is created, and the print method reads the name of whoever is running it.
Only the worker gets a name; the main thread is already called `main`.

**Tests.** `SimpleThreadsTest` covers `usage()`, not the threading, and it must stay green. The
naming is checked by running the program, so say that in the commit message
([`../testing-first.md`](../testing-first.md)'s concurrency rule).

**Done when:** `SimpleThreads 2` prints `main: ...` on the main thread's lines and
`MessageLoop: ...` on the worker's lines (compare T4's output, which has no name at all), and
`SimpleThreadsTest` passes.

### Exercise 3: build `OrdersProcessingSystem`, a producer/consumer

`concurrency/OrdersProcessingSystem.java` is a stub: a `ConcurrentLinkedQueue<Order>` that nothing
adds to, an empty `start()`, and an inner `OrdersGeneratorSimulator` whose `run()` is empty. There
is nothing to modernise. You write the program, using virtual threads, test first.

**What you are building.**

- **One producer** (`OrdersGeneratorSimulator`) creates N orders and puts them on a queue.
- **K consumers**, each on its own virtual thread, take orders off the queue and "process" them.
  Processing stands in for a database or HTTP call. In `main` it's a `Thread.sleep(10)`; in the
  tests it records the order instead, so tests don't sleep.
- **`start()` returns only when every order has been processed** and every consumer has stopped.
  This isn't a nicety: virtual threads are daemon threads (T4), so if `start()` returns early,
  `main` returns, the JVM exits and the unprocessed orders vanish without an error.

**Decisions to make before you write code.** Each one is a trap if you don't make it on purpose.

1. **The queue type.** `ConcurrentLinkedQueue.poll()` returns `null` straight away when the
   queue is empty, so consumers would loop and keep asking. That burns the carriers T1 says to
   keep free. `LinkedBlockingQueue.take()` *waits*, so the virtual thread unmounts and costs
   nothing while there's no work. Switch to `BlockingQueue<Order>` / `LinkedBlockingQueue`.
2. **How consumers know to stop.** `take()` waits forever on an empty queue, so "the queue is
   empty" can't mean "done": it might just mean the producer is slow. Use a **poison pill**: one
   special `Order` constant per consumer, put on the queue after the last real order. A consumer
   that takes a pill returns. Compare the pill with `==`, not `equals`: `Order` is a record
   (doc 01), so a real order with the same name and code would be `equals` to it.
3. **Who puts the pills on.** Simplest: `start()` waits for the producer to finish, then puts K
   pills. The producer then doesn't need to know how many consumers there are.
4. **Seams for the tests.** The current code has a `static` queue and a `private` `start()`, which
   tests can't control. Pass the queue and the processing step in instead. Something like:

       OrdersGeneratorSimulator(BlockingQueue<Order> queue, int count)          // producer
       OrderProcessor(BlockingQueue<Order> queue, Consumer<Order> process)      // consumer, a Runnable
       OrdersProcessingSystem(int orders, int consumers, Consumer<Order> process)
       void start()                                                             // blocks until done

   The names and shapes are yours to choose. What matters is that a test can hand in its own queue
   and its own `process`.
5. **Unique orders.** Give every generated order a distinct code (`"order-1"`, `"order-2"`, ...).
   Because `Order` compares by value, two identical orders would look like one in a `Set`, and a
   duplicate would hide.

**The tests, in this order.** Write each test, watch it fail, then write the code that passes it.
Only the last level involves real concurrency. The first two run on the test thread and are fully
deterministic.

1. **Producer test (no threads).** Give the producer a fresh `LinkedBlockingQueue` and N = 5, call
   `run()` directly, and assert the queue holds exactly 5 orders, all with distinct codes. Add one
   for N = 0.
2. **Consumer test (no threads).** Fill a queue with three orders and one pill, call the
   consumer's `run()` directly with a `process` that adds to a `List`. Assert the list is exactly
   those three orders, in order, and that `run()` returned. Wrap it in
   `assertTimeoutPreemptively(Duration.ofSeconds(1), ...)`: if the pill doesn't work, `run()`
   never returns, and you want a red test, not a frozen build.
3. **Consumer interrupt test (one thread).** Start the consumer on a virtual thread with an
   **empty** queue, so it waits in `take()`. Interrupt it, `join` it with a timeout, and assert it
   has finished. Decide what an interrupted consumer should do (stop, and restore the interrupt
   flag, because `run()` can't propagate it: T6) and write it down in the test name. Name the
   consumer threads too (T5), so a failing run tells you which consumer did what.
4. **System test (real virtual threads).** N = 1000 orders, K = 10 consumers, and a `process` that
   counts into a `ConcurrentHashMap<Order, Integer>` with `merge(order, 1, Integer::sum)`. After
   `start()` returns, assert there are N keys and **every count is exactly 1**: none lost, none
   processed twice. (A `Set` alone can't show a duplicate; that's why it's a count.) Wrap it in
   `assertTimeoutPreemptively` too, and make it a `@RepeatedTest(50)`.

**Don't assert timing**, anywhere. "Finished in under 200 ms" is flaky on a busy machine and proves
nothing about correctness. And a green system test is weak evidence: it shows the race didn't
happen in 50 runs, not that it can't ([`../testing-first.md`](../testing-first.md), rule 2). Say
so in that commit's message.

**Commits.**

1. Switch the queue to `LinkedBlockingQueue`, with the reason (decision 1) in the commit message.
   Nothing uses it yet, so there's nothing to test.
2. Producer: its test, then the code.
3. Consumer: its two tests, then the code.
4. `start()`: the system test, then the code that starts the producer, the consumers and the pills,
   and waits for all of them.
5. `main`: wire it up with N = 100, K = 10 and a `process` that sleeps 10 ms and prints the order.
   Run it by hand and check that it prints 100 orders **and the JVM exits**.

**Then predict, then run** (scratch, not a commit). With N = 1000 and a 10 ms sleep: how long
should `start()` take with K = 10? With K = 100? With K = 1000? Write the three numbers down, then
time them. If you're far off, T1's rule says why.

**Done when:** all four test levels are green, `main` exits on its own, and your three predictions
were within a factor of two.

### Exercise 4: fill in "What got worse"

Do this once exercise 3 is in. Write at least two things that virtual threads made
worse or riskier, in your own words.

- _(fill this in)_
- _(fill this in)_

Prompts: what does an unbounded number of concurrent calls do to the service on the other end?
Where does backpressure live once the pool is not providing it by accident? (The pool of 3 was
also a limit of three simultaneous calls; `newVirtualThreadPerTaskExecutor` has no limit at all.)
Your `LinkedBlockingQueue` in exercise 3: is it bounded, and what happens to memory if the
producer is faster than the consumers? What happens to a `ThreadLocal` when there are a million
threads? And when the advice you learned
("avoid `synchronized` with virtual threads") stops being true two releases later, how would you
have found out?

### Exercise 5: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. Thirty tasks, each `Thread.sleep(100)`, on 10 cores. How long with `newFixedThreadPool(5)`, and
   how long with `newVirtualThreadPerTaskExecutor()`? (T1, T2)
2. Same thirty tasks, but each one spins the CPU for 100 ms. How long with virtual threads, and
   why didn't they help? (T1, T2)
3. On JDK 26, one carrier, ten virtual threads each sleep 100 ms inside `synchronized` on **one
   shared** object. It takes about 1000 ms. Is that pinning? How would you prove it either way?
   (T3)
4. Name one thing that still pins a virtual thread on JDK 26. (T3)
5. What does `Thread.ofVirtual().name("x")` return, and where does the name `"x"` first become
   visible? (T5)
6. You want every virtual thread an executor creates to be named `worker-0`, `worker-1`, ... Which
   two builder calls and which executor factory do you use? (T5)
7. `Future.get()` throws `InterruptedException`. What state is the interrupt flag in now, and what
   are your two correct options? (T6)
8. What's the difference between `Thread.interrupted()` and `isInterrupted()`, and which one do
   you call at the end of a test, and why? (T6)
9. A `main` starts a virtual thread and returns straight away. What happens to the thread? What
   does `start()` in exercise 3 have to do about it? (T4)
10. Why does a virtual-thread consumer use `LinkedBlockingQueue.take()` and not
    `ConcurrentLinkedQueue.poll()`? (T1, exercise 3)
11. Why is the poison pill compared with `==` and not `equals`? (exercise 3, decision 2)

**Done when:** you can answer all eleven without looking.
