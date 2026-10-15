# 07. Accidental quadratics, and where the crossover actually is

**Stage 7 of [the DSA track](INDEX.md)** · new code: `algorithm/Quadratics.java`, four TODOs

## Where it lands

**`ProductsOfArrayElementsTest`**, whose `productsArray()` is O(n²) by construction — it calls
`product(otherValues(...))` once per element, and each call walks the whole array. It also boxes an
`Integer` in a loop at `:46-52`.

[`research.md`](research.md) calls this the single most common real performance bug. It is also the
one that is invisible at test sizes, which is the actual subject of this stage.

## The numbers, measured

`List.contains` inside a loop over the same data, versus a `HashSet`:

| n | contains-in-loop | set | ratio |
| --- | --- | --- | --- |
| 100 | 401 µs | 152 µs | **2.6x** |
| 1,000 | 3.7 ms | 0.5 ms | 7.3x |
| 10,000 | 34.7 ms | 2.1 ms | 16.2x |
| 100,000 | **2,488 ms** | 9.6 ms | **259x** |

And two more, same shape:

    ArrayList.remove(0) x100,000   655 ms      ArrayDeque.poll()   1 ms
    String += x50,000              239 ms      StringBuilder       2 ms

**Read the n=100 row again.** 2.6x, and 400 microseconds. At that size the quadratic version is
*fine*, and "it does not matter" is the correct professional answer — saying so is part of this
stage, not a failure of it.

The danger is not that quadratics are slow. It is that **the ratio where you test and the ratio in
production are a hundred times apart**, so the thing that looked fine in review becomes a two and a
half second request. The crossover sits between n=1,000 and n=10,000 on this machine, which is
exactly the range real collections live in.

Measure these yourself; [doc 34](../modernization/34-measuring-it-properly.md) has the rules, and
these are whole-program measurements rather than microbenchmarks, so wall-clock is right.

## The exercise

**TODO 1 is the quadratic version and you write it on purpose.** You should be able to produce it
from memory, because that is how it gets written: a loop, a `contains`, and nothing that looks
wrong.

**TODO 2 is the rewrite, and the test asserts both return the same list in the same order.** A
rewrite that changes the order is not the same rewrite, and `duplicatesBySet` needs a
`LinkedHashSet` rather than a `HashSet` to keep it — which is [stage 01](01-which-collection.md)'s
decision table being load-bearing two stages later.

**TODO 4 is prefix sums**, which [`research.md`](research.md) rates load-bearing-but-narrow:
`productsExceptSelf` in two passes instead of n passes. One prefix pass, one suffix pass, no
division.

**Do it without division even though division is shorter**, because a single zero breaks the
division trick and the test has one:

    [1, 2, 3, 4]  ->  [24, 12, 8, 6]
    [0, 2, 3, 4]  ->  [24, 0, 0, 0]     one zero: only index 0 survives
    [0, 2, 0, 4]  ->  [0, 0, 0, 0]      two zeros: everything is zero

The prefix/suffix version handles all three without a special case. The division version needs
three branches, and you have to *notice* that it does.

## The catalogue worth carrying

Four shapes. Recognising them on sight is the deliverable:

1. **`collection.contains(x)` inside a loop over a collection** → a `Set` outside the loop
2. **`list.remove(0)` or `list.add(0, x)` in a loop** → `ArrayDeque`
3. **`string += x` in a loop** → `StringBuilder` (the compiler does *not* fix this across
   iterations)
4. **a method that walks the input, called once per element** → that is
   `ProductsOfArrayElements`, and it is the one that hides, because neither the method nor the loop
   looks quadratic alone

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: at n=100 the quadratic is 400 µs — write down the n at which you would actually change the
code, and how you would know n. A `HashSet` costs allocation and hashing — is there a size at which
the `contains` loop genuinely wins, and did you measure it or assume it? What would make shape 4
visible in review, given neither half is wrong on its own? `String +=` is quadratic across
iterations and the compiler cannot fix it — why not?
