# 03. Pattern matching

## Where it lands

**`Door.toString()`** is a four-line `if/else` returning one of two strings.

**`algorithm/DecimalToBinary.java`** is the more interesting one, and not only for pattern
matching. Read it before you change anything: the class is called `DecimalToBinary` and the loop
divides by 16. **It prints hex.** There is no test, which is why nobody noticed.

Do the bug first, with a test, as its own commit. Then modernise.

## The exercise

1. `Door.toString()` to an expression. Decide between a ternary, a switch expression and an enum,
   and say why the one you picked wins at two states.
2. Once `Order` is a record (document 01), route it with a record pattern and see what the pattern
   buys over accessors at one level of nesting. It may buy nothing. Say so if it does not.

**The detail to actually learn**: where `case null` goes in a pattern switch, and what a pattern
switch does when you leave it out. Get that wrong and a null stops being a branch and starts being
a `NullPointerException`.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: at what nesting depth does a record pattern stop being readable? What does a long pattern
switch do to a diff when one variant changes?
