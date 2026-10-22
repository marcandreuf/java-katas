# 20. The diagnostics you get for free

**Java 14** (helpful `NullPointerException`s, JEP 358; on by default from 15) · **Java 9, 11**
(the rest of the free tier) · stage 9 of [the release-ordered track](INDEX.md) · compiles at
`--release 21`, no build change

New code only, with tests already written, so there's no safety net to build first.

## What this stage is actually about

Five words, defined once:

- a **helpful NPE message** is the text the JVM now puts in a `NullPointerException` it throws
  itself: what it was trying to do, and what was null. Before JEP 358, `getMessage()` was `null`.
- the message has **two halves**. The first, `Cannot invoke "…"`, is the operation that failed.
  The second, after `because`, is the expression that **was null**. That's the one you go and fix.
- **debug information** is the optional part of a class file that maps bytecode back to source:
  line numbers, and the `LocalVariableTable`, which holds local variable and parameter names.
  `javac -g` writes it; plain `javac` writes only line numbers.
- a **placeholder** is what the message says when the names aren't there: `"<local1>"`,
  `"<parameter1>"`.
- **`-XX:-ShowCodeDetailsInExceptionMessages`** turns the feature off, and the message goes back
  to `null`.

That gives one rule, and everything below follows from it: **the message describes the bytecode
that failed, so it can only say what the class file still knows.** It names a method or field
because the call instruction names it. It names a local variable only if debug information was
compiled in. And it says nothing at all for an NPE that your code threw, because there was no
failing instruction to describe.

## The worked example

A chain with three links, not the exercise's:

    record Customer(String email) {}
    record Order(Customer customer) {}

    static int emailLength(Order order) {
        return order.customer().email().strip().length();     // Shop.java:6
    }

Break it in three places, one at a time (verified, JDK 26):

| Call | Message |
| --- | --- |
| `emailLength(new Order(new Customer(null)))` | `Cannot invoke "String.strip()" because the return value of "Shop$Customer.email()" is null` |
| `emailLength(new Order(null))` | `Cannot invoke "Shop$Customer.email()" because the return value of "Shop$Order.customer()" is null` |
| `emailLength(null)`, run as `java Shop.java` | `Cannot invoke "Shop$Order.customer()" because "<parameter1>" is null` |

And with the flag off, all three are the same:

    Exception in thread "main" java.lang.NullPointerException
            at Shop.emailLength(Shop.java:6)

One line, four dereferences on it, and nothing to say which one failed. The old fix was to split
the line into four statements, redeploy, and wait for it to happen again. **That's the workflow
this feature removed.**

The third row is the rule. Run as a source file, the code is compiled without debug information,
so the parameter's name isn't in the class file, and the JVM can't print it. The same code
compiled with `javac -g` says `because "order" is null`.

## Part one: `diagnostics/NullPaths.java`, three TODOs

Delete the `@Disabled` in `NullPathsTest` first: **6 tests, all red** (verified: 3 failures and 3
errors, all from the `UnsupportedOperationException` stubs).

1. **TODO 1 and TODO 2, one commit.** The order matters. **Write down your prediction of the
   messages before you run anything**: for each of the three broken chains in the test, which
   accessor will be in the first half, and which in the second. Then implement `cityOf` as the
   one-line walk, with no null checks, and run the test. Put your predictions in the commit
   message, including the ones you got wrong. **A correction to the old version:** it printed all
   three messages in a table directly under the instruction to predict them, which made TODO 2
   impossible to do honestly. They're not here. Run the test. (The TODO's comment says "the two
   messages"; the test checks three broken chains.)
2. **TODO 3 is the `Optional` version**, as its own commit. It's a chain of `map` calls, and the
   rule from [doc 09](09-streams-and-optional.md) applies: no `isPresent()` and then `get()`. Then
   count the lines, and answer in writing: would you take it at three links? It can't throw, and
   it also can't tell you *which* link was missing, which is what the one-liner's message just
   did. The harder question is whether `Optional` is the right way to defend a chain at all, or
   whether the chain shouldn't have nulls in it, which is what records with a compact constructor
   ([doc 01](01-records.md)) could enforce.

