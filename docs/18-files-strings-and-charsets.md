# 18. Files, strings and charsets

**Java 11, 12, 18** · stage 7 of [the release-ordered track](INDEX.md) · `--release 21`, no build
change

## Where it lands

**`collections/RandomFileLines.java:48-60`** is the richest single target in the repo and
[doc 09](09-streams-and-optional.md) already owns two of its four problems. This document owns the
other two: **the hand-rolled `finally`** and **the charset**.

    File fin = new File(fileName);
    FileReader freader = new FileReader(fin);        // line 52 -- platform charset
    ...
    } finally {
        br.close();                                  // NPEs if the open threw
    }

New code: **`io/TextFiles.java`**, five TODOs.

## The exercise

### `Files.readString`, and what you delete

TODO 1. The whole read becomes one line. Then go back to `RandomFileLines` and **count the lines
you removed** — the reader variable, the buffered wrapper, the `finally`, the nested try that the
`close()` itself needs because `close()` throws too.

The `finally` there has the defect try-with-resources was invented for: if `new FileReader(fin)`
throws, `br` is still null, and `br.close()` in the `finally` throws a `NullPointerException`
**that replaces the real exception**. The file-not-found is gone and you are debugging an NPE.
Java 7 fixed this and this code is from 2014, which makes it the same "how long does an idiom
survive after it is obsolete" observation [doc 05](05-var.md) makes about the diamond.

### The charset, which is the part worth the document

TODO 2, and `TextFilesTest.reading_latin1_bytes_as_utf8_throws_rather_than_corrupting`.

Three APIs, three different answers to "these bytes are not valid UTF-8":

    Files.readString(p)                      -> MalformedInputException     (strict, throws)
    new String(bytes, UTF_8)                 -> "caf?"                      (silent replacement)
    new FileReader(p)                        -> before 18: platform charset; since 18: UTF-8

**`Files.readString` is strict and that is a feature.** It is the only one of the three that tells
you the file is not what you thought. `new String(bytes, UTF_8)` substitutes U+FFFD and carries on,
which is how one bad byte becomes a replacement character in a database and a bug report six months
later.

**JEP 400 (Java 18) made UTF-8 the default charset** for `FileReader`, `FileWriter`,
`InputStreamReader`, `PrintStream`, `Formatter`, `Scanner` and the rest. Before it, the default was
`file.encoding`, which meant **the same program read the same file differently on a German laptop,
a CI container and a Windows machine** — and `Files.readString` was always UTF-8, so a codebase
mixing the two APIs had two charsets in it without saying so.

This is the most consequential silent behaviour change in the 8→26 range. Two things to be able to
say about it:

1. **It can break you on upgrade**, specifically for files genuinely written in a legacy encoding
   by an older version of your own program. `-Dfile.encoding=COMPAT` restores the old behaviour as
   an escape hatch, and `System.out` was deliberately left on the console encoding so that
   terminals kept working.
2. **The fix is to never rely on a default.** `readAllWith` takes a `Charset` because the only
   charset-related code that is correct in every environment is the code that states its charset.

### `strip` is not `trim`

TODO 4, and the test uses `" abc "` — U+2000 EN QUAD:

    " abc ".trim()    ->  " abc "   unchanged, length 5
    " abc ".strip()   ->  "abc"               length 3

`trim()` removes codepoints `<= U+0020`. That was the definition in 1995 and it is frozen for
compatibility. `strip()` (Java 11) uses `Character.isWhitespace`, which is the Unicode definition.
**For ASCII input they are identical, which is why nobody notices** — until a user pastes text out
of Word, or a CSV arrives with a non-breaking space in it, and `trim()` leaves it there and your
`equals` comparison fails on a string that looks right on screen.

Also from Java 11 and 12 in the same family: `isBlank()`, `lines()`, `repeat(n)`, `indent(n)`,
`transform(fn)`. **`lines()` handles `\n`, `\r\n` and bare `\r`**; `split("\n")` handles one of
the three and leaves a `\r` on the end of every line of a file written on Windows. The test pins
that.

### Then fix the old code

Rewrite `RandomFileLines.readLines` with `Files.readAllLines` or `Files.lines`. Do the off-by-one
at line 32 **first**, as its own commit with its own test, exactly as
[doc 09](09-streams-and-optional.md) says — `rline.nextInt(lstLines.size() - 1)` never selects the
last line, and a rewrite that silently fixes it has destroyed the evidence.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: `Files.readString` reads the whole file into memory — at what file size is that the wrong
call, and what do you use instead? `Files.lines` returns a stream holding an open file handle, so
it must be closed — what happens if you return it from a method, and does try-with-resources on a
`Stream` look like something a reviewer would notice? If `strip()` is the correct one, what would
it take to change every `trim()` in a large codebase, and would you?
