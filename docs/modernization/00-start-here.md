# 00. Start here

## Step zero, done 2026-09-25

The repo did not compile. `ProductsOfArrayElements.java` imported `org.apache.commons.lang.ArrayUtils`,
commons-lang **2.x**, while the build provided commons-lang**3**, whose package is
`org.apache.commons.lang3`. Fixed by repointing the import.

Two things fell out of it that were worth more than the fix.

**Three test classes had never run.** Surefire only picks up `Test*`, `*Test`, `*Tests` and
`*TestCase`. `ProductsOfArrayElements` and `SumWordsLengthCallableResultsSample` matched none of
them, so six tests sat in the repo for years without executing. Both were renamed and all six pass.
`RotateMatrix90DegreeInPlace` turned out to have no `@Test` methods at all: it is a `main` program
in the test tree, and still needs a home.

**The dependency question is still open**, and it is the smallest version of what this whole track
asks: *what is this dependency still buying me?* commons-lang3 now earns its place with exactly one
call, `ArrayUtils.removeElement`, in `otherValues()`. Replacing it with `java.util.Arrays` and
streams is a real exercise with a trap in it, described in
[`09-streams-and-optional.md`](09-streams-and-optional.md): `removeElement` drops only the **first**
occurrence, while `filter(v -> v != elem)` drops **every** one. The existing test data is all
distinct values, so it cannot tell the two apart and would stay green while the behaviour changed.
Write the duplicates test first.

## Starting an exercise

The build is green. The outstanding work is the disabled tests:

    grep -rn "@Disabled" src/test

Each carries a reason naming the document that owns it. **Deleting that one annotation is how you
start an exercise**, and it turns a silent gap into a failing test with a specific message. Commit
the deletion together with the fix.

Never add a bare `@Disabled`. `FairWorkloadTest.testMaxNumPerSixWorkers` was switched off in 2014
with a note saying the cause was never found, and it stayed off for twelve years. That is the
failure mode this convention exists to prevent.

## How the track works

One document per concept in this folder, numbered. Each one names the files in this repo that
carry the old idiom, says what to change and, more importantly, what to look for once you have
changed it.

**Two rules.**

1. **One concept per commit.** The diff is the deliverable. A commit that modernises records and
   streams and concurrency at once is unreadable six months later, and unreadable diffs are the
   reason nobody can tell what they learned.
2. **Every document ends with a "what got worse" section, and you fill it in.** Three honest lines
   about where a feature hurt is what produces an explanation that survives a follow-up question.
   "I tried it and it was not worth it here" is a better thing to be able to say than a list of
   features you can name.

## The order

Records, sealed types and pattern matching first: they are the smallest changes and they touch
data classes that nothing else depends on. Streams next. Concurrency last, because that is where
the real bugs are and you want the rest quiet by then.

| # | Concept | Where it lands |
| --- | --- | --- |
| 01 | Records | `Order`, `Door`, the maze `Cell`, `generics/PageObject` |
| 02 | Sealed types | new code, `sealedtypes/ToolCallResult` |
| 03 | Pattern matching | `Door`, `DecimalToBinary` |
| 04 | Switch expressions, text blocks | `Door.toString`, `The100DoorsCyberDojoTest` |
| 05 | `var` and the diamond | eight pre-diamond sites across the repo |
| 06 | Virtual threads | `SumWordsLength`, `SimpleThreads`, `OrdersProcessingSystem` |
| 07 | Classic concurrency | `guardedLock/*`, `forkjoin/ForkBlur` |
| 08 | The memory model | `BadThreads`, `MyDeadlock`, `Safelock` |
| 09 | Streams and Optional | `FindDups`, `RandomFileLines`, `FairWorkLoad` |
| 10 | Sequenced collections, HttpClient | `CircularList`, `MyArrayList`, new `httpclient/LocalHttp` |
| 11 | JVM diagnostics | `forkjoin/ForkBlur` as the workload |

## A note on whose code this is

Eight files in `concurrency/` and `collections/` are Oracle Java Tutorials code under a BSD-3
header, listed in `NOTICE`. They are worth modernising, but when you are asked what you wrote,
they are not it. Your own code is the `algorithm` package, `CircularList`, `MyArrayList`,
`FairWorkLoad`, `SumWordsLength` and `OrdersProcessingSystem`.
