# 12. Lambdas, functional interfaces and method references

**Java 8** · stage 1 of [the release-ordered track](INDEX.md) · compiles at `--release 21`, no
build change

> **No safety net:** `BadThreads`, `Consumer`, `FindDups`, `L3_Synchronization_Udemy`, `Order`,
> `OrdersProcessingSystem`, `Producer` have no tests. Write them first, in a separate commit,
> asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why the order matters.

## What this stage is actually about

Three words that people use interchangeably and should not:

- a **functional interface** is an interface with **exactly one abstract method**. That is the
  whole definition. `Runnable` (`void run()`), `Comparator<T>` (`int compare(T,T)`) and
  `UnaryOperator<String>` (`String apply(String)`) are all functional interfaces, and so is any
  one-method interface you write yourself.
- a **lambda** is a way to write an implementation of one inline: `(a, b) -> a.length()`
- a **method reference** is a way to point at a method that *already* has the right shape:
  `String::length`

The compiler's job is to check that the shape matches. If the interface's single method takes a
`String` and returns an `int`, then any lambda or method reference taking a `String` and returning
an `int` can stand in for it. **That is the only rule**, and everything below follows from it.

## The worked example

`collections/FindDups.java:40-45` has this, written in 2014:

    Comparator<String> comparator = new Comparator<String>() {
        @Override
        public int compare(String s1, String s2) {
            return s1.compareToIgnoreCase(s2);
        }
    };

Six lines. `Comparator<String>` is a functional interface, so there are four ways to write the
same thing, and **all four sort identically** — verified:

    // 1. anonymous class: what the repo has
    Comparator<String> c = new Comparator<String>() { ... };

    // 2. lambda: the body was one expression, so it becomes one expression
    Comparator<String> c = (s1, s2) -> s1.compareToIgnoreCase(s2);

    // 3. method reference: the lambda does nothing but call an existing method
    Comparator<String> c = String::compareToIgnoreCase;

    // 4. no code at all: the JDK already has this comparator
    Comparator<String> c = String.CASE_INSENSITIVE_ORDER;

Form 3 is the one worth staring at. `compareToIgnoreCase` is an **instance** method —
`"a".compareToIgnoreCase("b")` — so it looks like it takes one argument, not two. Written as
`String::compareToIgnoreCase` it becomes a two-argument function: **the receiver turns into the
first parameter.** That is the "unbound instance method reference" form, and it is the one that
confuses everybody once.

**Form 4 is the real answer**, and it is the point of the whole exercise: the best lambda is
frequently no lambda.

## Part one: the old code

Every anonymous class here is a lambda that was never written:

| File | What is there |
| --- | --- |
| `collections/FindDups.java:40-45` | anonymous `Comparator<String>` — the example above |
| `algorithm/L3_Synchronization_Udemy.java:25,45` | two anonymous `Runnable`s |
| `concurrency/BadThreads.java:41` | inner-class `Runnable` |
| `concurrency/guardedLock/Producer.java`, `Consumer.java` | `implements Runnable` as named classes |
| `concurrency/OrdersProcessingSystem.java:26-33` | inner `Runnable` with an empty `run()` |

1. **`FindDups.java:40` through all four forms above, in two commits.** Commit the lambda, then
   commit `String.CASE_INSENSITIVE_ORDER` and delete it. The first commit teaches the syntax; the
   second teaches that the shortest code is no code. ([Doc 09](09-streams-and-optional.md) asks
   for the same deletion from the other direction.)
2. **`L3_Synchronization_Udemy.java:25` and `:45`.** Both are `new Runnable() { public void run()
   { … } }`, so both *can* become `() -> { … }`. One shrinks to a single readable line. The other
   has a nine-line body with a `try/catch` in it and gets worse. **Convert the first, leave the
   second, and write down the line count where you switched.** That number is your own answer to
   "when is a lambda worse than a class", and it is more useful than anyone else's.
3. **`guardedLock/Producer` and `Consumer` are not lambda targets**, even though they implement
   `Runnable`. Work out why in one sentence before reading on. — They hold fields and have a
   constructor. A lambda has neither: it is an implementation of one method, not a place to keep
   state.

## Part two: `functional/TextPipeline.java`

