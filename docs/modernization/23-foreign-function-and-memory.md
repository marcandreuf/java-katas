# 23. Foreign function and memory

**Java 22** (FFM API, JEP 454; preview before that) · stage 12 of
[the release-ordered track](INDEX.md) · needs `--release 22` or newer, and the repo is at 26, so
no build change. Surefire already passes `--enable-native-access=ALL-UNNAMED`; T6 says why.

No safety net needed: this is new code, `ffm/NativeStrlen.java`, and its test,
`NativeStrlenTest`, already exists. It's `@Disabled`. Delete that line to begin. The test is
`@EnabledOnOs({LINUX, MAC})`, because the default lookup finds libc there and not on Windows.

At `--release 21`, `javac` says `Linker is a preview API and is disabled by default` (verified).
From 22 it compiles without flags.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `NativeStrlen`, TODOs 1 to 3](#exercise-1-nativestrlen-todos-1-to-3) | Call C's `strlen` from Java. One commit. | T2–T6 | `NativeStrlenTest` exists: delete `@Disabled`. |
| [2. TODO 4: bytes, not characters](#exercise-2-todo-4-bytes-not-characters) | Explain why `strlen("café")` is 5. In the commit message. | T4 | The test already asserts it. |
| [3. The descriptor nobody checks](#exercise-3-the-descriptor-nobody-checks) | Answer in writing: what test catches a wrong descriptor? | T5 | None. |
| [4. Optional: upcalls](#exercise-4-optional-upcalls) | Sort with C's `qsort` and a Java comparator, in scratch. | T7 | None. |
| [5. What got worse](#exercise-5-fill-in-what-got-worse) | Write down two costs. | all | None. |
| [6. Review questions](#exercise-6-review-questions) | Answer without looking, then check. | all | None. |

## Theory

Read the seven sections in order: each one builds on the one before. Here is the whole story in one
paragraph, so you know where it's going.

> Java has always needed two things it couldn't do safely on its own: call C code, and use memory
> outside the garbage-collected heap. For twenty-five years the answers were JNI, which needed a C
> build, and `sun.misc.Unsafe`, which was never supported and let you read freed memory without a
> word (T1). The FFM API replaces both. You describe a C function and get a Java handle that calls
> it (T2), with one sharp edge from `MethodHandle` (T3). Memory comes in segments owned by arenas,
> and every access is checked: bounds, lifetime and thread (T4). But the description of the C
> function is *your claim*, and nothing checks it (T5). Because a wrong claim can crash the JVM,
> the calls that make one are restricted and need a flag (T6). And the API goes the other way too:
> C can call back into Java (T7).

Every code sample below is a complete program. Save it as the class name plus `.java` and run it
with `java Name.java`; no build needed. Outputs marked *verified* were run on JDK 26, **x86-64
Linux with glibc 2.39**. The C functions used (`abs`, `getpid`) are in libc on Linux and macOS;
on Windows the default lookup won't find them.

Samples that call C print four `WARNING` lines first unless you pass
`--enable-native-access=ALL-UNNAMED`. T6 explains them. The outputs below were run with the flag,
so the warnings are left out.

### T1. The problem: JNI and `Unsafe`

Two jobs Java couldn't do in plain Java:

- **Calling a C library.** The answer was **JNI**: write a Java `native` method, generate a C
  header from it, write C glue code against that header, compile a shared library for every
  platform you ship on, and load it at runtime. The build broke whenever either side changed.
- **Memory outside the heap**, for big buffers the garbage collector shouldn't move or scan.
  `ByteBuffer.allocateDirect` existed but was limited (2 GB, slow to free), so half the ecosystem
  used **`sun.misc.Unsafe`**, an internal class that was never a supported API:

```java
import java.lang.reflect.Field;

public class OldWay {
    public static void main(String[] args) throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) f.get(null);

        long address = unsafe.allocateMemory(8);          // 8 bytes, off the heap, no owner
        unsafe.putLong(address, 42L);
        System.out.println("read back: " + unsafe.getLong(address));
        unsafe.freeMemory(address);
        // Nothing stops this line. It reads freed memory and prints whatever is there:
        // unsafe.getLong(address);
    }
}
```

```text
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called    (verified 2026-11-23)
WARNING: sun.misc.Unsafe::allocateMemory has been called by OldWay (file:...)
WARNING: Please consider reporting this to the maintainers of class OldWay
WARNING: sun.misc.Unsafe::allocateMemory will be removed in a future release
read back: 42
```

(`javac` also prints four `[removal]` warnings while compiling it.) Look at what `Unsafe` gives you:
a raw `long`. It has no size, no owner and no lifetime. Read past the end, or after
`freeMemory`, and you get whatever bytes are there, or a crash.

With `--sun-misc-unsafe-memory-access=deny`, the same program stops at the first call (verified):

```text
Exception in thread "main" java.lang.UnsupportedOperationException: allocateMemory
```

The JDK couldn't remove `Unsafe` until there was a replacement, and FFM is it. **So the real
purpose of this API isn't "call C conveniently". It's "make `Unsafe` removable."**
[Doc 30](30-upgrade-hazards.md) builds on that framing. Nothing in this repo uses JNI or `Unsafe`,
which is why the exercise is new code.

### T2. Calling C: linker, lookup, descriptor, handle

Calling a C function from Java takes four pieces, and each answers one question:

| Piece | Question it answers |
| --- | --- |
| **`Linker.nativeLinker()`** | How does this platform call C? (registers, stack, calling convention) |
| **`linker.defaultLookup()`**, a `SymbolLookup` | Where is the function? `find("abs")` gives its address, as an `Optional`: it may not be there. |
| **`FunctionDescriptor`** | What does the function look like? Its return and parameter types, as layouts: `JAVA_INT`, `JAVA_LONG`, `ADDRESS` (a pointer). |
| **downcall handle**, a `MethodHandle` | The result: a Java object that calls the C function. |

Here are two C functions that need no memory at all, `int abs(int)` and `int getpid(void)`:

```java
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.WrongMethodTypeException;

import static java.lang.foreign.ValueLayout.JAVA_INT;

public class Downcall {
    public static void main(String[] args) throws Throwable {
        Linker linker = Linker.nativeLinker();            // this platform's C calling convention
        SymbolLookup libc = linker.defaultLookup();       // the standard C library

        // int abs(int)  ->  returns JAVA_INT, takes one JAVA_INT
        MethodHandle abs = linker.downcallHandle(
                libc.find("abs").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, JAVA_INT));

        // int getpid(void)  ->  returns JAVA_INT, takes nothing
        MethodHandle getpid = linker.downcallHandle(
                libc.find("getpid").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT));

        int a = (int) abs.invokeExact(-42);               // the (int) cast is part of the call
        System.out.println("abs(-42)           = " + a);
        System.out.println("abs.invoke(-42)    = " + abs.invoke(-42) + " (boxed: invoke adapts)");
        try {
            long wrong = (long) abs.invokeExact(-42);     // same handle, wrong return type at the call site
        } catch (WrongMethodTypeException e) {
            System.out.println("(long) invokeExact : " + e.getMessage());
        }
        System.out.println("find(no_such_fn)   = " + libc.find("no_such_fn"));

        int pid = (int) getpid.invokeExact();
        System.out.println("getpid() == ProcessHandle pid? " + (pid == ProcessHandle.current().pid()));
    }
}
```

```sh
java --enable-native-access=ALL-UNNAMED Downcall.java
```

```text
abs(-42)           = 42                                  (verified 2026-11-23)
abs.invoke(-42)    = 42 (boxed: invoke adapts)
(long) invokeExact : handle's method type (int)int but found (int)long
find(no_such_fn)   = Optional.empty
getpid() == ProcessHandle pid? true
```

No C compiler, no header, no shared library of your own. (An earlier run of the same calls printed
`getpid()` as 125831, the same as `ProcessHandle.current().pid()`; the number is different every
run, so the sample compares instead.)

### T3. The cast that isn't decoration

The `(int)` in `(int) abs.invokeExact(-42)` looks like noise. It isn't. `invokeExact` matches the
*call site's* types against the handle's type **exactly**, and the cast is the only way to write
the return type at the call site. Change it to `(long)` and the call throws
`WrongMethodTypeException: handle's method type (int)int but found (int)long`, even though an
`int` would fit in a `long`.

`invoke` (without `Exact`) adapts the types for you: it boxes, widens and casts. It's a little
slower and hides mistakes, which is why FFM code uses `invokeExact`. This is `MethodHandle`
behaviour, not FFM's, and it catches everyone once.

### T4. Memory: segments and arenas

FFM's answer to `Unsafe`'s raw `long` is a **`MemorySegment`**: a region of memory, on or off the
heap, that knows its **size** and its **lifetime**. The lifetime comes from an **`Arena`**, which
owns every segment allocated from it. Close the arena and all its segments are freed at once.
`Arena.ofConfined()` is the one you'll use most: it's closed with try-with-resources and belongs to
the thread that created it.

Every access to a segment is checked three ways, and this program trips all three:

```java
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static java.lang.foreign.ValueLayout.JAVA_INT;

public class Segments {
    public static void main(String[] args) throws InterruptedException {
        MemorySegment escaped;
        try (Arena arena = Arena.ofConfined()) {          // owns the memory; one thread only
            MemorySegment ints = arena.allocate(JAVA_INT, 3);   // room for three C ints: 12 bytes
            ints.setAtIndex(JAVA_INT, 0, 10);
            ints.setAtIndex(JAVA_INT, 2, 30);
            System.out.println("ints: " + ints.byteSize() + " bytes, [0]=" + ints.getAtIndex(JAVA_INT, 0)
                    + " [1]=" + ints.getAtIndex(JAVA_INT, 1) + " [2]=" + ints.getAtIndex(JAVA_INT, 2));

            try {
                ints.getAtIndex(JAVA_INT, 3);             // one past the end
            } catch (IndexOutOfBoundsException e) {
                System.out.println("past the end:   " + e.getClass().getSimpleName());
            }

            Thread other = new Thread(() -> {
                try {
                    ints.getAtIndex(JAVA_INT, 0);         // a confined arena belongs to its thread
                } catch (WrongThreadException e) {
                    System.out.println("other thread:   " + e.getMessage());
                }
            });
            other.start();
            other.join();

            escaped = ints;                               // keep a reference past the block
        }                                                 // memory freed here

        try {
            escaped.getAtIndex(JAVA_INT, 0);
        } catch (IllegalStateException e) {
            System.out.println("after close:    " + e.getMessage());
        }
    }
}
```

```text
ints: 12 bytes, [0]=10 [1]=0 [2]=30                      (verified 2026-11-23)
past the end:   IndexOutOfBoundsException
other thread:   Attempted access outside owning thread
after close:    Already closed
```

`arena.allocate` zeroes the memory, which is why `[1]` is 0. The other three lines are **the
entire argument for the API**: each one would have been a silent read of foreign, borrowed or
freed memory with `Unsafe`.

Strings work the same way. `arena.allocateFrom("hello")` copies the string into the arena as C
expects it (UTF-8, with a terminating NUL byte), and `segment.getString(0)` reads it back
(verified):

```java
MemorySegment escaped;
try (Arena arena = Arena.ofConfined()) {
    escaped = arena.allocateFrom("hello");
    escaped.byteSize()          // 6: five bytes and the terminating NUL
    escaped.getString(0)        // "hello"
}
escaped.getString(0)            // IllegalStateException: Already closed
```

A segment is also what you pass to a C function that takes a pointer: its parameter is declared
`ADDRESS` in the descriptor. Putting T2 and T4 together like that is exercise 1.

### T5. The descriptor is a claim, and nothing checks it

That gives one rule, and the rest of this doc follows from it:

> **The API checks memory, and it can't check C.**

Every segment is bounds-checked, lifetime-checked and thread-checked. The descriptor, which is your
claim about what the C function looks like, isn't checked against anything, because a compiled C
function carries no type information to check it against. Here is `abs` described correctly, then
described as `long abs(long)`, which it isn't:

```java
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

public class Unchecked {
    public static void main(String[] args) throws Throwable {
        Linker linker = Linker.nativeLinker();
        var abs = linker.defaultLookup().find("abs").orElseThrow();

        // The truth: int abs(int).
        MethodHandle right = linker.downcallHandle(abs, FunctionDescriptor.of(JAVA_INT, JAVA_INT));
        // A lie: long abs(long). Nothing checks it against C.
        MethodHandle wrong = linker.downcallHandle(abs, FunctionDescriptor.of(JAVA_LONG, JAVA_LONG));

        System.out.println("right abs(-42)            = " + (int) right.invokeExact(-42));
        System.out.println("wrong abs(-42L)           = " + (long) wrong.invokeExact(-42L));
        System.out.println("wrong abs(-5000000000L)   = " + (long) wrong.invokeExact(-5_000_000_000L));
        System.out.println("Math.abs(-5000000000L)    = " + Math.abs(-5_000_000_000L));
    }
}
```

```text
right abs(-42)            = 42                           (verified 2026-11-23, x86-64 Linux)
wrong abs(-42L)           = 42
wrong abs(-5000000000L)   = 705032704
Math.abs(-5000000000L)    = 5000000000
```

The wrong descriptor gives **the right answer for -42**. C's `abs` only ever looked at the low 32
bits of the argument, and on small values those are all there is. Only a value that doesn't fit in
an `int` shows the lie, and then the answer is silently wrong, not an exception. (What the upper
bits contain is up to the platform; on another CPU or calling convention it could be different
garbage, or a crash.)

The same holds for the old doc's other checks, all verified on x86-64 Linux:

| Declared | Real C type | Result |
| --- | --- | --- |
| `abs` returning `JAVA_LONG` | `int` | `abs(-42)` is 42, `abs(-1)` is 1 |
| `atoi` returning `JAVA_LONG` | `int` | `atoi("-5")` is -5 |
| `strlen` returning `JAVA_INT` | `size_t` (64-bit) | `strlen("hello")` is 5 |

**Every one is wrong and every one passes a small test.** For `strlen`, the mismatch shows only on a
string of 2 GiB plus 10 bytes (verified, needs about 2 GB of free memory):

```text
strlen declared JAVA_LONG:   2147483658
strlen declared JAVA_INT:   -2147483638
```

**The memory safety is new. The signature safety is exactly what JNI had: none until it runs.**
`jextract`, which generates descriptors from C headers, is the tool that closes that gap. It isn't
part of the JDK (`which jextract` finds nothing here) and needs a download, so it's unverified
here.

### T6. Restricted methods, and the flag

A wrong descriptor can do worse than return garbage: it can crash the JVM. So the methods that
create one, such as `Linker.downcallHandle`, are **restricted methods**. Calling one from code
that hasn't been granted *native access* prints a warning. Run `Downcall` from T2 with no flag
(verified):

```text
WARNING: A restricted method in java.lang.foreign.Linker has been called
WARNING: java.lang.foreign.Linker::downcallHandle has been called by Downcall in an unnamed module (file:...)
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled

abs(-42)           = 42
...
```

The program still works. There are three settings:

| Flag | What a restricted call does |
| --- | --- |
| none | works, after four lines of `WARNING` |
| `--enable-native-access=ALL-UNNAMED` | works, silently: you've granted native access to code on the class path |
| `--illegal-native-access=deny` | throws `IllegalCallerException: Illegal native access from an unnamed module (file:...)` |

(The old version of this doc showed three warning lines; JDK 26 prints four.) `pom.xml` lines
90-94 pass the first flag to Surefire, so `mvn test` is quiet.

**Read the last warning line again before you add that flag anywhere real.** It's a release's
notice that this becomes an error. It's the same "integrity by default" move as strong
encapsulation (JEP 403) and the `Unsafe` warnings in T1, which [doc 30](30-upgrade-hazards.md)
collects. The flag opts in to something that can crash the JVM, and it's named so that it shows
up in a code review and in `ps`.

### T7. The other direction: upcalls

The API also goes the other way. An **upcall stub** is a C function pointer that calls a Java
method. C's `qsort` takes a comparator as a function pointer, and an upcall stub to a Java
comparator sorts `[5, 3, 9, 1, 7]` into `[1, 3, 5, 7, 9]` (verified).

That's what [doc 06, T3](06-virtual-threads.md#t3-pinning-and-what-changed-in-jdk-24) uses to show
what still pins a virtual thread on JDK 26: a Java callback that blocks while a C frame (`qsort`)
is on the stack can't unmount. With one carrier, ten virtual threads each sleeping 100 ms inside a
`qsort` callback took 1043 ms: one after another, with ten `jdk.VirtualThreadPinned` events
recorded by JFR. Doc 06 only describes it; exercise 4 is where to run it, if you want to.

## Practice

### Exercise 1: `NativeStrlen`, TODOs 1 to 3

`NativeStrlen.strlen(String)` should return C's `strlen` of the UTF-8 encoding. The TODOs in the
file walk through it: find the symbol, describe it, allocate the string in a confined arena, call
it. T2 and T4 have every piece you need, but not assembled.

**A correction to the old version of this doc:** it printed the complete method, which was
TODOs 1 to 3 solved. It's gone. Write it from the theory.

**Delete the `@Disabled` and make the four tests green**, in one commit. `strlen`'s C signature is
`size_t strlen(const char *s)`. Decide what layout `size_t` is on a 64-bit platform before you
look it up (T5 says what happens if you guess wrong, and why a test won't tell you).

**Done when:** `NativeStrlenTest` runs and passes, with no `@Disabled`, and `mvn test` prints no
native-access warning.

### Exercise 2: TODO 4: bytes, not characters

**In the commit message**: why is `strlen("café")` 5 and `"café".length()` 4? The segment sizes
give it away (verified):

```text
allocateFrom("café").byteSize()    6      length() 4
allocateFrom("€").byteSize()       4      length() 1
allocateFrom("").byteSize()        1      length() 0
```

Every off-by-one at a native boundary is a version of this: characters on one side, bytes on the
other, and a NUL that one side counts and the other doesn't.

**Done when:** the commit message explains all three rows of that table, including where the
extra byte in each `byteSize()` comes from.

### Exercise 3: the descriptor nobody checks

Answer in writing: what test would catch a `JAVA_INT` descriptor for `strlen` without allocating
2 GB (T5)? If your answer is "none, by calling it", say what you'd do instead.

**Done when:** you have a written answer, and it says either which test or what replaces one.

### Exercise 4: optional: upcalls

In a scratch file, not a commit: sort `[5, 3, 9, 1, 7]` with C's `qsort` and a Java comparator,
through an upcall stub (T7). Then, if you want, reproduce doc 06's pinning number: one carrier
(`-Djdk.virtualThreadScheduler.parallelism=1`), ten virtual threads, each sleeping 100 ms inside
the comparator. Predict the time before you run it, and record it with JFR
(`jfr print --events jdk.VirtualThreadPinned`).

**Done when:** your sort prints `[1, 3, 5, 7, 9]`, and, if you did the second half, your time
matched your prediction.

### Exercise 5: fill in "What got worse"

- _(fill this in)_
- _(fill this in)_

Prompts: you've swapped a build-time C dependency for a runtime signature you can get wrong
silently, and the table above shows tests won't catch it. Is that a better trade, and for whom?
What does `jextract` do, why isn't it in the JDK, and does that change your answer? If closing an
`Arena` makes a segment unusable, what happens to a pointer you passed to a C function that kept
it? When would you still reach for JNI?

### Exercise 6: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. What two things did JNI and `Unsafe` do, and what made each one painful? (T1)
2. What's the real purpose of the FFM API, according to this doc? (T1)
3. Name the four pieces you need to call a C function, and the question each one answers. (T2)
4. Why does `(long) abs.invokeExact(-42)` throw when `abs` returns `int`? What does `invoke` do
   differently? (T3)
5. What three checks does every `MemorySegment` access go through, and what exception does each
   one throw? (T4)
6. What happens to a segment when its arena is closed, and what would `Unsafe` have done in the
   same situation? (T1, T4)
7. Why does `abs` declared as `long abs(long)` return 42 for -42? Which input shows the lie? (T5)
8. "The API checks memory, and it can't check C." What exactly can't it check, and why? (T5)
9. What are the three native-access settings, and what does each do to a restricted call? (T6)
10. What's an upcall stub, and why does a blocking upcall pin a virtual thread on JDK 26? (T7)

**Done when:** you can answer all ten without looking.
