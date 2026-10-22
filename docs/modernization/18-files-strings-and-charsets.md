# 18. Files, strings and charsets

**Java 7** (try-with-resources) · **Java 9** (`try (existingVariable)`) · **Java 11**
(`Files.readString`, `strip`, `isBlank`, `lines`, `repeat`) · **Java 12** (`indent`,
`transform`) · **Java 18** (UTF-8 by default, JEP 400) · stage 7 of
[the release-ordered track](INDEX.md) · no build change

> **No safety net:** `RandomFileLines` has no test. Write one first, in a separate commit,
> asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md)
> says how, and why the order matters.

## What this stage is actually about

Five words, defined once:

- a **charset** is the rule for turning bytes into characters. The same two bytes, `C2 A0`, are
  one character in UTF-8 (a no-break space) and two in windows-1252 (`Â` and a no-break space).
- the **default charset** is the one an API uses when you don't name one. Since Java 18 it is
  UTF-8. Before that, it came from the operating system's locale.
- **malformed input** is a byte sequence that isn't valid in the charset you're decoding with.
  An API can **throw** (`MalformedInputException`) or **replace** each bad sequence with U+FFFD, the
  replacement character `�`, and carry on.
- **whitespace** has two definitions in `String`. `trim()` (1.0) removes every character up to
  U+0020. `strip()` (11) removes what `Character.isWhitespace` says is whitespace. They are not
  the same set, and neither of them is "everything that looks blank".
- a **resource** is anything that must be closed: a reader, a stream from `Files.lines`, a
  socket. **try-with-resources** closes it for you, even when the body throws.

That gives one rule, and everything below follows from it: **any API that chooses a charset or a
definition of whitespace for you has given you a default, and a default is a decision you didn't
make.** The code that behaves the same everywhere is the code that names its charset.

## The worked example: this repo's own text file

`src/main/resources/randomLinesText.txt` is the file `RandomFileLines` reads. It's 36 lines, 27
of them non-blank, and **8 of them end in a no-break space**, the bytes `C2 A0` (verified with
`od -c`). It was probably pasted from a web page. Read its first line, `Hi all,` plus that
character, with `new FileReader(file)` under three default charsets (verified):

| How the JVM was started | Default charset | Line 1 length | Last characters |
| --- | --- | --- | --- |
| `java ...` (JDK 26, any locale) | UTF-8 | 8 | `,` U+00A0 |
| `LC_ALL=C java -Dfile.encoding=COMPAT ...` | US-ASCII | 9 | `,` U+FFFD U+FFFD |
| `java -Dfile.encoding=windows-1252 ...` | windows-1252 | 9 | `,` U+00C2 U+00A0 |

The second row is what Java 17 and earlier did on a machine with that locale. JDK 26 still
accepts `-Dfile.encoding=COMPAT` as the escape hatch, and it still means "use the locale's
charset" (verified). The third row is what a Western-European Windows machine used to do: `Hi all,Â`
and a space. **The same program read the same file three different ways**, depending only on how
the machine was set up. JEP 400 made the first row the default everywhere.

But not for output. Run `RandomFileLines` on JDK 26 with `LC_ALL=C`, and the file is read
correctly, while every no-break space in the printed lines comes out as `?` (verified). JEP 400
deliberately left `System.out` on the console's encoding. `-XshowSettings:properties` shows the
split: `file.encoding = UTF-8` and `stdout.encoding = ANSI_X3.4-1968` (verified). And one more
default with no warning: `-Dfile.encoding=bogus` starts normally and uses UTF-8 (verified).

## Part one: `io/TextFiles.java`, five TODOs

Delete the `@Disabled` on `TextFilesTest` first.

**TODO 1, `Files.readString`.** It reads the whole file as UTF-8 in one call, with no reader
variable and nothing to close.

**TODO 2, the charset**, and the test `reading_latin1_bytes_as_utf8_throws_rather_than_corrupting`.
It writes `café` as ISO-8859-1, which is the bytes `99 97 102 -23` (verified). Here is what every
way of reading that file back on JDK 26 does with the `-23`, which isn't valid UTF-8 (all
verified):

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
becomes a replacement character in a database and a bug report six months later. **A correction to
the old version of this doc:** it listed `FileReader` only as "platform charset before 18, UTF-8
since". The part that matters is the other column: `FileReader` **replaces**, and the `Files`
methods throw. Switching `RandomFileLines` from one to the other changes what happens to a bad
file, even on a machine where the charset was always UTF-8.

