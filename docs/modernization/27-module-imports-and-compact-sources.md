# 27. Module imports, compact source files, instance `main`

**Java 25** (module import declarations, JEP 511; compact source files and instance `main`,
JEP 512; both final) · stage 16 of [the release-ordered track](INDEX.md) · needs `--release 25`
or newer

Two pieces of new code, with their tests already written:

- **`modern/ModuleImports.java`**: three TODOs. `ModuleImportsTest` is `@Disabled` until you
  begin.
- **[`snippets/Hello.java`](snippets/Hello.java)**: three TODOs, run directly with
  `java docs/modernization/snippets/Hello.java`. Maven doesn't compile it, and TODO 3 is about
  why.

Do the modules paper exercise in [doc 10](10-sequenced-collections-and-httpclient.md) first.
`import module` only makes sense once you know what a module exports.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `ModuleImports`](#exercise-1-moduleimports) | Three commits: the methods, the ambiguity and its two fixes, the written question. | T1–T4 | `ModuleImportsTest`: delete `@Disabled` to start. |
| [2. `Hello.java`](#exercise-2-hellojava-a-compact-source-file) | TODOs 1 and 2: a field, a second method, `String[] args` and the launcher's order. | T5, T6 | None: you run it. |
| [3. Calling `Hello` from elsewhere](#exercise-3-todo-3-calling-it-from-somewhere-else) | TODO 3: three callers, and which failures are new. | T5 | None: you compile it. |
| [4. What got worse](#exercise-4-fill-in-what-got-worse) | Write down what these features cost. | all | None. |
| [5. Review questions](#exercise-5-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the six sections in order: each one builds on the one before. Here is the whole story in one
paragraph, so you know where it's going.

> Java has always had two ways to import: one type by name, or a whole package with `*` (T1).
> Java 25 adds a third, bigger one: a whole module, which is every package the module exports
> (T2). Bigger imports bring back a problem from 1996: two imports that supply the same simple
> name, so the compiler can't tell which one you mean (T3). The rule that settles it is about
> specificity, and a module import is the least specific kind there is (T4). The same release made
> the smallest Java program small: a file with no class around it (T5), and a `main` that is
> neither `static` nor `public` and needn't take `String[] args` (T6).

Every code sample below is a complete program unless it says otherwise. Save it as the class name
plus `.java` and run it with `java Name.java`; no build needed. Outputs marked *verified* were run
on JDK 26.

### T1. Three kinds of import

**The problem.** A Java file names types by their simple name (`List`, `Path`), but every type
really has a full name (`java.util.List`, `java.nio.file.Path`). Imports are how the compiler gets
from one to the other. Before Java 25 there were two kinds:

- a **single-type import** names one type: `import java.util.List;`.
- an **on-demand import** names a package: `import java.util.*;`. It's been legal since Java 1.0.

A file that uses types from five packages needs five on-demand imports, or many single-type ones.
Java 25 adds a third kind:

- a **module import** names a module: `import module java.base;`. It imports every public
  top-level type in every package the module exports.

One line, and types from five different packages need nothing else:

```java
import module java.base;

public class NoImports {
    public static void main(String[] args) {
        Path file = Path.of("notes", "today.txt");                      // java.nio.file
        Duration meeting = Duration.ofMinutes(90);                       // java.time
        Map<String, Integer> stock = new TreeMap<>(Map.of("fig", 3, "pear", 5));   // java.util
        Function<Integer, Integer> twice = n -> n * 2;                   // java.util.function
        AtomicInteger hits = new AtomicInteger();                        // java.util.concurrent.atomic
        hits.incrementAndGet();

        System.out.println(file + " " + meeting + " " + stock + " " + twice.apply(21) + " " + hits);
    }
}
```

```text
notes/today.txt PT1H30M {fig=3, pear=5} 42 1             (verified 2026-11-23)
```

### T2. What a module import actually brings in

A module import is exactly as big as the module's list of exports, and that list is in the module
descriptor, so a program can ask for it. [Doc 10](10-sequenced-collections-and-httpclient.md)'s
modules exercise is the background here.

```java
public class Exports {
    public static void main(String[] args) {
        Module base = ModuleLayer.boot().findModule("java.base").orElseThrow();
        long open = base.getDescriptor().exports().stream().filter(e -> !e.isQualified()).count();
        long qualified = base.getDescriptor().exports().stream().filter(e -> e.isQualified()).count();
        System.out.println("java.base exports " + open + " packages to everyone, "
                + qualified + " more only to named friends");

        Module sql = ModuleLayer.boot().findModule("java.sql").orElseThrow();
        sql.getDescriptor().requires().stream()
                .filter(r -> r.modifiers().contains(java.lang.module.ModuleDescriptor.Requires.Modifier.TRANSITIVE))
                .map(r -> r.name()).sorted()
                .forEach(n -> System.out.println("java.sql requires transitive " + n));
    }
}
```

```text
java.base exports 58 packages to everyone, 58 more only to named friends   (verified 2026-11-23)
java.sql requires transitive java.logging
java.sql requires transitive java.transaction.xa
java.sql requires transitive java.xml
```

Two rules come out of that:

- **Only unrestricted exports count.** `import module java.base;` brings in the 58 packages
  exported to everyone. A *qualified* export (`exports x to some.module`) is for the named
  module only, and your code isn't it.
- **`requires transitive` is followed; plain `requires` isn't.** A module import also brings in
  the exports of every module the named one requires *transitively*. So `import module java.sql;`
  gives you `java.util.logging.Logger` as well as `java.sql.Types`. But every module reads
  `java.base` without `transitive`, so it does **not** give you `List`:

```java
import module java.sql;

public class Transitive {
    public static void main(String[] args) {
        Logger log = Logger.getLogger("demo");          // java.util.logging, via java.sql's requires transitive
        System.out.println(log.getName() + " " + Types.VARCHAR);   // java.sql.Types
    }
}
```

```text
demo 12                                                  (verified 2026-11-23)
```

```java
import module java.sql;

public class TransitiveNotBase {
    public static void main(String[] args) {
        List<String> xs = new ArrayList<>();
        System.out.println(xs);
    }
}
```

```text
$ javac -d out TransitiveNotBase.java                    (verified 2026-11-23)
TransitiveNotBase.java:5: error: cannot find symbol
        List<String> xs = new ArrayList<>();
        ^
  symbol:   class List
  location: class TransitiveNotBase
...
2 errors
```

(`java.lang` is the one package every file gets for free, whatever it imports. That's where
`String`, `System` and, since Java 25, `IO` come from: T5.)

### T3. The ambiguity is from 1996

The more names an import supplies, the more likely two imports supply the same one. This has been
a compile error since Java 1.0:

```java
import java.util.*;
import java.awt.*;
public class M { List<String> l = new ArrayList<>(); }
```

```text
$ javac -d out M.java                                    (verified 2026-11-23)
M.java:3: error: reference to List is ambiguous
public class M { List<String> l = new ArrayList<>(); }
                 ^
  both class java.awt.List in java.awt and interface java.util.List in java.util match
1 error
```

`java.awt` has a class called `List`, from before the collections framework existed, so two
on-demand imports supply the same name and neither is more specific than the other. Every Java
style guide that bans `import x.*` cites some version of this.

Module imports bring the same problem at a larger scale: one line supplies hundreds of names
instead of dozens. And the `java.desktop` module exports `java.awt`, so it's the same `List`:

```java
import module java.base;
import module java.desktop;

public class Clash {
    public static void main(String[] args) {
        List<String> names = new ArrayList<>();
        System.out.println(names);
    }
}
```

```text
$ javac -d out Clash.java                                (verified 2026-11-23)
Clash.java:6: error: reference to List is ambiguous
        List<String> names = new ArrayList<>();
        ^
  both class java.awt.List in java.awt and interface java.util.List in java.util match
1 error
```

Same message, same two candidates. But note *where* the error is: on the line that says `List`,
not on the imports. Two imports that supply the same name are fine until you use the name:

```java
import module java.base;
import module java.desktop;

public class Unused {
    public static void main(String[] args) {
        // Both modules supply the name List. This file never says List, so nothing is ambiguous.
        Map<String, Integer> sizes = new TreeMap<>();
        sizes.put("frame", new Dimension(640, 480).width);              // java.awt, via java.desktop
        System.out.println(sizes);
    }
}
```

```text
{frame=640}                                              (verified 2026-11-23)
```

So adding a module import can break a file that compiled yesterday, and only at the lines that
happen to use a clashing name.

### T4. The rule: more specific wins, and a module import is the least specific

That gives one rule, and the rest of the import half of this doc follows from it:

> **When two imports supply the same simple name, the more specific import wins, and a module
> import is the least specific kind there is.**

A module import never overrides anything you wrote by hand. It only fills in names nothing else
supplied, and if two module imports supply the same name, it fills in neither. That's T3's error.

The most specific thing of all is a type you declare yourself. `java.base` supplies a `Duration`
(from `java.time`), and this file declares its own:

```java
import module java.base;

public class Ours {
    // A type declared in this file. java.base also supplies a Duration, through java.time.
    record Duration(int minutes) { }

    public static void main(String[] args) {
        Duration d = new Duration(90);                  // which Duration?
        System.out.println(d + " is " + d.getClass().getName());
    }
}
```

```text
Duration[minutes=90] is Ours$Duration                    (verified 2026-11-23)
```

No error and no warning: the file's own `Duration` simply wins, and the module import quietly
supplies nothing for that name. Where the other kinds of import sit in that order, and what that
means for fixing T3's error, is exercise 1.

### T5. Compact source files: a program without a class

[Doc 25, T3](25-tooling.md#t3-no-class-at-all-compact-source-files-and-io-java-25) introduced
compact source files from the launcher's side, using `snippets/Hello.java`, and showed that
`IO` lives in `java.lang` and so works in *every* file. This section is the language's side:
what the compiler does with a file that has no class.

- a **compact source file** is a `.java` file with methods and fields at the top level and no
  class declaration. The compiler wraps them in an **implicitly declared class**, named after the
  file.

Four separate things were removed, and each one has a rule:

1. **The class declaration.** The class still exists, named after the file. It's `final`, and it
   isn't `public`.
2. **The package.** A compact source file can't have one. It's always in the unnamed package.
3. **The imports.** It gets `import module java.base;` for free (T1).
4. **`System.out`.** `IO.println` replaces it, but only because `IO` is in `java.lang` (doc 25,
   T3). What you *don't* get is `println` on its own.

Here are rules 1 and 3 in one file. It uses `List`, `ArrayList` and `Collections` with no import
line, and asks the class its own name:

```java
void main() {
    List<Integer> scores = new ArrayList<>(List.of(7, 3, 9, 1));   // no import line anywhere
    Collections.sort(scores);
    IO.println("sorted: " + scores);
    IO.println("this class is called " + getClass().getName());
}
```

Save it as `Tally.java`:

```text
$ java Tally.java                                        (verified 2026-11-23)
sorted: [1, 3, 7, 9]
this class is called Tally

$ javac -d o Tally.java && javap -p o/Tally.class
Compiled from "Tally.java"
final class Tally {
  Tally();
  void main();
}
```

And rules 2 and 4, as the compiler states them:

```java
package x;
void main() {
    IO.println("x");
}
```

```text
$ javac -d o Pkg.java                                    (verified 2026-11-23)
Pkg.java:1: error: compact source file should not have package declaration
package x;
^
1 error
```

```java
void main() {
    println("x");
}
```

```text
$ javac -d o Bare.java                                   (verified 2026-11-23)
Bare.java:2: error: cannot find symbol
    println("x");
    ^
  symbol:   method println(String)
  location: class Bare
1 error
```

No file of either kind gets `IO`'s methods imported statically. At `--release 24`, `Tally.java`
gives `implicitly declared classes are not supported in -source 24` (verified): it was a preview
feature until 25.

### T6. Instance `main`: what the launcher calls

- an **instance `main`** is `void main()`: not `static`, not `public`, and with no `String[]`
  parameter if you don't want one. The launcher creates an instance and calls it.

That's not only for compact files. An ordinary named class can have one too:

```java
public class Counter {
    void main() {                                         // an ordinary class, an instance main
        IO.println("the launcher made a " + getClass().getSimpleName() + " and called main() on it");
    }
}
```

```text
$ java Counter.java                                      (verified 2026-11-23)
the launcher made a Counter and called main() on it
```

Because the launcher made an instance, `main` can call the class's other instance methods and
read its fields directly, with no `static` anywhere. To do that, the class needs a constructor
that takes no arguments and isn't private. Which `main` the launcher picks when a class has more
than one candidate is exercise 2.

## Practice

### Exercise 1: `ModuleImports`

`ModuleImports.java` has exactly one import, `import module java.base;`, and its javadoc lists
the types that line supplies. Delete the `@Disabled`, and all four tests error with
`UnsupportedOperationException` (verified).

Commits, in this order:

1. **TODO 1**, the three methods, adding no import lines. The tests say what each one must do.
   `longestFirst` is the same comparator shape as [doc 12](12-lambdas-and-method-references.md)
   and [doc 14](14-map-defaults-and-collectors.md).
2. **TODO 2**: add `import module java.desktop;` and recompile. You get the same error as T3,
   `reference to List is ambiguous`, with the same two candidates (verified). Fix it without
   deleting either module import. **There are two different one-line fixes that work**
   (verified). Find both, and use T4's rule to explain why each one wins. Then write down what
   this means for a file that needs two large modules: how many single-type imports does it end
   up with anyway? Put the answer in the commit message, then decide whether `java.desktop`
   stays.
3. **TODO 3**: the written question. An IDE shows you where a type came from, but a diff in a
   browser doesn't. That's the same objection you were asked about for `var` in
   [doc 05](05-var.md). If you accepted `var` and reject `import module`, or the other way round,
   say what makes them different. "One hides a type, the other hides where it came from" is a
   start.

Where `import module` really is a saving: a file that uses a lot of `java.util` and
`java.util.function`, scripts and teaching code, and a project's **own** modules, which is the
case the JEP is aimed at. This repo can't show that last one, because it has no
`module-info.java`.

**Done when:** `ModuleImportsTest` passes with no import line added for TODO 1, both TODO 2 fixes
are found and explained by T4's rule in the commit message, and TODO 3 is answered in writing.

### Exercise 2: `Hello.java`, a compact source file

[`snippets/Hello.java`](snippets/Hello.java) is, apart from its comments:

```java
void main() {
    IO.println("Hello from a compact source file.");
    IO.println("No class. No static. No args array.");
}
```

```text
$ java docs/modernization/snippets/Hello.java
Hello from a compact source file.
No class. No static. No args array.
```

(Verified on 26 and on 25.) The four removals in T5 apply to it, verified on this file:

1. Compiling it with `javac` produces `Hello.class`, and `javap` shows
   `final class Hello { Hello(); void main(); }` (verified).
2. `package x;` gives `compact source file should not have package declaration` (verified).
3. `List`, `ArrayList` and `Collections` work with no import line (verified).
4. **A correction to the old version:** it said `IO` is "implicitly available here", as if it
   were special to compact files. It's in `java.lang`, like `String`, so every Java file can use
   it. `IO.println` works in an ordinary class too (verified). What you *don't* get is `println`
   on its own: `void main() { println("x"); }` is `cannot find symbol` (verified).

A compact file can still declare more than one type: `record Pt(int x, int y) {}` at the top
level of the same file compiles and runs (verified). It becomes a member of the implicit class.

**TODO 1** (a field and a second method) and **TODO 2** (adding `String[] args` back) are in the
file. For TODO 2, give the file both `void main()` and `void main(String[] args)`, and find out
which one the launcher calls. Then make one of them `static` and check whether that changes the
answer. Record the order you find.

**Done when:** `Hello.java` has a field and a second method and still runs, it reads an argument
from `String[] args`, and you've written down the launcher's order for the signatures you tried.

### Exercise 3: TODO 3, calling it from somewhere else

**A correction to the old version:** it said nothing outside the file can call `Hello`'s `main`,
"even by reflection-free means". That isn't quite what happens. Try three callers, each with
`new Hello().main()`:

1. a class in `src/main/java`, which is always in a named package
2. a class in the unnamed package, compiled **in the same `javac` command** as `Hello.java`
3. a class in the unnamed package, compiled **against the `Hello.class`** produced by compiling
   `Hello.java` first

They don't all behave the same way (verified). Find out which work, and work out which of the
failures is new in Java 25 and which has always applied to every class in the unnamed package.
Then write down what that means for shipping a compact source file in anything bigger
than one file.

This is the end of the line that started with `jshell` (9) and launching a single source file
(11, and multi-file in 22). See [stage 14](25-tooling.md).

**Done when:** you've tried all three callers, recorded which compile, and written down which
failure is new in Java 25 and what that means for shipping a compact source file.

### Exercise 4: fill in "What got worse"

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: is `import module java.base` better or worse than `import java.util.*`, which style
guides ban for the reason in T3? If your answer differs, why? Does an implicitly declared class
help a learner, or does it just postpone four questions to day three? `java.lang.IO` is now in
every Java file's namespace: what would you have had to believe to approve that? If a compact
source file can't sensibly be called from anything else, what stops it becoming a dialect?

### Exercise 5: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. Name the three kinds of import, from most specific to least. (T1, T4)
2. How many packages does `import module java.base;` bring in, and why not the qualified
   exports too? (T2)
3. Does `import module java.sql;` give you `java.util.logging.Logger`? Does it give you
   `java.util.List`? Why the difference? (T2)
4. Why is `List` ambiguous with `import java.util.*; import java.awt.*;`, and what does
   `java.desktop` have to do with it? (T3)
5. A file has `import module java.base; import module java.desktop;` and never uses `List`. Does
   it compile? (T3)
6. A file declares its own `record Duration` and has `import module java.base;`. Which
   `Duration` does it get, and is there a warning? (T4)
7. What is the class in a compact source file called, and what are its two modifiers or missing
   modifiers? (T5)
8. Can a compact source file have a `package` declaration? (T5)
9. `IO.println` works in a compact file. Does it work in an ordinary class? Does `println` on its
   own work in either? (T5, doc 25 T3)
10. Can an ordinary named class have an instance `void main()`? What does the launcher do to run
    it? (T6)

**Done when:** you can answer all ten without looking.
