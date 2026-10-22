# 15. `CompletableFuture`

**Java 8** · `orTimeout`, `failedFuture` are **Java 9** · stage 4 of
[the release-ordered track](INDEX.md) · compiles at `--release 21`, no build change

`Fanout` is new code with its tests already written. The old code this replaces,
`SumWordsLength`, belongs to [doc 06](06-virtual-threads.md), and this doc doesn't change it.

## What this stage is actually about

Five words, defined once:

- a **`CompletableFuture<T>`** is a `T` that will exist later, or a failure that will exist
  instead. Unlike a plain `Future`, you can attach what happens next without waiting for it.
- a **stage** is one step attached to a future: `thenApply`, `thenCompose`, `exceptionally`. Each
  one returns a **new** future and leaves the original alone.
- **`join()`** and **`get()`** are the two ways to **wait** for the value. Everything else
  attaches a stage and returns at once.
- an **`Executor`** is what runs a stage's function. The methods without an `Executor` parameter
  use `ForkJoinPool.commonPool()`, the one shared pool in the JVM.
- a **wrapper exception** is the exception a failure arrives inside: `ExecutionException` from
  `get()`, `CompletionException` from `join()` and from later stages. The original is its
  `getCause()`.

That gives one rule, and everything below follows from it: **every call either attaches a stage
or waits. Know which one you're writing, and which thread will run your function.** Mistakes
with this API are rarely about the arithmetic. They are about waiting in the wrong place, running
on the wrong pool, or catching the wrong wrapper.

## The worked example

Which thread runs `s -> Thread.currentThread().getName()`? Verified, JDK 26, 10 cores:

    completedFuture("x").thenApply(fn)          // main: already done, so the caller runs it now
    pending.thenApply(fn); then "completer" thread calls pending.complete("x")
                                                // completer: whoever completes it runs it
    completedFuture("x").thenApplyAsync(fn)     // ForkJoinPool.commonPool-worker-1
    supplyAsync(fn)                             // ForkJoinPool.commonPool-worker-1

So **plain `thenApply` doesn't choose a thread**. It runs on whichever thread happens to be there:
the caller if the future is already done, otherwise the thread that completes it. The `Async`
versions without an executor go to the common pool, and the common pool is small: **9 threads on
10 cores**, and **1 thread** with `-XX:ActiveProcessorCount=2` (both verified). It's shared with
every parallel stream in the JVM.

Fill it with blocking work and watch something unrelated wait. Nine `runAsync` tasks that each
sleep for 2 s, then one `supplyAsync(() -> 1).join()` (verified, timed under load):

    an unrelated supplyAsync behind 9 blockers waited 1900 ms

A task that returns a constant took two seconds, because every common-pool thread was asleep. On
a two-core container, **one** blocking call does this. It's the most common `CompletableFuture`
failure in production, and it's why every method in `Fanout` takes its `Executor` as a parameter.

## Part one: read the old code

`concurrency/SumWordsLength.java:25-46` is the pattern this API was meant to replace:

    Set<Future<Integer>> setThreadResults = new HashSet<Future<Integer>>();   // line 28
    ...
    for (Future<Integer> result : setThreadResults)
        sum += result.get();                                                  // line 38: one at a time
    ...
    } catch (Exception e) { System.out.println(e.getMessage()); }            // line 41

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
- **`get()` in a loop waits for each task in turn.** The total wait is the same as waiting for the
  slowest one, but the code describes waiting, not combining.

## Part two: `Fanout`, four TODOs

`async/Fanout.java`. `FanoutTest` has nine tests. Delete its `@Disabled` to begin. All nine pass
against a scratch implementation (verified), so a failure is yours, not the test's. One commit per
TODO, each turning its tests green.

### TODO 1: `all()`, and `allOf` returns `Void`

`CompletableFuture.allOf(...)` completes when every future you pass it completes, and its value is
`null` (verified: `allOf(completedFuture(1), completedFuture("two")).join()` is `null`). It can't
give you the values, because the futures may have different types. That's the whole puzzle of
TODO 1: you know *when*, and you still have to get the values from somewhere, in **input order**.

`all_preserves_input_order_not_completion_order` uses a `CountDownLatch` to force the second
task to finish strictly before the first, with no sleeps anywhere. An implementation that collects
results as each one finishes fails it, every run (verified):

    expected: <[first, second]> but was: <[second, first]>

**Read that test before you write anything.** A deterministic concurrency test, rather than a
usually-passing one, is worth more than the API. `all_fails_if_any_task_fails` is the other one to
read: one failure fails the whole fan-out, with no partial result. That's the property
`SumWordsLength` doesn't have.

