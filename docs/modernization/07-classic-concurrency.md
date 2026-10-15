# 07. Classic concurrency

> **No safety net:** `Drop`, `ForkBlur` have no tests. Write them first, in a separate commit,
> asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why the order matters.

## Where it lands

**`concurrency/guardedLock/`** is a textbook `wait`/`notifyAll` guarded block: `Drop` holds a
single message and a `boolean empty`, with `synchronized take()` and `put()` looping on `wait()`.
This is the canonical thing that `BlockingQueue` replaced.

It also has the classic interruption bug, twice:

    catch (InterruptedException e) {}

An empty catch on `InterruptedException` swallows the interrupt and clears the flag, so nothing
upstream can ever cancel this. Fix that before anything else, and know the two correct responses:
restore the flag with `Thread.currentThread().interrupt()`, or let the exception propagate.

**`concurrency/forkjoin/ForkBlur.java`** is a `RecursiveAction` fork/join blur over a
`BufferedImage`. Leave the algorithm alone. It is also the workload for document 11.

**`SumWordsLengthCallableResultsSampleTest`** is three `Callable`/`Future` tests that never ran
until the class was renamed to match the Surefire pattern. They pass. Read them before you touch
`SumWordsLength`, because they are the only description of what its current behaviour is.

## The exercise

1. Fix the swallowed interrupts across `guardedLock/`.
2. Replace `Drop` with a `SynchronousQueue` or an `ArrayBlockingQueue` of capacity 1. Count the
   lines you deleted. Then answer the harder question: **what did the explicit `wait`/`notifyAll`
   version make visible that the queue now hides?**
3. Strip the `System.out.println` tracing as you go. There are eleven calls in `Drop` alone, and
   they are there because the original was a teaching aid. A test asserting the handoff order is
   the modern replacement.

## What got worse

- _(fill this in)_

Prompts: what does `CompletableFuture` do to a stack trace when something throws three
compositions deep? Which idiom can you actually debug at three in the morning?
