# 17. Stream and `Optional` additions after 8

**Java 9** (`takeWhile`, `dropWhile`, three-argument `iterate`, `Optional.or`, `Optional.stream`,
`ifPresentOrElse`) · **Java 10** (`orElseThrow()`) · **Java 12** (`Collectors.teeing`) ·
**Java 16** (`Stream.toList`, `mapMulti`) · stage 6 of [the release-ordered track](INDEX.md) · no
build change

[Doc 09](09-streams-and-optional.md) is the Java 8 core. This doc is what four later releases
added on top, and it's mostly new code: **every file in `src/main` that uses a stream or an
`Optional` was added in 2026** (verified with `git log`). The 2014 code has none. Part four is
the one old-code site, and it already has a test.

**A correction to the old version:** its "No safety net" banner named `FindDups` and
`RandomFileLines`, which this doc never touches. Doc 09 owns them, and its banner covers them.

## What this stage is actually about

Five words, defined once:

- **encounter order** is the order a stream hands out its elements. A `List` has one; a `HashSet`
  doesn't. [Doc 10](10-sequenced-collections-and-httpclient.md) defines it for collections.
- a **short-circuiting** operation can finish without reading the whole stream: `takeWhile`,
  `findFirst`, `anyMatch`, `limit`.
- a **lazy argument** is a `Supplier` that is only called if its value is needed. An eager one is
  computed before the method is even called.
- a **single-pass source** can only be read once: a file being read, a socket, a database cursor,
  or any `Stream` object, which throws `IllegalStateException: stream has already been operated
  upon or closed` the second time (verified).
- a **value-based class** (`Optional` is one) is a class whose instances should be compared with
  `equals`, never `==`, and never used as a lock.

That gives one rule, and everything below follows from it: **each addition names a loop shape
that Java 8 streams couldn't express: stop at the first failure, loop while a condition holds,
fall back to another source.** Use one when it is the shape you mean. If the data's order doesn't
matter, or the loop doesn't stop early, the older operation is the honest one.

## The worked example

Five log lines and two questions that sound the same (verified):

    List<String> log = List.of("INFO start", "INFO load", "ERROR disk", "INFO retry", "INFO done");

    log.stream().takeWhile(l -> !l.startsWith("ERROR")).toList()
        // [INFO start, INFO load]                          everything before the first error
    log.stream().filter(l -> !l.startsWith("ERROR")).toList()
        // [INFO start, INFO load, INFO retry, INFO done]   every line that isn't an error

"What happened before it broke" is `takeWhile`. "Everything except the errors" is `filter`.
Writing `filter` for the first question passes every test whose fixture has the error last.

Now run `takeWhile` on a stream with **no encounter order**: ten integers in a `HashSet`, and
`takeWhile(x -> x < 5)`. The set iterated as `[64, 1, 33, 2, 3, ...]`, so the result was `[]`
(verified): the first element failed, and the stream stopped. Nothing threw. The javadoc allows
exactly this: on an unordered stream, `takeWhile` "is free to take any subset of matching elements
(which includes the empty set)" (verified in the JDK 26 source). **The operation is a statement about order, and a `HashSet` has none to give.**

## Part one: `functional/StreamAdditions.java`, TODOs 1–3

Delete the `@Disabled` on `StreamAdditionsTest` first. The fixture is `[1, 2, 3, 10, 4, 5]` and
the limit is 5, and the three answers the tests expect (all three verified against the JDK
operations the TODOs name):

    takeWhile   [1, 2, 3]          stops at the 10
    filter      [1, 2, 3, 4]       doesn't stop
    dropWhile   [10, 4, 5]         everything from the 10 on

`takeWhile` and `dropWhile` together give back the whole stream, in order (verified by
concatenating them). `filter` and its negation don't, because the 4 is on both sides of the
10. That's what TODO 2's note means by "not the complement under filter semantics".

On a parallel stream, the `takeWhile` javadoc says it "can be quite expensive on ordered parallel
pipelines" (verified in the JDK 26 source), because it must return "the longest prefix of
elements in the encounter order", not just any matching prefix, and the threads working on later
chunks can't know where that prefix ends. That's one of the few places you can predict from the API
alone that `.parallel()` may make things slower.

**TODO 3, the three-argument `iterate`.** The two-argument form from Java 8 is infinite, so you
had to add `limit(n)` with a count you'd worked out yourself. Working out "how many powers of two
are below 100" so you can pass it to `limit` is where the bug goes. The three-argument form takes
the condition instead. Write the Java 8 form first, as the TODO asks.

