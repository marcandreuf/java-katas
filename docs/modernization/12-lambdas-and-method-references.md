# 12. Lambdas, functional interfaces and method references

**Java 8** · stage 1 of [the release-ordered track](INDEX.md) · compiles at `--release 21`, no
build change

> **No safety net:** `BadThreads`, `Consumer`, `FindDups`, `L3_Synchronization_Udemy`, `Order`,
> `OrdersProcessingSystem`, `Producer` have no tests. Write them first, in a separate commit,
> asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why the order matters.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. The old code](#exercise-1-the-anonymous-classes-in-the-old-code) | Turn the repo's anonymous classes into lambdas where that helps, and leave them where it doesn't. | T1–T3 | Characterize first: the files have no tests. |
| [2. `clean()`](#exercise-2-textpipeline-todo-1-clean-the-normal-way) | `TextPipeline` TODO 1: chain four transforms with `andThen`. | T2, T5 | `TextPipelineTest`, already written: delete its `@Disabled` to start. |
| [3. `cleanAsStep()`](#exercise-3-textpipeline-todo-2-the-same-chain-with-step-and-why-you-cannot) | TODO 2: the same chain on your own interface, and the default method it needs. | T5 | `TextPipelineTest`. |
| [4. Method references](#exercise-4-textpipeline-todo-3-method-references-and-the-one-that-lies) | TODO 3: replace helpers with references, and find the one that lies. | T3, T4 | `TextPipelineTest`. |
| [5. Comparators](#exercise-5-textpipeline-todo-4-comparators-and-where-reversed-goes) | TODO 4: `shortestFirst()` and `longestFirst()`. | T6 | `TextPipelineTest`. |
| [6. What got worse](#exercise-6-fill-in-what-got-worse) | Write down what lambdas cost. | all | None. |
| [7. Review questions](#exercise-7-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the six sections in order: each one builds on the one before. Here is the whole story in one
paragraph, so you know where it's going.

> Before Java 8, the only way to hand a piece of behaviour to a method ("compare like this", "run
> this") was to write a class for it, usually an anonymous one, six lines around the one line that
> mattered. A **functional interface** is any interface with exactly one abstract method, and a
> **lambda** is that one line, written on its own (T1). The compiler accepts a lambda wherever its
> shape matches the interface's one method, and that's the only rule (T2). A **method reference**
> is shorter still: it points at a method that already has the right shape (T3). But a reference
> can't pass arguments, so when a method is overloaded it can quietly pick the wrong one (T4).
> Lambdas only became useful once interfaces could carry methods for *joining* them, and default
> methods are what made that possible (T5). The `Comparator` builders are the best-known example
> of that, with one trap in where `reversed()` goes (T6).

Every code sample below is a complete program unless it says otherwise. Save it as the class name
plus `.java` and run it with `java Name.java`; no build needed. Outputs marked *verified* were run
on JDK 26. All the samples also compile at `--release 21`, this doc's level. A few use features
from after Java 8 (`List.of` from 9, records from 16, `Locale.of` from 19) to keep them short; the
lambda parts are all Java 8.

### T1. The problem: behaviour as an argument

Some methods need to be told *how* to do part of their job. `Collections.sort` needs to know how
to compare two elements; a `Thread` needs to know what to run. Before Java 8, the only thing you
could pass to a method was a value or an object, so "how to compare" had to be an object: an
instance of a class implementing `Comparator`. Usually an anonymous class, written on the spot:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Before {
    public static void main(String[] args) {
        List<Integer> a = new ArrayList<>(List.of(30, 4, 150, 22));
        List<Integer> b = new ArrayList<>(a);

        // Java 7: to pass "how to compare", you write a whole class (here, an anonymous one)
        Collections.sort(a, new Comparator<Integer>() {
            @Override
            public int compare(Integer x, Integer y) {
                return Integer.compare(y, x);          // the only line that matters: biggest first
            }
        });

        // Java 8: the same comparator, written as just the line that matters
        Collections.sort(b, (x, y) -> Integer.compare(y, x));

        System.out.println("anonymous class: " + a);
        System.out.println("lambda:          " + b);
    }
}
```

```text
anonymous class: [150, 30, 22, 4]                        (verified 2026-11-23)
lambda:          [150, 30, 22, 4]
```

Both are the same thing to the JVM: an object implementing `Comparator<Integer>`. The lambda is
just a shorter way to write it. Three words that people use interchangeably, and should not:

- a **functional interface** is an interface with **exactly one abstract method**. That is the
  whole definition. `Runnable` (`void run()`), `Comparator<T>` (`int compare(T,T)`) and
  `UnaryOperator<String>` (`String apply(String)`) are all functional interfaces, and so is any
  one-method interface you write yourself. (`@FunctionalInterface` asks the compiler to check it,
  but isn't needed.)
- a **lambda** is a way to write an implementation of one inline: `(a, b) -> a.length()`
- a **method reference** is a way to point at a method that *already* has the right shape:
  `String::length`

### T2. The only rule: the shape has to match

A lambda has no type of its own. Its type comes from where you put it: the variable it's assigned
to, or the parameter it's passed to. The compiler's job is to check that the shape matches. If the
interface's single method takes a `String` and returns an `int`, then any lambda or method
reference taking a `String` and returning an `int` can stand in for it. **That is the only rule**,
and everything below follows from it.

So the same lambda text can become several different types:

```java
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

public class Shapes {
    @FunctionalInterface
    interface Measure {                     // your own functional interface: one abstract method
        int of(String text);
    }

    public static void main(String[] args) throws Exception {
        // The same lambda text, three different target types. The compiler only checks the shape.
        Measure m                     = text -> text.length();
        ToIntFunction<String> t       = text -> text.length();
        Function<String, Integer> f   = text -> text.length();
        System.out.println(m.of("lambda") + " " + t.applyAsInt("lambda") + " " + f.apply("lambda"));

        // Zero arguments, returning a value: fits Supplier and Callable alike.
        Supplier<String> s = () -> "ready";
        Callable<String> c = () -> "ready";
        System.out.println(s.get() + " " + c.call());

        // A lambda can read local variables, but only ones that are never reassigned.
        String suffix = "!";
        Function<String, String> shout = text -> text + suffix;
        System.out.println(shout.apply("hello"));
        // suffix = "?";   // uncomment: "local variables referenced from a lambda expression
        //                 //  must be final or effectively final"
    }
}
```

```text
6 6 6                                                    (verified 2026-11-23)
ready ready
hello!
```

`Measure`, `ToIntFunction<String>` and `Function<String, Integer>` are unrelated types, and the one
lambda fits all three because all three have a single method from `String` to a number. Once
assigned, though, they *are* different types: you can't pass `m` where a `Function` is wanted.

The last part is the one restriction worth knowing: a lambda can use a local variable from around
it only if that variable is **effectively final**, never reassigned. (Fields are fine.)

`java.util.function` has about forty ready-made interfaces, so you rarely need your own. The few
you'll use all the time: `Function<T, R>` (T in, R out), `UnaryOperator<T>` (T in, T out),
`Predicate<T>` (T in, boolean out), `Supplier<T>` (nothing in, T out) and `Consumer<T>` (T in,
nothing out).

### T3. Method references: pointing at a method that already fits

When a lambda does nothing but call one existing method with its own arguments, you can name the
method instead: `s -> s.length()` becomes `String::length`. There are four forms, and the
difference between them is **where the receiver comes from** (the object before the dot):

```java
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Refs {
    public static void main(String[] args) {
        // 1. static: Integer.parseInt(String) has the shape String -> Integer
        Function<String, Integer> parse = Integer::parseInt;
        System.out.println("static:           " + parse.apply("42"));

        // 2. unbound instance: the receiver becomes the first parameter
        Function<String, Integer> length = String::length;            // s -> s.length()
        BiFunction<String, String, Boolean> starts = String::startsWith; // (s, p) -> s.startsWith(p)
        System.out.println("unbound instance: " + length.apply("four") + " " + starts.apply("lambda", "lam"));

        // 3. bound instance: the receiver is fixed when you write the reference
        String greeting = "hello";
        Function<String, Boolean> isGreeting = greeting::equals;        // x -> greeting.equals(x)
        Consumer<String> print = System.out::println;                   // x -> System.out.println(x)
        System.out.print("bound instance:   ");
        print.accept(isGreeting.apply("hello") + " " + isGreeting.apply("bye"));

        // 4. constructor
        Supplier<List<String>> fresh = ArrayList::new;                  // () -> new ArrayList<>()
        List<String> list = fresh.get();
        list.add("new");
        System.out.println("constructor:      " + list);
    }
}
```

```text
static:           42                                     (verified 2026-11-23)
unbound instance: 4 true
bound instance:   true false
constructor:      [new]
```

| Form | Example | Shape it has |
| --- | --- | --- |
| static | `Integer::parseInt` | `(String) -> Integer` |
| unbound instance | `String::length` | `(String) -> Integer`: receiver becomes the argument |
| bound instance | `greeting::equals` | the receiver is fixed at the point you write it |
| constructor | `ArrayList::new` | `() -> ArrayList` |

The unbound form is the one that confuses everybody once. `startsWith` is an **instance**
method: `"lambda".startsWith("lam")`, so it looks like it takes one argument. Written as
`String::startsWith` it becomes a two-argument function: **the receiver turns into the first
parameter.** Knowing which form is which matters the first time the compiler says `invalid method
reference` and you have to work out whether it wanted a receiver.

**The worked example from this repo.** `collections/FindDups.java:13-18` has this, written in
2014:

```java
Comparator<String> comparator = new Comparator<>() {
    @Override
    public int compare(String s1, String s2) {
        return s1.compareToIgnoreCase(s2);
    }
};
```

Six lines. `Comparator<String>` is a functional interface, so there are four ways to write the
same thing, and **all four sort identically** (verified):

```java
// 1. anonymous class: what the repo has
Comparator<String> c = new Comparator<>() { ... };

// 2. lambda: the body was one expression, so it becomes one expression
Comparator<String> c = (s1, s2) -> s1.compareToIgnoreCase(s2);

// 3. method reference: the lambda does nothing but call an existing method
Comparator<String> c = String::compareToIgnoreCase;

// 4. no code at all: the JDK already has this comparator
Comparator<String> c = String.CASE_INSENSITIVE_ORDER;
```

Form 3 is the unbound form again: `compareToIgnoreCase` takes one argument, and the reference
takes two, because the receiver became the first. **Form 4 is the real answer**, and it is the
point of the whole exercise: the best lambda is frequently no lambda.

### T4. When the shortest form picks the wrong overload

A method reference names a method but can't supply any arguments. So when a method is
**overloaded** (two methods with the same name and different parameters), the reference picks the
overload whose parameters fit the interface's shape, and the one it picks may not be the one you
meant.

`String` has `toUpperCase()` and `toUpperCase(Locale)`. A `UnaryOperator<String>` passes exactly one
`String`, so `String::toUpperCase` can only mean the no-argument version, and that one uses the
**JVM's default locale**:

```java
import java.util.Locale;
import java.util.function.UnaryOperator;

public class Overload {
    public static void main(String[] args) {
        // String has toUpperCase() and toUpperCase(Locale). A method reference can't pass the
        // Locale, so it can only mean the no-argument one, which uses the JVM's default locale.
        UnaryOperator<String> ref    = String::toUpperCase;
        UnaryOperator<String> lambda = s -> s.toUpperCase(Locale.ROOT);

        Locale.setDefault(Locale.of("tr", "TR"));     // what -Duser.language=tr does at startup
        System.out.println("reference: " + ref.apply("title"));
        System.out.println("lambda:    " + lambda.apply("title"));
    }
}
```

```text
reference: TİTLE                                         (verified 2026-11-23)
lambda:    TITLE
```

In Turkish, the capital of `i` is `İ`, with a dot. Both lines compile; only the lambda says which
locale it means. `Locale.ROOT` means "no particular language": use it for text a program reads,
like keys, codes and identifiers. **The shortest form is not always the correct one.** Exercise 4
has the lowercase version of the same trap, in the repo's own code.

### T5. Default methods: how an interface learns to combine

A lambda on its own is one step. Real code wants to **join** steps: "do this, then that", "this
and that". A method that takes behaviours and returns a new, combined behaviour is called a
**combinator**, and the natural place for one is on the interface itself: `first.andThen(second)`.

Before Java 8 that was impossible. Adding a method to a published interface broke every class
that implemented it, because each of them suddenly had a method it didn't implement. Java 8 added
**default methods**: interface methods with a body. Every existing implementor inherits the body,
so nothing breaks. That's what lets `Function` carry `andThen` and `compose`:

```java
import java.util.function.Function;
import java.util.function.Predicate;

public class Combine {
    public static void main(String[] args) {
        Function<Integer, Integer> addOne = n -> n + 1;
        Function<Integer, Integer> twice  = n -> n * 2;

        // andThen: run this, then that.  compose: run that first, then this.
        System.out.println("addOne.andThen(twice) on 5: " + addOne.andThen(twice).apply(5));
        System.out.println("addOne.compose(twice) on 5: " + addOne.compose(twice).apply(5));

        // Predicate carries combinators too: and, or, negate. All three are default methods.
        Predicate<Integer> positive = n -> n > 0;
        Predicate<Integer> even     = n -> n % 2 == 0;
        Predicate<Integer> both     = positive.and(even);
        System.out.println("both on 4, -4, 3: " + both.test(4) + " " + both.test(-4) + " " + both.test(3));
    }
}
```

```text
addOne.andThen(twice) on 5: 12                           (verified 2026-11-23)
addOne.compose(twice) on 5: 11
both on 4, -4, 3: true false false
```

`andThen` gives (5 + 1) × 2 = 12; `compose` gives 5 × 2 + 1 = 11. Same two functions, opposite
order. And `Function` and `Predicate` are still functional interfaces, because a default method
isn't abstract: each still has exactly one abstract method (`apply`, `test`).

Each combinator returns a new behaviour built out of the ones you gave it. That only works
because the interface can carry a method with a body. **That's why default methods and lambdas
shipped in the same release**: lambdas are only useful if the interfaces they target can carry the
machinery for joining them together. Java 8's two headline features are one feature. An interface
you write yourself gets none of this for free, and finding that out is exercise 3.

### T6. Comparator builders, and where `reversed()` goes

`Comparator` is the best-known interface full of combinators. Its static methods build a
comparator from a **key** (the thing to sort by), and its default methods add to it:

- `Comparator.comparingInt(Player::score)` orders by an `int` key. `comparing(...)` does the same
  for any `Comparable` key.
- `.thenComparing(...)` adds a tie-breaker, used only when the first comparison says "equal".
- `.reversed()` flips **the whole comparator it's called on**.

That last point is the trap. `reversed()` flips *everything to its left*, tie-breakers included:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Ranking {
    record Player(String name, int score) {
        @Override public String toString() { return name + ":" + score; }
    }

    static final List<Player> PLAYERS = List.of(
            new Player("cleo", 7), new Player("ana", 9), new Player("bo", 7), new Player("dev", 3));

    public static void main(String[] args) {
        Comparator<Player> byScore = Comparator.comparingInt(Player::score);
        Comparator<Player> byName  = Comparator.comparing(Player::name);

        print("score, then name     ", byScore.thenComparing(byName));
        print("reversed() at the end", byScore.thenComparing(byName).reversed());
        print("reversed() in middle ", byScore.reversed().thenComparing(byName));
    }

    static void print(String label, Comparator<Player> order) {
        List<Player> copy = new ArrayList<>(PLAYERS);
        copy.sort(order);
        System.out.println(label + " " + copy);
    }
}
```

```text
score, then name      [dev:3, bo:7, cleo:7, ana:9]       (verified 2026-11-23)
reversed() at the end [ana:9, cleo:7, bo:7, dev:3]
reversed() in middle  [ana:9, bo:7, cleo:7, dev:3]
```

Both reversed lines put the highest score first. They differ only on the tie: `bo` and `cleo` both
scored 7. With `reversed()` at the end, the name tie-breaker is flipped too, so the tie comes out
Z→A. With `reversed()` right after `byScore`, only the score is flipped. Both compile, both read
as "highest first", and **only data with a tie can tell them apart**. That's why this bug reaches
production constantly: test data usually has no ties.

## Practice

### Exercise 1: the anonymous classes in the old code

Every anonymous class here is a lambda that was never written (T1):

| File | What is there |
| --- | --- |
| `collections/FindDups.java:13-18` | anonymous `Comparator<String>`: the worked example in T3 |
| `algorithm/L3_Synchronization_Udemy.java:25,45` | two anonymous `Runnable`s |
| `concurrency/BadThreads.java:11` | inner-class `Runnable` |
| `concurrency/guardedLock/Producer.java`, `Consumer.java` | `extends Thread` as named classes |
| `concurrency/OrdersProcessingSystem.java:26-33` | inner `Runnable` with an empty `run()` |

1. **`FindDups.java:13` through all four forms in T3, in two commits.** Commit the lambda, then
   commit `String.CASE_INSENSITIVE_ORDER` and delete it. The first commit teaches the syntax; the
   second teaches that the shortest code is no code. ([Doc 09](09-streams-and-optional.md) asks
   for the same deletion from the other direction.)
2. **`L3_Synchronization_Udemy.java:25` and `:45`.** Both are `new Runnable() { public void run()
   { … } }`, so both *can* become `() -> { … }`. One shrinks to a single readable line. The other
   has a nine-line body with a `try/catch` in it and gets worse. **Convert the first, leave the
   second, and write down the line count where you switched.** That number is your own answer to
   "when is a lambda worse than a class", and it is more useful than anyone else's.
3. **`guardedLock/Producer` and `Consumer` are not lambda targets**, even though they are
   `Runnable`s (they `extend Thread`, which implements it). Work out why in one sentence before
   reading on. — They hold fields and have a constructor. A lambda has neither: it is an
   implementation of one method, not a place to keep state.

**Done when:** `FindDups` is in two commits (lambda, then `CASE_INSENSITIVE_ORDER`), one
`L3_Synchronization_Udemy` `Runnable` is converted and the other left with your line count written
down, and you can say in one sentence why `Producer` and `Consumer` stay classes.

### `functional/TextPipeline.java`: what exercises 2 to 5 build

Four static methods, each `String -> String`. **Composing them means producing one object that
runs all four in order**, so that:

```java
clean().apply("  The   QUICK\tbrown\nfox  ")   ==   "The quick brown fox"
```

Order: trim, collapse whitespace runs, lowercase, capitalize the first letter. The test checks the
order as well as the result — `capitalize` before `lower` gives `"the quick brown fox"` and fails.

`TextPipelineTest` is already written and switched off with `@Disabled`. **Deleting that one line
is how you start** ([00-start-here](00-start-here.md#starting-an-exercise)): the tests go red, and
exercises 2 to 5 turn them green.

### Exercise 2: `TextPipeline` TODO 1, `clean()` the normal way

`UnaryOperator<String>` extends `Function<String,String>`, which carries `andThen` as a **default
method** (T5). `a.andThen(b)` returns a new function that runs `a`, then feeds the result to `b`.
So a chain of four is three `andThen` calls, and you start by getting the first transform into a
variable of the right type:

```java
UnaryOperator<String> step = TextPipeline::trimEnds;    // a method reference, shape matches
// ... then andThen three times
```

Then write it with `compose` instead and notice you have to list the same four names backwards.
That is why `andThen` is the one people reach for.

**Done when:** `clean()` passes its tests, and you've written the `compose` version once and seen
the order flip.

### Exercise 3: `TextPipeline` TODO 2, the same chain with `Step`, and why you cannot

`Step` is a functional interface you own: one abstract method, `String apply(String)`. So
`TextPipeline::trimEnds` assigns to a `Step` just as happily as to a `UnaryOperator` (T2).

**Then you get stuck, and that is the exercise.** There is no `andThen` on `Step`. The only thing
you can do with a `Step` is call `apply` on it. Writing the chain by hand means nesting four calls
inside each other, which is the thing composition was supposed to remove.

So add it — one line, `default Step andThen(Step next)`.

**The lesson is why that line is allowed to exist.** Before Java 8, adding a method to a published
interface broke every existing implementor, so `java.util.function.Function` could not have
carried `andThen` either. **Default methods are what make combinators possible** (T5).

**Done when:** `cleanAsStep()` passes its tests, and you've written down the one thing the named
`Step` type buys that `UnaryOperator<String>` doesn't (the class's Javadoc asks the question).

### Exercise 4: `TextPipeline` TODO 3, method references, and the one that lies

Three of the four transforms can point straight at a `String` method and skip the helper
entirely — `String::trim` is the obvious one. Find the others.

Then `lower()`. There **is** a method reference for it, and it compiles, and it is wrong (T4):

```java
UnaryOperator<String> a = String::toLowerCase;               // compiles
UnaryOperator<String> b = s -> s.toLowerCase(Locale.ROOT);    // compiles
```

`String` has two `toLowerCase` methods: a no-argument one and one taking a `Locale`. A method
reference cannot supply an argument, so `String::toLowerCase` resolves to the **no-argument**
overload, which uses the **JVM's default locale**. Under `-Duser.language=tr` that lowercases
`"TITLE"` to `"tıtle"` — a dotless `ı` — so your test passes on your laptop and fails in Istanbul.
Verified: `"TITLE".toLowerCase(new Locale("tr","TR"))` really is `tıtle`.

**The shortest form is not the correct one here.** That is the best available answer to "when
would you not use a method reference", and it is worth more than the syntax.

The four forms (T3), for the record. Two of them appear in this file:

| Form | Example | Shape it has |
| --- | --- | --- |
| static | `TextPipeline::trimEnds` | `(String) -> String` |
| unbound instance | `String::trim` | `(String) -> String` — receiver becomes the argument |
| bound instance | `someInstance::method` | the receiver is fixed at the point you write it |
| constructor | `StringBuilder::new` | `() -> StringBuilder` |

**Done when:** the three helpers that can be references are, `lower()` keeps its explicit
`Locale.ROOT`, and the tests still pass.

### Exercise 5: `TextPipeline` TODO 4, comparators, and where `reversed()` goes

Three pieces (T6):

```java
Comparator.comparingInt(String::length)   // order by length
    .thenComparing(Comparator.naturalOrder())   // break ties alphabetically
    .reversed()                                  // flip whatever it is called on
```

`shortestFirst()` is the first two. `longestFirst()` needs all three — and **where `reversed()`
goes changes the answer**:

```java
comparingInt(String::length).reversed().thenComparing(naturalOrder())   // [apple, kiwi, pear, plum, fig]
comparingInt(String::length).thenComparing(naturalOrder()).reversed()   // [apple, plum, pear, kiwi, fig]
```

`reversed()` flips *everything to its left*. In the second form that includes the tie-breaker, so
strings of equal length come back Z→A. Both compile, both read as "longest first", and the test
fixture has three four-letter words precisely so it can tell them apart.

**This bug reaches production constantly**, because test data usually has no ties.

**Done when:** `shortestFirst()` and `longestFirst()` pass their tests, including the three-way
tie.

### Exercise 6: fill in "What got worse"

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

### Exercise 7: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. What is a functional interface? Does it need `@FunctionalInterface`? (T1)
2. What does the compiler check when you assign a lambda to a variable? (T2)
3. Why can't you assign a `ToIntFunction<String>` variable to a `Function<String, Integer>`, when
   the same lambda fits both? (T2)
4. A lambda uses a local variable that's reassigned later in the method. What happens? (T2)
5. Name the four forms of method reference, with one example each. (T3)
6. `String::startsWith` is a `BiFunction<String, String, Boolean>`. Where did the first
   argument come from? (T3)
7. Why is `String.CASE_INSENSITIVE_ORDER` better than any of the three ways to write
   `compareToIgnoreCase` as a comparator? (T3)
8. Why does `String::toUpperCase` behave differently from `s -> s.toUpperCase(Locale.ROOT)`? (T4)
9. Why couldn't `Function` have had `andThen` before Java 8? (T5)
10. Is an interface with one abstract method and three default methods a functional interface?
    (T5)
11. `byScore.thenComparing(byName).reversed()` versus `byScore.reversed().thenComparing(byName)`:
    when do they give different results? (T6)

**Done when:** you can answer all eleven without looking.
