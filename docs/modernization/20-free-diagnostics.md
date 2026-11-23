# 20. The diagnostics you get for free

**Java 14** (helpful `NullPointerException`s, JEP 358; on by default from 15) · **Java 9, 11**
(the rest of the free tier) · stage 9 of [the release-ordered track](INDEX.md) · compiles at
`--release 21`, no build change

New code only, with tests already written, so there's no safety net to build first.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `NullPaths`, TODOs 1 and 2](#exercise-1-nullpaths-todos-1-and-2-predict-then-walk) | Predict three NPE messages in writing, then implement the one-line walk. One commit. | T1–T3 | `NullPathsTest`, already written: delete its `@Disabled`. |
| [2. `NullPaths`, TODO 3](#exercise-2-nullpaths-todo-3-the-optional-walk) | The same walk with `Optional`, and a written answer on whether you'd keep it. One commit. | T5 | The same test class. |
| [3. What got worse](#exercise-3-fill-in-what-got-worse) | Write down what this feature costs. | all | None. |
| [4. Review questions](#exercise-4-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the seven sections in order: each one builds on the one before. Here is the whole story in one
paragraph, so you know where it's going.

> For twenty years a `NullPointerException` said nothing but a line number, and one line can
> dereference four things (T1). Since Java 14 the JVM writes a message that says what it tried to
> do and what was null, in two halves you have to read the right way round (T2). The message is
> built from the bytecode that failed, so it can only say what the class file still knows: a local
> variable's name only if it was compiled in (T3). The same rule explains which NPEs get a message
> at all, and which never will (T4). That's why an explicit null check still has a job: it fails
> where the bad value arrives, not where it's used (T5). The message quotes your code, so some
> people switch it off (T6). And it's one of a handful of improvements since Java 9 that you got
> without changing a line (T7).

Every code sample below is a complete program. Save it as the class name plus `.java` and run it
with `java Name.java`; no build needed. **How you run it matters in this doc** (T3), so each
output says which way it was run. Outputs marked *verified* were run on JDK 26.

### T1. The problem: four dereferences, one line number

Here is a chain with three links. It isn't the exercise's chain, so reading its messages spoils
nothing:

```java
public class Shop {
    record Customer(String email) {}
    record Order(Customer customer) {}

    static int emailLength(Order order) {
        return order.customer().email().strip().length();     // four dereferences, one line
    }

    static void tryIt(String label, Order order) {
        try {
            emailLength(order);
        } catch (NullPointerException e) {
            System.out.println(label + ": " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        tryIt("email null   ", new Order(new Customer(null)));
        tryIt("customer null", new Order(null));
        tryIt("order null   ", null);
    }
}
```

First, the way Java behaved before 14. The flag that switches the feature off (T6) gives you the
old behaviour back:

```sh
java -XX:-ShowCodeDetailsInExceptionMessages Shop.java
```

```text
email null   : null                                      (verified 2026-11-23)
customer null: null
order null   : null
```

Three different bugs, three identical exceptions. An uncaught one would print just this:

```text
Exception in thread "main" java.lang.NullPointerException
        at Shop.emailLength(Shop.java:6)
```

One line, four dereferences on it, and nothing to say which one failed. The old fix was to split
the line into four statements, redeploy, and wait for it to happen again. **That's the workflow
this feature removed.**

### T2. The message, and its two halves

Run the same program normally:

```sh
java Shop.java
```

```text
email null   : Cannot invoke "String.strip()" because the return value of "Shop$Customer.email()" is null      (verified 2026-11-23)
customer null: Cannot invoke "Shop$Customer.email()" because the return value of "Shop$Order.customer()" is null
order null   : Cannot invoke "Shop$Order.customer()" because "<parameter1>" is null
```

A **helpful NPE message** is the text the JVM now puts in a `NullPointerException` it throws
itself. Before JEP 358, `getMessage()` was `null`. The message has **two halves**:

- the first, `Cannot invoke "…"`, is the **operation that failed**: the call the JVM was trying to
  make;
- the second, after `because`, is the **expression that was null**. That's the one you go and fix.

People read them backwards. In the first line, `strip()` isn't the problem: it never ran. The
problem is that `email()` returned `null`. Notice too that the message moves along the chain as
the null moves: each row names a different pair of links.

### T3. The message can only say what the class file knows

The third line above says `"<parameter1>"`, not `"order"`. Why?

The message is built from the **bytecode that failed**, not from your source. A call instruction
names the method it calls, so method names are always there. But a local variable or a parameter
is just a numbered slot in bytecode. Its *name* exists only in the optional **debug information**:
the part of a class file that maps bytecode back to source (line numbers, and the
`LocalVariableTable` of local variable and parameter names). `javac -g` writes all of it; plain
`javac` writes only line numbers. Without the names, the JVM prints a **placeholder**:
`"<local1>"`, `"<parameter1>"`.

That gives one rule, and everything else in this doc follows from it:

> **The message describes the bytecode that failed, so it can only say what the class file still
> knows.**

You can watch it happen with one program compiled three ways:

```java
public class Where {
    public static void main(String[] args) {
        String greeting = args.length > 0 ? args[0] : null;
        try {
            System.out.println(greeting.length());
        } catch (NullPointerException e) {
            System.out.println(e.getMessage());
        }
    }
}
```

```sh
java Where.java                              # source launcher
javac -d plain Where.java && java -cp plain Where
javac -g -d debug Where.java && java -cp debug Where
```

```text
Cannot invoke "String.length()" because "<local1>" is null       (source launcher, verified 2026-11-23)
Cannot invoke "String.length()" because "<local1>" is null       (javac, verified 2026-11-23)
Cannot invoke "String.length()" because "greeting" is null       (javac -g, verified 2026-11-23)
```

The source launcher compiles without the variable names, just like plain `javac`. `Shop` behaves
the same way: compiled with `javac -g`, its third line says `because "order" is null` (verified).

**This repo's build includes the names.** Maven compiles with debug information by default:
`javap -c -l` on `SimpleThreads.class` from a `mvn compile` shows a `LocalVariableTable` with `e`
and `args` in it (verified). So in this repo's tests, messages name your locals and parameters.
`java File.java` doesn't. **The same bug gives a different message depending on how the code was
compiled.**

### T4. Which NPEs get a message, and which never will

The message covers more than method calls. Anything the JVM does that needs a non-null reference
can fail, and each kind has its own wording:

```java
import java.util.Map;
import java.util.Objects;

public class Kinds {
    static int[] counts;                                  // never assigned: null
    Integer boxed;                                        // never assigned: null

    static void show(String label, Runnable r) {
        try {
            r.run();
        } catch (NullPointerException e) {
            System.out.println(label + "\n    " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        String[] names = { "ana", null };
        Kinds k = new Kinds();
        Map<String, Integer> stock = Map.of("pear", 3);
        Object lock = null;

        show("names[1].length()", () -> names[1].length());
        show("counts[0]", () -> { int c = counts[0]; });
        show("int x = k.boxed", () -> { int x = k.boxed; });
        show("int v = stock.get(\"fig\")", () -> { int v = stock.get("fig"); });
        show("synchronized (lock)", () -> { synchronized (lock) { } });
        show("Objects.requireNonNull(lock)", () -> Objects.requireNonNull(lock));
        show("throw new NullPointerException()", () -> { throw new NullPointerException(); });
    }
}
```

Compiled with `javac -g`:

```text
names[1].length()                                        (javac -g, verified 2026-11-23)
    Cannot invoke "String.length()" because "names[1]" is null
counts[0]
    Cannot load from int array because "Kinds.counts" is null
int x = k.boxed
    Cannot invoke "java.lang.Integer.intValue()" because "k.boxed" is null
int v = stock.get("fig")
    Cannot invoke "java.lang.Integer.intValue()" because the return value of "java.util.Map.get(Object)" is null
synchronized (lock)
    Cannot enter synchronized block because "lock" is null
Objects.requireNonNull(lock)
    null
throw new NullPointerException()
    null
```

Read them by the rule:

- **Unboxing is a hidden call.** `int x = k.boxed` has no method call in the source, but the
  bytecode calls `Integer.intValue()`, so that's what the message names. The same goes for
  `int v = map.get(...)`: a missing key, unboxed.
- **Arrays and monitors** have their own instructions, so they get their own wording: `Cannot load
  from int array`, `Cannot enter synchronized block`.
- **The last two say `null`.** Your code threw those, not a failing instruction, so the JVM has
  nothing to describe. `Objects.requireNonNull(x)` without a message is a `throw` inside the JDK.

Run the same file with `java Kinds.java` instead and the names change in an unexpected way:
`"<parameter1>[1]"`, `"<parameter1>.boxed"`, `"<parameter1>"` for the lock (verified). Each
lambda is compiled into a hidden method, and the variables it captures become that method's
*parameters*. Without debug information they're numbered. T3's rule, once more.

The old version of this table, from a test class compiled with `-g`, also gave
`because "orders[1]" is null` and `because "Npe.counts" is null` (verified): same wording, other
names.

### T5. What's left for an explicit null check

If the JVM explains every NPE this well, why write `Objects.requireNonNull(x, "x")` at all? Because
of *where* it fails. A helpful NPE fails **when the null is used**. An explicit check fails **when
the bad value arrives**. Those can be far apart:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Early {
    static class Mailer {
        private final List<String> outbox = new ArrayList<>();
        private final boolean strict;

        Mailer(boolean strict) { this.strict = strict; }

        void queue(String address) {
            if (strict) {
                Objects.requireNonNull(address, "address");   // fails here, as it arrives
            }
            outbox.add(address);
        }

        void sendAll() {
            for (String a : outbox) {
                System.out.println("  sending to " + a.toLowerCase());  // fails here, much later
            }
        }
    }

    static void run(boolean strict) {
        Mailer m = new Mailer(strict);
        try {
            m.queue("ANA@EXAMPLE.ORG");
            m.queue(null);                                    // the actual mistake
            m.queue("BO@EXAMPLE.ORG");
            m.sendAll();
        } catch (NullPointerException e) {
            StackTraceElement mine = java.util.Arrays.stream(e.getStackTrace())
                    .filter(f -> f.getClassName().startsWith("Early"))
                    .findFirst().orElseThrow();           // the first frame in our own code
            System.out.println("  NPE in " + mine.getMethodName() + "(): " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        System.out.println("lenient:");
        run(false);
        System.out.println("strict:");
        run(true);
    }
}
```

```text
lenient:                                                 (source launcher, verified 2026-11-23)
  sending to ana@example.org
  NPE in sendAll(): Cannot invoke "String.toLowerCase()" because "<local2>" is null
strict:
  NPE in queue(): address
```

The lenient run's message is accurate and useless: it points at `sendAll()`, and the stack trace
there doesn't include whoever called `queue(null)`. One mail was already sent before it failed.
The strict run fails at the door, with the caller still on the stack, and with a message you
wrote.

`Optional` is a third answer: make "missing" a value instead of a failure. Exercise 2 asks what
that costs on a chain, and whether the better answer is a chain that can't hold nulls at all.

### T6. The flag, and why you might turn it off

```sh
java -XX:-ShowCodeDetailsInExceptionMessages ...
```

T1 used it to show the old behaviour. Why would anyone use it for real? The message quotes your
code: method names, field names, and, with debug information, local variable names. Those
messages end up in logs and error responses that leave your machine. JEP 358 lists this itself as
a reason someone might not want it ("the null-detail message gives insight into source code that
is otherwise not easy to obtain"), and it shipped switched off in Java 14 for that and for
compatibility with tools that parse stack traces. It became the default in 15. (Both from the JEP
and the JDK 15 release notes; not checked here, because only JDK 25 and 26 are installed.)

So if you see a bare `NullPointerException` with no message on a modern JDK, either the code threw
it itself (T4), or **somebody turned this off**. That's a fact about their deployment, not about
Java.

### T7. The rest of the free tier

No exercise; know what they changed. Each was verified where a JDK 26 can show it.

**Compact strings** (Java 9, JEP 254). A `String` that fits in Latin-1 is stored as one byte per
character instead of two. You can see it by reading `String`'s private `value` array, which needs
the module system's permission (`--add-opens`):

```java
import java.lang.reflect.Field;

public class Compact {
    public static void main(String[] args) throws Exception {
        Field value = String.class.getDeclaredField("value");
        value.setAccessible(true);                       // needs --add-opens, see below
        for (String s : new String[] { "hello", "héllo", "h€llo" }) {
            byte[] bytes = (byte[]) value.get(s);
            System.out.println(s + " -> byte[" + bytes.length + "]");
        }
    }
}
```

```sh
java --add-opens java.base/java.lang=ALL-UNNAMED Compact.java
java --add-opens java.base/java.lang=ALL-UNNAMED -XX:-CompactStrings Compact.java
```

```text
hello -> byte[5]                                         (verified 2026-11-23)
héllo -> byte[5]
h€llo -> byte[10]
```

```text
hello -> byte[10]                                        (-XX:-CompactStrings, verified 2026-11-23)
héllo -> byte[10]
h€llo -> byte[10]
```

`é` is in Latin-1, so `"héllo"` still fits in one byte per character; `€` isn't, so `"h€llo"`
takes two. Most strings halved in memory, and no code had to change.

**G1 as the default collector** (Java 9, JEP 248). Verified in
[doc 10](10-sequenced-collections-and-httpclient.md): G1 on a 10-core machine, but **Serial** with
`-XX:ActiveProcessorCount=1`. The default depends on the machine. [Doc 31](31-runtime-defaults.md)
follows that through to Java 27.

**Flight Recorder** became free and open in Java 11 (JEP 328). It had been a commercial feature.
[Doc 11](11-jvm-diagnostics.md) teaches it, with `jcmd`.

**`@Deprecated(forRemoval = true, since = "…")`** (Java 9, JEP 277). The compiler treats "will be
removed" and "merely deprecated" differently:

```java
public class Removal {
    @SuppressWarnings("unused")
    public static void main(String[] args) {
        Runtime.getRuntime().runFinalization();          // deprecated, forRemoval = true
        Integer five = new Integer(5);                   // deprecated, not for removal
    }
}
```

```sh
javac -d out Removal.java
```

```text
Removal.java:4: warning: [removal] runFinalization() in Runtime has been deprecated and marked for removal      (verified 2026-11-23)
        Runtime.getRuntime().runFinalization();          // deprecated, forRemoval = true
                            ^
Note: Removal.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
1 warning
```

The removal gets a real warning with no flags at all. The plain deprecation gets only a `Note`,
unless you pass `-Xlint:deprecation`, which turns it into a second `[deprecation]` warning
(verified). (`@SuppressWarnings("unused")` only silences the unused variable; it doesn't touch
either deprecation warning.) This repo's pom passes `-Xlint:all`, so you see both.
[Doc 30](30-upgrade-hazards.md) is built on that distinction.

## Practice

### Exercise 1: `NullPaths`, TODOs 1 and 2, predict then walk

`diagnostics/NullPaths.java` has a three-link chain: a `Person` whose `employer()` is a `Company`
whose `address()` is an `Address` whose `city()` you uppercase. Delete the `@Disabled` in
`NullPathsTest` first: **6 tests, all red** (verified: 3 failures and 3 errors, all from the
`UnsupportedOperationException` stubs).

**TODO 1 and TODO 2, one commit.** The order matters. **Write down your prediction of the
messages before you run anything**: for each of the three broken chains in the test, which
accessor will be in the first half, and which in the second (T2). Then implement `cityOf` as the
one-line walk, with no null checks, and run the test. Put your predictions in the commit message,
including the ones you got wrong.

**A correction to the old version:** it printed all three messages in a table directly under the
instruction to predict them, which made TODO 2 impossible to do honestly. They're not here, and
T1–T4 deliberately use other chains. Run the test. (The TODO's comment says "the two messages";
the test checks three broken chains.)

**Done when:** your written predictions are in the commit message, `cityOf` is one line with no
null checks, and the four `cityOf` tests are green.

### Exercise 2: `NullPaths`, TODO 3, the `Optional` walk

**TODO 3 is the `Optional` version**, as its own commit. It's a chain of `map` calls, and the rule
from [doc 09](09-streams-and-optional.md) applies: no `isPresent()` and then `get()`. Then count
the lines, and answer in writing: would you take it at three links? It can't throw, and it also
can't tell you *which* link was missing, which is what the one-liner's message just did (T2). The
harder question is whether `Optional` is the right way to defend a chain at all, or whether the
chain shouldn't have nulls in it, which is what records with a compact constructor
([doc 01](01-records.md)) could enforce. That's T5's "fail where it arrives", built into the type.

**Done when:** all six `NullPathsTest` tests are green, there's no `isPresent()`/`get()` pair, and
your written answer is in the commit message.

### Exercise 3: fill in "What got worse"

- _(fill this in)_

Prompts: a helpful NPE message quotes your code into a log: in what deployment is that a problem
you'd have to argue about? If the message is this good, when is an explicit `requireNonNull` still
worth writing, and where does it go? Does a better exception message make `Optional` less
necessary, or just less urgent?

### Exercise 4: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. Before Java 14, what did `getMessage()` return for a JVM-thrown NPE, and why was one line
   number not enough? (T1)
2. In `Cannot invoke "A.b()" because the return value of "C.d()" is null`, which method returned
   null, and which one never ran? (T2)
3. Why does `java Where.java` print `"<local1>"` but `javac -g` print `"greeting"`? What is the
   rule? (T3)
4. Do this repo's Maven-built tests see local variable names in NPE messages? How do you check?
   (T3)
5. `int v = map.get("missing");` throws. What method does the message name, and why that one,
   when the source has no such call? (T4)
6. Name two NPEs whose message is `null` even on JDK 26 with the feature on. Why? (T4, T6)
7. A captured local in a lambda shows up as `"<parameter1>"` under the source launcher. Why a
   parameter? (T4)
8. A helpful NPE fails where the null is *used*. Where does `requireNonNull(x, "x")` fail, and
   why can that be the better place? (T5)
9. Give one reason to run with `-XX:-ShowCodeDetailsInExceptionMessages`. (T6)
10. Why is `"héllo"` a `byte[5]` but `"h€llo"` a `byte[10]`? (T7)
11. Which gets a compiler warning with no `-Xlint` flags: a call to something deprecated for
    removal, or to something merely deprecated? (T7)

**Done when:** you can answer all eleven without looking.
