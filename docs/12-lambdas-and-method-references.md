# 12. Lambdas, functional interfaces and method references

**Java 8** · stage 1 of [the release-ordered track](INDEX.md) · compiles at `--release 21`, no
build change

## Where it lands

Run this first, because the number is the finding:

    grep -rln --include='*.java' -e '->' -e '::' src

**One file.** `httpclient/LocalHttp.java`, which this track added. Eleven years of Java in this
repo and not one lambda. Every anonymous class below is a lambda that was never written:

| File | What is there |
| --- | --- |
| `collections/FindDups.java:40-45` | anonymous `Comparator<String>`, six lines, one expression |
| `algorithm/L3_Synchronization_Udemy.java:25,45` | two anonymous `Runnable`s |
| `concurrency/BadThreads.java:41` | inner-class `Runnable` |
| `concurrency/guardedLock/Producer.java`, `Consumer.java` | `implements Runnable` as named classes |
| `concurrency/OrdersProcessingSystem.java:26-33` | inner `Runnable` with an empty `run()` |

New code for the composition half: **`functional/TextPipeline.java`**, four TODOs.

## The exercise

### Part one, the old code

1. `FindDups.java:40` to a lambda. Then to `String.CASE_INSENSITIVE_ORDER` and delete the lambda
   too — which is the real answer, and the one [doc 09](09-streams-and-optional.md) asks for.
   **Doing it in two commits is the exercise**: the lambda commit teaches the syntax, the
   second teaches that the best lambda is often no lambda.
2. `L3_Synchronization_Udemy.java:25` and `:45` to lambdas. One of the two shrinks to a single
   line and reads better. The other does not, because its body is nine lines with a `try/catch`
   in it. **Leave that one as an anonymous class and write down the line count where you
   switched.** That number is your own answer to "when is a lambda worse", and it is more useful
   than anyone else's.
3. `guardedLock/Producer` and `Consumer` are *not* lambda targets even though they implement
   `Runnable`, and working out why in one sentence is worth more than the conversion. (They hold
   state and have a constructor. A lambda has neither.)

### Part two, composition

`functional/TextPipeline.java`. Work the TODOs in order.

**TODO 1 is the one that teaches something you cannot get from a syntax guide.** Try to chain the
four transforms using the hand-rolled `Step` interface and you will find you cannot: there is no
`andThen`. You have to add it yourself, as a `default` method. That is precisely what
`java.util.function.Function` does, and it is why **default methods and lambdas shipped in the
same release** — lambdas are only useful if the interfaces they target can carry combinators, and
you cannot add a method to a published interface without them. Java 8's two headline features are
one feature.

**TODO 3 has a trap and the trap is the lesson.** Three of the four transforms have a clean
method reference. The fourth is `lower`:

    UnaryOperator<String> a = String::toLowerCase;              // compiles
    UnaryOperator<String> b = s -> s.toLowerCase(Locale.ROOT);   // compiles

`a` resolves to the no-arg overload, which uses the **default locale**. Under
`-Duser.language=tr` it lowercases `"TITLE"` to `"tıtle"` with a dotless ı, and your test passes
on your laptop and fails in Istanbul. A method reference cannot pass an argument, so it cannot
pass a `Locale`. **The shortest form is not the correct one here.** Write that down — it is the
best available answer to "when would you not use a method reference".

While you are there, name the four method-reference forms out loud: static
(`TextPipeline::trimEnds`), unbound instance (`String::trim`), bound instance
(`someInstance::method`) and constructor (`StringBuilder::new`). Two of the four appear in this
file. Knowing which is which matters the first time the compiler tells you
`invalid method reference` and you have to work out whether it wanted a receiver.

**TODO 4** is the `reversed()` placement trap:

    comparing(String::length).reversed().thenComparing(naturalOrder())   // length desc, ties A→Z
    comparing(String::length).thenComparing(naturalOrder()).reversed()   // length desc, ties Z→A

Both compile. Both look right. The test pins the first. This bug reaches production constantly
because the test data usually has no ties.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: what does a four-deep `andThen` chain do to a stack trace when the second step throws —
and compare your answer with the same question in [doc 09](09-streams-and-optional.md) and
[doc 15](15-completable-future.md), because it is the same complaint three times and that is worth
noticing. What does `Comparator.comparing(String::length)` cost compared with
`comparingInt(String::length)`, and at what collection size would you care? When does a named
inner class beat a lambda for debugging? What does a lambda do to the class count in your jar, and
does the answer change your mind about anything?
