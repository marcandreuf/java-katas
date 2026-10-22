# 07. Classic concurrency

**Java 1.0** (`wait`/`notifyAll`) · **Java 5** (`java.util.concurrent`) · **Java 7** (fork/join)
· concept stage 07 of [the concept order](00-start-here.md) · no build change

> **No safety net:** `Consumer`, `Drop`, `ForkBlur`, `Producer` have no tests. Write them first,
> in a separate commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why
> the order matters. Most of this is concurrent, so its "Concurrency is the exception" section is
> the rule that applies here.

## What this stage is actually about

Five words, defined once:

- a **monitor** is the lock every Java object carries. `synchronized` takes it; only one thread
  at a time can hold it.
- **`wait()`** releases the monitor and sleeps until another thread calls **`notifyAll()`** on the
  same object. You may only call either while holding the monitor.
- a **guarded block** is the loop around `wait()`: `while (!condition) wait();`. It is a loop,
  not an `if`, because waking up doesn't mean the condition is now true.
- a **`BlockingQueue`** is a queue whose `put()` waits for space and whose `take()` waits for an
  element. It is the guarded block, written once, correctly, in the JDK.
- **interruption** is Java's only way to ask a thread to stop. `t.interrupt()` sets a flag on
  `t`. If `t` is blocked in `wait`, `sleep`, `join`, `take` or `put`, that call throws
  `InterruptedException` **and clears the flag**.

That gives one rule, and everything below follows from it: **when you catch
`InterruptedException`, the request to stop is now in your hands, and nobody else can see it.**
The flag has been cleared. If you don't act on it (stop, or propagate it, or set the flag again),
the request is gone. Every bug in this doc is a variation on that.

## The worked example

`concurrency/guardedLock/Drop.java:16-24`, from the Oracle tutorial, trimmed to the guarded
block:

    public synchronized String take() {
        while (empty) {
            try {
                wait();
            } catch (InterruptedException e) {}
        }
        ...
    }

Start a thread in `take()` on an empty `Drop`, interrupt it, and wait two seconds (verified):

    after interrupt + 2 s: alive=true interrupted flag=false

The interrupt woke `wait()`, which threw. The empty `catch` dropped it, and the flag was already
cleared, so the `while` loop went round and called `wait()` again. **The thread can't be
cancelled.** Only a `put()` will ever release it.

There are two correct responses, and you should be able to name both:

    // 1. propagate: declare it, and let the caller decide
    public synchronized String take() throws InterruptedException {
        while (empty) wait();
        ...
    }

    // 2. restore the flag, then stop doing what you were doing
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;            // or break, or throw something unchecked: but stop
    }

Response 1 is almost always right for a method like `take()`. Response 2 is for a `run()`, which
isn't allowed to throw checked exceptions. Restoring the flag and then *carrying on* is not a third
option. It looks like 2, and it behaves like the empty `catch`.

## Part one: what is actually in `guardedLock/` today

**This isn't the code the old version of this doc described.** By 2014, `Producer` and `Consumer`
had already been switched from `Drop` to a `BlockingQueue`. `ProducerConsumerExample.java:13`
creates a `SynchronousQueue`, and `Drop` is now used by nothing at all. So the job is not
"replace `Drop` with a queue". It is: the replacement has been done, so find out what it changed,
and what it left broken.

### `Drop` and `SynchronousQueue` are not the same thing

`Drop` holds **one** message. `put()` returns as soon as it has stored it. A `SynchronousQueue`
holds **none**: `put()` waits until a consumer takes the message. Verified, with no consumer
running:

    Drop.put("x")                          // returned after 5 ms
    new SynchronousQueue<>().offer("x")    // false: no taker, so no handoff
    new ArrayBlockingQueue<>(1).offer("x") // true: one slot, like Drop

**The 2014 swap changed the program's behaviour.** The equivalent of `Drop` is
`ArrayBlockingQueue` with capacity 1. With `SynchronousQueue`, the producer runs in lockstep with
the consumer instead of being able to get one message ahead. Nothing broke, because nothing
depended on it. Write down which one you would have chosen, and why.

### The interruption bugs, all of them (each verified)

| Where | What it does | What happens when interrupted |
| --- | --- | --- |
| `Drop.java:23`, `:44` | `catch (InterruptedException e) {}` inside the `while` | can't be cancelled at all: shown above |
| `Consumer.java:28` | `catch (InterruptedException e) {}` around the whole loop | exits, prints the last message, flag lost |
| `Producer.java:32-34`, `:38-40` | prints `e.getMessage()`, carries on | prints `sleep interrupted` and produces **every** message anyway |

The `Consumer` one is the dangerous one, because of how it combines with a `SynchronousQueue`.
Run the real pair, and interrupt the consumer after half a second (verified):

    MESSAGE RECEIVED: Mares eat oats
    Mares eat oats
    consumer alive=false
    producer alive after 25 s=true state=WAITING

