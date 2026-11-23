# 17. Stream and `Optional` additions after 8

**Java 9** (`takeWhile`, `dropWhile`, three-argument `iterate`, `Optional.or`, `Optional.stream`,
`ifPresentOrElse`) · **Java 10** (`orElseThrow()`) · **Java 12** (`Collectors.teeing`) ·
**Java 16** (`Stream.toList`, `mapMulti`) · stage 6 of [the release-ordered track](INDEX.md) · no
build change

[Doc 09](09-streams-and-optional.md) is the Java 8 core. This doc is what four later releases
added on top, and it's mostly new code: **every file in `src/main` that uses a stream or an
`Optional` was added in 2026** (verified with `git log`). The 2014 code has none. Exercise 4 is
the one old-code site, and it already has a test.

**A correction to the old version:** its "No safety net" banner named `FindDups` and
`RandomFileLines`, which this doc never touches. Doc 09 owns them, and its banner covers them.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. TODOs 1–3](#exercise-1-streamadditions-todos-13-stop-early-and-bounded-iterate) | `takeWhile`, `filter`, `dropWhile`, and the three-argument `iterate`. | T1, T2 | `StreamAdditionsTest`: delete its `@Disabled`. |
| [2. TODOs 4–5](#exercise-2-optional-todos-45) | `Optional.or` and `Optional::stream`. | T3, T4 | Same test class. |
| [3. TODO 6](#exercise-3-teeing-todo-6) | Min and max in one pass with `teeing`, then say whether it was worth it. | T5 | Same test class. |
| [4. `getLoop`](#exercise-4-the-one-old-code-site-getloop) | Rewrite a 2014 loop as one stream, then judge it. | T2, T6 | Already tested: `loopDoorsInOrderOfN`. |
| [5. What got worse](#exercise-5-fill-in-what-got-worse) | Write down what the additions cost. | all | None. |
| [6. Review questions](#exercise-6-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the six sections in order. Here is the whole story in one paragraph, so you know where it's
going.

> Java 8 streams could filter, map and collect, but a lot of everyday loops didn't fit them: a
> loop that **stops** at the first bad element (T1), a loop that runs **while** a condition holds
> (T2). `Optional` had the same gap: it couldn't **fall back** to a second source without being
> unwrapped (T3), and it didn't fit into a stream (T4). Later releases added one method for each
> of those shapes. Two more additions are about collecting: getting **two answers from one pass**
> over data you can only read once (T5), and the shortest way to get a `List` out, which turns
> out not to be the same list as before (T6).

The rule behind all of them:

> **Each addition names a loop shape that Java 8 streams couldn't express: stop at the first
> failure, loop while a condition holds, fall back to another source.** Use one when it is the
> shape you mean. If the data's order doesn't matter, or the loop doesn't stop early, the older
> operation is the honest one.

Every code sample below is a complete program. Save it as the class name plus `.java` and run it
with `java Name.java`; no build needed. They use nothing newer than Java 16, so they'd compile at
this repo's `--release` too. Outputs marked *verified* were run on JDK 26. Stream basics
(pipelines, laziness, `Optional` itself) are in [doc 09's theory](09-streams-and-optional.md#theory);
this doc doesn't repeat them.

### T1. Stopping early: `takeWhile`, `dropWhile`, and encounter order

**The problem.** "Give me the log lines before the first error" is a loop with a `break`. Java 8
streams had no `break`: `filter` looks at every element and keeps the ones that match, wherever
they are. So the closest stream answered a different question, and it was easy not to notice.

Java 9 added the `break`:

- **`takeWhile(p)`** keeps elements from the start *while* `p` holds, and stops at the first one
  that fails. It's **short-circuiting**: it can finish without reading the whole stream, like
  `findFirst`, `anyMatch` and `limit`.
- **`dropWhile(p)`** is the other half: it skips elements while `p` holds, then keeps everything
  from the first failure on, *including* any later elements that would have matched.

```java
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public class Prefix {
    public static void main(String[] args) {
        List<String> log = List.of("INFO start", "INFO load", "ERROR disk", "INFO retry", "INFO done");

        System.out.println("takeWhile: " + log.stream().takeWhile(l -> !l.startsWith("ERROR")).toList());
        System.out.println("filter:    " + log.stream().filter(l -> !l.startsWith("ERROR")).toList());
        System.out.println("dropWhile: " + log.stream().dropWhile(l -> !l.startsWith("ERROR")).toList());

        // The same question asked of a collection with no encounter order.
        Set<Integer> unordered = new HashSet<>();
        IntStream.of(64, 1, 33, 2, 3, 70, 4, 90, 5, 6).forEach(unordered::add);
        System.out.println("HashSet iterates as: " + unordered);
        System.out.println("takeWhile(x < 5):    " + unordered.stream().takeWhile(x -> x < 5).toList());
    }
}
```

```text
takeWhile: [INFO start, INFO load]                       (verified 2026-11-23)
filter:    [INFO start, INFO load, INFO retry, INFO done]
dropWhile: [ERROR disk, INFO retry, INFO done]
HashSet iterates as: [64, 1, 33, 2, 3, 4, 5, 70, 6, 90]
takeWhile(x < 5):    []
```

"What happened before it broke" is `takeWhile`. "Everything except the errors" is `filter`.
Writing `filter` for the first question passes every test whose fixture has the error last.
`takeWhile` and `dropWhile` with the same predicate split the stream in two, and together give
back the whole of it, in order. `filter` and its negation don't: `INFO retry` would be on the
"keep" side of `filter` but after the break for `takeWhile`.

**Encounter order** is the order a stream hands out its elements. A `List` has one; a `HashSet`
doesn't ([doc 10](10-sequenced-collections-and-httpclient.md) defines it for collections). The
last line above is what happens without one: the set happened to start with `64`, the first
element failed, and the stream stopped with `[]`. Nothing threw. The javadoc allows exactly this:
on an unordered stream, `takeWhile` "is free to take any subset of matching elements (which
includes the empty set)" (verified in the JDK 26 source). **The operation is a statement about
order, and a `HashSet` has none to give.**

Order costs something in parallel too. The `takeWhile` javadoc says it "can be quite expensive on
ordered parallel pipelines" (verified in the JDK 26 source), because it must return "the longest
prefix of elements in the encounter order", not just any matching prefix, and the threads working
on later chunks can't know where that prefix ends. That's one of the few places you can predict
from the API alone that `.parallel()` may make things slower.

### T2. A loop with a condition: the three-argument `iterate`

**The problem.** Java 8's `Stream.iterate(seed, step)` is infinite: `1, 2, 4, 8, ...` forever.
To end it you add `limit(n)`, and that means you must know *how many* elements you want before
you start. Often you don't. You know when to stop, not how many steps it takes.

Java 9's `Stream.iterate(seed, hasNext, next)` takes the stopping condition instead. It's a `for`
loop, one argument per part: `for (seed; hasNext; next)`. The Collatz sequence is a good test,
because nobody knows its length in advance:

```java
import java.util.stream.Stream;

public class Collatz {
    public static void main(String[] args) {
        // Java 8: an infinite stream, so you have to know the length in advance.
        // You don't, for Collatz: that's the whole question.
        // Java 9: seed, condition, step. The condition ends the stream.
        long steps = Stream.iterate(27L, n -> n != 1, n -> n % 2 == 0 ? n / 2 : 3 * n + 1).count();
        System.out.println("27 reaches 1 after " + steps + " steps");

        // The same three parts as a for loop:
        long loopSteps = 0;
        for (long n = 27; n != 1; n = n % 2 == 0 ? n / 2 : 3 * n + 1) loopSteps++;
        System.out.println("for loop agrees:     " + loopSteps);

        // The condition is only as good as the arithmetic. skip and limit are here so this demo ends.
        Stream.iterate(1, i -> i < 2_000_000_000, i -> i * 2)
              .skip(29).limit(5)
              .forEach(i -> System.out.println("  " + i));
    }
}
```

```text
27 reaches 1 after 111 steps                             (verified 2026-11-23)
for loop agrees:     111
  536870912
  1073741824
  -2147483648
  0
  0
```

The last part is the trap the three-argument form doesn't fix. Without the `skip`/`limit`,
`Stream.iterate(1, i -> i < 2_000_000_000, i -> i * 2)` **never ends** (verified, first 35
elements): after 1073741824, `i * 2` overflows to -2147483648, which is less than two billion,
and then to 0, which doubles to 0 forever. A `for` loop with the same condition has the same bug.
**Stating the condition doesn't make it true.** [DSA stage 09](../dsa/09-integer-overflow.md) owns
overflow.

### T3. Falling back, lazily: `or`, `orElse`, `orElseGet`

**The problem.** "Look in the cache; if it's not there, read the disk; if that fails too, ask
the database." In Java 8 `Optional` could give you a final default (`orElse`), but it couldn't
fall through to *another `Optional`*. You had to unwrap with `isPresent()` and `get()`, which
[doc 09](09-streams-and-optional.md) tells you not to do.

Java 9's **`first.or(supplier)`** returns `first` if it has a value, otherwise whatever the
`Supplier` returns, and the result is still an `Optional`, so you can chain the next fallback.

The word that matters is *supplier*. A **lazy argument** is a `Supplier` that's only called if its
value is needed. An eager one is computed before the method is even called, because Java evaluates
every argument before the call:

```java
import java.util.Optional;

public class Fallbacks {
    static int calls = 0;

    static String expensiveDefault() {           // stands in for a database or HTTP call
        calls++;
        return "from the database";
    }

    static Optional<String> fromDisk() {
        calls++;
        return Optional.of("from disk");
    }

    public static void main(String[] args) {
        Optional<String> cached = Optional.of("from the cache");

        calls = 0;
        String a = cached.orElse(expensiveDefault());              // argument evaluated first
        System.out.println("orElse:     " + a + "   calls=" + calls);

        calls = 0;
        String b = cached.orElseGet(Fallbacks::expensiveDefault);  // supplier, called only if empty
        System.out.println("orElseGet:  " + b + "   calls=" + calls);

        calls = 0;
        Optional<String> c = cached.or(Fallbacks::fromDisk);       // like orElseGet, but stays an Optional
        System.out.println("or:         " + c + "   calls=" + calls);

        calls = 0;
        String d = Optional.<String>empty()
                .or(Fallbacks::fromDisk)                           // still empty? try the next source
                .orElseGet(Fallbacks::expensiveDefault);           // and finally leave Optional
        System.out.println("empty, or:  " + d + "   calls=" + calls);
    }
}
```

```text
orElse:     from the cache   calls=1                     (verified 2026-11-23)
orElseGet:  from the cache   calls=0
or:         Optional[from the cache]   calls=0
empty, or:  from disk   calls=1
```

The value was in the cache every time, and `orElse` still made the expensive call. `orElseGet`
and `or` didn't. If the fallback is a database call, that's the difference between one query and
none, on every single lookup.

| Method | Since | Argument | Returns |
| --- | --- | --- | --- |
| `orElse(value)` | 8 | eager: evaluated before the call | the value |
| `orElseGet(supplier)` | 8 | lazy | the value |
| `or(supplier)` | 9 | lazy, and supplies another `Optional` | an `Optional`, so you can chain |

### T4. `Optional` as a stream of zero or one, and as a return type

**The problem.** You have a stream of lookups, each returning `Optional`, and you want just the
values that were found. Java 8 made you write `filter(Optional::isPresent).map(Optional::get)`:
two steps, and the second one is the `get()` you're trying to avoid.

Java 9 gave `Optional` a **`stream()`** method: a stream of one element if a value is present,
zero if not. So `flatMap(Optional::stream)` (doc 09, T7, explains `flatMap`) drops the empties in
one step. Two smaller additions round it off: **`ifPresentOrElse`** (9) does one thing or the
other in one call, and **`orElseThrow()`** with no argument (10) throws when empty.

```java
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ZeroOrOne {
    static final Map<String, Integer> STOCK = Map.of("apple", 3, "pear", 0, "fig", 7);

    static Optional<Integer> stockOf(String fruit) {
        return Optional.ofNullable(STOCK.get(fruit));
    }

    public static void main(String[] args) {
        List<String> order = List.of("apple", "kiwi", "fig", "plum");

        // Each lookup is a stream of zero or one; flatMap joins them and the empties vanish.
        int known = order.stream().map(ZeroOrOne::stockOf).flatMap(Optional::stream)
                         .mapToInt(Integer::intValue).sum();
        System.out.println("units of the fruits we stock: " + known);

        stockOf("kiwi").ifPresentOrElse(
                n -> System.out.println("kiwi: " + n),
                () -> System.out.println("kiwi: not stocked"));

        try {
            stockOf("plum").orElseThrow();
        } catch (Exception e) {
            System.out.println("orElseThrow(): " + e);
        }
    }
}
```

```text
units of the fruits we stock: 10                         (verified 2026-11-23)
kiwi: not stocked
orElseThrow(): java.util.NoSuchElementException: No value present
```

`orElseThrow()` throws the same `NoSuchElementException: No value present` as `get()` (verified).
**A correction to the old version:** it said `get()` is "effectively deprecated". It isn't
deprecated: there's no `@Deprecated` in the JDK 26 source. What its javadoc says is "The
preferred alternative to this method is `orElseThrow()`" (verified), because the name
`orElseThrow` tells the reader what happens when it's empty, and `get` doesn't.

**Where `Optional` belongs.** Its javadoc says `Optional` "is primarily intended for use as a
method return type" (verified), like `stockOf` above. It's also a **value-based class**: compare
instances with `equals`, never `==`, and never use one as a lock. And it doesn't implement
`Serializable` (verified), which is what goes wrong when you use it as a field:

```java
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Optional;

public class OptionalField {
    static class Customer implements Serializable {
        String name = "Ada";
        Optional<String> nickname = Optional.empty();      // javac -Xlint:all warns about this line
    }

    public static void main(String[] args) throws Exception {
        try (var out = new ObjectOutputStream(new ByteArrayOutputStream())) {
            out.writeObject(new Customer());
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}
```

```text
java.io.NotSerializableException: java.util.Optional     (verified 2026-11-23)
```

This repo's build passes `-Xlint:all`, so `javac` also warns: `non-transient instance field of a
serializable class declared with a non-serializable type` (verified). **A second correction:** the
old version said the javadoc "specifically" tells you not to use it as a field. It doesn't. The
field advice is a conclusion from the "return type" sentence and from serialization, not a rule
written in the javadoc.

### T5. Two answers from one pass: `teeing`, and single-pass sources

**The problem.** A **single-pass source** can only be read once: a file being read, a socket, a
database cursor, or any `Stream` object. If you need two results from it (a count *and* a total),
you can't go round twice. Java 8 made you write a custom collector, or collect into a list first
and lose the point of streaming.

Java 12's **`Collectors.teeing(c1, c2, merger)`** sends every element to two collectors and
merges their two results at the end, like a T-junction in a pipe.

```java
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OnePass {
    public static void main(String[] args) {
        Stream<String> words = Stream.of("pear", "fig", "banana", "kiwi");
        words.count();
        try {
            words.count();                                   // a Stream is single-pass
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage());
        }

        // Two answers from one pass: the number of words and their total length, merged at the end.
        String summary = Stream.of("pear", "fig", "banana", "kiwi")
                .collect(Collectors.teeing(
                        Collectors.counting(),
                        Collectors.summingInt(String::length),
                        (count, letters) -> count + " words, " + letters + " letters"));
        System.out.println(summary);

        // The doc's average, from one pass.
        double average = List.of(3, 4, 8).stream()
                .collect(Collectors.teeing(Collectors.summingInt(i -> i), Collectors.counting(),
                        (sum, n) -> (double) sum / n));
        System.out.println("average of [3, 4, 8] = " + average);
    }
}
```

```text
stream has already been operated upon or closed          (verified 2026-11-23)
4 words, 17 letters
average of [3, 4, 8] = 5.0
```

The first line is the `IllegalStateException` every `Stream` throws the second time it's used.
That's why `teeing` exists. And it's also when it's worth it: **on a `List` already in memory,
you can simply go round twice**, and two plain passes often read better than one clever one.
Exercise 3 asks you to make that call.

`mapMulti` (16) is the other collecting-side addition. [Doc 09, T7](09-streams-and-optional.md)
covers it, and its exercise 5 asks you to try it and report, so it isn't repeated here.

### T6. `toList()`: shorter, and not the same list

**The problem** was only length: `.collect(Collectors.toList())` is a lot of ceremony for the most
common ending a stream has. Java 16 added **`Stream.toList()`**. But it isn't a shorthand for the
old call. It returns a different kind of list:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ToList {
    public static void main(String[] args) {
        List<String> before16 = Stream.of("a", "b").collect(Collectors.toList());
        List<String> since16  = Stream.of("a", "b").toList();

        before16.add("c");                                   // works today; the javadoc doesn't promise it
        System.out.println("Collectors.toList() after add: " + before16);
        try {
            since16.add("c");
        } catch (UnsupportedOperationException e) {
            System.out.println("toList().add: UnsupportedOperationException");
        }

        System.out.println("toList() keeps nulls: " + Stream.of("a", null).toList());
        System.out.println("need to change it? copy: " + new ArrayList<>(since16));
    }
}
```

```text
Collectors.toList() after add: [a, b, c]                 (verified 2026-11-23)
toList().add: UnsupportedOperationException
toList() keeps nulls: [a, null]
need to change it? copy: [a, b]
```

| | `collect(Collectors.toList())` | `toList()` (16) |
| --- | --- | --- |
| Can you `add` to it? | yes, in practice (an `ArrayList`), but not promised | no: unmodifiable |
| Nulls | allowed | allowed (unlike `List.of`, [doc 16](16-collection-factories.md)) |

So swapping one for the other is a behaviour change if any caller modifies the result. A test
that only reads the list won't notice.

## Practice

### Exercise 1: `StreamAdditions`, TODOs 1–3: stop early, and bounded `iterate`

`functional/StreamAdditions.java`. Delete the `@Disabled` on `StreamAdditionsTest` first. The
fixture is `[1, 2, 3, 10, 4, 5]` and the limit is 5, and the three answers the tests expect (all
three verified against the JDK operations the TODOs name):

```text
takeWhile   [1, 2, 3]          stops at the 10
filter      [1, 2, 3, 4]       doesn't stop
dropWhile   [10, 4, 5]         everything from the 10 on
```

`takeWhile` and `dropWhile` together give back the whole stream, in order (verified by
concatenating them). `filter` and its negation don't, because the 4 is on both sides of the
10. That's what TODO 2's note means by "not the complement under filter semantics" (T1).

**TODO 3, the three-argument `iterate`** (T2). The two-argument form from Java 8 is infinite, so
you had to add `limit(n)` with a count you'd worked out yourself. Working out "how many powers of
two are below 100" so you can pass it to `limit` is where the bug goes. The three-argument form
takes the condition instead. Write the Java 8 form first, as the TODO asks.

**Done when:** TODOs 1–3 pass, you've committed the Java 8 `limit` version of TODO 3 before the
three-argument one, and you can say in one line why the three-argument form exists.

### Exercise 2: `Optional`, TODOs 4–5

[Doc 09](09-streams-and-optional.md) sets the rule: **no `isPresent()` followed by `get()`**.

- **TODO 4** uses `Optional.or` (T3).
- **TODO 5** uses `Optional::stream` with `flatMap` (T4). The Java 8 version is
  `filter(Optional::isPresent).map(Optional::get)`. Both work. Say which you'd rather find in a
  review.

**Done when:** the four `Optional` tests pass with no `isPresent()` and no `get()` in the code, and
you've written down which TODO 5 version you'd rather review, and why.

### Exercise 3: `teeing`, TODO 6

TODO 6 asks for the min and the max in one pass (T5). **Then answer honestly whether one pass was
worth it.** For a `List` already in memory, two passes over six elements is free and arguably
reads better. `teeing` earns its keep on a single-pass source, where you can't go round again.
Before you answer, look at what `IntStream.summaryStatistics()` returns for the fixture: it was
already in Java 8. If you can't name a single-pass source in your own work where you needed two
results, that's a legitimate "what got worse" line.

**Done when:** `teeing_computes_min_and_max_in_one_pass` passes, and you've written your answer on
whether one pass was worth it, with `summaryStatistics()` considered.

### Exercise 4: the one old-code site, `getLoop`

`The100DoorsCyberDojoTest.java:78-87` is a 2014 helper that has a bounded `iterate` in it,
without saying so:

```java
doorNumber=number;
while(doorNumber <= size){
    retVal = retVal+(doorNumber-1)+",";
    doorNumber=doorNumber+number;
};
```

Seed `number`, condition `doorNumber <= size`, step `+ number`: the three arguments of
`Stream.iterate` (T2), one per line. **It already has a test.** `loopDoorsInOrderOfN` (lines
63-76) asserts its output for three inputs, including the trailing comma (`"2,5,8,"`), so it's
safe to change.

1. **Rewrite it as one stream expression** that produces the same strings. It can be done
   (verified: all three of the test's expected strings, from one expression). The trailing comma
   is the part that decides which collector you can use.
2. **Then decide whether it was better.** The loop is six lines and anyone can read it. The
   stream is one line, and needs the reader to know the three-argument `iterate`. (While you're
   in there: the `;` after the `while` block at line 85 is an empty statement, and the
   indentation at lines 81-85 is one tab deeper than it should be. Neither does anything.)

**Done when:** `getLoop` is one stream expression, `loopDoorsInOrderOfN` is still green, and you've
written down whether the stream was better.

### Exercise 5: fill in "What got worse"

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: `takeWhile` on a parallel stream: what does it have to do, and when is that worse than
not parallelising at all? `Optional.or` takes a `Supplier`, and `orElse` takes a value: at a call
site that does I/O, would you have noticed which one you'd written? The three-argument `iterate` is
a `for` loop, so at what point does the loop win? `getLoop` as a stream: shorter, but is it
clearer, and to whom?

### Exercise 6: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. Same list, same predicate: when do `takeWhile` and `filter` give different answers? (T1)
2. `takeWhile(x -> x < 5)` on a `HashSet` returned `[]`. Is that a bug? What does the javadoc
   allow? (T1)
3. Why can `takeWhile` be slower on a parallel stream than on a sequential one? (T1)
4. What does the three-argument `iterate` replace, and what does it *not* protect you from? (T2)
5. `cached.orElse(load())` with a value in the cache: how many times does `load()` run? And with
   `orElseGet`? (T3)
6. What's the difference between `orElseGet` and `or`? (T3)
7. What does `flatMap(Optional::stream)` do to the empties, and what's the Java 8 way to write the
   same thing? (T4)
8. Is `Optional.get()` deprecated? What does its javadoc say instead? (T4)
9. Name two reasons not to make `Optional` a field of a `Serializable` class. (T4)
10. When is `teeing` worth it, and when are two passes just as good? (T5)
11. You replace `.collect(Collectors.toList())` with `.toList()`. What can break, and why won't a
    read-only test notice? (T6)

**Done when:** you can answer all eleven without looking.
