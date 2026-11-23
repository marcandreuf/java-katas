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

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `Inspect`](#exercise-1-inspect-todos-1-to-4) | Fill in TODOs 1 to 4: read a class's bytes, then its version, methods and fields. Three commits. | T1, T2, T4 | `InspectTest` exists; delete its `@Disabled`. |
| [2. The class file isn't the source](#exercise-2-the-class-file-isnt-the-source) | Point your `Inspect` at a few more classes and write down what surprised you. | T1 | None; no commit. |
| [3. What got worse](#exercise-3-fill-in-what-got-worse) | Write down what the API costs. | all | None. |
| [4. Review questions](#exercise-4-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the four sections in order: each one builds on the one before. Here is the whole story in one
paragraph, so you know where it's going.

> What the JVM runs isn't your source: it's a class file, which has methods you didn't write and
> is missing things you did (T1). Every class file carries a version number, and a reader can only
> understand the versions that existed when it was written. The JVM is a reader, and you've seen
> its error message (T2). For twenty years the other readers, in mocking frameworks, coverage tools
> and agents, were built on a library called ASM, released separately from the JDK, so it fell
> behind every new release (T3). Java 24 put a reader and writer into the JDK itself. It doesn't
> make version errors go away; it makes a tool's reader arrive with the JDK it runs on (T4).

The Java samples below are complete programs. Save each as the class name plus `.java` and run it
with `java Name.java`; no build needed. The ```` ```sh ```` blocks are commands to run in the same
directory. Outputs marked *verified* were run on JDK 26.

### T1. A class file is what runs, and it isn't your source

`javac` doesn't hand your source to the JVM. It writes a **class file**: the bytes the JVM loads.
Most of the time you never look at one, so it's easy to assume it's your source in a different
shape. It isn't. Compile this:

```java
import java.util.function.Supplier;

public class Greeting {
    static final String WORD = "hello";            // a compile-time constant

    public static void main(String[] args) {
        Supplier<String> s = () -> WORD + ", world";
        System.out.println(s.get());
    }
}
```

and look at it with `javap`, the JDK's class-file printer (`-p` includes private members, `-c`
shows the bytecode):

```sh
javac --release 26 Greeting.java
javap -p Greeting.class
javap -c -p Greeting.class
```

```text
Compiled from "Greeting.java"                            (verified 2026-11-23)
public class Greeting {
  static final java.lang.String WORD;
  public Greeting();
  public static void main(java.lang.String[]);
  private static java.lang.String lambda$main$0();
}
...
  private static java.lang.String lambda$main$0();
    Code:
         0: ldc           #32                 // String hello, world
         2: areturn
```

Two things in that output aren't in the source:

- **Methods you didn't write.** `public Greeting()` is the default constructor that `javac` adds
  when you declare none. `lambda$main$0` is the lambda's body, moved into a private static method.
  Its name has a `$` in a place no hand-written method would put one, so it can't clash with yours.
- **Code you did write, already gone.** The lambda doesn't concatenate anything at run time. `WORD`
  is a compile-time constant, so `javac` folded `WORD + ", world"` into the single string
  `"hello, world"` and loads it with one `ldc` instruction.

Every tool that works on compiled code (a mocking framework, a coverage tool, a Java agent) sees
this file, not your source. That's what a class-file reader is for.

### T2. The version number, and the error you've already seen

The first eight bytes of every class file are a header. Here are `Greeting.class`'s:

```sh
xxd -l 8 Greeting.class
javap -v Greeting.class | grep -E 'minor|major'
```

```text
00000000: cafe babe 0000 0046                      .......F   (verified 2026-11-23)
  minor version: 0
  major version: 70
```

Bytes 0 to 3 are the magic number `CA FE BA BE`, which says "this is a class file". Bytes 6 and 7
are the **major version**, here `0x46` = 70. It records which release the file was compiled for,
and the rule is simple: **major = release + 44**. The table, from `javac --release N` and
`javap -v` (verified):

| Java | 8 | 11 | 17 | 21 | 24 | 25 | 26 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| major | 52 | 55 | 61 | 65 | 68 | 69 | 70 |

The JVM is a class-file reader too, and it can only read versions up to its own. Ask it to load a
newer one and it throws **`UnsupportedClassVersionError`**. You can see that on a single JDK by
compiling a class and then patching its version bytes to a release that doesn't exist yet:

```java
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;

public class TooNew {
    public static void main(String[] args) throws Exception {
        // Compile a one-line class into a temp directory, with the JDK's own javac.
        Path dir = Files.createTempDirectory("toonew");
        Path source = Files.writeString(dir.resolve("Tiny.java"), "public class Tiny {}");
        ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "26", source.toString());
        byte[] bytes = Files.readAllBytes(dir.resolve("Tiny.class"));

        System.out.println("as compiled: " + load(bytes));

        bytes[6] = 0;                                  // bytes 6 and 7 hold the major version:
        bytes[7] = 99;                                 // pretend it came from "Java 55"
        System.out.println("patched:     " + load(bytes));
    }

    static String load(byte[] bytes) {
        ClassLoader loader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) {
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        try {
            return "loaded " + loader.loadClass("Tiny");
        } catch (Throwable e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }
}
```

```text
as compiled: loaded class Tiny                           (verified 2026-11-23)
patched:     UnsupportedClassVersionError: Tiny has been compiled by a more recent version of the Java Runtime (class file version 99.0), this version of the Java Runtime only recognizes class file versions up to 70.0
```

You'll meet the real version of this when a class built for a newer Java runs on an older one. JDK
25 is installed here next to 26, so here's a class compiled at `--release 26`, run on JDK 25
(verified):

```text
java.lang.UnsupportedClassVersionError: M has been compiled by a more recent version of the
Java Runtime (class file version 70.0), this version of the Java Runtime only recognizes class
file versions up to 69.0
```

**70 means it was compiled for 26, and 69 means the JVM is 25.** Being able to decode that line on
sight is worth more day to day than the rest of this doc.

That gives one rule, and everything below follows from it:

> **A class-file reader can only understand formats that existed when it was written.**

### T3. ASM, and why tools broke on every new JDK

The JVM isn't the only reader. Mocking frameworks, coverage tools, Java agents and Spring all read
(and often rewrite) class files, and nearly all of them did it with **ASM**, a third-party library
that has existed since 2002. ASM is released separately from the JDK. So by T2's rule, it falls
behind every new JDK until someone updates it, and then every library built on it has to update
too.

You can watch it happen. ASM 9.6 and 9.7 are already in your local Maven repository (something in
Maven's own plugin stack pulled them in). This program asks ASM for a class's name, which means it
has to parse the file first:

```java
import java.nio.file.Files;
import java.nio.file.Path;
import org.objectweb.asm.ClassReader;

public class OldReader {
    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(args[0]));
        try {
            System.out.println("ASM read: " + new ClassReader(bytes).getClassName());
        } catch (IllegalArgumentException e) {
            System.out.println("ASM failed: " + e.getMessage());
        }
    }
}
```

```sh
mkdir -p r22 r26
javac --release 22 -d r22 Greeting.java
javac --release 26 -d r26 Greeting.java
java -cp ~/.m2/repository/org/ow2/asm/asm/9.6/asm-9.6.jar OldReader.java r22/Greeting.class
java -cp ~/.m2/repository/org/ow2/asm/asm/9.6/asm-9.6.jar OldReader.java r26/Greeting.class
```

```text
ASM read: Greeting                                       (verified 2026-11-23)
ASM failed: Unsupported class file major version 70
```

ASM 9.7 gives the same two lines. The old version of this doc ran the same experiment on the
repo's `Door.class` with ASM 9.6, across three releases (verified):

```text
javac --release 22   major 66    new ClassReader(bytes)   ok
javac --release 23   major 67    IllegalArgumentException: Unsupported class file major version 67
javac --release 26   major 70    IllegalArgumentException: Unsupported class file major version 70
```

**That's the failure mode, live.** ASM 9.6 was written when 22 was the newest format, so a
library built on it breaks deep inside a framework the day you compile with a newer `--release`,
and you wait for ASM, then for the library, to catch up. If you've ever been unable to upgrade
Java because a mocking or coverage tool couldn't read the new bytecode, this is why.

### T4. The JDK's own reader and writer, and what it doesn't fix

Java 24's answer is the **Class-File API**, `java.lang.classfile`: a parser and writer that ship
inside the JDK. `ClassFile.of().parse(bytes)` returns a `ClassModel` you can ask for methods,
fields and the version; that's what you'll use in exercise 1. It writes class files too, with
builders, so here is the other direction: a class that no source file ever described, built from
nothing, loaded and called.

```java
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;

public class Forge {
    public static void main(String[] args) throws Exception {
        // Build a class that no source file ever described:
        //     public class Greeter { public static String greet() { return "hello from bytes"; } }
        byte[] bytes = ClassFile.of().build(ClassDesc.of("Greeter"), clazz -> clazz
                .withFlags(ClassFile.ACC_PUBLIC)
                .withMethodBody("greet",
                        MethodTypeDesc.of(ConstantDescs.CD_String),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                        code -> code.ldc("hello from bytes").areturn()));

        Files.write(Path.of("Greeter.class"), bytes);    // so javap can look at it too

        ClassLoader loader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) {
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        Class<?> greeter = loader.loadClass("Greeter");
        System.out.println(bytes.length + " bytes, loaded " + greeter);
        System.out.println(greeter.getMethod("greet").invoke(null));
    }
}
```

```sh
java Forge.java
javap -v Greeter.class | grep major
javap -c Greeter.class
```

```text
148 bytes, loaded class Greeter                          (verified 2026-11-23)
hello from bytes
  major version: 70
public class Greeter {
  public static java.lang.String greet();
    Code:
         0: ldc           #6                  // String hello from bytes
         2: areturn
}
```

Notice the shape: you never call "write a method" on a mutable object. You hand the builder a
lambda that describes the class, and the lambda hands the method builder a lambda that describes
the code. And notice the version: the JDK 26 writer produced major 70 without being told. It knows
the newest format because it shipped with it.

**A correction to the old version of this doc:** it said the JDK's API "cannot lag" the format. It
can, by the same amount as the JVM. Run JDK 25's `ClassFile.of().parse` on a Java 26
`Door.class` and it throws `IllegalArgumentException: Unsupported class file version: 70`
(verified). On JDK 26, patch the version bytes to 99 (as `TooNew` does) and you get the same
exception with 99. T2's rule still holds.

What changed isn't that the API reads the future. It's that **a tool built on it needs no library
update to read the present**: upgrade the JDK the tool runs on, which you had to do anyway to run
the classes, and the reader comes with it. With ASM, you upgraded the JDK and then waited.

## Practice

### Exercise 1: `Inspect`, TODOs 1 to 4

Facts, all verified against the build's own `Door.class`:

| Question | Answer |
| --- | --- |
| first four bytes | `CA FE BA BE`, which `reads_a_class_off_the_classpath` checks |
| major version | **70**, because `pom.xml` sets `<java.release>26`. The test asserts `>= 65` so it survives a change of release. |
| methods, in order | `[<init>, isOpen, toggle, toString]` |
| fields | `[open]` |

Commits, in this order:

1. **Delete the `@Disabled`, and do TODO 1 alone.** `bytesOf` needs the resource name of a class,
   which isn't its fully qualified name. `Class.getResourceAsStream` resolves a relative name
   against the class's own package, which makes this shorter than you expect. One test goes
   green.
2. **TODOs 2 to 4**, with `ClassFile.of().parse` (T4). All five tests green.
3. **Then the surprise, in the commit message.** `Door.java` declares three methods and the class
   file has four. Which one didn't you write, and why is its name one you can't type as a Java
   identifier? (T1 has a hint.)

**One comment in the test is wrong:** the javadoc above `works_on_itself` says Inspect "has
exactly one method". `javap -p` shows five: the private constructor and the four methods you're
writing. The assertions are right (they only check two names), so the test passes. Fix the
comment when you make that test green.

**Done when:** the three commits are in, all five `InspectTest` tests pass, the commit message
answers the surprise, and the `works_on_itself` comment is fixed.

### Exercise 2: the class file isn't the source

Point your `methodNamesOf` and `fieldNamesOf` at a few more classes and write down what you
didn't expect. Here's what the JDK 26 parser reports for three tiny classes (verified):

```text
class S { static final long T = System.nanoTime();  static final int C = 3;  void m() {} }
    methods [<init>, m, <clinit>]                   fields [T, C]

record P(int x) {}
    methods [<init>, toString, hashCode, equals, x] fields [x]

enum E { A }
    methods [values, valueOf, <init>, $values, <clinit>]   fields [A, $VALUES]
```

`<clinit>` is the static initialiser. `S` has one because `T` needs code to run when the class
loads. `C` doesn't: it's a compile-time constant, so `javac` puts the value in the class file and
callers copy it, with no code at all (T1's `WORD` is the same thing seen from the caller's side).
The record's `equals`, `hashCode` and `toString` are all there, because records generate real
methods ([doc 01](01-records.md)). `$values` in the enum is a synthetic helper you'll never see in
source.

None of this needs a commit. Paste the output for one class of your own into the
"What got worse" section and say what surprised you.

**Done when:** you've run your `Inspect` on at least one class of your own and written down one
thing in its output that isn't in its source.

### Exercise 3: fill in "What got worse"

- _(fill this in)_

Prompts: ASM has existed since 2002, and the API went final two releases after it was previewed.
What does the JDK gain by owning this that a well-maintained library couldn't? The correction in
T4 shows it doesn't remove version errors, it only moves them. Is that worth a new package in
`java.lang`? If you wrote a Java agent today, would you use this or ASM, and what would decide
it? The API is immutable and builder-based, and ASM is visitor-based. Which would you rather
debug?

### Exercise 4: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. Name two things in a class file that aren't in its source, and one thing in the source that
   isn't in the class file. (T1)
2. What's in the first four bytes of a class file, and where is the major version? (T2)
3. A class file has major version 65. Which Java release was it compiled for? (T2)
4. Decode: "class file version 70.0, this version of the Java Runtime only recognizes class file
   versions up to 69.0". What compiled it, and what's trying to run it? (T2)
5. State the one rule about class-file readers. (T2)
6. Why did mocking and coverage tools break when you moved to a new `--release`, even though your
   code compiled fine? (T3)
7. Does the Class-File API on JDK 25 read a class compiled for 26? What does it do? (T4)
8. Then what did the API actually change for a tool author? (T4)
9. In `Forge`, which major version did the writer produce, and why didn't it need to be told? (T4)

**Done when:** you can answer all nine without looking.
