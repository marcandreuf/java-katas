# 01. Records

**Java 16** (preview in 14 and 15) · concept stage 01 of [the concept order](00-start-here.md) ·
no build change

> **No safety net:** `Order` has no test. Write one first, in a separate commit, asserting what
> the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why the order matters.

## What this stage is actually about

Five words, defined once:

- a **record** is a class declared by listing its data: `record Order(String name, double code) {}`
- its **components** are the things in that list — here `name` and `code`. Each one becomes a
  `private final` field.
- an **accessor** is the method the compiler writes to read a component. It is called `name()`,
  **not** `getName()`.
- the **canonical constructor** is the one whose parameters are exactly the components, in order.
  The compiler writes it for you.
- a **compact constructor** is a way to add checks to the canonical constructor without
  re-declaring its parameters. Part one, step 4 shows one.

The compiler also writes `equals`, `hashCode` and `toString`, all based on the components, and makes
the class `final`.

That gives one rule, and everything below follows from it: **a record says that the object *is*
its data.** Two records with the same components are equal, and either one can stand in for the
other. If that is true of your class, a record fits. If it is not, no amount of syntax will make
it fit.

## The worked example

`concurrency/Order.java:9-15` has this, written in 2014:

    private final String name;
    private final double code;

    public Order(String name, double code) {
        this.name = name;
        this.code = code;
    }

Two fields and a constructor. **No accessors, no `equals`, no `hashCode`, no `toString`.** Nothing
can read an `Order`, compare it or print it. The only place in the repo that uses it is
`OrdersProcessingSystem.java:11`, as the element type of a queue nothing ever adds to.

Here is the record version, and what `javap -p` shows the compiler generated from it (verified):

    record Order(String name, double code) {}

    final class Order extends java.lang.Record {
      private final java.lang.String name;
      private final double code;
      Order(java.lang.String, double);
      public final java.lang.String toString();
      public final int hashCode();
      public final boolean equals(java.lang.Object);
      public java.lang.String name();
      public double code();
    }

Writing those six members by hand would take about thirty lines, and the 2014 class never had
them. **So this is not a shorter way to write the old class. It is a shorter way to write a class
the repo never had.** The behaviour changes as well as the line count (verified):

    // the 2014 class
    new Order("a", 1).equals(new Order("a", 1))   // false: Object.equals, identity
    new Order("a", 1).toString()                  // "Order@1b6d3586"

    // the record
    new Order("a", 1).equals(new Order("a", 1))   // true
    new Order("a", 1).toString()                  // "Order[name=a, code=1.0]"

That one `false` turning into `true` is the subject of part one, step 2.

## Part one: `Order`

Four commits, in this order. Each one is small, and the order is the point.

1. **Characterize it first.** Write `OrderTest` against the class as it is. It has no accessors, so
   there is almost nothing to assert, and **finding that out is the first lesson**. Three things
   you can pin down:
   - `new Order(null, 1)` and `new Order("  ", 1)` construct without complaint.
   - two orders built from the same arguments are **not** `equals`.
   - `toString()` starts with `"Order@"`.

   Commit that on its own. It is green, and it records that today's `Order` behaves as nothing
   more than a memory address.

