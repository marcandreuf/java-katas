# DSA track: the contracts, not the algorithms

**Twelve stages.** The thesis, argued at length in [`research.md`](research.md) and tested against
the OCP objectives and *Effective Java*: **almost none of the algorithms are load-bearing, and
almost all of the contracts are.** Nobody shipping Java implements a hash table or a shortest-path
search. What ships, repeatedly and wrongly, is a `contains` inside a loop, a `binarySearch` on
unsorted input, a key whose `hashCode` disagrees with its `equals`, a comparator that overflows,
and a recursion whose depth is the size of the input.

Every one of those is a contract failure. **Most of them are in this repo right now.**

## How this track differs from the modernization one

[The modernization track](../modernization/INDEX.md) rewrites old code into current idiom. This one
does not rewrite anything: it **writes the test that was never there**, and most of the time the
test fails. Of the existing code the reuse table below lists, four files have no test at all
(`NumberReverse`, `DuplicateNumber`, `StringRecursiveReversal`, `PascalsTriangleRecursive`), and
three of the ones that do are green while the code under them is wrong (`BinarySearch`,
`FairWorkLoad`, `CircularList`).

So the convention is inverted. There, deleting `@Disabled` starts a modernization. Here, deleting
`@Disabled` usually turns a green build red, and *that is the deliverable* — the fix comes after.

**The two tracks write opposite kinds of test, and the difference matters.** This track writes the
test the code *should* pass, so it goes red and stays red until the bug is fixed — `BinarySearch`
is the example. The modernization track needs the other kind: a **characterization** test that
asserts what the code does *today*, bugs and all, so that a refactor underneath it can be proved
not to have changed anything. Writing the aspirational test before a refactor gives you a red
build and no way to tell the refactor from the fix.
[`../testing-first.md`](../testing-first.md) covers both and says which to reach for.

## The stages

| # | Stage | Lands on | Code |
| --- | --- | --- | --- |
| 01 | [Which collection, and what it costs](01-which-collection.md) | `CircularList`, `MyArrayList`, measurement | runnable, no tests |
| 02 | [The contract matrix: nulls, order, and refusals](02-contract-matrix.md) | new | yes |
| 03 | [`equals`, `hashCode`, and the 65x `Comparable` surprise](03-equals-and-hashcode.md) | new | yes |
| 04 | [`Comparable` and `Comparator` contracts](04-ordering-contracts.md) | `SumSearch` | yes |
| 05 | [Binary search: the contract, and the bug in this repo](05-binary-search.md) | **`BinarySearch.java`** | yes |
| 06 | [Binary search over an answer space](06-answer-space-search.md) | `FairWorkLoad` | yes |
| 07 | [Accidental quadratics, and the real crossover](07-accidental-quadratics.md) | `ProductsOfArrayElements` | yes |
| 08 | [Recursion depth, and the exponential nobody measured](08-recursion-depth.md) | `StringRecursiveReversal`, `PascalsTriangleRecursive` | yes |
| 09 | [Integer overflow is an algorithm bug](09-integer-overflow.md) | `NumberReverse`, `DuplicateNumber` | yes |
| 10 | [Structures you assemble from the JDK](10-jdk-assemblies.md) | new | yes |
| 11 | [Reachability, not Dijkstra](11-reachability.md) | `mousemaze/` (greenfield, has a spec) | yes |
| 12 | [What we are deliberately not teaching](12-not-teaching.md) | paper | written answers |

**All twelve are written.** Read them in order: 02, 03 and 04 are one argument in three parts, 05
is where it lands on code somebody already shipped, and 12 is the stage to disagree with.

## Reuse, which is most of the point

This track adds eleven exercise classes with TODOs: one in each of stages 02 to 10, plus `Cell`
and `Maze` in stage 11. Everything else lands on code that has been in the repo since 2014:

| Existing file | Stage | What it is for |
| --- | --- | --- |
| `algorithm/BinarySearch.java` | **05** | Broken and green. Misses 5 of 12 elements in its own fixture. |
| `algorithm/FairWorkLoad.java` | 06 | Genuinely binary search over an answer space. Its `max()` is also wrong. |
| `algorithm/NumberReverse.java` | 09 | Silent overflow, no test. |
| `algorithm/DuplicateNumber.java` | 09 | Overflow, no test. Valid input `1..n` plus a duplicate goes wrong from n = 46,341, before the sum overflows. |
| `algorithm/StringRecursiveReversal.java` | 08 | Recursion depth is the input length. |
| `algorithm/PascalsTriangleRecursive.java` | 08 | Exponential, and the honest fix is "do nothing". |
| `algorithm/SumSearch.java` | 04 | Sorts the caller's array. Undocumented mutation. |
| `CircularList.java`, `algorithm/MyArrayList.java` | 01 | Hand-rolled structures. The lesson is "don't". |
| `mousemaze/` | 11 | In 2014, only `Rules.txt` and two test files with zero `@Test` methods. |
| `ProductsOfArrayElementsTest` | 07 | O(n²) by construction. |

Named as toys and given no stage: `PascalsTriangle` and `RotateMatrix90DegreeInPlace`. Stage 12
says why. (`FindMiddleIndex` used to be on this list. It's now
[modernization doc 09](../modernization/09-streams-and-optional.md)'s `OptionalInt` exercise.)

Owned by the other track, cross-referenced rather than duplicated: `DecimalToBinary` (broken base 16, not even hex —
[doc 03](../modernization/03-pattern-matching.md)), `MyArrayList` and `CircularList` as rewrites
([doc 10](../modernization/10-sequenced-collections-and-httpclient.md)), view semantics
([doc 16](../modernization/16-collection-factories.md)), and measurement discipline
([doc 34](../modernization/34-measuring-it-properly.md)).

## The two rules, same as the other track

1. **One concept per commit.** The diff is the deliverable.
2. **Every stage ends with "What got worse", and you fill it in.** Twelve stages of contracts you
   can recite is worth less than three you have been burned by.

Needs JDK 26, same as the rest of the repo. See [`../env-setup.md`](../env-setup.md).
