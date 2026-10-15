# 22. Stream gatherers

**Java 24** · stage 11 of [the release-ordered track](INDEX.md) · **needs `--release 24` or newer**

## Where it lands

New code: **`functional/Windows.java`**, five TODOs. This is the most significant addition to
`Stream` since `Stream` — read [doc 09](09-streams-and-optional.md) and
[doc 17](17-stream-and-optional-additions.md) first.

## The hole this fills

Streams shipped in 2014 with an extension point for **terminal** operations — `Collector`, which
anybody can implement — and **no extension point for intermediate ones.** Everything between the
source and the terminal had to be assembled from the fixed set: `map`, `filter`, `flatMap`,
`limit`, `peek`, `sorted`, `distinct`.

That set cannot express anything that carries state across elements. "Group these into threes."
"Give me consecutive pairs." "Stop when the running total exceeds N." Every one of those meant
leaving the stream and writing an index loop — which is why so much stream code in the wild has a
`for` loop bolted to the end of it.

`Gatherer` (JEP 485, final in Java 24) is `Collector` for the middle of the pipe.

## The exercise

### The built-ins

    Gatherers.windowFixed(3)      [1..7] -> [[1,2,3], [4,5,6], [7]]
    Gatherers.windowSliding(2)    [1..7] -> [[1,2], [2,3], [3,4], [4,5], [5,6], [6,7]]
    Gatherers.scan(..)            [1..7] -> [1, 3, 6, 10, 15, 21, 28]
    Gatherers.fold(..)            [1..7] -> [28]
    Gatherers.mapConcurrent(4, f)

TODOs 1–3. Note **`windowFixed` keeps the short final window** rather than dropping it — the
test pins that, because the other choice is equally defensible and you need to know which one you
got. And note `scan` emits one element per input while `fold` emits exactly one, which is the
whole difference between a running total and a total.

**Both window gatherers also emit a short window when the input is shorter than the window**, and
this one does bite:

    windowSliding(2) of []           ->  []
    windowSliding(2) of [5]          ->  [[5]]        <-- a window of ONE
    windowSliding(2) of [1,2]        ->  [[1, 2]]
    windowSliding(2) of [1,2,4,8]    ->  [[1,2], [2,4], [4,8]]

So `windowSliding(2)` does **not** guarantee pairs. The obvious `deltas` implementation —
`.map(pair -> pair.get(1) - pair.get(0))` — throws `ArrayIndexOutOfBoundsException` on a
one-element list, and `WindowsTest.deltas_of_a_single_element_is_empty` is the test that catches
it. Every window-based calculation needs to decide what it does with that short window, and the
name `windowSliding` actively suggests it will not exist.

**Write `deltas` with an index loop first** (TODO 2). Then with `windowSliding(2)`. The loop is
shorter. Say so in "what got worse" if that is your honest conclusion — the gatherer wins when
it is one stage of a longer pipeline, and loses as a whole method body.

### The custom one, which is the actual exercise

TODO 4. `takeUntilSumExceeds` cannot be built from the built-in set at all:

    Gatherer.ofSequential(
        () -> new int[1],                                  // state
        Gatherer.Integrator.ofGreedy((state, element, downstream) -> {
            state[0] += element;
            if (state[0] > limit) return false;            // false = stop the whole stream
            return downstream.push(element);
        }));

Three things to take from that shape:

- **The integrator returns a boolean, and `false` ends the stream.** That is how a gatherer
  short-circuits, and it is why `takeWhile` did not need to be a special case in the JDK — it is
  expressible now.
- **State is explicit and the gatherer declares it is sequential.** `ofSequential` means "do not
  parallelise me". A gatherer that *can* be parallelised must supply a combiner, exactly like a
  `Collector`, and most stateful ones cannot.
- `downstream.push` returning `false` means the *downstream* is finished (someone further along
  called `limit`), and a well-behaved integrator propagates that.

### `mapConcurrent`, and the third answer to fan-out

TODO 5. `Gatherers.mapConcurrent(n, fn)` runs the mapper on **virtual threads**, with `n` in
flight, and **preserves input order**.

Hold this against the other two answers in the track:

    allOf(futures).thenApply(...)                            // doc 15, CompletableFuture
    try (var ex = newVirtualThreadPerTaskExecutor()) { ... }  // doc 06, virtual threads
    stream().gather(mapConcurrent(4, this::call)).toList()   // here

For "call this service for each of N items, at most 4 at a time, in order", **the gatherer is now
the shortest correct answer in the language** — and the concurrency limit is a parameter rather
than something you build out of a semaphore. [Doc 15](15-completable-future.md) ends by asking
which of three you would use; this is the third, and it is often the right one.

Note what it does not give you: per-item timeouts, per-item fallbacks, or any error handling
beyond "the stream fails". That is where `CompletableFuture` still earns its place.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: a custom gatherer's state is a mutable array in a closure — what does that do to
reasoning about a parallel stream, and is `ofSequential` a fix or an admission? How does a
four-stage pipeline with two gatherers in it read in a stack trace? ([Docs 09, 12 and 15 ask the
same question; this is the fourth time.](INDEX.md)) `mapConcurrent` is bounded by its parameter —
where does backpressure live when the source is faster than the mapper?