The consumer quit cleanly. The producer is now blocked in `put()` forever, waiting for a taker who
has gone away, and `ProducerConsumerExample` never exits. **Cancelling one side of a handoff
without telling the other side is a hang**, and the empty `catch` is what makes it silent.

## Part two: the commits

[`../testing-first.md`](../testing-first.md) says concurrent code gets a test of its invariant, not
of its timing, and that's what's possible here once one thing changes: **both threads sleep for
`random.nextInt(5000)` ms**, which is up to twenty seconds for four messages and can't be
controlled from a test. [Doc 19](19-randomness-and-numerics.md) asks whether the `Random` should
be passed in. This is where that pays off.

1. **Make the delay injectable, and characterize.** Add a constructor that takes the maximum delay
   (or the `Random`, or a `Sleeper` interface: pick one and say why). Keep the old constructor
   delegating with the old value, so `ProducerConsumerExample` doesn't change. Then write the
   invariant test, with zero delay: **the consumer receives the four messages in order, and
   `DONE` ends it.** Collect what it receives into a list instead of only printing it. Then write
   one more test, for the hang: interrupt the consumer, and assert the producer is **still alive**
   after a timeout. It records today's bug; it passes because the bug is there.
2. **Fix `Consumer` and `Producer`.** Restore the flag and stop. Then decide how the other side
   finds out: the standard answer is that whoever owns both threads interrupts both, which is
   what `ExecutorService.shutdownNow()` does. The hang test's assertion flips in this commit.
3. **Deal with `Drop`.** It is used by nothing, so the honest options are to fix its two catches
   (response 1 above, `throws InterruptedException`) and keep it as the teaching example, or to
   delete it. **If you delete it, also update `licenses/oracle-bsd-3.txt`**, which lists it as one
   of the eight Oracle files. A licence notice listing a file that no longer exists is harmless
   but wrong.
4. **Strip the tracing.** `Drop` has 8 `System.out.println` calls (`grep -c`; the old version of
   this doc said eleven), `Producer` and `Consumer` four more between them. The list your test
   collects in step 1 is the replacement: it states the handoff order, while the `println`s only
   showed it to whoever was watching.

**Then the question to answer in writing:** what did the explicit `wait`/`notifyAll` version make
visible that the queue now hides? (Hint: count how many of `Drop`'s comments describe *state*.
The queue still has that state. You just can't see it any more.)

## Part three: `ForkBlur`, test the arithmetic, leave the threads alone

`concurrency/forkjoin/ForkBlur.java` is a `RecursiveAction`. Below `sThreshold` (10,000 pixels) it
blurs directly. Above it, it splits the range in half and calls `invokeAll` on both halves. **Leave
the algorithm alone**: [doc 11](11-jvm-diagnostics.md) uses this file as its workload, and its
measurements depend on it.

This is the cleanest concurrency case in the repo for `testing-first.md` rule 1, because the
concurrent version has a **deterministic answer to compare against**. Verified, over 200,000
random pixels:

    new ForkBlur(src, 0, n, d1).computeDirectly();          // one thread
    ForkJoinPool.commonPool().invoke(new ForkBlur(src, 0, n, d2));
    Arrays.equals(d1, d2)                                    // true

`computeDirectly()` is `protected`, so the test goes in the same package. That one assertion
covers all the fork/join plumbing: if a split ever drops or double-counts a pixel, the arrays
differ. It's the invariant test `testing-first.md` asks for, and here it's exact, not
statistical.

**Then characterize what the blur does, because it isn't quite a horizontal blur.** It works on
the image flattened into one array, so the window doesn't stop at the end of a row. Verified on a
20×2 image with a black top row and a white bottom row:

    last pixel of row 0  = 0xFF777777       // should be black
    first pixel of row 1 = 0xFF888888       // should be white

Each row's edge pixels are averaged with the far end of the next row. Pin that down in a test as
the current behaviour. Fixing it changes the output image, so it's a behaviour change in its own
commit, if you make it at all. It's the Oracle sample's simplification, not a typo. (An all-white
image does stay exactly white, `0xFFFFFFFF`, so the `float` arithmetic isn't losing anything at
the top of the range.)

One more thing to know before doc 11: `ForkBlur.main` reads `original.JPG` from the working
directory, and **there is no such file in the repo**, so `main` fails as it stands.

## What got worse

- _(fill this in)_

Prompts: what does `CompletableFuture` do to a stack trace when something throws three
compositions deep? Which idiom can you actually debug at three in the morning? A `BlockingQueue`
fixed the guarded block, but the interrupt bugs survived the swap from `Drop` untouched: what does
that say about what a better API can and can't fix?
