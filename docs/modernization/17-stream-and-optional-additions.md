# 17. Stream and `Optional` additions after 8

**Java 9, 10, 12, 16** · stage 6 of [the release-ordered track](INDEX.md) · `--release 21`, no
build change

> **No safety net:** `FindDups`, `RandomFileLines` have no tests. Write them first, in a
> separate commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why the
> order matters.

## Where it lands

[Doc 09](09-streams-and-optional.md) covers the Java 8 core against `FindDups`, `RandomFileLines`
and `FairWorkLoad`. This document is the **accretion**: four releases of additions that are the
reason modern stream code looks nothing like 2014 stream code.

New code: **`functional/StreamAdditions.java`**, six TODOs. No old-code target, because the old
code has no streams in it at all.

## The exercise

### `takeWhile` is not `filter`, and this is the one people get wrong

The fixture is `[1, 2, 3, 10, 4, 5]` and the predicate is `< 5`:

    takeWhile(x -> x < 5)   ->  [1, 2, 3]            stops at the 10
    filter(x -> x < 5)      ->  [1, 2, 3, 4]         keeps going
    dropWhile(x -> x < 5)   ->  [10, 4, 5]           everything from the 10 onwards

**`filter` is what everyone writes and it is frequently not what they meant.** "Give me the log
lines until the first error" is `takeWhile`. "Give me the lines that are not errors" is `filter`.
Writing `filter` for the first one works on every test fixture where the data happens to be
sorted, which is most of them.

Note also that `takeWhile` and `dropWhile` need an **ordered** stream to mean anything, and that
on a parallel stream `takeWhile` has to do real work to preserve that — one of the few places
where `.parallel()` makes things slower in a way you can predict from the API alone.

### Bounded `iterate`

TODO 3. Write it the Java 8 way first:

    Stream.iterate(1, i -> i * 2).limit(7)                // Java 8: you must know the count
    Stream.iterate(1, i -> i < 100, i -> i * 2)           // Java 9: you state the condition

The three-argument form exists because the two-argument one is **infinite**, so the termination
condition had to live at the call site as a `limit()` with a precomputed count — and computing
"how many powers of two are below 100" to pass to `limit` is the bug. This is a `for` loop that
finally has a stream spelling.

### `Optional`, without `isPresent()`

TODOs 4 and 5. [Doc 09](09-streams-and-optional.md) states the rule — **no `isPresent()` followed
by `get()`** — and these are the two methods that let you keep it:

    first.or(() -> second)                                  // Java 9, Optional -> Optional
    options.stream().flatMap(Optional::stream)              // Java 9, drop the empties

Compare that `flatMap` with what you had to write before:

    .filter(Optional::isPresent).map(Optional::get)         // Java 8

Both work. Say which one you would rather find in a review and why. Also learn
`ifPresentOrElse` (9) and the no-argument `orElseThrow()` (10), which is `get()` with a name that
tells the truth — **`get()` is effectively deprecated by convention** and the only reason it is
still there is compatibility.

### `teeing`

TODO 6. `Collectors.teeing` (Java 12) runs two collectors over one pass and merges the results:

    collect(teeing(summarizingInt(..), counting(), (stats, n) -> "min=" + .. + " max=" + ..))

It is the answer to "I need the min and the max and the stream is not re-traversable". **Then
answer honestly whether one pass was worth it here**, because for a `List` in memory it is not —
two passes over six elements is free and reads better. `teeing` earns its keep on a stream you
cannot rewind: a file, a socket, a database cursor. If you cannot name a case like that in your own
work, that is a legitimate "what got worse" line.

While you are here, `mapMulti` (16) is the other one, and [doc 09](09-streams-and-optional.md)
already asks you to try it and report that it probably was not worth it.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: `takeWhile` on a parallel stream — what does it have to do, and when is that worse than
not parallelising? What does `Optional` as a *field* type do to serialisation, and why does the
javadoc specifically tell you not to? If `Optional.or` takes a `Supplier`, what does that buy over
taking an `Optional`, and would you have noticed the difference at a call site that does I/O?
`Stream.iterate` three-arg is a `for` loop — at what point does the loop win?