**TODO 3, `lines()`.** Verified:

    "a\r\nb\r\n".lines()      [a, b]
    "a\r\nb\r\n".split("\n")  [a\r, b\r]     a \r left on every line
    "a\rb".split("\n")        [a\rb]         one "line"
    "a\n\n".split("\n")       [a]            and split drops trailing empty strings
    "a\n\n".lines()           [a, ]          lines() doesn't

**TODO 4, `strip` and `trim`.** The test pads `abc` with U+2000 EN QUAD: `trim()` leaves it at
length 5, and `strip()` removes it, length 3 (verified). For ASCII input they agree, which is why
nobody notices.

**A correction to the old version:** it said `strip()` is the answer when "a CSV arrives with a
non-breaking space in it". **It isn't.** `Character.isWhitespace(' ')` is `false` (verified),
deliberately: a no-break space is meant to *not* break, so Java doesn't count it as whitespace.
`strip()` leaves it, `trim()` leaves it, and `" ".isBlank()` is `false` (all verified). That's
the character at the end of 8 lines of this repo's own file. Other characters that neither method
removes: U+2007 FIGURE SPACE, U+202F NARROW NO-BREAK SPACE, U+200B ZERO WIDTH SPACE, U+FEFF (the
byte-order mark). U+3000 IDEOGRAPHIC SPACE is removed by `strip()` and not by `trim()` (all
verified). **Neither method means "invisible".** If your input has these, you need to decide
which characters you mean and say so.

**TODO 5, `repeat`.** Also in the family: `indent(n)` (12), which adds a trailing newline you may
not expect (`"a\nb".indent(2)` is `"  a\n  b\n"`, verified), and `transform(fn)` (12).

## Part two: `RandomFileLines`, the `finally` and the charset

`collections/RandomFileLines.java` has four problems. [Doc 09](09-streams-and-optional.md) owns
two (the off-by-one at line 35 and the double report). This doc owns the other two.
`readFileLines` is lines 47-74. **A correction to the old version**, which said 48-60: that range
stops before the `finally`, which is the part this doc is about.

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

Run it from a directory where the file doesn't exist, and this is the whole output (verified):

    src/main/resources/randomLinesText.txt (No such file or directory)
    Cannot invoke "java.io.BufferedReader.close()" because "br" is null

The first line is the `catch` printing the real problem. Then the `finally` throws a
`NullPointerException`, and **an exception thrown from `finally` replaces the one in flight**. The
`FileNotFoundException` is gone: `main` receives the NPE, and prints its message. Called directly,
`readFileLines("no-such-file.txt")` throws `NullPointerException`, with **0** suppressed exceptions
(verified). try-with-resources was designed for exactly this. When both the body and the `close`
throw, the body's exception wins and the `close` failure is attached to it with `getSuppressed()`
(verified with a resource whose `close` throws).

Commits, in this order:

1. **Characterize.** Extract the reading so it can be called with a path, and assert today's
   behaviour: the repo's file gives **27** lines, a line keeps its trailing no-break space, and a
   missing file throws **`NullPointerException`**. That last assertion records the bug, and it's
   meant to.
2. **Fix the `finally`**, alone, with try-with-resources. The missing-file assertion flips in this
   commit, to whatever exception you decide the method should throw. Note that `try (br)`, the
   Java 9 form, won't compile here as the code stands: `br` is assigned twice, and `javac` says
   `variable ... used as a try-with-resources resource neither final nor effectively final`
   (verified). The restructuring is part of the exercise.
3. **Name the charset**, as its own commit. On this machine it changes nothing, and the 27-line
   test stays green. Say in the commit message which environment it would have changed: row two of
   the worked example's table.
4. **Then rewrite the reading** with `Files.readAllLines` or `Files.lines`, **after** doc 09's
   off-by-one commit, for the reason doc 09 gives. This one is a behaviour change, even though the
   tests may not show it: a file with a bad byte used to load with a `�` in it, and now it throws.
   Add a test for that before the rewrite, so the rewrite is what flips it. The blank-line filter
   uses `line.trim().isEmpty()`. Swapping it for `isBlank()` gives the same 27 lines on this file
   (verified), but the two aren't the same in general: a line of only U+3000 is blank to one and
   not to the other.

## What got worse

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
