# 18. Files, strings and charsets

**Java 7** (try-with-resources) · **Java 9** (`try (existingVariable)`) · **Java 11**
(`Files.readString`, `strip`, `isBlank`, `lines`, `repeat`) · **Java 12** (`indent`,
`transform`) · **Java 18** (UTF-8 by default, JEP 400) · stage 7 of
[the release-ordered track](INDEX.md) · no build change

> **No safety net:** `RandomFileLines` has no test. Write one first, in a separate commit,
> asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md)
> says how, and why the order matters.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `io/TextFiles`](#exercise-1-iotextfiles-five-todos) | Fill in five TODOs, each one a Java 11/12 method. | T1–T5, T7 | `TextFilesTest` exists: delete its `@Disabled` first. |
| [2. `RandomFileLines`](#exercise-2-randomfilelines-the-finally-and-the-charset) | Fix the `finally` that hides the real exception, name the charset, then rewrite the reading. Four commits. | T2, T3, T5, T6 | Characterize first, bugs included. |
| [3. What got worse](#exercise-3-fill-in-what-got-worse) | Write down what the new APIs cost. | all | None. |
| [4. Review questions](#exercise-4-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the seven sections in order: each one builds on the one before. Here is the whole story in
one paragraph, so you know where it's going.

> A file holds bytes, not text, and a charset is the rule that turns one into the other: the same
> bytes are different text under different rules (T1). When you don't name a charset, Java picks
> one for you, and until Java 18 that pick depended on the machine (T2). When the bytes don't fit
> the rule, some APIs throw and others quietly substitute `�`, and which one you called decides
> whether you find out (T3). Once you have text, splitting it into lines and trimming it both hide
> more defaults: three line terminators (T4) and two definitions of whitespace, neither of which
> means "looks blank" (T5). Reading a file also means closing it, and a hand-written `finally` can
> throw away the very exception you needed (T6). The small Java 11/12 string helpers finish the
> picture (T7).

Every code sample below is a complete program. Save it as the class name plus `.java` and run it
with `java Name.java`; no build needed. Outputs marked *verified* were run on JDK 26, on Linux with
a UTF-8 locale unless the section says otherwise. The samples that touch the disk make their own
temp files and delete them.

### T1. Bytes are not text: what a charset is

A file, a socket and a byte array all hold **bytes**. A `String` holds **characters**. Getting
from one to the other needs a rule, and that rule is a **charset**. There is more than one, and
they disagree:

```java
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

public class Decode {
    public static void main(String[] args) {
        // The same two bytes, read with three different rules.
        byte[] bytes = { (byte) 0xC2, (byte) 0xA0 };
        for (String name : new String[] { "UTF-8", "windows-1252", "US-ASCII" }) {
            String text = new String(bytes, Charset.forName(name));
            System.out.printf("%-12s -> %d char(s): %s%n", name, text.length(), codePoints(text));
        }

        // And the other way: one word, two encodings, different bytes.
        String word = "año";
        System.out.println("UTF-8      bytes of año: " + HexFormat.ofDelimiter(" ").formatHex(word.getBytes(StandardCharsets.UTF_8)));
        System.out.println("ISO-8859-1 bytes of año: " + HexFormat.ofDelimiter(" ").formatHex(word.getBytes(StandardCharsets.ISO_8859_1)));
    }

    static String codePoints(String s) {
        return Arrays.toString(s.codePoints().mapToObj(c -> String.format("U+%04X", c)).toArray());
    }
}
```

```text
UTF-8        -> 1 char(s): [U+00A0]                      (verified 2026-11-23)
windows-1252 -> 2 char(s): [U+00C2, U+00A0]
US-ASCII     -> 2 char(s): [U+FFFD, U+FFFD]
UTF-8      bytes of año: 61 c3 b1 6f
ISO-8859-1 bytes of año: 61 f1 6f
```

The bytes `C2 A0` are one character in UTF-8 (a no-break space) and two in windows-1252 (`Â` and
a no-break space). In US-ASCII they aren't valid at all, so each byte became U+FFFD, the
**replacement character** `�`. Going the other way, `ñ` is two bytes in UTF-8 and one in
ISO-8859-1. Neither answer is wrong. They are different rules, and **text is only correct if it is
read with the rule it was written with.**

### T2. The default charset, and the one JEP 400 didn't change

Many APIs take a charset as an optional argument: `new FileReader(file)`, `new String(bytes)`,
`new Scanner(file)`. Leave it out, and they use the **default charset**. Since Java 18 (JEP 400)
that is UTF-8 everywhere. Before that, it came from the operating system's locale, so the same
program behaved differently on different machines.

That gives one rule, and everything in this doc follows from it:

> **Any API that chooses a charset or a definition of whitespace for you has given you a default,
> and a default is a decision you didn't make.** The code that behaves the same everywhere is the
> code that names its charset.

You can see the defaults the JVM picked:

```java
import java.nio.charset.Charset;

public class Defaults {
    public static void main(String[] args) {
        System.out.println("Charset.defaultCharset() = " + Charset.defaultCharset());
        System.out.println("file.encoding            = " + System.getProperty("file.encoding"));
        System.out.println("native.encoding          = " + System.getProperty("native.encoding"));
        System.out.println("stdout.encoding          = " + System.getProperty("stdout.encoding"));
    }
}
```

```sh
java Defaults.java
LC_ALL=C java Defaults.java
LC_ALL=C java -Dfile.encoding=COMPAT Defaults.java
```

```text
Charset.defaultCharset() = UTF-8                         (verified 2026-11-23, UTF-8 locale)
file.encoding            = UTF-8
native.encoding          = UTF-8
stdout.encoding          = UTF-8

Charset.defaultCharset() = UTF-8                         (LC_ALL=C)
file.encoding            = UTF-8
native.encoding          = ANSI_X3.4-1968
stdout.encoding          = ANSI_X3.4-1968

Charset.defaultCharset() = US-ASCII                      (LC_ALL=C, -Dfile.encoding=COMPAT)
file.encoding            = ANSI_X3.4-1968
native.encoding          = ANSI_X3.4-1968
stdout.encoding          = ANSI_X3.4-1968
```

Read the three runs together. `native.encoding` is what the machine's locale says, and it changes
with `LC_ALL`. On JDK 18 and later, the default charset ignores it and stays UTF-8. `COMPAT` is
the escape hatch back to the old behaviour: "use the locale's charset". And `stdout.encoding`
follows the locale in both runs: **JEP 400 deliberately left `System.out` on the console's
encoding.** (`ANSI_X3.4-1968` is the formal name for US-ASCII.)

**The repo's own file shows why this mattered.** `src/main/resources/randomLinesText.txt` is the
file `RandomFileLines` reads. It's 36 lines, 27 of them non-blank, and **8 of them end in a
no-break space**, the bytes `C2 A0` (verified with `od -c`). It was probably pasted from a web
page. Read its first line, `Hi all,` plus that character, with `new FileReader(file)` under three
default charsets (verified):

| How the JVM was started | Default charset | Line 1 length | Last characters |
| --- | --- | --- | --- |
| `java ...` (JDK 26, any locale) | UTF-8 | 8 | `,` U+00A0 |
| `LC_ALL=C java -Dfile.encoding=COMPAT ...` | US-ASCII | 9 | `,` U+FFFD U+FFFD |
| `java -Dfile.encoding=windows-1252 ...` | windows-1252 | 9 | `,` U+00C2 U+00A0 |

The second row is what Java 17 and earlier did on a machine with that locale. JDK 26 still
accepts `-Dfile.encoding=COMPAT`, and it still means "use the locale's charset" (verified). The
third row is what a Western-European Windows machine used to do: `Hi all,Â` and a space. **The
same program read the same file three different ways**, depending only on how the machine was set
up: exactly T1's table, happening by accident. JEP 400 made the first row the default everywhere.

But not for output. Run `RandomFileLines` on JDK 26 with `LC_ALL=C`, and the file is read
correctly, while every no-break space in the printed lines comes out as `?` (verified). That's
the `stdout.encoding` line above. `-XshowSettings:properties` shows the same split:
`file.encoding = UTF-8` and `stdout.encoding = ANSI_X3.4-1968` (verified). And one more default
with no warning: `-Dfile.encoding=bogus` starts normally and uses UTF-8 (verified).

### T3. Malformed input: throw, or replace and carry on

T1 showed US-ASCII turning bytes it didn't understand into `�`. That's one of two things an API
can do with **malformed input**, a byte sequence that isn't valid in the charset you're decoding
with:

- **throw** a `MalformedInputException`, so you find out now, or
- **replace** each bad sequence with U+FFFD `�`, and carry on, so you find out later, if ever.

Which one you get depends on the API, not the charset:

```java
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class StrictOrReplace {
    public static void main(String[] args) throws IOException {
        Path file = Files.createTempFile("latin1-", ".txt");
        try {
            // "año" written as ISO-8859-1: the ñ is the single byte F1, which isn't valid UTF-8.
            Files.write(file, "año".getBytes(StandardCharsets.ISO_8859_1));

            try {
                Files.readString(file);                              // UTF-8, strict
            } catch (IOException e) {
                System.out.println("Files.readString:     throws " + e);
            }

            byte[] bytes = Files.readAllBytes(file);
            System.out.println("new String(.., UTF_8): " + new String(bytes, StandardCharsets.UTF_8));

            try (BufferedReader in = new BufferedReader(new FileReader(file.toFile()))) {
                System.out.println("FileReader:            " + in.readLine());
            }

            System.out.println("named ISO_8859_1:      " + Files.readString(file, StandardCharsets.ISO_8859_1));
        } finally {
            Files.delete(file);
        }
    }
}
```

```text
Files.readString:     throws java.nio.charset.MalformedInputException: Input length = 1   (verified 2026-11-23)
new String(.., UTF_8): a�o
FileReader:            a�o
named ISO_8859_1:      año
```

All three UTF-8 reads saw the same bad byte. Only `Files.readString` told you. The full list, for
every way of reading such a file back on JDK 26 (all verified, with the test's `café` file from
exercise 1):

| API | What happens |
| --- | --- |
| `Files.readString(p)` | throws `MalformedInputException: Input length = 1` |
| `Files.readAllLines(p)` | throws `MalformedInputException: Input length = 1` |
| `Files.lines(p)` | no exception when you call it. The terminal operation throws `UncheckedIOException` wrapping the same `MalformedInputException` |
| `new String(bytes, UTF_8)` | `caf�`: replaced, silently |
| `new FileReader(p)` + `readLine()` | `caf�`: replaced, silently |
| `new Scanner(p)` + `nextLine()` | `caf�`: replaced, silently |
| `Files.readString(p, ISO_8859_1)` | `café` |

**The `Files` methods are strict, and that's a feature.** They are the only ones that tell you the
file isn't what you thought. The others substitute `�` and carry on, which is how one bad byte
becomes a replacement character in a database and a bug report six months later.

**A correction to the old version of this doc:** it listed `FileReader` only as "platform charset
before 18, UTF-8 since". The part that matters is the other column: `FileReader` **replaces**, and
the `Files` methods throw. Switching `RandomFileLines` from one to the other changes what happens
to a bad file, even on a machine where the charset was always UTF-8.

### T4. Lines: three terminators, and `split` knows one

Text from different systems ends its lines differently: `\n` (Unix), `\r\n` (Windows) and, rarely
now, a bare `\r` (classic Mac OS). `String.lines()` (Java 11) knows all three. `split("\n")` knows
the one you typed:

```java
import java.util.Arrays;

public class Terminators {
    public static void main(String[] args) {
        String unix    = "one\ntwo\n";
        String windows = "one\r\ntwo\r\n";
        String oldMac  = "one\rtwo";

        for (String text : new String[] { unix, windows, oldMac }) {
            System.out.println(show(text) + "  lines(): " + show(text.lines().toList().toString())
                    + "  split: " + show(Arrays.toString(text.split("\n"))));
        }
    }

    // Make \r and \n visible.
    static String show(String s) {
        return s.replace("\r", "\\r").replace("\n", "\\n");
    }
}
```

```text
one\ntwo\n  lines(): [one, two]  split: [one, two]       (verified 2026-11-23)
one\r\ntwo\r\n  lines(): [one, two]  split: [one\r, two\r]
one\rtwo  lines(): [one, two]  split: [one\rtwo]
```

On Unix text they agree, which is why nobody notices. On Windows text, `split` leaves a `\r` on
every line, invisible when printed and fatal to an `equals`. One more difference, on trailing
blank lines (verified):

```text
"a\n\n".split("\n")       [a]            split drops trailing empty strings
"a\n\n".lines()           [a, ]          lines() doesn't
```

### T5. Whitespace: two definitions, and neither means "looks blank"

`String` has two ideas of whitespace:

- **`trim()`** (1.0) removes every character up to U+0020 from both ends: space, tab, newline,
  and the control characters.
- **`strip()`** (11) removes whatever `Character.isWhitespace` says is whitespace. `isBlank()`
  (11) uses the same test: "empty, or only whitespace".

They aren't the same set, and the gap is bigger than it looks. Here are nine characters that look
like a space, or like nothing:

```java
public class Spaces {
    public static void main(String[] args) {
        int[] candidates = { 0x0020, 0x0009, 0x00A0, 0x2000, 0x2007, 0x202F, 0x200B, 0x3000, 0xFEFF };
        System.out.println("char    isWhitespace  trim removes  strip removes  isBlank");
        for (int c : candidates) {
            String one = Character.toString(c);
            String padded = one + "x" + one;
            System.out.printf("U+%04X  %-12s  %-12s  %-13s  %s%n", c,
                    Character.isWhitespace(c),
                    padded.trim().equals("x"),
                    padded.strip().equals("x"),
                    one.isBlank());
        }
    }
}
```

```text
char    isWhitespace  trim removes  strip removes  isBlank      (verified 2026-11-23)
U+0020  true          true          true           true
U+0009  true          true          true           true
U+00A0  false         false         false          false
U+2000  true          false         true           true
U+2007  false         false         false          false
U+202F  false         false         false          false
U+200B  false         false         false          false
U+3000  true          false         true           true
U+FEFF  false         false         false          false
```

Three groups:

- **Space and tab**: both methods agree. That's all most input contains, which is why nobody
  notices the difference.
- **U+2000 EN QUAD and U+3000 IDEOGRAPHIC SPACE**: Unicode whitespace above U+0020. `strip()`
  removes them, `trim()` doesn't.
- **U+00A0 NO-BREAK SPACE, U+2007 FIGURE SPACE, U+202F NARROW NO-BREAK SPACE, U+200B ZERO WIDTH
  SPACE, U+FEFF** (the byte-order mark): **neither** removes them, and `isBlank()` says no.

**A correction to the old version of this doc:** it said `strip()` is the answer when "a CSV
arrives with a non-breaking space in it". **It isn't.** `Character.isWhitespace(' ')` is `false`
(verified), deliberately: a no-break space is meant to *not* break, so Java doesn't count it as
whitespace. `strip()` leaves it, `trim()` leaves it, and `" ".isBlank()` is `false` (all
verified). That's the character at the end of 8 lines of this repo's own file (T2). **Neither
method means "invisible".** If your input has these, you need to decide which characters you mean
and say so.

### T6. Closing a resource: the `finally` that loses the exception

A **resource** is anything that must be closed: a reader, a stream from `Files.lines`, a socket.
Before Java 7 you closed it in a `finally`, by hand. That has a trap: **an exception thrown from
`finally` replaces the one already in flight.** If the open fails, the variable is still `null`,
`close()` throws a `NullPointerException`, and the real error is gone.

**try-with-resources** (Java 7) closes the resource for you, even when the body throws, and it
gets the ordering right. When both the body and `close()` throw, the body's exception wins, and the
`close` failure is attached to it as a **suppressed** exception (`getSuppressed()`):

```java
import java.io.IOException;

public class Masked {
    // A resource that fails twice: on open if asked to, and always on close.
    static class Fragile implements AutoCloseable {
        Fragile(boolean failOnOpen) throws IOException {
            if (failOnOpen) throw new IOException("open failed");
        }
        void use() throws IOException { throw new IOException("use failed"); }
        @Override public void close() throws IOException { throw new IOException("close failed"); }
    }

    public static void main(String[] args) {
        // 1. Hand-rolled finally: the open fails, then the finally fails too.
        try {
            handRolled();
        } catch (Exception e) {
            System.out.println("hand-rolled: caught " + e + ", suppressed=" + e.getSuppressed().length);
        }

        // 2. try-with-resources: use() fails, then close() fails.
        try (Fragile f = new Fragile(false)) {
            f.use();
        } catch (IOException e) {
            System.out.println("try-with-resources: caught " + e);
            for (Throwable s : e.getSuppressed()) {
                System.out.println("  suppressed: " + s);
            }
        }
    }

    static void handRolled() throws IOException {
        Fragile f = null;
        try {
            f = new Fragile(true);           // throws: f stays null
            f.use();
        } finally {
            f.close();                       // NullPointerException replaces the IOException
        }
    }
}
```

```text
hand-rolled: caught java.lang.NullPointerException: Cannot invoke "Masked$Fragile.close()" because "<local0>" is null, suppressed=0   (verified 2026-11-23)
try-with-resources: caught java.io.IOException: use failed
  suppressed: java.io.IOException: close failed
```

In the first case, `open failed` appears nowhere: the caller gets an NPE with nothing attached.
In the second, nothing is lost: the main failure, with the secondary one hanging off it. (The
`<local0>` is because `java Name.java` compiles without local-variable names. Compiled with
`javac -g`, the same line says `because "f" is null`, verified.) `RandomFileLines` has the first
shape, and exercise 2 is about it.

**Java 9 added a shorter form**, `try (existingVariable) { ... }`, for a resource you already have
in a variable. The variable must be final or effectively final, meaning assigned exactly once.
`javac` refuses anything else with `variable ... used as a try-with-resources resource neither
final nor effectively final` (verified).

### T7. The small helpers: `repeat`, `indent`, `transform`

Three more methods from Java 11 and 12 finish the family. They're small, and each has one detail
worth knowing:

```java
public class Family {
    public static void main(String[] args) {
        System.out.println("[" + "ab".repeat(3) + "]");
        System.out.println("[" + "x".repeat(0) + "]");
        System.out.println("[" + "one\ntwo".indent(4).replace("\n", "\\n") + "]");
        System.out.println("[" + "    deep\n      deeper".indent(-4).replace("\n", "\\n") + "]");
        System.out.println("[" + "  Hello  ".transform(String::strip).transform(String::length) + "]");
    }
}
```

```text
[ababab]                                                 (verified 2026-11-23)
[]
[    one\n    two\n]
[deep\n  deeper\n]
[5]
```

- **`repeat(n)`** (11): `n` copies; `repeat(0)` is the empty string.
- **`indent(n)`** (12) adds `n` spaces to every line, or removes up to `-n` of them, and **adds a
  trailing newline you may not expect**: the input above had none. (`"a\nb".indent(2)` is
  `"  a\n  b\n"`, verified.)
- **`transform(fn)`** (12) applies a function to the string and returns whatever it returns, so a
  chain can end in something that isn't a `String`.

## Practice

### Exercise 1: `io/TextFiles`, five TODOs

Delete the `@Disabled` on `TextFilesTest` first.

**TODO 1, `Files.readString`.** It reads the whole file as UTF-8 in one call, with no reader
variable and nothing to close (T3, T6).

**TODO 2, the charset**, and the test `reading_latin1_bytes_as_utf8_throws_rather_than_corrupting`.
It writes `café` as ISO-8859-1, which is the bytes `99 97 102 -23` (verified). The `-23` (`E9`)
isn't valid UTF-8. T3's table says what every way of reading it back does, and why the test
expects a throw rather than a `�`.

**TODO 3, `lines()`.** T4 says why `split("\n")` is the wrong tool, and the test
`lines_handles_windows_terminators` is the case where it shows.

**TODO 4, `strip` and `trim`.** The test pads `abc` with U+2000 EN QUAD: `trim()` leaves it at
length 5, and `strip()` removes it, length 3 (verified). For ASCII input they agree, which is why
nobody notices (T5).

**TODO 5, `repeat`.** Also in the family: `indent(n)` and `transform(fn)` (T7).

**Done when:** the `@Disabled` line is gone and `TextFilesTest` passes.

### Exercise 2: `RandomFileLines`, the `finally` and the charset

`collections/RandomFileLines.java` has four problems. [Doc 09](09-streams-and-optional.md) owns
two (the off-by-one at line 35 and the double report). This doc owns the other two.
`readFileLines` is lines 47-74. **A correction to the old version**, which said 48-60: that range
stops before the `finally`, which is the part this doc is about.

```java
BufferedReader br = null;                     // line 49
try{
    File fin = new File(fileName);
    FileReader freader = new FileReader(fin); // line 52: default charset
    ...
}catch(Exception e){
    System.out.println(e.getMessage());
    throw e;
}finally {
    try {
        br.close();                           // line 69: br is still null if line 52 threw
    } catch (IOException e) { ... }
}
```

Run it from a directory where the file doesn't exist, and this is the whole output (verified):

```text
src/main/resources/randomLinesText.txt (No such file or directory)
Cannot invoke "java.io.BufferedReader.close()" because "br" is null
```

The first line is the `catch` printing the real problem. Then the `finally` throws a
`NullPointerException`, and, as T6 showed, **an exception thrown from `finally` replaces the one
in flight**. The `FileNotFoundException` is gone: `main` receives the NPE, and prints its message.
Called directly, `readFileLines("no-such-file.txt")` throws `NullPointerException`, with **0**
suppressed exceptions (verified). try-with-resources was designed for exactly this. When both the
body and the `close` throw, the body's exception wins and the `close` failure is attached to it
with `getSuppressed()` (verified with a resource whose `close` throws: T6's `Masked`).

Commits, in this order:

1. **Characterize.** Extract the reading so it can be called with a path, and assert today's
   behaviour: the repo's file gives **27** lines, a line keeps its trailing no-break space, and a
   missing file throws **`NullPointerException`**. That last assertion records the bug, and it's
   meant to.
2. **Fix the `finally`**, alone, with try-with-resources. The missing-file assertion flips in this
   commit, to whatever exception you decide the method should throw. Note that `try (br)`, the
   Java 9 form, won't compile here as the code stands: `br` is assigned twice, and `javac` says
   `variable ... used as a try-with-resources resource neither final nor effectively final`
   (verified; T6). The restructuring is part of the exercise.
3. **Name the charset**, as its own commit. On this machine it changes nothing, and the 27-line
   test stays green. Say in the commit message which environment it would have changed: row two of
   the table in T2.
4. **Then rewrite the reading** with `Files.readAllLines` or `Files.lines`, **after** doc 09's
   off-by-one commit, for the reason doc 09 gives. This one is a behaviour change, even though the
   tests may not show it: a file with a bad byte used to load with a `�` in it, and now it throws
   (T3). Add a test for that before the rewrite, so the rewrite is what flips it. The blank-line
   filter uses `line.trim().isEmpty()`. Swapping it for `isBlank()` gives the same 27 lines on this
   file (verified), but the two aren't the same in general: a line of only U+3000 is blank to one
   and not to the other (T5).

**Done when:** the four commits are in, in that order; the missing-file assertion flipped in
commit 2 and the bad-byte assertion flipped in commit 4; and the 27-line test is green throughout.

### Exercise 3: fill in "What got worse"

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: `Files.readString` reads the whole file into memory. At what file size is that the wrong
call, and what do you use instead? `Files.lines` returns a stream holding an open file, so it must
be closed: what happens if you return it from a method, and would a reviewer notice a missing
try-with-resources around a `Stream`? The strict `Files` methods turned a corrupt file from a `�`
into an exception: who handles that exception now, and is that better for the person running the
program? If `strip()` is better than `trim()` but still not what anyone means by "blank", what
would it take to change every `trim()` in a large codebase, and would you?

### Exercise 4: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. The bytes `C2 A0`: how many characters are they in UTF-8, and in windows-1252? (T1)
2. What is the default charset on JDK 26, and what was it on JDK 17? (T2)
3. On JDK 26 with `LC_ALL=C`, a file with a no-break space is read correctly but prints as `?`.
   Why? (T2)
4. Name one API that throws on malformed UTF-8 and one that replaces it with `�`. Which would you
   rather have, and why? (T3)
5. `Files.lines(p)` on a file with a bad byte: when does it throw, and what type? (T3)
6. What does `"a\r\nb".split("\n")` give, and what does `"a\r\nb".lines()` give? (T4)
7. Does `strip()` remove a no-break space (U+00A0)? Does `trim()`? (T5)
8. Name a character `strip()` removes and `trim()` doesn't. (T5)
9. A hand-written `finally` calls `close()` on a variable that's still `null`. What happens to the
   exception that was already being thrown? (T6)
10. With try-with-resources, both the body and `close()` throw. Which exception does the caller
    see, and where is the other one? (T6)
11. What does `"a".indent(2)` return, exactly? (T7)

**Done when:** you can answer all eleven without looking.
