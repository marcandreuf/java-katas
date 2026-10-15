# 21. Unnamed variables and patterns, and primitive patterns

**Java 22** (unnamed) · **preview through 26** (primitive) · stage 10 of
[the release-ordered track](INDEX.md) · **needs `--release 22`+**, and `<java.release>` is now 25

## Where it lands

New code: **`patterns/Unnamed.java`**, five TODOs. Do [doc 02](02-sealed-types.md) and
[doc 03](03-pattern-matching.md) first — this builds directly on both.

There is almost no target in the old code, **and that is the finding.** `_` is for things the
language forces you to name and you do not use. The old code's unused variables are not of that
kind: they are variables somebody declared and then stopped using, which `_` does not help with
and which the compiler never complained about either.

The one honest old-code target is `catch` with an ignored exception — and the two in
`guardedLock/` are *not* it, because an empty `catch (InterruptedException e) {}` is a bug that
[doc 07](07-classic-concurrency.md) makes you fix properly rather than tidy up.

## The exercise

The five places `_` is legal, all in one file:

    case Circle(double r, _)                      // TODO 1, record pattern component
    case Rect(double w, double h, _)
    catch (NumberFormatException _)               // TODO 3, the unarguable one
    map.forEach((_, value) -> ...)                // TODO 4, lambda parameter
    for (var _ : shapes)                          // enhanced-for variable
    var _ = sideEffectingCall();                  // local variable

**TODO 3 is the case with no counter-argument.** `catch (SomeException e)` with an unused `e` is
the most common unused variable in Java, every linter flags it, and the only previous fix was to
name it `ignored` and hope the next reader believed you. `catch (NumberFormatException _)` says it
in the language.

**TODO 1 is the case where you should push back.** Bind the label to `_`, then name it `label` and
leave it unused, and notice the compiler is equally happy with both. So what did `_` buy? Only
that a reader does not have to scan the body to check whether `label` is used. That is real but
small, and "it is a readability feature, not a correctness one" is the right summary.

**TODO 5 is where it stops being cosmetic:**

    if (drawing instanceof Drawing(Circle(double r, _), _))

Without `_` you must invent two names for things you do not want, and every reader has to verify
they are unused. At one level of nesting record patterns are already arguably not worth it
([doc 03](03-pattern-matching.md) asks you to say so if they are not); at two levels, `_` is what
keeps them readable. **The feature exists for record patterns, and the `catch` case is a bonus.**

## Primitive types in patterns: the preview that will not finish

`case int i`, `instanceof byte b`, and `switch` over a primitive with exhaustiveness. JEP 455 and
then 507, 530, 532 — **five previews, Java 23 through 27, still not final.**

    // what it will allow, when it lands
    switch (value) {
        case int i when i > 100 -> "big";
        case int i -> "small";
        case long l -> "long";
    }

Two things are worth knowing now, and neither requires writing any:

1. **It is the last piece of pattern matching**, which is why it keeps being re-previewed rather
   than withdrawn. Records, sealed types, record patterns and switch patterns all shipped; only
   primitives are missing, and the hard part is the **unsafe narrowing** question —
   `case byte b` matching an `int` of 300 must not silently truncate, so the semantics have to
   define exactly when a conversion is "exact".
2. **Five previews is a signal, not a delay.** [Doc 21's](INDEX.md) neighbour on the index,
   String templates, was previewed twice and withdrawn. Lazy Constants is on its third preview
   under its second name. The rule the index states applies here: *preview is a real warning.*
   [Stage 22](INDEX.md) is about reading those signals.

To try it you need the `preview` profile — `mvn -Ppreview verify` — and a JDK whose version
matches `<java.release>`. It is not part of any compiled exercise here for exactly the reason
above.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: `_` was a legal identifier until Java 9 deprecated it and Java 22 took it — what does
that do to code written in 2015 that used `_` as a lambda parameter name, and was that an
acceptable price? If `_` means "I do not use this", what is the argument for ever naming an
unused `catch` parameter? Does `var _ = call();` read as "I am discarding this result" or as
noise, and would you accept it in review?
