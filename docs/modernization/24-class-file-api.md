# 24. The Class-File API

**Java 24** (`java.lang.classfile`, JEP 484; preview in 22 and 23) · stage 13 of
[the release-ordered track](INDEX.md) · needs `--release 24` or newer, and the repo is at 26, so
no build change

No safety net needed: this is new code, `classfile/Inspect.java`, and its test, `InspectTest`,
already exists. It's `@Disabled`. Delete that line to begin. It parses `Door.class`, which the
build already produces, so there's nothing to generate and nothing to download.

At `--release 23`, `javac` says `ClassFile is a preview API and is disabled by default`
(verified). This is the shallowest stage in the track on purpose. You won't write a bytecode
rewriter. The point is to know what the API is for, and why it shipped.

## What this stage is actually about

Five words, defined once:

- a **class file** is what `javac` writes: the bytes the JVM loads. It isn't the source. It has
  methods you didn't write and lacks things you did, such as constant expressions, which `javac`
  has already folded away.
- the **major version** is a number in bytes 6 and 7 of every class file. It records which
  release the file was compiled for: **major = release + 44**.
- **ASM** is the third-party library that nearly every bytecode tool used to read and write class
  files: mocking frameworks, coverage tools, agents, Spring.
- the **Class-File API** is the JDK's own parser and writer. `ClassFile.of().parse(bytes)`
  returns a `ClassModel` you can ask for methods, fields and the version.
- **`UnsupportedClassVersionError`** is what the JVM throws when a class file's major version is
  newer than it understands.

That gives one rule, and everything below follows from it: **a class-file reader can only
understand formats that existed when it was written.** ASM is released separately from the JDK,
so it falls behind every new JDK until someone updates it. The JDK's reader is released with the
JDK, so it's never behind the JDK it runs on. That's the whole reason the API exists.

## The worked example

ASM 9.6 is already in your local Maven repository: something in Maven's own plugin stack pulled it
in (`~/.m2/repository/org/ow2/asm/asm/9.6/asm-9.6.jar`). Hand it `Door.class` compiled for three
releases (verified):

    javac --release 22   major 66    new ClassReader(bytes)   ok
    javac --release 23   major 67    IllegalArgumentException: Unsupported class file major version 67
    javac --release 26   major 70    IllegalArgumentException: Unsupported class file major version 70

**That's the failure mode, live.** ASM 9.6 was written when 22 was the newest format, so a
library built on it breaks deep inside a framework the day you compile with a newer `--release`,
and you wait for ASM, then for the library, to catch up. If you've ever been unable to upgrade
Java because a mocking or coverage tool couldn't read the new bytecode, this is why.

The JVM has the same problem, and you can see its version of it, because JDK 25 is installed here
next to 26. A class compiled at `--release 26`, run on JDK 25 (verified):

    java.lang.UnsupportedClassVersionError: M has been compiled by a more recent version of the
    Java Runtime (class file version 70.0), this version of the Java Runtime only recognizes class
    file versions up to 69.0

**70 means it was compiled for 26, and 69 means the JVM is 25.** Being able to decode that line on
sight is worth more day to day than the rest of this doc.

**A correction to the old version:** it said the JDK's API "cannot lag" the format. It can, by
the same amount as the JVM. Run JDK 25's `ClassFile.of().parse` on that Java 26 `Door.class` and it
throws `IllegalArgumentException: Unsupported class file version: 70` (verified). On JDK 26,
patch the version bytes to 99 and you get the same exception with 99. What changed isn't that the
API reads the future. It's that **a tool built on it needs no library update to read the
present**: upgrade the JDK the tool runs on, which you had to do anyway to run the classes, and the
reader comes with it.

## Part one: `Inspect`, TODOs 1 to 4

Facts, all verified against the build's own `Door.class`:

| Question | Answer |
| --- | --- |
| first four bytes | `CA FE BA BE`, which `reads_a_class_off_the_classpath` checks |
| major version | **70**, because `pom.xml` sets `<java.release>26`. The test asserts `>= 65` so it survives a change of release. |
| methods, in order | `[<init>, isOpen, toggle, toString]` |
| fields | `[open]` |

The version table behind the rule, from `javac --release N` and `javap -v` (verified):

| Java | 8 | 11 | 17 | 21 | 24 | 25 | 26 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| major | 52 | 55 | 61 | 65 | 68 | 69 | 70 |

Commits, in this order:

1. **Delete the `@Disabled`, and do TODO 1 alone.** `bytesOf` needs the resource name of a class,
   which isn't its fully qualified name. `Class.getResourceAsStream` resolves a relative name
   against the class's own package, which makes this shorter than you expect. One test goes
   green.
2. **TODOs 2 to 4**, with `ClassFile.of().parse`. All five tests green.
3. **Then the surprise, in the commit message.** `Door.java` declares three methods and the class
   file has four. Which one didn't you write, and why is its name one you can't type as a Java
   identifier?

**One comment in the test is wrong:** the javadoc above `works_on_itself` says Inspect "has
exactly one method". `javap -p` shows five: the private constructor and the four methods you're
writing. The assertions are right (they only check two names), so the test passes. Fix the
comment when you make that test green.

## Part two: the class file isn't the source

Point your `methodNamesOf` and `fieldNamesOf` at a few more classes and write down what you
didn't expect. Here's what the JDK 26 parser reports for three tiny classes (verified):

    class S { static final long T = System.nanoTime();  static final int C = 3;  void m() {} }
        methods [<init>, m, <clinit>]                   fields [T, C]

    record P(int x) {}
        methods [<init>, toString, hashCode, equals, x] fields [x]

    enum E { A }
        methods [values, valueOf, <init>, $values, <clinit>]   fields [A, $VALUES]

`<clinit>` is the static initialiser. `S` has one because `T` needs code to run when the class
loads. `C` doesn't: it's a compile-time constant, so `javac` puts the value in the class file and
callers copy it, with no code at all. The record's `equals`, `hashCode` and `toString` are all
there, because records generate real methods ([doc 01](01-records.md)). `$values` in the enum is a
synthetic helper you'll never see in source.

None of this needs a commit. Paste the output for one class of your own into the
"What got worse" section and say what surprised you.

## What got worse

- _(fill this in)_

Prompts: ASM has existed since 2002, and the API went final two releases after it was previewed.
What does the JDK gain by owning this that a well-maintained library couldn't? The correction above
shows it doesn't remove version errors, it only moves them. Is that worth a new package in
`java.lang`? If you wrote a Java agent today, would you use this or ASM, and what would decide
it? The API is immutable and builder-based, and ASM is visitor-based. Which would you rather
debug?
