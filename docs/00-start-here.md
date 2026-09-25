# 00. Start here

## Step zero: make it build

The repo does not compile as it stands. `src/test/java/org/mandfer/practices/ProductsOfArrayElements.java`
imports `org.apache.commons.lang.ArrayUtils`, which is commons-lang **2.x**. The only such
dependency declared is commons-lang**3**, whose package is `org.apache.commons.lang3`.

That is the first exercise, and it is a one-line change. Decide which of the two you want:

- point the import at `org.apache.commons.lang3.ArrayUtils`, or
- drop the dependency and do the array work with `java.util.Arrays` and streams.

The second is the better answer, and it is the smallest possible version of the question this
whole track asks: **what is this dependency still buying me?**

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
