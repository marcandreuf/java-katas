# 26. Scoped values, and structured concurrency as it is now spelled

**Java 25** (`ScopedValue`, JEP 506, final) · **preview in 26** (structured concurrency, JEP 525)
· stage 15 of [the release-ordered track](INDEX.md) · `TenantContext` needs `--release 25` or
newer; the structured concurrency experiments need `--enable-preview` on JDK 26

New code with its tests already written: `TenantContextTest` is `@Disabled` until you begin. Do
[doc 06](06-virtual-threads.md) first. This doc answers the question that one ends on: *what
happens to a `ThreadLocal` when there are a million threads?*

## What this stage is actually about

Five words, defined once:

- a **`ThreadLocal`** is one mutable slot per thread. `set()` writes it, `get()` reads it, and the
  value stays there until somebody calls `remove()` or the thread dies.
- a **binding** is a value attached to a `ScopedValue` for the duration of one call:
  `ScopedValue.where(KEY, value).run(body)`. Inside `body`, and in everything `body` calls,
  `KEY.get()` returns `value`. When `body` returns, the binding is gone. There's no `set()` and no
  `remove()`.
- **structured concurrency** means that subtasks started inside a block finish before the block
  exits, the way a method call returns before its caller carries on.
- a **`StructuredTaskScope`** is that block: `open(...)`, then `fork(...)` the subtasks, then
  `join()`, all inside a `try`-with-resources.
- a **`Joiner`** is the policy you pass to `open(...)`: wait for all of them, or take the first
  success, or something else. It decides what `join()` returns.

That gives one rule, and everything below follows from it: **a binding lives exactly as long as
one call, so only code that provably runs inside that call can see it.** A thread you start
yourself might outlive the call, so it can't see the binding. A fork of a `StructuredTaskScope`
can't outlive the call, so it can.

## The worked example: why `ThreadLocal` had to be replaced

A `ThreadLocal` set by one task, on a pool of one thread (verified):

    ExecutorService pool = Executors.newFixedThreadPool(1);
    pool.submit(() -> USER.set("request-1-user-alice")).get();
    pool.submit(() -> USER.get()).get()       // "request-1-user-alice"

The second task is a different request, and it sees the first request's user. Nobody called
`remove()`. In a pooled server that's a data leak between requests, and it's a bug that has
shipped many times. The `ThreadLocal` API makes it possible, because the value lives as long as
the *thread*, and pooled threads live forever.

Virtual threads change the cost side too (verified on JDK 26):

    USER.set("acme"); INHERITED.set("acme");          // ThreadLocal, InheritableThreadLocal
    Thread.ofVirtual().start(...)                      // child sees: null, "acme"

A virtual thread inherits `InheritableThreadLocal` values just like a platform thread. So a million
virtual threads started from a request handler each get a copy of the parent's inheritable map,
and each one can `set()` its own slot to something else at any moment. That's the three
properties the rule above removes: mutable, as long-lived as the thread, and copied on
inheritance.

Three facts about `ScopedValue`, all verified on JDK 26:

| Call | Result |
| --- | --- |
| `KEY.get()` with nothing bound | `NoSuchElementException: ScopedValue not bound` |
| `KEY.orElse("dflt")` with nothing bound | `"dflt"` |
| `ScopedValue.where(KEY, null).run(...)`, then `KEY.isBound()` and `KEY.get()` | `true`, `null` |

The third row is why `get()` throws instead of returning `null`. `null` is a value you can bind, so
returning it for "nothing bound" would make the two impossible to tell apart. `isBound()` is the
question to ask.

And `ScopedValue` is final in 25: `javac --release 25` compiles it with no flags, and
`javac --release 24` says `ScopedValue is a preview API and is disabled by default` (both
verified).

## Part one: `TenantContext`

`concurrency/TenantContext.java` has four TODOs, and `TenantContextTest` has six tests. Delete the
`@Disabled` and all six error with `UnsupportedOperationException` (verified).

Commits, in this order:

1. **TODO 1, `current()`.** The tenant, or `"none"`. One test goes green:
   `nothing_is_bound_outside_a_scope`. Use the table above to choose between `isBound()`, `orElse`
   and a `try/catch`, and say which in the commit message.
2. **TODOs 2 and 3, `runAs` and `callAs`.** The javadoc names the API, so these are short. The
   point is the test called `the_binding_is_gone_after_the_body_returns`. Read it, and notice that
   nothing in your code made it pass. Write down why the absence of `remove()` is the whole safety
   argument, using the pool example above.
3. **TODO 4, `tenantInsideAVirtualThread()`.** Before you write it, **predict what the child thread
   sees and put the prediction in the commit message.** Then write it, run the test, and say
   whether you were right. The test's javadoc has the answer, so don't read it first.

Once you've run TODO 4, compare it with the same lookup made from a `StructuredTaskScope` fork
inside the same binding (verified):

    ScopedValue.where(TENANT, "acme").call(() -> {
        try (var scope = StructuredTaskScope.open(Joiner.<String>allSuccessfulOrThrow())) {
            scope.fork(TenantContext::current);
            scope.fork(TenantContext::current);
            return scope.join();                      // [acme, acme]
        }
    });

