# 07. Accidental quadratics, and how big n has to be before they matter

**Stage 7 of [the DSA track](INDEX.md)** · new code: `algorithm/Quadratics.java`, four TODOs ·
lands on **`ProductsOfArrayElementsTest`**

## What this stage is actually about

Five words, defined once:

- **O(n²)**, or **quadratic**, means doubling the input roughly quadruples the time. **O(n)**, or
  **linear**, means doubling it roughly doubles the time.
- an **accidental quadratic** is a linear-looking loop whose body is itself linear: `contains` on a
  `List`, `remove(0)` on an `ArrayList`, `+=` on a `String`. Neither half looks wrong on its own.
- the **ratio** is quadratic time divided by linear time at the same n. It grows roughly in
  proportion to n, so **no single ratio describes the bug**.
- a **prefix pass** walks the array once, left to right, keeping a running result (a sum or a
  product) so that each position knows everything to its left. A **suffix pass** does the same
  from the right.
- a **cold** measurement is the first run in a fresh JVM, before the JIT compiler has done
  anything. A **warm** one comes after the code has run enough times to be compiled.

That gives one rule, and everything below follows from it: **an accidental quadratic is a fact
about the code; whether it matters depends on n, so find out n before you decide.** At test sizes
it nearly always looks fine, and "at n = 100 it doesn't matter" is often the correct professional
answer. The danger is that the n you test with and the n in production can be a thousand times
apart.

## The worked example: `ArrayList.remove(0)`

Not the exercise. A queue drained from the front, two ways:

    List<Integer> list = new ArrayList<>(...);  while (!list.isEmpty()) list.remove(0);
    ArrayDeque<Integer> q = new ArrayDeque<>(...); while (q.poll() != null) ;

`remove(0)` shifts every remaining element one slot to the left, so draining n elements copies
about n²/2 of them. `ArrayDeque.poll()` moves a head index. Measured cold, n = 100,000, three
runs (verified):

    ArrayList.remove(0)   940 ms    773 ms    810 ms
    ArrayDeque.poll()      14 ms     11 ms     12 ms        ratio 65-69x

And the third shape, building a 50,000-character string (verified, same three runs):

    String +=             382 ms    275 ms    305 ms
    StringBuilder           5 ms      2 ms      2 ms        ratio 74-138x

The fix for each one is the obvious one-line change. The skill is noticing them, because the code
doesn't look slow: a `while` loop and a method call. (The `String +=` case is why "the compiler
turns `+` into a `StringBuilder`" doesn't save you. It does that within one expression, so every
iteration of the loop still copies the whole string built so far.)

These numbers were taken on a 10-core machine **while other JVMs were running**, so they're
noisier than they'd be on an idle machine. Treat the ratios as the result, not the milliseconds.
[Doc 34](../modernization/34-measuring-it-properly.md) has the rules for measuring properly. These
are whole-program timings, not microbenchmarks.

## The measurement for the exercise's shape

`List.contains` inside a loop over the same data, against a `HashSet`, on n distinct values (the
worst case for `contains`). Three cold runs, and one warm (verified):

| n | contains, cold | set, cold | ratio, cold (3 runs) | ratio, warm |
| --- | --- | --- | --- | --- |
| 100 | 0.5–2.3 ms | 0.2–3.9 ms | **0.6x – 2.7x** | 3x |
| 1,000 | 3–20 ms | 0.2–0.3 ms | 14x – 101x | 4x |
| 10,000 | 30–94 ms | 1.6–16 ms | 2x – 32x | 90x |
| 100,000 | **3.7–5.0 s** | 15–24 ms | **157x – 243x** | **2,372x** |

**Read the n = 100 row first.** In one of the three runs the *set* was slower. At that size the
difference is smaller than the noise, the whole thing costs about a millisecond, and "it doesn't
matter" is the right answer. Saying so is part of this stage.

