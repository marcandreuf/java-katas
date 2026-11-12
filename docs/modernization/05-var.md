# 05. `var`, and the diamond

**Java 7** (diamond) · **Java 9** (diamond on anonymous classes) · **Java 10** (`var`) ·
**Java 11** (`var` in lambda parameters) · concept stage 05 of [the concept order](00-start-here.md)
· no build change

> **No safety net:** `ArgsRandom`, `FindDups`, `ProducerConsumerExample`, `RandomFileLines` have no
> tests. Write them first, in a separate commit, asserting what the code does today — bugs
> included. [`../testing-first.md`](../testing-first.md) says how, and why the order matters. Part
> one is the one exception in the track, and it says how you prove that instead of assuming it.

## What this stage is actually about

Five words, defined once:

- a **type argument** is the type in angle brackets: the `String` in `List<String>`.
- the **diamond** is `<>` with nothing in it, on the right of `new`: `new ArrayList<>()`. The
  compiler works out the type argument from where the object is going.
- **type inference** is the compiler working out a type you didn't write.
- **`var`** is a local variable declaration with the type left out: `var lines = new
  ArrayList<String>();`. The compiler works out the type from the initializer.
- the **static type** of a variable is the type the compiler uses to check it: what you could call
  on it, and what you could assign to it. It is fixed when the variable is declared.

That gives one rule, and everything below follows from it: **both features let the compiler fill
in a type it can already work out, and what you get is exactly the type it works out, which is
not always the type you would have written.** The diamond takes its type from the *left* (where
the value is going). `var` takes its type from the *right* (what the value is). Use both in one
declaration and neither side says anything, which is part two, case 3.

## The worked example

`concurrency/SumWordsLength.java:28`, written in 2014:

    Set<Future<Integer>> setThreadResults = new HashSet<Future<Integer>>();

`Future<Integer>` is written twice. Here are the three ways to say it once:

    // 1. diamond: the type argument comes from the left
    Set<Future<Integer>> setThreadResults = new HashSet<>();

    // 2. var: the whole type comes from the right
    var setThreadResults = new HashSet<Future<Integer>>();

    // 3. both: nothing is said on either side
    var setThreadResults = new HashSet<>();          // HashSet<Object>

**They aren't the same program.** Form 1 is exactly what the 2014 line says. **Form 2 changes
the static type**: `setThreadResults` is now a `HashSet`, not a `Set`. **Form 3 has lost the
`Future<Integer>` entirely**, so the `result.get()` loop at line 38 no longer knows what it is
getting.

You can see the difference in the bytecode (verified with `javap -c`):

    List<String> l = new ArrayList<>();   l.add("x");    // invokeinterface java/util/List.add
    var          l = new ArrayList<String>(); l.add("x"); // invokevirtual   java/util/ArrayList.add

The diamond compiles to the same bytes as the type argument written out. `var` compiles to
different bytes as soon as the inferred type is narrower than the one you would have declared.
**That difference is why the two halves of this doc have different safety rules.**

## Part one: the diamond, which the compiler can prove

The diamond arrived in Java 7, and this code is from 2014. So these sites aren't a modern-Java
question at all. **They are a "how long does an idiom survive after it is obsolete" question,**
which is the more useful one when you are reading somebody else's codebase. Here are the six that
are left in `src/main`, and one in a test:

| File | What is there |
| --- | --- |
| `collections/ArgsRandom.java:12` | `new ArrayList<String>(Arrays.asList(args))` |
| `collections/FindDups.java:13` | `new Comparator<String>() { ... }`, an anonymous class |
| `collections/FindDups.java:20` | `new TreeSet<String>(comparator)` |
| `collections/RandomFileLines.java:54` | `new ArrayList<String>((int)listSize)` |
| `concurrency/SumWordsLength.java:28` | `new HashSet<Future<Integer>>()`, the example above |
| `concurrency/guardedLock/ProducerConsumerExample.java:13` | `new SynchronousQueue<String>()` |
| `src/test/.../The100DoorsCyberDojoTest.java:46` | `new ArrayList<Door>(100)` |

(`DuplicateNumber.java:38` and the javadoc at `Immutable.java:8` also match a grep, but one is
commented out and the other is a quote. Leave them.)

`FindDups.java:13` is the one to look at twice. A diamond on an **anonymous class** wasn't allowed
until Java 9. Under `--release 8` it fails with
`'<>' with anonymous inner classes is not supported in -source 8` (verified). So that site was
stuck until 2017, three years after the code was written.
[Doc 12](12-lambdas-and-method-references.md) deletes the anonymous class anyway; diamond it here
regardless, since it is one commit.

### Why this part doesn't need a test first

Four of these files have no test, and the rule in [doc 00](00-start-here.md) is no test, no
rewrite. **This is the one change in the track where the compiler is the safety net, but only
because you can check it.** The diamond leaves no trace in the class file, so if the class files
are identical before and after, the program is identical. Prove it; don't assume it:

    javac --release 26 -g -parameters -nowarn -d /tmp/before $(find src/main/java -name '*.java')
    # ... apply all six diamonds in src/main ...
    javac --release 26 -g -parameters -nowarn -d /tmp/after  $(find src/main/java -name '*.java')
    diff -r /tmp/before /tmp/after && echo identical

