# 30. Upgrade hazards: deprecations and removals

**Java 8 → 26** · stage 19 of [the release-ordered track](INDEX.md) · **no code changes: two
JDK tools, then written answers**

**No TODOs and no tests.** Nothing here edits the repo. The deliverable is the written answers at
the end, and the most useful of them is about a project you work on, not this one.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Exercises 1 to 3 end with a **Done when** line; exercise 4 is the written answers.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `jdeprscan`](#exercise-1-jdeprscan-on-this-repo-then-on-what-it-depends-on) | Scan this repo's classes, then the jars it depends on. | T1–T3 | None: write down what came back. |
| [2. `jdeps`](#exercise-2-jdeps---jdk-internals) | List the modules the repo uses, and any JDK internals. | T4 | None. |
| [3. Runtime warnings](#exercise-3-the-runtime-warnings-on-this-repo) | Run the repo's tests on 26 and look for the warnings no scanner sees. | T5 | The existing suite. |
| [4. Write the answers here](#exercise-4-write-the-answers-here) | Five written answers, mostly about a project you work on. | all | None. |
| [5. What got worse](#exercise-5-fill-in-what-got-worse) | Write down what the upgrade policy costs. | all | None. |
| [6. Review questions](#exercise-6-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the seven sections in order: each one builds on the one before. Here is the whole story in
one paragraph, so you know where it's going.

> An API doesn't vanish in one step. It's deprecated, then marked for removal, then removed (T1).
> Your own code gets recompiled on the new JDK, and the compiler tells you everything. The jars
> you depend on don't: they were compiled years ago, and a removed method only fails when the line
> that calls it runs (T2). So you point two tools at the compiled code, dependencies first:
> `jdeprscan` for deprecated APIs (T3) and `jdeps` for JDK internals (T4). Some of the newest
> changes are about *how* an API is used, not which one, and no scanner sees them: they show up as
> runtime warnings (T5). Lined up release by release, 8 to 26 (T6), every change turns out to be
> the same four-step move, called integrity by default (T7).

Every code sample below is a complete program unless it says otherwise. Outputs marked *verified*
were run on JDK 26 (Temurin 26.0.2), unless the sample says JDK 25 (Temurin 25.0.4).

### T1. Deprecated, for removal, removed

Five words, defined once:

- **deprecated** means the JDK has marked an API with `@Deprecated`: it still works, and the
  compiler warns when you use it.
- **`forRemoval=true`** is the stronger mark, added in Java 9 (JEP 277). It means the API is
  scheduled to go. `javac` reports it as a `[removal]` warning, separately from plain deprecation.
- **removed** means the API is no longer in the JDK. Source that uses it no longer compiles, and
  a class compiled against an older JDK fails *at runtime*, on the first call, with a
  `NoSuchMethodError` or `NoClassDefFoundError`.
- **`jdeprscan`** reads compiled classes or jars and lists every use of a deprecated JDK API, for a
  release you choose (T3).
- **`jdeps --jdk-internals`** lists every use of a JDK *internal* API, the kind that strong
  encapsulation turned off in Java 16 and 17 (T4).

The compiler treats the first two marks very differently. Plain deprecation gets a one-line `Note`
unless you ask for more; for-removal gets a real warning every time. Here are both:

```java
import java.util.Date;

public class Dep {
    public static void main(String[] args) {
        Integer boxed = new Integer(5);                 // deprecated since 9
        int year = new Date().getYear();                // deprecated since 1.1
        System.out.println(boxed + " " + (year > 100));
    }
}
```

```sh
javac Dep.java
```

```text
Note: Dep.java uses or overrides a deprecated API.            (verified 2026-11-23)
Note: Recompile with -Xlint:deprecation for details.
```

Two deprecated calls, and the default build says almost nothing. Neither is for removal on JDK 26,
so neither gets a `[removal]` warning. ([Doc 20](20-free-diagnostics.md)'s `Removal` sample puts a
for-removal call next to `new Integer(5)` in one file.) Compare `Thread.stop()` in T2, which is for removal: compiled
for 25, it gets a `warning: [removal]` with no flags at all.

That gives one rule, and everything below follows from it:

> **An upgrade breaks at the edges you didn't compile.**

Your own code gets recompiled and the compiler tells you everything. The jars you depend on were
compiled years ago against an older JDK, and nothing checks them until the line runs. So the tools
are pointed at **the dependencies first**.

### T2. Compiled on one JDK, run on another: `Thread.stop()`

`Thread.stop()` has been deprecated since Java 1.2 and marked `forRemoval` since 18. On JDK 26 it
is gone (verified). One program, compiled three ways:

```java
public class Stop {
    public static void main(String[] args) {
        System.out.println("started, about to call Thread.stop()");
        Thread.currentThread().stop();
        System.out.println("never printed");
    }
}
```

```sh
javac --release 25 -d out25 Stop.java     # compile against 25's API, on JDK 26
javac -d out26 Stop.java                   # compile against 26's API
java -cp out25 Stop                        # run the 25-compiled class on JDK 26
```

```text
Stop.java:4: warning: [removal] stop() in Thread has been deprecated and marked for removal
        Thread.currentThread().stop();                         (verified 2026-11-23)
                              ^
1 warning

Stop.java:4: error: cannot find symbol
        Thread.currentThread().stop();
                              ^
  symbol:   method stop()

started, about to call Thread.stop()
Exception in thread "main" java.lang.NoSuchMethodError: 'void java.lang.Thread.stop()'
        at Stop.main(Stop.java:4)
```

The last run is the one to remember. **The class loaded and the program started.** It only failed
when it reached the call. In a jar you depend on, that call can be in an error path that runs once
a month.

The same class run on JDK 25 fails differently, because on 25 the method still exists but no
longer does anything:

```text
started, about to call Thread.stop()                      (verified 2026-11-23, JDK 25)
Exception in thread "main" java.lang.UnsupportedOperationException
        at java.base/java.lang.Thread.stop(Thread.java:1557)
```

So the method was already useless before it was removed. Removal changed *which* exception you get,
and moved it from inside the JDK to the call site.

**A correction to the old version:** its timeline didn't mention `Thread.stop()`. It was removed
in 26 without a JEP of its own. The way to find removals like that is to diff `jdeprscan`'s own
list between two releases (verified, after normalising the annotation text):

```sh
jdeprscan --release 25 --list | wc -l        # 720
jdeprscan --release 26 --list | wc -l        # 703, of which 115 are forRemoval=true
```

Twenty-three entries left the list between 25 and 26 (verified). Among them: all of
`java.applet` and `javax.swing.JApplet` (JEP 504), `Thread.stop()`, the `byte`-TTL methods on
`MulticastSocket` and `DatagramSocketImpl`, `javax.management.modelmbean.XMLParseException`, and
six `finalize()` overrides in `javax.imageio`. The ones checked with `javap` are gone from the JDK
26 class library, not just undeprecated (verified). Six were added, among them
`Socket.setPerformancePreferences` and `java.net.SocketPermission`.

### T3. `jdeprscan`: what the compiler would have said, for code you can't recompile

`jdeprscan` reads class files, not source, so it works on any jar. You tell it which release to
check against, and it lists every call into an API that release has deprecated. Here it is on the
`Dep` class from T1:

```sh
javac -d dep Dep.java
jdeprscan --release 26 dep
jdeprscan --release 26 --for-removal dep
```

```text
Directory dep:                                            (verified 2026-11-23)
class Dep uses deprecated method java/lang/Integer::<init>(I)V
class Dep uses deprecated method java/util/Date::getYear()I
Directory dep:
```

Three things to read from that:

- **The names are class-file names**: `/` for `.`, `<init>` for a constructor, and a JVM type
  descriptor (`(I)V` is "takes an `int`, returns nothing"). [Doc 24](24-class-file-api.md) is where
  those come from.
- **`--for-removal` keeps only the dangerous ones.** Here it printed just the header, because
  neither call is for removal on 26. On a real jar, that second command is the short list.
- **`--release` matters.** The same jar can be clean against 21 and not against 26, because the
  list changes every release (T2's 720 and 703).

### T4. `jdeps --jdk-internals`: the APIs that were never public

Deprecation is about public APIs on their way out. The other upgrade hazard is code that used APIs
that were **never** public: classes in packages a module doesn't export, like `sun.misc` or
`jdk.internal.*`. Java 9 hid them (JEP 260), and 16 and 17 locked them away (T6). `jdeps` finds
them:

```java
import java.lang.reflect.Field;
import sun.misc.Unsafe;

public class Peek {
    public static void main(String[] args) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Unsafe unsafe = (Unsafe) f.get(null);
        System.out.println("address size: " + unsafe.addressSize());
    }
}
```

```sh
javac -d peek Peek.java          # four "internal proprietary API" warnings, one [removal]
jdeps --jdk-internals peek
jdeps -s peek
```

```text
peek -> jdk.unsupported                                   (verified 2026-11-23, trimmed)
   Peek     -> sun.misc.Unsafe     JDK internal API (jdk.unsupported)
...
JDK Internal API                         Suggested Replacement
----------------                         ---------------------
sun.misc.Unsafe                          See https://openjdk.org/jeps/260

peek -> java.base
peek -> jdk.unsupported
```

`jdeps` exits 0 either way: it reports, it doesn't fail the build. `-s` gives just the summary,
one line per module the code depends on.

Be careful with the word "internal". A module whose name starts with `jdk.` is not internal by
that fact. `jdk.httpserver`'s `com.sun.net.httpserver` is exported and supported; doc 10's samples
use it. **"Internal" means a package the module doesn't export.** `jdk.unsupported` is the odd one
out: it exports `sun.misc` on purpose, so old libraries keep working, and its name says what that
promise is worth.

### T5. The runtime warnings, which no scanner sees

Two of the newest changes don't show up in `jdeprscan` at all, because they're about *how* an
API is used, not which one. Both verified on JDK 26:

**`sun.misc.Unsafe` memory access** (deprecated for removal in 23, JEP 471; warns from 24, JEP
498). Calling `Unsafe.allocateMemory` prints four `WARNING:` lines on the first call and carries
on. `--sun-misc-unsafe-memory-access=deny` turns it into `UnsupportedOperationException`.
[Doc 23](23-foreign-function-and-memory.md) is the replacement, and its T1 shows the warnings.

**Final field mutation by reflection** (JEP 500, "Prepare to Make Final Mean Final", new in 26).
`setAccessible(true)` has let reflection write a `final` field since Java 1.2. On 26 it still
works, and says it won't forever:

```java
import java.lang.reflect.Field;

public class Fin {
    static class P {
        private final int x;
        P(int x) { this.x = x; }
    }

    public static void main(String[] args) throws Exception {
        P p = new P(1);
        Field f = P.class.getDeclaredField("x");
        f.setAccessible(true);
        f.setInt(p, 2);
        System.out.println("x=" + f.getInt(p));
        f.setInt(p, 3);                                  // a second write
        System.out.println("x=" + f.getInt(p));
    }
}
```

```text
WARNING: Final field x in class Fin$P has been mutated reflectively by class Fin in unnamed module @44a664f2 (file:.../Fin.java)
WARNING: Use --enable-final-field-mutation=ALL-UNNAMED to avoid a warning
WARNING: Mutating final fields will be blocked in a future release unless final field mutation is enabled
x=2                                                       (verified 2026-11-23, path trimmed)
x=3
```

The write still happens. The warning prints **once**, on the first mutation: the second write
printed nothing. On JDK 25 the same program prints `x=2` and `x=3` and no warning at all
(verified, JDK 25). And you can try the future today, or switch the warning off by opting in:

```sh
java --illegal-final-field-mutation=deny Fin.java
java --enable-final-field-mutation=ALL-UNNAMED Fin.java
```

```text
Exception in thread "main" java.lang.IllegalAccessException: class Fin (in unnamed module @44a664f2) cannot set final field Fin$P.x (in unnamed module @44a664f2), unnamed module @44a664f2 is not allowed to mutate final fields
        at java.base/java.lang.reflect.Field.preSetFinal(Field.java:1504)
...                                                       (verified 2026-11-23, trimmed)

x=2
x=3
```

Neither of these is visible in a class file. `jdeprscan` sees a call to `Field.setInt`, which isn't
deprecated, and has no way to know the field is `final`. **The only way to find them is to run the
code**, which is why exercise 3 runs the tests.

### T6. The timeline, 8 to 26

The behaviour in the last column was checked on JDK 26. The JEP numbers come from the JEP index
at openjdk.org; running `java` can't check them.

| Release | What changed | What you see on 26 |
| --- | --- | --- |
| 9 | Internals encapsulated (JEP 260); deprecation gets `forRemoval` (JEP 277) | |
| 11 | Java EE and CORBA modules removed (JEP 320) | no `java.xml.bind` in `java --list-modules` |
| 15 | Nashorn removed (JEP 372) | no `jdk.scripting.nashorn` module |
| 16 | Internals strongly encapsulated (JEP 396) | |
| 17 | `--illegal-access` removed (JEP 403); Security Manager deprecated (JEP 411) | `Ignoring option --illegal-access=permit; support was removed in 17.0` |
| 18 | UTF-8 by default (JEP 400); finalization deprecated (JEP 421) | `defaultCharset()` is UTF-8 even with `LANG=C` |
| 23 | `Unsafe` memory access deprecated for removal (JEP 471) | |
| 24 | Security Manager permanently disabled (JEP 486); `Unsafe` warns (JEP 498) | `-Djava.security.manager=allow`: `Error occurred during initialization of VM` |
| 25 | 32-bit x86 port removed (JEP 503) | not checkable on this machine |
| 26 | Final means final, step 1 (JEP 500); Applet API removed (JEP 504); `Thread.stop()` removed | the warning in T5; `package java.applet does not exist` |

Each "what you see" cell is one command, and all of them were run again for this version:

```sh
java --list-modules | grep -c 'java.xml.bind\|nashorn'           # 0
LANG=C LC_ALL=C java -XshowSettings:properties -version 2>&1 | grep encoding
java -Djava.security.manager=allow -version
java --illegal-access=permit -version
echo 'public class A extends java.applet.Applet {}' > A.java && javac A.java
```

```text
0                                                         (verified 2026-11-23)
    file.encoding = UTF-8
    native.encoding = ANSI_X3.4-1968
Error occurred during initialization of VM
java.lang.Error: A command line option has attempted to allow or enable the Security Manager. Enabling a Security Manager is not supported.
OpenJDK 64-Bit Server VM warning: Ignoring option --illegal-access=permit; support was removed in 17.0
A.java:1: error: package java.applet does not exist
```

Note the second pair: under `LANG=C` the *operating system's* encoding is ASCII
(`native.encoding`), and Java's default is still UTF-8. [Doc 18](18-files-strings-and-charsets.md)
is about what that does to files.

### T7. The pattern, which is the thing to be able to say

Every row is the same move, and inside OpenJDK it's called **integrity by default**. A hole that
made some library possible (`Unsafe`, deep reflection, `setAccessible` on anything, final fields
that weren't final) also made the platform impossible to reason about or optimise. Each one is
closed the same way:

1. **deprecate**: javadoc and a compiler warning (T1)
2. **warn at runtime**: it still works, and says it won't forever (T5)
3. **require a flag**: it works if you opt in, visibly, on the command line (T5's
   `--enable-final-field-mutation`)
4. **remove** (T2)

You've met step 3 already: `--enable-native-access` in [doc 23](23-foreign-function-and-memory.md)
and `--enable-preview` in [doc 33](33-preview-shelf.md). **The flags are the design.** A dangerous
capability behind a named flag shows up in a code review, in `ps` and in a container spec. One
that's on by default shows up nowhere.

> **Answer exercise 4's question 2 in your head before you reread T6:** it asks you to place a row
> on this ladder, and T5 and T6 together give most of the rows away.

## Practice

### Exercise 1: `jdeprscan` on this repo, then on what it depends on

```sh
mvn -q clean package
jdeprscan --release 26 target/classes
```

**A correction to the old version:** it showed `jdeprscan` reporting
`class Dep uses deprecated method java/lang/Integer::<init>(I)V`. That isn't from this repo.
**On this repo the output is empty** (verified): just the header line `Directory target/classes:`,
for both `target/classes` and `target/test-classes`. It's what you'd expect from a build that
already compiles with `-Xlint:all` and shows no deprecation warnings. (The `Dep` lines are real
output. They come from a two-line class that calls `new Integer(5)` and `Date.getYear()`: that's
T3's sample.)

Now point it at the test dependencies in `~/.m2` (T1's rule: dependencies first). commons-lang3 at
the version the pom pins (verified):

```sh
jdeprscan --release 26 ~/.m2/repository/org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.jar
```

```text
3 uses deprecated method java/lang/reflect/AccessibleObject::isAccessible()Z
2 uses deprecated method java/lang/Thread::getId()J
3 uses deprecated method java/util/Locale::<init>(...)       // three overloads
```

**Nine uses, none of them for removal** (`--for-removal` prints nothing). `junit-jupiter-params`
and `junit-platform-commons` show the same `Locale` constructor and `isAccessible()` (verified).
That's the realistic picture: your code is clean, and your dependencies aren't, and nobody's
build has warned about it.

Commits: none. Write down what came back and whether any of it is `forRemoval`.

**Done when:** you've scanned `target/classes`, `target/test-classes` and at least the three jars
above, with and without `--for-removal`, and written down the results.

### Exercise 2: `jdeps --jdk-internals`

```sh
jdeps --jdk-internals target/classes          # prints nothing, exit 0 (verified)
jdeps -s target/classes                       # which modules the code uses
```

`-s` shows `java.base`, `java.desktop`, `java.net.http` and `jdk.httpserver` (verified). The last
one starts with `jdk.` and is **not** an internal API (T4): `com.sun.net.httpserver` is exported
and supported. "Internal" means a package the module doesn't export, like `sun.misc` or
`jdk.internal.*`.

On a multi-release jar you need the release, or it refuses (verified):

```sh
jdeps --multi-release 26 --jdk-internals commons-lang3-3.14.0.jar
```

**Done when:** you've run both commands on `target/classes` and the multi-release one on the
commons-lang3 jar, and can say which module in the `-s` list is the surprising one and why it
isn't internal.

### Exercise 3: the runtime warnings on this repo

Run the repo's tests: `mvn test` on JDK 26 prints none of T5's warnings (verified, 248 tests).
JUnit 5.10, hamcrest and commons-lang3 don't write final fields. Serialization frameworks, mocking
libraries and dependency injection containers often do. That's question 4 in exercise 4.

**Done when:** you've run `mvn test` on JDK 26, searched its output for `WARNING:`, and know how
you'd pass `--illegal-final-field-mutation=deny` to the test JVM for exercise 4's question 4.

### Exercise 4: Write the answers here

1. **Run both tools against a project you actually work on**: its jars, not just its classes.
   What came back, and how much of it is `forRemoval`? _(fill in)_
2. **Your own four-step example.** Pick one row and place it on the deprecate → warn → flag →
   remove ladder. Which step is it on now, and what would you do today if your code depended on
   it? _(fill in)_
3. **The Java 11 question.** A service on Java 8 uses `javax.xml.bind`. Describe the upgrade.
   _(fill in)_
4. **Final means final.** Run that project's tests with `--illegal-final-field-mutation=deny`.
   What failed, and which library was it? If nothing failed, what does that tell you about the
   tests, as well as the libraries? _(fill in)_
5. **The explain-it version.** _"We're on Java 17, should we go to 21 or 25?"_ Answer in four
   sentences, naming what you would run before promising a date. _(fill in)_

### Exercise 5: fill in "What got worse"

- _(fill this in)_

Prompts: integrity by default costs the ecosystem real work. Who pays, and is the trade right?
`Thread.stop()` compiled fine on 25 and failed on 26 only when it ran: what test would have caught
it, and do you have one? If a flag makes a dangerous thing visible, what stops it becoming
boilerplate everyone copies without reading? Is a four-release ladder long enough for a library
with one unpaid maintainer?

### Exercise 6: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. What's the difference between `@Deprecated` and `@Deprecated(forRemoval=true)`, as far as
   `javac` is concerned? (T1)
2. Why are the tools pointed at the dependencies first? State the rule. (T1)
3. A class compiled for 25 calls `Thread.stop()`. What happens when it loads on 26, and when it
   reaches the call? What did the same class do on 25? (T2)
4. How would you find an API removed in a release without a JEP of its own? (T2)
5. What does `(I)V` mean in `jdeprscan`'s output, and where do names like `<init>` come from? (T3)
6. `jdeprscan --for-removal` prints only the header. What does that tell you, and what doesn't it?
   (T3)
7. Is `com.sun.net.httpserver` an internal API? What makes a package internal? (T4)
8. Why can't `jdeprscan` find a reflective write to a `final` field? How do you find one? (T5)
9. On JDK 26, how many times does the final-field warning print for two writes, and what two
   flags change that? (T5)
10. Name the four steps of integrity by default, and one flag that is step 3. (T7)

**Done when:** you can answer all ten without looking.
