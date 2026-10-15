# 23. Foreign function and memory

**Java 22** · stage 12 of [the release-ordered track](INDEX.md) · **needs `--release 22`**, and a
native-access flag to run quietly

## Where it lands

New code: **`ffm/NativeStrlen.java`**, four TODOs. It calls C's `strlen` out of libc, which needs
no compiler, no header, no `.so` to build and no network. The test is
`@EnabledOnOs({LINUX, MAC})` because the default lookup is libc there and `msvcrt` on Windows.

Nothing in this repo uses JNI or `Unsafe`, which is why this is new code. **That absence is worth
a sentence though**: the reason the repo has no JNI is the same reason most codebases have none —
it was expensive enough that people routed around it.

## What it replaced

Two things, and it is worth being precise about both:

**JNI**, which needed a C toolchain, a hand-written header matching your Java signatures, a
shared library per platform, and a build step that broke whenever either side changed. The
signature was checked at **runtime**, by crashing.

**`sun.misc.Unsafe`**, which half the Java ecosystem used for off-heap memory because the
supported alternative was `ByteBuffer` and it was not good enough. `Unsafe` was never public API,
and the JDK could not evolve past it until there was a replacement. Now there is, and
[stage 19](INDEX.md) covers the consequence: `Unsafe`'s memory-access methods were deprecated in
Java 23 (JEP 471), started warning in 24 (JEP 498), and are on a path to removal.

**So this API's real purpose is not "call C conveniently". It is "make `Unsafe` removable."**
That framing is worth more in a conversation than the API surface.

## The exercise

    Linker linker = Linker.nativeLinker();
    MethodHandle strlen = linker.downcallHandle(
            linker.defaultLookup().find("strlen").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

    try (Arena arena = Arena.ofConfined()) {
        MemorySegment s = arena.allocateFrom("hello");
        return (long) strlen.invokeExact(s);
    }

Four things in that block, in order of how much they matter:

1. **`Arena` is the whole safety story.** It is `try`-with-resources over native memory. When the
   block exits the memory is freed, and any `MemorySegment` that escaped the block throws
   `IllegalStateException` on use rather than reading freed memory. A confined arena also pins
   itself to one thread, so a segment used from another thread fails loudly. **This is what
   `Unsafe` could not do**, and it is the entire argument for the API.
2. **`FunctionDescriptor` is unchecked against reality.** `FunctionDescriptor.of(JAVA_LONG,
   ADDRESS)` says "returns `size_t`, takes a pointer". Get it wrong — say `JAVA_INT` for the
   return — and it compiles, does not throw, and gives you a wrong number or a VM crash. There is
   no header file to check you. **Write that down as the cost**: the memory safety is new, the
   signature safety is not.
3. **`invokeExact` is exact.** The cast `(long)` is not decoration; `invokeExact` matches the
   descriptor's types precisely and throws `WrongMethodTypeException` for `(int)`. This is
   `MethodHandle` semantics and it surprises everybody once.
4. **TODO 4 is the detail that makes the boundary real.** `strlen("café")` is **5** and
   `"café".length()` is **4**, because `allocateFrom` encodes UTF-8 and `strlen` counts bytes to
   the NUL. `strlen("€")` is 3 against a `length()` of 1. **Every off-by-one at a native boundary
   is some version of this**, and the test pins both numbers next to each other so the difference
   is unmissable.

## The flag, which is a lesson in itself

Run the test without configuration and it passes, printing:

    WARNING: A restricted method in java.lang.foreign.Linker has been called
    WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
    WARNING: Restricted methods will be blocked in a future release unless native access is enabled

`pom.xml` now passes `--enable-native-access=ALL-UNNAMED` in the Surefire `argLine` so the warning
is gone. **Read the third line again before you add that flag anywhere real.** The JDK is giving
you a release's notice that this becomes an error — the same integrity-by-default move as JEP 403
(strong encapsulation), JEP 486 (security manager) and JEP 500 (final means final), all of which
[stage 19](INDEX.md) collects. The flag is an opt-in to something dangerous, named so that it
appears in a code review and in `ps` output.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: you have swapped a build-time C dependency for a runtime signature you can get wrong
silently — is that a better trade, and for whom? What does `jextract` do, why is it not in the
JDK, and does that change your answer? If an `Arena` makes a segment unusable after close, what
happens to a segment you handed to a native call that kept the pointer? When would you still reach
for JNI?