**Then the bottom row.** Four to five seconds, from the same loop that took a millisecond in the
test. The **old version of this doc** gave one number for each cell (2.6x at 100, 259x at 100,000)
and a "crossover between 1,000 and 10,000". The table above shows why one number per cell isn't
reproducible, and it doesn't show a crossover either: the ratio keeps growing with n. What *is*
stable is the shape: a few milliseconds at n = 1,000, and several seconds at n = 100,000.
**Somewhere between those two n stops being a test size and becomes a production size.** That range is
where most real collections are.

## Where it lands

`ProductsOfArrayElementsTest.java` (in `src/test/java/org/mandfer/practices/`), whose
`productsArray()` (lines 60-66) is O(n²) by construction. It calls `product(otherValues(...))` once
per element, and each call walks the whole array. `product` (lines 46-52) also accumulates into an
`Integer` (line 47), so every multiplication unboxes and boxes again.

**Note what kind of code this is: there's no production class.** The quadratic lives *inside the
test*, and it runs on a four-element array, `{1, 3, 5, 2}`. As written it can't hurt anyone. It's
here because it's the fourth and hardest shape (see below) in its purest form, and because
`Quadratics.productsExceptSelf` (TODO 4) is the production version it never had.

## The exercise: `Quadratics`, test first

`QuadraticsTest` has six tests and is `@Disabled`. Delete that line to begin (verified). Commits, in
this order:

1. **TODO 1 and TODO 2 together**: `duplicatesByContains`, then `duplicatesBySet`. Write TODO 1, the
   quadratic one, **on purpose**. You should be able to write it from memory, because that's how it
   gets written: a loop, a `contains`, and nothing that looks wrong. TODO 2 is the rewrite, and
   `both_versions_agree` asserts the **same list in the same order** (`[2, 1]` for
   `[1, 2, 3, 2, 1, 4]`). A rewrite that changes the order isn't the same rewrite.
   [Stage 01](01-which-collection.md)'s table tells you which `Set` keeps it.
2. **TODO 3, `firstRepeatIndex`**: one pass, one `Set`, and it returns the index, not the value.
3. **TODO 4, `productsExceptSelf`**: one prefix pass and one suffix pass, no division. Do it without
   division even though division is shorter, because one zero breaks the division trick, and the
   test has zeros:

        [1, 2, 3, 4]  ->  [24, 12, 8, 6]
        [0, 2, 3, 4]  ->  [24, 0, 0, 0]     one zero: only its own position survives
        [0, 2, 0, 4]  ->  [0, 0, 0, 0]      two zeros: everything is zero

   The prefix/suffix version handles all three without a special case. The division version needs
   three branches, and you have to *notice* that it needs them.
4. **Then the old test, characterize first.** Before `ProductsOfArrayElementsTest` changes at all,
   add a case with a repeated value. [Modernization doc 09](../modernization/09-streams-and-optional.md)
   shows why: for `{2, 2, 3}`, `ArrayUtils.removeElement` gives `[6, 6, 4]`, which is right, and the
   obvious stream replacement gives `[3, 3, 4]`, which is wrong (verified there). `{1, 3, 5, 2}` can't
   tell them apart. Then decide what that test is for, now that a real implementation exists:
   point it at `Quadratics.productsExceptSelf`, or delete it. A test whose code under test is its own
   private helper is testing itself. Write down which you chose.

## The catalogue worth carrying

Four shapes. Recognising them on sight is the deliverable:

1. **`collection.contains(x)` inside a loop over a collection** → a `Set` built once, outside the
   loop
2. **`list.remove(0)` or `list.add(0, x)` in a loop** → `ArrayDeque`
3. **`string += x` in a loop** → `StringBuilder`
4. **a method that walks the input, called once per element** → that's `productsArray`, and it's
   the one that hides, because neither the method nor the loop looks quadratic on its own

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: at n = 100 the quadratic costs about a millisecond. Write down the n at which you'd change
the code, and how you'd find out what n is in production. A `HashSet` costs allocation and hashing:
is there a size at which the `contains` loop genuinely wins, and did you measure it or assume it?
(One run above says yes, at 100, and two say no.) What would make shape 4 visible in review, when
neither half is wrong on its own? The prefix/suffix version is linear and correct, and it's also
harder to read than `product(otherValues(...))`: at four elements, which would you rather maintain?
