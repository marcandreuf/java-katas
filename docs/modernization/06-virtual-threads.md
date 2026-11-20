# 06. Virtual threads

**Java 21** (preview in 19 and 20) · **JDK 24** changed what pins (JEP 491) · concept stage 06 of
[the concept order](00-start-here.md) · no build change

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line. Exercise 1 is done; start at exercise 2.

| Exercise | What you do | Tests |
| --- | --- | --- |
| [1. `SumWordsLength`](#exercise-1-modernise-sumwordslength) | **Done** (`6ef7a4c`). Fix two bugs, then move it to virtual threads. | Tighten the existing test first. |
| [2. `OrdersProcessingSystem`](#exercise-2-build-ordersprocessingsystem-a-producerconsumer) | Build a producer/consumer on virtual threads from a stub. Five commits. | Write them first: producer, consumer, then the whole system. |
| [3. What got worse](#exercise-3-fill-in-what-got-worse) | Write down two costs of what you just did. | None. |

> **Safety net:** exercise 1 started by tightening `SumWordsLength`'s existing test, because the
> code worked and the test had to pin it down before the change. Exercise 2 is new code,
> so there's nothing to pin down: you write each test before the code that passes it.
> [`../testing-first.md`](../testing-first.md) has the rules for concurrent code.

## Theory

### T1. Five words and one rule

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

### T2. The worked example

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

### T3. Pinning, and what changed in JDK 24

The numbers are from runs already done (verified), so you don't need to reproduce them.

Every tutorial written before 2025 says that blocking inside `synchronized` **pins** the virtual
thread, so the carrier is stuck. **That was true through JDK 23 and isn't true on the JDK this repo
uses.** JEP 491 (JDK 24) let virtual threads unmount while holding a monitor. The experiment: the
JVM limited to **one** carrier (`-Djdk.virtualThreadScheduler.parallelism=1`), thirty tasks, each
sleeping 100 ms inside `synchronized`:

| Lock | Time | What it means |
| --- | --- | --- |
| each task locks its own `new Object()` | **112 ms** | not pinned: all thirty unmounted while asleep |
| all tasks lock one shared object | **3016 ms** | **not pinning either**: they queue for the lock, one at a time |

The second row is the trap. It looks like pinning, and it's just a lock doing its job: only one
thread may hold it. On JDK 21 *both* rows would take about 3000 ms with one carrier, and the
first row is the pinning one. (That's from JEP 491's description of the old behaviour, not a run:
only JDK 25 and 26 are installed here.) **Telling those two apart is the point of this section.**

Two more things that changed:

- `-Djdk.tracePinnedThreads=full`, which most articles tell you to use, is **silently ignored** on
  JDK 26. No output, no warning.
- The way to see pinning now is Flight Recorder:

      java -XX:StartFlightRecording=filename=pin.jfr,settings=profile ...
      jfr print --events jdk.VirtualThreadPinned pin.jfr

  For the experiment above it records **zero** `jdk.VirtualThreadPinned` events, for either row.

**What still pins on JDK 26** is a virtual thread blocking while a **native frame** is on its
stack: Java code called back *from* C. Calling C's `qsort` through the foreign function API with a
Java comparator that sleeps (one carrier, ten tasks):

    10 virtual threads, each sleeping 100 ms inside a qsort callback: 1043 ms

That's one after another, and JFR records ten events with
`pinnedReason = "Native or VM frame on stack"`. The foreign-function API is
[doc 23](23-foreign-function-and-memory.md)'s subject, and that's where to try it if you want to.
The point to take from here is simpler: **pinning still exists, it moved, and the old advice to
avoid `synchronized` is now wrong.**

### T4. `SimpleThreads` on a virtual thread

What happens if
`concurrency/SimpleThreads.java:45`, which starts its worker as `new Thread(new MessageLoop())`,
used `Thread.ofVirtual().unstarted(new MessageLoop())` instead, run with `5` (give up after five
seconds). Verified output:

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
  `SumWordsLength` leak fixed in exercise 1, and just as quiet. Exercise 2 runs into it
  directly.

### T5. Structured concurrency: not here

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
> Loose ends: nothing tests a task failing (`ExecutionException`), and the scratch timing run
> below can't be checked from the repo.
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
inside `call()`), give it thirty words, and reproduce the left column of T2's table with
both executors. Then make it spin instead of sleep, and reproduce the right column. **Being able
to predict both columns before you run them is the deliverable.**
[Doc 34](34-measuring-it-properly.md) says why a stopwatch around one run is weak evidence, and
how to do better.

**Done when:** the four commits are in, a `main` calling `calculate` exits on its own, and you
predicted both columns of T2's table before running them.

### Exercise 2: build `OrdersProcessingSystem`, a producer/consumer

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
   has finished. Decide what an interrupted consumer should do (stop, and keep the interrupt
   flag set: the same rule as exercise 1, step 2) and write it down in the test name.
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

### Exercise 3: fill in "What got worse"

Do this last, once exercise 2 is in. Write at least two things that virtual threads made
worse or riskier, in your own words.

- _(fill this in)_
- _(fill this in)_

Prompts: what does an unbounded number of concurrent calls do to the service on the other end?
Where does backpressure live once the pool is not providing it by accident? (The pool of 3 was
also a limit of three simultaneous calls; `newVirtualThreadPerTaskExecutor` has no limit at all.)
Your `LinkedBlockingQueue` in exercise 2: is it bounded, and what happens to memory if the
producer is faster than the consumers? What happens to a `ThreadLocal` when there are a million threads? And when the advice you learned
("avoid `synchronized` with virtual threads") stops being true two releases later, how would you
have found out?
