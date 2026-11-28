# 16. Collection factories and immutability

**Java 9** (`List.of`, `Set.of`, `Map.of`) · **Java 10** (`List.copyOf`,
`Collectors.toUnmodifiableList`) · **Java 16** (`Stream.toList`) · stage 5 of
[the release-ordered track](INDEX.md) · no build change

> **No safety net:** `ArgsRandom` has no test. Write one first, in a separate commit, asserting
> what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how,
> and why the order matters. Exercise 3 changes a test helper whose main test has **no
> assertion**, so it starts by adding one.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `Immutable`](#exercise-1-collectionsimmutablejava-four-todos) | Fill in four TODOs, one kind of list each, then defend doc 01's `Basket` both ways. | T1–T5 | `ImmutableTest` exists, `@Disabled`: delete the annotation to start. |
| [2. `ArgsRandom`](#exercise-2-argsrandom) | Characterize the shuffle, then decide what the line should be. Two commits. | T2, T4 | None today: write the characterization test first. |
| [3. `The100DoorsCyberDojoTest`](#exercise-3-the100doorscyberdojotest-the-site-that-isnt-what-it-seemed) | Add the missing assertion, then rewrite `initDoors` with a factory if one fits. | T3, T6 | Add the assertion first. |
| [4. What got worse](#exercise-4-fill-in-what-got-worse) | Write down three costs. | all | None. |
| [5. Review questions](#exercise-5-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the six sections in order: each one builds on the one before. Here is the whole story in one
paragraph, so you know where it's going.

> A method hands you a list. Can you change it? Can anyone else? Java's `List` type answers
> neither question, and getting it wrong is how an object's private data leaks out through a
> getter (T1). There are four kinds of "list", and they differ in exactly which operations they
> refuse (T2). Even the strictest kind only fixes the list's slots, not the objects in them (T3).
> The Java 9 factories add opinions of their own: no nulls, no duplicates, and a set order that
> changes every run (T4). Two ways of collecting a stream into a list look the same and aren't
> (T5). And one old factory, `nCopies`, fills every slot with the same object, which is a bug the
> moment that object is mutable (T6).

Every code sample below is a complete program. Save it as the class name plus `.java` and run it
with `java Name.java`; no build needed. Outputs marked *verified* were run on JDK 26.

### T1. The question a list's type can't answer

Here is the bug this doc exists for. A class keeps a private list and wants to let callers read it
without changing it. The usual answer is `Collections.unmodifiableList`, and it does stop the
caller from changing anything. It doesn't stop the *class*:

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Leak {
    static class Team {
        private final List<String> members = new ArrayList<>();

        void join(String name) { members.add(name); }

        List<String> viewOfMembers() { return Collections.unmodifiableList(members); }
        List<String> snapshotOfMembers() { return List.copyOf(members); }
    }

    public static void main(String[] args) {
        Team team = new Team();
        team.join("ana");
        team.join("ben");

        List<String> view = team.viewOfMembers();
        List<String> snapshot = team.snapshotOfMembers();

        try {
            view.add("eve");                          // the caller can't change it...
        } catch (UnsupportedOperationException e) {
            System.out.println("view.add: UnsupportedOperationException");
        }

        team.join("cai");                             // ...but the team still can
        System.out.println("view:     " + view);
        System.out.println("snapshot: " + snapshot);
    }
}
```

```text
view.add: UnsupportedOperationException                  (verified 2026-11-23)
view:     [ana, ben, cai]
snapshot: [ana, ben]
```

The caller who kept `view` reasonably thought they had the members *as they were*. They have a
window onto the members *as they are*, and it keeps moving. That's the difference between two
words the javadoc uses carefully and most people use as synonyms:

- **unmodifiable** describes a *reference*: calling `add`, `set` or `remove` through it throws
  `UnsupportedOperationException`. It says nothing about whether someone else can change the data.
- **immutable** describes the *data*: nobody can change it, through any reference.

And `unmodifiableList` returns a **view**: a collection backed by another one. It copies nothing,
so changes to the original show through it.

That gives one rule, and everything below follows from it:

> **A list's type tells you nothing about who else can change it. Ask two questions: can *I*
> change it, and can *anyone else*?**

Only a copy answers "no" to both, and even then only for the slots, not for what they point at
(T3). `List.copyOf` (Java 10) is the method that means what people assume `unmodifiableList`
meant, which is why so much code from the 2010s defends itself badly.

### T2. Four kinds of list, by what they refuse

"Can I change it?" isn't even a yes/no question, because there are two kinds of change: replacing
an element (`set`) and changing the size (`add`, `remove`). Four common ways of making a list give
four different answers:

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FourKinds {
    static String attempt(Runnable r) {
        try {
            r.run();
            return "works";
        } catch (UnsupportedOperationException e) {
            return "throws";
        }
    }

    static void show(String label, List<Integer> list) {
        System.out.printf("%-22s set: %-6s add: %s%n", label,
                attempt(() -> list.set(0, 99)),
                attempt(() -> list.add(4)));
    }

    public static void main(String[] args) {
        Integer[] array = { 1, 2, 3 };
        show("new ArrayList<>(...)", new ArrayList<>(List.of(1, 2, 3)));
        show("Arrays.asList(array)", Arrays.asList(array));
        show("unmodifiableList(...)", Collections.unmodifiableList(new ArrayList<>(List.of(1, 2, 3))));
        show("List.of(...)", List.of(1, 2, 3));
        System.out.println("array after Arrays.asList's set: " + Arrays.toString(array));
    }
}
```

```text
new ArrayList<>(...)   set: works  add: works            (verified 2026-11-23)
Arrays.asList(array)   set: works  add: throws
unmodifiableList(...)  set: throws add: throws
List.of(...)           set: throws add: throws
array after Arrays.asList's set: [99, 2, 3]
```

The second row is the one people forget. **Fixed-size** is a third state, between mutable and
unmodifiable: `Arrays.asList` lets you `set` an element but not `add` or `remove`, because it's a
window onto an array, and arrays don't grow. The last line shows the window works both ways: the
`set` went straight through to the caller's array.

| Kind | `set` | `add` / `remove` | Can someone else change it? |
| --- | --- | --- | --- |
| mutable (`ArrayList`) | yes | yes | yes, anyone holding it |
| fixed-size (`Arrays.asList`) | yes | no | yes, through the array |
| unmodifiable view (`Collections.unmodifiableList`) | no | no | yes, through the original |
| unmodifiable copy (`List.of`, `List.copyOf`) | no | no | no |

### T3. Shallow: the slots are fixed, not the things in them

Even the last row of T2's table has a limit. A **shallow** guarantee covers the list's slots, not
the objects in them. Every list in this doc is shallow:

```java
import java.util.List;

public class Shallow {
    public static void main(String[] args) {
        List<StringBuilder> list = List.of(new StringBuilder("a"));
        list.get(0).append("!");                      // List.of refuses nothing: the list didn't change
        System.out.println("list: " + list);

        List<StringBuilder> copy = List.copyOf(list);
        list.get(0).append("?");
        System.out.println("copy: " + copy + "   same element? " + (copy.get(0) == list.get(0)));

        List<String> words = List.of("x", "y");
        System.out.println("copyOf(List.of) is the same object? " + (List.copyOf(words) == words));
    }
}
```

```text
list: [a!]                                               (verified 2026-11-23)
copy: [a!?]   same element? true
copyOf(List.of) is the same object? true
```

`List.of` refused nothing, because nothing was added: the *list* never changed. The
`StringBuilder` inside it did. "Immutable list" means the slots are fixed, not that the things in
them are. **A list of mutable objects is a list of mutable objects, however it was built.** And
the copy copied the slots, so both lists point at the same `StringBuilder`.

The last line is a different surprise: **a copy isn't always a copy.**
`List.copyOf(List.of("a", "b")) ==` the original: **`true`** (verified). When the source is
already one of the JDK's immutable lists, there's nothing to protect, so it hands back the same
object. `List.copyOf` of an `unmodifiableList` view or a `Stream.toList()` result does copy (both
verified `false`). That's what makes it cheap to call defensively.

### T4. The factories' own opinions

`List.of`, `Set.of` and `Map.of` aren't just shorter spellings of `new ArrayList<>()` plus `add`.
They have rules, and every rule is a runtime failure you'll eventually see:

```java
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

public class Opinions {
    static void attempt(String label, Callable<?> c) {
        try {
            System.out.printf("%-30s -> %s%n", label, c.call());
        } catch (Exception e) {
            System.out.printf("%-30s -> %s%s%n", label, e.getClass().getSimpleName(),
                    e.getMessage() == null ? "" : ": " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        attempt("List.of(\"x\", null)", () -> List.of("x", null));
        attempt("Set.of(\"x\", \"x\")", () -> Set.of("x", "x"));
        attempt("Map.of(\"id\", 1, \"id\", 2)", () -> Map.of("id", 1, "id", 2));
        attempt("Map.of(\"id\", null)", () -> Map.of("id", null));
        attempt("List.of(\"x\").contains(null)", () -> List.of("x").contains(null));
    }
}
```

```text
List.of("x", null)             -> NullPointerException   (verified 2026-11-23)
Set.of("x", "x")               -> IllegalArgumentException: duplicate element: x
Map.of("id", 1, "id", 2)       -> IllegalArgumentException: duplicate key: id
Map.of("id", null)             -> NullPointerException
List.of("x").contains(null)    -> NullPointerException
```

- **No nulls**, as elements, keys or values. A `null` key throws `NullPointerException` too.
- **No duplicates** in `Set.of` and `Map.of`: they'd silently lose one, so they throw instead.
  [Doc 14](14-map-defaults-and-collectors.md) had you meet `Collectors.toMap`'s duplicate-key
  throw; `Map.of` has the same rule.
- **Even asking about `null` throws.** That last line is the one nobody expects. On `List.of`,
  `List.copyOf` and `toUnmodifiableList`, `contains(null)` **throws** (verified). Swap a
  `new ArrayList<>(...)` for `List.copyOf(...)` and any `list.contains(x)` where `x` can be `null`
  goes from `false` to a crash. The `List` javadoc allows this ("if the specified element is null
  and this list does not permit null elements"), and the JDK's immutable lists do it.

And one rule you can only see by running a program more than once. **`Set.of` and `Map.of`
iteration order changes from one JVM run to the next:**

```java
import java.util.Set;

public class SetOrder {
    public static void main(String[] args) {
        System.out.println(Set.of("mon", "tue", "wed", "thu", "fri", "sat", "sun"));
    }
}
```

```text
[wed, thu, sat, sun, tue, mon, fri]                      (six runs, verified 2026-11-23;
[mon, tue, sun, sat, thu, wed, fri]                       your orders will differ)
[fri, mon, tue, sun, sat, thu, wed]
[mon, tue, sun, sat, thu, wed, fri]
[mon, tue, sun, sat, thu, wed, fri]
[sun, tue, mon, fri, wed, thu, sat]
```

Six runs, four different orders. (An earlier check got three orders from four runs, also
verified.) There's a per-process salt in `ImmutableCollections`, so nobody can depend on the
order. A test that asserts the iteration order of a `Set.of` passes on one run and fails on the
next. Not on a different machine: on a different *run*.

That's a design decision worth stealing: **when a contract says "unspecified", the library
actively randomises instead of being consistent by accident.**

### T5. Collecting a stream: `collect(toList())` versus `toList()`

There are three ways to end a stream with a list, from three releases, and they aren't
interchangeable:

| Call | Since | Returns | Accepts `null` elements |
| --- | --- | --- | --- |
| `collect(Collectors.toList())` | 8 | an `ArrayList` in practice, though the javadoc promises "no guarantees on the type, mutability" | yes |
| `collect(Collectors.toUnmodifiableList())` | 10 | an unmodifiable copy, like `List.copyOf` | **no: NPE** |
| `stream().toList()` | 16 | an unmodifiable list | **yes** |

The first one is the trap when migrating. Code relied on getting an `ArrayList`, whatever the
javadoc said:

```java
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Collected {
    public static void main(String[] args) {
        List<String> old = Arrays.asList("b", "c", "a").stream().collect(Collectors.toList());
        System.out.println("collect(toList()) gives " + old.getClass().getName());
        old.sort(null);
        System.out.println("  and sort works: " + old);

        List<String> modern = Arrays.asList("b", "c", "a").stream().toList();
        try {
            modern.sort(null);
        } catch (UnsupportedOperationException e) {
            System.out.println("toList().sort(null): UnsupportedOperationException");
        }
    }
}
```

```text
collect(toList()) gives java.util.ArrayList              (verified 2026-11-23)
  and sort works: [a, b, c]
toList().sort(null): UnsupportedOperationException
```

So if you're ever migrating `collect(Collectors.toList())` to `.toList()` across a codebase, there
are two behaviour changes waiting: anything that changed the result now throws, and (if you pick
`toUnmodifiableList` instead) any `null` element now throws too.

### T6. `nCopies`: one object, many slots

`Collections.nCopies(n, x)` looks like the shortest way to make a list of `n` things. It is: of
`n` references to **one** thing. With an immutable `x` (a string, a number) that's exactly what
you want. With a mutable one, T3's shallow rule stops being a curiosity:

```java
import java.util.Collections;
import java.util.List;

public class SameOne {
    public static void main(String[] args) {
        List<StringBuilder> notes = Collections.nCopies(3, new StringBuilder());
        notes.get(0).append("first ");                // write into "slot 0"
        System.out.println(notes);
        System.out.println("slot 0 and slot 2 are the same object? " + (notes.get(0) == notes.get(2)));
    }
}
```

```text
[first , first , first ]                                 (verified 2026-11-23)
slot 0 and slot 2 are the same object? true
```

One `new StringBuilder()` ran, so there is one builder, and every slot points at it. A write
"into slot 0" is a write into all of them. Exercise 3 is this bug in a real test class, and
nothing there noticed it.

## Practice

### Exercise 1: `collections/Immutable.java`, four TODOs

The tests in `ImmutableTest` are `@Disabled`; delete the annotation to start. Here is what each
kind of list does, every cell verified on JDK 26 with a two-element source that then had `"c"`
added to it:

| Built with | `add` | `set` | Sees the later `add` to its source | Accepts `null` elements | `contains(null)` |
| --- | --- | --- | --- | --- | --- |
| `List.of(...)` | throws | throws | n/a, no source | **NPE** | **NPE** |
| `List.copyOf(src)` | throws | throws | no | **NPE** | **NPE** |
| `Collections.unmodifiableList(src)` | throws | throws | **yes** | yes | `false` |
| `Arrays.asList(a, b)` | throws | **works** | n/a, but it sees writes to its array | yes | `false` |
| `stream().toList()` | throws | throws | no | **yes** | `false` |
| `collect(toUnmodifiableList())` | throws | throws | no | **NPE** | **NPE** |

**TODO 2 is the one the doc exists for** (T1). Its test asserts that after `source.add("c")`, the
view contains `[a, b, c]`. This is the classic defensive-copy bug: a getter returns
`Collections.unmodifiableList(this.items)`, the caller can't modify it, and the object's own later
changes leak out through a reference the caller reasonably thought was a snapshot. Before you
write it, write down the difference between "unmodifiable" and "immutable" in your own words.

**TODO 3** is T2's fixed-size row. The TODO asks you to name the category it makes.

**TODO 4** is the null column (T5). `Stream.toList()` (16) keeps a `null` element, and
`Collectors.toUnmodifiableList()` (10) throws `NullPointerException` on it. The test uses exactly
that input.

**Then go back to doc 01's `Basket`** (`src/test/java/org/mandfer/practices/modern/RecordLimitsTest.java`,
[doc 01](01-records.md), Experiment A). Doc 01 step 4 gave it a compact constructor with
`items = List.copyOf(items);`. Add a second record next to it, `ViewBasket`, whose compact
constructor uses `items = Collections.unmodifiableList(items);` instead. Predict first, then run
against both:

1. Doc 01's step 1 (the caller adds to the list it passed in) and step 2 (the reader calls
   `items().add`). **They are not equivalent**: one of them still fails step 1. Which one, and why
   does the word "view" in its javadoc explain it (T1)?
2. Construct each with a list containing a `null`. Which constructor throws (T4)?
3. Call `items().contains(null)` on a basket built without a `null`. Which one throws (T4)?

This is TODO 2's defensive-copy bug again, inside a record whose `final` field made it look safe.

**Done when:** `ImmutableTest` has no `@Disabled`, all its tests pass, you've written down the
unmodifiable/immutable difference, and both baskets have been run through the three checks above.

### Exercise 2: `ArgsRandom`

`collections/ArgsRandom.java:12`:

```java
List<String> lstArgs = new ArrayList<>(Arrays.asList(args));
Collections.shuffle(lstArgs);
```

**A correction to the old version of this doc**, which said the second copy is "actually
necessary" because `Arrays.asList` can't be added to. `shuffle` never adds. It only calls `set`,
and `Arrays.asList` supports `set` (T2). `Collections.shuffle(Arrays.asList(args))` works
(verified). What it also does is **shuffle `args` itself**, because the list writes through to the
array (verified: after the shuffle, the list and the array are in the same new order). So the copy
isn't there to make shuffling possible. It's there to keep the caller's array unchanged, and in a
`main` that never looks at `args` again, nothing can tell the difference.

And the obvious modern spelling, `List.of(args)`, **throws** `UnsupportedOperationException` the
moment `shuffle` calls `set` (verified). It works with zero or one argument (verified), because
`shuffle` doesn't need to call `set` then. So a test with one argument would pass.

Commits, in this order:

1. **Characterize.** Extract the shuffle into a method that returns the list, and pass in a seeded
   `Random` (`Collections.shuffle(list, random)` takes one), so the order is deterministic.
   [Doc 19](19-randomness-and-numerics.md) is about that change. Assert the output for a seed, with
   at least **three** arguments, and assert that the input array is unchanged afterwards.
2. **Decide what the line should be**, and write down why. Three candidates: keep the copy,
   shuffle `Arrays.asList(args)` in place, or something from the table in exercise 1. One of them
   breaks your array assertion, and one breaks the shuffle. Only one keeps both green.

**Done when:** both commits are in, the test uses at least three arguments and checks the input
array, and the reason for your choice is written down.

### Exercise 3: `The100DoorsCyberDojoTest`, the site that isn't what it seemed

**A correction to the old version:** it called `The100DoorsCyberDojoTest:46` "the second site" of
the same `asList`-then-copy pattern. It isn't. Lines 45-51 are:

```java
List<Door> lstDoors = new ArrayList<>(100);
for(int i=0; i<100; i++){
    lstDoors.add(i, new Door());
}
```

That's 100 **distinct** `Door` objects, and `Door` is mutable (`toggle()`).
[Doc 01](01-records.md) explains why it isn't a record. The tempting one-line factory is
`Collections.nCopies(100, new Door())`. Make that swap in a scratch copy and run the class:
**all 5 tests stay green** (verified). And the output of `goThroughAndToggleTheDoors` changes from
**10 doors open** (1, 4, 9, … 100, the right answer) to **none open** (verified). `nCopies` holds
one `Door` 100 times, so every toggle hits the same object (T6).

Nothing caught it, because `goThroughAndToggleTheDoors` (lines 89-107) **has no assertion**. It
prints and passes. So:

1. **Characterize.** Add the assertion that test should always have had: exactly doors 1, 4, 9,
   16, 25, 36, 49, 64, 81 and 100 are open. Green today.
2. **Then rewrite `initDoors`** with a factory, if one fits. Try `nCopies` first, deliberately,
   and watch the new assertion fail. Then find a form that creates one `Door` per slot. Decide
   whether the result needs to be mutable at all: nothing in the class calls `add` or `set` on it
   after `initDoors` returns.

**Done when:** the assertion is in and green, you've seen it fail with `nCopies`, and `initDoors`
creates one `Door` per slot (whether or not you kept the loop).

### Exercise 4: fill in "What got worse"

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: `List.of` has fixed-arity overloads for up to ten elements before it falls back to
varargs. Does that matter to anything you'd write, and how would you find out? When is a *view*
actually what you want, and what makes it right there? If a getter returns `List.copyOf(items)` on
every call, what have you done to a caller that invokes it in a loop, and how does the
same-instance shortcut above change your answer? `contains(null)` throwing is allowed by the
`List` contract: is it a feature or a trap, and who pays for it?

### Exercise 5: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. What's the difference between "unmodifiable" and "immutable"? Which one does
   `Collections.unmodifiableList` give you? (T1)
2. A getter returns `Collections.unmodifiableList(items)`. The object adds to `items` later. What
   does a caller who kept the returned list see? (T1)
3. Which kind of list lets you `set` but not `add`, and why? What happens to the array behind it
   when you `set`? (T2)
4. A `List.of` holds one `StringBuilder`. Can you change what the list prints? (T3)
5. When does `List.copyOf` return the very same object it was given, and why is that safe? (T3)
6. Name three inputs that make `List.of`, `Set.of` or `Map.of` throw. (T4)
7. `List.copyOf(list).contains(null)`: what happens? (T4)
8. A test asserts that `Set.of("a", "b", "c")` prints `[a, b, c]`. It passed this morning. Why
   might it fail this afternoon on the same machine? (T4)
9. You replace `collect(Collectors.toList())` with `.toList()`. Name the two behaviours that can
   change. (T5)
10. Why is `Collections.nCopies(3, new StringBuilder())` almost never what you want? (T6)

**Done when:** you can answer all ten without looking.
