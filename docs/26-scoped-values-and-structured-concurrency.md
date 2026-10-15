# 26. Scoped values, and structured concurrency as it is now spelled

**Java 25** (scoped values, final) · **preview through 26** (structured concurrency) · stage 15 of
[the release-ordered track](INDEX.md) · **needs `--release 25`**

## Where it lands

New code: **`concurrency/TenantContext.java`**, four TODOs. Do [doc 06](06-virtual-threads.md)
first — this is the answer to the question it ends on:

> *What happens to a `ThreadLocal` when there are a million threads?*

## Scoped values

### The problem with `ThreadLocal`

A `ThreadLocal` is a **mutable** map entry per thread, which **lives until someone removes it**,
and which is **inherited by copying** (`InheritableThreadLocal`). All three properties were
acceptable when threads were expensive and pooled, and all three became problems when virtual
threads made a million threads reasonable:

- mutable: anyone can `set()` at any time, so nothing can reason about the value
- unbounded lifetime: in a pooled world, forgetting `remove()` leaks a request's data into the
  next request on that thread — a real and recurring security bug
- copied on inheritance: a million threads inheriting a context means a million copies

`ScopedValue` (JEP 506, final in 25) fixes all three by being immutable and bound to a **call**,
not a thread:

    ScopedValue.where(TENANT, "acme").run(() -> ...);     // bound inside, gone after
    ScopedValue.where(TENANT, "acme").call(() -> ...);    // same, with a return value

**There is no `set()` and no `remove()`.** You cannot mutate a binding, only establish a narrower
one inside it, and it unwinds automatically. TODO 2 is where that lands: the API shape *is* the
safety argument.

Note `get()` on an unbound value throws `NoSuchElementException` rather than returning `null` —
deliberately, so that "nothing is bound" is not indistinguishable from "null is bound". `isBound()`
is the check.

### TODO 4, which is the thing to actually learn

**Predict the answer before you run the test.**

    ScopedValue.where(TENANT, "acme").call(() -> {
        try (var ex = Executors.newVirtualThreadPerTaskExecutor()) {
            return ex.submit(TenantContext::current).get();     // what does this see?
        }
    });

It sees **`"none"`**. The binding is not inherited.

Now the same thing through a `StructuredTaskScope`:

    ScopedValue.where(TENANT, "acme").call(() -> {
        try (var scope = StructuredTaskScope.open(Joiner.<String>allSuccessfulOrThrow())) {
            scope.fork(TenantContext::current);
            scope.fork(TenantContext::current);
            return scope.join().map(Subtask::get).toList();     // [acme, acme]
        }
    });

**`[acme, acme]`.** Both verified on the JDK in this VM.

The reason is the whole design: a `StructuredTaskScope`'s forks are **guaranteed** to finish
before the scope's block exits, so they are guaranteed to be inside the binding's lifetime. A task
submitted to an executor carries no such guarantee — it could outlive the binding — so the JDK
refuses to pretend it inherits anything.

**This is the single most surprising thing about `ScopedValue`, and it has a consequence worth
saying out loud: it is a drop-in replacement for `ThreadLocal` only if you also adopt structured
concurrency.** If you are passing context to a plain executor, `ScopedValue` does not do what you
want and `ThreadLocal` is still the answer. That is a legitimate "what got worse" line.

## Structured concurrency, and the correction to doc 06

[Doc 06](06-virtual-threads.md) says structured concurrency is still preview and that
`ShutdownOnFailure` is not how JDK 25 spells it. **Both still true**, and here is the current
shape — it is a bigger change than "renamed":

    // JDK 21, the version in every tutorial written in 2023
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        var a = scope.fork(this::fetchA);
        var b = scope.fork(this::fetchB);
        scope.join();
        scope.throwIfFailed();
        return combine(a.get(), b.get());
    }

    // JDK 25, 26 and 27: a static factory and a Joiner, no subclassing, join() returns the result
    try (var scope = StructuredTaskScope.open(Joiner.<String>allSuccessfulOrThrow())) {
        scope.fork(this::fetchA);
        scope.fork(this::fetchB);
        return scope.join().map(Subtask::get).toList();
    }

The policy moved from a **subclass** to a **`Joiner` parameter**, which is the better design and
breaks every example on the internet. The four to know, all verified here:

| Joiner | Behaviour |
| --- | --- |
| `allSuccessfulOrThrow()` | all must succeed; `join()` returns a stream of subtasks |
| `anySuccessfulResultOrThrow()` | first success wins, rest cancelled; `join()` returns the value |
| `awaitAll()` | waits for everything; you inspect `subtask.state()` yourself |
| `allUntil(predicate)` | your own stopping rule |

A failure surfaces as `StructuredTaskScope.FailedException` with the original as its cause —
*not* `ExecutionException` or `CompletionException`, so that is a **third** wrapper type to add to
the two in [doc 15](15-completable-future.md).

### Running it

It is preview, so it is not in the default build. `pom.xml` now has a profile:

    mvn -Ppreview verify

**Do not enable preview by default**, and know why: a class compiled with `--enable-preview`
records the exact JDK version it was built with and **refuses to load on any other**. That is
deliberate — it makes preview features impossible to ship by accident.

**Two incubator rounds and then seven previews**: JEP 428 and 437 incubated it in Java 19 and
20, then JEP 453, 462, 480, 499, 505 and 525 previewed it once per release through Java 26, and it
previews again in 27. [Stage 22](INDEX.md) is about what that count means. It is not the same signal as String
templates, because the API is converging rather than being abandoned, but it is not nothing
either: anything you write against it today needs rewriting when it lands.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: if `ScopedValue` only inherits through structured concurrency, what is your migration
path in a codebase built on thread pools and `ThreadLocal`? What does a framework like Spring do
with request context in a virtual-thread world, and did it have to choose? `Joiner` instead of a
subclass is a better design — what did it cost the people who adopted the preview in 2023, and
does that change what you would advise someone about adopting a preview now?
