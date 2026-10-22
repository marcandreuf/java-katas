# 16. Collection factories and immutability

**Java 9** (`List.of`, `Set.of`, `Map.of`) · **Java 10** (`List.copyOf`,
`Collectors.toUnmodifiableList`) · **Java 16** (`Stream.toList`) · stage 5 of
[the release-ordered track](INDEX.md) · no build change

> **No safety net:** `ArgsRandom` has no test. Write one first, in a separate commit, asserting
> what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how,
> and why the order matters. Part three changes a test helper whose main test has **no
> assertion**, so it starts by adding one.

## What this stage is actually about

Five words, defined once:

- **unmodifiable** describes a *reference*: calling `add`, `set` or `remove` through it throws
  `UnsupportedOperationException`. It says nothing about whether someone else can change the data.
- **immutable** describes the *data*: nobody can change it, through any reference.
- a **view** is a collection backed by another one. It copies nothing, so changes to the
  original show through it. `Collections.unmodifiableList` returns a view.
- **fixed-size** is a third state: `Arrays.asList` lets you `set` an element but not `add` or
  `remove`, because it is a window onto an array, and arrays don't grow.
- a **shallow** guarantee covers the list's slots, not the objects in them. Every list in this
  doc is shallow.

That gives one rule, and everything below follows from it: **a list's type tells you nothing about
who else can change it. Ask two questions: can *I* change it, and can *anyone else*?** Only a copy
answers "no" to both, and even then only for the slots, not for what they point at.

## The worked example

The shallow half first, because it's the one people forget (verified):

    List<StringBuilder> list = List.of(new StringBuilder("a"));
    list.get(0).append("!");
    list                                     // [a!]

    List<StringBuilder> copy = List.copyOf(list);
    list.get(0).append("?");
    copy                                     // [a!?]: the copy shares the element

`List.of` refused nothing, because nothing was added: the *list* never changed. The
`StringBuilder` inside it did. "Immutable list" means the slots are fixed, not that the things in
them are. A list of mutable objects is a list of mutable objects, however it was built.

And a copy isn't always a copy. `List.copyOf(List.of("a", "b")) == ` the original: **`true`**
(verified). When the source is already one of the JDK's immutable lists, there is nothing to
protect, so it hands back the same object. `List.copyOf` of an `unmodifiableList` view or a
`Stream.toList()` result does copy (both verified `false`). That is what makes it cheap to call
defensively.

## Part one: `collections/Immutable.java`, four TODOs

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

**TODO 2 is the one the doc exists for.** Its test asserts that after `source.add("c")`, the view
contains `[a, b, c]`. This is the classic defensive-copy bug: a getter returns
`Collections.unmodifiableList(this.items)`, the caller can't modify it, and the object's own later
changes leak out through a reference the caller reasonably thought was a snapshot. The Java 10
method is the one that means what people assume the older one meant, which is why so much code
from the 2010s defends itself badly.

**TODO 4** is the null column. `Stream.toList()` (16) keeps a `null` element, and
`Collectors.toUnmodifiableList()` (10) throws `NullPointerException` on it. The test uses exactly
that input. If you are ever migrating `collect(Collectors.toList())` to `.toList()` across a
codebase, there are two behaviour changes waiting:

- `collect(toList())` returns a `java.util.ArrayList` today (verified), so `result.sort(null)`
  works on it. On a `.toList()` result, the same call throws `UnsupportedOperationException`
  (verified). The `Collectors.toList()` javadoc says there are "no guarantees on the type,
  mutability" of the list it returns, but code relied on it anyway.
- The null column above.

