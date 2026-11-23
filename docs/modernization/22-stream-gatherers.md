# 22. Stream gatherers

**Java 24** (`Gatherer`, JEP 485; preview in 22 and 23) · stage 11 of
[the release-ordered track](INDEX.md) · needs `--release 24` or newer, and the repo is at 26, so
no build change

No safety net needed: this is new code, `functional/Windows.java`, and its test,
`WindowsTest`, already exists. It's `@Disabled`. Delete that line to begin. Read
[doc 09](09-streams-and-optional.md) and [doc 17](17-stream-and-optional-additions.md) first.

At `--release 23`, `javac` refuses the file: `Gatherer is a preview API and is disabled by
default` (verified). From 24 it compiles without flags.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. The built-ins](#exercise-1-the-built-ins-todos-1-to-3) | `Windows` TODOs 1 to 3: `windowFixed`, `windowSliding`, `scan`. Three commits. | T1, T4 | `WindowsTest` exists: delete its `@Disabled`. |
| [2. A custom gatherer](#exercise-2-a-custom-gatherer-todo-4) | TODO 4, then prove it stops. Up to three commits. | T2, T3 | The three existing tests, then one you write that counts reads. |
| [3. `mapConcurrent`](#exercise-3-mapconcurrent-todo-5) | TODO 5, and the commit message answers its question. | T5 | The existing test. |
| [4. What got worse](#exercise-4-fill-in-what-got-worse) | Write down what gatherers cost. | all | None. |
| [5. Review questions](#exercise-5-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the five sections in order: each one builds on the one before. Here is the whole story in one
paragraph, so you know where it's going.

> Since Java 8 you could write your own *terminal* stream operation (a `Collector`), but not your
> own *intermediate* one, so anything stateful in the middle of a pipeline meant leaving the stream
> for a loop (T1). Java 24's gatherer fills that gap: it's a loop body plus the state it carries,
> plugged in with `stream.gather(...)` (T2). It talks to the rest of the pipeline through the
> downstream, and it makes two promises about itself, greedy and sequential, which the stream
> believes without checking (T3). Most of the time you don't write one: `Gatherers` ships five
> ready-made (T4). The most interesting of those, `mapConcurrent`, replaces a fan-out you would
> otherwise build by hand from virtual threads and a semaphore (T5).

Every code sample below is a complete program unless it says otherwise. Save it as the class name
plus `.java` and run it with `java Name.java`; no build needed. Outputs marked *verified* were run
on JDK 26.

### T1. The gap: you could write your own ending, not your own middle

A stream pipeline has three parts: a source, some **intermediate operations** (`map`, `filter`,
`limit`, ...) and a terminal operation (`toList`, `collect`, ...). Since Java 8 you could write your
own terminal operation, a `Collector`. But the set of intermediate operations was fixed. If the
step you needed in the middle wasn't on the list, you couldn't add it.

That matters as soon as the step needs to **remember** something between elements. Take "drop an
element when it repeats the one just before it". The closest built-in, `distinct()`, answers a
different question: it drops *every* repeat, not just adjacent ones. So you leave the stream and
write a loop:

```java
import java.util.ArrayList;
import java.util.List;

public class Adjacent {
    public static void main(String[] args) {
        List<String> input = List.of("a", "a", "b", "b", "b", "a", "c", "c");

        // The question: drop an element when it repeats the one just before it.
        List<String> loop = new ArrayList<>();
        for (String s : input) {
            if (loop.isEmpty() || !loop.getLast().equals(s)) loop.add(s);
        }

        // The closest built-in stream operation answers a different question.
        List<String> distinct = input.stream().distinct().toList();

        System.out.println("loop:       " + loop);
        System.out.println("distinct(): " + distinct);
    }
}
```

```text
loop:       [a, b, a, c]                                 (verified 2026-11-23)
distinct(): [a, b, c]
```

The loop is fine on its own. It stops being fine when this is one step in a longer pipeline: you
have to break the stream in two, collect into a list, loop, and stream again.
[Doc 09, T4](09-streams-and-optional.md#t4-the-rule-a-stream-fits-when-the-answer-doesnt-depend-on-the-walk)
gives the rule for when a stream fits at all; this is the case where it fits everywhere except
one stateful step.

### T2. A gatherer: a loop body plus the state it carries

A **gatherer** (`Gatherer<T, A, R>`) is a user-written intermediate operation. It takes `T`s, keeps
some state of type `A`, and emits `R`s. `stream.gather(g)` plugs it into the pipeline like any
other stage.

It's built from up to four functions. Only the integrator is required:

| Part | Runs | Its job |
| --- | --- | --- |
| **initializer** | once per stream evaluation | creates a fresh state |
| **integrator** | once per element | the loop body: gets the state, the element and the downstream; returns `boolean` |
| **combiner** | only in parallel | merges two states, like a `Collector`'s combiner |
| **finisher** | once, after the last element | can emit whatever is left in the state |

The **downstream** is the rest of the pipeline. `downstream.push(r)` sends an element on.

Here is "drop adjacent duplicates" from T1 as a gatherer. It needs an initializer (the last
element seen) and an integrator, and nothing else:

```java
import java.util.List;
import java.util.stream.Gatherer;

public class Dedupe {
    static final Gatherer<String, String[], String> dedupeAdjacent = Gatherer.ofSequential(
            () -> new String[1],                          // initializer: the state, the last element seen
            Gatherer.Integrator.ofGreedy((last, e, downstream) -> {
                if (e.equals(last[0])) return true;       // skip it, and keep going
                last[0] = e;
                return downstream.push(e);                // pass it on; false means "stop"
            }));

    public static void main(String[] args) {
        List<String> input = List.of("a", "a", "b", "b", "b", "a", "c", "c");

        System.out.println("gather:          " + input.stream().gather(dedupeAdjacent).toList());
        System.out.println("gather again:    " + input.stream().gather(dedupeAdjacent).toList());
        System.out.println("then limit(2):   " + input.stream().gather(dedupeAdjacent).limit(2).toList());
        System.out.println("parallelStream:  " + input.parallelStream().gather(dedupeAdjacent).toList());
        System.out.println("distinct():      " + input.stream().distinct().toList());
    }
}
```

```text
gather:          [a, b, a, c]                            (verified 2026-11-23)
gather again:    [a, b, a, c]
then limit(2):   [a, b]
parallelStream:  [a, b, a, c]
distinct():      [a, b, c]
```

Four things to see in it:

- **The state is a one-element array**, because a lambda can only capture effectively final
  variables, and the array is the box. The state supplier runs once per stream evaluation, so the
  same gatherer object can be reused: running it twice gives the same result.
- **It is honestly greedy.** It returns `true` to skip, and it returns `false` only if
  `downstream.push` does. So the only time it stops is when the rest of the pipeline asks it to.
  `.gather(dedupeAdjacent).limit(2)` gives `[a, b]`.
- **It is honestly sequential.** "The last element seen" only means something in encounter order.
  On `parallelStream()` it still gives `[a, b, a, c]`, because `ofSequential` makes that part of
  the pipeline run on one thread.
- The loop version in T1 is five lines and gives the same answer. The gatherer wins when it's one
  stage in a longer pipeline and loses as a whole method body. That's the trade-off for every
  exercise below.

### T3. The downstream, and the two promises

The integrator returns a `boolean`, and that `boolean` is how a gatherer and the stream talk about
stopping:

- **`true`** means "keep sending me elements".
- **`false`** means "I'm done: stop the stream". It can say this for its own reasons, or because
  `downstream.push` returned `false`, which is the rest of the pipeline saying it wants no more
  (for example, a later `limit` is satisfied).

Stopping early is what makes a stream on an infinite source finish. Here the source never ends,
and `peek` counts how many elements were actually read:

```java
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Gatherer;
import java.util.stream.Stream;

public class StopsEarly {
    static final Gatherer<Integer, Integer[], Integer> dedupeAdjacent = Gatherer.ofSequential(
            () -> new Integer[1],
            Gatherer.Integrator.ofGreedy((last, e, downstream) -> {
                if (e.equals(last[0])) return true;
                last[0] = e;
                return downstream.push(e);
            }));

    public static void main(String[] args) {
        AtomicInteger reads = new AtomicInteger();

        // An infinite source: 0, 0, 1, 1, 2, 2, 3, 3, ...  peek counts every element read.
        var firstThree = Stream.iterate(0, n -> n + 1)
                .map(n -> n / 2)
                .peek(n -> reads.incrementAndGet())
                .gather(dedupeAdjacent)
                .limit(3)
                .toList();

        System.out.println("result: " + firstThree + "   elements read: " + reads.get());
    }
}
```

```text
result: [0, 1, 2]   elements read: 5                     (verified 2026-11-23)
```

Five reads: `0, 0, 1, 1, 2`. When the gatherer pushed `2`, `limit(3)` was satisfied, `push`
returned `false`, the integrator passed that `false` back, and the stream stopped reading. Nothing
in the gatherer decided to stop; it only passed the message on.

On top of that, a gatherer makes **two promises** about itself, through the factory methods you
build it with:

- **greedy** (`Integrator.ofGreedy`) promises that the integrator **never** returns `false` on its
  own: it consumes all its input and stops only if the downstream says so. `dedupeAdjacent` keeps
  that promise. The stream uses it to skip work it would otherwise do to support stopping early.
- **sequential** (`Gatherer.ofSequential`) promises nothing in parallel: no combiner, one thread.
  A gatherer that can run in parallel has to supply a combiner, like a `Collector`, and most
  stateful ones can't.

That gives one rule, and everything below follows from it:

> **A gatherer is a loop body plus the state it carries, and you tell the stream how it behaves
> (sequential or not, greedy or not). The stream believes what you tell it and doesn't check.**

Make a promise you don't keep, and the results can still look right while the stream does
something else. Exercise 2 is a case where exactly that happened, in an earlier version of this
doc.

### T4. The built-ins: `Gatherers`

Most of the time you don't write a gatherer. `java.util.stream.Gatherers` ships five, and four of
them cover the stateful steps people used to leave the stream for:

| Built-in | What it emits | In one line |
| --- | --- | --- |
| `windowFixed(n)` | lists of up to `n` | consecutive groups that don't overlap |
| `windowSliding(n)` | lists of up to `n` | each group starts one element after the last |
| `scan(init, f)` | one element per input | the running result so far |
| `fold(init, f)` | exactly one element | only the final result, like `reduce`, but as a stage |
| `mapConcurrent(max, f)` | one element per input | `map`, run on virtual threads, at most `max` at a time (T5) |

Two things to keep in mind before you use them, because both show up in exercise 1:

- **Read what they do at the edges.** "Up to `n`" in the first two rows is deliberate: what each
  one emits when the input doesn't divide evenly, or is shorter than the window, is a decision the
  JDK made, and your code inherits it. Exercise 1 lists the verified edge cases.
- **All five are sequential**: none has a combiner (verified: each one's `combiner()` is
  `Gatherer.defaultCombiner()`), for the reason T3 gives: a window or a running total only means
  something in encounter order. `mapConcurrent` is the only one whose *mapper* runs on several
  threads, and it still emits in order.

### T5. `mapConcurrent`, and the fan-out it replaces

A common job: call a slow service once for each of N items, at most 4 calls at a time, and keep
the results in the input order. Before Java 24 you built that yourself, from the pieces in
[doc 06](06-virtual-threads.md#t1-what-a-virtual-thread-is-and-the-one-rule): a virtual thread per
call, a `Semaphore` for the limit, and the `Future`s read back in input order:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class FanOut {
    public static void main(String[] args) throws Exception {
        List<Integer> input = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        Semaphore limit = new Semaphore(4);               // at most 4 calls at once
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger mostInFlight = new AtomicInteger();

        List<Future<String>> futures = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int n : input) {
                futures.add(executor.submit(() -> {
                    limit.acquire();
                    try {
                        mostInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                        Thread.sleep(50 + (n % 3) * 40);  // a "service call" of uneven length
                        return "item-" + n;
                    } finally {
                        inFlight.decrementAndGet();
                        limit.release();
                    }
                }));
            }
            List<String> results = new ArrayList<>();
            for (Future<String> f : futures) results.add(f.get());   // read back in input order
            System.out.println("results:        " + results);
        }
        System.out.println("most in flight: " + mostInFlight.get());
    }
}
```

```text
results:        [item-1, item-2, ..., item-12]           (verified 2026-11-23, trimmed)
most in flight: 4
```

The calls finish out of order (their lengths differ), but reading the `Future`s in the order they
were submitted puts the results back in input order. "Most in flight" was 4 in six runs out of
six; it can't be more, because of the semaphore.

That's about twenty lines of plumbing: an executor, a semaphore, a `try/finally`, a list of
futures. **`Gatherers.mapConcurrent(maxConcurrency, mapper)` is all of it as one stream stage**: it
runs the mapper on virtual threads, at most `maxConcurrency` at a time, and emits the results in
input order. The limit is a parameter instead of a `Semaphore` you build yourself. Exercise 3 uses
it, and lists what it does and doesn't give you.

It's one more way to fan out, next to [doc 06](06-virtual-threads.md)'s
`newVirtualThreadPerTaskExecutor` and the three that [doc 15](15-completable-future.md) compares at
the end. Keep all of them in mind for exercise 3's question.

## Practice

### Exercise 1: the built-ins, TODOs 1 to 3

Facts to know before you write them (T4), all verified on JDK 26, input `[1..7]`:

```text
Gatherers.windowFixed(3)         [[1, 2, 3], [4, 5, 6], [7]]       the short last window is kept
Gatherers.windowSliding(2)       [[1, 2], [2, 3], ... [6, 7]]
Gatherers.scan(() -> 0, Integer::sum)   [1, 3, 6, 10, 15, 21, 28]  one output per input
Gatherers.fold(() -> 0, Integer::sum)   [28]                       exactly one output
```

`windowFixed` **keeps** the short final window rather than dropping it. The test
`batches_keeps_the_short_final_window` pins that, because dropping it would be equally
defensible. The windows themselves are unmodifiable: `add` throws
`UnsupportedOperationException`. `windowFixed(0)` throws `IllegalArgumentException: 'windowSize'
must be greater than zero`.

**`windowSliding` also emits a short window when the input is shorter than the window**, and this
one bites:

```text
windowSliding(2) of []           []
windowSliding(2) of [5]          [[5]]                  a window of ONE
windowSliding(2) of [1, 2]       [[1, 2]]
windowSliding(2) of [1, 2, 4, 8] [[1, 2], [2, 4], [4, 8]]
```

So `windowSliding(2)` doesn't guarantee pairs. A `deltas` that assumes it does throws
`ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 1` on `[5]` (verified), and
`deltas_of_a_single_element_is_empty` is the test that catches it.

Commits, in this order:

1. **Delete the `@Disabled`** and write TODO 1 and TODO 3. Seven of the ten tests stay red, all with
   `UnsupportedOperationException("TODO n")`, which is the point.
2. **TODO 2 with an index loop first**, green, committed. Then rewrite it with `windowSliding(2)`
   and keep it green, including the one-element test. Count the lines in each version.
3. **One sentence in the commit message for TODO 3**: what is the difference between `scan` and
   `fold`, in terms of the number of elements each one emits?

**Done when:** `WindowsTest` has no `@Disabled`, the `batches`, `deltas` and `runningTotals` tests
are green (including `deltas_of_a_single_element_is_empty`), the index-loop `deltas` was committed
before the `windowSliding` one, and the `scan`/`fold` sentence is in a commit message.

### Exercise 2: a custom gatherer, TODO 4

`takeUntilSumExceeds` can't be built from the built-ins. It needs a running total (state) and
the ability to stop the stream early. Build it with `Gatherer.ofSequential`, as in T2's example.
The difference is that this one **does** decide to stop, by returning `false` (T3).

**A correction to the old version of this doc:** it printed a complete solution, and the solution
was wrong in a way the tests can't see. It wrapped the integrator in `Integrator.ofGreedy`, then
returned `false` from it. That breaks T3's rule: greedy promises never to return `false`, so
the stream doesn't treat `false` as "stop". The three `take_until_sum_exceeds` tests still pass,
because once the total is over the limit nothing more is pushed, so the output is the same. But
the stream reads its whole input. Verified, on a list of one million elements, limit 10:

```text
Integrator.ofGreedy(...)   reads 1,000,000 elements   emits 474,737 of them
Integrator.of(...)         reads 5 elements           emits [1, 2, 3, 4]
```

The greedy version doesn't just waste work, it gets the wrong answer. The running total is an
`int`, and after about 65,000 elements it overflows, goes negative, drops back under the limit,
and elements start coming through again: the 5th element emitted is `65536`. On an infinite
source (`Stream.iterate`), it never stops at all, and runs out of heap (verified).

So write it, and then prove it stops:

1. **Make the three `take_until_sum_exceeds` tests green.** Commit.
2. **Write the test that can tell a stopping gatherer from a greedy one.** It needs a source that
   counts how many elements were read, the same idea as T3's `peek`. Here's the harness
   (verified), which is test code, not the answer:

   ```java
   AtomicInteger reads = new AtomicInteger();
   List<Integer> counting = new AbstractList<>() {
       public Integer get(int i) { reads.incrementAndGet(); return i + 1; }
       public int size() { return 1_000_000; }
   };
   ```

   Assert both the result `[1, 2, 3, 4]` and that `reads` is small. If your integrator is greedy,
   this test fails while the other three pass. Fix it in its own commit, and say in the message
   which promise you broke.
3. **Write down the overflow.** The stopping version never meets it on this test. Does that mean
   it can't happen? What should the state's type be, and what does `Math.addExact` do?
   [DSA stage 09](../dsa/09-integer-overflow.md) owns overflow.

**Done when:** the three `take_until_sum_exceeds` tests and your read-counting test are green, any
greedy-to-stopping fix is its own commit naming the broken promise, and the overflow question is
answered in writing.

### Exercise 3: `mapConcurrent`, TODO 5

`Gatherers.mapConcurrent(maxConcurrency, mapper)` runs the mapper on virtual threads, at most
`maxConcurrency` at a time, and emits the results **in input order** (T5). Verified, 20 elements
whose mappers sleep for different times, `maxConcurrency` 4:

```text
output             [10, 20, ..., 200]    input order, though they finished out of order
threads            all virtual
most in flight     4
a mapper throws IllegalStateException    the stream throws IllegalStateException, unwrapped
```

Hold it against the other ways to fan out in this track:
[doc 06](06-virtual-threads.md)'s `newVirtualThreadPerTaskExecutor`, and the three that
[doc 15](15-completable-future.md) compares at the end (`CompletableFuture`, virtual threads,
structured concurrency). **A correction to the old version:** it said this was "the third" of
doc 15's three answers. Doc 15's three don't include it, so it's a fourth. For "call a service
for each of N items, at most 4 at a time, keep the order", it's often the shortest correct
answer, and the concurrency limit is a parameter instead of a `Semaphore` you build yourself.

What it doesn't give you: a timeout or fallback per element, or any error handling beyond "the
whole stream throws". That's where `CompletableFuture` still earns its place.

Write the commit message as the answer to the question `Windows.java` asks in TODO 5: which would
you use for N HTTP calls, and why?

**Done when:** the `timesTenConcurrently` test is green and the commit message answers TODO 5's
question, naming at least one thing `mapConcurrent` can't do.

### Exercise 4: fill in "What got worse"

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: the greedy mistake above passed every test the repo had. What does that say about
testing a stream only by its output? A gatherer's state is a mutable array in a closure. Is
`ofSequential` a fix for that, or an admission? How does a four-stage pipeline with two gatherers
in it read in a stack trace? ([Docs 09, 12 and 15](INDEX.md) ask the same question.)
`mapConcurrent` is limited by its parameter: when the source is faster than the mapper, where
does the backlog go? [Doc 34](34-measuring-it-properly.md) asks you to measure whether
`windowSliding` beats the index loop. Guess first, and write the guess here.

### Exercise 5: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. Before Java 24, which kind of stream operation could you write yourself, and which couldn't
   you? (T1)
2. Why doesn't `distinct()` answer "drop adjacent duplicates"? (T1)
3. Name the four parts a gatherer can have. Which one is required, and which one only runs in
   parallel? (T2)
4. Why is `dedupeAdjacent`'s state a one-element array, and why can the same gatherer object be
   used on two streams? (T2)
5. The integrator returns `false`. What does that mean, and what are the two reasons it might?
   (T3)
6. In `StopsEarly`, nothing in the gatherer decides to stop, yet an infinite stream finishes after
   five reads. Who stopped it, and how did the message travel? (T3)
7. What does `Integrator.ofGreedy` promise, and what happens if your integrator breaks that
   promise? Do the output-only tests notice? (T3, exercise 2)
8. Why are window and running-total gatherers sequential? (T3, T4)
9. `windowSliding(2)` on a one-element list: what does it emit? (exercise 1)
10. What three things does `mapConcurrent` do that `FanOut` builds by hand, and what does it still
    not give you? (T5, exercise 3)

**Done when:** you can answer all ten without looking.