All 97 class files in `src/main` came out byte-identical (verified), including the anonymous
`FindDups$1`. Put the command and its result in the commit message. **That's what makes the
exception legitimate**: you replaced the test with a proof, and you didn't just skip the test. It
doesn't extend to anything else in this doc. Part two changes bytecode, so the rule applies again
in full.

## Part two: `var`, everywhere it compiles, then take it back

**The code is not the deliverable here. Your opinion is.** Apply `var` everywhere it compiles,
take it back out wherever the code got worse, and write the cases down at the end. "Where is this
the wrong choice" is the harder half of knowing a feature, and `var` is the cheapest topic in this
repo to have a real opinion about.

Five cases to go looking for, each verified:

### 1. The literal decides the type: `SimpleThreads.java:26`

    long defaultDelayInMiliseconds = 1000 * 60 * 60;

Change `long` to `var` and the variable becomes an `int`, because `1000 * 60 * 60` is `int`
arithmetic, and `var` takes the type of the right-hand side. Line 30 then stops compiling:
`possible lossy conversion from long to int`, because `Long.parseLong(...) * 1000` is a `long`.
The compiler caught it, and the fix is `1000L * 60 * 60`.

**And look at what the `long` was never doing.** Make the delay 30 days:

    long a = 1000 * 60 * 60 * 24 * 30;     // -1702967296
    var  b = 1000 * 60 * 60 * 24 * 30;     // -1702967296
    long c = 1000L * 60 * 60 * 24 * 30;    //  2592000000

The arithmetic overflows as an `int` **before** it reaches the `long`. The declared type never
protected you; it only looked as if it did. `var` didn't make this worse. It took away a type
annotation that was misleading. Decide whether that counts for or against it.

### 2. Boxing you didn't ask for: `RandomFileLines.java:18`

    int numLinesToPrint = Integer.valueOf(5);

With `var`, `numLinesToPrint` is an **`Integer`**, not an `int`, and it will happily accept `null`
later. `var` faithfully keeps a mistake the explicit `int` was quietly correcting. (The fix for
this line has nothing to do with `var`: it's `int numLinesToPrint = 5;`.)

### 3. `var` with a diamond: nothing on either side

    var lines = new ArrayList<>();
    lines.add(1);
    lines.add("two");                  // compiles: it's an ArrayList<Object>
    String s = lines.get(1);           // error: Object cannot be converted to String

The diamond looks left for its type argument, `var` looks right for its type, and both find
nothing, so you get `Object`. **Never combine them.** If you want `var`, write the type argument
out on the right.

### 4. Where `var` isn't allowed at all

| Written | Result |
| --- | --- |
| `var count = 1;` as a field | `'var' is not allowed here` |
| `var a;` | `cannot infer type for local variable a` |
| `var b = null;` | `cannot infer type for local variable b` |
| `var c = {1, 2};` | `cannot infer type for local variable c` |
| `var d = () -> 1;` and `var e = String::length;` | `cannot infer type for local variable` |
| `var e = 1, f = 2;` | `'var' is not allowed in a compound declaration` |

They all come down to the same rule. `var` needs an initializer whose type the compiler can work
out *without* knowing the target. A lambda has no type until it has a target, and `null` and
`{1, 2}` don't have a usable one either. Parameters and return types are excluded on purpose:
they are a method's contract with its callers, and that should be written down.

### 5. Where it reads better

- **Loops**: `for (var str : lstArgs)` in `ArgsRandom.java:14`, or `for (var i = 0; ...)`.
- **When the right-hand side already names the type**: `var fin = new File(fileName);` at
  `RandomFileLines.java:51` says `File` once instead of twice.
- **Lambda parameters** (Java 11): `(var a, var b) -> a.length() + b.length()`. It is all or
  none: `(var a, b) ->` is `invalid lambda parameter declaration`. It exists so you can annotate
  a parameter (`(@NonNull var a, ...)`) without having to write out its type. With no annotation,
  plain `(a, b)` says the same thing in fewer words.

**The one to argue with yourself about** is `RandomFileLines.java:21`:
`var lstLines = readFileLines(fileName);`. The type is only visible if you go and read
`readFileLines`. In an IDE that shows the inferred type, that costs nothing. In a code review in a
browser, it costs a click per variable.

## Where it made the code worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: 

- What does `var` do to a review in a browser with no type hints?
  The Code review can be more difficult to read or catch potential bugs.

- Where did it change the static type from an interface to a class, and does anything in that method now depend on the
class? [Doc 27](27-module-imports-and-compact-sources.md) asks you to make the same kind of call about module imports. 
  I like to use types on both sides when using collections to make the code
  more explicit. 

- Write down the rule you used here, so you can check whether you apply it consistently there.
  Only use var when the type is obvious in the same line of code. 