Four static methods, each `String -> String`. **Composing them means producing one object that
runs all four in order**, so that:

    clean().apply("  The   QUICK\tbrown\nfox  ")   ==   "The quick brown fox"

Order: trim, collapse whitespace runs, lowercase, capitalize the first letter. The test checks the
order as well as the result — `capitalize` before `lower` gives `"the quick brown fox"` and fails.

### TODO 1 — `clean()`, the normal way

`UnaryOperator<String>` extends `Function<String,String>`, which carries `andThen` as a **default
method**. `a.andThen(b)` returns a new function that runs `a`, then feeds the result to `b`. So a
chain of four is three `andThen` calls, and you start by getting the first transform into a
variable of the right type:

    UnaryOperator<String> step = TextPipeline::trimEnds;    // a method reference, shape matches
    // ... then andThen three times

Then write it with `compose` instead and notice you have to list the same four names backwards.
That is why `andThen` is the one people reach for.

### TODO 2 — the same chain with `Step`, and why you cannot

`Step` is a functional interface you own: one abstract method, `String apply(String)`. So
`TextPipeline::trimEnds` assigns to a `Step` just as happily as to a `UnaryOperator`.

**Then you get stuck, and that is the exercise.** There is no `andThen` on `Step`. The only thing
you can do with a `Step` is call `apply` on it. Writing the chain by hand means nesting four calls
inside each other, which is the thing composition was supposed to remove.

So add it — one line, `default Step andThen(Step next)`.

**The lesson is why that line is allowed to exist.** Before Java 8, adding a method to a published
interface broke every existing implementor, so `java.util.function.Function` could not have
carried `andThen` either. **Default methods are what make combinators possible.** That is why
default methods and lambdas shipped in the same release: lambdas are only useful if the interfaces
they target can carry the machinery for joining them together. Java 8's two headline features are
one feature.

### TODO 3 — method references, and the one that lies

Three of the four transforms can point straight at a `String` method and skip the helper
entirely — `String::trim` is the obvious one. Find the others.

Then `lower()`. There **is** a method reference for it, and it compiles, and it is wrong:

    UnaryOperator<String> a = String::toLowerCase;               // compiles
    UnaryOperator<String> b = s -> s.toLowerCase(Locale.ROOT);    // compiles

`String` has two `toLowerCase` methods: a no-argument one and one taking a `Locale`. A method
reference cannot supply an argument, so `String::toLowerCase` resolves to the **no-argument**
overload, which uses the **JVM's default locale**. Under `-Duser.language=tr` that lowercases
`"TITLE"` to `"tıtle"` — a dotless `ı` — so your test passes on your laptop and fails in Istanbul.
Verified: `"TITLE".toLowerCase(new Locale("tr","TR"))` really is `tıtle`.

**The shortest form is not the correct one here.** That is the best available answer to "when
would you not use a method reference", and it is worth more than the syntax.

The four forms, for the record. Two of them appear in this file:

| Form | Example | Shape it has |
| --- | --- | --- |
| static | `TextPipeline::trimEnds` | `(String) -> String` |
| unbound instance | `String::trim` | `(String) -> String` — receiver becomes the argument |
| bound instance | `someInstance::method` | the receiver is fixed at the point you write it |
| constructor | `StringBuilder::new` | `() -> StringBuilder` |

Knowing which is which matters the first time the compiler says `invalid method reference` and you
have to work out whether it wanted a receiver.

### TODO 4 — comparators, and where `reversed()` goes

Three pieces:

    Comparator.comparingInt(String::length)   // order by length
        .thenComparing(Comparator.naturalOrder())   // break ties alphabetically
        .reversed()                                  // flip whatever it is called on

`shortestFirst()` is the first two. `longestFirst()` needs all three — and **where `reversed()`
goes changes the answer**:

    comparingInt(String::length).reversed().thenComparing(naturalOrder())   // [apple, kiwi, pear, plum, fig]
    comparingInt(String::length).thenComparing(naturalOrder()).reversed()   // [apple, plum, pear, kiwi, fig]

`reversed()` flips *everything to its left*. In the second form that includes the tie-breaker, so
strings of equal length come back Z→A. Both compile, both read as "longest first", and the test
fixture has three four-letter words precisely so it can tell them apart.

**This bug reaches production constantly**, because test data usually has no ties.

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
