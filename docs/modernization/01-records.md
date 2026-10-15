# 01. Records

## Where it lands

**`concurrency/Order.java`** is the clearest case in the repo. Seventeen lines: two final fields,
a constructor, and nothing else. No accessors, no `equals`, no `hashCode`, no `toString`. As
written it is a value that cannot be read, compared or printed, which means nothing can have been
using it.

**`Door.java`** is the counter-case, and worth doing second so the contrast lands. It has one
mutable `boolean` and a `toggle()` that flips it. **It is not a record**, and working out why in
one sentence is the exercise.

Also look at the maze `Cell` (see `mousemaze/CellTest`) and `generics/PageObject`.

## The exercise

1. Turn `Order` into a record. Notice what you get for free and what the compiler now forces.
2. `double code` for an order code: decide whether that survives the rewrite. A `double`
   identifier is a bug waiting for a value that does not round-trip.
3. Add a compact constructor that rejects a null or blank name, and write the test first.
4. Leave `Door` alone, and write down why it is the wrong shape for a record.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: what happens to a builder? What does JPA do when you try to make a record an `@Entity`?
What breaks if a field needs to be mutable later, and how expensive is that change compared with
the same change to the hand-written class?
