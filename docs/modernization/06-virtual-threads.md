# 06. Virtual threads

**Java 21** (preview in 19 and 20) · **JDK 24** changed what pins (JEP 491) · concept stage 06 of
[the concept order](00-start-here.md) · no build change

> **No safety net:** `OrdersProcessingSystem`, `SimpleThreads` have no tests. Write them first,
> in a separate commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why
> the order matters.

## What this stage is actually about

Five words, defined once:

- a **platform thread** is what `new Thread(...)` has always given you: one Java thread backed by
  one operating-system thread. They are expensive: about a megabyte of stack each, and the OS
  schedules them.
- a **virtual thread** is a Java thread that the JVM schedules, not the OS. It is cheap enough that
  you create one per task and throw it away: `Thread.ofVirtual().start(...)`, or
  `Executors.newVirtualThreadPerTaskExecutor()`.
- a **carrier thread** is the platform thread a virtual thread is actually running on at a given
  moment. The JVM keeps a small pool of them, one per CPU core by default.
- **mounting** and **unmounting**: when a virtual thread blocks (on `sleep`, I/O, a lock, a queue),
  the JVM takes it off its carrier and puts another virtual thread there instead. When the
  blocking call returns, it gets mounted again, possibly on a different carrier.
- **pinning** is when a virtual thread blocks but *can't* be unmounted, so its carrier is stuck
  waiting with it.

That gives one rule, and everything below follows from it: **a virtual thread is cheap only while
it is blocked.** While it waits, it gives its carrier to someone else, so ten thousand waiting
tasks cost almost nothing. While it computes, it holds a carrier like any other thread, and
there are only as many carriers as cores. Blocking work scales; CPU work doesn't; pinning is what
happens when blocking work starts behaving like CPU work.

## The worked example

Thirty tasks, each taking 100 ms, on a 10-core machine (verified, `nproc` = 10):

| Executor | Task blocks (`Thread.sleep(100)`) | Task computes (spins for 100 ms) |
| --- | --- | --- |
| `newFixedThreadPool(3)` | **1005 ms** | **1003 ms** |
| `newVirtualThreadPerTaskExecutor()` | **109 ms** | **309 ms** |

Read the table by the rule:

- **Pool of 3, either column:** three at a time, ten rounds: `tasks / 3 * 100 ms` = 1000 ms. The
  pool doesn't care whether the task waits or works.
- **Virtual, blocking:** all thirty sleep at once, each one off its carrier, so the total is about
  one task's time: ~100 ms.
- **Virtual, computing:** each task holds a carrier for its whole 100 ms. Ten carriers, thirty
  tasks, three rounds: 300 ms. That's exactly what a fixed pool of 10 would give you. **Virtual
  threads made no difference to CPU-bound work**; they only stopped the pool of 3 from being too
  small.

The code change itself is one line, plus the try-with-resources that `ExecutorService` has
supported since Java 19 (`close()` waits for submitted tasks, then shuts down):

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        // submit tasks; close() at the end of the block waits for them
    }

## Part one: `SumWordsLength`

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

Four commits, in this order:

1. **Tighten the test.** Assert 22 exactly. Add a test that interrupts the thread, calls
   `calculate`, and asserts the current (wrong) result of 0. Remove the `try/catch` from
   `calcWordLength`. Clear the interrupt flag at the end of your test (`Thread.interrupted()`) so
   it doesn't leak into the next one.
2. **Fix the swallowed exception.** Decide what `calculate` should do when a task fails or the
   thread is interrupted, and write it down: throw, probably. Restore the interrupt flag
   (`Thread.currentThread().interrupt()`) before throwing on `InterruptedException`, because
   swallowing it is how a cancelled program keeps running. The interrupt test's assertion changes
   in this commit, because this is the behaviour fix.
3. **Fix the leak** with try-with-resources around the existing fixed pool. One line moves; the
   tests stay green. Check by hand that a `main` calling `calculate` now exits.
4. **Swap the executor.** `Executors.newFixedThreadPool(3)` becomes
   `Executors.newVirtualThreadPerTaskExecutor()`. With the try-with-resources from step 3, that is
   the whole change. Tests stay green, which tells you the swap was correct, and also that it made
   no difference: the work never blocked.

Then, **in a scratch copy, not a commit**, make the task actually block (`Thread.sleep(100)`
inside `call()`), give it thirty words, and reproduce the left column of the table above with
both executors. Then make it spin instead of sleep, and reproduce the right column. **Being able
to predict both columns before you run them is the deliverable.**
[Doc 34](34-measuring-it-properly.md) says why a stopwatch around one run is weak evidence, and
how to do better.

## Part two: pinning, and what changed in JDK 24

Every tutorial written before 2025 says that blocking inside `synchronized` **pins** the virtual
thread, so the carrier is stuck. **That was true through JDK 23 and isn't true on the JDK this repo
uses.** JEP 491 (JDK 24) let virtual threads unmount while holding a monitor. Here's the
experiment, run with the JVM limited to **one** carrier
(`-Djdk.virtualThreadScheduler.parallelism=1`), thirty tasks, each sleeping 100 ms inside
`synchronized` (verified):