## Part two: what the message can and can't tell you

**This repo's build includes the names.** Maven compiles with debug information by default:
`javap -c -l` on `SimpleThreads.class` from a `mvn compile` shows a `LocalVariableTable` with `e`
and `args` in it (verified). So in this repo's tests, messages name your locals and parameters.
`java File.java` doesn't, as row three above showed. **The same bug gives a different message
depending on how the code was compiled.**

The message covers more than method calls. All verified, from one test class compiled with `-g`:

| Code | Message |
| --- | --- |
| `orders[1].customer()` | `Cannot invoke "…Order.customer()" because "orders[1]" is null` |
| `counts[0]`, a null static array | `Cannot load from int array because "Npe.counts" is null` |
| `int x = n.boxed;` | `Cannot invoke "java.lang.Integer.intValue()" because "n.boxed" is null` |
| `int v = map.get("k");` | `… because the return value of "java.util.Map.get(Object)" is null` |
| `synchronized (lock) {}` | `Cannot enter synchronized block because "lock" is null` |
| `Objects.requireNonNull(x)` | `null` |
| `throw new NullPointerException()` | `null` |

The last two are the rule again: your code threw those, not a failing instruction, so the JVM has
nothing to describe. That answers the old question "what's left for an explicit null check?"
`Objects.requireNonNull(x, "x")` at the top of a method fails **when the bad value arrives**, with
a message you wrote. A helpful NPE fails **when it's used**, which may be much later and far from
whoever passed it in.

## Part three: the flag, and why you might turn it off

    java -XX:-ShowCodeDetailsInExceptionMessages ...

The message quotes your code: method names, field names, and, with debug information, local
variable names. Those messages end up in logs and error responses that leave your machine. JEP 358
lists this itself as a reason someone might not want it ("the null-detail message gives insight
into source code that is otherwise not easy to obtain"), and it shipped switched off in Java 14
for that and for compatibility with tools that parse stack traces. It became the default in 15.
(Both from the JEP and the JDK 15 release notes; not checked here, because only JDK 25 and 26 are
installed.)

So if you see a bare `NullPointerException` with no message on a modern JDK, either the code threw
it itself, or **somebody turned this off**. That's a fact about their deployment, not about Java.

## The rest of the free tier

No exercise; know what they changed. Each was verified where a JDK 26 can show it.

- **Compact strings** (Java 9, JEP 254). A `String` that fits in Latin-1 is stored as one byte per
  character. Read through reflection: `"hello"` and `"héllo"` are a `byte[5]`, `"h€llo"` is a
  `byte[10]`, and with `-XX:-CompactStrings` all three are `byte[10]` (verified). Most strings
  halved in memory, and no code had to change.
- **G1 as the default collector** (Java 9, JEP 248). Verified in [doc 10](10-sequenced-collections-and-httpclient.md):
  G1 on a 10-core machine, but **Serial** with `-XX:ActiveProcessorCount=1`. The default depends
  on the machine. [Doc 31](31-runtime-defaults.md) follows that through to Java 27.
- **Flight Recorder** became free and open in Java 11 (JEP 328). It had been a commercial feature.
  [Doc 11](11-jvm-diagnostics.md) uses it.
- **`@Deprecated(forRemoval = true, since = "…")`** (Java 9, JEP 277). The compiler treats the two
  differently (verified): calling `Runtime.runFinalization()`, which is marked for removal, prints
  a `[removal]` warning even with no `-Xlint` flags. Calling `new Integer(5)`, which is only
  deprecated, prints just a `Note: ... uses or overrides a deprecated API` unless you pass
  `-Xlint:deprecation`. This repo's pom passes `-Xlint:all`, so you see both.
  [Doc 30](30-upgrade-hazards.md) is built on that distinction.

## What got worse

- _(fill this in)_

Prompts: a helpful NPE message quotes your code into a log: in what deployment is that a problem
you'd have to argue about? If the message is this good, when is an explicit `requireNonNull` still
worth writing, and where does it go? Does a better exception message make `Optional` less
necessary, or just less urgent?
