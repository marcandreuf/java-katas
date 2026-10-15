# 05. `var`, and the diamond

> **No safety net:** `FindDups`, `RandomFileLines` have no tests. Write them first, in a
> separate commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how, and why the
> order matters.

## Where it lands

Eight sites in this repo still write the type argument twice. The clearest:

- `SumWordsLength`: `new HashSet<Future<Integer>>()`
- `FindDups`: `new TreeSet<String>(comparator)`
- `RandomFileLines`: `new ArrayList<String>((int) listSize)`

The diamond arrived in Java 7 and this code is from 2014, so these are not a modern-Java question
at all. They are a **how long does an idiom survive after it is obsolete** question, which is the
more useful one when you are looking at somebody else's codebase.

## The exercise

**The code is not the deliverable here.** Apply the diamond everywhere first, since that one is
unambiguous. Then apply `var` everywhere it compiles, take it back out wherever the code got
worse, and write the three cases down below.

"Where is this the wrong choice" is the harder half of knowing a feature, and `var` is the cheapest
topic in this repo to have a real opinion about.

## Where it made the code worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: what happens to `var` with a diamond on the right-hand side, where the two features cancel
out? With a method whose return type you cannot see from the call site? With a numeric literal,
where `var` silently picks `int`? What does it do to a review in a browser with no type hints?
