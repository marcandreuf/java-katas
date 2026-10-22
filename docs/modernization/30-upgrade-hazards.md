# 30. Upgrade hazards: deprecations and removals

**Java 8 → 26** · stage 19 of [the release-ordered track](INDEX.md) · **no code changes: two
JDK tools, then written answers**

**No TODOs and no tests.** Nothing here edits the repo. The deliverable is the written answers at
the end, and the most useful of them is about a project you work on, not this one.

## What this stage is actually about

Five words, defined once:

- **deprecated** means the JDK has marked an API with `@Deprecated`: it still works, and the
  compiler warns when you use it.
- **`forRemoval=true`** is the stronger mark, added in Java 9 (JEP 277). It means the API is
  scheduled to go. `javac` reports it as a `[removal]` warning, separately from plain deprecation.
- **removed** means the API is no longer in the JDK. Source that uses it no longer compiles, and
  a class compiled against an older JDK fails *at runtime*, on the first call, with a
  `NoSuchMethodError` or `NoClassDefFoundError`.
- **`jdeprscan`** reads compiled classes or jars and lists every use of a deprecated JDK API, for a
  release you choose.
- **`jdeps --jdk-internals`** lists every use of a JDK *internal* API, the kind that strong
  encapsulation turned off in Java 16 and 17.

That gives one rule, and everything below follows from it: **an upgrade breaks at the edges you
didn't compile.** Your own code gets recompiled and the compiler tells you everything. The jars
you depend on were compiled years ago against an older JDK, and nothing checks them until the line
runs. So the tools are pointed at **the dependencies first**.

## The worked example: `Thread.stop()`, compiled on 25, run on 26

`Thread.stop()` has been deprecated since Java 1.2 and marked `forRemoval` since 18. On JDK 26 it
is gone (verified). Three runs of a one-line program that calls it:

    javac --release 25 Stop.java     // warning: [removal] stop() in Thread has been deprecated
                                     //          and marked for removal
    javac Stop.java                  // on 26: error: cannot find symbol  method stop()

    javac --release 25 Stop.java && java Stop      // compiled for 25, run on 26:
    Exception in thread "main" java.lang.NoSuchMethodError: 'void java.lang.Thread.stop()'

The last line is the one to remember. **The class loaded and the program started.** It only
failed when it reached the call. In a jar you depend on, that call can be in an error path that
runs once a month.

**A correction to the old version:** its timeline didn't mention `Thread.stop()`. It was removed
in 26 without a JEP of its own. The way to find removals like that is to diff `jdeprscan`'s own
list between two releases (verified, after normalising the annotation text):

    jdeprscan --release 25 --list | wc -l        # 720
    jdeprscan --release 26 --list | wc -l        # 703, of which 115 are forRemoval=true

Twenty-three entries left the list between 25 and 26 (verified). Among them: all of
`java.applet` and `javax.swing.JApplet` (JEP 504), `Thread.stop()`, the `byte`-TTL methods on
`MulticastSocket` and `DatagramSocketImpl`, `javax.management.modelmbean.XMLParseException`, and
six `finalize()` overrides in `javax.imageio`. The ones checked with `javap` are gone from the JDK
26 class library, not just undeprecated (verified). Six were added, among them
`Socket.setPerformancePreferences` and `java.net.SocketPermission`.

## Part one: `jdeprscan` on this repo, then on what it depends on

    mvn -q clean package
    jdeprscan --release 26 target/classes

**A correction to the old version:** it showed `jdeprscan` reporting
`class Dep uses deprecated method java/lang/Integer::<init>(I)V`. That isn't from this repo.
**On this repo the output is empty** (verified): just the header line `Directory target/classes:`,
for both `target/classes` and `target/test-classes`. It's what you'd expect from a build that
already compiles with `-Xlint:all` and shows no deprecation warnings. (The `Dep` lines are real
output. They come from a two-line class that calls `new Integer(5)` and `Date.getYear()`, verified.
Write one yourself if you want to see the format.)

Now point it at the test dependencies in `~/.m2`. commons-lang3 at the version the pom pins
(verified):

    jdeprscan --release 26 ~/.m2/repository/org/apache/commons/commons-lang3/3.14.0/commons-lang3-3.14.0.jar

    3 uses deprecated method java/lang/reflect/AccessibleObject::isAccessible()Z
    2 uses deprecated method java/lang/Thread::getId()J
    3 uses deprecated method java/util/Locale::<init>(...)       // three overloads

