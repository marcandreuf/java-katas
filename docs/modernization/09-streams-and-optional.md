# 09. Streams and Optional

**Java 8** (the core: `Stream`, `Collectors`, `Optional`) · `mapMulti` is **Java 16** · concept
stage 09 of [the concept order](00-start-here.md) · no build change. The additions since 8 are
[doc 17](17-stream-and-optional-additions.md).

> **No safety net:** `FindDups`, `FindMiddleIndex`, `RandomFileLines` have no tests. Write them
> first, in a separate commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why the
> order matters.

## What this stage is actually about

Five words, defined once:

- a **stream** is a pipeline over a sequence of values: a **source** (`Arrays.stream(args)`,
  `list.stream()`), zero or more **intermediate operations** (`filter`, `map`, `distinct`,
  `sorted`), and one **terminal operation** (`count`, `toList`, `collect`, `sum`) that runs the
  whole thing.
- **lazy** means the intermediate operations do nothing on their own. Nothing runs until the
  terminal operation asks for values, and then each value travels through the whole pipeline.
- a **stateless** operation looks at one value at a time (`filter`, `map`). A **stateful** one has
  to remember what it has seen (`distinct`, `sorted`). Your own lambdas should be stateless; the
  stateful ones are the JDK's job.
- an **`Optional<T>`** is a value that may be absent, returned instead of `null` or a sentinel.
  `OptionalInt` is the primitive version.
- a **collector** is a recipe for a terminal `collect`: `Collectors.toSet()`,
  `groupingBy(...)`. [Doc 14](14-map-defaults-and-collectors.md) covers them.

That gives one rule, and everything below follows from it: **a stream says *what* you want from a
sequence, not how to walk it, so it only fits when the answer doesn't depend on the walking.** A
loop that carries state from one element to the next (a running total that resets, a "current
worker", a string built as it goes) is describing the walk, and forcing it into a stream gives you
a lambda that mutates something outside itself. That's a loop with extra syntax.

## The worked example

`collections/FindDups.java:20-25`, written in 2014:

    SortedSet<String> s = new TreeSet<String>(comparator);   // comparator: compareToIgnoreCase
    for (String a : args)
        if (!s.add(a))
            System.out.println("Duplicate detected: " + a);
    System.out.println(s.size() + " distinct words: " + s);

The obvious stream is `Arrays.stream(args).distinct().count()`. **It's wrong.** Given
`Cat dog cat Dog bird` (verified):

    TreeSet(CASE_INSENSITIVE_ORDER):   3  [bird, Cat, dog]
    Arrays.stream(words).distinct():   5  [Cat, dog, cat, Dog, bird]

`distinct()` uses `equals`, which is case-*sensitive*. The `TreeSet` uses its comparator, which
isn't. The two say different things about the same input, and only one of them is what the 2014
code did. Two stream versions that do match it:

    // 1. normalise first, then distinct: but now the output is all lower case
    Arrays.stream(args).map(w -> w.toLowerCase(Locale.ROOT)).distinct().sorted().toList()
    // [bird, cat, dog]

    // 2. collect into the same kind of set: keeps the first spelling seen, like the original
    Arrays.stream(args).collect(Collectors.toCollection(
        () -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)))
    // [bird, Cat, dog]

Form 2 is the honest translation, and **it's barely a stream**: one source and one collector.
(The `Locale.ROOT` is there for the reason [doc 12](12-lambdas-and-method-references.md) TODO 3
gives.) And neither form says *which* words were duplicates. Reporting that from a stream means
`filter(w -> !seen.add(w))`, a lambda that changes a set outside itself, which is exactly what the
rule says not to do. If you want the duplicates, count them with `groupingBy`, which is
[doc 14](14-map-defaults-and-collectors.md)'s problem.

## Part one: `FindDups`

1. **Extract and characterize first**: [`../testing-first.md`](../testing-first.md) has this
   exact file as its worked example. Include a test with mixed case (`"Cat", "cat"`), because that
   is the one that catches the `distinct()` mistake.
2. **Write the stream version** and keep the test green. Decide between the two forms above, and
   decide what happens to the "Duplicate detected" lines. Both changes are behaviour you have to
   choose, not syntax you have to translate.
3. If [doc 12](12-lambdas-and-method-references.md) hasn't already deleted the anonymous
   comparator, delete it now: `String.CASE_INSENSITIVE_ORDER` has been in the JDK since Java 2.

## Part two: `RandomFileLines`

`collections/RandomFileLines.java` has four problems stacked on top of each other. This doc owns
two of them, and [doc 18](18-files-strings-and-charsets.md) owns the other two (the hand-written
`finally` that throws `NullPointerException` if the open failed, and the platform charset).

**The off-by-one, `RandomFileLines.java:35`:**

    index = rline.nextInt(lstLines.size()-1);

`nextInt(bound)` returns `0` to `bound - 1`. So with five lines this picks from `0..3`, and
**the last line can never be printed**. Verified, 100,000 draws over five lines:

    [25061, 25034, 24768, 25137, 0]

