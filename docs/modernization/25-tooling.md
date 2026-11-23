# 25. Tooling: jshell, source launch, jwebserver, markdown javadoc

**Java 9 to 25**: `jshell` (9, JEP 222), source launch (11, JEP 330; several files from 22,
JEP 458), `jwebserver` and `@snippet` (18, JEPs 408 and 413), Markdown doc comments (23,
JEP 467), compact source files (25, JEP 512) · stage 14 of [the release-ordered track](INDEX.md)
· no build change

**No code to commit and no tests.** The deliverable is that you've run each of these once, and
the written answers at the end. Everything works offline with the JDK alone.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line. Nothing here is committed: you run things, and
write answers.

| Exercise | What you do | Theory it uses |
| --- | --- | --- |
| [1. `jshell` on the repo](#exercise-1-jshell-on-the-repos-classes) | Load the repo's classes into `jshell` and settle one old question. | T4 |
| [2. Source launch](#exercise-2-source-launch-and-hellojava) | Run the two-file program both ways, and the repo's compact source file. | T1–T3 |
| [3. `jwebserver`](#exercise-3-jwebserver) | Serve a folder and watch the log. | T5 |
| [4. Doc comments](#exercise-4-break-a-doc-comment-on-purpose) | Write one comment each way, and break one on purpose. | T6, T7 |
| [5. Written answers](#exercise-5-write-the-answers-here) | Four questions. | all |
| [6. What got worse](#exercise-6-fill-in-what-got-worse) | Write down what the tools cost. | all |
| [7. Review questions](#exercise-7-review-questions) | Answer without looking, then check. | all |

## Theory

Read the seven sections in order. Here is the whole story in one paragraph, so you know where it's
going.

> Java used to need a ceremony before it would run anything: a class, `public static void
> main(String[] args)`, `javac`, then `java`. Since Java 9 the JDK has been removing that
> ceremony piece by piece. The launcher runs a `.java` file directly (T1), then a program spread
> over several files (T2), and since 25 a file with no class at all (T3). `jshell` answers "what
> does this return?" one line at a time (T4). `jwebserver` serves a folder with one command (T5).
> And javadoc learned Markdown, which fixes some of the old HTML traps but not all (T6), and
> learned to include code from other files, which doesn't mean that code compiles (T7).

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
  treats *any* line starting with `@` as one, which matters in T6.

That gives one rule, and everything below follows from it:

> **Run it rather than believe it.**

These tools exist so that "what does this return?" costs ten seconds instead of a project. The old
version of this doc made four claims about these tools that running them disproves. Each one is
marked **A correction** below, and each one looked like it worked.

Every sample below was run on JDK 26 in an empty scratch folder. Outputs marked *verified* are
copied from those runs.

### T1. Run a `.java` file directly

**The problem.** To try one idea in Java you used to need two commands and a leftover file:
`javac Greet.java` writes `Greet.class`, then `java Greet` runs it. For a ten-line experiment
that's friction, and the `.class` files pile up.

**Since Java 11** the launcher accepts a source file. It compiles it in memory and runs it:

```java
public class Greet {
    public static void main(String[] args) {
        String who = args.length > 0 ? args[0] : "nobody";
        System.out.println("hello, " + who);
    }
}
```

```sh
$ java Greet.java ana
$ ls
```

```text
hello, ana                                               (verified 2026-11-23)
Greet.java
```

`ana` reached `main` as `args[0]`: everything after the file name is an argument to *your*
program, not to the launcher. And `ls` shows no `Greet.class`. Most samples in this track's other
docs are run this way.

### T2. Several files, one command (Java 22)

**The problem.** Until Java 21, source launch worked for exactly one file. The moment your
experiment grew a helper class in a second file, you were back to `javac`.

Java 22 lifted that. Here are two files in one folder:

```java
// App.java
import java.util.Arrays;

public class App {
    public static void main(String[] args) {
        System.out.println(Helper.greet() + " args=" + Arrays.toString(args));
    }
}
```

```java
// Helper.java
class Helper {
    static String greet() {
        return "from Helper.java";
    }
}
```

The old version of this doc showed it as:

```sh
java App.java Helper.java       # several files, no javac, no jar
```

Run it both ways (verified):

```text
$ java App.java
from Helper.java args=[]

$ java App.java Helper.java
from Helper.java args=[Helper.java]
```

**A correction to the old version:** you name only the file with `main`. The launcher finds
`Helper.java` itself, in the same directory, when `App` refers to `Helper`. Anything after the
first `.java` file is an argument to your program (T1), so the old command passed
`"Helper.java"` to `main`. It ran, and it printed the right greeting, which is why nobody noticed.
`ls` afterwards shows only the two `.java` files, no `.class` (verified).

That's the pattern for this whole doc: **the wrong version looked like it worked.** Only reading
what `args` contained showed the difference.

### T3. No class at all: compact source files and `IO` (Java 25)

**The problem.** `public static void main(String[] args)` is five things a beginner has to type
before they understand any of them. Java 25 made all five optional.

`docs/modernization/snippets/Hello.java` is a compact source file:

```java
void main() {
    IO.println("Hello from a compact source file.");
    IO.println("No class. No static. No args array.");
}
```

```text
$ java docs/modernization/snippets/Hello.java world
Hello from a compact source file.
No class. No static. No args array.
```

(Verified, from the repo root.) No `package`, no `class`, no `static`, no `String[] args`.
[Doc 27](27-module-imports-and-compact-sources.md) owns it as an exercise; its TODOs are in the
file's own comment, so they're not answered here. It's in this doc because it's the end of the
same line of work as T1 and T2: making the smallest useful Java program small. At
`--release 24`, `javac` says `implicitly declared classes are not supported in -source 24`
(verified): it was a preview feature until 25.

**A correction to the old version:** it said `java.lang.IO` was "implicitly available in a compact
source file". It's available in *every* file. `IO` is a class in `java.lang`, and `java.lang` is
imported everywhere. An ordinary class shows it:

```java
public class Plain {
    public static void main(String[] args) {
        IO.println("IO works in an ordinary class too");
    }
}
```

```text
$ java Plain.java                                        (verified 2026-11-23)
IO works in an ordinary class too

$ javac --release 24 -d out Plain.java
Plain.java:3: error: cannot find symbol
        IO.println("IO works in an ordinary class too");
        ^
```

It compiles and runs at `--release 25`, and fails with `cannot find symbol` at `--release 24`.
Ask yourself what it says that Java added a class to `java.lang` in 2025 for this.

### T4. `jshell`: one line at a time

**The problem.** Most questions in this track are small: "does `trim()` remove a no-break space?",
"what does `-7 % 3` give?". Writing a class with a `main` to answer each one is overkill even with
T1. A **REPL** answers them one line at a time.

Started with no arguments, `jshell` is interactive. Each expression's value is printed with a
name (`$1`, `$2`) you can reuse:

```text
jshell> "café".length()
$1 ==> 4
jshell> "café".getBytes(java.nio.charset.StandardCharsets.UTF_8).length
$2 ==> 5
```

The first two are [doc 23](23-foreign-function-and-memory.md)'s TODO 4 in two lines (verified):
four characters, five bytes, because `é` takes two bytes in UTF-8 ([doc 18](18-files-strings-and-charsets.md)
explains why).

`jshell` can also run a script, which is how this doc verified it. One thing to know: **a script
runs quietly.** Expression values aren't printed, only what you print yourself, or what a command
like `/vars` lists. Save this as `try.jsh`:

```text
"café".length()
"café".getBytes(java.nio.charset.StandardCharsets.UTF_8).length
var xs = java.util.List.of(3, 1, 2)
xs.stream().sorted().toList()
System.out.println(Math.floorMod(-7, 3) + " " + (-7 % 3))
/vars
/exit
```

```text
$ jshell try.jsh                                         (verified 2026-11-23)
2 -1
  int $1 = 4
  int $2 = 5
  List<Integer> xs = [3, 1, 2]
  List<Integer> $4 = [1, 2, 3]
```

The first four lines printed nothing as they ran. The `println` did, and `/vars` listed every
value, named. (`$5` is missing because `println` returns nothing.)

Useful commands inside: `/vars`, `/methods`, `/imports`, `/open file.jsh`, and
`/env -class-path target/classes` to add the repo's classes after starting. `jshell
--class-path target/classes` adds them at start; exercise 1 uses it.

### T5. `jwebserver`: a folder on HTTP, one command (Java 18)

**The problem.** A javadoc build, a coverage report or a test page is a folder of HTML. Opening it
with `file://` breaks anything that fetches another file, so you needed a web server, which meant
Python or a Node package.

The JDK has had one since Java 18:

```sh
jwebserver -p 8000 -d /path/to/dir
```

Serving a folder with one file in it, `a.txt`, and sending it four requests with `curl`
(verified 2026-11-23; the run used a random free port, shown here as 8000, and the path and
timestamps are trimmed):

```text
Binding to loopback by default. For all interfaces use "-b 0.0.0.0" or "-b ::".
Serving /.../site and subdirectories on 127.0.0.1 port 8000
URL http://127.0.0.1:8000/
127.0.0.1 - - [...] "GET /a.txt HTTP/1.1" 200 -
127.0.0.1 - - [...] "GET /missing HTTP/1.1" 404 -
127.0.0.1 - - [...] "POST /a.txt HTTP/1.1" 405 -
127.0.0.1 - - [...] "HEAD /a.txt HTTP/1.1" 200 -
```

It logs every request and answers only `GET` and `HEAD`: the `POST` got 405, "method not
allowed". It isn't for production, and it binds to `127.0.0.1` unless told otherwise. That's
exactly right for serving a javadoc build or a coverage report.

It's also a programmable API, `com.sun.net.httpserver.SimpleFileServer`, in the same module as the
`HttpServer` that `httpclient/LocalHttp.java` starts so that
[doc 10](10-sequenced-collections-and-httpclient.md) works with no network. The same server, from
code, on any free port:

```java
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

public class Serve {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("site");
        Files.writeString(dir.resolve("a.txt"), "hello from a file");

        // The same server jwebserver runs, started from code. Port 0: any free port.
        HttpServer server = SimpleFileServer.createFileServer(
                new InetSocketAddress("127.0.0.1", 0), dir, OutputLevel.NONE);
        server.start();
        int port = server.getAddress().getPort();

        try (HttpClient client = HttpClient.newHttpClient()) {
            for (String path : new String[] { "/a.txt", "/missing" }) {
                HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println("GET " + path + " -> " + response.statusCode()
                        + (response.statusCode() == 200 ? " " + response.body() : ""));
            }
        } finally {
            server.stop(0);
            Files.delete(dir.resolve("a.txt"));
            Files.delete(dir);
        }
    }
}
```

```text
GET /a.txt -> 200 hello from a file                      (verified 2026-11-23)
GET /missing -> 404
```

### T6. Markdown doc comments, and what they actually fix (Java 23)

**The problem.** A classic doc comment is HTML inside `/** */`. Lists need `<ul><li>`, code needs
`{@code ...}`, a code sample needs `<pre>{@code ...}</pre>`, and every `<` in a sample is a
chance to break the page. Most people stopped writing anything but one-line comments.

Java 23 added a second form: lines starting `///`, written in Markdown. Here is the same comment
both ways:

```java
/**
 * A price in whole cents, so that {@code 0.1 + 0.2} never happens.
 * <ul>
 *   <li>never negative</li>
 *   <li>compared by value, like any {@link Comparable}</li>
 * </ul>
 * <pre>{@code
 * @Override public String toString() { return "x"; }
 * }</pre>
 *
 * @param cents the amount, in cents
 */
public record Price(long cents) {}
```

````java
/// A price in whole cents, so that `0.1 + 0.2` never happens.
///
/// - never negative
/// - compared by value, like any [Comparable]
///
/// ```java
/// @Override public String toString() { return "x"; }
/// ```
///
/// @param cents the amount, in cents
public record Price(long cents) {}
````

Both pass `javadoc -Xdoclint:all` with no error or warning, and both render a real `<li>`, a real
link to `Comparable`, and a code block with the `@Override` intact (verified 2026-11-23). The
Markdown version renders the sample as `<pre><code class="language-java">`. Backticks replace
`{@code ...}`, `[Comparable]` replaces `{@link Comparable}`, `-` replaces `<ul><li>`, and a fenced
block replaces `<pre>{@code ...}</pre>`.

**A correction to the old version:** it said an `@Override` inside a `<pre>{@code ...}</pre>`
sample "silently eats the rest of the comment". On JDK 26 it doesn't: inside `{@code ...}` the
sample renders whole (verified, and the classic `Price` above shows it). What does break in the
old form, all verified with `javadoc -Xdoclint:all` (and checked again 2026-11-23):

| Old form | What happens |
| --- | --- |
| `@Override` in a bare `<pre>`, without `{@code` | `error: unknown tag`: it's read as a block tag |
| `a < b` in a bare `<pre>` | `error: malformed HTML` |
| `{@code if (x) { return; }` with unbalanced braces | `error: unterminated inline tag` |

**And Markdown doesn't fix the first one.** The block-tag rule from the five words still
applies: any line that *starts* with `@` is read as a tag. Outside a code fence:

```java
/// A price in whole cents.
///
/// @Override is not a tag, but this line starts with one.
///
/// @param cents the amount, in cents
public record Price(long cents) {}
```

```text
$ javadoc -Xdoclint:all -d out Price.java                (verified 2026-11-23)
Price.java:3: error: unknown tag. Unregistered custom tag?
/// @Override is not a tag, but this line starts with one.
    ^
1 error
```

Inside a fence, it's fine.

### T7. `@snippet`: included, not compiled (Java 18)

**The problem.** A code sample written inside a comment is never compiled, so it rots: the method
gets renamed and the sample keeps the old name. `@snippet` (Java 18) lets a doc comment include a
region of a real file instead.

The comment names the file and a region:

```java
/// Usage:
///
/// {@snippet file="Use.java" region="use"}
public class A {}
```

and the region is marked in that file, kept in a `snippet-files` folder next to the source:

```java
// snippet-files/Use.java
class Use {
    void use() {
        // @start region="use"
        new A().methodThatDoesNotExist();
        // @end
    }
}
```

```text
$ javadoc -Xdoclint:all -sourcepath . -d out A.java      (verified 2026-11-23)
A.java:4: warning: use of default constructor, which does not provide a comment
1 warning
```

The one warning is about `A`'s constructor. Nothing complains that `methodThatDoesNotExist`
doesn't exist, and it appears in the HTML.

**A correction to the old version:** it said the sample "is compiled and cannot rot". Javadoc
doesn't compile it. A snippet file calling a method that doesn't exist renders into the HTML with
no error and no warning (verified). It only stays correct if your build compiles that file
somewhere else, for example as test code.

## Practice

### Exercise 1: `jshell` on the repo's classes

```text
jshell --class-path target/classes
jshell> "café".length()
$1 ==> 4
jshell> "café".getBytes(java.nio.charset.StandardCharsets.UTF_8).length
$2 ==> 5
jshell> new org.mandfer.practices.Door().toString()
$3 ==> "close"
```

(All three verified, after `mvn -q compile`.) The third line is the point: `--class-path` puts the
repo's own classes in reach, so you can poke at them without a test.

**Use it for every "what does this actually return?" question in this track.** Pick one claim
from an earlier doc that you got wrong when you guessed, settle it in `jshell`, and write it down
as answer 2 in exercise 5.

**Done when:** you've run the three lines above against the repo, and answer 2 is written.

### Exercise 2: source launch, and `Hello.java`

1. Make the two files from T2 in a scratch folder (not the repo). Run `java App.java` and
   `java App.java Helper.java`, and check that `args` differs. Then `ls`.
2. From the repo root, run `java docs/modernization/snippets/Hello.java world`. Compare its output
   with T3's. Where did `world` go?
3. Compile it at `--release 24` and read the error.

`Hello.java`'s own TODOs belong to [doc 27](27-module-imports-and-compact-sources.md). Leave them
for that doc.

**Done when:** you've seen both `args` lines from T2 yourself, `Hello.java` has run, and you can
say why `--release 24` refuses it.

### Exercise 3: `jwebserver`

Serve a folder you have: the javadoc output from exercise 4, or any folder of HTML. Open it in a
browser, then send one request it refuses (`curl -X POST ...`) and find that line in the log.

**Done when:** you've seen a 200, a 404 and a 405 in the log, and you can say why it binds to
`127.0.0.1` by default.

### Exercise 4: break a doc comment on purpose

In a scratch folder, write one small class with a `///` comment and one with a `/** */` comment.
Run `javadoc -Xdoclint:all -d out` on each and open the HTML (exercise 3 can serve it). Then
reproduce one row of T6's table, and the `@Override` error from T6 in Markdown form.

**Done when:** both comments render, and you've seen at least one of the old-form errors and the
Markdown `unknown tag` error with your own eyes.

### Exercise 5: write the answers here

1. **Which of these didn't you know existed, and which of the corrections above would you have
   believed?** _(fill in)_
2. **Settle one question from an earlier stage in `jshell`**, one you guessed wrong. Write down
   which, what you assumed, and what it returned. _(fill in)_
3. **The `main` question.** `public static void main(String[] args)` was the first thing you ever
   had to not understand. Java 25 removed the need for all four modifiers and the parameter. Was
   that worth a new class in `java.lang`? Argue the other side too. _(fill in)_
4. **What would you still write a `public static void main` for**, now that `java File.java` and
   `jshell` both exist? _(fill in)_

### Exercise 6: fill in "What got worse"

- _(fill this in)_

Prompts: `java App.java Helper.java` ran and looked right. What else in your daily tooling
would you only find out was wrong by reading its arguments? Markdown comments mean a file can mix
`/** */` and `///`: what does that do to a codebase's consistency, and would you migrate old
comments? A source-launched script has no build file. Where do its dependencies go when it needs
one?

### Exercise 7: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.

1. `java Greet.java ana`: what does the launcher do with `Greet.java`, and with `ana`? What's
   left on disk afterwards? (T1)
2. `java App.java Helper.java`, where `App` uses `Helper`. What is `args` in `App.main`, and how
   did the launcher find `Helper`? (T2)
3. Is `IO.println` only available in compact source files? How would you prove it either way?
   (T3)
4. Which release first accepts a compact source file without a preview flag? (T3)
5. You run `jshell script.jsh` and a line in it is `"café".length()`. What gets printed for that
   line, and how would you see its value? (T4)
6. `jwebserver` gets a `POST`. What does it answer, and which interface does it listen on by
   default? (T5)
7. Name the Markdown replacements for `{@code}`, `{@link}`, `<ul><li>` and `<pre>{@code}`. (T6)
8. Does a `///` comment fix the "`@` at the start of a line" problem? Where is an `@` safe? (T6)
9. Does javadoc compile an `@snippet` file? What has to happen for that sample to stay correct?
   (T7)

**Done when:** you can answer all nine without looking.
