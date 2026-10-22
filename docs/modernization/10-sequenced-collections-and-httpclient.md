# 10. Sequenced collections, and HttpClient

**Java 21** (sequenced collections, JEP 431) · **Java 11** (`HttpClient`, JEP 321) · **Java 9**
(modules, paper only) · concept stage 10 of [the concept order](00-start-here.md) · no build
change

Two unrelated topics, both shallow on purpose, plus two paper exercises. Every target here
already has a test, but none of those tests covers the bugs below, so part one starts by adding
the tests that do.

## What this stage is actually about

Five words, defined once:

- **encounter order** is the order a collection hands back its elements: insertion order for a
  `List` or `LinkedHashSet`, sorted order for a `TreeSet`, *no defined order* for a `HashSet`.
- a **sequenced collection** (`SequencedCollection`, Java 21) is one with a defined encounter
  order, and it now has the methods that order implies: `getFirst()`, `getLast()`,
  `addFirst()`, `addLast()`, `removeFirst()`, `removeLast()`, `reversed()`. `List`, `Deque`,
  `LinkedHashSet` and `SortedSet` all are one; `HashSet` isn't. `SequencedMap` is the same idea
  for maps (`LinkedHashMap`, `TreeMap`).
- a **view** is a collection backed by another one: `reversed()` doesn't copy, so later changes to
  the original show through it.
- an **`HttpClient`** sends an **`HttpRequest`** and gets back an **`HttpResponse<T>`**, where
  `T` is decided by the **body handler** you pass (`BodyHandlers.ofString()` gives a `String`).
- a **module** (`module-info.java`) is a named set of packages that says which other modules it
  `requires` and which of its packages it `exports`.

The rule for the first half: **a sequenced collection names an order the collection already had.
It doesn't create one.** That explains everything below: why `HashSet` isn't sequenced, why a
`TreeSet` can't support `addFirst`, and why replacing a hand-written sorted list means first
working out what order it was promising.

## The worked example

The JDK's answers to "a sorted collection of ints, first and last at hand" (verified):

    var set = new TreeSet<>(List.of(5, 10, 20, 10, 1));
    set                  // [1, 5, 10, 20]       duplicates gone: it's a set
    set.getFirst()       // 1
    set.getLast()        // 20
    set.reversed()       // [20, 10, 5, 1]
    set.addFirst(0)      // UnsupportedOperationException

`addFirst` throws because the order belongs to the comparator, not to you. Putting 0 "first" in a
sorted set isn't an operation that means anything. It is on the interface, and it throws, and
that's the rule above showing through.

    var list = new ArrayList<>(List.of(5, 10, 20, 10, 1));
    Collections.sort(list);
    list                 // [1, 5, 10, 10, 20]   duplicates kept
    list.getFirst()      // 1    (before Java 21: list.get(0))
    list.getLast()       // 20   (before Java 21: list.get(list.size() - 1))

    var rev = list.reversed();
    list.add(99);
    rev                  // [99, 20, 10, 10, 5, 1]   a view, not a copy

And the two ways to ask an empty list for its first element now fail differently:
`new ArrayList<>().getFirst()` throws `NoSuchElementException`, while `get(0)` throws
`IndexOutOfBoundsException`. The new method says what's actually wrong: there's no first
element, which isn't the same thing as a bad index.

## Part one: `CircularList`

`CircularList.java` is a hand-written **sorted circular linked list**: `insert(n)` puts `n` in
order and returns the head, and the last node points back to the first. `CircularListTest` has
five tests, all green. They insert in ascending order, or into the middle. **None of them inserts
a value smaller than the head of a list that has two or more elements, and none inserts a
duplicate.** Both are broken (verified):

    new CircularList().insert(1).insert(2).insert(3).insert(4)    // 1,2,3,4,     correct
    new CircularList().insert(5).insert(10).insert(20).insert(1)  // 5,10,20,1,   1 at the end
    new CircularList().insert(5).insert(10).insert(1)             // 5,10,1,
    new CircularList().insert(5).insert(10).insert(20).insert(10) // 5,10,20,10,  dup at the end

