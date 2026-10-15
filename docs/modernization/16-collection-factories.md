# 16. Collection factories and immutability

**Java 9, 10** · stage 5 of [the release-ordered track](INDEX.md) · `--release 21`, no build change

## Where it lands

**`collections/ArgsRandom.java:12`** is the whole Java 9 argument in one line:

    List<String> lstArgs = new ArrayList<String>(Arrays.asList(args));

An array, wrapped in a fixed-size list, copied into a growable one. Two conversions to get to a
mutable list, written that way because in 2014 there was no shorter path.
`The100DoorsCyberDojoTest:46` is the second site.

New code: **`collections/Immutable.java`**, four TODOs.

## The exercise

Java has **five** things that get called an immutable list and they behave three different ways.
TODOs 1–3 make you produce one of each, and the test pins the differences.

| Expression | `add` | Sees later changes to its source |
| --- | --- | --- |
| `List.copyOf(src)` | throws | **no** |
| `Collections.unmodifiableList(src)` | throws | **yes** |
| `Arrays.asList(a, b)` | throws | n/a — `set` works |
| `stream().toList()` | throws | no |
| `collect(toUnmodifiableList())` | throws | no |

**TODO 2 is the one that matters, and the test asserts the surprising direction:**

    List<String> source = new ArrayList<>(List.of("a", "b"));
    List<String> view = Collections.unmodifiableList(source);
    source.add("c");
    // view is now [a, b, c]

`unmodifiableList` returns a **window**, not a copy. "Unmodifiable" is a claim about the reference
you were handed, not about the data behind it. **`List.copyOf` is the one that means what people
think `unmodifiableList` means**, and it arrived in Java 10 — which is the honest reason so much
2010s code defends itself badly.

This is the classic defensive-copy bug: a getter returns `unmodifiableList(this.items)`, the caller
cannot modify it, and the object's own later mutations leak out through a reference the caller is
still holding and reasonably assumes is a snapshot.

**TODO 3**, `Arrays.asList`, is the third category and the reason `ArgsRandom` is written the way
it is: fixed length, mutable contents. `set(0, "z")` works and `add` throws. Almost nobody can
state that from memory, and it is why the second copy in `ArgsRandom:12` is actually necessary.

**TODO 4**: `Stream.toList()` (16) and `Collectors.toUnmodifiableList()` (10) differ on exactly
one input:

    Arrays.asList("a", null).stream().toList()                        // [a, null]
    Arrays.asList("a", null).stream().collect(toUnmodifiableList())   // NullPointerException

`Stream.toList()` tolerates nulls; the collector does not. Neither is wrong — they were specified
six years apart with different opinions about null. **If you are migrating
`collect(Collectors.toList())` to `.toList()` across a codebase, this is the behaviour change
waiting for you**, along with the other one: `collect(toList())` returns a mutable `ArrayList` in
practice, and `.toList()` does not, so code that sorted the result in place stops working.

## The factories' own opinions

Learn these four, because each one is a runtime failure you will meet:

- `List.of(...)` **rejects null elements** — `NullPointerException`
- `Map.of(...)` **rejects duplicate keys** — `IllegalArgumentException: duplicate key: k`
- `Map.of(...)` rejects null keys and values
- **`Set.of` and `Map.of` iteration order is randomised per JVM run.** There is a per-process salt
  in `ImmutableCollections`, deliberately, so that nobody can depend on the order. A test that
  asserts the iteration order of a `Set.of` passes on your machine and fails in CI on a different
  run — not a different machine, a different *run*. If you have ever seen that and blamed the CI
  box, this was it.

That last one is a design decision worth stealing: when a contract says "unspecified", the library
actively randomises rather than being accidentally consistent. [Doc 14](14-map-defaults-and-collectors.md)
had you meet `Map.of`'s duplicate-key throw from the other side.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: `List.of` caps out at ten varargs-free overloads before it allocates an array — does that
matter to anything you would write? What do the `of` factories cost in a hot loop compared with a
pre-sized `ArrayList`, and did you measure? When is a *view* actually what you want, and what makes
it the right answer there? If a method returns `List.copyOf(items)` on every call, what have you
done to a caller that invokes it in a loop?
