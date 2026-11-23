# 26. Scoped values, and structured concurrency as it is now spelled

**Java 25** (`ScopedValue`, JEP 506, final) · **preview in 26** (structured concurrency, JEP 525)
· stage 15 of [the release-ordered track](INDEX.md) · `TenantContext` needs `--release 25` or
newer; the structured concurrency experiments need `--enable-preview` on JDK 26

New code with its tests already written: `TenantContextTest` is `@Disabled` until you begin. Do
[doc 06](06-virtual-threads.md) first. This doc answers the question that one ends on: *what
happens to a `ThreadLocal` when there are a million threads?*

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `TenantContext`](#exercise-1-tenantcontext) | Fill in four TODOs with `ScopedValue`. Three commits; predict TODO 4 before you write it. | T1–T3 | `TenantContextTest` exists: delete `@Disabled` and all six go red. |
| [2. Five behaviours](#exercise-2-reproduce-five-structured-concurrency-behaviours) | Reproduce five structured-concurrency results in a scratch file outside the repo. | T4–T7 | None: scratch experiments, preview flag. |
| [3. What got worse](#exercise-3-fill-in-what-got-worse) | Write down three costs. | all | None. |
| [4. Review questions](#exercise-4-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the seven sections in order. T1 to T3 are about `ScopedValue`, which is **final** in Java
25: no flags. T4 to T7 are about `StructuredTaskScope`, which is still a **preview** in Java 26:
every sample there needs `--enable-preview`. Here is the whole story in one paragraph.

> A `ThreadLocal` is a mutable slot that lives as long as its thread. On a thread pool that's a
> data leak, and with virtual threads it's a million copies nobody clears (T1). A `ScopedValue`
> fixes it by turning the slot into a *binding*: a value attached for exactly one call, immutable,
> and gone when the call returns (T2). That one design choice gives one rule: only code that
> provably runs inside the call can see the binding (T3). Proving that for a *thread* needs a new
> shape of concurrency, where subtasks can't outlive the block that started them (T4). Java's
> version of that block is `StructuredTaskScope`, with a `Joiner` that decides what "done" means
> (T5). It's still a preview, and its API changed in every release up to 26 (T6), which is also
> why a preview class won't run on the next JDK (T7).

Every code sample below is a complete program. Save it as the class name plus `.java` and run it
with `java Name.java`, or with the preview command shown for T4 to T7. Outputs marked *verified*
were run on JDK 26.

### T1. The problem: a `ThreadLocal` lives as long as its thread

A server needs to know, deep inside some call, which user or tenant the current request belongs
to. Passing it as a parameter through every method is noisy, so for twenty years the answer was a
**`ThreadLocal`**: one mutable slot per thread. `set()` writes it, `get()` reads it, and the value
stays there until somebody calls `remove()` or the thread dies.

"Until the thread dies" is the trap. Pooled threads never die:

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LeakyPool {
    static final ThreadLocal<String> USER = new ThreadLocal<>();

    public static void main(String[] args) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(1)) {     // one thread, reused
            // Request 1 sets the user and forgets to remove() it.
            pool.submit(() -> USER.set("alice")).get();

            // Request 2 is someone else. It never set anything.
            String seen = pool.submit(() -> USER.get()).get();
            System.out.println("request 2 sees user: " + seen);
        }
    }
}
```

```text
request 2 sees user: alice                               (verified 2026-11-23)
```

The second task is a different request, and it sees the first request's user. Nobody called
`remove()`. In a pooled server that's a data leak between requests, and it's a bug that has
shipped many times. The `ThreadLocal` API makes it possible, because the value lives as long as
the *thread*. (The old version of this example, `"request-1-user-alice"` on a pool of one, was
verified the same way.)

Virtual threads change the cost side too. A thread can also inherit values, through
`InheritableThreadLocal`:

```java
public class Inherit {
    static final ThreadLocal<String> PLAIN = new ThreadLocal<>();
    static final InheritableThreadLocal<String> INHERITED = new InheritableThreadLocal<>();

    public static void main(String[] args) throws Exception {
        PLAIN.set("acme");
        INHERITED.set("acme");

        Thread child = Thread.ofVirtual().start(() -> {
            System.out.println("child sees PLAIN:     " + PLAIN.get());
            System.out.println("child sees INHERITED: " + INHERITED.get());
            INHERITED.set("evil-corp");             // the child's copy is mutable...
        });
        child.join();
        System.out.println("parent still sees:    " + INHERITED.get());   // ...but it's a copy
    }
}
```

```text
child sees PLAIN:     null                               (verified 2026-11-23)
child sees INHERITED: acme
parent still sees:    acme
```

A virtual thread inherits `InheritableThreadLocal` values just like a platform thread. So a million
virtual threads started from a request handler each get a **copy** of the parent's inheritable
map, and each one can `set()` its own slot to something else at any moment. Three properties
cause all of this:

1. **mutable**: anyone can `set()` at any time;
2. **as long-lived as the thread**: on a pool, forever;
3. **copied on inheritance**: a million threads, a million copies.

`ScopedValue` removes all three.

### T2. `ScopedValue`: a binding for the length of one call

A **binding** is a value attached to a `ScopedValue` for the duration of one call:
`ScopedValue.where(KEY, value).run(body)`. Inside `body`, and in everything `body` calls,
`KEY.get()` returns `value`. When `body` returns, the binding is gone. There's no `set()` and no
`remove()`: you can't change a binding, only open a narrower one inside it.

```java
import java.util.NoSuchElementException;

public class Bindings {
    static final ScopedValue<String> LOCALE = ScopedValue.newInstance();

    static void greet() {                               // deep in the call stack: no parameter
        System.out.println("  greet() sees " + LOCALE.get());
    }

    public static void main(String[] args) {
        ScopedValue.where(LOCALE, "en").run(() -> {
            greet();
            ScopedValue.where(LOCALE, "ca").run(Bindings::greet);   // a narrower binding
            greet();                                                // the outer one is back
        });

        System.out.println("after run: isBound = " + LOCALE.isBound());
        try {
            LOCALE.get();
        } catch (NoSuchElementException e) {
            System.out.println("after run: get() throws " + e.getMessage());
        }
        System.out.println("after run: orElse   = " + LOCALE.orElse("default"));

        String shouted = ScopedValue.where(LOCALE, "es").call(() -> LOCALE.get().toUpperCase());
        System.out.println("call returns " + shouted);
    }
}
```

```text
  greet() sees en                                        (verified 2026-11-23)
  greet() sees ca
  greet() sees en
after run: isBound = false
after run: get() throws ScopedValue not bound
after run: orElse   = default
call returns ES
```

Read it against T1's three properties:

- **Not mutable.** The inner `where(LOCALE, "ca")` didn't overwrite `"en"`. It opened a narrower
  binding, and when that call returned, `"en"` was back.
- **Not long-lived.** After `run` returns, nothing is bound. There was no `remove()` to forget,
  which is why `LeakyPool`'s bug can't be written with a `ScopedValue`.
- **`run` for a body with no result, `call` for one that returns a value** (and may throw).

Three facts about reading a `ScopedValue`, all verified on JDK 26:

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

### T3. The one rule: only code inside the call can see the binding

That gives one rule, and everything below follows from it:

> **A binding lives exactly as long as one call, so only code that provably runs inside that call
> can see it.**

Inside the same thread, that's easy: every method `body` calls runs before `body` returns, so
`greet()` in T2 sees the binding however deep it is.

Other threads are the hard part. A thread you start yourself might outlive the call, so it can't
see the binding. A fork of a `StructuredTaskScope` can't outlive the call, so it can. Exercise 1's
TODO 4 asks you to predict what that means for a plain virtual thread started inside a binding.
**Predict before you read the test.** T4 to T6 are about the kind of thread that *can't* outlive
the call.

### T4. Structured concurrency: subtasks that can't outlive their block

> **Preview from here to the end of the theory.** Every sample needs
> `java --enable-preview --source 26 Name.java`.

Start with the problem, using only final APIs. Two tasks are submitted to an executor; one fails
straight away, the other is slow:

```java
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Orphan {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        Future<String> slow = pool.submit(() -> {
            Thread.sleep(1_000);
            System.out.println("  slow task: finished anyway, nobody wants this");
            return "slow";
        });
        Future<String> broken = pool.submit(() -> { throw new IllegalStateException("boom"); });

        try {
            System.out.println(broken.get() + slow.get());
        } catch (ExecutionException e) {
            System.out.println("main: gave up because of " + e.getCause());
        }
        System.out.println("main: is the slow task still running? " + !slow.isDone());
        pool.close();                                   // waits for the orphan
    }
}
```

```text
main: gave up because of java.lang.IllegalStateException: boom   (verified 2026-11-23)
main: is the slow task still running? true
  slow task: finished anyway, nobody wants this
```

(This one is final API; it runs with plain `java Orphan.java`.) `main` has given up, but the slow
task is still running, for nobody. An executor has no idea that the two tasks belong together.
Nothing cancels the sibling, and nothing stops a task from outliving the method that started it.
That last point is exactly why T3's rule can't let it see a binding.

**Structured concurrency** means that subtasks started inside a block finish before the block
exits, the way a method call returns before its caller carries on. If they can't outlive the
block, they can't outlive the binding the block runs in, and T3's rule is satisfied.

### T5. `StructuredTaskScope` and the `Joiner`, as JDK 26 spells it

A **`StructuredTaskScope`** is that block: `open(...)`, then `fork(...)` the subtasks, then
`join()`, all inside a `try`-with-resources. Each fork runs in its own virtual thread
([doc 06](06-virtual-threads.md)). A **`Joiner`** is the policy you pass to `open(...)`: wait for
all of them, or take the first success, or something else. It decides what `join()` returns, and
when to cancel the forks that are still running (by interrupting them, see
[doc 06, T6](06-virtual-threads.md#t6-the-interrupt-flag-propagate-it-or-restore-it) and
[doc 07, T3](07-classic-concurrency.md#t3-interruption-and-where-it-gets-lost)).

```java
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;

public class Scope {
    static String fetch(String name, long millis) throws InterruptedException {
        Thread.sleep(millis);                           // stands in for an HTTP or DB call
        return name;
    }

    public static void main(String[] args) throws InterruptedException {
        long start = System.nanoTime();
        try (var scope = StructuredTaskScope.open(Joiner.<String>allSuccessfulOrThrow())) {
            scope.fork(() -> fetch("prices", 300));     // each fork runs in its own virtual thread
            scope.fork(() -> fetch("stock", 200));
            List<String> results = scope.join();        // waits for both; values in fork order
            System.out.println("results: " + results);
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("took about the slowest fork, not the sum: " + (ms < 450));
    }
}
```

```sh
java --enable-preview --source 26 Scope.java
```

```text
results: [prices, stock]                                 (verified 2026-11-23)
took about the slowest fork, not the sum: true
```

Without the flags it fails at compile time: `StructuredTaskScope is a preview API and is disabled
by default` (verified).

The `Joiner`s, and what `join()` returns on JDK 26:

| Joiner | Policy | `join()` returns |
| --- | --- | --- |
| `allSuccessfulOrThrow()` | every fork must succeed; the first failure cancels the rest | `List<T>`, in fork order |
| `anySuccessfulOrThrow()` | the first success wins; the rest are cancelled | `T` |
| `awaitAllSuccessfulOrThrow()` | like the first, but you read results from the `Subtask`s yourself | `Void` |
| `awaitAll()` | wait for everything, success or not | `Void` |
| `allUntil(predicate)` | wait until the predicate says stop | `List<Subtask<T>>` |

Compare with `Orphan`: under `allSuccessfulOrThrow()`, a failing fork cancels its siblings, and
the `try` block can't end while any fork is still running. Exercise 2 has you reproduce exactly
what happens when a fork fails, when one wins, and when you break the rules.

### T6. The API changed in every release, and it changed again in 26

Structured concurrency has been a preview for six releases, and its shape has moved under every
tutorial written about it. Three versions of the same code:

```java
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
```

(Fragments, not programs.) Verified with both JDKs installed here: the JDK 21 form fails on 25
(`cannot find symbol`, `ShutdownOnFailure`). The JDK 25 form runs on 25 and prints `[a, b]`, and
on 26 it fails to compile:

```text
error: cannot find symbol
    System.out.println(scope.join().map(StructuredTaskScope.Subtask::get).toList());
                                   ^
  symbol:   method map(Structured[...]::get)
```

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

### T7. Why preview stays out of the default build

A class compiled with `--enable-preview` records the exact JDK it was compiled for. The JDK 25
class from T6, run on 26 (verified):

```text
java.lang.UnsupportedClassVersionError: Old25 (class file version 69.65535) was compiled with
preview features that are unsupported. This version of the Java Runtime only recognizes
preview features for class file version 70.65535
```

`65535` is the minor version that marks a preview class. That's deliberate: preview code can't be
shipped by accident, because it won't run on the next JDK. It's why `pom.xml` keeps preview in a
profile. (T6 shows the source side of the same story: even recompiled, the 25 code doesn't build
on 26.)

## Practice

### Exercise 1: `TenantContext`

`concurrency/TenantContext.java` has four TODOs, and `TenantContextTest` has six tests. Delete the
`@Disabled` and all six error with `UnsupportedOperationException` (verified).

Commits, in this order:

1. **TODO 1, `current()`.** The tenant, or `"none"`. One test goes green:
   `nothing_is_bound_outside_a_scope`. Use the table in T2 to choose between `isBound()`, `orElse`
   and a `try/catch`, and say which in the commit message.
2. **TODOs 2 and 3, `runAs` and `callAs`.** The javadoc names the API, so these are short. The
   point is the test called `the_binding_is_gone_after_the_body_returns`. Read it, and notice that
   nothing in your code made it pass. Write down why the absence of `remove()` is the whole safety
   argument, using `LeakyPool` from T1.
3. **TODO 4, `tenantInsideAVirtualThread()`.** Before you write it, **predict what the child thread
   sees and put the prediction in the commit message.** Then write it, run the test, and say
   whether you were right. The test's javadoc has the answer, so don't read it first.

Once you've run TODO 4, compare it with the same lookup made from a `StructuredTaskScope` fork
inside the same binding (verified; preview, see T5):

```java
ScopedValue.where(TENANT, "acme").call(() -> {
    try (var scope = StructuredTaskScope.open(Joiner.<String>allSuccessfulOrThrow())) {
        scope.fork(TenantContext::current);
        scope.fork(TenantContext::current);
        return scope.join();                      // [acme, acme]
    }
});
```

The forks see the binding. If that's different from what TODO 4 gave you, the rule in T3 is the
explanation, and it has a consequence worth writing down. **`ScopedValue` replaces `ThreadLocal`
only in code that also uses structured concurrency.** Code that hands work to a plain executor
still needs another way to pass the context.

**Done when:** the three commits are in, all six `TenantContextTest` tests pass, and the commit
messages record your `current()` choice, the safety argument and your TODO 4 prediction.

### Exercise 2: reproduce five structured-concurrency behaviours

**A correction to the old version:** it said `mvn -Ppreview verify` was how to run this part.
Nothing in `src/` uses `StructuredTaskScope`, so the preview profile compiles and tests the repo
(it does, verified) without running any of it. Do these experiments in a single file **outside
the repo** instead. Source-launch mode takes the preview flag directly:

```sh
java --enable-preview --source 26 Scope.java
```

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
rule in T3 being enforced: a fork has to see the same bindings its scope was opened in.

**Done when:** you have one experiment per row, each gives the result in the table, and you can
say which theory section explains each row.

### Exercise 3: fill in "What got worse"

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: if `ScopedValue` only reaches forks of a `StructuredTaskScope`, what's the migration path
for a codebase built on thread pools and `ThreadLocal`, and which of the two do you change first?
What does a framework do with request context in a virtual-thread world, and does it have to
choose? A `Joiner` is a better design than a subclass, and it broke every example written for
21, and then 26 broke the 25 ones too. What does that tell you about adopting a preview API
today?

### Exercise 4: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. Why does the second task in `LeakyPool` see `alice`, and which `ThreadLocal` method would have
   prevented it? (T1)
2. A virtual thread is started after the parent set an `InheritableThreadLocal`. What does the
   child see, and what happens to the parent's value if the child calls `set()`? (T1)
3. Name the three properties of `ThreadLocal` that `ScopedValue` removes. (T1, T2)
4. How do you "change" a scoped value inside a binding, and what do callers see after your call
   returns? (T2)
5. Why does `get()` on an unbound `ScopedValue` throw instead of returning `null`? (T2)
6. State the one rule about which code can see a binding. (T3)
7. In `Orphan`, why is the slow task still running after `main` has given up? (T4)
8. What does `join()` return under `allSuccessfulOrThrow()` on JDK 26, and what did it return on
   JDK 25? (T5, T6)
9. How does a scope cancel the forks that are still running? (T5)
10. Why does a class compiled with `--enable-preview` on JDK 25 refuse to run on JDK 26? (T7)

**Done when:** you can answer all ten without looking.
