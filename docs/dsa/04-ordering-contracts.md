# 04. `Comparable` and `Comparator` contracts

**Stage 4 of [the DSA track](INDEX.md)** · new code: `ordering/Ordering.java`, four TODOs · test:
`ordering/OrderingTest.java`, 6 tests, `@Disabled` · old code: `algorithm/SumSearch.java`, 4 tests,
all green (all verified)

## Where it lands

New code, plus one old-code target: **`algorithm/SumSearch.java:13`** calls
`Arrays.sort(sequence)` on the caller's array (verified). That's undocumented argument mutation.
The method is called `search`, nothing in its signature says "and I'll reorder your input", and
sorting is the subject of this stage.

The comparator contract is an OCP Java SE 21 objective and *Effective Java* Item 14. It's here
because every failure below **passes a small unit test**.

## What this stage is actually about

Four words, defined once:

- a **comparator** is a function `compare(a, b)` returning a negative number, zero or a positive
  number for "a first", "tie", "b first". `Comparable` is the same thing built into the class, as
  `compareTo`.
- the **contract** is three promises: swapping the arguments flips the sign; if a is before b and b
  is before c, then a is before c; and two things that tie are interchangeable in every other
  comparison. Break any of them and "sorted" stops meaning anything.
- **consistent with `equals`** means `compare(a, b) == 0` exactly when `a.equals(b)`. It's
  recommended, not required, and the JDK itself breaks it.
- **TimSort** is the sort behind `List.sort` and `Arrays.sort` for objects. It finds already-sorted
  runs in the input and merges them.

That gives one rule, and everything below follows from it: **the JDK trusts your comparator, and
checks almost nothing.** A broken one doesn't throw, it sorts wrong. Sorted collections don't ask
`equals`, they ask the comparator. And the one check that does exist is a side effect of merging,
so whether you ever see it depends on the size and shape of your data.

## The worked example: `BigDecimal`, which breaks the recommendation on purpose

Not one of the TODOs: the JDK doing it to itself (verified):

    new BigDecimal("1.0").compareTo(new BigDecimal("1.00"))   // 0      same value
    new BigDecimal("1.0").equals(new BigDecimal("1.00"))      // false  different scale

    new HashSet<>(List.of(bd("1.0"), bd("1.00"))).size()      // 2
    new TreeSet<>(List.of(bd("1.0"), bd("1.00"))).size()      // 1

The same two objects make two entries or one, decided entirely by which collection you reached
for. `HashSet` asks `equals` (after `hashCode`, as [stage 03](03-equals-and-hashcode.md) showed);
`TreeSet` asks `compareTo` and never calls `equals`. Both answers are correct by their own
contracts. `BigDecimal`'s javadoc warns about this, because scale is part of its identity and not
part of its order.

Keep that in mind for TODO 4: it's the same split, with a comparator you choose.

## TODO 1 and 2: the comparator everybody writes

    (a, b) -> a - b        // TODO 1, and it's wrong
    Integer::compare       // TODO 2, and it's right

Sorting `[MIN_VALUE, MAX_VALUE, 0]` (verified):

    subtraction:       [0, 2147483647, -2147483648]      wrong, silently
    Integer::compare:  [-2147483648, 0, 2147483647]      correct

`MIN_VALUE - MAX_VALUE` overflows to `1` (verified), so the comparator says `MIN_VALUE` comes after
`MAX_VALUE`. No exception. The sort completes, and the answer is wrong.

**The shorter one is the broken one**, which is why it survives review: it reads as obviously
equivalent, and it works for every value a test fixture is likely to contain. Item 14's advice is
explicit: use the static `compare` methods, not subtraction or relational operators.

(**A correction to the old version of this doc**, which said the "same trap" is in `hashCode`.
It isn't: `hashCode` overflows all the time, deliberately, and that's harmless because a hash code
only has to be consistent, not ordered. The trap is anywhere a subtraction's *sign* is used as an
answer: comparators, and "distance" functions compared against zero.)

## TODO 3: the size at which the JDK notices

The test's broken comparator is `(x, y) -> x < y ? -1 : 1`. **A correction to the old version of
this doc**, which said this comparator "never returns 0, so `compare(x,y)` and `compare(y,x)` both
say greater". That's only true when x and y are *equal*: then each says the other comes first,
which breaks the first promise. For unequal values it's a perfectly good comparator.

**And so on a small list it sorts correctly.** The old version said "order silently wrong" below
the threshold, and so does the comment on `OrderingTest.java:45`. Verified: the test's 8-element
input `[0, 1, 1, 0, 2, 1, 2, 1]` comes out `[0, 0, 1, 1, 1, 1, 2, 2]`, correct. Over 20 random
seeds and every size below 32, no list came out wrong. The bug is real, and on small inputs it's
invisible in *both* ways: no exception, and no wrong answer.