### TODO 2: `thenApply` vs `thenCompose`

This is `map` vs `flatMap`, and the types tell you which. Write `lengthWithThenCompose` with
`thenApply` first, using a mapper that returns a future, and look at what you get (verified):

    CompletableFuture<CompletableFuture<Integer>> nested = ...
    nested.join()          // CompletableFuture@6340e5f0[Completed normally]
    nested.join().join()   // 1

(That's on `completedFuture("x")`, so the value is its length, 1.) Two `join()`s to reach the
value. Every "my async code returns a future of a future" question is
that line. Then fix it.

### TODO 3: three error handlers

| Method | Called on | Can replace the outcome | Returns |
| --- | --- | --- | --- |
| `exceptionally` | failure only | yes | `CompletableFuture<T>` |
| `handle` | success and failure | yes | `CompletableFuture<U>` |
| `whenComplete` | success and failure | **no** | `CompletableFuture<T>` |

**`whenComplete` is the trap.** It looks like the general one, and it can't change the outcome:
the failure passes straight through it. Use it for `orElse` and the test fails with the original
exception still attached (verified):

    FanoutTest.or_else_substitutes_on_failure:101 » Completion java.lang.IllegalStateException: boom

And if the `whenComplete` action itself throws while handling a failure, the original failure
wins and the new exception is added to it as a *suppressed* one (verified). Pick your handler, and
write down what the other two would have done.

### TODO 4: two wrappers, and a third case

    failedFuture(boom).get()    // ExecutionException,  getCause() == boom   (verified)
    failedFuture(boom).join()   // CompletionException, getCause() == boom   (verified)

The same failure arrives in two wrapper types, one checked and one not. Code that switches from
`get()` to `join()` keeps compiling, and its `catch (ExecutionException e)` silently stops
matching, because `CompletionException` is unchecked and the compiler won't say a word.

There's a third case, and the test doesn't cover it. A handler attached **directly** to the
failed future sees the original exception. A handler attached **one stage later** sees it wrapped
(verified):

    failedFuture(boom).exceptionally(e -> ...)                  // e is the IllegalStateException
    failedFuture(boom).thenApply(x -> x).exceptionally(e -> ...) // e is a CompletionException, cause boom

So "unwrap the wrapper" has to cope with both. Add a test for the one-stage-later case before
you call TODO 4 done. That's how this bug gets found in real code: a handler that worked until
someone inserted a `thenApply` above it.

## Part three: the question this doc sets up

The repo's other answer to "do these N things at once" is [doc 06](06-virtual-threads.md), and
[doc 26](26-scoped-values-and-structured-concurrency.md) adds the third. Hold all three in view:

    // CompletableFuture: non-blocking, composed; the control flow is in the callbacks
    allOf(a, b, c).thenApply(...)

    // virtual threads: blocking, sequential; the control flow is the code
    try (var ex = Executors.newVirtualThreadPerTaskExecutor()) { ... }

    // structured concurrency: blocking, and the tasks' lifetime is the block
    try (var scope = StructuredTaskScope.open(Joiner.<String>allSuccessfulOrThrow())) {
        scope.fork(...); scope.fork(...);
        List<String> results = scope.join();
    }

The last one is still a **preview API on JDK 26**: without `--enable-preview` it fails with
`StructuredTaskScope is a preview API and is disabled by default`, and with it, the block above
prints `[a, b]` (both verified).

**Since Java 21, `CompletableFuture` is no longer the obvious choice for a fan-out.** It existed
because blocking a platform thread was expensive. Virtual threads made blocking cheap, so the
readable sequential version is now also the fast one, and the composition API has to justify
itself on composition alone. It still wins for a real pipeline, with branches, and timeouts and
fallbacks per stage. It loses when all you wanted was "run these and give me the results".

The deliverable: pick one concrete case, say which of the three you'd use, and say why. The code
is secondary. Do this before `httpclient/LocalHttp.java` TODO 3 ([doc 10](10-sequenced-collections-and-httpclient.md)),
which asks for `sendAsync()` and the same decision.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: throw from three stages deep and read the stack trace. How much of your own code is in
it? ([Doc 07](07-classic-concurrency.md) asks the same question; compare the two answers.) What
happens to a `ThreadLocal` request context across a `thenApplyAsync`, in a framework that keeps
the tenant id there? Where does backpressure live when the fan-out is 10,000 wide? `orTimeout`
arrived in Java 9: when it fires, what happens to the task that's still running? And what does
`cancel(true)` do to it? Find out before you answer: both have a surprise in them.
