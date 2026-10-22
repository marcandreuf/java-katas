# 28. Flexible constructor bodies

**Java 25** (JEP 513, final) · stage 17 of [the release-ordered track](INDEX.md) · needs
`--release 25` or newer

New code with its tests already written: **`modern/Validated.java`**, three TODOs, and
`ValidatedTest`, which is `@Disabled` until you begin. This is the smallest stage in the track:
one rule changed. It's here because that rule had been there since Java 1.0.

## What this stage is actually about

Five words, defined once:

- an **explicit constructor invocation** is a `super(...)` or `this(...)` call inside a
  constructor. Until Java 25 it had to be the first statement.
- the **prologue** is everything before that call. The **epilogue** is everything after it.
- while the prologue runs, the object already exists, but its superclass part hasn't been
  initialised yet. The JLS calls this an **early construction context**.
- a **field initializer** is the `= 1` in `int withInit = 1;`. It runs right after `super(...)`
  returns, so a field that has one isn't really yours to set until then.
- an **overridable method** is a non-`final`, non-`private` instance method. A superclass
  constructor that calls one runs the *subclass's* version, before the subclass constructor has
  done its work.

That gives one rule, and everything below follows from it: **in the prologue, you may write to the
object under construction but never read it, and you may only write the fields that have no
initializer.** Nothing can observe a half-built object, and that's what makes allowing statements
before `super(...)` safe.

## The worked example: the bug this fixes

A superclass constructor that calls an overridable method (verified on JDK 26):

    static class Base {
        Base() { System.out.println("Base sees: " + describe()); }
        String describe() { return "base"; }
    }

    static class Old extends Base {
        private final String label;
        Old(String l) { super(); this.label = l; }
        @Override String describe() { return "label=" + label; }
    }

    static class New extends Base {
        private final String label;
        New(String l) { this.label = l; super(); }    // legal from Java 25
        @Override String describe() { return "label=" + label; }
    }

    new Old("x");     // Base sees: label=null
    new New("x");     // Base sees: label=x

`Old` is the classic bug. `Base()` calls `describe()`, which runs `Old`'s version, which reads
`label` before `Old`'s constructor has assigned it. A `final` field, observed holding `null`. `New`
assigns it in the prologue, and the bug is gone.

**A correction to the old version:** it said the prologue "may not ... use `this` in any way" and
so "cannot make that worse". The first half is wrong, and the second half undersells the feature.
The prologue *can* assign fields, and that's exactly what fixes the bug. It can't assign a field
that has an initializer, because that initializer only runs once `super()` has returned.

The rest of the rule, as javac 26 reports it (each verified):

| In the prologue | javac says |
| --- | --- |
| `helper();` (an instance method) | `reference to helper() may only appear after an explicit constructor invocation` |
| `Object me = this;` | `reference to this may only appear after an explicit constructor invocation` |
| `this.withInit = 3;` (the field has `= 1`) | `assignment to initialized field 'withInit' may only appear after an explicit constructor invocation` |
| `return;` | `'return' not allowed before explicit constructor invocation` |
| `this.size = s;` (no initializer) | compiles |
| `int t = s * 2; if (t < 0) throw ...;` | compiles: locals, arithmetic and `throw` are fine |

And on earlier releases: `javac --release 24` on JDK 26 rejects a prologue with `flexible
constructors is not supported in -source 24` (verified), and adding `--enable-preview` gives
`invalid source release 24 with --enable-preview (preview language features are only supported
for release 26)` (verified). The three previews before it (JEP 447, 482 and 492, in 22, 23 and
24) are listed in [the index](INDEX.md) and **not verified here**, because no JDK 22 to 24 is
installed.

## Part one: `Validated`

`Validated.java` has a base, `Measured`, that counts how many times its constructor ran, and two
subclasses that must reject a negative size. Delete the `@Disabled`: one test passes already
(`a_valid_size_is_accepted`) and four fail (verified). `NonNegative` doesn't validate yet, and
`Legacy` throws `UnsupportedOperationException`.

The test worth reading is `the_base_constructor_never_runs_for_a_rejected_argument`. It doesn't
just check that `-1` is rejected. It checks that `Measured`'s constructor **never ran**, by asserting
the counter is still 0. If you validate after `super(size)`, the exception still comes out, but
the base has already run once (verified: `base ran 1 time(s)`). The test proves the *order*, not
just the outcome.

Commits, in this order:

1. **TODO 1, `NonNegative`.** Validate in the prologue. Two tests go green: the rejection, and the
   counter at 0.
2. **TODO 2, `Legacy`.** The pre-25 way: the check lives in a `static` method that wraps the
   argument, `super(requireNonNegative(size))`. Write it, and the last two tests go green. Then
   write down what it cost: why the helper has to be `static`, and what a constructor with three
   validated arguments would look like.
3. **TODO 3, the restriction.** In a scratch copy, read a field of `this` before `super(...)`.
   Write down the exact error, which isn't in the table above. Then don't commit it.

**A correction to the old version:** it said `Objects.requireNonNull` is the `static`-helper
pattern "blessed into the JDK, which is why it exists at all". The JDK's own javadoc says it's
"designed primarily for doing parameter validation in methods and constructors", and its example
is `this.bar = Objects.requireNonNull(bar);`, not a `super(...)` argument (verified, in the JDK
26 source). It *returns* its argument, and that's what lets it sit inside `super(...)`, but that
isn't the only reason it exists.

## What got worse

- _(fill this in)_

Prompts: this was previewed three times for one grammar change. Given the table above, what was
hard about it? If `super(requireNonNegative(size))` already worked, is this a feature or a tidy-up,
and would you mention it in an interview as either? Now that statements can go before `super(...)`,
will people put more logic in constructors, and would you raise that in review? A record's compact
constructor also runs code before the fields are set: is it the same idea, and what can it do
that this can't?
