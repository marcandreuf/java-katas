# 33. The preview shelf, and what withdrawal looks like

**Java 21 → 26** · stage 22 of [the release-ordered track](INDEX.md) · **written answers, plus
scratch files outside the repo**

**No TODOs and no tests.** Nothing in the repo uses a preview feature (exercise 2 shows that), so
there's nothing to characterize. The experiments below are single-file programs you compile in a
scratch directory, and the deliverable is the written answers.

## What you do in this doc

Read the **Theory** sections first. They change no code. **But answer question 1 of exercise 3
before you read T6**: it asks for a prediction, and T6's table would spoil it. Then do the
**Practice** exercises, in order.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. The class-file lock](#exercise-1-reproduce-the-class-file-lock) | Reproduce T2's three runs in a scratch directory. | T1, T2 | None: scratch files, outside the repo. |
| [2. The `preview` profile](#exercise-2-mvn--ppreview-verify-and-what-it-doesnt-do) | Check what the repo's `preview` Maven profile does and doesn't do. | T2 | None: you run the existing build. |
| [3. Write the answers](#exercise-3-write-the-answers-here) | Five written answers. | all | None. |
| [4. What got worse](#exercise-4-fill-in-what-got-worse) | Write down what previews cost. | all | None. |
| [5. Review questions](#exercise-5-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the six sections in order: each one builds on the one before. Here is the whole story in one
paragraph, so you know where it's going.

> Java almost never takes a feature back, so it needs a way to let people try a design before it
> becomes permanent: that's a preview, and the question this doc asks is how to tell which ones
> will last (T1). The JVM enforces the "not permanent yet" part hard: a class that uses a preview
> runs on exactly one JDK version, and only with a flag (T2). Sometimes the trial ends in
> withdrawal, and String templates show what that looks like (T3). The way to see it coming is to
> watch what *changes* between previews, not to count them: Lazy Constants and structured
> concurrency are the two contrasting cases (T4). APIs have an even earlier stage, the incubator,
> where the Vector API has sat for years (T5). And T6 lists what's on the shelf in JDK 26.

Every program below is a complete single file. Outputs marked *verified* were run on JDK 26
(Temurin 26.0.2), or on JDK 25 (Temurin 25.0.4) where the label says so.

### T1. Why previews exist, and the one rule

**The problem.** Java's compatibility promise means a feature, once final, is effectively
permanent: code written against it has to keep compiling for decades. So a design mistake in a
language feature can't be fixed afterwards. The JDK needs real users to try a design *before* it
is permanent, without those users building things that will break.

**The answer** is the preview: a feature that ships complete but switched off, which you turn on
per compile and per run. [Doc 21, T5](21-unnamed-and-primitive-patterns.md#t5-preview-features-finished-enough-to-try-not-to-keep)
explains the mechanics with primitive patterns as the example. This doc is about the shelf as a
whole: what's on it, and how to judge it. Five words, defined once:

- a **preview feature** is a finished-looking language feature or API that ships disabled. It
  compiles only with `--enable-preview` and `--release` equal to the running JDK's version, and it
  may change or disappear in the next release.
- an **incubator module** is an earlier stage, for APIs only: a `jdk.incubator.*` module you add
  with `--add-modules`. The Vector API has been one since Java 16.
- **withdrawn** means a preview feature was removed without becoming final. The JEP is closed,
  and code that used it no longer compiles.
- a **class file version** is the pair of numbers at the front of every `.class` file: the
  major version is the release (70 for Java 26), and a minor version of **65535** marks a class
  that uses preview features.
- **convergence** is a feature's design settling between previews: each round changes less, and
  the changes are spelling rather than premise.

That gives one rule, and everything below follows from it:

> **The number of previews tells you nothing; what changed between them tells you everything.**

A feature can preview seven times and be safe to plan for, or twice and be withdrawn.

### T2. A preview class runs on exactly one JDK

`LazyConstant` is a preview API in 26. Here is a small program that uses it:

```java
import java.util.concurrent.atomic.AtomicInteger;

public class Lz {
    static final AtomicInteger calls = new AtomicInteger();
    static final LazyConstant<String> GREETING = LazyConstant.of(() -> {
        calls.incrementAndGet();
        return "computed";
    });

    public static void main(String[] args) {
        System.out.println(GREETING.isInitialized() + " " + calls.get());
        System.out.println(GREETING.get() + " " + calls.get());
        System.out.println(GREETING.get() + " " + calls.get());
        System.out.println(GREETING.isInitialized());
    }
}
```

Compile it with the flag, and run it with the flag:

```sh
javac --release 26 --enable-preview Lz.java
java --enable-preview Lz
```

```text
Note: Lz.java uses preview features of Java SE 26.       (verified 2026-11-23)
Note: Recompile with -Xlint:preview for details.
false 0
computed 1
computed 1
true
```

Before the first `get()`, nothing has run. The first `get()` computes; the second returns the
cached value without calling the supplier again. (`java --enable-preview --source 26 Lz.java`
gives the same four lines without a separate `javac` step.)

Compiling **without** the flag fails before anything runs:

```text
Lz.java:5: error: LazyConstant is a preview API and is disabled by default.   (verified 2026-11-23)
    static final LazyConstant<String> GREETING = LazyConstant.of(() -> {
                 ^
  (use --enable-preview to enable preview APIs)
```

**Where the lock lives.** `javap` shows what the compiler wrote into the class file:

```sh
javap -v Lz.class | grep -E 'minor|major'
```

```text
  minor version: 65535                                   (verified 2026-11-23)
  major version: 70
```

Version 70.65535 means "Java 26, with preview features". Now run that same `Lz.class` two more
ways:

```sh
java Lz                                  # JDK 26, no flag
/path/to/jdk-25/bin/java --enable-preview Lz      # JDK 25, with the flag
```

```text
java.lang.UnsupportedClassVersionError: Preview features are not enabled for Lz          (verified 2026-11-23)
  (class file version 70.65535). Try running with '--enable-preview'

java.lang.UnsupportedClassVersionError: Lz has been compiled by a more recent version of
  the Java Runtime (class file version 70.65535), this version of the Java Runtime only
  recognizes class file versions up to 69.0
```

(Each is preceded by `Error: LinkageError occurred while loading main class Lz`; the lines are
wrapped here.)

**And the other direction.** The same idea in Java 25 was called `StableValue`. This class
compiled on **JDK 25** with `--release 25 --enable-preview`:

```java
public class Sv {
    static final StableValue<String> GREETING = StableValue.of();

    public static void main(String[] args) {
        System.out.println(GREETING.orElseSet(() -> "computed"));
    }
}
```

prints `computed` on JDK 25 with the flag. Run the same `Sv.class` on **JDK 26** with the flag:

```text
java.lang.UnsupportedClassVersionError: Sv (class file version 69.65535) was compiled with   (verified 2026-11-23)
  preview features that are unsupported. This version of the Java Runtime only recognizes
  preview features for class file version 70.65535
```

> **A preview class runs on exactly one JDK feature version, with a flag.**

That isn't a warning you can ignore: it's an error at class load, and the next JDK upgrade
triggers it. Newer *and* older JDKs both refuse.

**A correction to the old version:** it said "a class compiled with `--enable-preview` ... refuses
to load on any other version". That's only true of a class that **uses** a preview feature. A
plain class compiled on 25 with `--enable-preview` gets minor version **0**, and it runs on 26
with or without the flag (verified). The marker is per class. One preview class in a jar locks
that class, not the whole jar. Whether the rest still works without it is another question. It
also said the error is a `ClassFormatError`. It's `UnsupportedClassVersionError`, which is a
subclass of it.

```java
public class Plain {
    public static void main(String[] args) {
        System.out.println("plain class, no preview feature used");
    }
}
```

```text
$ (JDK 25) javac --release 25 --enable-preview Plain.java          (verified 2026-11-23)
$ javap -v Plain.class | grep -E 'minor|major'
  minor version: 0
  major version: 69
$ (JDK 26) java Plain
plain class, no preview feature used
$ (JDK 26) java --enable-preview Plain
plain class, no preview feature used
```

### T3. Withdrawal: String templates

String templates were previewed in **21** (JEP 430), again in **22** (JEP 459), then withdrawn and
removed in **23**. The syntax was `STR."Hello \{name}"`: a *template processor* (`STR`), a dot,
and a string with `\{...}` holes. Here is a program that used it:

```java
public class Templates {
    public static void main(String[] args) {
        String name = "ana";
        System.out.println(STR."Hello \{name}");
    }
}
```

On JDK 26, even with the flag, the syntax doesn't parse at all:

```sh
java --enable-preview --source 26 Templates.java
```

```text
Templates.java:4: error: illegal escape character        (verified 2026-11-23)
        System.out.println(STR."Hello \{name}");
                                       ^
1 error
error: compilation failed
```

JDK 25 gives the same error. The feature is so thoroughly gone that the compiler no longer knows
`\{` was ever meant to be anything: to today's compiler it's just a backslash before a character
that isn't an escape.

Everything about it looked safe: two previews, a high-profile JEP, the most-requested feature for
a decade, a lot of tutorials. The design was dropped because the template-processor mechanism
(the `STR.` part, which existed to make SQL and HTML injection impossible by construction) was
judged not to be worth its complexity.

**What changed between its previews is the signal**: the processor mechanism, the premise, was
still in question at the end. That's T1's rule. The INDEX marks this row `skipped`.

### T4. Convergence, or still being designed? Two cases

T1's rule says to read the changes between previews. Here are two features read that way.

**Lazy Constants, and the rename.** Put T2's two programs side by side:

```java
// JDK 25: StableValue. Created empty; the code that fills it is at the call site.
static final StableValue<String> GREETING = StableValue.of();
... GREETING.orElseSet(() -> "computed") ...

// JDK 26: LazyConstant. The code that fills it is part of the declaration.
static final LazyConstant<String> GREETING = LazyConstant.of(() -> "computed");
... GREETING.get() ...
```

**A correction to the old version:** its example used `StableValue.of()` and `orElseSet`, and
called it "verified working here". That's the Java 25 API. **On 26, `StableValue` doesn't exist**
(`cannot find symbol`, verified). It's `LazyConstant.of(supplier)` and `get()`. The supplier moved
from the call site to the declaration. That's a design change, not just a new name.

What it's for: a `final` field computed lazily that the JIT can still treat as a constant. A
`volatile` holder gives you laziness but not constant folding, and a `static final` gives you
constant folding but not laziness. [Doc 34](34-measuring-it-properly.md) explains why constant
folding matters.

**The rename is the interesting part.** "Stable value" was the internal HotSpot term (`@Stable` is
a real JVM-internal annotation), and it meant nothing to anyone outside OpenJDK. "Lazy constant"
says what it does. A feature renamed between previews, with the API reshaped at the same time,
is still being designed. Write down whether that's convergence or not, by T1's rule.

**Structured concurrency, and when a long preview is fine.** Six previews in 26, the longest run
on this shelf, and **not** the String templates signal.
[Doc 26](26-scoped-values-and-structured-concurrency.md) shows why: subclassing was replaced by a
`Joiner` parameter, and `join()` now returns the result. Each round simplified the spelling. The
premise, that forks can't outlive their scope, never moved. `ScopedValue`, from the same project,
went final in 25.

So the two cases differ in exactly the way the rule predicts: one changed its *spelling* round
after round with the same premise, and the other changed its *shape* in its latest round.

### T5. Incubator modules: the stage before preview

Previews are for language features and APIs that are close to final. An API can also start one
stage earlier, as an **incubator module**: a separate `jdk.incubator.*` module that isn't even
visible unless you add it. The Vector API is the long-running example. It lets you write one
operation that the CPU applies to several values at once (SIMD):

```java
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorSpecies;

public class Lanes {
    static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_128;   // 4 ints at a time

    public static void main(String[] args) {
        int[] a = { 1, 2, 3, 4 };
        int[] b = { 10, 20, 30, 40 };
        int[] sum = new int[4];

        IntVector va = IntVector.fromArray(SPECIES, a, 0);
        IntVector vb = IntVector.fromArray(SPECIES, b, 0);
        va.add(vb).intoArray(sum, 0);                                     // one add, four lanes

        System.out.println(SPECIES.length() + " lanes: " + java.util.Arrays.toString(sum));
    }
}
```

```sh
java --add-modules jdk.incubator.vector Lanes.java
```

```text
WARNING: Using incubator modules: jdk.incubator.vector   (verified 2026-11-23)
4 lanes: [11, 22, 33, 44]
```

Without `--add-modules`, the package doesn't exist as far as the compiler is concerned:

```text
Lanes.java:1: error: package jdk.incubator.vector is not visible   (verified 2026-11-23)
import jdk.incubator.vector.IntVector;
                    ^
```

Note what's different from a preview: no `--enable-preview`, no 65535 minor version, just a module
you opt into and a warning on every run. It has been an incubator since Java 16 and has never
moved on to preview. Exercise 3, question 4 asks why.

### T6. What's on the shelf in 26

**Answered exercise 3, question 1 yet?** It asks for a prediction before you read this table.

Verified on JDK 26: each one fails without `--enable-preview` with the message shown, and works
with it. The JEP numbers and counts come from the [INDEX](INDEX.md).

| Feature | In 26 | Without the flag (verified) |
| --- | --- | --- |
| Structured concurrency | 6th preview, JEP 525 | `StructuredTaskScope is a preview API and is disabled by default` |
| Primitive types in patterns | 4th preview, JEP 530 | `primitive patterns are a preview feature and are disabled by default` |
| Lazy Constants (Stable Values in 25) | 2nd preview, JEP 526 | `LazyConstant is a preview API and is disabled by default` |
| PEM encodings | 2nd preview, JEP 524 | `PEMEncoder is a preview API and is disabled by default` |
| Vector API | 11th incubator, JEP 529 | needs `--add-modules jdk.incubator.vector`, and warns `Using incubator modules` |

The old version's table also counted previews in 27. There's no JDK 27 on this machine, so those
counts are left out here. The INDEX has the 27 list.

## Practice

### Exercise 1: reproduce the class-file lock

In a scratch directory **outside the repo**, save T2's `Lz.java` and `Plain.java`, and reproduce:

1. `Lz` compiled and run with the flag on 26: the four lines.
2. `Lz` run on 26 without the flag, and on 25 with it: the two `UnsupportedClassVersionError`s.
3. `javap -v` on `Lz.class` and on `Plain.class` (compiled with `--enable-preview`): 65535 versus 0.

If you have only one JDK installed, do steps 1 and 3 and the no-flag half of step 2; T2 shows the
JDK 25 output.

**Done when:** you've seen the errors yourself, and can say from `javap` alone whether a class
will load on another JDK.

### Exercise 2: `mvn -Ppreview verify`, and what it doesn't do

`pom.xml` has a `preview` profile. It sets `--release` to the running JDK and passes
`--enable-preview` to `javac` and to Surefire. **A correction to the old version:** it presented
the profile as how you run "them", the preview features. **Nothing in the repo uses one**
(verified):

```sh
mvn -Ppreview clean verify        # BUILD SUCCESS, 248 tests, 211 skipped: the same as without
javap -v <every class in target/classes> | grep 'minor version: 65535'      # no matches
```

The profile is there for when you add preview code: [doc 26](26-scoped-values-and-structured-concurrency.md)'s
`StructuredTaskScope` work, or the experiments above, if you move them into the repo. The moment you
do, every class that uses one gets `70.65535` (T2), and the default build (no profile) fails to
compile it. **That's the design, and the default build is the guard.** Keep preview off by default.

**Done when:** you've run both commands and got the same result, and can say in one sentence why
the default build is the guard.

### Exercise 3: write the answers here

1. **Before reading T6's table**, write which of the five you think will ship first, and
   why, using the rule in T1 rather than the preview count. _(fill in)_
2. **The String templates test.** What, concretely, would have told you in 2024 not to build on
   them? Is there anything, or was it genuinely unforeseeable? _(fill in)_
3. **Your rule.** One sentence you'll actually apply the next time a preview feature is the
   obvious solution to a problem in front of you. _(fill in)_
4. **The Vector API** has incubated since Java 16 and has never been previewed. What is it waiting
   for? (The answer is a project name.) _(fill in)_
5. **A preview class runs on exactly one JDK version.** Is there any production circumstance in
   which you'd accept that? What would you have to control? _(fill in)_

### Exercise 4: fill in "What got worse"

- _(fill this in)_

Prompts: previews exist so the JDK gets feedback before committing, but only people who use them
give it, and using them is discouraged. Is that circular, and how would you fix it? A rename plus
an API reshape between previews: reassuring or alarming? Does `--enable-preview` make Java slower
to adopt good ideas, or faster to avoid bad ones?

### Exercise 5: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. Why does Java need previews at all, when other languages just ship features? (T1)
2. State T1's rule in your own words. (T1)
3. What do the major and minor version of a class that uses a preview feature on JDK 26 look like?
   (T2)
4. A class compiled on 26 with `--enable-preview` uses `LazyConstant`. What happens when you run
   it on 26 without the flag? On 25 with the flag? (T2)
5. A class compiled with `--enable-preview` uses no preview feature. Does it run on the next JDK?
   (T2)
6. Is the error a `ClassFormatError` or an `UnsupportedClassVersionError`? What's the relationship
   between the two? (T2)
7. Why does `STR."Hello \{name}"` fail with "illegal escape character" on JDK 26, and not with a
   message about a removed feature? (T3)
8. What changed between `StableValue` (25) and `LazyConstant` (26), beyond the name? (T4)
9. Structured concurrency has previewed six times. Why is that not a warning sign, by T1's rule?
   (T4)
10. How do you use an incubator module, and what's different about it from a preview? (T5)

**Done when:** you can answer all ten without looking.
