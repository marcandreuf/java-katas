# 02. Sealed types and exhaustiveness

**Java 17** (preview in 15 and 16) · concept stage 02 of [the concept order](00-start-here.md) ·
the `switch` over it is **Java 21** · no build change

## What this stage is actually about

Four words, defined once:

- a **sealed** type is a class or interface that lists the only types allowed to extend it:
  `sealed interface Reply permits Accepted, Rejected`. Any other type that tries to extend it is a
  compile error.
- the **`permits` clause** is that list. If every subtype is in the same file, you can leave the
  clause out and the compiler fills it in.
- each permitted subtype must say how the line continues: **`final`** (stops here), **`sealed`**
  (lists its own subtypes) or **`non-sealed`** (open again, so anyone can extend it). A record is
  implicitly `final`, which is why records and sealed interfaces are usually seen together.
- a `switch` is **exhaustive** when it has a case for every value the input could be. For a sealed
  type the compiler knows the full list, so it can check that for you, and it does.

That gives one rule, and everything below follows from it: **sealing a type turns "did I handle
every case?" from something you remember into something the compiler checks.** A sealed type
with no exhaustive `switch` over it has bought you very little.

Doc 01 gave you records: one type, *and* its data. This stage is the other half: *one of* several
types, each with different data. The two together are what other languages call algebraic data
types. You don't need that name to do the exercise, but you'll hear it.

## The worked example

Something small that is not the exercise: a reply that is either accepted with an id or rejected
with a reason. The two variants carry different data, and that is what makes it a candidate.

**Without `sealed`**, the compiler cannot know what implements `Reply`, because someone may add an
implementation in another file next year:

    interface Reply {
        record Accepted(int id) implements Reply {}
        record Rejected(String why) implements Reply {}
    }

    static String describe(Reply r) {
        return switch (r) {
            case Reply.Accepted a -> "accepted #" + a.id();
            case Reply.Rejected x -> "rejected: " + x.why();
        };
    }
    // error: the switch expression does not cover all possible input values

The only fix is a `default` branch, which means writing code for a case you believe can't happen.

**With `sealed`**, one word changes and the same `switch` compiles with no `default`:

    sealed interface Reply {
        record Accepted(int id) implements Reply {}
        record Rejected(String why) implements Reply {}
    }

Read `case Reply.Accepted a ->` as "if `r` is an `Accepted`, call it `a`, and run this". That is
a **type pattern**, and it is all the pattern syntax this stage needs.
[Doc 03](03-pattern-matching.md) covers patterns properly, including the form that unpacks the
record's components directly: `case Reply.Rejected(String why) -> "rejected: " + why`. Both
forms compile here (verified).

**Now add a third variant**, `record Pending() implements Reply {}`, and the `describe` method
stops compiling, with the same error as before (verified). The compiler has found every `switch`
that has to learn about `Pending`. A `default` branch would have prevented that: with
`default -> "?"` in place, adding `Pending` compiles silently and every pending reply turns into
`"?"` at runtime (verified). **That is why the exercise says no `default`.** The `default` branch
is what switches the check off.

## The exercise: `sealedtypes/ToolCallResult.java`

Nothing in the old code has a type hierarchy whose variants carry different data, so there is
nothing to seal, and this exercise is new code. That fact is worth remembering too: most code does
not need this. `ToolCallResult` has four outcomes for one call, and each carries something
different:

| Variant | Carries |
| --- | --- |
| `Ok` | a `payload` |
| `Denied` | a `reason` |
| `Failed` | a `message` and how many `attempts` |
| `Timeout` | `afterMillis` |

Delete the `@Disabled` line in `ToolCallResultTest` first. It fails with `TODO 3` until you get
there, and that is fine.

### TODO 1 — seal the interface, and the trap in `permits`

The TODO asks for an interface that permits exactly the four variants. Write the `permits` clause
out, even though you could leave it out. Here is the trap (verified):

    sealed interface Reply permits Accepted, Rejected { ... }                 // cannot find symbol
    sealed interface Reply permits Reply.Accepted, Reply.Rejected { ... }     // compiles

The records are nested *inside* the interface, and the `permits` clause comes *before* the body
opens, so their short names aren't in scope there yet. Qualify them. Or leave the clause out
altogether, which compiles because all four are in the same file. **Decide which you prefer and
write down why**: the explicit list documents the set where a reader can see it, but it is a
second place to update every time you add a variant.

### TODO 2 — keep the variants records

There is nothing to type here: they already are records, so they are already `final`. The TODO
exists so you notice why that matters. Change one to a plain `class` and the compiler says
`sealed, non-sealed or final modifiers expected` (verified). Every permitted subtype has to
decide whether the hierarchy stops with it, and a record has already decided.

### TODO 3 — `route()`, exhaustive, no `default`

The test says what each variant routes to: `"ok:hello"`, `"denied:no scope"`, `"failed:boom:3"`,
`"timeout:250"`. Write `route()` as a `switch` *expression* (`return switch (result) { ... };`)
with one case per variant, and no `default`.

**The check that you are done:** if the `switch` compiles with no `default` branch, the type is
sealed. If the compiler demands a `default`, TODO 1 is not finished, and adding a `default` hides
that problem instead of fixing it.

One thing it does *not* cover: `route(null)` throws `NullPointerException` (verified). The
compiler checks that every *type* is handled, not that a value is there. If null is a legal input,
you have to say so with `case null ->`.

### TODO 4 — add a fifth variant, then take it out

Add `record Cancelled() implements ToolCallResult {}` (and add it to `permits` if you wrote one
out). `route()` stops compiling. **That compile error is the whole point of the stage**, and it is
the answer to give when someone asks why you used a sealed type: adding a case becomes a list of
compile errors instead of a list of bugs.

Then delete it again. The test fixes four variants.

**And one thing the compiler cannot save you from.** If the sealed type and the `switch` are
compiled separately (say the type lives in a library and you upgrade the jar without
recompiling), the `switch` meets a variant it has never seen. The compiler added a hidden
fallback for exactly this case, and it throws `java.lang.MatchException` (verified, by compiling
`Reply` with two variants, recompiling it alone with a third, and running the old `switch`
against it). So exhaustiveness is a compile-time promise and only as current as your last build.

## Then the counter-example: `Door`

`Door.java` has two states, open and closed, and neither carries any data. Here are the same two
states as a sealed type and as an enum:

    sealed interface DoorState permits DoorState.Open, DoorState.Closed {
        record Open() implements DoorState {}
        record Closed() implements DoorState {}
    }

    enum DoorState { OPEN, CLOSED }

Both give you an exhaustive `switch`: add `AJAR` to the enum and every `switch` expression over
it stops compiling with the same `does not cover all possible input values` error (verified). The
enum is one line instead of four, has `values()` and `ordinal()` already, and has exactly one
instance of each state.

**Write down where the line is, in one sentence.** The test is whether the variants carry
*different data*: if they do, use sealed types; if they don't, use an enum. `Door` itself is a
`boolean`, and [doc 03](03-pattern-matching.md) asks whether even an enum earns its place there.
Being able to say why you wouldn't use a sealed type is worth more than being able to write one.
**Don't change `Door` in this stage.**

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: what does sealing cost when the variants genuinely belong to different modules (a sealed
type and its subtypes have to share a module, or a package if there is no module)? What happens to
a library's ability to extend your type, and when is that a feature? Once a library ships a sealed
type, is adding a variant a compatible change for the people who `switch` over it, and what does
your answer to the `MatchException` question above say about that?