Read `insert` (lines 19-47) and the reason is in the loop. It walks forward looking for a gap where
`current < n < next`, and if it never finds one, it calls `insertAtEnd`. A value smaller than
everything never fits a gap. Neither does a value *equal* to one already there, because both
comparisons are strict. Only the one-element case (lines 25-30) knows how to insert before the
head.

And one more, which is an API problem rather than a bug: `insert` **returns** the head, and the
receiver may not be it. `CircularList e = new CircularList(); e.insert(7);` leaves `e` empty
(verified): the new node was returned and dropped. The empty list is a sentinel object that
`insert` never changes.

Commits, in this order:

1. **Characterize.** Add three tests: smaller-than-head, duplicate, and ignored return value.
   Assert today's output, `"5,10,20,1,"` and `"5,10,20,10,"`, and that `e` stays empty. Green, and
   recording bugs.
2. **Decide what it should do**, then fix it. The smaller-than-head case must become the new head,
   which means `insert` must return it. For the duplicate, choose: keep it (a sorted list) or drop
   it (a sorted set). **That choice is the same one as `ArrayList` vs `TreeSet` above.** The
   assertions from step 1 flip in this commit.
3. **`toString()`**, lines 99-110. `StringBuffer` is the synchronized version of
   `StringBuilder`, from Java 1.0. Every `append` takes a lock that no other thread will ever
   contend for. Switch to `StringBuilder`, then ask whether a `StringBuilder` is needed at all:
   collecting the values and calling `String.join(",", ...)` says what it does. **Note what the
   current output has: a trailing comma, `"1,2,3,4,"`, and every test that prints it depends on
   that.** `String.join` doesn't produce one. Keeping or dropping it is a behaviour change, so it
   gets its own commit and its own assertion.
4. **Then the comparison, in writing.** What does `CircularList` give you that a sorted `ArrayList`
   or a `TreeSet` doesn't? The circularity: from the last element, `_next` is the first. Is
   anything using that? (Nothing in the repo is: its only caller is its test.) Write down how much
   of the 112 lines was carrying its own weight.

## Part two: `MyArrayList`, finish it and then compare

`algorithm/MyArrayList.java` is 28 lines and **unfinished**. Its javadoc asks for `add()`, `get()`,
`remove()` and `size()`, growing "when it reaches threshold". It has `add()` and `size()`, and
(verified):

    new MyArrayList().size()       // 10, though it holds nothing: it returns values.length
    // the 11th add():               ArrayIndexOutOfBoundsException: Index 10 out of bounds for length 10

`MyArrayListTest` has two tests, both `@Disabled` and both naming this doc.

1. **Delete the first `@Disabled`, then fix `size()`.** It should return `lastIndex`. One line.
2. **Write the rest test-first, against the javadoc**: `get(int)`, `remove(int)`, and growth.
   Delete the second `@Disabled`, and add a test that adds 11 elements. The one design question
   is how much to grow by. `ArrayList` grows by half its current size: read `ArrayList.grow` in
   the JDK source and say why it isn't 1, and why it isn't 2×.
3. **Compare it with `ArrayList`**: nothing in the repo calls `MyArrayList`, so there's no caller
   to switch over. The deliverable is the comparison. `ArrayList` since Java 21 also has
   `getFirst`, `getLast`, `addFirst`, `removeLast` and `reversed`. Which of those would you have
   written for `MyArrayList`, and which would you have got wrong? (`addFirst` on an array-backed
   list is the expensive one: it shifts every element.)

## Part three: `HttpClient`, on `httpclient/LocalHttp.java`

Nothing in the old code makes an HTTP call, so this is new code. **It runs entirely inside the
JVM**, against a `com.sun.net.httpserver` instance the test starts on `127.0.0.1` and a random
port, so it works with no network. Delete the `@Disabled` in `LocalHttpTest` first.

The shape is already in the file: `requestFor(uri)`, `stringBody()` and `client()` at lines 47-59
are the three pieces. TODO 1 is putting them together, and checking the result against the
test's `"echo:/ping"`.

Before TODO 2, four facts, all verified on JDK 26:

