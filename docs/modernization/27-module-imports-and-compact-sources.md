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

## What this stage is actually about

Five words, defined once:

- a **single-type import** names one type: `import java.util.List;`.
- an **on-demand import** names a package: `import java.util.*;`. It's been legal since Java 1.0.
- a **module import** names a module: `import module java.base;`. It imports every public
  top-level type in every package the module exports. `java.base` exports 58 packages without
  restriction (verified, by asking `ModuleLayer.boot()`).
- a **compact source file** is a `.java` file with methods and fields at the top level and no
  class declaration. The compiler wraps them in an **implicitly declared class**, named after the
  file.
- an **instance `main`** is `void main()`: not `static`, not `public`, and with no `String[]`
  parameter if you don't want one. The launcher creates an instance and calls it.

That gives one rule, and everything below follows from it: **when two imports supply the same
simple name, the more specific import wins, and a module import is the least specific kind
there is.** A module import never overrides anything you wrote by hand. It only fills in names
nothing else supplied, and if two module imports supply the same name, it fills in neither.

## The worked example: the ambiguity is from 1996

This has been a compile error since Java 1.0 (verified on JDK 26):

    import java.util.*;
    import java.awt.*;
    public class M { List<String> l = new ArrayList<>(); }

    error: reference to List is ambiguous
      both class java.awt.List in java.awt and interface java.util.List in java.util match

`java.awt` has a class called `List`, from before the collections framework existed, so two
on-demand imports supply the same name and neither is more specific than the other. Every Java
style guide that bans `import x.*` cites some version of this.

Module imports bring the same problem at a larger scale: one line supplies hundreds of names
instead of dozens. And the `java.desktop` module exports `java.awt`, so it's the same `List`.

## Part one: `ModuleImports`

`ModuleImports.java` has exactly one import, `import module java.base;`, and its javadoc lists
the types that line supplies. Delete the `@Disabled`, and all four tests error with
`UnsupportedOperationException` (verified).

Commits, in this order:

1. **TODO 1**, the three methods, adding no import lines. The tests say what each one must do.
   `longestFirst` is the same comparator shape as [doc 12](12-lambdas-and-method-references.md)
   and [doc 14](14-map-defaults-and-collectors.md).
2. **TODO 2**: add `import module java.desktop;` and recompile. You get the same error as the
   worked example, `reference to List is ambiguous`, with the same two candidates (verified). Fix
   it without deleting either module import. **There are two different one-line fixes that
   work** (verified). Find both, and use the rule above to explain why each one wins. Then write
   down what this means for a file that needs two large modules: how many single-type imports
   does it end up with anyway? Put the answer in the commit message, then decide whether
   `java.desktop` stays.
3. **TODO 3**: the written question. An IDE shows you where a type came from, but a diff in a
   browser doesn't. That's the same objection you were asked about for `var` in
   [doc 05](05-var.md). If you accepted `var` and reject `import module`, or the other way round,
   say what makes them different. "One hides a type, the other hides where it came from" is a
   start.

Where `import module` really is a saving: a file that uses a lot of `java.util` and
`java.util.function`, scripts and teaching code, and a project's **own** modules, which is the
case the JEP is aimed at. This repo can't show that last one, because it has no
`module-info.java`.

## Part two: `Hello.java`, a compact source file

[`snippets/Hello.java`](snippets/Hello.java) is, apart from its comments:

    void main() {
        IO.println("Hello from a compact source file.");
        IO.println("No class. No static. No args array.");
    }

    $ java docs/modernization/snippets/Hello.java
    Hello from a compact source file.
    No class. No static. No args array.

(Verified on 26 and on 25.) Four separate things were removed, and each one has a rule:

1. **The class declaration.** Compiling it with `javac` produces `Hello.class`, and `javap` shows
   `final class Hello { Hello(); void main(); }` (verified). So the class does have a name, taken
   from the file. It's `final`, and it isn't `public`.
2. **The package.** A compact source file can't have one: `package x;` gives `compact source file
   should not have package declaration` (verified). It's always in the unnamed package.
3. **The imports.** It gets `import module java.base;` for free. `List`, `ArrayList` and
   `Collections` work with no import line (verified).
4. **`System.out`.** `IO` is a new class in `java.lang`. **A correction to the old version:** it
   said `IO` is "implicitly available here", as if it were special to compact files. It's in
   `java.lang`, like `String`, so every Java file can use it. `IO.println` works in an ordinary
   class too (verified). What you *don't* get is `println` on its own: `void main() { println("x"); }`
   is `cannot find symbol` (verified). No file of either kind gets `IO`'s methods imported
   statically.

A compact file can still declare more than one type: `record Pt(int x, int y) {}` at the top
level of the same file compiles and runs (verified). It becomes a member of the implicit class.

**TODO 1** (a field and a second method) and **TODO 2** (adding `String[] args` back) are in the
file. For TODO 2, give the file both `void main()` and `void main(String[] args)`, and find out
which one the launcher calls. Then make one of them `static` and check whether that changes the
answer. Record the order you find.

### TODO 3: calling it from somewhere else

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

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: is `import module java.base` better or worse than `import java.util.*`, which style
guides ban for the reason in the worked example? If your answer differs, why? Does an implicitly
declared class help a learner, or does it just postpone four questions to day three? `java.lang.IO`
is now in every Java file's namespace: what would you have had to believe to approve that? If a
compact source file can't sensibly be called from anything else, what stops it becoming a
dialect?
