# 09. Stream and Optional additions since 8

## Where it lands

This is the richest seam in the repo. The code is from 2014 and predates the team habit of
reaching for streams, so nearly every loop is a candidate.

**`collections/FindDups.java`** builds an anonymous `Comparator<String>` implementing
`compareToIgnoreCase`. That is `String.CASE_INSENSITIVE_ORDER`, which has existed since Java 2.
The whole `main` is a `distinct` and a `count` over `args`.

**`collections/RandomFileLines.java`** is the one to spend time on. It has four separate problems
stacked on top of each other:

- `BufferedReader` closed in a hand-written `finally` that **throws `NullPointerException` if the
  open itself failed**, masking the real exception. This is what try-with-resources exists for.
- `catch (Exception e) { System.out.println(...); throw e; }`, which logs and rethrows, so the
  same failure gets reported twice.
- `rline.nextInt(lstLines.size() - 1)` **never selects the last line.** Off-by-one, no test.
- a capacity guess of `(fin.length() / avgCharsPerLine) * 2` that `ArrayList` does not need.

`Files.readAllLines` or `Files.lines` collapses most of it. Do the off-by-one as its own commit
with a test, before the rewrite, so the fix is visible.

**`algorithm/FairWorkLoad.java`**, **`algorithm/DuplicateNumber.java`** and
**`ProductsOfArrayElementsTest`** are index-loop algorithms. Some will read better as streams and some
will not. **Deciding which is the exercise**, not converting all of them.

## The exercise

1. `FindDups` to a stream, and delete the anonymous comparator.
2. `RandomFileLines`: the off-by-one, then try-with-resources, then the stream.
3. For `Optional`, the rule is **no `isPresent()` followed by `get()`**. If you write that pair you
   have used `Optional` as a null check with extra syntax and got nothing for it.
4. Try `mapMulti` somewhere and say honestly whether it beat `flatMap` at this size. It usually
   does not, and "I tried it and it was not worth it" is the better answer.

## What got worse

- _(fill this in)_

Prompts: what does a four-stage stream do to a stack trace? When is a plain `for` loop the right
answer, and how do you tell before you have written both?
