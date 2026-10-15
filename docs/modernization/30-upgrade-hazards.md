# 30. Upgrade hazards: deprecations and removals

**Java 8 → 26** · stage 19 of [the release-ordered track](INDEX.md) · **two real commands, then
written answers**

## Why this is the most useful stage in the track

Everything else here is about what you can newly write. **This is about what stops working**, and
in real work it is the part that decides whether an upgrade takes a day or a quarter. It is also
the part nobody studies, because there is no feature to demo.

There are two tools for it and they ship with the JDK.

## The exercise

### 1. `jdeprscan` — what in your code is deprecated

    mvn -q clean package
    jdeprscan --release 25 --class-path target/classes \
        $(cd target/classes && find . -name '*.class' | sed 's|^\./||; s|\.class$||; s|/|.|g')

Or point it at one class:

    jdeprscan --release 25 --class-path target/classes org.mandfer.practices.collections.RandomFileLines

The output names the exact call site:

    class Dep uses deprecated method java/lang/Integer::<init>(I)V
    class Dep uses deprecated method java/util/Date::getYear()I

And to see the whole surface you are exposed to:

    jdeprscan --release 25 --list | wc -l        # 720 on JDK 25
    jdeprscan --release 25 --list | grep forRemoval=true

**`forRemoval=true` is the line that matters.** JEP 277 (Java 9) split deprecation into "we would
rather you did not" and "this is going away", and only the second one is a schedule.

### 2. `jdeps --jdk-internals` — what in your code reaches inside the JDK

    jdeps --jdk-internals -cp target/classes target/classes

Clean output here is expected and is itself the finding: **this repo uses no JDK internals, which
is why it compiles on 25 at all.** Run it against a dependency-heavy project you work on and the
output is rarely empty.

This is the tool that would have told you, in 2017, which of your libraries was going to break on
Java 9.

## The timeline, 8 to 26

The individual entries matter less than the pattern, which is stated after the table.

| Release | What went | Why it bites |
| --- | --- | --- |
| 9 | JDK internals encapsulated (JEP 260); `--illegal-access=permit` as a grace period | Everything using `sun.*` warned |
| 11 | **Java EE and CORBA modules removed** (JEP 320) | `javax.xml.bind`, `javax.annotation`, `javax.jws` gone — add a dependency or do not upgrade |
| 15 | Nashorn removed (JEP 372) | JS-in-the-JVM was a supported feature and then was not |
| 16 | Internals **strongly** encapsulated by default (JEP 396) | The 9-era warning became an error |
| 17 | The `--illegal-access` escape hatch **removed** (JEP 403); Security Manager deprecated (411); Applet deprecated for removal | No more opting out |
| 18 | **UTF-8 by default** (JEP 400) | Behaviour change, not a removal. [Doc 18](18-files-strings-and-charsets.md) |
| 18 | Finalization deprecated (JEP 421) | `finalize()` was already a bug; now it is a warned bug |
| 21 | 32-bit x86 port deprecated (JEP 449) | |
| 23 | `sun.misc.Unsafe` memory access deprecated for removal (JEP 471) | The big one — see below |
| 24 | Security Manager **permanently disabled** (JEP 486); `Unsafe` memory access now **warns** (498); JNI use restricted with warnings (472) | |
| 25 | 32-bit x86 port **removed** (JEP 503) | |
| 26 | **Prepare to Make Final Mean Final** (JEP 500); **Applet API removed** (JEP 504) | See below |

## The pattern, which is the thing to be able to say

Every row above is the same move, and it has a name inside OpenJDK: **integrity by default.**

The JDK spent twenty years with holes in it — `sun.misc.Unsafe`, deep reflection into other
people's private fields, `setAccessible` on anything, a Security Manager that promised more than
it delivered, final fields that were not final. Each hole made some library possible and made the
whole platform impossible to reason about or optimise.

The sequence for closing each one is identical and takes about four releases:

1. **deprecate** — javadoc and a compiler warning
2. **warn at runtime** — the thing still works, and says it will not forever
3. **require a flag** — it works if you explicitly opt in, visibly, on the command line
4. **remove**

You have already met step 3 twice in this track: `--enable-native-access` in
[doc 23](23-foreign-function-and-memory.md) and `--enable-preview` in
[doc 26](26-scoped-values-and-structured-concurrency.md). **The flags are not obstacles, they are
the design.** A dangerous capability that requires a named flag shows up in a code review, in
`ps`, and in a container spec. One that is on by default shows up nowhere.

Two entries deserve their own note:

**`sun.misc.Unsafe` (23, 24)** is only removable because [JEP 454 FFM](23-foreign-function-and-memory.md)
and `VarHandle` (Java 9) replaced its two real use cases — off-heap memory and low-level atomics.
That is why [stage 12](23-foreign-function-and-memory.md) frames FFM as "make `Unsafe` removable"
rather than "call C conveniently". Half the Java ecosystem depended on `Unsafe` and did not say so.

**"Final means final" (26, JEP 500)** is step 1 for the last big hole: deep reflection that
*mutates* a `final` field. Java 26 warns; a later release will refuse. This breaks serialization
frameworks, mocking libraries and dependency injection containers that write final fields after
construction — which is most of them. **If you want one thing to watch for in a Java 26 upgrade,
it is this**, and the warning is how you find out which of your dependencies does it.

## Write the answers here

1. **Run both tools against a project you actually work on**, not this one. What came back?
   _(fill in)_
2. **Your own four-step example.** Pick one row and place it on the deprecate → warn → flag →
   remove ladder. Which step is it on now, and what would you do today if your code depended on
   it? _(fill in)_
3. **The Java 11 question.** A service on Java 8 uses `javax.xml.bind`. Describe the upgrade.
   _(fill in)_
4. **"Final means final".** Name a library you depend on that probably writes final fields by
   reflection, and say how you would check. _(fill in)_
5. **The explain-it version.** _"We are on Java 17, should we go to 21 or 25?"_ Answer in four
   sentences, naming what you would run before promising a date. _(fill in)_

## What got worse

- _(fill this in)_

Prompts: integrity by default costs the ecosystem real work — who pays, and is the trade right?
If a flag makes a dangerous thing visible, what stops it from becoming boilerplate everyone copies
without reading? Is a four-release ladder long enough for a library with one unpaid maintainer?
