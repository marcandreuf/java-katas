# 25. Tooling: jshell, source launch, jwebserver, markdown javadoc

**Java 9 → 23** · stage 14 of [the release-ordered track](INDEX.md) · **no code, no tests** — the
deliverable is that you have run each of these once

## Why this is a stage

Four features that are not language features and not library features, and that between them
remove most of the reasons people used to write a throwaway `Main.java` with a `psvm` in it. Every
one is verified working on the JDK in this VM.

## 1. `jshell` (Java 9, JEP 222)

    jshell
    jshell> "café".length()
    jshell> "café".getBytes(java.nio.charset.StandardCharsets.UTF_8).length

**Use it for every "what does this actually return" question in this track.** Half the assertions
in docs 12–24 were settled by typing the expression somewhere rather than reasoning about it —
`trim()` vs `strip()` on U+2000, whether `Map.of` throws or overwrites, what `filter(x -> x < 5)`
does to `[1,2,3,10,4,5]`. Reasoning about those is slower and less reliable than asking.

Useful inside it: `/vars`, `/methods`, `/imports`, `/open file.jsh`, `/env -class-path target/classes`.
That last one lets you poke at this repo's own classes:

    jshell --class-path target/classes
    jshell> new org.mandfer.practices.Door().toString()

## 2. Source-file launch (Java 11, JEP 330 → Java 22, JEP 458)

    java Hello.java                 # Java 11: one file
    java App.java Helper.java       # Java 22: several files, no javac, no jar

Java 11 let you run a single file. **Java 22 lifted the single-file restriction**, which is what
made it useful for anything beyond a one-liner — before that, the moment your script needed a
second class you had to go back to a build.

No `.class` files are written. It compiles in memory and runs.

## 3. Compact source files (Java 25, JEP 512)

See **[`snippets/Hello.java`](snippets/Hello.java)**, which runs:

    java docs/snippets/Hello.java

    void main() {
        IO.println("Hello from a compact source file.");
    }

No `package`, no `class`, no `static`, no `String[] args`. [Stage 16](27-module-imports-and-compact-sources.md)
owns this as an exercise; it is listed here because it is the end of the same line of work as
`jshell` and source launch — **making the smallest useful Java program small.**

Note where `println` came from: JEP 512 added **`java.lang.IO`**, implicitly available in a compact
source file. That is a new class in `java.lang` in 2025, which tells you how seriously the
"teaching Java should not require explaining `public static void main`" problem was taken.

## 4. `jwebserver` (Java 18, JEP 408)

    jwebserver -p 8000 -d /path/to/dir

A static file server in the JDK. Not for production — it says so itself — and exactly right for
serving a javadoc build, a coverage report, or a directory you want to `curl`.

It is also a **programmable** API (`com.sun.net.httpserver.SimpleFileServer`), which matters here:
`httpclient/LocalHttp.java` already starts a `com.sun.net.httpserver.HttpServer` inside the test
JVM so that [doc 10](10-sequenced-collections-and-httpclient.md) works with no network. JEP 408 is
why that class is a reasonable thing to build on rather than a hack.

## 5. Markdown doc comments (Java 23, JEP 467)

    /// A markdown doc comment.
    ///
    /// - no `<p>` tags
    /// - a [link to String]
    ///
    /// ```java
    /// Doc.answer();
    /// ```

Three slashes instead of `/** */`, and the body is CommonMark. **`{@code ...}` becomes backticks,
`{@link String}` becomes `[String]`, `<ul><li>` becomes `-`, and `<pre>{@code ...}</pre>` becomes
a fenced block.** Verified: `javadoc` renders the above to real `<li>` and a real link.

Worth knowing for one specific reason: **the old form's `<pre>{@code}` for a code sample is
genuinely painful** — HTML entities, indentation, and `@` signs that javadoc tries to interpret.
If you have ever had a `@Override` inside a javadoc code sample silently eat the rest of the
comment, that is what this fixes.

Related: **`@snippet`** (Java 18, JEP 413) lets a doc comment include a region of a real source
file by reference, so the sample is compiled and cannot rot. The two solve different halves of the
same problem.

## Write the answers here

1. **Which of the five did you not know existed?** _(fill in)_
2. **Settle one question from an earlier stage in `jshell` rather than by reading** — pick one you
   got wrong — and write down which, and what you had assumed. _(fill in)_
3. **The `main` question.** `public static void main(String[] args)` was the first thing you ever
   had to not understand. Java 25 removed the need for all four modifiers. _Was that worth a new
   class in `java.lang`?_ Argue the other side too. _(fill in)_
4. **What would you still write a `psvm` for**, now that `java file.java` and `jshell` both exist?
   _(fill in)_