| Lock | Time | What it means |
| --- | --- | --- |
| each task locks its own `new Object()` | **112 ms** | not pinned: all thirty unmounted while asleep |
| all tasks lock one shared object | **3016 ms** | **not pinning either**: they queue for the lock, one at a time |

The second row is the trap. It looks like pinning, and it's just a lock doing its job: only one
thread may hold it. On JDK 21 *both* rows should take about 3000 ms with one carrier, and the
first row is the pinning one. (That's from JEP 491's description of the old behaviour, not a run:
only JDK 25 and 26 are installed here. If you have a JDK 21, run it and correct this line if it's
wrong.) **Telling those two apart is what the follow-up
question is really testing.**

Two more things that changed, both verified:

- `-Djdk.tracePinnedThreads=full`, which most articles tell you to use, is **silently ignored** on
  JDK 26. No output, no warning.
- The way to see pinning now is Flight Recorder:

      java -XX:StartFlightRecording=filename=pin.jfr,settings=profile ...
      jfr print --events jdk.VirtualThreadPinned pin.jfr

  For the experiment above it records **zero** `jdk.VirtualThreadPinned` events, for either row.

**What still pins on JDK 26** is a virtual thread blocking while a **native frame** is on its
stack: Java code called back *from* C. To see it, call C's `qsort` through the foreign function
API with a Java comparator that sleeps (verified, one carrier, ten tasks):

    10 virtual threads, each sleeping 100 ms inside a qsort callback: 1043 ms

That's one after another, and JFR records ten events with
`pinnedReason = "Native or VM frame on stack"`. The foreign-function API is
[doc 23](23-foreign-function-and-memory.md)'s subject. Do this experiment after that one if you do
it at all. The point to take from here is simpler: **pinning still exists, it moved, and the
old advice to avoid `synchronized` is now wrong.**

## Part three: `SimpleThreads` on a virtual thread

`concurrency/SimpleThreads.java:39` starts its worker as `new Thread(new MessageLoop())`. Change
it to `Thread.ofVirtual().unstarted(new MessageLoop())` in a scratch copy and run it with `5`
(give up after five seconds). Verified output:

    main: Starting MessageLoop thread
    main: Waiting for MessageLoop thread to finish
    : Mares eat oats
    main: Tired of waiting!
    : I wasn't done!
    main: Finally!

Three things, in order of how likely they are to bite:

- **The name is empty.** Virtual threads are unnamed by default, so the program's whole logging
  scheme (`threadName + ": " + message`) now prints a leading colon. The fix is
  `Thread.ofVirtual().name("MessageLoop")`. Check what your real logging framework prints for
  a virtual thread before you switch a production service over.
- **Interruption works exactly as before.** `t.interrupt()` wakes the sleeping virtual thread, and
  the `catch (InterruptedException e)` runs.
- **Virtual threads are always daemon threads.** `SimpleThreads` joins its worker, so it doesn't
  matter here, but a `main` that starts a virtual thread and returns *exits*, killing the
  thread mid-task. A platform thread would have kept the JVM alive. That is the opposite of the
  `SumWordsLength` leak, and just as quiet.

The test for this commit is the pure part: if you extracted `usage()` in
[doc 04](04-switch-and-text-blocks.md), it still passes. The threading itself is covered by
[`../testing-first.md`](../testing-first.md)'s concurrency rule: say so in the commit message.

## Part four, optional: `OrdersProcessingSystem` from a blank page

`concurrency/OrdersProcessingSystem.java` is a stub: a `ConcurrentLinkedQueue<Order>` that nothing
adds to, an empty `start()`, and an inner `Runnable` with an empty `run()`. It isn't a
conversion. It's a blank page. If you want to build it:

- One generator produces N orders onto the queue; several virtual threads take them off and
  "process" them (a `sleep` is enough).
- **The test asserts the invariant, not the timing**: every generated order is processed exactly
  once. Collect processed orders into a concurrent set and compare it with the generated ones.
  `testing-first.md` rule 2 is this exact case.
- **Decide about the queue first.** `ConcurrentLinkedQueue` doesn't block: `poll()` returns
  `null` when it's empty, so the consumers would spin. That burns the carriers this stage just
  taught you to keep free. A `LinkedBlockingQueue`'s `take()` blocks, so the virtual thread
  unmounts. Write down why you changed it, in the commit that changes it.

## Structured concurrency: not here

Structured concurrency is **still a preview API**, and its shape changed after JDK 21: the
`ShutdownOnFailure` subclass in most tutorials isn't how JDK 25 or 26 spell it. It's taught, with
the current API, in [doc 26](26-scoped-values-and-structured-concurrency.md), which needs the
preview flag. Do this doc first.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: what does an unbounded number of concurrent calls do to the service on the other end?
Where does backpressure live once the pool is not providing it by accident? (The pool of 3 was
also a limit of three simultaneous calls; `newVirtualThreadPerTaskExecutor` has no limit at all.)
What happens to a `ThreadLocal` when there are a million threads? And when the advice you learned
("avoid `synchronized` with virtual threads") stops being true two releases later, how would you
have found out?
