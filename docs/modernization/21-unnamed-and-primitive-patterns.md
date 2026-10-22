# 21. Unnamed variables and patterns, and primitive patterns

**Java 22** (unnamed variables and patterns, JEP 456) · **preview in Java 23 to 27** (primitive
patterns) · stage 10 of [the release-ordered track](INDEX.md) · needs `--release 22` or newer;
`<java.release>` is 26, no build change

Do [doc 02](02-sealed-types.md) and [doc 03](03-pattern-matching.md) first: this builds on both.
The exercise is new code with tests already written. The one old-code change in part two is proven
by the compiler, not by a test, and that part says why that's enough.

## What this stage is actually about

Five words, defined once:

- an **unnamed variable** is a declaration written as `_`: the language makes you declare
  something, and you're saying you won't use it. You can't read it afterwards.
- an **unnamed pattern** is `_` in place of a pattern inside a record pattern, as in
  `case Key(var c, _)`. It matches anything, and binds nothing.
- a **preview feature** is finished enough to try and not finished enough to keep: it compiles
  only with `--enable-preview`, at a `--release` equal to the running JDK, and it can change or be
  withdrawn.
- a **primitive pattern** is a type pattern over a primitive type: `x instanceof byte b`,
  `case int i`. Preview in every release from 23 to 27.
- an **exact** conversion is one that loses nothing: 100 fits in a `byte`, 300 doesn't, and
  2.0 converts to `int` exactly while 1.5 doesn't.

Two rules, one for each half:

- **`_` is for a name the language forces you to write.** If nothing forced you to declare it, `_`
  doesn't help: delete the variable instead.
- **A primitive pattern matches only when the conversion is exact.** It never truncates. That's
  why it's taken five previews: every pair of primitive types needs a definition of "exact".

## The worked example

A sealed type that isn't the exercise's:

    sealed interface Event permits Click, Key, Scroll {}
    record Click(int x, int y, long at) implements Event {}
    record Key(char c, long at) implements Event {}
    record Scroll(int dy, long at) implements Event {}

    static String kind(Event e) {
        return switch (e) {
            case Click _, Scroll _ -> "pointer";
            case Key(var c, _)     -> "key " + c;
        };
    }

`kind(new Click(1, 2, 0L))` is `"pointer"` and `kind(new Key('q', 0L))` is `"key q"` (verified). Two
things in it are new in Java 22:

- `case Click _, Scroll _` puts **two patterns in one arm**. That's legal only because neither one
  binds anything. Try `case Click(var x, _, _), Scroll(var y, _)` and the compiler says
  `illegal fall-through from a pattern` (verified): the arm couldn't know which variable exists.
- `Key(var c, _)` takes the character and ignores the timestamp.

Where else `_` is legal, all verified in one file on JDK 26: a local variable (`var _ =
sideEffect();`), an enhanced `for` (`for (var _ : list) n++;`), the initialiser of a basic `for`, a
try-with-resources resource (`try (var _ = open()) {…}`), a `catch` parameter, and a lambda
parameter, more than once if you like (`(_, _) -> 0`). Where it isn't: a method parameter or a
field, both `underscore not allowed here` (verified). Those names are part of an API or an
object's state, so nothing forces you to write one you don't use.

**And what `_` doesn't catch.** Name the unused component `label` instead, and compile with
`-Xlint:all`: **no warning** (verified). `javac` has never warned about unused variables, and
still doesn't. `_` is a promise to the reader, not a check.

The history, verified with JDK 26's `javac` at each `--release`:

| `--release` | `int _ = 1;` |
| --- | --- |
| 8 | warning: `as of release 9, '_' is a keyword, and may not be used as an identifier` |
| 9 to 21 | error: `unnamed variables are not supported in -source N` |
| 22 and later | compiles, and reading `_` afterwards is `underscore not allowed here` |

Java 21 had it as a preview (JEP 443), which JDK 26's `javac` can't show, because it only enables
preview features for its own release.

## Part one: `patterns/Unnamed.java`, five TODOs

Delete the `@Disabled` in `UnnamedTest`: **8 tests, all red**, every one from a TODO stub
(verified).

**A correction to the old version, and to the class's own javadoc:** both say `_` is legal in
"five places". The six declaration forms above, plus unnamed patterns, is more than five. The
TODOs cover three of them (record pattern components, `catch` and a lambda parameter), and that
is the honest selection: they're the ones you'll meet.

1. **TODO 1 and 2, one commit.** TODO 1 is the one to push back on. Bind the label to `_`, then
   rename it to `label` and leave it unused, and confirm the compiler doesn't care either way.
   Write down what `_` bought. "A reader doesn't have to scan the arm to check whether `label` is
   used" is real, and small.