And with a one-line file it's `nextInt(0)`, which throws `IllegalArgumentException: bound must
be positive` (verified). So a file with exactly one non-blank line crashes the program.

**The double report, lines 64-66:** `catch (Exception e) { System.out.println(e.getMessage());
throw e; }` logs the failure and then rethrows it, and `main` catches it at line 25 and prints it
again. One failure, two messages, and the first one has no stack trace.

Commits, in this order:

1. **Extract and characterize.** The line selection needs a `Random` you can seed, so pass one
   in. That is the change [doc 19](19-randomness-and-numerics.md) is about. Assert that the last
   line is never chosen over many draws, and that a one-line list throws. Both are today's
   behaviour.
2. **Fix the off-by-one**, alone. Both assertions flip in this commit, and that's the evidence.
   If you do the rewrite first, it will probably fix this by accident, and the bug will vanish
   without anyone having seen it.
3. **Then rewrite the reading** with `Files.readAllLines` or `Files.lines` (doc 18 has the
   details), which also removes the capacity guess at line 53: `ArrayList` grows on its own.

## Part three: `FairWorkLoad`, and a stream that would fix a bug by accident

`algorithm/FairWorkLoad.java:15-58` is a binary search over index arithmetic, and its inner loop
(lines 26-44) carries three pieces of state from one cabinet to the next: `currentLoad`,
`requiredWorkers`, and the trace `line`. By the rule above, **that loop is not a stream**. A
stream version needs a lambda mutating all three. Writing that down is the answer.

The two helpers at lines 61-71 *are* stream-shaped: each one reduces an array to one number.
**Don't convert them yet**, and here's why. `FairWorkloadTest.testMaxNumPerSixWorkers` has been
`@Disabled` since 2014 with "cause never diagnosed". As written, `getMostWork` returns **900** for
that test's input, and the test expects **950** (verified). Rewrite one of those two helpers as the
obvious one-line stream and change nothing else, and it returns **950** (verified). The
twelve-year-old test passes.

**That is exactly the outcome to avoid.** The bug would be gone, and nobody would know what it
was, or that the stream was what fixed it. A rewrite that makes a failing test pass by accident
has taught you nothing. So:

1. **Diagnose the disabled test first**, without changing the code. Work out which helper, and
   why every other test passes anyway. [DSA stage 06](../dsa/06-answer-space-search.md) has the
   full diagnosis. Try first, then read it.
2. **Fix it as its own commit**: the smallest change, in the loop form, with the `@Disabled`
   deleted in the same commit, so the diff carries the evidence.
3. **Only then** convert the two helpers to `Arrays.stream(...).max()` and `.sum()`, as a separate
   commit, with every test green before and after.

`algorithm/DuplicateNumber.java:24-31` (`getSum`) is the same shape and converts the same way:
`numbers.stream().mapToInt(Integer::intValue).sum()`. It has an overflow problem the stream
doesn't fix: [DSA stage 09](../dsa/09-integer-overflow.md) owns that.

## Part four: `Optional`, on the one method that wants it

`algorithm/FindMiddleIndex.java:9-33` finds the index where the sum of everything up to and
including it equals the sum of everything after it. When there isn't one, it **throws a checked
`Exception`**, with the message `"Please pass proper array to match the requirement"`. Verified:

| Input | Result |
| --- | --- |
| `{2, 4, 4, 5, 4, 1}` | `2` (2+4+4 = 5+4+1) |
| `{1, 2, 3}` | `1` |
| `{1, 1}` | `0` |
| `{5}` | throws `Exception` |

"No such index" isn't an error. It's an ordinary answer. That's what `OptionalInt` is for:

    static OptionalInt findMiddleIndex(int[] numbers)

1. **Characterize** the table above as tests. It's one of the two classes
   [`../testing-first.md`](../testing-first.md) says to start with, because it already returns a
   value.
2. **Change the return type** to `OptionalInt`: `OptionalInt.of(endIndex)` where it returns,
   `OptionalInt.empty()` where it throws. The `{5}` test changes from `assertThrows` to
   `assertTrue(result.isEmpty())`, and `main` stops needing a `try/catch`.

**And the rule for using it: no `isPresent()` followed by `get()`.** If you write

    if (r.isPresent()) print(r.getAsInt()); else print("none");

you've used `Optional` as a null check with extra syntax and got nothing for it. Say what to do
with the value, and what to do without it, in one expression:

    r.ifPresentOrElse(i -> print("index " + i), () -> print("none"));   // Java 9
    int index = r.orElseThrow();                                        // when absence is a bug

(The rule doesn't forbid `getAsInt()`; it forbids testing and then getting as two steps, when
one step says it better.) `Optional` is for **return values**. Don't use it for fields or
parameters, where it only adds a third state (`null`, empty, present) to something that had two.

## Part five: `mapMulti`, try it and report

`mapMulti` (Java 16) is `flatMap` without building a `Stream` per element: you get a callback
and push zero or more outputs into it. **Try it on one of the conversions above and say honestly
whether it beat `flatMap`** at this size. It usually doesn't, and "I tried it and it was not worth
it" is the better answer.

## And a trap in a test: `otherValues()`

`ProductsOfArrayElementsTest.java:30-32` uses commons-lang3 for one call:

    private int[] otherValues(int[] inputArray, int elem) {
        return ArrayUtils.removeElement(inputArray, elem);
    }

[Doc 00](00-start-here.md) asks whether that dependency still earns its place, and the natural
stream replacement is `IntStream.of(inputArray).filter(v -> v != elem).toArray()`. **They aren't
the same.** `removeElement` removes the **first** occurrence; `filter` removes **every** one. For
the "product of every other element" this test computes, `removeElement` happens to be right and
`filter` is wrong (verified, input `{2, 2, 3}`):

    removeElement:  [6, 6, 4]     // 2·3, 2·3, 2·2: correct
    filter:         [3, 3, 4]     // both 2s removed: wrong

The test data `{1, 3, 5, 2}` has no repeated values, so it can't tell them apart. It would stay
green while the behaviour changed. **Write the duplicates test first**, and only then replace the
dependency.

## What got worse

- _(fill this in)_

Prompts: what does a four-stage stream do to a stack trace? When is a plain `for` loop the right
answer, and how do you tell before you have written both? (`FairWorkLoad`'s inner loop is one
answer. Can you state the rule it follows?) Compare your stack-trace answer with the same question
in [doc 12](12-lambdas-and-method-references.md) and [doc 15](15-completable-future.md).
