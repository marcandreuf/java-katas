# 25. Tooling: jshell, source launch, jwebserver, markdown javadoc

**Java 9 to 25**: `jshell` (9, JEP 222), source launch (11, JEP 330; several files from 22,
JEP 458), `jwebserver` and `@snippet` (18, JEPs 408 and 413), Markdown doc comments (23,
JEP 467), compact source files (25, JEP 512) · stage 14 of [the release-ordered track](INDEX.md)
· no build change

**No code to commit and no tests.** The deliverable is that you've run each of these once, and
the written answers at the end. Everything works offline with the JDK alone.

## What this stage is actually about

Five words, defined once:

- the **launcher** is the `java` command. Given a class name it runs a compiled class. Given a
  file ending in `.java` it compiles that file in memory and runs it, writing no `.class` files.
- a **REPL** (read-eval-print loop) runs one expression or statement at a time and prints the
  result. `jshell` is Java's.
- a **compact source file** (Java 25) is a `.java` file with no class declaration, only methods
  and fields. The compiler wraps them in a class with no name you can refer to.
- a **doc comment** is the comment javadoc turns into HTML. The old form is `/** ... */` with
  HTML inside. The new form is lines starting `///` with Markdown inside.
- a **block tag** is a javadoc tag at the start of a line, like `@param` or `@return`. Javadoc
  treats *any* line starting with `@` as one, which matters below.

That gives one rule, and everything below follows from it: **run it rather than believe it.**
These tools exist so that "what does this return?" costs ten seconds instead of a project. The old
version of this doc made four claims about these tools that running them disproves. Each one is
marked below.

## The worked example: two files, one command

Java 22 lets the launcher run a program that spans several source files. The old version of this
doc showed it as:

    java App.java Helper.java       # several files, no javac, no jar

Try it, with `App.java` calling `Helper.greet()` and printing its arguments (verified):

    $ java App.java
    from Helper.java args=[]

    $ java App.java Helper.java
    from Helper.java args=[Helper.java]

**A correction to the old version:** you name only the file with `main`. The launcher finds
`Helper.java` itself, in the same directory, when `App` refers to `Helper`. Anything after the
first `.java` file is an argument to your program, so the old command passed `"Helper.java"` to
`main`. It ran, and it printed the right greeting, which is why nobody noticed. `ls` afterwards
shows only the two `.java` files, no `.class` (verified).

That's the pattern for this whole stage: the wrong version looked like it worked.

## Part one: `jshell`

    jshell --class-path target/classes
    jshell> "café".length()
    $1 ==> 4
    jshell> "café".getBytes(java.nio.charset.StandardCharsets.UTF_8).length
    $2 ==> 5
    jshell> new org.mandfer.practices.Door().toString()
    $3 ==> "close"

(All three verified, after `mvn -q compile`.) The first two are [doc 23](23-foreign-function-and-memory.md)'s
TODO 4 in two lines. Useful commands inside: `/vars`, `/methods`, `/imports`, `/open file.jsh`,
`/env -class-path target/classes` to add the repo's classes after starting.

**Use it for every "what does this actually return?" question in this track.** Pick one claim
from an earlier doc that you got wrong when you guessed, settle it in `jshell`, and write it down
as answer 2.

## Part two: source launch, and compact source files

The worked example above is the multi-file half. The single-file half goes back to Java 11.

`docs/modernization/snippets/Hello.java` is a compact source file:

    void main() {
        IO.println("Hello from a compact source file.");
        IO.println("No class. No static. No args array.");
    }

    $ java docs/modernization/snippets/Hello.java world
    Hello from a compact source file.
    No class. No static. No args array.

(Verified, from the repo root.) No `package`, no `class`, no `static`, no `String[] args`.
[Doc 27](27-module-imports-and-compact-sources.md) owns it as an exercise. It's here because it's
the end of the same line of work as `jshell` and source launch: making the smallest useful Java
program small. At `--release 24`, `javac` says `implicitly declared classes are not supported in
-source 24` (verified): it was a preview feature until 25.