**And the column nobody expects: `contains(null)`.** On `List.of`, `List.copyOf` and
`toUnmodifiableList`, asking whether the list contains `null` **throws** (verified). Swap a
`new ArrayList<>(...)` for `List.copyOf(...)` and any `list.contains(x)` where `x` can be `null`
goes from `false` to a crash. The `List` javadoc allows this ("if the specified element is null and
this list does not permit null elements"), and the JDK's immutable lists do it.

## The factories' own opinions

All verified, and each is a runtime failure you will eventually see:

- `List.of("a", null)`: `NullPointerException`
- `Map.of("k", 1, "k", 2)`: `IllegalArgumentException: duplicate key: k`
- `Set.of("a", "a")`: `IllegalArgumentException: duplicate element: a`
- `Map.of("k", null)` and a `null` key: `NullPointerException`
- **`Set.of` and `Map.of` iteration order changes from one JVM run to the next.** Seven strings
  in a `Set.of`, printed by four separate runs of the same program, came out in three different
  orders (verified). There's a per-process salt in `ImmutableCollections`, so nobody can depend on
  the order. A test that asserts the iteration order of a `Set.of` passes on one run and fails on
  the next. Not on a different machine: on a different *run*.

That last one is a design decision worth stealing: when a contract says "unspecified", the
library actively randomises instead of being consistent by accident.
[Doc 14](14-map-defaults-and-collectors.md) had you meet `Map.of`'s duplicate-key throw from the
other side.

## Part two: `ArgsRandom`

`collections/ArgsRandom.java:12`:

    List<String> lstArgs = new ArrayList<String>(Arrays.asList(args));
    Collections.shuffle(lstArgs);

**A correction to the old version of this doc**, which said the second copy is "actually
necessary" because `Arrays.asList` can't be added to. `shuffle` never adds. It only calls `set`,
and `Arrays.asList` supports `set`. `Collections.shuffle(Arrays.asList(args))` works (verified).
What it also does is **shuffle `args` itself**, because the list writes through to the array
(verified: after the shuffle, the list and the array are in the same new order). So the copy isn't
there to make shuffling possible. It's there to keep the caller's array unchanged, and in a `main`
that never looks at `args` again, nothing can tell the difference.

And the obvious modern spelling, `List.of(args)`, **throws** `UnsupportedOperationException` the
moment `shuffle` calls `set` (verified). It works with zero or one argument (verified), because
`shuffle` doesn't need to call `set` then. So a test with one argument would pass.

Commits, in this order:

1. **Characterize.** Extract the shuffle into a method that returns the list, and pass in a seeded
   `Random` (`Collections.shuffle(list, random)` takes one), so the order is deterministic.
   [Doc 19](19-randomness-and-numerics.md) is about that change. Assert the output for a seed, with
   at least **three** arguments, and assert that the input array is unchanged afterwards.
2. **Decide what the line should be**, and write down why. Three candidates: keep the copy,
   shuffle `Arrays.asList(args)` in place, or something from the table above. One of them breaks
   your array assertion, and one breaks the shuffle. Only one keeps both green.

## Part three: `The100DoorsCyberDojoTest`, the site that isn't what it seemed

**A correction to the old version:** it called `The100DoorsCyberDojoTest:46` "the second site" of
the same `asList`-then-copy pattern. It isn't. Lines 45-51 are:

    List<Door> lstDoors = new ArrayList<Door>(100);
    for(int i=0; i<100; i++){
        lstDoors.add(i, new Door());
    }

That's 100 **distinct** `Door` objects, and `Door` is mutable (`toggle()`).
[Doc 01](01-records.md) explains why it isn't a record. The tempting one-line factory is
`Collections.nCopies(100, new Door())`. Make that swap in a scratch copy and run the class:
**all 5 tests stay green** (verified). And the output of `goThroughAndToggleTheDoors` changes from
**10 doors open** (1, 4, 9, … 100, the right answer) to **none open** (verified). `nCopies` holds
one `Door` 100 times, so every toggle hits the same object. That is the worked example's shallow
rule with a real consequence.

Nothing caught it, because `goThroughAndToggleTheDoors` (lines 89-107) **has no assertion**. It
prints and passes. So:

1. **Characterize.** Add the assertion that test should always have had: exactly doors 1, 4, 9,
   16, 25, 36, 49, 64, 81 and 100 are open. Green today.
2. **Then rewrite `initDoors`** with a factory, if one fits. Try `nCopies` first, deliberately,
   and watch the new assertion fail. Then find a form that creates one `Door` per slot. Decide
   whether the result needs to be mutable at all: nothing in the class calls `add` or `set` on it
   after `initDoors` returns.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: `List.of` has fixed-arity overloads for up to ten elements before it falls back to
varargs. Does that matter to anything you'd write, and how would you find out? When is a *view*
actually what you want, and what makes it right there? If a getter returns `List.copyOf(items)` on
every call, what have you done to a caller that invokes it in a loop, and how does the
same-instance shortcut above change your answer? `contains(null)` throwing is allowed by the
`List` contract: is it a feature or a trap, and who pays for it?
