# 28. Flexible constructor bodies

**Java 25** · stage 17 of [the release-ordered track](INDEX.md) · **needs `--release 25` or newer**

## Where it lands

New code: **`modern/Validated.java`**, three TODOs. The smallest stage in the track — one rule
changed — and it is here because the rule it changed had been there since Java 1.0.

## The rule that changed

Until Java 25, `super(...)` or `this(...)` had to be the **first statement** in a constructor.
JEP 513 allows statements before it, provided they do not read the instance under construction.

    public NonNegative(int size) {
        if (size < 0) throw new IllegalArgumentException("negative size: " + size);
        super(size);                                  // legal from Java 25
    }

## The exercise

**TODO 1** is four lines. **The test is the interesting part:**

    @Test
    void the_base_constructor_never_runs_for_a_rejected_argument() {
        assertThrows(IllegalArgumentException.class, () -> new Validated.NonNegative(-1));
        assertEquals(0, Validated.Measured.constructions());
    }

`Measured` counts its own invocations, so the test can prove the **ordering** rather than just the
outcome. Validate after `super(size)` and the base constructor has already run and the counter is
1. Only a check before `super(...)` keeps it at 0. **Before Java 25 that test could not be passed
without the trick in TODO 2.**

**TODO 2 is the pre-25 workaround**, and writing it is what makes the feature feel worth having:

    public Legacy(int size) {
        super(requireNonNegative(size));              // the only way, before 25
    }

    private static int requireNonNegative(int size) { ... }

It works. It has always worked. It costs a `static` helper per validated argument, it has to be
`static` because `this` does not exist yet, and with three arguments to validate you get three
helpers and a `super(a(x), b(y), c(z))` call that nobody enjoys reading. `Objects.requireNonNull`
is this pattern blessed into the JDK, which is why it exists at all.

**TODO 3 is the restriction, and it is what makes the feature safe.** Try this:

    public NonNegative(int size) {
        System.out.println(this.size);                // the compiler stops you
        super(size);
    }

The *prologue* — the code before `super(...)` — may not read fields, call instance methods, or
use `this` in any way. It can compute, validate, throw, and assign to its own locals. **Write the
error message down**, because that restriction is the entire reason this is not a hole in object
initialisation: there is no window in which a partially built object is observable.

Which means it also finally gives a clean answer to a classic problem: an overridable method
called from a superclass constructor sees uninitialised subclass fields. The prologue cannot make
that worse, because it cannot touch `this` at all.

## What got worse

- _(fill this in)_

Prompts: this was previewed three times (JEP 447, 482, 492) for one grammar change — what was
hard about it? If `Objects.requireNonNull` inside `super(...)` already worked, is this a feature
or a tidy-up, and would you mention it in an interview as either? Does allowing statements before
`super(...)` make constructors a place people now put more logic, and is that a problem you would
raise in review? What does a record's compact constructor do here, and is it the same feature?
