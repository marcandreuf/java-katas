# 21. Unnamed variables and patterns, and primitive patterns

**Java 22** (unnamed variables and patterns, JEP 456) · **preview in Java 23 to 27** (primitive
patterns) · stage 10 of [the release-ordered track](INDEX.md) · needs `--release 22` or newer;
`<java.release>` is 26, no build change

Do [doc 02](02-sealed-types.md) and [doc 03](03-pattern-matching.md) first: this builds on both.
The exercise is new code with tests already written. The one old-code change in exercise 2 is
proven by the compiler, not by a test, and that exercise says why that's enough.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `Unnamed`](#exercise-1-patternsunnamedjava-five-todos) | Fill in five TODOs, each one a place `_` belongs. | T1–T4 | `UnnamedTest` exists: delete its `@Disabled`, 8 tests go red. |
| [2. `SimpleThreads`](#exercise-2-the-one-old-code-target) | Replace one unused `catch` parameter with `_`. One commit. | T1, T3 | None needed: the compiler proves it, and the commit message says how. |
| [3. What got worse](#exercise-3-fill-in-what-got-worse) | Write down what `_` and primitive patterns cost. | all | None. |
| [4. Review questions](#exercise-4-review-questions) | Answer without looking, then check against the theory. | all | None. |

Primitive patterns (T5–T7) have no exercise, on purpose: they're a preview feature, and T5 says
why the repo doesn't turn previews on.

## Theory

Read the seven sections in order. Here is the whole story in one paragraph, so you know where
it's going.

> Java sometimes forces you to declare a name you'll never use: a `catch` parameter, a lambda
> parameter, a record component you don't care about. Before Java 22 you invented a name and
> hoped the reader would check it was unused. Now you write `_`, which says "nothing here" (T1).
> Its real home is record patterns, where it lets you match a shape and ignore most of it (T2).
> It's also legal in a handful of other declarations, and not in the ones that are part of an API
> (T3); and it took three releases to get here (T4). The second half of the doc is a feature that
> isn't finished: primitive patterns, still a preview in JDK 26. A preview needs a flag and ties
> your class files to one JDK (T5). The feature itself is simple to state: a primitive pattern
> matches only when a conversion loses nothing (T6), and that rule also lets `switch` work on any
> primitive (T7).

Every code sample below is a complete program. Save it as the class name plus `.java` and run it
as the sample says. Outputs marked *verified* were run on JDK 26. **Samples for the final feature
(`_`, T1–T4) run with plain `java Name.java`. Samples for the preview feature (T6, T7) need
`--enable-preview`, and say so every time.**

### T1. The name the language forces you to write

Some declarations aren't optional. A `catch` must name its exception. A lambda for a two-argument
interface must name two parameters. A record pattern must say something for every component. When
you don't need the value, you still have to write a name, and the old habit was `ignored`, `e`, or
`unused`. That works until someone has to read the code: every name is a question ("is this used
further down?") that the reader has to answer by scanning.

Java 22 (JEP 456) gives you a way to answer it in the declaration. Two words:

- an **unnamed variable** is a declaration written as `_`: the language makes you declare
  something, and you're saying you won't use it. You can't read it afterwards.
- an **unnamed pattern** is `_` in place of a pattern inside a record pattern, as in
  `case Key(var c, _)`. It matches anything, and binds nothing.

And one rule:

> **`_` is for a name the language forces you to write.** If nothing forced you to declare it,
> `_` doesn't help: delete the variable instead.

### T2. Unnamed patterns: match the shape, ignore the rest

The feature exists for record patterns ([doc 03](03-pattern-matching.md)). A record pattern has to
mention every component, so a switch that only cares about one of them used to bind all the others
anyway. Here is a sealed type that isn't the exercise's:

```java
public class Events {
    sealed interface Event permits Click, Key, Scroll {}
    record Click(int x, int y, long at) implements Event {}
    record Key(char c, long at) implements Event {}
    record Scroll(int dy, long at) implements Event {}

    static String kind(Event e) {
        return switch (e) {
            case Click _, Scroll _ -> "pointer";          // two patterns, one arm: neither binds
            case Key(var c, _)     -> "key " + c;         // take the char, ignore the timestamp
        };
    }

    public static void main(String[] args) {
        System.out.println(kind(new Click(1, 2, 0L)));
        System.out.println(kind(new Scroll(-3, 0L)));
        System.out.println(kind(new Key('q', 0L)));
    }
}
```

```text
pointer                                                  (verified 2026-11-23)
pointer
key q
```

Two things in it are new in Java 22:

- `case Click _, Scroll _` puts **two patterns in one arm**. That's legal only because neither one
  binds anything. If they bound variables, the arm couldn't know which ones exist:

  ```java
  case Click(var x, _, _), Scroll(var y, _) -> "pointer";
  ```

  ```text
  error: illegal fall-through from a pattern               (verified 2026-11-23)
  ```

- `Key(var c, _)` takes the character and ignores the timestamp. With a name there instead
  (`Key(var c, var at)`), a reader has to check the arm to see whether `at` is used. With `_`,
  the pattern says so.

At one level of nesting that's a small win. In a record pattern nested inside another, where each
level has components you don't need, it's the difference between readable and not: without `_`
you invent names a reader then has to check are unused. Exercise 1's TODO 5 is that case.

### T3. Where else `_` goes, and where it can't

`_` is legal in six declaration forms, plus unnamed patterns (all verified in one file on JDK 26).
Four of them in one program:

```java
import java.util.List;

public class Discards {
    static int calls = 0;

    static boolean register() {                       // called for its side effect
        calls++;
        return true;
    }

    static int startTimer() {                         // also a side effect, returning an int
        calls++;
        return 0;
    }

    static AutoCloseable openAudit() {
        System.out.println("  audit opened");
        return () -> System.out.println("  audit closed");
    }

    public static void main(String[] args) throws Exception {
        var _ = register();                           // a local: the result is deliberately dropped
        System.out.println("registered, calls = " + calls);

        int rows = 0;
        for (var _ : List.of("a", "b", "c")) {        // enhanced for: count, don't look
            rows++;
        }
        System.out.println("rows = " + rows);

        int ticks = 0;
        for (int i = 0, _ = startTimer(); i < 2; i++) {   // the initialiser of a basic for
            ticks++;
        }
        System.out.println("ticks = " + ticks + ", calls = " + calls);

        System.out.println("try-with-resources:");
        try (var _ = openAudit()) {                   // a resource you need open, never touch
            System.out.println("  working");
        }
    }
}
```

```text
registered, calls = 1                                    (verified 2026-11-23)
rows = 3
ticks = 2, calls = 2
try-with-resources:
  audit opened
  working
  audit closed
```

The other two forms are the ones exercise 1 asks you to write, so they're described here, not
shown: a **`catch` parameter** (an exception you handle without reading), and a **lambda
parameter**, more than once if you like (`(_, _) -> 0`).

**Where it isn't legal:** a method parameter or a field. Both say:

```text
error: underscore not allowed here                       (verified 2026-11-23)
```

Those names are part of an API or an object's state, so nothing forces you to write one you don't
use: T1's rule, enforced by the compiler.

**And what `_` doesn't catch.** Name an unused record component `label` instead of `_`, and
compile with `-Xlint:all`: **no warning** (verified). `javac` has never warned about unused
variables, and still doesn't. **`_` is a promise to the reader, not a check.**

### T4. A short history of `_`

`_` wasn't always available to mean "nothing". Verified with JDK 26's `javac` at each
`--release`:

| `--release` | `int _ = 1;` |
| --- | --- |
| 8 | warning: `as of release 9, '_' is a keyword, and may not be used as an identifier` |
| 9 to 21 | error: `unnamed variables are not supported in -source N` |
| 22 and later | compiles, and reading `_` afterwards is `underscore not allowed here` |

Up to Java 8, `_` was an ordinary name. Java 9 took it away so that it could later mean something
else, and Java 22 gave it that meaning. Java 21 had it as a preview (JEP 443), which JDK 26's
`javac` can't show, because it only enables preview features for its own release. T5 explains
what that means.

### T5. Preview features: finished enough to try, not to keep

The rest of this doc is about a feature that isn't final. Java ships some features as a
**preview**: finished enough to try and not finished enough to keep. A preview compiles only with
`--enable-preview`, at a `--release` equal to the running JDK, and it can change or be withdrawn
in the next release.

Without the flag, the compiler refuses and tells you why:

```text
Exact.java:4: error: primitive patterns are a preview feature and are disabled by default.
  (use --enable-preview to enable primitive patterns)       (verified 2026-11-23, trimmed)
```

With it, the class compiles, but it's marked. Compile the T6 sample with `javac` and the class
file's minor version is `65535`, which means "uses preview features of exactly this release". It
won't load anywhere else:

```sh
javac --enable-preview --release 26 -d out Exact.java
java -cp out Exact                     # JDK 26, no flag
java --enable-preview -cp out Exact    # JDK 25, with the flag
```

```text
Note: Exact.java uses preview features of Java SE 26.    (verified 2026-11-23, trimmed)
java.lang.UnsupportedClassVersionError: Preview features are not enabled for Exact (class file version 70.65535). Try running with '--enable-preview'
java.lang.UnsupportedClassVersionError: Exact has been compiled by a more recent version of the Java Runtime (class file version 70.65535), this version of the Java Runtime only recognizes class file versions up to 69.0
```

On JDK 26 with `--enable-preview` it runs (T6's output). On any other JDK it won't, flag or no
flag. That's why there's no compiled exercise for primitive patterns in this repo: to try one
inside it, you'd need `mvn -Ppreview verify`, and the pom's own comment says why preview is never
on by default: a class compiled with it won't load on any other JDK version.

### T6. Primitive patterns: exact or nothing

Pattern matching has always worked on reference types: `obj instanceof String s`. A **primitive
pattern** is a type pattern over a primitive type: `x instanceof byte b`, `case int i`. Preview in
every release from 23 to 27.

The interesting question is what "matches" means for primitives. A cast never refuses: `(byte) 300`
is 44, silently. A primitive pattern can't do that, or `instanceof` would be lying. So it uses one
word:

- an **exact** conversion is one that loses nothing: 100 fits in a `byte`, 300 doesn't, and 2.0
  converts to `int` exactly while 1.5 doesn't.

> **A primitive pattern matches only when the conversion is exact.** It never truncates.

Here are the cast and the pattern side by side. **Preview: run with
`java --enable-preview --source 26 Exact.java`.**

```java
public class Exact {
    public static void main(String[] args) {
        int big = 300, small = 100;
        System.out.println("(byte) 300 = " + (byte) big + "   300 instanceof byte: " + (big instanceof byte b));
        if (small instanceof byte b) {
            System.out.println("100 instanceof byte: true, b = " + b);
        }

        double two = 2.0, half = 1.5, negZero = -0.0;
        if (two instanceof int i) {
            System.out.println("2.0 instanceof int: true, i = " + i);
        }
        System.out.println("(int) 1.5 = " + (int) half + "   1.5 instanceof int: " + (half instanceof int i));
        System.out.println("(int) -0.0 = " + (int) negZero + "   -0.0 instanceof int: " + (negZero instanceof int i));

        int odd = 16_777_217;
        System.out.println("(float) 16777217 = " + (float) odd + "   instanceof float: " + (odd instanceof float f));
    }
}
```

```text
(byte) 300 = 44   300 instanceof byte: false             (verified 2026-11-23)
100 instanceof byte: true, b = 100
2.0 instanceof int: true, i = 2
(int) 1.5 = 1   1.5 instanceof int: false
(int) -0.0 = 0   -0.0 instanceof int: false
(float) 16777217 = 1.6777216E7   instanceof float: false
```

(The source launcher prints no preview note; `javac` does, as T5 shows.)

The cast truncates; the pattern refuses. Two to remember:

- **`-0.0`**: the cast gives 0, but the sign is lost, so it isn't exact.
- **16,777,217**: an `int` that a `float` can't hold. The cast rounds it to 16,777,216 without a
  word; the pattern says no.

That rule is also why the feature has taken five previews: every pair of primitive types needs a
definition of "exact", and every definition has edge cases like those two.

### T7. `switch` over any primitive

The same rule lets `switch` take any primitive, and test whether a value fits a narrower type.
**Preview: run with `java --enable-preview --source 26 Sizes.java`.**

```java
public class Sizes {
    static String size(long value) {
        return switch (value) {
            case int i when i > 100 -> "big int";
            case int i              -> "int";
            case long l             -> "long";
        };
    }

    public static void main(String[] args) {
        System.out.println(size(5) + ", " + size(500) + ", " + size(1L << 40));
    }
}
```

```text
int, big int, long                                       (verified 2026-11-23)
```

`case int i` matches a `long` that fits in an `int` exactly; `case long l` catches the rest.

**A correction to the old version of this doc:** it showed this switch without a type, and as an
`int` selector it doesn't compile. `case int i` and `case long l` are then both unconditional (any
`int` fits both), and `javac` says so:

```text
error: duplicate unconditional pattern                   (verified 2026-11-23)
```

The selector must be wider than the first pattern. Switching on a `boolean`, which
[doc 03](03-pattern-matching.md) ran into on `Door`, is part of the same preview.

**Five previews.** From the JEP pages, since only JDK 25 and 26 are installed: JEP 455 in 23, 488
in 24, 507 in 25, 530 in 26 and 532 in 27. **The old version listed four**: it skipped 488. JEP 532
says 27's preview is "without change". JEP 530, in 26, tightened the dominance checks, which is
probably where that `duplicate unconditional pattern` comes from.

**Five previews is a signal, not a delay.** It's the last missing piece of pattern matching
(records, sealed types, record patterns and switch patterns have all shipped), and what remains
is the exactness question from T6, for every pair of types. String templates were previewed twice
and withdrawn. [Doc 33](33-preview-shelf.md) is about reading that difference.

## Practice

### Exercise 1: `patterns/Unnamed.java`, five TODOs

Delete the `@Disabled` in `UnnamedTest`: **8 tests, all red**, every one from a TODO stub
(verified).

**A correction to the old version, and to the class's own javadoc:** both say `_` is legal in
"five places". The six declaration forms in T3, plus unnamed patterns, is more than five. The
TODOs cover three of them (record pattern components, `catch` and a lambda parameter), and that
is the honest selection: they're the ones you'll meet.

1. **TODO 1 and 2, one commit.** TODO 1 is the one to push back on. Bind the label to `_`, then
   rename it to `label` and leave it unused, and confirm the compiler doesn't care either way
   (T3's last paragraph). Write down what `_` bought. "A reader doesn't have to scan the arm to
   check whether `label` is used" is real, and small.
2. **TODO 3, `catch (NumberFormatException _)`.** This one has no counter-argument: a `catch` must
   name its parameter, and before Java 22 the best you could do was call it `ignored` and hope.
3. **TODO 4, the lambda.** `Map.forEach` must take two parameters even when you want one.
4. **TODO 5, the nested pattern.** This is where `_` stops being cosmetic (T2). Without it, you
   invent two names a reader then has to check are unused. At one level of nesting, record
   patterns are arguably not worth it ([doc 03](03-pattern-matching.md) asks you to say so); at
   two, `_` is what keeps them readable. **The feature exists for record patterns. The `catch` is
   a bonus.**

**Done when:** all 8 `UnnamedTest` tests pass with the `@Disabled` gone, the five TODOs are in as
four commits (TODOs 1 and 2 together), and you've written down what `_` bought in TODO 1.

### Exercise 2: the one old-code target

**A correction to the old version:** it said the old code has almost no target for `_`, and that
the only candidate, an ignored `catch` parameter, didn't apply because the two in `guardedLock/`
are bugs. The bugs part is right: `Consumer.java:28`, `Drop.java:23` and `:44`, and
`Safelock.java:83` are empty `catch (InterruptedException e) {}` blocks, and [doc 07](07-classic-concurrency.md)
makes you fix them, not tidy them. Putting `_` there would make a swallowed interrupt look
deliberate.

But there is a real one. `concurrency/SimpleThreads.java:38`:

```java
} catch (NumberFormatException e) {
    System.err.println(usage());
    System.exit(1);
}
```

The exception is handled, and `e` is never read. It's exactly TODO 3, in code from 2014. Every
other `catch` in `src/main` either reads its exception or is one of the bugs above (`grep`).

`SimpleThreadsTest` covers only `usage()` (doc 04), not this `catch`, and **this change doesn't
need a test**. Compile it both ways, with `-g -parameters --release 26` as Maven does (verified):

- `javap -c` is **identical**: not one instruction changes.
- the class file differs in **one line of debug information**: the `LocalVariableTable` entry
  `e  Ljava/lang/NumberFormatException;` is gone.
- compiled without `-g`, the two class files are **byte-identical**.

That's [`../testing-first.md`](../testing-first.md)'s "when the compiler can prove it instead"
case, by the margin of a debug table. One commit, with that evidence in the message.

**Done when:** the one commit is in, its message records the `javap -c` comparison, and
`SimpleThreadsTest` still passes.

### Exercise 3: fill in "What got worse"

- _(fill this in)_
- _(fill this in)_

Prompts: `_` stopped being a legal name in Java 9: what did that do to 2015 code that used it as a
lambda parameter, and was it an acceptable price? If `_` means "I don't use this", what's the
argument for ever naming an unused `catch` parameter? Does `var _ = call();` read as "I'm
discarding this result" or as noise, and would you accept it in review? A primitive pattern
refuses where a cast truncates: which would you rather debug?

### Exercise 4: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. What's the one-line rule for when `_` helps, and what should you do instead when it doesn't?
   (T1)
2. Why is `case Click _, Scroll _ ->` legal, and `case Click(var x, _, _), Scroll(var y, _) ->`
   not? (T2)
3. Name four declarations where `_` is legal, and two where it isn't. Why those two? (T3)
4. You name an unused component `label` and compile with `-Xlint:all`. What does `javac` say, and
   what does that tell you about `_`? (T3)
5. What did `int _ = 1;` do at `--release 8`, at `--release 17`, and at `--release 22`? (T4)
6. You compile a class with `--enable-preview` on JDK 26. What happens when you run it on JDK 26
   without the flag, and on JDK 25 with it? (T5)
7. What do `(byte) 300` and `300 instanceof byte b` each give, and why do they differ? (T6)
8. Why is `-0.0 instanceof int i` false, when `(int) -0.0` is 0? (T6)
9. Why does `switch` on an `int` with both `case int i` and `case long l` fail to compile, while
   the same cases on a `long` work? (T7)
10. Primitive patterns have been previewed five times. Why is that a signal rather than a delay?
    (T7)

**Done when:** you can answer all ten without looking.
