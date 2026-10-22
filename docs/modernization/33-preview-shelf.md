# 33. The preview shelf, and what withdrawal looks like

**Java 21 → 26** · stage 22 of [the release-ordered track](INDEX.md) · **written answers, plus
scratch files outside the repo**

**No TODOs and no tests.** Nothing in the repo uses a preview feature (part three shows that), so
there's nothing to characterize. The experiments below are single-file programs you compile in a
scratch directory, and the deliverable is the written answers.

## What this stage is actually about

Five words, defined once:

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

That gives one rule, and everything below follows from it: **the number of previews tells you
nothing; what changed between them tells you everything.** A feature can preview seven times and
be safe to plan for, or twice and be withdrawn.

## The worked example: a preview class on the wrong JDK

`LazyConstant` is a preview API in 26. A five-line program that uses it, compiled with
`javac --release 26 --enable-preview` (all verified):

    false 0              // isInitialized(), calls to the supplier
    computed 1           // first get(): computes
    computed 1           // second get(): cached, not recomputed
    true

Now run that class three ways:

    java --enable-preview Lz     // works, as above
    java Lz                      // UnsupportedClassVersionError: Preview features are not enabled
                                 //   for Lz (class file version 70.65535). Try running with '--enable-preview'
    (JDK 25) java --enable-preview Lz
                                 // UnsupportedClassVersionError: Lz has been compiled by a more
                                 //   recent version of the Java Runtime (class file version 70.65535)

And the other direction: a class that uses `StableValue` (the name in 25), compiled on 25, run on
26 with the flag:

    UnsupportedClassVersionError: Sv (class file version 69.65535) was compiled with preview
    features that are unsupported. This version of the Java Runtime only recognizes preview
    features for class file version 70.65535

**A preview class runs on exactly one JDK feature version, with a flag.** That isn't a warning
you can ignore: it's an error at class load, and the next JDK upgrade triggers it.

**A correction to the old version:** it said "a class compiled with `--enable-preview` ... refuses
to load on any other version". That's only true of a class that **uses** a preview feature. A
plain class compiled on 25 with `--enable-preview` gets minor version **0**, and it runs on 26
with or without the flag (verified). The marker is per class. One preview class in a jar locks
that class, not the whole jar. Whether the rest still works without it is another question. It
also said the error is a `ClassFormatError`. It's `UnsupportedClassVersionError`, which is a
subclass of it.

## Part one: String templates, the one that was withdrawn

Previewed in **21** (JEP 430), again in **22** (JEP 459), then withdrawn and removed in **23**.
On JDK 26, with `--enable-preview`, the syntax doesn't parse at all (verified):

    System.out.println(STR."Hello \{name}");
    error: illegal escape character

The feature is so thoroughly gone that the compiler no longer knows `\{` was ever meant to be
anything. Everything about it looked safe: two previews, a high-profile JEP, the most-requested
feature for a decade, a lot of tutorials. The design was dropped because the template-processor
mechanism (the `STR.` part, which existed to make SQL and HTML injection impossible by
construction) was judged not to be worth its complexity.

**What changed between its previews is the signal**: the processor mechanism, the premise, was
still in question at the end. That's the rule above. The INDEX marks this row `skipped`.

## Part two: what's on the shelf in 26

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

### Lazy Constants, and the rename

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
is still being designed. Write down whether that's convergence or not, by the rule above.

### Structured concurrency, and when a long preview is fine

Six previews in 26, the longest run on this shelf, and **not** the String templates signal.
[Doc 26](26-scoped-values-and-structured-concurrency.md) shows why: subclassing was replaced by a
`Joiner` parameter, and `join()` now returns the result. Each round simplified the spelling. The
premise, that forks can't outlive their scope, never moved. `ScopedValue`, from the same project,
went final in 25.

## Part three: `mvn -Ppreview verify`, and what it doesn't do

`pom.xml` has a `preview` profile. It sets `--release` to the running JDK and passes
`--enable-preview` to `javac` and to Surefire. **A correction to the old version:** it presented
the profile as how you run "them", the preview features. **Nothing in the repo uses one**
(verified):

    mvn -Ppreview clean verify        # BUILD SUCCESS, 248 tests, 211 skipped: the same as without
    javap -v <every class in target/classes> | grep 'minor version: 65535'      # no matches

The profile is there for when you add preview code: [doc 26](26-scoped-values-and-structured-concurrency.md)'s
`StructuredTaskScope` work, or the experiments above, if you move them into the repo. The moment you
do, every class that uses one gets `70.65535`, and the default build (no profile) fails to compile
it. **That's the design, and the default build is the guard.** Keep preview off by default.

## Write the answers here

1. **Before reading part two's table**, write which of the five you think will ship first, and
   why, using the rule above rather than the preview count. _(fill in)_
2. **The String templates test.** What, concretely, would have told you in 2024 not to build on
   them? Is there anything, or was it genuinely unforeseeable? _(fill in)_
3. **Your rule.** One sentence you'll actually apply the next time a preview feature is the
   obvious solution to a problem in front of you. _(fill in)_
4. **The Vector API** has incubated since Java 16 and has never been previewed. What is it waiting
   for? (The answer is a project name.) _(fill in)_
5. **A preview class runs on exactly one JDK version.** Is there any production circumstance in
   which you'd accept that? What would you have to control? _(fill in)_

## What got worse

- _(fill this in)_

Prompts: previews exist so the JDK gets feedback before committing, but only people who use them
give it, and using them is discouraged. Is that circular, and how would you fix it? A rename plus
an API reshape between previews: reassuring or alarming? Does `--enable-preview` make Java slower
to adopt good ideas, or faster to avoid bad ones?
