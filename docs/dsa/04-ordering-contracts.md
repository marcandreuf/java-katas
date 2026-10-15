# 04. `Comparable` and `Comparator` contracts

**Stage 4 of [the DSA track](INDEX.md)** · new code: `ordering/Ordering.java`, four TODOs

## Where it lands

New code, plus one old-code target: **`algorithm/SumSearch.java:13`** calls
`Arrays.sort(sequence)` on the caller's array. That is undocumented argument mutation — the method
is named `sumSearch`, nothing in its signature says "and I will reorder your input", and the
sorting is the subject of this stage.

This is an explicit OCP Java SE 21 objective and *Effective Java* Item 14. It is here because all
three failure modes below **pass a small unit test**.

## TODO 1 and 2: the comparator everybody writes

    (a, b) -> a - b        // TODO 1, and it is wrong
    Integer::compare       // TODO 2, and it is right

Sorting `[MIN_VALUE, MAX_VALUE, 0]`:

    subtracting() -> [0, 2147483647, -2147483648]      wrong, silently
    comparing()   -> [-2147483648, 0, 2147483647]      correct

`MIN_VALUE - MAX_VALUE` overflows to `1`, so the comparator says `MIN_VALUE > MAX_VALUE`. No
exception. The sort completes and the answer is wrong.

**The shorter one is the broken one**, which is why this survives: it reads as obviously
equivalent, it is three characters shorter, and it works for every value either side of zero that
a test fixture is likely to contain. Item 14's advice is explicit — use the static `compare`
methods, not relational operators or subtraction.

Same trap in `hashCode`, incidentally, and in any "distance" function.

## TODO 3: the size at which the JDK notices

An intransitive comparator — one that never returns `0`, so `compare(x,y)` and `compare(y,x)` both
say "greater" — measured on lists of identical shape:

| n | Result |
| --- | --- |
| 8, 16, 31, 32, 33 | **no exception**, order silently wrong |
| 63 and above | `IllegalArgumentException: Comparison method violates its general contract!` |

**Find the boundary yourself and write it down.** The reason is TimSort: below about 32 elements it
uses binary insertion sort and never merges, and the contract check lives in the merge. No merge,
no check.

So the failure mode is exact and brutal: **your unit test is under the threshold and production is
over it.** A comparator that has been wrong for two years throws the first time a customer has 64
of something. If you have ever seen `Comparison method violates its general contract!` appear in
production from code nobody touched, this is it — the data got bigger, not the code.

And note what the exception is *not*: it is not a bug in TimSort, and the stack trace points at
`java.util.TimSort.mergeLo`, which is why people file JDK bugs about it. It is the JDK telling you
your comparator is inconsistent.

## TODO 4: the comparator decides what a duplicate is

    treeMapSize(String.CASE_INSENSITIVE_ORDER, ["Key", "KEY", "key"])  ->  1
    treeMapSize(naturalOrder(),                ["Key", "KEY", "key"])  ->  3

**`TreeMap` and `TreeSet` define equality by the comparator and never call `equals`.** A `TreeSet`
built with `CASE_INSENSITIVE_ORDER` containing `"key"` answers `contains("KeY")` with `true`, and a
`HashSet` with the same contents answers `false`.

This is Item 14's "`compareTo` should be consistent with `equals`" stated as a consequence rather
than a rule. When they disagree, the same objects behave differently depending on which collection
you put them in — and both behaviours are correct per their own contracts.

[Stage 02](02-contract-matrix.md)'s TODO 4 is the other half: *which* of the colliding keys the map
keeps.

**The JDK does this to itself**, which is the best argument that it is a real hazard rather than a
rule for other people. `BigDecimal` is `Comparable` and deliberately inconsistent with `equals`:

    new BigDecimal("1.0").compareTo(new BigDecimal("1.00")) == 0   // true,  same value
    new BigDecimal("1.0").equals(new BigDecimal("1.00"))           // false, different scale

    new HashSet<>(List.of(bd("1.0"), bd("1.00"))).size()   ->  2
    new TreeSet<>(List.of(bd("1.0"), bd("1.00"))).size()   ->  1

Verified. The same two objects, two entries or one, decided entirely by which collection you
reached for. `BigDecimal`'s javadoc warns about this explicitly, because scale is part of its
identity and not part of its order.

## The old-code exercise

Fix `SumSearch.java:13`. Three defensible answers and you should pick one deliberately:

1. **Sort a copy** — `Arrays.copyOf` first. Costs an allocation, surprises nobody.
2. **Document the mutation** and rename the parameter so the signature admits it.
3. **Require a sorted array** as a precondition, documented, and verify it in an assert.

Option 3 is what `Arrays.binarySearch` itself does, and [stage 05](05-binary-search.md) is about
what that costs when the precondition is not met. Say which you picked and why.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: `Comparator.comparing(...).thenComparing(...)` is composable and allocates a chain of
objects per call — would you hoist it to a `static final`, and does that change anything measurable?
If a broken comparator only throws above 32 elements, what is the cheapest test that would have
caught it? Is "consistent with equals" a rule worth following when the natural ordering genuinely
differs from equality, as with `BigDecimal`'s `1.0` and `1.00`? What does a comparator that reads a
mutable field do to a `TreeMap` when that field changes?
