# 15. `CompletableFuture`

**Java 8** · `orTimeout`, `failedFuture` are **Java 9** · stage 4 of
[the release-ordered track](INDEX.md) · compiles at `--release 21`, no build change

`Fanout` is new code with its tests already written. The old code this replaces,
`SumWordsLength`, belongs to [doc 06](06-virtual-threads.md), and this doc doesn't change it.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. Read the old code](#exercise-1-read-the-old-code) | Find the three problems in `SumWordsLength`, and which one this API doesn't fix. No change. | T1, T6 | None. |
| [2. `Fanout`](#exercise-2-fanout-four-todos) | Four TODOs, one commit each. | T2–T6 | `FanoutTest`, nine tests, already written. Delete its `@Disabled` to begin. Add one more for TODO 4. |
| [3. Three ways to fan out](#exercise-3-pick-one-of-three-ways-to-fan-out) | Pick one concrete case and say which of three tools you'd use, and why. | all, plus doc 06 | None. |
| [4. What got worse](#exercise-4-fill-in-what-got-worse) | Write down what the API costs. | all | None. |
| [5. Review questions](#exercise-5-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the seven sections in order: each one builds on the one before. Here is the whole story in
one paragraph, so you know where it's going.

> A plain `Future` can only be waited on, so code that uses one ends up blocking a thread for
> every step. A `CompletableFuture` lets you say *what happens next* instead, and keep going (T1).
> Every such call attaches a new stage and returns at once, or it waits: there's nothing in between
> (T2). The question nobody asks until it bites is *which thread runs the stage*, and the answer
> is "whoever is there", or a small pool shared with the whole JVM (T3). Two futures combine into
> one (T4). A stage whose function itself returns a future needs `thenCompose`, the `flatMap` of
> futures (T5). A failure skips every stage after it and arrives wrapped, in a different wrapper
> depending on how you wait (T6). And Java 9 added timeouts as stages too (T7).

Every code sample below is a complete program unless it says otherwise. Save it as the class name
plus `.java` and run it with `java Name.java`; no build needed. All of them also compile at
`--release 21`, the level this doc targets. Outputs marked *verified* were run on JDK 26, 10
cores.

### T1. From `Future` to `CompletableFuture`: say what happens next

Java 5's `Future<T>` is a receipt for a value that an `ExecutorService` will compute later. It
has one useful method, `get()`, and `get()` waits. So if you want "fetch the price, then add tax",
the "then" has to happen on some thread that sat waiting for the price:

```java
int withTax = (int) (future.get() * 1.2);   // this thread blocks until the price exists
```

Java 8's **`CompletableFuture<T>`** is a `T` that will exist later, or a failure that will exist
instead. Unlike a plain `Future`, you can attach what happens next without waiting for it. Here is
the same job both ways:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class OldFuture {
    static int price(String item) {
        try { Thread.sleep(200); } catch (InterruptedException e) { throw new RuntimeException(e); }
        return item.length() * 10;
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            // A plain Future: the only thing you can do with it is wait.
            Future<Integer> f = pool.submit(() -> price("apple"));
            int withTax = (int) (f.get() * 1.2);        // blocks here, 200 ms
            System.out.println("Future:            " + withTax);

            // A CompletableFuture: say what happens next, and keep going.
            CompletableFuture<Integer> cf = CompletableFuture
                    .supplyAsync(() -> price("apple"), pool)
                    .thenApply(p -> (int) (p * 1.2));   // attached, not run yet
            System.out.println("CompletableFuture: attached, isDone=" + cf.isDone());
            System.out.println("CompletableFuture: " + cf.join()); // now wait
        }
    }
}
```

```text
Future:            60                                    (verified 2026-11-23)
CompletableFuture: attached, isDone=false
CompletableFuture: 60
```

Same answer. The difference is where the waiting is. With `Future`, the tax step can't even be
written until the wait is over. With `CompletableFuture`, the whole recipe is written first, and
`main` waits once, at the end, only because it wants to print. (The try-with-resources on the
executor is [doc 06, T2](06-virtual-threads.md#t2-the-rule-in-numbers)'s pattern: `close()` waits
for the tasks, then shuts the pool down.)

### T2. Attach or wait: there's nothing in between

The vocabulary, defined once:

- a **stage** is one step attached to a future: `thenApply`, `thenCompose`, `exceptionally`. Each
  one returns a **new** future and leaves the original alone.
- **`join()`** and **`get()`** are the two ways to **wait** for the value. Everything else
  attaches a stage and returns at once.
- an **`Executor`** is what runs a stage's function. The methods without an `Executor` parameter
  use `ForkJoinPool.commonPool()`, the one shared pool in the JVM.
- a **wrapper exception** is the exception a failure arrives inside: `ExecutionException` from
  `get()`, `CompletionException` from `join()` and from later stages. The original is its
  `getCause()`.

That gives one rule, and everything below follows from it:

> **Every call either attaches a stage or waits. Know which one you're writing, and which thread
> will run your function.**

Mistakes with this API are rarely about the arithmetic. They are about waiting in the wrong place,
running on the wrong pool, or catching the wrong wrapper.

You can see "attach" happen with no threads at all. `new CompletableFuture<>()` is an empty
future, and `complete(value)` fills it by hand:

```java
import java.util.concurrent.CompletableFuture;

public class Stages {
    public static void main(String[] args) {
        CompletableFuture<String> start = new CompletableFuture<>();   // nothing in it yet
        CompletableFuture<Integer> length = start.thenApply(String::length);
        CompletableFuture<String> label = length.thenApply(n -> n + " letters");

        System.out.println("before: start=" + start.isDone() + " label=" + label.isDone());

        start.complete("pear");                                        // fill the first one

        System.out.println("after:  start=" + start.isDone() + " label=" + label.isDone());
        System.out.println("start still holds " + start.join() + ", label holds " + label.join());
    }
}
```

```text
before: start=false label=false                          (verified 2026-11-23)
after:  start=true label=true
start still holds pear, label holds 4 letters
```

Two `thenApply` calls built a chain of three futures, and ran nothing. Completing the first one
ran the whole chain. And `start` still holds `"pear"`: a stage never changes the future it was
attached to. It makes a new one.

### T3. Which thread runs your function?

This is the question the API doesn't make you answer, and the one that causes the production
incidents. Ask it directly:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class WhichThread {
    static final Thread MAIN = Thread.currentThread();

    // Name the kind of thread, not the exact worker: worker numbers vary from run to run.
    static String who() {
        Thread t = Thread.currentThread();
        if (t == MAIN) return "main";
        if (t.getName().startsWith("ForkJoinPool.commonPool")) return "common pool";
        return t.getName();
    }

    public static void main(String[] args) throws InterruptedException {
        Function<String, String> fn = s -> who();

        System.out.println("thenApply on a done future:  "
                + CompletableFuture.completedFuture("x").thenApply(fn).join());

        CompletableFuture<String> pending = new CompletableFuture<>();
        CompletableFuture<String> attached = pending.thenApply(fn);
        Thread completer = new Thread(() -> pending.complete("x"), "completer");
        completer.start();
        completer.join();
        System.out.println("thenApply, completed later:  " + attached.join());

        System.out.println("thenApplyAsync, no executor: "
                + CompletableFuture.completedFuture("x").thenApplyAsync(fn).join());

        ExecutorService mine = Executors.newSingleThreadExecutor(r -> new Thread(r, "my-pool"));
        System.out.println("thenApplyAsync, my executor: "
                + CompletableFuture.completedFuture("x").thenApplyAsync(fn, mine).join());
        mine.shutdown();
    }
}
```

```text
thenApply on a done future:  main                        (verified 2026-11-23, 3 runs)
thenApply, completed later:  completer
thenApplyAsync, no executor: common pool
thenApplyAsync, my executor: my-pool
```

So **plain `thenApply` doesn't choose a thread**. It runs on whichever thread happens to be there:
the caller if the future is already done, otherwise the thread that completes it. The `Async`
versions without an executor go to the common pool. The `Async` versions *with* an executor go
where you said. (The old version of this doc printed the raw names, verified on JDK 26:
`supplyAsync(fn)` and `completedFuture("x").thenApplyAsync(fn)` both ran on
`ForkJoinPool.commonPool-worker-1`. The worker number isn't stable, which is why `who()` above
prints only the kind.)

**The common pool is small**: **9 threads on 10 cores**, and **1 thread** with
`-XX:ActiveProcessorCount=2` (both verified). It's shared with every parallel stream in the JVM.
Fill it with blocking work and watch something unrelated wait:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

public class Starved {
    public static void main(String[] args) {
        int size = ForkJoinPool.commonPool().getParallelism();
        System.out.println("common pool threads: " + size);

        List<CompletableFuture<Void>> blockers = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            blockers.add(CompletableFuture.runAsync(() -> {
                try { Thread.sleep(2_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }));
        }

        long start = System.nanoTime();
        int one = CompletableFuture.supplyAsync(() -> 1).join();    // trivial work
        long waited = (System.nanoTime() - start) / 1_000_000;
        System.out.println("supplyAsync(() -> 1) returned " + one + " after " + waited + " ms");
        blockers.forEach(CompletableFuture::join);
    }
}
```

```text
common pool threads: 9                                   (verified 2026-11-23)
supplyAsync(() -> 1) returned 1 after 2004 ms
```

With `java -XX:ActiveProcessorCount=2 Starved.java` it prints `common pool threads: 1` and still
waits about 2000 ms (verified): on a two-core container, **one** blocking call does this. (The old
version of this doc measured the same thing as "an unrelated supplyAsync behind 9 blockers waited
1900 ms", verified; the exact number depends on how long the blockers had already slept.)

A task that returns a constant took two seconds, because every common-pool thread was asleep. It's
the most common `CompletableFuture` failure in production, and it's why every method in `Fanout`
takes its `Executor` as a parameter. If the work blocks, give it its own executor; on Java 21, a
virtual-thread one ([doc 06, T1](06-virtual-threads.md#t1-what-a-virtual-thread-is-and-the-one-rule)).

### T4. Two futures into one: `thenCombine`

Often you need two independent results before you can go on: a price and a shipping cost. Start
both, then combine them. `thenCombine` attaches a stage that runs when **both** are done:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Combine {
    static int slow(int value, long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { throw new RuntimeException(e); }
        return value;
    }

    public static void main(String[] args) {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            long start = System.nanoTime();
            CompletableFuture<Integer> price = CompletableFuture.supplyAsync(() -> slow(40, 300), pool);
            CompletableFuture<Integer> shipping = CompletableFuture.supplyAsync(() -> slow(5, 300), pool);

            CompletableFuture<Integer> total = price.thenCombine(shipping, Integer::sum);

            System.out.println("total = " + total.join());
            long ms = (System.nanoTime() - start) / 1_000_000;
            System.out.println("both ran at once: " + (ms < 550) + " (" + ms + " ms)");
        }
    }
}
```

```text
total = 45                                               (verified 2026-11-23, 3 runs)
both ran at once: true (307 ms)
```

Two 300 ms tasks, about 300 ms in total: they ran side by side, and the sum waited for both. The
millisecond figure varies by run; the `true` doesn't.

That's two futures. For **N** futures there's `CompletableFuture.allOf(...)`, and it's less
friendly than it looks: its value is `null`, a `CompletableFuture<Void>`, because the futures you
pass it may all have different types. It tells you *when* they're all done, not *what* they
produced. Getting the values back, in the right order, is exactly `Fanout`'s TODO 1, so it's left
for exercise 2.

### T5. A function that returns a future: `thenApply` versus `thenCompose`

Sometimes the next step is itself asynchronous: "given the user id, look up their city", where
the lookup returns a `CompletableFuture<String>`. Attach it with `thenApply`, and look at the type
you get:

```java
import java.util.concurrent.CompletableFuture;

public class Nested {
    // An async step: given a user id, look up their city "later".
    static CompletableFuture<String> cityOf(int userId) {
        return CompletableFuture.completedFuture(userId == 7 ? "Girona" : "unknown");
    }

    public static void main(String[] args) {
        CompletableFuture<Integer> user = CompletableFuture.completedFuture(7);

        // thenApply with a function that itself returns a future:
        CompletableFuture<CompletableFuture<String>> nested = user.thenApply(Nested::cityOf);

        System.out.println("nested.join():        " + nested.join().getClass().getSimpleName());
        System.out.println("nested.join().join(): " + nested.join().join());
    }
}
```

```text
nested.join():        CompletableFuture                  (verified 2026-11-23)
nested.join().join(): Girona
```

A future of a future: two `join()`s to reach the value. Every "my async code returns a future of
a future" question is that line. It's the same problem as `map` versus `flatMap` on streams
([doc 09, T7](09-streams-and-optional.md)), and it has the same answer:

| Method | The function you pass returns | You get back |
| --- | --- | --- |
| `thenApply(fn)` | a plain `U` | `CompletableFuture<U>` |
| `thenCompose(fn)` | a `CompletableFuture<U>` | `CompletableFuture<U>`: flattened, one level |

The types tell you which one to use. If your function already returns a future, use
`thenCompose`. Rewriting `Nested` to use it is a two-word change, and it's `Fanout`'s TODO 2 in a
different shape, so try it there.

### T6. Failures: they skip stages, and they arrive wrapped

A failure in one stage doesn't stop the program, and it doesn't run the stages after it either. It
travels down the chain, skipping every `thenApply` on the way, until something handles it or
someone waits:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class Failures {
    public static void main(String[] args) throws InterruptedException {
        CompletableFuture<Integer> parsed = CompletableFuture.supplyAsync(() -> Integer.parseInt("forty"));
        CompletableFuture<Integer> doubled = parsed.thenApply(n -> {
            System.out.println("doubling " + n);       // never prints: this stage is skipped
            return n * 2;
        });

        try {
            doubled.join();
        } catch (CompletionException e) {
            System.out.println("join(): " + e.getClass().getSimpleName()
                    + ", cause " + e.getCause().getClass().getSimpleName());
        }

        try {
            doubled.get();
        } catch (ExecutionException e) {
            System.out.println("get():  " + e.getClass().getSimpleName()
                    + ", cause " + e.getCause().getClass().getSimpleName());
        }
        System.out.println("isCompletedExceptionally: " + doubled.isCompletedExceptionally());
    }
}
```

```text
join(): CompletionException, cause NumberFormatException   (verified 2026-11-23)
get():  ExecutionException, cause NumberFormatException
isCompletedExceptionally: true
```

Three things to take from it:

- **`doubling` never printed.** A failed stage skips the stages after it. Nothing tells you,
  unless you wait or attach a handler.
- **The same failure arrives in two wrappers.** `join()` throws `CompletionException`, which is
  unchecked. `get()` throws `ExecutionException`, which is checked, and declares
  `InterruptedException` as well (that one is [doc 06, T6](06-virtual-threads.md#t6-the-interrupt-flag-propagate-it-or-restore-it)'s
  subject). Either way, the original is `getCause()`.
- **Code that switches from `get()` to `join()` keeps compiling.** Its
  `catch (ExecutionException e)` silently stops matching, because `CompletionException` is
  unchecked and the compiler won't say a word.

To *handle* a failure as a stage, there are three methods, and they differ in what they can do:
`exceptionally`, `handle` and `whenComplete`. Exercise 2's TODO 3 has the table, and the trap in
it. TODO 4 has a third wrapping case that neither `join()` nor `get()` shows.

### T7. Timeouts are stages too (Java 9)

A future that never completes makes everyone who waits on it wait forever. Java 9 added two
stages that put a deadline on it:

```java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public class Timeouts {
    static String slowLookup() {
        try { Thread.sleep(2_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return "fresh";
    }

    public static void main(String[] args) {
        String a = CompletableFuture.supplyAsync(Timeouts::slowLookup)
                .completeOnTimeout("cached", 100, TimeUnit.MILLISECONDS)   // a default value
                .join();
        System.out.println("completeOnTimeout: " + a);

        try {
            CompletableFuture.supplyAsync(Timeouts::slowLookup)
                    .orTimeout(100, TimeUnit.MILLISECONDS)                 // a failure
                    .join();
        } catch (CompletionException e) {
            System.out.println("orTimeout: " + e.getCause().getClass().getName());
        }
    }
}
```

```text
completeOnTimeout: cached                                (verified 2026-11-23)
orTimeout: java.util.concurrent.TimeoutException
```

`completeOnTimeout` completes the future with a default value; `orTimeout` fails it with a
`TimeoutException`, which arrives wrapped like any other failure (T6). Both are about the
**future**. What happens to the `slowLookup` that's still sleeping when the deadline passes is a
different question, and this sample deliberately doesn't print it: it's one of exercise 4's
prompts. Find out before you answer.

Java 9 also added `CompletableFuture.failedFuture(e)`, a future that's already failed. It's the
quickest way to test a failure path, and exercise 2 uses it.

## Practice

### Exercise 1: read the old code

`concurrency/SumWordsLength.java:25-46` is the pattern this API was meant to replace:

```java
Set<Future<Integer>> setThreadResults = new HashSet<Future<Integer>>();   // line 28
...
for (Future<Integer> result : setThreadResults)
    sum += result.get();                                                  // line 38: one at a time
...
} catch (Exception e) { System.out.println(e.getMessage()); }            // line 41
```

Three things to notice. **Don't change the file here**: doc 06 owns it, and has already found that
the pool is never shut down (the JVM never exits) and that an interrupt returns a wrong sum.

- **The results come back in no order.** Iterating a `HashSet` of eight futures, submitted as
  tasks 0–7, gave `[7, 2, 0, 5, 4, 1, 6, 3]` (verified). A sum doesn't care. A list of results
  would.
- **The `catch` swallows the failure and returns whatever the sum was so far.**
  **A correction to the old version of this doc:** it said "a failed task produces a partial
  sum". In this file, no task can fail: `word.length()` on a piece of `split(" ")` never throws.
  The swallowing `catch` is reachable through an interrupt, which is the case doc 06 tested. It's
  still the worst failure mode available, a wrong answer returned as a right one, and
  `CompletableFuture` doesn't fix it. It only makes the correct version shorter.
- **`get()` in a loop waits for each task in turn** (T1). The total wait is the same as waiting
  for the slowest one, but the code describes waiting, not combining.

**Done when:** you can name the three problems without looking, and say which one
`CompletableFuture` doesn't fix on its own.

### Exercise 2: `Fanout`, four TODOs

`async/Fanout.java`. `FanoutTest` has nine tests. Delete its `@Disabled` to begin. All nine pass
against a scratch implementation (verified), so a failure is yours, not the test's. One commit per
TODO, each turning its tests green.

#### TODO 1: `all()`, and `allOf` returns `Void`

`CompletableFuture.allOf(...)` completes when every future you pass it completes, and its value is
`null` (verified: `allOf(completedFuture(1), completedFuture("two")).join()` is `null`). It can't
give you the values, because the futures may have different types (T4). That's the whole puzzle
of TODO 1: you know *when*, and you still have to get the values from somewhere, in **input
order**.

`all_preserves_input_order_not_completion_order` uses a `CountDownLatch` to force the second
task to finish strictly before the first, with no sleeps anywhere. An implementation that collects
results as each one finishes fails it, every run (verified):

```text
expected: <[first, second]> but was: <[second, first]>
```

**Read that test before you write anything.** A deterministic concurrency test, rather than a
usually-passing one, is worth more than the API. `all_fails_if_any_task_fails` is the other one to
read: one failure fails the whole fan-out, with no partial result. That's the property
`SumWordsLength` doesn't have.

#### TODO 2: `thenApply` vs `thenCompose`

This is `map` vs `flatMap`, and the types tell you which (T5). Write `lengthWithThenCompose` with
`thenApply` first, using a mapper that returns a future, and look at what you get (verified):

```java
CompletableFuture<CompletableFuture<Integer>> nested = ...
nested.join()          // CompletableFuture@6340e5f0[Completed normally]
nested.join().join()   // 1
```

(That's on `completedFuture("x")`, so the value is its length, 1.) Two `join()`s to reach the
value. Then fix it.

#### TODO 3: three error handlers

| Method | Called on | Can replace the outcome | Returns |
| --- | --- | --- | --- |
| `exceptionally` | failure only | yes | `CompletableFuture<T>` |
| `handle` | success and failure | yes | `CompletableFuture<U>` |
| `whenComplete` | success and failure | **no** | `CompletableFuture<T>` |

**`whenComplete` is the trap.** It looks like the general one, and it can't change the outcome:
the failure passes straight through it. Use it for `orElse` and the test fails with the original
exception still attached (verified):

```text
FanoutTest.or_else_substitutes_on_failure:101 » Completion java.lang.IllegalStateException: boom
```

And if the `whenComplete` action itself throws while handling a failure, the original failure
wins and the new exception is added to it as a *suppressed* one (verified). Pick your handler, and
write down what the other two would have done.

#### TODO 4: two wrappers, and a third case

```java
failedFuture(boom).get()    // ExecutionException,  getCause() == boom   (verified)
failedFuture(boom).join()   // CompletionException, getCause() == boom   (verified)
```

The same failure arrives in two wrapper types, one checked and one not (T6).

There's a third case, and the test doesn't cover it. A handler attached **directly** to the
failed future sees the original exception. A handler attached **one stage later** sees it wrapped
(verified):

```java
failedFuture(boom).exceptionally(e -> ...)                  // e is the IllegalStateException
failedFuture(boom).thenApply(x -> x).exceptionally(e -> ...) // e is a CompletionException, cause boom
```

So "unwrap the wrapper" has to cope with both. Add a test for the one-stage-later case before
you call TODO 4 done. That's how this bug gets found in real code: a handler that worked until
someone inserted a `thenApply` above it.

**Done when:** four commits are in, one per TODO; all nine `FanoutTest` tests pass with the
`@Disabled` gone; the one-stage-later test from TODO 4 is in and green; and you've written down
what the two handlers you didn't pick in TODO 3 would have done.

### Exercise 3: pick one of three ways to fan out

The repo's other answer to "do these N things at once" is [doc 06](06-virtual-threads.md), and
[doc 26](26-scoped-values-and-structured-concurrency.md) adds the third. Hold all three in view:

```java
// CompletableFuture: non-blocking, composed; the control flow is in the callbacks
allOf(a, b, c).thenApply(...)

// virtual threads: blocking, sequential; the control flow is the code
try (var ex = Executors.newVirtualThreadPerTaskExecutor()) { ... }

// structured concurrency: blocking, and the tasks' lifetime is the block
try (var scope = StructuredTaskScope.open(Joiner.<String>allSuccessfulOrThrow())) {
    scope.fork(...); scope.fork(...);
    List<String> results = scope.join();
}
```

The last one is still a **preview API on JDK 26**: without `--enable-preview` it fails with
`StructuredTaskScope is a preview API and is disabled by default`, and with it, the block above
prints `[a, b]` (both verified).

**Since Java 21, `CompletableFuture` is no longer the obvious choice for a fan-out.** It existed
because blocking a platform thread was expensive. Virtual threads made blocking cheap, so the
readable sequential version is now also the fast one, and the composition API has to justify
itself on composition alone. It still wins for a real pipeline, with branches, and timeouts and
fallbacks per stage (T4–T7). It loses when all you wanted was "run these and give me the results".

The deliverable: pick one concrete case, say which of the three you'd use, and say why. The code
is secondary. Do this before `httpclient/LocalHttp.java` TODO 3 ([doc 10](10-sequenced-collections-and-httpclient.md)),
which asks for `sendAsync()` and the same decision.

**Done when:** your case and your choice are written down, with the reason, before you start
`LocalHttp` TODO 3.

### Exercise 4: fill in "What got worse"

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: throw from three stages deep and read the stack trace. How much of your own code is in
it? ([Doc 07](07-classic-concurrency.md) asks the same question; compare the two answers.) What
happens to a `ThreadLocal` request context across a `thenApplyAsync`, in a framework that keeps
the tenant id there? Where does backpressure live when the fan-out is 10,000 wide? `orTimeout`
arrived in Java 9: when it fires, what happens to the task that's still running? And what does
`cancel(true)` do to it? Find out before you answer: both have a surprise in them.

### Exercise 5: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. What can you do with a `CompletableFuture` that you can't do with a plain `Future`? (T1)
2. `thenApply` returns at once. What does it return, and what happened to the future you called
   it on? (T2)
3. Name the two methods that wait. Which one throws a checked exception? (T2, T6)
4. A future is already complete when you call `thenApply(fn)` on it. Which thread runs `fn`? And if
   it completes later, from a thread called `completer`? (T3)
5. How many threads does the common pool have on 10 cores, and on a two-core container? What
   happens to an unrelated `supplyAsync(() -> 1)` when they're all blocked? (T3)
6. Why does `allOf` return `CompletableFuture<Void>` instead of the values? (T4)
7. Your function returns a `CompletableFuture<String>`. Which method do you attach it with, and
   what do you get if you pick the other one? (T5)
8. A stage fails. What happens to the `thenApply` attached after it? (T6)
9. You change `get()` to `join()`. Why does your `catch (ExecutionException e)` still compile, and
   why does it stop working? (T6)
10. What's the difference between `completeOnTimeout` and `orTimeout`? (T7)

**Done when:** you can answer all ten without looking.
