# 06. Virtual threads

> **No safety net:** `OrdersProcessingSystem`, `SimpleThreads` have no tests. Write them first,
> in a separate commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why
> the order matters.

## Where it lands

**`concurrency/SumWordsLength.java`** is the one to start with. It splits a string, submits one
`Callable` per word to a fixed pool of three, and collects `Future`s into a `HashSet`. The work is
`word.length()`, so the pool is pure overhead, but the *shape* is exactly right: many small tasks,
submitted, awaited.

It also has two defects worth fixing before you touch the threading:

- `catch (Exception e) { System.out.println(e.getMessage()); }` swallows an `ExecutionException`
  and returns a partial sum as though it were correct.
- the `ExecutorService` is never shut down, so the JVM does not exit.

**`concurrency/SimpleThreads.java`** sleeps four seconds per message on a platform thread and
handles interruption. Good for watching what a virtual thread does to the same code.

**`concurrency/OrdersProcessingSystem.java`** is a stub: `start()` is empty and the inner
`Runnable.run()` is empty. It is not a conversion, it is a blank page with a queue already
declared. Build it with virtual threads from scratch.

## The exercise

1. Fix the two defects in `SumWordsLength` first, as their own commit.
2. Convert to `Executors.newVirtualThreadPerTaskExecutor()` and use try-with-resources, because
   the executor is now `AutoCloseable`.
3. Make the task actually block, with a sleep, so the numbers mean something. Time the fixed pool
   against virtual threads and be able to explain why a pool of 3 takes roughly
   `tasks / 3 * blockMillis` and virtual threads take roughly `blockMillis`, **and why that stops
   being true the moment the work is CPU-bound**.
4. **Then learn pinning**, because it is the follow-up question. Wrap the blocking call in a
   `synchronized` block, re-run, and write down what happened and why.

## Structured concurrency, optional, with a caveat

Structured concurrency is **still a preview API** and its shape changed between JDK 21 and later
releases: the `ShutdownOnFailure` subclass in most tutorials is not how JDK 25 spells it. That is
why it is not part of the compiled exercise.

If you want it anyway, add `--enable-preview` to the compiler args and the surefire `argLine`, and
**check the API against your own JDK's javadoc rather than a blog post**.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: what does an unbounded number of concurrent calls do to the service on the other end?
Where does backpressure live once the pool is not providing it by accident? What happens to a
`ThreadLocal` when there are a million threads?
