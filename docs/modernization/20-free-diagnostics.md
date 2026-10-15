# 20. The diagnostics you get for free

**Java 14, 15** · stage 9 of [the release-ordered track](INDEX.md) · `--release 21`, no build
change

## Where it lands

Nowhere, and that is the point. **Helpful NullPointerExceptions (JEP 358) required no code
change at all.** They arrived in Java 14 behind a flag and became the default in 15, which means
this repo has had them since whichever JDK you first built it with, and nothing in it was written
to take advantage of them because nothing had to be.

It is the cheapest improvement in the whole 8→26 range, and most people have never deliberately
read one.

New code: **`diagnostics/NullPaths.java`**, three TODOs.

## The exercise

A three-link chain — `Person` → `Company` → `Address` → `String` — and a one-line walk:

    person.employer().address().city().toUpperCase(Locale.ROOT);

**TODO 2 is the exercise and it happens before you run anything: predict the two messages.**
Write your prediction down, then run `NullPathsTest`. Here is what the JVM actually says:

| Null link | Message |
| --- | --- |
| `employer` | `Cannot invoke "…Company.address()" because the return value of "…Person.employer()" is null` |
| `address` | `Cannot invoke "…Address.city()" because the return value of "…Company.address()" is null` |
| `city` | `Cannot invoke "String.toUpperCase(java.util.Locale)" because the return value of "…Address.city()" is null` |

Two halves, and **people read them backwards.** The first half is what you *tried to call*. The
second half, after `because`, is what *was null*. The thing you need to go and fix is always in
the second half.

Before Java 14 all three of those were the same message:

    java.lang.NullPointerException
            at org.mandfer.practices.diagnostics.NullPaths.cityOf(NullPaths.java:42)

One line number, four dereferences on it, and no way to tell which. The standard response was to
split the line across four statements and redeploy to find out — in production, on a bug you could
not reproduce locally. **That is the workflow this feature deleted**, and it is worth having felt
the difference once.

**TODO 3** is the `Optional.map` version of the same walk. Write it, count the lines, and decide
whether you would take it at three links. The answer is genuinely not obviously yes:
`Optional.ofNullable(p).map(Person::employer).map(Company::address)…` is four lines that cannot
throw, against one line that throws a message naming the exact problem. The rule from
[doc 09](09-streams-and-optional.md) applies — **no `isPresent()` then `get()`** — and the harder
question is whether `Optional` is the right tool for defending a chain at all, or whether the
chain should not have had nulls in it.

## The flag, and why you might turn it off

    java -XX:-ShowCodeDetailsInExceptionMessages ...

That reverts to the old message. There is one real reason to want it: **the message contains
expressions from your source**, including local variable names, and those messages end up in logs
that leave your machine. It is a small information disclosure, not a large one, but it is why the
feature spent a release behind a flag before becoming the default.

It is worth knowing the flag exists for a second reason: if you are reading a stack trace with a
bare `NullPointerException` and no message on a modern JDK, **somebody turned this off**, and
that is now a fact about their deployment rather than a fact about Java.

## The rest of the free tier

No exercise for these; know that they exist and what they changed.

- **Compact strings** (Java 9, JEP 254). `String` stores Latin-1 in a `byte[]` instead of always
  `char[]`, so most strings halved in memory with no code change. One of the largest free wins in
  Java's history and nobody had to read a release note.
- **G1 as the default collector** (Java 9, JEP 248), replacing Parallel GC. Your latency profile
  changed on upgrade without you asking. [Stage 20](INDEX.md) is about that whole thread, through
  to Java 26.
- **Flight Recorder became free** (Java 11, JEP 328) — it was a commercial feature.
  [Doc 11](11-jvm-diagnostics.md) uses it.
- **`@Deprecated(forRemoval = true, since = "…")`** (Java 9, JEP 277). Deprecation finally
  distinguishes "we would rather you did not" from "this is going away", which is the distinction
  [stage 19](INDEX.md) is built on.

## What got worse

- _(fill this in)_

Prompts: a helpful NPE message quotes your source expressions into a log — in what deployment is
that a problem you would have to argue about? If the message is this good, what is the remaining
case for an explicit null check at the top of the method? Does a better exception message make
`Optional` less necessary, or just less urgent?
