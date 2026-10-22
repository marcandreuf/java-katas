# Write the test first

**Shared between both tracks. Read this before the first rewrite, not after it.**

Twenty of the thirty-two classes that were in this repo before the track started **have no test
and never have had one.** Seventeen modernization documents and three DSA documents tell you to
change one of them. That is a defect in the documents, and this file is the fix.

## The rule

> **Do not change a file that has no test. Add the test first, in its own commit, asserting what
> the code does today — then refactor underneath it.**

A refactor is a change that provably does not alter behaviour. If nothing checks the behaviour,
you are not refactoring. You are rewriting and hoping, and `git diff` cannot tell the difference.

This matters more here than in most repos, because **this repo is known to contain bugs you have
not found yet.** Two are documented (`BinarySearch` cannot find five of twelve elements;
`FairWorkLoad` returns an impossible 900). Those were found by writing tests. The rest of the
untested code has had exactly as much scrutiny as those two had in 2014, which is none.

## The technique has a name

Michael Feathers, *Working Effectively with Legacy Code* (2004), defines legacy code as **code
without tests** — nothing to do with its age — and the test you write to get it under control is a
**characterization test**. The point that trips everyone up:

> **A characterization test asserts what the code actually does, including the bugs.**

You are not writing the test you wish were true. You are pinning down current behaviour so that a
later change is *visible*. If `FairWorkLoad` returns 900 and 900 is wrong, your first test asserts
**900**. That feels absurd for about a minute, and then it pays for itself, because now the refactor
and the bug fix are two different commits:

| Commit | Source | Test | Meaning |
| --- | --- | --- | --- |
| 1 | unchanged | **new**, asserts 900 | "this is what it does" |
| 2 | **refactored** | unchanged, still green | "I changed the shape, not the behaviour" |
| 3 | **fixed** | **changed** to assert 950 | "I changed the behaviour, deliberately" |

Commit 2 is the only one where a green test proves something. Commit 3 is the only one where the
test *should* change. **If a test assertion changes in the same commit as a refactor, you have lost
the ability to say which one broke it** — and that is exactly the commit people bisect to at 2am.

This maps onto the rule the repo already has. "One concept per commit" and "write the test first"
are the same instruction viewed from two sides.

## What to do when there is nothing to test

Most of the untested code here cannot be tested as written, and **noticing that is the finding.**
Untestability is a design report: it says the code mixes deciding with doing.

`collections/FindDups.java` is the clearest case. The whole class is a `main` that prints:

    SortedSet<String> s = new TreeSet<String>(comparator);
    for (String a : args)
        if (!s.add(a))
            System.out.println("Duplicate detected: " + a);
    System.out.println(s.size() + " distinct words: " + s);

There is no return value. To assert anything you would have to capture `System.out`, which couples
your test to the wording of a message rather than to the behaviour. So **the first commit is not a
test. It is an extraction:**

    static SortedSet<String> distinct(String[] words) { ... }   // decides
    static List<String> duplicatesIn(String[] words) { ... }    // decides
    public static void main(String[] args) { ... }              // prints

Now `main` is three lines of printing over two methods that return values, and the test writes
itself. **Then** you do the lambda work doc 12 asks for, against a test that was green before you
started. Extract, characterize, refactor, fix — four commits, in that order.

The same move applies to `DecimalToBinary.printBinaryFormat(int)` (returns `void`, prints),
`ArgsRandom` and `RandomFileLines` (both `main`-only).

Two shortcuts worth knowing:

- **`algorithm/FindMiddleIndex.findMiddleIndex(int[])` already returns an `int`.** It is testable
  as it stands, so it is the cheapest place to practise this on. Start there.
- **`concurrency/Order` is a plain data class** — constructor and fields, nothing to extract. Its
  test is four assertions and it takes two minutes. ([Doc 01](modernization/01-records.md) then
  turns it into a record, and the test you just wrote shows exactly what changed: the record
  is *not* equivalent, because equality becomes by value, and the one assertion that flips is the
  evidence.)

## Concurrency is the exception, and it is most of the list

**Eleven of the twenty untested classes are concurrency code**, and a characterization test does
not work on a race: the behaviour you would be pinning down is "usually this, sometimes that". A
green test on `BadThreads` proves the race did not happen *this time*.

So the instruction changes. For `BadThreads`, `MyDeadlock`, `Safelock`, `SimpleThreads`,
`L3_Synchronization_Udemy`, `OrdersProcessingSystem`, `ForkBlur` and the four `guardedLock`
classes:

