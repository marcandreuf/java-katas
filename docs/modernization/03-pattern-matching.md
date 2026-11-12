# 03. Pattern matching

**Java 16** (`instanceof` patterns) · **Java 21** (patterns in `switch`, record patterns) · concept
stage 03 of [the concept order](00-start-here.md) · no build change

> **No safety net:** `DecimalToBinary`, `Order` have no tests. Write them first, in a separate
> commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why the order
> matters.

## What this stage is actually about

Five words, defined once:

- a **pattern** is a test and a declaration in one. `o instanceof Order ord` asks "is `o` an
  `Order`?" and, if it is, gives you `ord`, already cast.
- a **type pattern** is that form: a type followed by a name. `Order ord` in an `instanceof`, or
  `case Order ord ->` in a `switch`.
- a **binding variable** is the name the pattern introduces (`ord`).
- a **record pattern** takes a record apart: `Order(String name, double code)` matches an `Order`
  and binds its components directly, so you never call the accessors. Patterns nest, so
  `Pair(Ok(var x), Ok(var y))` is legal.
- a **guard** is a condition on a `case`: `case Failed(var m, int n) when n > 3 ->`. The case only
  matches if the pattern matches *and* the condition is true.

That gives one rule, and everything below follows from it: **a binding variable exists only where
the compiler can prove the test passed.** Scoping, dominance, why guards don't count toward
exhaustiveness: each is that rule applied to a different shape of code.

Doc 01 gave you records and doc 02 gave you sealed types. This is the stage that uses them: the
sealed type tells the compiler what the cases are, and patterns are how you take each case apart.

## The worked example

### `instanceof`: the cast you no longer write

Here is the shape every hand-written `equals` had before Java 16:

    public boolean equals(Object other) {
        if (!(other instanceof Key)) return false;
        Key key = (Key) other;            // you've just checked the type; now you say it again
        return key.id == id;
    }

The repo's old code never wrote an `equals`, so the newer `equality/Keys.java:44` is the in-repo
example of the pattern form:

    return other instanceof EqualsOnly key && key.id == id;

The test and the cast are one step, and **`key` only exists on the right of `&&`**, because that
is the only place the test is known to have passed. The same rule explains the negated form,
which looks wrong the first time (verified):

    if (!(o instanceof Order ord)) return "not an order";
    return ord.name();                     // compiles: we only get here if the test passed

    o instanceof Order ord || ord.name().isEmpty()   // error: cannot find symbol

On the right of `||` the test has *failed*, so there is no `ord`. And you can't test against a
generic type the JVM can't check at runtime: `o instanceof List<String> l` is an error,
`Object cannot be safely cast to List<String>` (verified).

### `switch`: from types to shapes

A sealed type that is not the exercise, with a nested variant to show what nesting buys:

    sealed interface R {
        record Ok(String payload) implements R {}
        record Failed(String message, int attempts) implements R {}
        record Pair(R first, R second) implements R {}
    }

    static String describe(R r) {
        return switch (r) {
            case R.Failed(String m, int n) when n > 3 -> "gave up:" + m;
            case R.Failed(String m, int n)            -> "failed:" + m + ":" + n;
            case R.Ok(var p)                          -> "ok:" + p;
            case R.Pair(R.Ok(var x), R.Ok(var y))     -> "two oks:" + x + y;
            case R.Pair p                             -> "pair";
        };
    }

Verified outputs:

    describe(new Failed("boom", 5))                     // "gave up:boom"
    describe(new Failed("boom", 3))                     // "failed:boom:3"
    describe(new Pair(new Ok("a"), new Ok("b")))        // "two oks:ab"
    describe(new Pair(new Ok("a"), new Failed("b", 1))) // "pair"

Four things to see in it:

- **`var` works inside a record pattern** and takes the component's type. Write the type out when
  it helps the reader; use `var` when the type is obvious.
- **The nested `Pair` case is the one that would be ugly without patterns**: two `instanceof`
  checks and two casts, or four accessor calls. One level of nesting is where record patterns
  start to pay.
- **Order matters, and the compiler enforces it.** Swap the two `Failed` lines so the unguarded
  one comes first, and you get `this case label is dominated by a preceding case label`
  (verified). The unguarded case catches every `Failed`, so the guarded one could never run. The
  same goes for `Pair p` above the nested `Pair(...)`: specific first, general last.
- **A guarded case doesn't count toward exhaustiveness.** Delete the unguarded `Failed` line and
  the switch stops compiling with `does not cover all possible input values` (verified). The
  compiler can't know whether `n > 3` is always true, so `Failed` with three attempts is still
  unhandled.

## Part one: `DecimalToBinary`, which is not pattern matching

This file is here because [the DSA track](../dsa/INDEX.md) hands it to this doc, not because it
has a pattern in it. It doesn't have one. **Do it first anyway**: it is the cleanest example in
the repo of the testing-first sequence, and the bug is worse than its reputation.

It is easy to read as "prints hex". It doesn't. It prints each base-16 digit as a *decimal
number*, one after another (verified against the file as it stands):

| Input | Prints | Real hex | Real binary |
| --- | --- | --- | --- |
| 25 | `19` | `19` | `11001` |
| 10 | `10` | `a` | `1010` |
| 255 | `1515` | `ff` | `11111111` |
| 0 | *(nothing)* | `0` | `0` |
| -5 | *(nothing)* | `fffffffb` | 32 digits |

