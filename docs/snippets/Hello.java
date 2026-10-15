/// A compact source file: no `package`, no `class`, no `String[] args`, no `static`.
///
/// This file is NOT compiled by Maven, deliberately -- it has no package declaration and no
/// class name of its own, so it does not belong in `src/main/java`. Run it directly:
///
/// ```
/// java docs/snippets/Hello.java
/// java docs/snippets/Hello.java world
/// ```
///
/// Three features at once, all final in Java 25 (JEP 512):
///   - compact source file: the implicit class is unnamed and takes its name from the file
///   - instance main method: `void main()`, not `public static void main(String[])`
///   - the implicit `java.lang.IO` import, which is where `println` comes from here
///
/// TODO 1: add a field and a second method, and confirm both work without a class declaration.
/// TODO 2: add `String[] args` back to main() and read an argument. Note which signatures the
///         launcher will accept, and in what priority order.
/// TODO 3: try to call this from a normal class in src/main/java. Work out why you cannot, and
///         write down what that means for using compact source files in anything shipped.
///
/// See docs/27-module-imports-and-compact-sources.md.
void main() {
    IO.println("Hello from a compact source file.");
    IO.println("No class. No static. No args array.");
}