1. **Test the part that is not concurrent.** Most of these have a pure computation buried in a
   `Runnable`. Extract it, test it, and leave the threading alone. `ForkBlur`'s blur arithmetic is
   a pure function of a pixel array.
2. **For the rest, write down the invariant instead of an assertion** — "every produced item is
   consumed exactly once", "the counter equals the number of increments". Then assert *that*, in a
   loop, and accept that a green run is weak evidence.
3. **Say so in the commit message.** "No test: this is a visibility demo and the failure is
   non-deterministic" is a legitimate commit. A silent `@Disabled` is not — that is how
   `FairWorkloadTest` stayed switched off from 2014 to 2026.

[Doc 08](modernization/08-memory-model.md) is the one place where you *want* the test to be
non-deterministic, because the demo is the point. Read it before you try to pin `BadThreads` down.

## The coverage map

**Has a test — refactor freely, the net is already there:**

`BinarySearch`, `CircularList`, `Door`, `DuplicateNumber`, `FairWorkLoad`, `MyArrayList`,
`NumberReverse`, `PascalsTriangle`, `PascalsTriangleRecursive`, `StringRecursiveReversal`,
`SumSearch`, `SumWordsLength`

**No test. Write one first:**

| Class | Named by | Testable as written? |
| --- | --- | --- |
| `algorithm/FindMiddleIndex` | mod 09 | **yes** — returns an `int`. Start here. |
| `concurrency/Order` | mod 01, 03, 12 | **yes** — plain data class |
| `algorithm/DecimalToBinary` | mod 03 · DSA 12 | no — `void` + print, extract first |
| `collections/FindDups` | mod 05, 09, 12, 14 | no — `main` + print, extract first |
| `collections/ArgsRandom` | mod 16 | no — `main` + print |
| `collections/RandomFileLines` | mod 05, 09, 13, 18, 19, 30 | no — `main` + print, reads a file |
| `concurrency/BadThreads` | mod 08, 12 | concurrent — see above |
| `concurrency/MyDeadlock` | mod 08, 11 | concurrent — and does **not** deadlock as written; see mod 08 |
| `concurrency/Safelock` | mod 08, 19 | concurrent |
| `concurrency/SimpleThreads` | mod 04, 06, 13, 21, 34 | concurrent — the timeout path needs a clock seam and a poll seam; see mod 13 |
| `algorithm/L3_Synchronization_Udemy` | mod 12 | concurrent |
| `concurrency/OrdersProcessingSystem` | mod 06, 12 | concurrent — `run()` is empty |
| `concurrency/forkjoin/ForkBlur` | mod 07, 11, 13, 31, 34 | concurrent — blur maths is pure, extract it |
| `concurrency/guardedLock/Drop` | mod 07 | concurrent — but the invariant is clean |
| `concurrency/guardedLock/Producer` | mod 07, 12, 19 | concurrent |
| `concurrency/guardedLock/Consumer` | mod 07, 12, 19 | concurrent |
| `concurrency/guardedLock/ProducerConsumerExample` | — | concurrent |
| `generics/PageObject` | mod 01 | no — needs a Selenium driver |
| `generics/FirstPageObject` | — | no — needs a Selenium driver |
| `generics/PageObjectFactory` | — | no — needs a Selenium driver |

The last four are the honest exception: `generics/` wraps a web driver this repo does not depend
on, so there is nothing to test without adding one. [Doc 01](modernization/01-records.md) asks you
to look at `PageObject`, not to rewrite it.

## When the compiler can prove it instead

One kind of change needs no test, because you can prove it changed nothing: a change that leaves
every class file **byte-identical**. Adding the diamond (`new ArrayList<>()`) is the main one in
the track. Renaming an unused variable to `_` ([doc 21](modernization/21-unnamed-and-primitive-patterns.md))
is the other: `javap -c` is identical, and only the debug `LocalVariableTable` differs, so compile
without `-g` for the `diff`. [Doc 05](modernization/05-var.md) gives the before-and-after `javac` and `diff -r`
commands, and the result goes in the commit message. That proof is what replaces the test. If you
haven't run the `diff`, you don't have the exception. `var` does *not* qualify: it can change the
static type, and with it the bytecode.

## The one thing not to do

Do not write the test *after* the refactor and call it covered. A test written against code you
just changed asserts your new behaviour, including whatever you broke. It will be green, it will
stay green, and it proves nothing at all.
