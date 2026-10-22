# 22. Stream gatherers

**Java 24** (`Gatherer`, JEP 485; preview in 22 and 23) · stage 11 of
[the release-ordered track](INDEX.md) · needs `--release 24` or newer, and the repo is at 26, so
no build change

No safety net needed: this is new code, `functional/Windows.java`, and its test,
`WindowsTest`, already exists. It's `@Disabled`. Delete that line to begin. Read
[doc 09](09-streams-and-optional.md) and [doc 17](17-stream-and-optional-additions.md) first.

At `--release 23`, `javac` refuses the file: `Gatherer is a preview API and is disabled by
default` (verified). From 24 it compiles without flags.

## What this stage is actually about

Six words, defined once:

- an **intermediate operation** is a stage between a stream's source and its terminal operation:
  `map`, `filter`, `limit`. Until Java 24 the set was fixed. You could write your own *terminal*
  operation (a `Collector`) but not your own intermediate one.
- a **gatherer** (`Gatherer<T, A, R>`) is a user-written intermediate operation. It takes `T`s,
  keeps some state of type `A`, and emits `R`s. `stream.gather(g)` plugs it in.
- the **integrator** is the gatherer's loop body. It's called once per element with the state,
  the element and the downstream, and it returns a `boolean`.
- the **downstream** is the rest of the pipeline. `downstream.push(r)` sends an element on, and
  it returns `false` when the rest of the pipeline wants no more, for example because a later
  `limit` is satisfied.
- **sequential** (`Gatherer.ofSequential`) means the gatherer never runs in parallel. A gatherer
  that can run in parallel has to supply a combiner, like a `Collector`, and most stateful ones
  can't.
- **greedy** (`Integrator.ofGreedy`) is a promise that the integrator **never** returns `false`
  on its own: it consumes all its input and stops only if the downstream says so.

That gives one rule, and everything below follows from it: **a gatherer is a loop body plus the
state it carries, and you tell the stream how it behaves (sequential or not, greedy or not).
The stream believes what you tell it and doesn't check.** Make a promise you don't keep, and the
results can still look right while the stream does something else.

## The worked example

"Drop adjacent duplicates" can't be built from the old fixed set. `distinct()` drops *all*
duplicates, which is a different question. As a gatherer (verified):

    Gatherer<String, String[], String> dedupeAdjacent = Gatherer.ofSequential(
            () -> new String[1],                          // state: the last element seen
            Gatherer.Integrator.ofGreedy((last, e, downstream) -> {
                if (e.equals(last[0])) return true;       // skip it, and keep going
                last[0] = e;
                return downstream.push(e);
            }));

    List.of("a","a","b","b","b","a","c","c")
        .stream().gather(dedupeAdjacent).toList()         // [a, b, a, c]
        .stream().distinct().toList()                     // [a, b, c]     a different answer

Four things to see in it:

- **The state is a one-element array**, because a lambda can only capture effectively final
  variables, and the array is the box. The state supplier runs once per stream evaluation, so the
  same gatherer object can be reused (verified: running it twice gives the same result).
- **It is honestly greedy.** It returns `true` to skip, and it returns `false` only if
  `downstream.push` does. So the only time it stops is when the rest of the pipeline asks it to.
  `.gather(dedupeAdjacent).limit(2)` gives `[a, b]` (verified).
- **It is honestly sequential.** "The last element seen" only means something in encounter order.
  On `parallelStream()` it still gives `[a, b, a, c]` (verified), because `ofSequential` makes
  that part of the pipeline run on one thread.
- The loop version is five lines and gives the same answer (verified). The gatherer wins when
  it's one stage in a longer pipeline and loses as a whole method body. That's the trade-off for
  every exercise below.

## Part one: the built-ins, TODOs 1 to 3

Facts to know before you write them, all verified on JDK 26, input `[1..7]`:

    Gatherers.windowFixed(3)         [[1, 2, 3], [4, 5, 6], [7]]       the short last window is kept
    Gatherers.windowSliding(2)       [[1, 2], [2, 3], ... [6, 7]]
    Gatherers.scan(() -> 0, Integer::sum)   [1, 3, 6, 10, 15, 21, 28]  one output per input
    Gatherers.fold(() -> 0, Integer::sum)   [28]                       exactly one output

`windowFixed` **keeps** the short final window rather than dropping it. The test
`batches_keeps_the_short_final_window` pins that, because dropping it would be equally
defensible. The windows themselves are unmodifiable: `add` throws
`UnsupportedOperationException`. `windowFixed(0)` throws `IllegalArgumentException: 'windowSize'
must be greater than zero`.

**`windowSliding` also emits a short window when the input is shorter than the window**, and this
one bites:

    windowSliding(2) of []           []
    windowSliding(2) of [5]          [[5]]                  a window of ONE
    windowSliding(2) of [1, 2]       [[1, 2]]
    windowSliding(2) of [1, 2, 4, 8] [[1, 2], [2, 4], [4, 8]]

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

## Part two: the custom gatherer, TODO 4

`takeUntilSumExceeds` can't be built from the built-ins. It needs a running total (state) and
the ability to stop the stream early. Build it with `Gatherer.ofSequential`, as in the worked
example. The difference is that this one **does** decide to stop, by returning `false`.

**A correction to the old version of this doc:** it printed a complete solution, and the solution
was wrong in a way the tests can't see. It wrapped the integrator in `Integrator.ofGreedy`, then
returned `false` from it. That breaks the rule above: greedy promises never to return `false`, so
the stream doesn't treat `false` as "stop". The three `take_until_sum_exceeds` tests still pass,
because once the total is over the limit nothing more is pushed, so the output is the same. But
the stream reads its whole input. Verified, on a list of one million elements, limit 10:

    Integrator.ofGreedy(...)   reads 1,000,000 elements   emits 474,737 of them
    Integrator.of(...)         reads 5 elements           emits [1, 2, 3, 4]

The greedy version doesn't just waste work, it gets the wrong answer. The running total is an
`int`, and after about 65,000 elements it overflows, goes negative, drops back under the limit,
and elements start coming through again: the 5th element emitted is `65536`. On an infinite
source (`Stream.iterate`), it never stops at all, and runs out of heap (verified).

So write it, and then prove it stops:

1. **Make the three `take_until_sum_exceeds` tests green.** Commit.
2. **Write the test that can tell a stopping gatherer from a greedy one.** It needs a source that
   counts how many elements were read. Here's the harness (verified), which is test code, not
   the answer:

        AtomicInteger reads = new AtomicInteger();
        List<Integer> counting = new AbstractList<>() {
            public Integer get(int i) { reads.incrementAndGet(); return i + 1; }
            public int size() { return 1_000_000; }
        };

   Assert both the result `[1, 2, 3, 4]` and that `reads` is small. If your integrator is greedy,
   this test fails while the other three pass. Fix it in its own commit, and say in the message
   which promise you broke.
3. **Write down the overflow.** The stopping version never meets it on this test. Does that mean
   it can't happen? What should the state's type be, and what does `Math.addExact` do?
   [DSA stage 09](../dsa/09-integer-overflow.md) owns overflow.

## Part three: `mapConcurrent`, TODO 5

`Gatherers.mapConcurrent(maxConcurrency, mapper)` runs the mapper on virtual threads, at most
`maxConcurrency` at a time, and emits the results **in input order**. Verified, 20 elements whose
mappers sleep for different times, `maxConcurrency` 4:

    output             [10, 20, ..., 200]    input order, though they finished out of order
    threads            all virtual
    most in flight     4
    a mapper throws IllegalStateException    the stream throws IllegalStateException, unwrapped

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

## What got worse

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
