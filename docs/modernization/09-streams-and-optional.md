# 09. Streams and Optional

**Java 8** (the core: `Stream`, `Collectors`, `Optional`) · `mapMulti` is **Java 16** · concept
stage 09 of [the concept order](00-start-here.md) · no build change. The additions since 8 are
[doc 17](17-stream-and-optional-additions.md).

> **No safety net:** `FindDups`, `FindMiddleIndex`, `RandomFileLines` have no tests. Write them
> first, in a separate commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why the
> order matters.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `FindDups`](#exercise-1-finddups) | Turn the loop into a stream without changing what it counts. | T1, T3, T5 | Characterize first, with a mixed-case test. |
| [2. `RandomFileLines`](#exercise-2-randomfilelines) | Fix the off-by-one alone, then rewrite the reading. Three commits. | T1 | Characterize the bug first; the assertions flip in the fix. |
| [3. `FairWorkLoad`](#exercise-3-fairworkload-and-a-stream-that-would-fix-a-bug-by-accident) | Diagnose a disabled test before any stream touches the code. | T4 | The `@Disabled` test, deleted in the fix commit. |
| [4. `FindMiddleIndex`](#exercise-4-optional-on-the-one-method-that-wants-it) | Replace a thrown `Exception` with `OptionalInt`. | T6 | Characterize the table first. |
| [5. `mapMulti`](#exercise-5-mapmulti-try-it-and-report) | Try it on one conversion and report honestly. | T7 | None. |
| [6. `otherValues()`](#exercise-6-a-trap-in-a-test-othervalues) | Write the duplicates test before replacing a dependency. | T3 | The duplicates test, first. |
| [7. What got worse](#exercise-7-fill-in-what-got-worse) | Write down what streams cost. | all | None. |
| [8. Review questions](#exercise-8-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the seven sections in order: each one builds on the one before. Here is the whole story in
one paragraph, so you know where it's going.

> A loop mixes two things: *what* you want from a sequence and *how* to walk it. A stream keeps
> only the what: a source, some operations, and one terminal operation that runs it (T1). Nothing
> runs until that terminal operation asks, and then each value travels the whole pipeline (T2).
> Because the stream does the walking, your lambdas must not depend on it: a lambda that changes
> something outside itself is a bug waiting for `parallel()` (T3). That gives the rule for when a
> stream fits and when a loop is the honest answer (T4). Translating a loop also means matching
> what it meant by "the same": `distinct()` and a `TreeSet` disagree (T5). `Optional` is the
> stream library's answer to "there may be no result", and it has one rule for using it well
> (T6). `flatMap` and `mapMulti` turn one element into many (T7).

Every code sample below is a complete program unless it says otherwise. Save it as the class name
plus `.java` and run it with `java Name.java`; no build needed. Outputs marked *verified* were run
on JDK 26.

### T1. From a loop to a pipeline

Here is a loop that keeps the long words, upper-cases them, and sorts them. Read it and notice how
much of it is about walking: a list to fill, a `for`, an `if`, an `add`, a sort at the end. What
you *wanted* is spread across all of it.

A **stream** says only the what. It's a pipeline with three parts:

- a **source**: `list.stream()`, `Arrays.stream(args)`, `IntStream.range(0, n)`;
- zero or more **intermediate operations**: `filter`, `map`, `distinct`, `sorted`. Each one
  returns a new stream;
- exactly one **terminal operation**: `toList`, `count`, `collect`, `sum`, `findFirst`. It
  produces the result, and it's what makes the pipeline run.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LoopVsStream {
    public static void main(String[] args) {
        List<String> words = List.of("pear", "fig", "apple", "kiwi", "banana", "plum");

        // The loop: says how to walk, and the "what" is spread across five lines.
        List<String> loop = new ArrayList<>();
        for (String w : words) {
            if (w.length() > 3) {
                loop.add(w.toUpperCase(Locale.ROOT));
            }
        }
        Collections.sort(loop);

        // The stream: source, three intermediate operations, one terminal operation.
        List<String> stream = words.stream()                       // source
                .filter(w -> w.length() > 3)                       // intermediate
                .map(w -> w.toUpperCase(Locale.ROOT))              // intermediate
                .sorted()                                          // intermediate
                .toList();                                         // terminal: this runs it

        System.out.println("loop:   " + loop);
        System.out.println("stream: " + stream);
    }
}
```

```text
loop:   [APPLE, BANANA, KIWI, PEAR, PLUM]                (verified 2026-11-23)
stream: [APPLE, BANANA, KIWI, PEAR, PLUM]
```

Same answer. The stream reads top to bottom as a list of steps, and there is no list being filled
by hand. (`Locale.ROOT` is there for the reason [doc 12](12-lambdas-and-method-references.md)
TODO 3 gives. A **collector** is a recipe for a terminal `collect`, such as `Collectors.toSet()` or
`groupingBy(...)`; [doc 14](14-map-defaults-and-collectors.md) covers them.)

### T2. Lazy: nothing runs until the terminal operation asks

**Lazy** means the intermediate operations do nothing on their own. Building a pipeline only
describes it. When the terminal operation asks for a value, that value travels through the whole
pipeline before the next one starts, and if the terminal operation already has its answer, the
rest are never looked at.

```java
import java.util.List;
import java.util.stream.Stream;

public class Lazy {
    public static void main(String[] args) {
        List<String> words = List.of("pear", "fig", "apple", "kiwi");

        Stream<String> pipeline = words.stream()
                .filter(w -> { System.out.println("  filter " + w); return w.length() > 3; })
                .map(w -> { System.out.println("  map    " + w); return w.length(); })
                .map(String::valueOf);
        System.out.println("pipeline built, nothing has run yet");

        String first = pipeline.findFirst().orElse("none");    // terminal
        System.out.println("findFirst: " + first);
    }
}
```

```text
pipeline built, nothing has run yet                      (verified 2026-11-23)
  filter pear
  map    pear
findFirst: 4
```

Three things to see. Nothing printed until `findFirst`. `pear` went through `filter` *and* `map`
before anything else happened: values travel one at a time, not stage by stage. And `fig`,
`apple` and `kiwi` were never touched, because `findFirst` had its answer. A loop would need a
`break` to do that; the stream gets it for free.

### T3. Stateless lambdas, and why a lambda must not change the outside world

A **stateless** operation looks at one value at a time: `filter`, `map`. A **stateful** one has to
remember what it has seen: `distinct` remembers every value, `sorted` holds them all. The stateful
ones are the JDK's job, and it does them correctly even in parallel. **Your own lambdas should be
stateless**: given the same value, do the same thing, and touch nothing else.

The stream decides how to walk. It may walk on several threads at once (`parallel()`), and
[doc 07](07-classic-concurrency.md) showed what shared state does across threads. Here is a lambda
that adds to a list outside the stream, next to one that lets the terminal operation build the
result:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class SideEffect {
    public static void main(String[] args) {
        // Wrong: a lambda that changes a list outside itself.
        List<Integer> outside = new ArrayList<>();
        try {
            IntStream.range(0, 100_000).parallel().forEach(outside::add);
            System.out.println("forEach into ArrayList: " + outside.size() + " elements");
        } catch (RuntimeException e) {
            System.out.println("forEach into ArrayList: threw " + e.getClass().getSimpleName());
        }

        // Right: let the terminal operation build the result.
        List<Integer> collected = IntStream.range(0, 100_000).parallel().boxed().toList();
        System.out.println("toList():               " + collected.size() + " elements");
    }
}
```

```text
forEach into ArrayList: 51588 elements                   (verified 2026-11-23; varies by run)
toList():               100000 elements
```

Half the elements vanished. Five more runs gave 48640, 48926, 22484, 56932 and 16208: never the
same, never right. An `ArrayList` isn't safe to share between threads, and it may also throw
instead of losing elements (another run threw `ArrayIndexOutOfBoundsException`), which is why the
`catch` is there. Without `parallel()` the first
version happens to work, which is worse: it's correct until somebody adds one word.

### T4. The rule: a stream fits when the answer doesn't depend on the walk

That gives one rule, and everything in the practice follows from it:

> **A stream says *what* you want from a sequence, not how to walk it, so it only fits when the
> answer doesn't depend on the walking.**

A loop that carries state from one element to the next (a running total that resets, a "current
worker", a string built as it goes) is describing the walk. Forcing it into a stream gives you a
lambda that mutates something outside itself (T3). That's a loop with extra syntax.

Here is a loop that should stay a loop. It finds the longest run of equal neighbours, and every
step needs two things the stream doesn't give a lambda: the previous value, and the run so far.

```java
public class Streak {
    // The longest run of equal neighbours. The answer depends on the walk:
    // each step needs the previous value and the run so far.
    static int longestStreak(int[] values) {
        int best = 0;
        int run = 0;
        for (int i = 0; i < values.length; i++) {
            run = (i > 0 && values[i] == values[i - 1]) ? run + 1 : 1;
            best = Math.max(best, run);
        }
        return best;
    }

    public static void main(String[] args) {
        System.out.println(longestStreak(new int[] { 1, 1, 2, 2, 2, 3, 2, 2 }));
        System.out.println(longestStreak(new int[] {}));
    }
}
```

```text
3                                                        (verified 2026-11-23)
0
```

Compare it with summing an array or finding its maximum. Each element contributes on its own, in
any order, so `IntStream.of(values).sum()` and `.max()` say exactly what you mean. The test is
quick: **if the loop body needs a variable that was set by the previous iteration, it's a loop.**

### T5. "The same" has to mean the same: `distinct()` versus a `TreeSet`

Translating a loop into a stream isn't only syntax. You also have to keep what the loop meant by
"duplicate". Here's the repo's case, `collections/FindDups.java:20-25`, written in 2014:

```java
SortedSet<String> s = new TreeSet<>(comparator);   // comparator: compareToIgnoreCase
for (String a : args)
    if (!s.add(a))
        System.out.println("Duplicate detected: " + a);
System.out.println(s.size() + " distinct words: " + s);
```

The obvious stream is `Arrays.stream(args).distinct().count()`. **It's wrong.** Given
`Cat dog cat Dog bird` (verified):

```text
TreeSet(CASE_INSENSITIVE_ORDER):   3  [bird, Cat, dog]
Arrays.stream(words).distinct():   5  [Cat, dog, cat, Dog, bird]
```

`distinct()` uses `equals`, which is case-*sensitive*. The `TreeSet` uses its comparator, which
isn't. The two say different things about the same input, and only one of them is what the 2014
code did. Two stream versions that do match it (fragments):

```java
// 1. normalise first, then distinct: but now the output is all lower case
Arrays.stream(args).map(w -> w.toLowerCase(Locale.ROOT)).distinct().sorted().toList()
// [bird, cat, dog]

// 2. collect into the same kind of set: keeps the first spelling seen, like the original
Arrays.stream(args).collect(Collectors.toCollection(
    () -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)))
// [bird, Cat, dog]
```

Form 2 is the honest translation, and **it's barely a stream**: one source and one collector. And
neither form says *which* words were duplicates. Reporting that from a stream means
`filter(w -> !seen.add(w))`, a lambda that changes a set outside itself, which is exactly what T3
says not to do. If you want the duplicates, count them with `groupingBy`, which is
[doc 14](14-map-defaults-and-collectors.md)'s problem.

### T6. `Optional`: when "no answer" is an answer

Many methods have an ordinary case where there's nothing to return: no word is long enough, the
array is empty, no index matches. The old ways to say so all hide it from the caller. Returning
`null` crashes someone three calls later. A sentinel like `-1` is easy to use as a real value. A
thrown exception treats an ordinary answer as an error.

An **`Optional<T>`** is a value that may be absent, and the return type says so. `OptionalInt`,
`OptionalLong` and `OptionalDouble` are the primitive versions, and they're what `IntStream`'s
`max()`, `min()` and `findFirst()` return.

```java
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

public class Absent {
    // "No such word" is an ordinary answer, so the type says it may be missing.
    static Optional<String> firstLongerThan(List<String> words, int length) {
        return words.stream().filter(w -> w.length() > length).findFirst();
    }

    public static void main(String[] args) {
        List<String> words = List.of("pear", "fig", "banana");

        Optional<String> found = firstLongerThan(words, 4);
        Optional<String> missing = firstLongerThan(words, 10);

        // Say what to do with the value, and without it, in one expression.
        System.out.println(found.map(String::length).orElse(0));
        System.out.println(missing.map(String::length).orElse(0));
        missing.ifPresentOrElse(w -> System.out.println("found " + w),
                                () -> System.out.println("none"));

        // The primitive versions come out of IntStream's reductions.
        OptionalInt max = IntStream.of(3, 9, 4).max();
        OptionalInt noMax = IntStream.empty().max();
        System.out.println(max + " " + noMax);
        System.out.println(noMax.orElse(-1));
        try {
            noMax.orElseThrow();                       // when absence is a bug
        } catch (RuntimeException e) {
            System.out.println("orElseThrow: " + e.getClass().getSimpleName());
        }
    }
}
```

```text
6                                                        (verified 2026-11-23)
0
none
OptionalInt[9] OptionalInt.empty
-1
orElseThrow: NoSuchElementException
```

**The rule for using it: no `isPresent()` followed by `get()`.** If you write

```java
if (r.isPresent()) print(r.getAsInt()); else print("none");
```

you've used `Optional` as a null check with extra syntax and got nothing for it. Say what to do
with the value, and what to do without it, in one expression, as `Absent` does: `map` and
`orElse`, `ifPresentOrElse` (Java 9), or `orElseThrow()` when absence would be a bug. (The rule
doesn't forbid `getAsInt()`; it forbids testing and then getting as two steps, when one step says
it better.)

`Optional` is for **return values**. Don't use it for fields or parameters, where it only adds a
third state (`null`, empty, present) to something that had two.

### T7. One in, many out: `flatMap` and `mapMulti`

`map` turns each element into exactly one. Sometimes one element becomes several, or none: a line
becomes its words. `flatMap` does it by turning each element into a whole `Stream` and joining
them. `mapMulti` (Java 16) does it without building a `Stream` per element: you get a callback and
push zero or more outputs into it.

```java
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class Multi {
    public static void main(String[] args) {
        List<String> lines = List.of("the cat", "", "sat on the mat");

        // flatMap: each element becomes a Stream, and the streams are joined.
        List<String> viaFlatMap = lines.stream()
                .flatMap(line -> Arrays.stream(line.split(" ")))
                .filter(w -> !w.isEmpty())
                .toList();

        // mapMulti (Java 16): each element pushes zero or more outputs into a callback.
        List<String> viaMapMulti = lines.stream()
                .<String>mapMulti((String line, Consumer<String> out) -> {
                    for (String w : line.split(" ")) {
                        if (!w.isEmpty()) out.accept(w);
                    }
                })
                .toList();

        System.out.println("flatMap:  " + viaFlatMap);
        System.out.println("mapMulti: " + viaMapMulti);
    }
}
```

```text
flatMap:  [the, cat, sat, on, the, mat]                  (verified 2026-11-23)
mapMulti: [the, cat, sat, on, the, mat]
```

Same result. Notice the price: `mapMulti` needs a type witness (`<String>`) and typed lambda
parameters before the compiler can follow it, and its body is a little loop again. It saves the
cost of one small `Stream` per element, which only matters when there are very many elements and
each one produces very few outputs. Exercise 5 asks you to find out whether it matters here.

## Practice

### Exercise 1: `FindDups`

T5 is this file's worked example.

1. **Extract and characterize first**: [`../testing-first.md`](../testing-first.md) has this
   exact file as its worked example. Include a test with mixed case (`"Cat", "cat"`), because that
   is the one that catches the `distinct()` mistake.
2. **Write the stream version** and keep the test green. Decide between the two forms in T5, and
   decide what happens to the "Duplicate detected" lines. Both changes are behaviour you have to
   choose, not syntax you have to translate.
3. If [doc 12](12-lambdas-and-method-references.md) hasn't already deleted the anonymous
   comparator, delete it now: `String.CASE_INSENSITIVE_ORDER` has been in the JDK since Java 2.

**Done when:** the characterization test (with mixed case) went in first, the stream version keeps
it green, and you've written down which form you chose and what happened to the duplicate report.

### Exercise 2: `RandomFileLines`

`collections/RandomFileLines.java` has four problems stacked on top of each other. This doc owns
two of them, and [doc 18](18-files-strings-and-charsets.md) owns the other two (the hand-written
`finally` that throws `NullPointerException` if the open failed, and the platform charset).

**The off-by-one, `RandomFileLines.java:35`:**

```java
index = rline.nextInt(lstLines.size()-1);
```

`nextInt(bound)` returns `0` to `bound - 1`. So with five lines this picks from `0..3`, and
**the last line can never be printed**. Verified, 100,000 draws over five lines:

```text
[25061, 25034, 24768, 25137, 0]
```

And with a one-line file it's `nextInt(0)`, which throws `IllegalArgumentException: bound must
be positive` (verified). So a file with exactly one non-blank line crashes the program.

**The double report, lines 64-66:** `catch (Exception e) { System.out.println(e.getMessage());
throw e; }` logs the failure and then rethrows it, and `main` prints whatever arrives at line 25.
One failure, two messages, and the first one has no stack trace. If the open itself failed, what
arrives is the `finally`'s `NullPointerException`, not the original: see
[doc 18](18-files-strings-and-charsets.md).

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

**Done when:** the three commits are in that order, and both assertions flipped in commit 2, not
in the rewrite.

### Exercise 3: `FairWorkLoad`, and a stream that would fix a bug by accident

`algorithm/FairWorkLoad.java:15-58` is a binary search over index arithmetic, and its inner loop
(lines 26-44) carries three pieces of state from one cabinet to the next: `currentLoad`,
`requiredWorkers`, and the trace `line`. By T4's rule, **that loop is not a stream**. A stream
version needs a lambda mutating all three. Writing that down is the answer.

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
`numbers.stream().mapToInt(Integer::intValue).sum()`. `getSum` wraps on large input, which on
valid input turns out to be harmless. The class has a real overflow bug that starts before the sum
overflows, and the stream doesn't fix it: [DSA stage 09](../dsa/09-integer-overflow.md) owns that.

**Done when:** you've written down why the inner loop isn't a stream, the bug fix and the
`@Disabled` deletion are one commit in loop form, and the helper conversion is a later commit with
every test green before and after.

### Exercise 4: `Optional`, on the one method that wants it

`algorithm/FindMiddleIndex.java:9-33` finds the index where the sum of everything up to and
including it equals the sum of everything after it. When there isn't one, it **throws a checked
`Exception`**, with the message `"Please pass proper array to match the requirement"`. Verified:

| Input | Result |
| --- | --- |
| `{2, 4, 4, 5, 4, 1}` | `2` (2+4+4 = 5+4+1) |
| `{1, 2, 3}` | `1` |
| `{1, 1}` | `0` |
| `{5}` | throws `Exception` |

"No such index" isn't an error. It's an ordinary answer (T6). That's what `OptionalInt` is for:

```java
static OptionalInt findMiddleIndex(int[] numbers)
```

1. **Characterize** the table above as tests. It's one of the two classes
   [`../testing-first.md`](../testing-first.md) says to start with, because it already returns a
   value.
2. **Change the return type** to `OptionalInt`: `OptionalInt.of(endIndex)` where it returns,
   `OptionalInt.empty()` where it throws. The `{5}` test changes from `assertThrows` to
   `assertTrue(result.isEmpty())`, and `main` stops needing a `try/catch`.

Use T6's rule in `main`: no `isPresent()` followed by `get()`.

**Done when:** the table is four tests, the return type is `OptionalInt`, only the `{5}` test
changed in the second commit, and `main` has no `try/catch` and no `isPresent()`.

### Exercise 5: `mapMulti`, try it and report

T7 showed the syntax. **Try `mapMulti` on one of the conversions above and say honestly whether it
beat `flatMap`** at this size. It usually doesn't, and "I tried it and it was not worth it" is the
better answer.

**Done when:** you've written two or three lines on what you tried and whether it was worth it.

### Exercise 6: a trap in a test, `otherValues()`

`ProductsOfArrayElementsTest.java:30-32` uses commons-lang3 for one call:

```java
private int[] otherValues(int[] inputArray, int elem) {
    return ArrayUtils.removeElement(inputArray, elem);
}
```

[Doc 00](00-start-here.md) asks whether that dependency still earns its place, and the natural
stream replacement is `IntStream.of(inputArray).filter(v -> v != elem).toArray()`. **They aren't
the same.** `removeElement` removes the **first** occurrence; `filter` removes **every** one. For
the "product of every other element" this test computes, `removeElement` happens to be right and
`filter` is wrong (verified, input `{2, 2, 3}`):

```text
removeElement:  [6, 6, 4]     // 2·3, 2·3, 2·2: correct
filter:         [3, 3, 4]     // both 2s removed: wrong
```

The test data `{1, 3, 5, 2}` has no repeated values, so it can't tell them apart. It would stay
green while the behaviour changed. **Write the duplicates test first**, and only then replace the
dependency.

**Done when:** a test with a repeated value is in before the replacement, and stays green after it.

### Exercise 7: fill in "What got worse"

- _(fill this in)_

Prompts: what does a four-stage stream do to a stack trace? When is a plain `for` loop the right
answer, and how do you tell before you have written both? (`FairWorkLoad`'s inner loop is one
answer. Can you state the rule it follows?) Compare your stack-trace answer with the same question
in [doc 12](12-lambdas-and-method-references.md) and [doc 15](15-completable-future.md).

### Exercise 8: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. Name the three parts of a stream pipeline, and say which part makes it run. (T1)
2. In `Lazy`, why were `fig`, `apple` and `kiwi` never filtered? (T2)
3. What's the difference between a stateless and a stateful operation? Give one JDK example of
   each. (T3)
4. `forEach(outside::add)` on a parallel stream gave a different size every run. Why, and what
   should it have been instead? (T3)
5. State the rule for when a stream fits. What's the quick test for a loop that should stay a
   loop? (T4)
6. Given `Cat dog cat Dog bird`, what do `distinct().count()` and a case-insensitive `TreeSet`
   give, and why do they disagree? (T5)
7. Name three old ways to say "no result", and what's wrong with each. (T6)
8. What's wrong with `if (r.isPresent()) ... r.get()`, and what do you write instead? (T6)
9. Where should `Optional` not be used, and why? (T6)
10. What does `mapMulti` save compared with `flatMap`, and when does that saving matter? (T7)

**Done when:** you can answer all ten without looking.
