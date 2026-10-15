# 15. `CompletableFuture`

**Java 8** · stage 4 of [the release-ordered track](INDEX.md) · compiles at `--release 21`, no
build change

## Where it lands

**`concurrency/SumWordsLength.java:28-40`** is the pattern this API replaced, with the two
defects that make the case for it:

    Set<Future<Integer>> setThreadResults = new HashSet<Future<Integer>>();
    ...
    for (Future<Integer> result : setThreadResults) {
        sum += result.get();            // blocks, one at a time
    }
    catch (Exception e) { System.out.println(e.getMessage()); }   // swallows ExecutionException

Two things are wrong and neither is the threading. It collects into a **`HashSet`**, so the
results have no order at all — which happens not to matter for a sum and would matter for
anything else. And the catch swallows `ExecutionException`, so **a failed task produces a partial
sum that is returned as though it were correct.** That is the worst available failure mode, and
`CompletableFuture` does not fix it for you: it only makes the correct version shorter.

**`httpclient/LocalHttp.java`** TODO 3 asks for `sendAsync()`, which is a `CompletableFuture`
return. Do this document before that TODO.

New code: **`async/Fanout.java`**, four TODOs.

## The exercise

### TODO 1: `allOf` returns `Void`, and that is the whole lesson

    CompletableFuture<Void> done = CompletableFuture.allOf(f1, f2, f3);

`allOf` tells you *when* everything finished and hands back **nothing**. It cannot do otherwise —
the futures may have different types. So the shape you need is: keep the list of futures, wait on
`allOf`, then collect from the originals.

    List<CompletableFuture<T>> futures = tasks.stream().map(t -> supplyAsync(t, ex)).toList();
    return allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());

The `join()` inside `thenApply` **cannot block**, because `allOf` has already completed by the
time that function runs. If you find that uncomfortable, you have understood it correctly.

`FanoutTest.all_preserves_input_order_not_completion_order` uses a `CountDownLatch` to force the
second task to finish strictly before the first, with no sleeps anywhere. An implementation that
builds its result from completion order returns `[second, first]` and fails. **Write a
deterministic concurrency test rather than a usually-passing one** — that technique is worth more
than the API.

### TODO 2: `thenApply` vs `thenCompose`

This is `map` vs `flatMap` and the type tells you everything:

    hello.thenApply(s -> s.length())                          // CompletableFuture<Integer>
    hello.thenApply(s -> completedFuture(s.length()))         // CompletableFuture<CompletableFuture<Integer>>
    hello.thenCompose(s -> completedFuture(s.length()))       // CompletableFuture<Integer>

**Write the middle one first and look at what you get.** You need two `join()` calls to reach the
value. Every "my async code returns a future of a future" question is this line. If the mapper
returns a future, you want `thenCompose`.

Also know what the `Async` suffix means: `thenApply` may run on the thread that completed the
previous stage, `thenApplyAsync` posts it to an executor. **And know which executor** — the
no-executor overloads use `ForkJoinPool.commonPool()`, which is sized to your core count minus
one and is shared with every parallel stream in the JVM. Put a blocking call on it and you have
starved something unrelated. That is the single most common `CompletableFuture` production
failure, and it is why every method in `Fanout` takes its `Executor` as a parameter.

### TODO 3: the three error handlers

| Method | Sees | Can replace the value | Returns |
| --- | --- | --- | --- |
| `exceptionally` | failures only | yes | `CompletableFuture<T>` |
| `handle` | both | yes | `CompletableFuture<U>` |
| `whenComplete` | both | **no** | `CompletableFuture<T>` |

`orElse` wants `exceptionally`. **`whenComplete` is the trap**: it looks like the general one, and
it cannot change the outcome — the failure passes straight through it. People use it as a recovery
hook and are surprised when the future is still failed.

### TODO 4: the wrapper that is not the same wrapper

    failedFuture(boom).get()   ->  ExecutionException
    failedFuture(boom).join()  ->  CompletionException     (unchecked)

Same underlying failure, two wrapper types, one checked and one not. You need
`e.getCause()` either way. This is the inconsistency people hit when they migrate from `Future` to
`CompletableFuture` and their catch blocks stop matching, silently, because `CompletionException`
is unchecked and the compiler will not mention it.

## The question this document exists to set up

The repo's other answer to the same problem is [doc 06](06-virtual-threads.md), and
[stage 15](INDEX.md) will add the third. Hold all three in view:

    // CompletableFuture: non-blocking, composed, and the control flow is in the callbacks
    allOf(a, b, c).thenApply(...)

    // virtual threads: blocking, sequential, and the control flow is the code
    try (var ex = Executors.newVirtualThreadPerTaskExecutor()) { ... }

    // structured concurrency (still preview in 27): blocking, and the lifetime is the block
    try (var scope = StructuredTaskScope.open(Joiner.allSuccessfulOrThrow())) { ... }

**Since Java 21, `CompletableFuture` is no longer the obvious choice for "do these N things at
once".** Its reason for existing was that blocking a platform thread was expensive. Virtual
threads made blocking cheap, which means the readable sequential version is now also the fast
version, and the composition API is left justifying itself on composition alone. It still wins
when you genuinely have a pipeline with branches, timeouts and fallbacks per stage. It loses when
all you wanted was a fan-out.

Being able to say which of the three you would use, and why, for one concrete case, is the
deliverable of this document. The code is secondary.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: throw from three compositions deep and read the stack trace — how much of your own code
is in it? ([Doc 07](07-classic-concurrency.md) asks you the same thing; compare the two answers.)
What happens to a `ThreadLocal`-based request context across a `thenApplyAsync`, and what does
that cost in a framework that puts the tenant id there? Where does backpressure live when the
fan-out is 10,000 wide? `orTimeout` arrived in Java 9 — what did people write before it, and what
does the timeout do to the task that is still running?
