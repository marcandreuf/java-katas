# 28. Flexible constructor bodies

**Java 25** (JEP 513, final) · stage 17 of [the release-ordered track](INDEX.md) · needs
`--release 25` or newer

New code with its tests already written: **`modern/Validated.java`**, three TODOs, and
`ValidatedTest`, which is `@Disabled` until you begin. This is the smallest stage in the track:
one rule changed. It's here because that rule had been there since Java 1.0.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `NonNegative`](#exercise-1-todo-1-validate-in-the-prologue) | Reject a negative size before the base constructor runs. One commit. | T1, T3 | `ValidatedTest`, already written: delete the `@Disabled`. |
| [2. `Legacy`](#exercise-2-todo-2-the-pre-25-workaround) | Write the old static-helper workaround, and what it cost. One commit. | T2, T3 | The same test class. |
| [3. The restriction](#exercise-3-todo-3-the-restriction) | Read a field before `super(...)` in a scratch copy, and write down the error. No commit. | T4 | None: it doesn't compile. |
| [4. What got worse](#exercise-4-fill-in-what-got-worse) | Write down what the feature costs. | all | None. |
| [5. Review questions](#exercise-5-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the five sections in order: each one builds on the one before. Here is the whole story in one
paragraph, so you know where it's going.

> Building an object runs code in a fixed order: the superclass constructor first, then the
> subclass's field initializers, then the rest of the subclass constructor (T1). Because the
> superclass runs first, a superclass constructor that calls an overridable method lets the
> subclass see its own fields before anyone has set them, and a `final` field can be caught holding
> `null` (T2). Since Java 1.0 the language made `super(...)` the first statement, so there was
> nowhere to set those fields earlier. Java 25 allows statements before it, under one rule: write,
> but never read, the half-built object (T3). Everything that would break that rule is a compiler
> error (T4). And the feature needs `--release 25` (T5).

Every code sample below is a complete program unless it says otherwise. Save it as the class name
plus `.java` and run it with `java Name.java`; no build needed. Outputs marked *verified* were run
on JDK 26.

### T1. The order an object is built in

`new Sub()` doesn't run `Sub`'s constructor top to bottom. It runs pieces of three things in a
fixed order:

1. whatever `Sub()` does **before** `super(...)` (from Java 25; before that, nothing),
2. the superclass constructor, `Base()`, all of it,
3. `Sub`'s **field initializers** (the `= 1` in `int withInit = 1;`), in the order they're written,
4. the rest of `Sub()`, after `super(...)`.

This program prints each step as it happens:

```java
public class Steps {
    static class Base {
        Base() {
            System.out.println("2. Base() body runs");
        }
    }

    static class Sub extends Base {
        int withInit = log("3. field initializer runs", 1);
        int noInit;

        Sub() {
            System.out.println("1. Sub() prologue runs, before super()");
            super();
            System.out.println("4. Sub() body after super(): withInit=" + withInit);
            noInit = 2;
        }

        static int log(String step, int value) {
            System.out.println(step);
            return value;
        }
    }

    public static void main(String[] args) {
        new Sub();
    }
}
```

```text
1. Sub() prologue runs, before super()                  (verified 2026-11-23)
2. Base() body runs
3. field initializer runs
4. Sub() body after super(): withInit=1
```

Two names for those parts, used throughout this doc: the **prologue** is everything in a
constructor before its `super(...)` or `this(...)` call, and the **epilogue** is everything after
it. That call itself is an **explicit constructor invocation**. Until Java 25 it had to be the very
first statement, so the prologue was always empty and step 1 didn't exist.

Notice where step 3 is. A field initializer runs **after** `super(...)` returns. So a field that
has one isn't really the subclass's to set until then: anything written to it earlier would be
overwritten by the initializer a moment later.

### T2. The bug the old order caused

The order in T1 has a trap in it. An **overridable method** is a non-`final`, non-`private`
instance method. If the superclass constructor calls one, the JVM runs the *subclass's* version,
during step 2, before steps 3 and 4 have set the subclass's fields.

```java
public class TooEarly {
    static class Base {
        Base() {
            System.out.println("Base sees: " + describe());
        }
        String describe() { return "base"; }
    }

    static class Old extends Base {
        private final String label;
        private int retries = 3;                          // has an initializer

        Old(String label) {
            super();                                      // Base() runs first...
            this.label = label;                           // ...and only then is label set
        }

        @Override
        String describe() { return "label=" + label + ", retries=" + retries; }
    }

    static class New extends Base {
        private final String label;
        private int retries = 3;

        New(String label) {
            this.label = label;                           // legal from Java 25: before super()
            super();
        }

        @Override
        String describe() { return "label=" + label + ", retries=" + retries; }
    }

    public static void main(String[] args) {
        Old old = new Old("x");
        System.out.println("after Old():  " + old.describe());
        New neu = new New("x");
        System.out.println("after New():  " + neu.describe());
    }
}
```

```text
Base sees: label=null, retries=0                        (verified 2026-11-23)
after Old():  label=x, retries=3
Base sees: label=x, retries=0
after New():  label=x, retries=3
```

`Old` is the classic bug. `Base()` calls `describe()`, which runs `Old`'s version, which reads
`label` before `Old`'s constructor has assigned it. **A `final` field, observed holding `null`.**
Nothing threw; the wrong value just went wherever `Base` sent it. `New` assigns `label` in the
prologue, before `super()`, so `Base` sees `x`.

Look at `retries` too. It's `0` during `Base()` in *both* classes, because its initializer is step 3
and that hasn't happened yet. The prologue can't fix that one, and T3 says why.

The old version of this doc showed the same result as fragments (verified on JDK 26):

```java
new Old("x");     // Base sees: label=null
new New("x");     // Base sees: label=x
```

**A correction to the old version:** it said the prologue "may not ... use `this` in any way" and
so "cannot make that worse". The first half is wrong, and the second half undersells the feature.
The prologue *can* assign fields, and that's exactly what fixes the bug. It can't assign a field
that has an initializer, because that initializer only runs once `super()` has returned.

### T3. The new rule: write, never read

While the prologue runs, the object already exists, but its superclass part hasn't been initialised
yet. The JLS calls this an **early construction context**. Java 25 allows statements there under
one rule, and everything else in this doc follows from it:

> **In the prologue, you may write to the object under construction but never read it, and you may
> only write the fields that have no initializer.**

Nothing can observe a half-built object, and that's what makes allowing statements before
`super(...)` safe. Code that doesn't touch the object at all is fine: locals, arithmetic, calls to
`static` methods, `if`, loops, and `throw`.

That opens up one common use straight away: **preparing the arguments for `super(...)` or
`this(...)`**. Before Java 25, the arguments had to be single expressions, so any work on them had
to be squeezed into a `static` method called inside the parentheses. Now it can be ordinary
statements:

```java
public class Rect {
    final int width, height;

    Rect(int width, int height) {
        this.width = width;
        this.height = height;
    }

    // "3x4" -> Rect(3, 4). The prologue computes the arguments, then delegates.
    Rect(String spec) {
        int x = spec.indexOf('x');
        int w = Integer.parseInt(spec.substring(0, x));
        int h = Integer.parseInt(spec.substring(x + 1));
        this(w, h);
    }

    public static void main(String[] args) {
        Rect r = new Rect("3x4");
        System.out.println(r.width + " by " + r.height);
    }
}
```

```text
3 by 4                                                  (verified 2026-11-23)
```

The other use is the one exercises 1 and 2 are about: deciding *whether* the superclass
constructor should run at all.

### T4. What the compiler still stops

Everything that would break T3's rule is a compile error. This class breaks it four ways in one
prologue (it isn't meant to run; compile it with `javac Prologue.java`):

```java
public class Prologue {
    static class Base {
        Base(int size) { }
    }

    static class Sub extends Base {
        int withInit = 1;
        int size;

        Sub(int s) {
            helper();
            Object me = this;
            this.withInit = 3;
            if (s == 0) return;
            this.size = s;
            int t = s * 2;
            super(t);
        }

        void helper() { }
    }
}
```

```text
Prologue.java:11: error: reference to helper() may only appear after an explicit constructor invocation   (verified 2026-11-23)
            helper();
            ^
Prologue.java:12: error: reference to this may only appear after an explicit constructor invocation
            Object me = this;
                        ^
Prologue.java:13: error: assignment to initialized field 'withInit' may only appear after an explicit constructor invocation
            this.withInit = 3;
                ^
Prologue.java:14: error: 'return' not allowed before explicit constructor invocation
            if (s == 0) return;
                        ^
4 errors
```

Lines 15 and 16, `this.size = s;` and `int t = s * 2;`, raised nothing. The full rule, as javac 26
reports it (each verified):

| In the prologue | javac says |
| --- | --- |
| `helper();` (an instance method) | `reference to helper() may only appear after an explicit constructor invocation` |
| `Object me = this;` | `reference to this may only appear after an explicit constructor invocation` |
| `this.withInit = 3;` (the field has `= 1`) | `assignment to initialized field 'withInit' may only appear after an explicit constructor invocation` |
| `return;` | `'return' not allowed before explicit constructor invocation` |
| `this.size = s;` (no initializer) | compiles |
| `int t = s * 2; if (t < 0) throw ...;` | compiles: locals, arithmetic and `throw` are fine |

One case is missing from the table on purpose: reading a field of `this`. That's exercise 3.

### T5. Release levels

The feature is final in Java 25. On earlier releases the prologue doesn't compile at all:
`javac --release 24` on JDK 26 rejects `Rect` from T3 with:

```text
Rect.java:14: error: flexible constructors is not supported in -source 24   (verified 2026-11-23)
        this(w, h);
            ^
```

And adding `--enable-preview` gives `invalid source release 24 with --enable-preview (preview
language features are only supported for release 26)` (verified). The three previews before it
(JEP 447, 482 and 492, in 22, 23 and 24) are listed in [the index](INDEX.md) and **not verified
here**, because no JDK 22 to 24 is installed.

## Practice

### What's in `Validated.java`

`Validated.java` has a base, `Measured`, that counts how many times its constructor ran, and two
subclasses that must reject a negative size. Delete the `@Disabled`: one test passes already
(`a_valid_size_is_accepted`) and four fail (verified). `NonNegative` doesn't validate yet, and
`Legacy` throws `UnsupportedOperationException`.

The test worth reading is `the_base_constructor_never_runs_for_a_rejected_argument`. It doesn't
just check that `-1` is rejected. It checks that `Measured`'s constructor **never ran**, by asserting
the counter is still 0. If you validate after `super(size)`, the exception still comes out, but
the base has already run once (verified: `base ran 1 time(s)`). The test proves the *order* (T1),
not just the outcome.

### Exercise 1: TODO 1, validate in the prologue

**TODO 1, `NonNegative`.** Validate in the prologue (T3). Two tests go green: the rejection, and the
counter at 0.

**Done when:** `a_negative_size_is_rejected` and
`the_base_constructor_never_runs_for_a_rejected_argument` pass, in one commit.

### Exercise 2: TODO 2, the pre-25 workaround

**TODO 2, `Legacy`.** The pre-25 way: the check lives in a `static` method that wraps the
argument, `super(requireNonNegative(size))`. Write it, and the last two tests go green. Then
write down what it cost: why the helper has to be `static`, and what a constructor with three
validated arguments would look like.

**A correction to the old version:** it said `Objects.requireNonNull` is the `static`-helper
pattern "blessed into the JDK, which is why it exists at all". The JDK's own javadoc says it's
"designed primarily for doing parameter validation in methods and constructors", and its example
is `this.bar = Objects.requireNonNull(bar);`, not a `super(...)` argument (verified, in the JDK
26 source). It *returns* its argument, and that's what lets it sit inside `super(...)`, but that
isn't the only reason it exists.

**Done when:** all five `ValidatedTest` tests pass, and the two written answers are in the commit
message.

### Exercise 3: TODO 3, the restriction

**TODO 3.** In a scratch copy, read a field of `this` before `super(...)`. Write down the exact
error, which isn't in T4's table. Then don't commit it.

**Done when:** you've written down the error, and said in a sentence why that restriction is what
makes the feature safe (T3's rule).

### Exercise 4: fill in "What got worse"

- _(fill this in)_

Prompts: this was previewed three times for one grammar change. Given the table above, what was
hard about it? If `super(requireNonNegative(size))` already worked, is this a feature or a tidy-up,
and would you mention it in an interview as either? Now that statements can go before `super(...)`,
will people put more logic in constructors, and would you raise that in review? A record's compact
constructor also runs code before the fields are set: is it the same idea, and what can it do
that this can't? ([Doc 01](01-records.md) covers records.)

### Exercise 5: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. In `new Sub()`, put these in order: `Base()`'s body, `Sub`'s field initializers, `Sub()`'s
   prologue, `Sub()`'s epilogue. (T1)
2. What are the prologue and the epilogue of a constructor? (T1)
3. Why did `Base` see `label=null` in `Old`, even though `label` is `final`? (T2)
4. In `New`, `label` was right during `Base()` but `retries` was still `0`. Why? (T2, T1)
5. State the rule for the prologue in one sentence. (T3)
6. Name three things you may do in a prologue that don't touch the object at all. (T3)
7. Why can't the prologue assign a field that has an initializer? (T1, T4)
8. What does javac say if you call an instance method before `super(...)`? (T4)
9. What does `javac --release 24` say about a prologue? (T5)

**Done when:** you can answer all nine without looking.
