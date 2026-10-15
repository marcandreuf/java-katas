# 33. The preview shelf, and what withdrawal looks like

**Java 25, 26** · stage 22 of [the release-ordered track](INDEX.md) · **`mvn -Ppreview verify`**,
and mostly written answers

## Why this is a stage

Twenty-one stages of this track teach features that shipped. This one is about the features that
have not, because **reading the preview signal correctly is a professional skill** and the cost of
getting it wrong is real: code you wrote, tests you wrote, a blog post you published, and a
conference talk somebody gave about `STR."\{name}"` in 2024.

## The case study: String templates

Previewed in **Java 21** (JEP 430), previewed again in **22** (JEP 459), **withdrawn**, and
**removed from 23**. No successor JEP. They are not in Java 26 and nothing replaces them.

    // this was going to be the future of string interpolation. It is gone.
    String greeting = STR."Hello \{name}, you are \{age}";

Everything about it looked safe. Two previews, a high-profile JEP, the single most requested Java
feature for a decade, enormous tutorial coverage. And the design was abandoned because the
template-processor mechanism — the `STR.` part, which existed to make SQL and HTML injection
impossible by construction — was judged not to have earned its complexity.

**That is the signal to internalise: two previews is not convergence.** The index marks this row
`skipped` for exactly this reason.

## What is on the shelf now

| Feature | Previews | First | Latest |
| --- | --- | --- | --- |
| Structured concurrency | 2 incubator + 7 preview | 19 | 27 |
| Primitive types in patterns | 5 preview | 23 | 27 |
| Lazy Constants (was Stable Values) | 3 preview | 25 | 27 |
| PEM encodings | 3 preview | 25 | 27 |

### Lazy Constants, and the rename worth noticing

JEP 502 in Java 25 called it **Stable Values**. JEP 526 in Java 26 renamed it to **Lazy
Constants**. Same feature. Verified working here with `--enable-preview`:

    static final StableValue<String> LAZY = StableValue.of();

    LAZY.orElseSet(this::expensive);     // computes once
    LAZY.orElseSet(this::expensive);     // returns the cached value, does not recompute

What it is for: **a `final` field whose value is computed lazily, which the JIT can still treat as
a constant.** A `volatile` double-checked holder gives you laziness but not constant folding; a
`static final` gives you constant folding but not laziness. This gives both, and it is the
supported answer to the class-holder idiom.

**The rename is the interesting part.** "Stable value" was the internal JVM term —
`@Stable` is a real HotSpot annotation — and it meant nothing to anybody outside OpenJDK. "Lazy
constant" says what it does. A feature that gets renamed between previews is a feature still being
designed, and that is useful information about whether to build on it.

### Structured concurrency, and when a long preview is fine

Seven previews is the longest run in this list, and it is **not** the String templates signal.
[Doc 26](26-scoped-values-and-structured-concurrency.md) shows why: the API is converging —
subclassing replaced by a `Joiner` parameter, `join()` now returning the result — and each preview
has been a simplification, not a rethink of the premise. `ScopedValue`, which came through the
same programme, went final in 25.

**The distinction to be able to draw:** String templates kept re-litigating *whether the feature
should exist in that shape*. Structured concurrency has only ever re-litigated *the spelling*.

## Running them

    mvn -Ppreview verify

`pom.xml` has the profile. **Preview is off by default and must stay that way**, for a reason worth
knowing exactly: a class compiled with `--enable-preview` records the JDK version it was built
with and **refuses to load on any other version**. Not a warning — a `ClassFormatError`. That is
deliberate, and it means a preview feature cannot be shipped by accident, and that a preview
feature in your build makes your artifact unusable the moment you upgrade the JDK.

## Write the answers here

1. **Before reading the table above**, you had an opinion about which of these will ship. Write it
   down now, with a reason, for each of the four. _(fill in)_
2. **The String templates test.** What, concretely, would have told you in 2024 not to build on
   them? Is there anything, or was it genuinely unforeseeable? _(fill in)_
3. **Your rule.** Write the rule you will actually apply when the next preview feature is the
   obvious solution to a problem in front of you. One sentence. _(fill in)_
4. **The Vector API** has incubated for eleven releases since Java 16 and is still not previewed.
   Why, and what is it waiting for? (The answer is one word and it is a project name.) _(fill in)_
5. **`--enable-preview` makes your artifact JDK-version-locked.** Is there any production
   circumstance in which you would accept that? _(fill in)_

## What got worse

- _(fill this in)_

Prompts: previews exist so the JDK gets feedback before committing — but only people who use them
provide it, and using them is discouraged. Is that circular, and how would you fix it? If a
feature is renamed between previews, is that reassuring or alarming? Does the existence of
`--enable-preview` make Java slower to adopt good ideas, or faster to avoid bad ones?