2. **TODO 3, `catch (NumberFormatException _)`.** This one has no counter-argument: a `catch` must
   name its parameter, and before Java 22 the best you could do was call it `ignored` and hope.
3. **TODO 4, the lambda.** `Map.forEach` must take two parameters even when you want one.
4. **TODO 5, the nested pattern.** This is where `_` stops being cosmetic. Without it, you invent
   two names a reader then has to check are unused. At one level of nesting, record patterns are
   arguably not worth it ([doc 03](03-pattern-matching.md) asks you to say so); at two, `_` is
   what keeps them readable. **The feature exists for record patterns. The `catch` is a bonus.**

## Part two: the one old-code target

**A correction to the old version:** it said the old code has almost no target for `_`, and that
the only candidate, an ignored `catch` parameter, didn't apply because the two in `guardedLock/`
are bugs. The bugs part is right: `Consumer.java:28`, `Drop.java:23` and `:44`, and
`Safelock.java:83` are empty `catch (InterruptedException e) {}` blocks, and [doc 07](07-classic-concurrency.md)
makes you fix them, not tidy them. Putting `_` there would make a swallowed interrupt look
deliberate.

But there is a real one. `concurrency/SimpleThreads.java:31`:

    } catch (NumberFormatException e) {
        System.err.println("Argument must be an integer.");
        System.exit(1);
    }

The exception is handled, and `e` is never read. It's exactly TODO 3, in code from 2014. Every
other `catch` in `src/main` either reads its exception or is one of the bugs above (`grep`).

`SimpleThreads` has no test, and **this change doesn't need one**. Compile it both ways, with
`-g -parameters --release 26` as Maven does (verified):

- `javap -c` is **identical**: not one instruction changes.
- the class file differs in **one line of debug information**: the `LocalVariableTable` entry
  `e  Ljava/lang/NumberFormatException;` is gone.
- compiled without `-g`, the two class files are **byte-identical**.

That's [`../testing-first.md`](../testing-first.md)'s "when the compiler can prove it instead"
case, by the margin of a debug table. One commit, with that evidence in the message.

## Part three: primitive patterns, the preview that hasn't finished

All verified on JDK 26 with `--enable-preview --source 26`, in a scratch file. Without the flag,
`javac` says `primitive patterns are a preview feature and are disabled by default`.

    int x = 300;     x instanceof byte b      // false: (byte) 300 is 44, so not exact
    int y = 100;     y instanceof byte b      // true, b = 100
    double 2.0       instanceof int i         // true, i = 2
    double 1.5       instanceof int i         // false
    double -0.0      instanceof int i         // false, although (int) -0.0 is 0
    int 16_777_217   instanceof float f       // false: (float) gives 1.6777216E7

The cast truncates; the pattern refuses. `-0.0` is the one to remember: the cast gives 0, but the
sign is lost, so it isn't exact.

And `switch` over a primitive:

    static String size(long value) {
        return switch (value) {
            case int i when i > 100 -> "big int";
            case int i              -> "int";
            case long l             -> "long";
        };
    }

`size(5)`, `size(500)` and `size(1L << 40)` are `"int"`, `"big int"`, `"long"` (verified). **A
correction to the old version:** it showed this switch without a type, and as an `int` selector it
doesn't compile: `case int i` and `case long l` are then both unconditional, and `javac` says
`duplicate unconditional pattern` (verified). The selector must be wider than the first pattern.
Switching on a `boolean`, which [doc 03](03-pattern-matching.md) ran into on `Door`, is part of
the same preview.

The five previews (from the JEP pages, since only JDK 25 and 26 are installed): JEP 455 in 23, 488
in 24, 507 in 25, 530 in 26 and 532 in 27. **The old version listed four**: it skipped 488. JEP 532
says 27's preview is "without change". JEP 530, in 26, tightened the dominance checks, which is
probably where that `duplicate unconditional pattern` comes from.

**Five previews is a signal, not a delay.** It's the last missing piece of pattern matching
(records, sealed types, record patterns and switch patterns have all shipped), and what remains
is the exactness question above, for every pair of types. String templates were previewed twice
and withdrawn. [Doc 33](33-preview-shelf.md) is about reading that difference.

There's no compiled exercise here, on purpose. To try it inside the repo, you'd need
`mvn -Ppreview verify`, and the pom's own comment says why preview is never on by default: a
class compiled with it won't load on any other JDK version.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: `_` stopped being a legal name in Java 9: what did that do to 2015 code that used it as a
lambda parameter, and was it an acceptable price? If `_` means "I don't use this", what's the
argument for ever naming an unused `catch` parameter? Does `var _ = call();` read as "I'm
discarding this result" or as noise, and would you accept it in review? A primitive pattern
refuses where a cast truncates: which would you rather debug?