The forks see the binding. If that's different from what TODO 4 gave you, the rule above is the
explanation, and it has a consequence worth writing down. **`ScopedValue` replaces `ThreadLocal`
only in code that also uses structured concurrency.** Code that hands work to a plain executor
still needs another way to pass the context.

## Part two: structured concurrency, as JDK 26 spells it

**A correction to the old version:** it said `mvn -Ppreview verify` was how to run this part.
Nothing in `src/` uses `StructuredTaskScope`, so the preview profile compiles and tests the repo
(it does, verified) without running any of it. Do these experiments in a single file **outside
the repo** instead. Source-launch mode takes the preview flag directly:

    java --enable-preview --source 26 Scope.java

Without the flags it fails at compile time: `StructuredTaskScope is a preview API and is disabled
by default` (verified).

### Five behaviours to reproduce

Write one small experiment for each row and check you get the same result. All of these were
verified on JDK 26:

| Experiment | Result |
| --- | --- |
| Two forks inside a binding read the `ScopedValue` | `[acme, acme]`, a `List` |
| One fork throws `IllegalStateException("boom")` under `allSuccessfulOrThrow()` | `StructuredTaskScope$FailedException`, cause `IllegalStateException: boom` |
| `anySuccessfulOrThrow()`, one fork returns at once, one sleeps 5 s | `"fast"`. The scope closes in 25–29 ms (five runs), and the sleeper is interrupted |
| Fork, then close the scope without calling `join()` | `IllegalStateException: Owner did not join after forking` |
| `open()` outside a binding, then `fork` from inside a new binding | `StructureViolationException: Scoped value bindings have changed` |

The second row adds a **third** wrapper type to the two in
[doc 15](15-completable-future.md): `ExecutionException` from `Future.get()`, `CompletionException`
from `join()`, and now `FailedException`. You need `getCause()` for all three. The last row is the
rule above being enforced: a fork has to see the same bindings its scope was opened in.

### The API changed in every release, and it changed again in 26

    // JDK 21, the version in most tutorials
    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
        var a = scope.fork(this::fetchA);
        scope.join();
        scope.throwIfFailed();
        return a.get();
    }

    // JDK 25: a factory and a Joiner; join() hands back a Stream of subtasks
    try (var scope = StructuredTaskScope.open(Joiner.<String>allSuccessfulOrThrow())) {
        scope.fork(this::fetchA);
        scope.fork(this::fetchB);
        return scope.join().map(Subtask::get).toList();
    }

    // JDK 26: the same shape, and join() hands back the values
    try (var scope = StructuredTaskScope.open(Joiner.<String>allSuccessfulOrThrow())) {
        scope.fork(this::fetchA);
        scope.fork(this::fetchB);
        return scope.join();                          // List<String>
    }

Verified with both JDKs installed here: the JDK 21 form fails on 25 (`cannot find symbol`,
`ShutdownOnFailure`). The JDK 25 form runs on 25 and prints `[a, b]`, and on 26 it fails to
compile:

    error: cannot find symbol
        System.out.println(scope.join().map(StructuredTaskScope.Subtask::get).toList());
                                       ^
      symbol:   method map(Structured[...]::get)

The factories, from `javap` on each JDK:

| Joiner | `join()` returns on 25 | `join()` returns on 26 |
| --- | --- | --- |
| `allSuccessfulOrThrow()` | `Stream<Subtask<T>>` | `List<T>` |
| `anySuccessfulResultOrThrow()` on 25, `anySuccessfulOrThrow()` on 26 | `T` | `T` |
| `awaitAllSuccessfulOrThrow()` | `Void` | `Void` |
| `awaitAll()` | `Void` | `Void` |
| `allUntil(predicate)` | `Stream<Subtask<T>>` | `List<Subtask<T>>` |

**Two corrections to the old version.** It said `awaitAllSuccessfulOrThrow()` was new in 26, but
it's already in 25. And it left out `allUntil`, which also changed from a `Stream` to a `List`.
`open(joiner, config)` changed too: it took a `Function` on 25 and takes a `UnaryOperator` on 26.

**And the preview count:** the old version said "seven previews through Java 26". By its own list,
that's six: JEP 453, 462, 480, 499, 505 and 525, one per release from 21 to 26. The two
incubator rounds before them (JEP 428 and 437, in 19 and 20) and the seventh preview in 27 (JEP 533,
listed in [the index](INDEX.md)) are **not verified**, because no JDK 19, 20 or 27 is installed
here.

### Why preview stays out of the default build

A class compiled with `--enable-preview` records the exact JDK it was compiled for. The JDK 25
class from above, run on 26 (verified):

    java.lang.UnsupportedClassVersionError: Old25 (class file version 69.65535) was compiled with
    preview features that are unsupported. This version of the Java Runtime only recognizes
    preview features for class file version 70.65535

`65535` is the minor version that marks a preview class. That's deliberate: preview code can't be
shipped by accident, because it won't run on the next JDK. It's why `pom.xml` keeps preview in a
profile.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: if `ScopedValue` only reaches forks of a `StructuredTaskScope`, what's the migration path
for a codebase built on thread pools and `ThreadLocal`, and which of the two do you change first?
What does a framework do with request context in a virtual-thread world, and does it have to
choose? A `Joiner` is a better design than a subclass, and it broke every example written for
21, and then 26 broke the 25 ones too. What does that tell you about adopting a preview API
today?