25 is the input in its own `main`, and it is the one input where the output looks like hex. Every
digit above 9 comes out as two characters, so the output can't be decoded at all.

Four commits, in this order:

1. **Extract.** `printBinaryFormat` returns `void` and prints, so there is nothing to assert. Pull
   the conversion out into a method that returns a `String` and leave the printing in the old
   method. [`../testing-first.md`](../testing-first.md) walks through the same move on
   `FindDups`.
2. **Characterize.** Write the table above as a test, against the *current* output. `"1515"` for
   255 is the assertion that should make you uncomfortable, and that is the point: it records the
   bug before you fix it.
3. **Fix the base.** Change 16 to 2 and update the assertions. **Then try 33554432** (2²⁵):
   `ArrayIndexOutOfBoundsException: Index 25 out of bounds for length 25` (verified). In base 16,
   25 slots were more than enough. In base 2 a positive `int` needs up to 31. Fixing the first bug
   uncovered a second one, which your test finds only if you give it an input that big. Add the
   case, and fix the array size.
4. **Decide what 0 and negative numbers should do, and write it down.** Today they print nothing.
   `Integer.toBinaryString` gives `"0"` for 0 and the 32-bit two's-complement form for negatives.
   Replacing the whole method with that call is a legitimate final commit, as long as you decide
   whether that answer for negatives is the one you want.

## Part two: patterns on code you already have

### 1. Rewrite your `route()` from doc 02 with record patterns

Doc 02 only showed type patterns, so your `ToolCallResult.route()` probably looks like
`case Failed f -> ... f.message() ... f.attempts()`. Rewrite each case as a record pattern, as in the worked example, and keep
`ToolCallResultTest` green.

Then **say honestly which version reads better.** With one level of records and one or two
components each, the honest answer may be "neither". The nested `Pair` case above is where the
record pattern clearly wins, and `ToolCallResult` has no nesting.

Then try a guard, and find out what it costs:

- Add `case Failed(var m, int n) when n > 3 -> "gave up:" + m` **after** the unguarded `Failed`
  case, and read the dominance error.
- Move it **before**, and it compiles.
- Delete the unguarded `Failed` case, and read the exhaustiveness error.

The test only covers `attempts == 3`, so it can't tell the difference. Either revert the guard,
or keep it and write the test for `attempts == 5` first.

### 2. `case null`, the detail to actually learn

Doc 02 showed that `route(null)` throws `NullPointerException`. A pattern `switch` handles null in
exactly one way: a `case null` label. Add one to `route()` and find out where it can go. It
compiles anywhere among the cases, and it can be combined with `default` as
`case null, default ->` (both verified).

Then compare with the old `switch`, which has no `case null` at all. Given a null `String`, it
throws `NullPointerException` **before `default` gets a chance to run** (verified). If you
always thought `default` caught everything, this is where that stops being true. A pattern
`switch` doesn't change the default behaviour (null is still an NPE unless you say otherwise),
but it gives you a way to say otherwise.

### 3. `Order`, where a record pattern buys nothing

Once `Order` is a record (doc 01), write

    switch (order) { case Order(var name, var code) -> ... }

next to `order.name()` and `order.code()`. It compiles with no `default`, because a record pattern
whose components all match anything covers every `Order` (verified). **And it buys nothing**: one
type, one level, no decision to make. Take it out again, and write down that a pattern with
nothing to choose between is just a longer way to call two accessors.

## Then the counter-example: `Door.toString()`

`Door.java:19-24` is a four-line `if/else` on a `boolean`. Pattern matching has nothing to work
on here: there is one type, and the decision is a `true`/`false` value, not a shape. The obvious
modern form is

    switch (open) { case true -> "open"; case false -> "close"; }

and at `--release 26` it doesn't compile: `primitive patterns are a preview feature and are
disabled by default` (verified). Switching on a `boolean` is
[doc 21](21-unnamed-and-primitive-patterns.md)'s subject, and it is still preview. So the choice
is a ternary (`return open ? "open" : "close";`) or an enum, and with two states and no data the
ternary wins. Doc 02 made the same call from the other side.

Two things to do before you change it:

- **Nothing asserts `toString()` today.** `The100DoorsCyberDojoTest` prints it but doesn't check
  it. Add the assertion first, against what it returns now, which is `"close"`, not `"closed"`.
  If that's a bug, fixing it is a separate commit.
- It has no `@Override`. Add one; it costs nothing, and it is how the compiler catches a
  misspelled `toSting()`.

[Doc 04](04-switch-and-text-blocks.md) comes back to `Door.toString()` from the `switch` side.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: at what nesting depth does a record pattern stop being readable? 

- What does a long pattern switch do to a diff when one variant changes?
    1 or 2 levels max for readability.

- A record pattern names every component in order: what happens to every `case` that takes `Failed` apart when 
someone adds a third component to it, and how does that compare with the accessor version?
    Adding a variant to the record allowed types can have wider effect on all many files. At the same time we get more
    information about potential issues before compile time as the IDEs can help us analyze the exhaustiveness of a
    switch statement or expression.

  - Guards move logic into `case` labels: when is a `when` clause clearer than an `if` inside the case body, 
  and when is it hiding a decision?
    We should use when clause for routing outcomes. So different case clauses generate different flows or results. 
    
