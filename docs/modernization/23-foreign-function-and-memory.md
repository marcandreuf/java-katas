# 23. Foreign function and memory

**Java 22** (FFM API, JEP 454; preview before that) · stage 12 of
[the release-ordered track](INDEX.md) · needs `--release 22` or newer, and the repo is at 26, so
no build change. Surefire already passes `--enable-native-access=ALL-UNNAMED`; the last part says
why.

No safety net needed: this is new code, `ffm/NativeStrlen.java`, and its test,
`NativeStrlenTest`, already exists. It's `@Disabled`. Delete that line to begin. The test is
`@EnabledOnOs({LINUX, MAC})`, because the default lookup finds libc there and not on Windows.

At `--release 21`, `javac` says `Linker is a preview API and is disabled by default` (verified).
From 22 it compiles without flags.

## What this stage is actually about

Five words, defined once:

- the **`Linker`** connects Java to the platform's C calling convention.
  `Linker.nativeLinker().defaultLookup()` finds symbols in the standard C library.
- a **downcall handle** is a `MethodHandle` that calls a C function. You build it from the
  symbol's address and a **`FunctionDescriptor`**, which states the C function's return and
  parameter types as layouts (`JAVA_INT`, `JAVA_LONG`, `ADDRESS`).
- a **`MemorySegment`** is a bounded region of memory, on or off the Java heap, with a size and a
  lifetime. Reading past its end throws instead of reading whatever is there.
- an **`Arena`** owns the lifetime of the segments allocated from it. `Arena.ofConfined()` is
  closed with try-with-resources and belongs to one thread.
- a **restricted method** is one that can crash the JVM if misused (`downcallHandle` is one).
  Calling it prints a warning unless native access is enabled for that code.

That gives one rule, and everything below follows from it: **the API checks memory, and it can't
check C.** Every segment is bounds-checked, lifetime-checked and thread-checked. The descriptor,
which is your claim about what the C function looks like, isn't checked against anything, because
there's nothing to check it against.

## The worked example

Not `strlen`, which is the exercise: `abs` and `getpid`, which need no memory at all (verified,
x86-64 Linux):

    Linker linker = Linker.nativeLinker();
    SymbolLookup libc = linker.defaultLookup();

    MethodHandle abs = linker.downcallHandle(
            libc.find("abs").orElseThrow(),
            FunctionDescriptor.of(JAVA_INT, JAVA_INT));     // int abs(int)

    (int) abs.invokeExact(-42)                  // 42
    (long) abs.invokeExact(-42)                 // WrongMethodTypeException:
                                                //   handle's method type (int)int but found (int)long
    abs.invoke(-42)                             // 42, boxed as an Integer: invoke adapts, invokeExact doesn't
    libc.find("no_such_fn")                     // Optional.empty

    // int getpid(void)
    (int) getpid.invokeExact()                  // 125831, the same as ProcessHandle.current().pid()

**The `(int)` cast isn't decoration.** `invokeExact` matches the call site's types against the
handle's exactly, and the cast is how you write the return type at the call site. This is
`MethodHandle` behaviour, not FFM's, and it catches everyone once.

Now the memory half, with an arena (verified):

    MemorySegment escaped;
    try (Arena arena = Arena.ofConfined()) {
        escaped = arena.allocateFrom("hello");
        escaped.byteSize()          // 6: five bytes and the terminating NUL
        escaped.getString(0)        // "hello"
    }
    escaped.getString(0)            // IllegalStateException: Already closed

    // inside an open confined arena, from another thread:
    //                                 WrongThreadException: Attempted access outside owning thread
    // reading an int at offset 4 of a 4-byte segment:
    //                                 IndexOutOfBoundsException

**That's the entire argument for the API.** Each of those three would have been a silent read of
freed or foreign memory with `sun.misc.Unsafe`.

## What it replaced

**JNI** needed a C compiler, a generated header, a shared library per platform, and a build step
that broke whenever either side changed. **`sun.misc.Unsafe`** was what half the ecosystem used for
off-heap memory, because `ByteBuffer` wasn't good enough, and it was never a supported API.

The JDK couldn't remove `Unsafe` until there was a replacement, and this is it. On JDK 26, calling
`Unsafe.allocateMemory` prints `WARNING: A terminally deprecated method in sun.misc.Unsafe has
been called`, and `--sun-misc-unsafe-memory-access=deny` turns it into
`UnsupportedOperationException: allocateMemory` (both verified). **So the real purpose of this
API isn't "call C conveniently". It's "make `Unsafe` removable."**
[Doc 30](30-upgrade-hazards.md) builds on that framing.