2. **Turn it into a record.** One line, as in the worked example. The equality assertion from
   step 1 now fails, and **this is the one case where you change an assertion in the same commit**.
   You did not refactor the class. You gave it value semantics, on purpose, and the flipped
   assertion is the evidence. Say so in the commit message ("equality is now by value, which is
   the point"), and change only that assertion. If any other assertion breaks, something else has
   changed and you should stop and find out what. The `toString` assertion also changes; flip it
   the same way and name it in the message.

   This is safe because nothing in the repo reads `Order`. Do the same thing to a class that is a
   `HashMap` key somewhere and the map starts merging entries it used to keep apart.

3. **`double code` is a bug, so fix it.** An order code is an identifier, and a `double` cannot
   hold every identifier (verified):

       (long) (double) 9007199254740993L          // 9007199254740992: the last digit is gone
       new Order("a", 9007199254740992L)
           .equals(new Order("a", 9007199254740993L))   // true: two orders, one identity
       new Order("a", 20240117)                    // prints "Order[name=a, code=2.0240117E7]"

   The record makes this worse in a way the old class could not, because record `equals` compares
   `double` components with `Double.compare`, not `==`:

       new Order("a", 0.0).equals(new Order("a", -0.0))                // false, though 0.0 == -0.0
       new Order("a", Double.NaN).equals(new Order("a", Double.NaN))   // true, though NaN != NaN

   Choose `String` or `long`, and write down why in one line: does a code ever have a leading
   zero, a letter, or more than 18 digits? First write the test that shows two different codes
   colliding, then change the type in a separate commit.

4. **Reject a blank name, test first.** Write a test that expects `new Order(" ", 1)` to throw.
   It fails, because step 1 pinned down that it doesn't. Then add a compact constructor:

       record Order(String name, String code) {
           Order {
               if (name == null || name.isBlank())
                   throw new IllegalArgumentException("name is blank");
           }
       }

   No parameter list and no assignments. The body runs **before** the fields are set, and the
   compiler adds `this.name = name;` and the rest at the end. So you may *reassign the parameter*
   (`name = name.strip();` compiles, and the stripped value is what gets stored), but
   `this.name = ...` is a compile error: `cannot assign a value to final variable name`. That
   error is the compiler telling you the assignment is its job.

   The step 1 assertion that blank names are accepted now fails as well. This is a behaviour fix,
   so it gets its own commit, and the assertion changes in that commit and no other.

## Part two: three classes that are not this exercise

The rule says a record is right when two objects with the same data can stand in for each other.
Test each of these against it before reading the answer.

**`Door.java`: mutable, so no.** `toggle()` at `Door.java:15-17` flips a field in place, and a
record's fields are `final`. You *could* write `record Door(boolean open)` with a `toggle()` that
returns a new `Door`. Then look at how `The100DoorsCyberDojoTest.java:98-99` uses it:

    currentDoor = doors.get(doorNumber-1);
    currentDoor.toggle();

That code changes door #7 by holding door #7. With a record, `toggle()` returns a new door and the
list never sees it. Worse, all hundred closed doors are now *equal* (verified, with a record `Door`):

    doors.indexOf(doors.get(5))      // 0: door #6 is "found" at position #1
    new HashSet<>(doors).size()      // 1: a hundred doors, one element

Door #6 and door #7 are different doors even when both are closed. **Their identity is the data
that matters, and a record throws it away.** That is the one sentence to write down. ([Doc
03](03-pattern-matching.md) modernizes `Door.toString()` without making `Door` a record, and
[doc 04](04-switch-and-text-blocks.md) explains why a switch doesn't help either.)

**`generics/PageObject.java`: built to be extended, so no.** It is an empty class, and
`FirstPageObject.java:3` is `class FirstPageObject extends PageObject`. Records are implicitly
`final`. Make `PageObject` a record and `FirstPageObject` stops compiling:
`cannot inherit from final PageObject` (verified). A page object exists to wrap a browser
session, which is the opposite of a value. Look at it; don't change it. It needs a Selenium driver
the repo does not depend on, so it has no test and won't get one
([`../testing-first.md`](../testing-first.md)).

**`mousemaze/Cell.java`: already a record, and the right one.** Four booleans, one for each
border. A cell with a left border is the same as any other cell with a left border, so equality by
value is exactly what you want. `CellTest.java:64-69` (`cells_with_the_same_borders_are_equal`)
tests behaviour that no one wrote: the record supplied it. The 2014 TODO said "one cell type per
border combination", which is sixteen classes; the record is one line. Read it and leave its TODOs
alone, because [DSA stage 11](../dsa/11-reachability.md) owns them. It also shows that a record can
have static factories (`Cell.open()`, `Cell.closed()`) and ordinary methods (`blocks`).

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: a record is only *shallowly* immutable. What happens to `record Basket(List<String>
items)` when the caller keeps a reference to the list they passed in, and how does a compact
constructor fix it? ([Doc 16](16-collection-factories.md) gives you the one-call answer.) The
accessor is `name()`, not `getName()`: what does that break in a framework that expects JavaBeans
getters? What happens to a builder? What does JPA do when you try to make a record an `@Entity`?
What breaks if a field needs to be mutable later, and how expensive is that change compared with
the same change to the hand-written class?
