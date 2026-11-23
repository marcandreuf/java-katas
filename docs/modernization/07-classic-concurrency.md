# 07. Classic concurrency

**Java 1.0** (`wait`/`notifyAll`) · **Java 5** (`java.util.concurrent`) · **Java 7** (fork/join)
· concept stage 07 of [the concept order](00-start-here.md) · no build change

> **No safety net:** `Consumer`, `Drop`, `ForkBlur`, `Producer` have no tests. Write them first,
> in a separate commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why
> the order matters. Most of this is concurrent, so its "Concurrency is the exception" section is
> the rule that applies here.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `guardedLock/`](#exercise-1-fix-the-interrupt-bugs-in-guardedlock) | Make the delay injectable, test the handoff, fix the interrupt bugs, deal with `Drop`, strip the tracing. Four commits. | T1–T5 | Characterize first: the order test, and a test that records today's hang. |
| [2. `ForkBlur`](#exercise-2-forkblur-test-the-arithmetic-leave-the-threads-alone) | Test that the fork/join version equals the one-thread version, and pin down the edge behaviour. | T6 | Two characterization tests; no code change. |
| [3. What got worse](#exercise-3-fill-in-what-got-worse) | Write down what the newer idioms cost. | all | None. |
| [4. Review questions](#exercise-4-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the six sections in order: each one builds on the one before. Here is the whole story in one
paragraph, so you know where it's going.

> Two threads that share state need a lock, and every Java object carries one: the monitor (T1).
> When a thread needs to wait until the state is right ("until there's a message"), it uses
> `wait()` and `notifyAll()` inside a loop called a guarded block (T2). Asking a waiting thread to
> stop is done with an interrupt, and the one place it gets lost is the `catch` block that
> receives it (T3). Java 5 wrote the guarded block once, correctly, as `BlockingQueue`, in three
> sizes that behave differently (T4). But a better queue doesn't fix bad interrupt handling: cancel
> one side of a handoff and the other side hangs (T5). Fork/join is a different tool, for
> splitting one big computation, and it's the easiest concurrent code to test, because it has an
> exact answer to compare with (T6).

Every code sample below is a complete program unless it says otherwise. Save it as the class name
plus `.java` and run it with `java Name.java`; no build needed. Outputs marked *verified* were run
on JDK 26.

### T1. The monitor: the lock every object carries

When two threads read and write the same fields, they need a way to take turns. Java's built-in
answer, since 1.0, is that **every object carries a lock**, called its **monitor**.
`synchronized` takes it:

```java
public synchronized void put(String message) { ... }   // takes the monitor of `this`

synchronized (lock) { ... }                            // takes the monitor of `lock`
```

Only one thread at a time can hold a given monitor. Any other thread that reaches a `synchronized`
block on the same object waits at the door until the holder leaves. That's all a monitor does: it
makes a block of code run one thread at a time. (What it also guarantees about *seeing* other
threads' writes is [doc 08](08-memory-model.md)'s subject.)

### T2. Waiting for a condition: `wait`, `notifyAll` and the guarded block

A lock lets threads take turns. It doesn't let a thread say "wake me when there's something to
do". Holding the lock and checking in a loop would block everyone else; letting go and checking
again in a loop burns a CPU. Java's answer is a pair of methods on every object:

- **`wait()`** releases the monitor and sleeps until another thread calls `notifyAll()` on the
  same object. When it wakes, it takes the monitor back before returning.
- **`notifyAll()`** wakes every thread waiting on that object.

You may only call either while holding the monitor; otherwise they throw
`IllegalMonitorStateException`.

The loop around `wait()` is called a **guarded block**: `while (!condition) wait();`. It's a
`while`, not an `if`, because waking up doesn't mean the condition is now true. Another thread may
have got there first and changed it back, or the thread may wake for no reason at all (a
"spurious wakeup", which the JVM is allowed to do). So the thread checks again every time it
wakes.

Here is the smallest useful example: a gate that threads wait at until someone opens it.

```java
public class Gate {
    private boolean open = false;                 // the state the guard checks

    public synchronized void await() throws InterruptedException {
        while (!open) {                           // a loop, not an if
            wait();                               // releases the monitor while asleep
        }
    }

    public synchronized void open() {
        open = true;                              // change the state first...
        notifyAll();                              // ...then wake everyone who is waiting on it
    }

    public static void main(String[] args) throws InterruptedException {
        Gate gate = new Gate();
        Runnable waiter = () -> {
            try {
                gate.await();
                System.out.println(Thread.currentThread().getName() + ": through the gate");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Thread a = new Thread(waiter, "a");
        Thread b = new Thread(waiter, "b");
        a.start();
        b.start();
        Thread.sleep(200);
        System.out.println("main: a is " + a.getState() + ", b is " + b.getState());
        gate.open();
        a.join();
        b.join();
    }
}
```

```text
main: a is WAITING, b is WAITING                         (verified 2026-11-23)
b: through the gate
a: through the gate
```

Both threads waited inside `await()` without holding the lock (otherwise `open()` could never
have run), and one `notifyAll()` released both. The order of the last two lines varies.

`guardedLock/Drop.java` is the same pattern with two conditions instead of one: `take()` waits
while the slot is empty, and `put()` waits while it's full.

### T3. Interruption, and where it gets lost

[Doc 06, T6](06-virtual-threads.md#t6-the-interrupt-flag-propagate-it-or-restore-it) introduced the
interrupt flag. Here is the short version, because this doc's bugs all come from it:
`t.interrupt()` is Java's only way to ask a thread to stop. It sets a flag on `t`. If `t` is
blocked in `wait`, `sleep`, `join`, `take` or `put`, that call throws `InterruptedException`
**and clears the flag**.

That gives one rule, and every bug in this doc is a variation on it:

> **When you catch `InterruptedException`, the request to stop is now in your hands, and nobody
> else can see it.**

The flag has been cleared. If you don't act on it (stop, or propagate it, or set the flag again),
the request is gone. Inside a guarded block, that's worse than it sounds, because the loop goes
straight back to `wait()`:

```java
public class Stuck {
    private boolean open = false;

    // The bug: the catch is inside the loop, and it's empty.
    public synchronized void awaitSwallowing() {
        while (!open) {
            try {
                wait();
            } catch (InterruptedException e) { }  // the request to stop is dropped here
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Stuck gate = new Stuck();
        Thread t = new Thread(gate::awaitSwallowing, "waiter");
        t.start();
        Thread.sleep(200);

        t.interrupt();                            // please stop
        t.join(2_000);                            // give it two seconds
        System.out.println("after interrupt + 2 s: alive=" + t.isAlive()
                + " state=" + t.getState() + " flag=" + t.isInterrupted());
        System.exit(0);                           // the waiter would keep the JVM alive forever
    }
}
```

```text
after interrupt + 2 s: alive=true state=WAITING flag=false   (verified 2026-11-23)
```

The interrupt woke `wait()`, which threw. The empty `catch` dropped it, the flag was already
cleared, and the `while` loop went round and called `wait()` again. **The thread can't be
cancelled.** Only `open()` will ever release it.

The repo's `Drop.take()` (`concurrency/guardedLock/Drop.java:16-24`, from the Oracle tutorial) has
exactly this shape, and behaves exactly the same way (verified):

```java
public synchronized String take() {
    while (empty) {
        try {
            wait();
        } catch (InterruptedException e) {}
    }
    ...
}
```

```text
after interrupt + 2 s: alive=true interrupted flag=false
```

There are two correct responses, and you should be able to name both:

```java
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
```

Response 1 is almost always right for a method like `take()`: that's what `Gate.await()` in T2
does. Response 2 is for a `run()`, which isn't allowed to throw checked exceptions: that's what
`Gate`'s `waiter` does. Restoring the flag and then *carrying on* is not a third option. It looks
like 2, and it behaves like the empty `catch`.

### T4. `BlockingQueue`: the guarded block, written once

Every producer/consumer program written with `wait`/`notifyAll` has the same guarded blocks in it,
and every one is a chance to get them wrong. Java 5 wrote them once, correctly, in the JDK. A
**`BlockingQueue`** is a queue whose `put()` waits for space and whose `take()` waits for an
element. Both propagate `InterruptedException` (response 1 from T3).

How much space there is decides how the producer and consumer move together, and it's the main
choice you make. `offer()` is a `put()` that gives up at once instead of waiting, so it shows the
capacity directly, with no consumer running:

```java
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;

public class Slots {
    public static void main(String[] args) {
        // offer() is put() that gives up at once instead of waiting. No consumer is running.
        BlockingQueue<String> none = new SynchronousQueue<>();
        BlockingQueue<String> one  = new ArrayBlockingQueue<>(1);
        BlockingQueue<String> many = new LinkedBlockingQueue<>();

        System.out.println("SynchronousQueue      offer: " + none.offer("x") + " " + none.offer("y"));
        System.out.println("ArrayBlockingQueue(1) offer: " + one.offer("x") + " " + one.offer("y"));
        System.out.println("LinkedBlockingQueue   offer: " + many.offer("x") + " " + many.offer("y"));
    }
}
```

```text
SynchronousQueue      offer: false false                 (verified 2026-11-23)
ArrayBlockingQueue(1) offer: true false
LinkedBlockingQueue   offer: true true
```

| Queue | Holds | `put()` returns when | The producer can get ahead by |
| --- | --- | --- | --- |
| `SynchronousQueue` | nothing | a consumer has taken the message: a **handoff** | nothing: they move in lockstep |
| `ArrayBlockingQueue<>(n)` | up to `n` | there's a free slot | `n` messages |
| `LinkedBlockingQueue<>()` | unbounded (`Integer.MAX_VALUE`) | at once, practically | as many as memory allows |

So "replace my hand-written guarded block with a `BlockingQueue`" isn't one change. It's three
different programs, and you have to pick the one whose capacity matches what the old code did.
Exercise 1 is a case where the wrong one was picked, and nothing noticed.

### T5. Cancelling one side of a handoff is a hang

A `BlockingQueue` fixes the guarded block. It doesn't fix what the threads do around it. Here a
producer hands three messages through a `SynchronousQueue`, and the consumer is interrupted after
the first one. Both threads handle the interrupt correctly by T3's rules: they restore the flag
and stop.

```java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

public class Hang {
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<String> queue = new SynchronousQueue<>();

        Thread producer = new Thread(() -> {
            try {
                for (String m : new String[] { "one", "two", "three" }) {
                    queue.put(m);                 // waits until someone takes it
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                System.out.println("consumer: got " + queue.take());
                Thread.sleep(10_000);             // pretend to work on it
                System.out.println("consumer: got " + queue.take());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("consumer: interrupted, stopping");
            }
        }, "consumer");

        producer.start();
        consumer.start();
        Thread.sleep(500);
        consumer.interrupt();                     // cancel one side only
        consumer.join();
        producer.join(2_000);
        System.out.println("producer alive=" + producer.isAlive() + " state=" + producer.getState());
        System.exit(0);
    }
}
```

```text
consumer: got one                                        (verified 2026-11-23)
consumer: interrupted, stopping
producer alive=true state=WAITING
```

The consumer quit cleanly. The producer is blocked in `put("two")` forever, waiting for a taker
who has gone away. Without the `System.exit(0)`, the program would never end.

> **Cancelling one side of a handoff without telling the other side is a hang.**

Nothing in either thread is wrong on its own. The bug is that nobody owned *both* threads. The
standard answer is that whoever started both threads also stops both, by interrupting each of them.
That's what `ExecutorService.shutdownNow()` does for every thread in a pool. And if one side
swallows its interrupt instead, as the repo's `Consumer` does, the hang is also silent: no stack
trace, no message, just a program that doesn't exit.

### T6. Fork/join: one big job, split in half until it's small

Everything so far is about threads that *coordinate*: one waits for another. Fork/join (Java 7)
is for a different problem: **one big computation** that can be cut into independent pieces.

The shape is always the same. A task looks at its range. If it's small enough, it does the work
directly. If not, it splits into two halves, runs both (`invokeAll`), and combines the results.
The `ForkJoinPool` runs the pieces on all cores, and lets idle threads steal pieces from busy ones.
`RecursiveTask<T>` returns a result; `RecursiveAction` (which `ForkBlur` uses) returns nothing and
writes into an array instead.

```java
import java.util.Random;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class SumTask extends RecursiveTask<Long> {
    private static final int THRESHOLD = 10_000;
    private final int[] numbers;
    private final int from, to;                   // the half-open range [from, to)

    SumTask(int[] numbers, int from, int to) {
        this.numbers = numbers;
        this.from = from;
        this.to = to;
    }

    long computeDirectly() {                      // the plain, one-thread version
        long sum = 0;
        for (int i = from; i < to; i++) sum += numbers[i];
        return sum;
    }

    @Override
    protected Long compute() {
        if (to - from <= THRESHOLD) {
            return computeDirectly();             // small enough: just do it
        }
        int mid = (from + to) >>> 1;              // too big: split in half
        SumTask left = new SumTask(numbers, from, mid);
        SumTask right = new SumTask(numbers, mid, to);
        invokeAll(left, right);                   // run both halves, maybe in parallel
        return left.join() + right.join();
    }

    public static void main(String[] args) {
        int[] numbers = new Random(42).ints(1_000_000, 0, 1_000).toArray();
        long direct = new SumTask(numbers, 0, numbers.length).computeDirectly();
        long forked = ForkJoinPool.commonPool().invoke(new SumTask(numbers, 0, numbers.length));
        System.out.println("direct=" + direct + " forked=" + forked + " equal=" + (direct == forked));
    }
}
```

```text
direct=499124766 forked=499124766 equal=true             (verified 2026-11-23)
```

Look at what `main` does: it computes the answer twice, once with one thread and once with
fork/join, and compares. That's why fork/join code is the easiest concurrent code to test. **The
one-thread version is an exact answer to compare with.** If a split ever dropped or counted an
element twice, the two numbers would differ. Unlike T5, there's no timing involved, so the test
can't be flaky. Exercise 2 uses exactly this idea on `ForkBlur`.

## Practice

### Exercise 1: fix the interrupt bugs in `guardedLock/`

#### What is actually there today

**This isn't the code the old version of this doc described.** By 2014, `Producer` and `Consumer`
had already been switched from `Drop` to a `BlockingQueue`. `ProducerConsumerExample.java:13`
creates a `SynchronousQueue`, and `Drop` is now used by nothing at all. So the job is not
"replace `Drop` with a queue". It is: the replacement has been done, so find out what it changed,
and what it left broken.

**`Drop` and `SynchronousQueue` are not the same thing** (T4). `Drop` holds **one** message.
`put()` returns as soon as it has stored it. A `SynchronousQueue` holds **none**: `put()` waits
until a consumer takes the message. Verified, with no consumer running:

```java
Drop.put("x")                          // returned after 5 ms
new SynchronousQueue<>().offer("x")    // false: no taker, so no handoff
new ArrayBlockingQueue<>(1).offer("x") // true: one slot, like Drop
```

**The 2014 swap changed the program's behaviour.** The equivalent of `Drop` is
`ArrayBlockingQueue` with capacity 1. With `SynchronousQueue`, the producer runs in lockstep with
the consumer instead of being able to get one message ahead. Nothing broke, because nothing
depended on it. Write down which one you would have chosen, and why.

**The interruption bugs, all of them** (each verified; T3):

| Where | What it does | What happens when interrupted |
| --- | --- | --- |
| `Drop.java:23`, `:44` | `catch (InterruptedException e) {}` inside the `while` | can't be cancelled at all: shown in T3 |
| `Consumer.java:28` | `catch (InterruptedException e) {}` around the whole loop | exits, prints the last message, flag lost |
| `Producer.java:32-34`, `:38-40` | prints `e.getMessage()`, carries on | prints `sleep interrupted` and produces **every** message anyway |

The `Consumer` one is the dangerous one, because of how it combines with a `SynchronousQueue`
(T5). Run the real pair, and interrupt the consumer after half a second (verified):

```text
MESSAGE RECEIVED: Mares eat oats
Mares eat oats
consumer alive=false
producer alive after 25 s=true state=WAITING
```

The consumer quit cleanly. The producer is now blocked in `put()` forever, waiting for a taker who
has gone away, and `ProducerConsumerExample` never exits. That's T5's hang, made silent by the
empty `catch`.

#### What to do

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
   what `ExecutorService.shutdownNow()` does (T5). The hang test's assertion flips in this commit.
3. **Deal with `Drop`.** It is used by nothing, so the honest options are to fix its two catches
   (response 1 in T3, `throws InterruptedException`) and keep it as the teaching example, or to
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

**Done when:** the four commits are in, the hang test's assertion flipped in commit 2 (not
before), `ProducerConsumerExample` exits on its own, and the written question is answered.

### Exercise 2: `ForkBlur`, test the arithmetic, leave the threads alone

`concurrency/forkjoin/ForkBlur.java` is a `RecursiveAction`. Below `sThreshold` (10,000 pixels) it
blurs directly. Above it, it splits the range in half and calls `invokeAll` on both halves (T6).
**Leave the algorithm alone**: [doc 11](11-jvm-diagnostics.md) uses this file as its workload, and
its measurements depend on it.

This is the cleanest concurrency case in the repo for `testing-first.md` rule 1, because the
concurrent version has a **deterministic answer to compare against**, just like `SumTask` in T6.
Verified, over 200,000 random pixels:

```java
new ForkBlur(src, 0, n, d1).computeDirectly();          // one thread
ForkJoinPool.commonPool().invoke(new ForkBlur(src, 0, n, d2));
Arrays.equals(d1, d2)                                    // true
```

`computeDirectly()` is `protected`, so the test goes in the same package. That one assertion
covers all the fork/join plumbing: if a split ever drops or double-counts a pixel, the arrays
differ. It's the invariant test `testing-first.md` asks for, and here it's exact, not
statistical.

**Then characterize what the blur does, because it isn't quite a horizontal blur.** It works on
the image flattened into one array, so the window doesn't stop at the end of a row. Verified on a
20×2 image with a black top row and a white bottom row:

```text
last pixel of row 0  = 0xFF777777       // should be black
first pixel of row 1 = 0xFF888888       // should be white
```

Each row's edge pixels are averaged with the far end of the next row. Pin that down in a test as
the current behaviour. Fixing it changes the output image, so it's a behaviour change in its own
commit, if you make it at all. It's the Oracle sample's simplification, not a typo. (An all-white
image does stay exactly white, `0xFFFFFFFF`, so the `float` arithmetic isn't losing anything at
the top of the range.)

One more thing to know before doc 11: `ForkBlur.main` reads `original.JPG` from the working
directory, and **there is no such file in the repo**, so `main` fails as it stands.

**Done when:** the equality test and the row-edge characterization test are in and green, and
`ForkBlur`'s algorithm is unchanged.

### Exercise 3: fill in "What got worse"

- _(fill this in)_

Prompts: what does `CompletableFuture` do to a stack trace when something throws three
compositions deep? Which idiom can you actually debug at three in the morning? A `BlockingQueue`
fixed the guarded block, but the interrupt bugs survived the swap from `Drop` untouched: what does
that say about what a better API can and can't fix?

### Exercise 4: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. What does `synchronized` actually take, and how many threads can hold it at once? (T1)
2. What does `wait()` do with the monitor while the thread sleeps, and what must be true before
   you're allowed to call it? (T2)
3. Why is a guarded block a `while` and not an `if`? Give both reasons. (T2)
4. A thread blocked in `wait()` is interrupted. What state is the flag in when the `catch` runs?
   (T3)
5. Why can't `Stuck.awaitSwallowing()` be cancelled, even though the interrupt did wake it? (T3)
6. Name the two correct responses to `InterruptedException`, and say which one fits `take()` and
   which fits `run()`. (T3)
7. No consumer is running. What does `offer("x")` return on a `SynchronousQueue`, and on an
   `ArrayBlockingQueue<>(1)`? Which of the two behaves like `Drop`? (T4)
8. Both threads in `Hang` handle their interrupt correctly. Why does the program still hang, and
   who should have prevented it? (T5)
9. Why is fork/join code easier to test than a producer/consumer? (T6)

**Done when:** you can answer all nine without looking.