Nothing in this repo uses JNI or `Unsafe`, which is why the exercise is new code.

## Part one: `NativeStrlen`, TODOs 1 to 4

`NativeStrlen.strlen(String)` should return C's `strlen` of the UTF-8 encoding. The TODOs in the
file walk through it: find the symbol, describe it, allocate the string in a confined arena, call
it. The worked example has every piece you need, but not assembled.

**A correction to the old version of this doc:** it printed the complete method, which was
TODOs 1 to 3 solved. It's gone. Write it from the worked example.

Commits, in this order:

1. **Delete the `@Disabled` and make the four tests green.** `strlen`'s C signature is
   `size_t strlen(const char *s)`. Decide what layout `size_t` is on a 64-bit platform before you
   look it up.
2. **TODO 4, in the commit message**: why is `strlen("café")` 5 and `"café".length()` 4? The
   segment sizes give it away (verified):

        allocateFrom("café").byteSize()    6      length() 4
        allocateFrom("€").byteSize()       4      length() 1
        allocateFrom("").byteSize()        1      length() 0

   Every off-by-one at a native boundary is a version of this: characters on one side, bytes on
   the other, and a NUL that one side counts and the other doesn't.

## Part two: the descriptor isn't checked, and testing won't tell you

The old version said that a wrong descriptor "gives you a wrong number or a VM crash". On this
machine, it usually gives the *right* number, which is worse. Verified on x86-64 Linux:

| Declared | Real C type | Result |
| --- | --- | --- |
| `abs` returning `JAVA_LONG` | `int` | `abs(-42)` is 42, `abs(-1)` is 1 |
| `atoi` returning `JAVA_LONG` | `int` | `atoi("-5")` is -5 |
| `strlen` returning `JAVA_INT` | `size_t` (64-bit) | `strlen("hello")` is 5 |

Every one is wrong and every one passes a small test. The mismatch shows up only when the value
doesn't fit. A string of 2 GiB plus 10 bytes (verified, needs about 2 GB of free memory):

    strlen declared JAVA_LONG:   2147483658
    strlen declared JAVA_INT:   -2147483638

**Write this down as the cost.** The memory safety is new. The signature safety is exactly what
JNI had: none until it runs. `jextract`, which generates descriptors from C headers, is the tool
that closes that gap. It isn't part of the JDK (`which jextract` finds nothing here) and needs a
download, so it's unverified here.

Then answer in writing: what test would catch a `JAVA_INT` descriptor for `strlen` without
allocating 2 GB? If your answer is "none, by calling it", say what you'd do instead.

## Part three: the flag, which is a lesson in itself

Run `strlen` outside Maven with no flag and it works, after printing four lines (verified):

    WARNING: A restricted method in java.lang.foreign.Linker has been called
    WARNING: java.lang.foreign.Linker::downcallHandle has been called by ... in an unnamed module (...)
    WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
    WARNING: Restricted methods will be blocked in a future release unless native access is enabled

(The old version showed three of them.) With `--enable-native-access=ALL-UNNAMED` they're gone;
with `--illegal-native-access=deny` the call throws `IllegalCallerException: Illegal native access
from an unnamed module` (both verified). `pom.xml` lines 90-94 pass the first flag to Surefire,
so `mvn test` is quiet.

**Read the last warning line again before you add that flag anywhere real.** It's a release's
notice that this becomes an error. It's the same "integrity by default" move as strong
encapsulation (JEP 403) and the `Unsafe` warnings above, which [doc 30](30-upgrade-hazards.md)
collects. The flag opts in to something that can crash the JVM, and it's named so that it shows
up in a code review and in `ps`.

## And one experiment, if you want it: upcalls

The API also goes the other way: an **upcall stub** is a C function pointer that calls a Java
method. C's `qsort` takes a comparator pointer, and an upcall stub to a Java comparator sorts
`[5, 3, 9, 1, 7]` into `[1, 3, 5, 7, 9]` (verified). [Doc 06](06-virtual-threads.md) uses exactly
this to show what still pins a virtual thread on JDK 26: a Java callback blocking while a C frame
is on the stack. Do that experiment now, if you skipped it there.

## What got worse

- _(fill this in)_
- _(fill this in)_

Prompts: you've swapped a build-time C dependency for a runtime signature you can get wrong
silently, and the table above shows tests won't catch it. Is that a better trade, and for whom?
What does `jextract` do, why isn't it in the JDK, and does that change your answer? If closing an
`Arena` makes a segment unusable, what happens to a pointer you passed to a C function that kept
it? When would you still reach for JNI?