**Nine uses, none of them for removal** (`--for-removal` prints nothing). `junit-jupiter-params`
and `junit-platform-commons` show the same `Locale` constructor and `isAccessible()` (verified).
That's the realistic picture: your code is clean, and your dependencies aren't, and nobody's
build has warned about it.

Commits: none. Write down what came back and whether any of it is `forRemoval`.

## Part two: `jdeps --jdk-internals`

    jdeps --jdk-internals target/classes          # prints nothing, exit 0 (verified)
    jdeps -s target/classes                       # which modules the code uses

`-s` shows `java.base`, `java.desktop`, `java.net.http` and `jdk.httpserver` (verified). The last
one starts with `jdk.` and is **not** an internal API: `com.sun.net.httpserver` is exported and
supported. "Internal" means a package the module doesn't export, like `sun.misc` or
`jdk.internal.*`.

On a multi-release jar you need the release, or it refuses (verified):

    jdeps --multi-release 26 --jdk-internals commons-lang3-3.14.0.jar

## Part three: the runtime warnings, which no scanner sees

Two of the newest changes don't show up in `jdeprscan` at all, because they're about *how* an
API is used, not which one. Both verified on JDK 26:

**`sun.misc.Unsafe` memory access** (deprecated for removal in 23, JEP 471; warns from 24, JEP
498). Calling `Unsafe.allocateMemory` prints four `WARNING:` lines on the first call and carries
on. `--sun-misc-unsafe-memory-access=deny` turns it into `UnsupportedOperationException`.
[Doc 23](23-foreign-function-and-memory.md) is the replacement.

**Final field mutation by reflection** (JEP 500, "Prepare to Make Final Mean Final", new in 26).
A `Field.setInt` on a `private final int`, after `setAccessible(true)`:

    WARNING: Final field x in class Fin$P has been mutated reflectively by class Fin in unnamed module
    WARNING: Use --enable-final-field-mutation=ALL-UNNAMED to avoid a warning
    WARNING: Mutating final fields will be blocked in a future release unless final field mutation is enabled
    x=2

The write still happens. The warning prints **once**, on the first mutation (a second write
printed nothing). And you can try the future today:

    java --illegal-final-field-mutation=deny ...
    java.lang.IllegalAccessException: class Fin ... cannot set final field Fin$P.x ...

Run the repo's tests: `mvn test` on JDK 26 prints none of these warnings (verified, 248 tests).
JUnit 5.10, hamcrest and commons-lang3 don't write final fields. Serialization frameworks, mocking
libraries and dependency injection containers often do. That's question 4.

## The timeline, 8 to 26

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
| 26 | Final means final, step 1 (JEP 500); Applet API removed (JEP 504); `Thread.stop()` removed | the warning above; `package java.applet does not exist` |

## The pattern, which is the thing to be able to say

Every row is the same move, and inside OpenJDK it's called **integrity by default**. A hole that
made some library possible (`Unsafe`, deep reflection, `setAccessible` on anything, final fields
that weren't final) also made the platform impossible to reason about or optimise. Each one is
closed the same way:

1. **deprecate**: javadoc and a compiler warning
2. **warn at runtime**: it still works, and says it won't forever
3. **require a flag**: it works if you opt in, visibly, on the command line
4. **remove**

You've met step 3 already: `--enable-native-access` in [doc 23](23-foreign-function-and-memory.md)
and `--enable-preview` in [doc 33](33-preview-shelf.md). **The flags are the design.** A dangerous
capability behind a named flag shows up in a code review, in `ps` and in a container spec. One
that's on by default shows up nowhere.

## Write the answers here

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

## What got worse

- _(fill this in)_

Prompts: integrity by default costs the ecosystem real work. Who pays, and is the trade right?
`Thread.stop()` compiled fine on 25 and failed on 26 only when it ran: what test would have caught
it, and do you have one? If a flag makes a dangerous thing visible, what stops it becoming
boilerplate everyone copies without reading? Is a four-release ladder long enough for a library
with one unpaid maintainer?
