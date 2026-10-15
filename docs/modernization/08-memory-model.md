# 08. The memory model

> **No safety net:** `BadThreads`, `MyDeadlock`, `Safelock` have no tests. Write them first, in
> a separate commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why the
> order matters.

## Where it lands

**`concurrency/BadThreads.java`** is the visibility demo from the Oracle tutorials, and the
interesting part is what was done to it. The original two `message` writes are commented out and
replaced with an `EncapsulatedMessage` whose accessors are both `synchronized`. So the bug is
fixed, the commented-out lines are the bug, and the `Thread.sleep` that made it visible is
commented out too.

**Restore the original first.** Uncomment the plain `static String message`, put the sleep back,
and run it until it hangs or prints the wrong string. A visibility bug you have not seen fail is a
fact you have memorised, not one you understand.

**`concurrency/MyDeadlock.java`** and **`concurrency/Safelock.java`** are the lock-ordering pair:
one deadlocks, one avoids it with `tryLock`.

## The exercise

1. Reproduce the visibility failure in `BadThreads`. Then fix it three ways: `volatile`,
   `synchronized`, and `AtomicReference`. Keep one and say why.
2. **The question worth the time**: `volatile` fixes a stop flag and does not fix a counter. Be
   able to say why in one sentence. That distinction is the difference between having read about
   the memory model and understanding it.
3. Run `MyDeadlock` until it hangs, then take a thread dump with `jcmd <pid> Thread.print` and
   find the cycle in it. That is the same tool document 11 uses, on a bug you planted.

## What got worse

- _(fill this in)_

Prompts: `synchronized`, `AtomicLong` or `LongAdder` for a counter hit a million times a second,
and what changes your answer? What does each cost when contention is low?