And the trap the three-argument form doesn't fix: `Stream.iterate(1, i -> i < 2_000_000_000,
i -> i * 2)` **never ends** (verified, first 35 elements): after 1073741824, `i * 2` overflows to
-2147483648, which is less than two billion, and then to 0, which doubles to 0 forever. A `for`
loop with the same condition has the same bug. Stating the condition doesn't make it true.
[DSA stage 09](../dsa/09-integer-overflow.md) owns overflow.

## Part two: `Optional`, TODOs 4–5

[Doc 09](09-streams-and-optional.md) sets the rule: **no `isPresent()` followed by `get()`**.
These are the Java 9 methods that make the rule easy to keep:

- `first.or(supplier)` returns `first` if it has a value, otherwise whatever the `Supplier`
  returns. **The argument is lazy.** With `first` present, the supplier was called **0** times
  (verified). Compare `orElse(x)`, whose argument is evaluated before the call even happens
  (verified: a side-effecting argument ran once, even though the value was present), and
  `orElseGet(supplier)`, which is lazy like `or`. If the fallback is a database call, that's the
  difference between one query and none.
- `optional.stream()` gives a stream of zero or one element, so
  `flatMap(Optional::stream)` drops the empties. The Java 8 version is
  `filter(Optional::isPresent).map(Optional::get)`. Both work. Say which you'd rather find in a
  review.

Two smaller ones. `ifPresentOrElse` (9) does one thing or the other in one call. `orElseThrow()`
with no argument (10) throws the same `NoSuchElementException: No value present` as `get()`
(verified). **A correction to the old version:** it said `get()` is "effectively deprecated". It
isn't deprecated: there's no `@Deprecated` in the JDK 26 source. What its javadoc says is
"The preferred alternative to this method is `orElseThrow()`" (verified), because the name
`orElseThrow` tells the reader what happens when it's empty, and `get` doesn't.

**`Optional` as a field.** `Optional` doesn't implement `Serializable` (verified), so a
`Serializable` class with an `Optional` field throws `NotSerializableException:
java.util.Optional` when written (verified). This repo's build passes `-Xlint:all`, so `javac`
also warns: `non-transient instance field of a serializable class declared with a non-serializable
type` (verified). **A second correction:** the old version said the javadoc "specifically" tells
you not to use it as a field. What it actually says is that `Optional` "is primarily intended for
use as a method return type" (verified). The field advice is a conclusion from that sentence and
from serialization, not a rule written in the javadoc.

## Part three: `teeing`, TODO 6

`Collectors.teeing` (12) sends every element to two collectors and merges the two results at the
end. A different example from the TODO, an average from one pass (verified, `[3, 4, 8]` gives
`5.0`):

    collect(teeing(summingInt(i -> i), counting(), (sum, n) -> (double) sum / n))

TODO 6 asks for the min and the max in one pass. **Then answer honestly whether one pass was
worth it.** For a `List` already in memory, two passes over six elements is free and arguably
reads better. `teeing` earns its keep on a single-pass source, where you can't go round again.
Before you answer, look at what `IntStream.summaryStatistics()` returns for the fixture: it was
already in Java 8. If you can't name a single-pass source in your own work where you needed two
results, that's a legitimate "what got worse" line.

`mapMulti` (16) is the other addition. [Doc 09](09-streams-and-optional.md) asks you to try it and
report, so it isn't repeated here.

## Part four: the one old-code site, `getLoop`

`The100DoorsCyberDojoTest.java:78-87` is a 2014 helper that has a bounded `iterate` in it,
without saying so:

    doorNumber=number;
    while(doorNumber <= size){
        retVal = retVal+(doorNumber-1)+",";
        doorNumber=doorNumber+number;
    };

Seed `number`, condition `doorNumber <= size`, step `+ number`: the three arguments of
`Stream.iterate`, one per line. **It already has a test.** `loopDoorsInOrderOfN` (lines 63-76)
asserts its output for three inputs, including the trailing comma (`"2,5,8,"`), so it's safe to
change.

1. **Rewrite it as one stream expression** that produces the same strings. It can be done
   (verified: all three of the test's expected strings, from one expression). The trailing comma is the
   part that decides which collector you can use.
2. **Then decide whether it was better.** The loop is six lines and anyone can read it. The
   stream is one line, and needs the reader to know the three-argument `iterate`. (While you're
   in there: the `;` after the `while` block at line 85 is an empty statement, and the
   indentation at lines 81-85 is one tab deeper than it should be. Neither does anything.)

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: `takeWhile` on a parallel stream: what does it have to do, and when is that worse than
not parallelising at all? `Optional.or` takes a `Supplier`, and `orElse` takes a value: at a call
site that does I/O, would you have noticed which one you'd written? The three-argument `iterate` is
a `for` loop, so at what point does the loop win? `getLoop` as a stream: shorter, but is it
clearer, and to whom?
