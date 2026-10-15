# 04. Switch expressions and text blocks

> **No safety net:** `DecimalToBinary`, `SimpleThreads` have no tests. Write them first, in a
> separate commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why the
> order matters.

## Where it lands

Switch material is thin in this repo, which is a finding rather than a gap: the old code reaches
for `if/else` chains and loops where a switch would have read better. `Door.toString()` is the
clearest one, and `The100DoorsCyberDojoTest` builds state with nested loops.

Text blocks have no target in `src/main` at all. The repo prints with `System.out.println` in 56
places and never builds a multi-line string.

## The exercise

1. Convert what switches there are to expressions, and note where the compiler now demands
   exhaustiveness that the statement form let you skip.
2. **Create the text-block target rather than pretending there is one.** `SimpleThreads` and
   `DecimalToBinary` both have `main` methods with no usage message. Add one, as a text block,
   and get the indentation right rather than approximately right.

**Incidental whitespace stripping is the one text-block rule people get wrong.** Write a test that
compares the string character for character. That is the cheapest way to learn where the closing
delimiter puts the margin.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: when does `formatted()` inside a text block read worse than plain concatenation? What
does a text block do to a `git blame` on the line you changed?