On larger lists, TimSort sometimes notices. Using the test's data shape (`Random(1)`, values 0 to
2), verified:

| n | Result |
| --- | --- |
| 1 to 45 | **no exception** |
| 46 to 3000, every size tried | `IllegalArgumentException: Comparison method violates its general contract!` |

**A correction to the old version**, which put the boundary at 63 and above. For this data it's
46. And it isn't a fixed number at all: over seeds 1 to 20, the first size that threw ranged from
**32 to 69**, and for 12 of the 20 seeds, some larger sizes *didn't* throw (verified, sizes up to
500). One rule did hold for every seed: **nothing below 32 ever threw.**

Find the reason in the source before reading on: `java.util.TimSort`, the constant `MIN_MERGE`.
Below 32 elements, TimSort sorts the whole list with binary insertion sort and never merges. The
contract check lives in the merge (every exception here came from `TimSort.mergeLo`, verified), and
even when merging, it only fires if the merge happens to trip over the inconsistency. **No merge,
no check; a merge that gets lucky, no check either.**

So the failure mode is exact and brutal: **your unit test is under the threshold and production is
over it.** A comparator that has been wrong for two years throws the first time a customer has 50
of something. If you've seen `Comparison method violates its general contract!` appear in
production from code nobody touched, this is it: the data got bigger, not the code. It isn't a
TimSort bug, even though the stack trace points into `java.util.TimSort`. It's the JDK telling you
your comparator is inconsistent.

`OrderingTest` uses sizes 8 and 100, so it's on the safe side of every boundary measured here.

## TODO 4: the comparator decides what a duplicate is

Verified:

    TreeMap with CASE_INSENSITIVE_ORDER, keys "Key", "KEY", "key"   ->  1 entry
    TreeMap with naturalOrder(),         keys "Key", "KEY", "key"   ->  3 entries

**`TreeMap` and `TreeSet` define equality by the comparator and never call `equals`.** A `TreeSet`
built with `CASE_INSENSITIVE_ORDER` containing `"key"` answers `contains("KeY")` with `true`, and a
`HashSet` with the same contents answers `false` (verified). That's the worked example's split
again, with a comparator you chose instead of one `BigDecimal` chose.

[Stage 02](02-contract-matrix.md)'s TODO 4 is the other half: *which* of the colliding keys the map
keeps.

## The commits for `Ordering`

1. **Delete the `@Disabled`**, and implement TODOs 1 and 2 together: their tests are a pair.
2. **TODO 3.** Then find your own boundary for the test's data, and write it in the commit
   message with the seed you used.
3. **TODO 4.**

## The old-code exercise: `SumSearch`

**First, why the four green tests can't see the bug.** Every test uses the same fixture,
`{1, 2, 4, 6}`, which is already sorted. Sorting it changes nothing, so no test can tell whether
the method mutated it. Verified: given `{6, 1, 4, 2}`, `search` returns `"1,4"` and the caller's
array is now `[1, 2, 4, 6]`.

1. **Characterize.** Add a test that passes an unsorted array and asserts it comes back
   **sorted**: today's behaviour, recorded on purpose. It passes.
2. **Fix it**, choosing one of three defensible answers deliberately:
   - **Sort a copy**: `Arrays.copyOf` first. It costs an allocation, and it surprises nobody.
   - **Document the mutation**, and rename the parameter so the signature admits it.
   - **Require a sorted array** as a documented precondition, the way `Arrays.binarySearch` does.
     (`binarySearch` documents it and never checks it. If you add an `assert`, remember that
     asserts only run with `-ea`; find out whether your test run has it on.)

   With the first answer, step 1's assertion flips in this commit. With the other two, it stays,
   and step 1's test becomes the documentation. Say which you picked and why.
   [Stage 05](05-binary-search.md) is about what the third answer costs when the precondition
   isn't met.

While you have the file open, one thing to note and **not** fix here: `search(new int[]{MAX_VALUE,
1}, MIN_VALUE)` returns `"1,2147483647"` (verified), because `MAX_VALUE + 1` overflows to
`MIN_VALUE`. That's the TODO 1 bug in a different place, and [stage 09](09-integer-overflow.md)
owns it.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: `Comparator.comparing(...).thenComparing(...)` is composable, and builds a chain of
objects. Would you hoist it to a `static final`, and does that change anything you can measure? If
a broken comparator never throws below 32 elements and doesn't sort wrong either, what's the
cheapest test that would catch it? (Hint: you don't need a sort to test a comparator.) Is
"consistent with `equals`" worth following when the natural order genuinely differs from
equality, as with `BigDecimal`? What does a comparator that reads a mutable field do to a `TreeMap`
when that field changes?