- **A 404 doesn't throw.** `send` returns normally with `statusCode() == 404`. `HttpClient` only
  throws when there's no HTTP response at all: `ConnectException` when nothing is listening,
  `HttpTimeoutException` when the timeout expires. So "handle a non-200 without throwing" means
  **check `statusCode()` yourself**. If you don't, a 404 page's body comes back as though it were
  the answer.
- **The echo server always answers 200**, so to test the non-200 path, add a context in your test:
  `server.createContext("/missing", x -> { x.sendResponseHeaders(404, -1); x.close(); });`
- **A timeout is per request**: `HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(300))`.
  Against a handler that sleeps for two seconds, `send` throws
  `HttpTimeoutException: request timed out`.
- **`HttpClient` is `AutoCloseable` since Java 21**, so `try (var client = HttpClient.newHttpClient())`
  shuts down its threads. A client is meant to be shared, though, so in real code you create one and
  keep it. Decide which one this class should do.

TODO 3 is `sendAsync`, which returns a `CompletableFuture<HttpResponse<T>>`. The write-up it asks
for (which one you'd use for a remote call, and why) is worth doing after
[doc 06](06-virtual-threads.md): with virtual threads, blocking `send` no longer costs a platform
thread, which removes the main argument for `sendAsync`. [Doc 15](15-completable-future.md) covers
the async side, and [doc 29](29-http3-and-the-httpclient.md) builds on this file, so finish
these TODOs first.

## Part four: paper exercises, no code committed

### Modules

Add a `module-info.java` to `src/main/java`, make the build work, and write down what happened.
Then delete it again. Here's what to expect, verified on a scratch copy of the repo:

    module org.mandfer.practices {
        requires java.net.http;     // HttpClient
        requires java.desktop;      // java.awt.image, javax.imageio, for ForkBlur
        requires jdk.httpserver;    // com.sun.net.httpserver, for LocalHttp
    }

With an empty module declaration, `src/main` fails with `package ... is not visible` for exactly
those three. With the three `requires`, **`mvn test` passes: 248 tests, the same result as without
it.** The old version of this doc said you would meet split packages and automatic modules
"straight away". In this repo, you don't, and the reasons are the finding:

- `src/main` has **no third-party dependencies**, only JDK modules.
- The tests live in the same packages as the code, which would be a split package, but Maven's
  compiler and Surefire plugins quietly patch them into the module. The build writes
  `target/test-classes/META-INF/jpms.args` containing
  `--patch-module org.mandfer.practices=_` and `--add-reads org.mandfer.practices=ALL-UNNAMED`.
  Open it.
- Of the test dependencies, `hamcrest-2.2.jar` has no module descriptor
  (`jar --describe-module` says `Derived automatic module`), and JUnit and commons-lang3 have real
  ones. It works anyway: Maven puts hamcrest on the class path, in the unnamed module, and the
  `--add-reads ... ALL-UNNAMED` line above is what lets the patched tests see it.

So write down **what you'd expect in a real codebase** that this one doesn't have: two jars
exporting the same package (a hard error on the module path), and an automatic module whose name
was derived from its file name and changes when the jar is renamed. Then compare with
[doc 27](27-module-imports-and-compact-sources.md), which reframes modules for Java 25.

### Garbage collectors

One paragraph: which collector your JDK picks, what G1 is optimising for, and when ZGC would be
the right call. Check it on your own machine:

    java -XX:+PrintFlagsFinal -version | grep -E "Use(G1|Serial|Parallel|Z)GC "

Verified here, on a 10-core, 23 GB machine: **G1**. Run it again with `-XX:ActiveProcessorCount=1`
and the JVM picks **Serial** instead: the default depends on the machine, which is why a
container with one CPU can behave differently from your laptop. [Doc 31](31-runtime-defaults.md)
has the rest of the runtime defaults.

## What got worse

- _(fill this in)_

Prompts: `reversed()` is a view. When is that a feature, and when is it a bug waiting for someone
who expected a copy? `getFirst()` on a `List` is shorter than `get(0)`. Is it clearer, or just
newer? `HttpClient` doesn't throw on a 500: where does your status-code check live, and what
happens the day someone forgets it?