**A correction to the old version:** it said `java.lang.IO` was "implicitly available in a compact
source file". It's available in *every* file. `IO` is a class in `java.lang`, and `java.lang` is
imported everywhere: an ordinary `public class N` with `IO.println` in its `main` compiles and
runs at `--release 25` (verified). At `--release 24`, the same class fails with `cannot find
symbol` (verified). Ask yourself what it says that Java added a class to `java.lang` in 2025 for
this.

## Part three: `jwebserver`

    jwebserver -p 8000 -d /path/to/dir

A static file server in the JDK. Verified, serving one file:

    Binding to loopback by default. For all interfaces use "-b 0.0.0.0" or "-b ::".
    GET  /a.txt      200
    GET  /missing    404
    POST /a.txt      405

It logs every request and answers only `GET` and `HEAD`. It isn't for production, and it binds to
`127.0.0.1` unless told otherwise. That's exactly right for serving a javadoc build or a coverage
report.

It's also a programmable API, `com.sun.net.httpserver.SimpleFileServer`, in the same module as the
`HttpServer` that `httpclient/LocalHttp.java` starts so that [doc 10](10-sequenced-collections-and-httpclient.md)
works with no network.

## Part four: Markdown doc comments, and what they actually fix

    /// A markdown doc comment.
    ///
    /// - no `<p>` tags
    /// - a [link to String][String]
    ///
    /// ```java
    /// @Override public String toString() { return "x"; }
    /// ```

`javadoc` renders that to a real `<li>`, a real link to `String`, and a code block with the
`@Override` intact (verified). Backticks replace `{@code ...}`, `[String]` replaces
`{@link String}`, `-` replaces `<ul><li>`, and a fenced block replaces `<pre>{@code ...}</pre>`.

**A correction to the old version:** it said an `@Override` inside a `<pre>{@code ...}</pre>`
sample "silently eats the rest of the comment". On JDK 26 it doesn't: inside `{@code ...}` the
sample renders whole (verified). What does break in the old form, all verified with
`javadoc -Xdoclint:all`:

| Old form | What happens |
| --- | --- |
| `@Override` in a bare `<pre>`, without `{@code` | `error: unknown tag`: it's read as a block tag |
| `a < b` in a bare `<pre>` | `error: malformed HTML` |
| `{@code if (x) { return; }` with unbalanced braces | `error: unterminated inline tag` |

And Markdown doesn't fix the first one. A `///` line starting with `@Override` *outside* a code
fence is still `error: unknown tag` (verified). Inside a fence it's fine.

**`@snippet`** (Java 18) includes a region of another file in a doc comment. **A correction to the
old version:** it said the sample "is compiled and cannot rot". Javadoc doesn't compile it. A
snippet file calling a method that doesn't exist renders into the HTML with no error and no
warning (verified). It only stays correct if your build compiles that file somewhere else, for
example as test code.

## Write the answers here

1. **Which of these didn't you know existed, and which of the corrections above would you have
   believed?** _(fill in)_
2. **Settle one question from an earlier stage in `jshell`**, one you guessed wrong. Write down
   which, what you assumed, and what it returned. _(fill in)_
3. **The `main` question.** `public static void main(String[] args)` was the first thing you ever
   had to not understand. Java 25 removed the need for all four modifiers and the parameter. Was
   that worth a new class in `java.lang`? Argue the other side too. _(fill in)_
4. **What would you still write a `public static void main` for**, now that `java File.java` and
   `jshell` both exist? _(fill in)_

## What got worse

- _(fill this in)_

Prompts: `java App.java Helper.java` ran and looked right. What else in your daily tooling
would you only find out was wrong by reading its arguments? Markdown comments mean a file can mix
`/** */` and `///`: what does that do to a codebase's consistency, and would you migrate old
comments? A source-launched script has no build file. Where do its dependencies go when it needs
one?
