# 27. Module imports, compact source files, instance `main`

**Java 25** · stage 16 of [the release-ordered track](INDEX.md) · **needs `--release 25`**

## Where it lands

Two pieces:

- **`modern/ModuleImports.java`**, three TODOs, compiled and tested
- **[`snippets/Hello.java`](snippets/Hello.java)**, three TODOs, run directly with
  `java docs/snippets/Hello.java` — **not compiled by Maven**, and TODO 3 is about why

Both are new code. This stage also **reframes the JPMS paper exercise** in
[doc 10](10-sequenced-collections-and-httpclient.md): that one asks you to add a
`module-info.java` and meet the split-package problem. Do it first if you have not, because
`import module` only makes sense once you know what a module exports.

## Module import declarations (JEP 511)

    import module java.base;

One line, and every public top-level type from every package `java.base` exports is in scope,
transitively. `ModuleImports.java` has no other import and uses `List`, `ArrayList`, `Map`,
`LinkedHashMap`, `Comparator`, `String` and `Collectors`.

**TODO 2 is the exercise, and it is a compile error you should go and cause:**

    import module java.base;
    import module java.desktop;    // add this

`List` is now **ambiguous**. `java.base` exports `java.util.List` and `java.desktop` exports
`java.awt.List`, and a module import has no way to prefer one. The fix is to add a single-type
import back — `import java.util.List;` — which **wins over both module imports**, because a
single-type import is more specific.

That resolution rule is the thing to learn, and it has a corollary worth stating: **`import module`
does not remove your imports, it removes the boring ones.** A file that needs two large modules
ends up with a module import plus a handful of disambiguating single-type imports, which is
arguably worse than the explicit list it replaced.

The genuine wins are narrower than the feature sounds:

- **`import module java.base`** in a file that uses a lot of `java.util` — a real saving
- **teaching and scripts**, where `import java.util.*` was already the pragmatic answer
- **`import module`** for your *own* modules, which is the case the JEP is really aimed at and
  which this repo cannot demonstrate because it has no `module-info.java`

**TODO 3 asks the question that matters.** An IDE tells you where a type came from; a diff in a
browser does not. That is the *same objection* you were asked to write down about `var` in
[doc 05](05-var.md). Decide whether your answer is consistent. If you accepted `var` and reject
`import module`, say what distinguishes them — there is a good answer available, and "one hides a
type and the other hides an origin" is the start of it.

## Compact source files and instance `main` (JEP 512)

[`snippets/Hello.java`](snippets/Hello.java), in full:

    void main() {
        IO.println("Hello from a compact source file.");
    }

No `package`, no `class`, no `static`, no `String[] args`, no `System.out`. Run it:

    java docs/snippets/Hello.java

Four separate things were removed, and it is worth separating them:

1. **The class declaration.** The file declares an *unnamed* top-level class. Fields and methods
   go at the top level and become its members.
2. **`static`.** `main` is an instance method; the launcher constructs the class and calls it.
3. **`String[] args`.** `void main()` is accepted. `void main(String[] args)` still is, and the
   launcher prefers the one with arguments if both exist — TODO 2 makes you check the order.
4. **`System.out.println`.** JEP 512 added **`java.lang.IO`**, implicitly available here. A new
   class in `java.lang` in 2025, purely so that a first program does not have to explain a static
   field on a class called `System`.

### TODO 3, which is the honest part

Try to call `Hello`'s `main` from a class in `src/main/java`. **You cannot.** The unnamed class
has no name you can write, so nothing outside the file can refer to it, and it is in the default
package so nothing in a named package can reach it even by reflection-free means.

**That is why this file is in `docs/snippets/` and not in the source tree.** Compact source files
are for programs that are one file and stay one file. The moment you need a second class you want
to call, you promote the file to a normal class — which is exactly the transition JEP 512 says it
is designed to make gradual, and it is worth having felt where the cliff is.

This is also the end of the line that began with `jshell` (9) and source launch (11, 22); see
[stage 14](25-tooling.md).

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: is `import module java.base` better or worse than `import java.util.*`, which has been
legal since 1996 and which style guides ban — and if your answer differs, why? Does an unnamed
class help a learner, or defer four questions they will hit on day three? `java.lang.IO` is now
permanently in every Java program's namespace — what would you have had to believe to approve
that? If compact source files cannot be called from anywhere, what stops them becoming a dialect?
