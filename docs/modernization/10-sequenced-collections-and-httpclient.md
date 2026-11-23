# 10. Sequenced collections, and HttpClient

**Java 21** (sequenced collections, JEP 431) · **Java 11** (`HttpClient`, JEP 321) · **Java 9**
(modules, paper only) · concept stage 10 of [the concept order](00-start-here.md) · no build
change

Two unrelated topics, both shallow on purpose, plus two paper exercises. Every target here
already has a test, but none of those tests covers the bugs below, so exercise 1 starts by adding
the tests that do.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `CircularList`](#exercise-1-circularlist) | Characterize two insert bugs and an API trap, fix them, rework `toString()`, compare in writing. Four commits. | T1, T2 | Characterize first: three tests that record today's bugs. |
| [2. `MyArrayList`](#exercise-2-myarraylist-finish-it-and-then-compare) | Fix `size()`, write `get`, `remove` and growth test-first, compare with `ArrayList`. | T2, T3 | Two `@Disabled` tests to switch on, then your own. |
| [3. `LocalHttp`](#exercise-3-httpclient-on-httpclientlocalhttpjava) | New code: TODOs 1 to 3 in `httpclient/LocalHttp.java`. | T5, T6 | `LocalHttpTest` is `@Disabled`; delete that first. |
| [4. Modules](#exercise-4-modules-paper-no-code-committed) | Paper: add a `module-info.java`, make it build, write down what happened, delete it. | T7 | `mvn test` before and after. |
| [5. Garbage collectors](#exercise-5-garbage-collectors-paper) | Paper: one paragraph on your JDK's default collector. | T8 | None. |
| [6. What got worse](#exercise-6-fill-in-what-got-worse) | Write down what these APIs cost. | all | None. |
| [7. Review questions](#exercise-7-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the sections in order; T1 to T4 are one story, T5 and T6 another, and T7 and T8 are short
notes for the paper exercises. Here is the whole of it in one paragraph, so you know where it's
going.

> Some collections hand back their elements in a defined order and some don't (T1). Until Java
> 21, the ones that did had no common way to say "first" and "last": a list used `get(0)`, a
> deque `peekFirst()`, a `LinkedHashSet` nothing at all. `SequencedCollection` gives them one set
> of methods, and the rule is that it **names an order the collection already had** (T2).
> `reversed()` is a view, not a copy, which is either a feature or a bug depending on who expected
> what (T3). Maps get the same treatment (T4). The second story is `HttpClient`: three objects, a
> client, a request and a response (T5), and the one surprise that matters, which is that an
> error status is *not* an exception (T6). Modules (T7) and the garbage collector your JVM picks
> (T8) are background for two paper exercises.

Every code sample below is a complete program unless it says otherwise. Save it as the class name
plus `.java` and run it with `java Name.java`; no build needed. Outputs marked *verified* were run
on JDK 26. None of them touches the network: the `HttpClient` samples start their own server
inside the same JVM.

### T1. Encounter order: which collections have one

The **encounter order** of a collection is the order it hands back its elements when you iterate
over it. Some collections promise one, and some don't:

- a `List` or a `LinkedHashSet`: **insertion order**, the order you added them;
- a `TreeSet`: **sorted order**, decided by its comparator;
- a `HashSet`: **no defined order**. Whatever it prints is an accident of hashing, and can change
  when the set grows.

```java
import java.util.*;

public class Orders {
    public static void main(String[] args) {
        List<String> input = List.of("pear", "apple", "fig");

        Collection<String> list   = new ArrayList<>(input);      // order you added them
        Collection<String> linked = new LinkedHashSet<>(input);  // order you added them
        Collection<String> sorted = new TreeSet<>(input);        // order the comparator says
        Collection<String> hashed = new HashSet<>(input);        // no promised order

        for (Collection<String> c : List.of(list, linked, sorted, hashed)) {
            System.out.printf("%-14s %-20s sequenced=%s%n",
                    c.getClass().getSimpleName(), c, c instanceof SequencedCollection);
        }
    }
}
```

```text
ArrayList      [pear, apple, fig]   sequenced=true       (verified 2026-11-23)
LinkedHashSet  [pear, apple, fig]   sequenced=true
TreeSet        [apple, fig, pear]   sequenced=true
HashSet        [apple, pear, fig]   sequenced=false
```

The `HashSet` line can come out in a different order on another JDK; that's the point. The last
column is the next section's subject: the three collections that promise an order are now all a
`SequencedCollection`, and the one that doesn't, isn't.

### T2. Sequenced collections: one name for "first" and "last"

**The problem.** Before Java 21, collections that had an order had no common way to talk about
it. Asking for the first element was `list.get(0)` on a list, `deque.peekFirst()` on a deque,
`set.first()` on a sorted set, and `set.iterator().next()` on a `LinkedHashSet`. Asking a
`LinkedHashSet` for its *last* element meant walking the whole set. And no method could accept
"any collection with an order", because there was no type for it.

**The fix.** A **sequenced collection** (`SequencedCollection`, Java 21, JEP 431) is a collection
with a defined encounter order, and it now has the methods that order implies: `getFirst()`,
`getLast()`, `addFirst()`, `addLast()`, `removeFirst()`, `removeLast()`, `reversed()`. `List`,
`Deque`, `LinkedHashSet` and `SortedSet` all are one; `HashSet` isn't.

That gives one rule, and everything below follows from it:

> **A sequenced collection names an order the collection already had. It doesn't create one.**

That's why `HashSet` isn't sequenced: it has no order to name. It's why a `TreeSet` can't support
`addFirst`: its order belongs to the comparator, not to you. And it's why replacing a hand-written
sorted list (exercise 1) means first working out what order it was promising.

```java
import java.util.*;

public class Firsts {
    public static void main(String[] args) {
        // Before Java 21: three collections, three ways to ask for the first element.
        List<Integer> list = new ArrayList<>(List.of(5, 10, 20, 10, 1));
        Collections.sort(list);
        Deque<Integer> deque = new ArrayDeque<>(list);
        LinkedHashSet<Integer> linked = new LinkedHashSet<>(list);

        System.out.println("old: " + list.get(0) + " " + deque.peekFirst() + " " + linked.iterator().next());
        // ...and the last one: list.get(list.size() - 1), deque.peekLast(), and for the
        // LinkedHashSet, walk the whole set.

        // Java 21: one method, on one interface they all share.
        for (SequencedCollection<Integer> c : List.<SequencedCollection<Integer>>of(list, deque, linked)) {
            System.out.println("new: " + c.getFirst() + ".." + c.getLast() + "  " + c.getClass().getSimpleName());
        }

        // The sorted set has the methods too, but the order belongs to its comparator.
        TreeSet<Integer> set = new TreeSet<>(List.of(5, 10, 20, 10, 1));
        System.out.println("set=" + set + " first=" + set.getFirst() + " last=" + set.getLast()
                + " reversed=" + set.reversed());
        try {
            set.addFirst(0);
        } catch (UnsupportedOperationException e) {
            System.out.println("set.addFirst(0): " + e.getClass().getSimpleName());
        }

        // Empty: the new method says what's actually wrong.
        List<Integer> empty = new ArrayList<>();
        try { empty.getFirst(); } catch (RuntimeException e) { System.out.println("getFirst(): " + e.getClass().getSimpleName()); }
        try { empty.get(0); } catch (RuntimeException e) { System.out.println("get(0):     " + e.getClass().getSimpleName()); }
    }
}
```

```text
old: 1 1 1                                               (verified 2026-11-23)
new: 1..20  ArrayList
new: 1..20  ArrayDeque
new: 1..20  LinkedHashSet
set=[1, 5, 10, 20] first=1 last=20 reversed=[20, 10, 5, 1]
set.addFirst(0): UnsupportedOperationException
getFirst(): NoSuchElementException
get(0):     IndexOutOfBoundsException
```

Read the output by the rule:

- **The list kept both 10s; the set dropped one.** `new TreeSet<>(List.of(5, 10, 20, 10, 1))` is
  `[1, 5, 10, 20]`, while the sorted list is `[1, 5, 10, 10, 20]`. Both are sorted. One is a set.
  That's the choice exercise 1 makes you face for `CircularList`.
- **`addFirst` is on the `TreeSet`, and it throws.** Putting 0 "first" in a sorted set isn't an
  operation that means anything. The method is on the interface, and it throws, and that's the
  rule showing through.
- **The two ways to ask an empty list for its first element fail differently.** `getFirst()`
  throws `NoSuchElementException`, while `get(0)` throws `IndexOutOfBoundsException`. The new
  method says what's actually wrong: there's no first element, which isn't the same thing as a
  bad index.

### T3. `reversed()` is a view, not a copy

A **view** is a collection backed by another one. It holds no elements of its own: every read
goes through to the original, and so does every write. `reversed()` returns a view, which makes it
free to call on a list of a million elements, and means later changes show through it.

```java
import java.util.*;

public class Views {
    public static void main(String[] args) {
        List<String> list = new ArrayList<>(List.of("a", "b", "c"));
        List<String> rev = list.reversed();           // a view: no copy is made

        list.add("d");                                // change the original...
        System.out.println("rev after list.add(d):  " + rev);

        rev.add("z");                                 // ...or change the view
        System.out.println("list after rev.add(z):  " + list);

        List<String> copy = new ArrayList<>(list.reversed());   // want a copy? make one
        list.clear();
        System.out.println("rev after clear: " + rev + "  copy: " + copy);
    }
}
```

```text
rev after list.add(d):  [d, c, b, a]                     (verified 2026-11-23)
list after rev.add(z):  [z, a, b, c, d]
rev after clear: []  copy: [d, c, b, a, z]
```

Adding to the *end* of the reversed view put `z` at the *front* of the original: the view is the
same list, read backwards. If you want a snapshot, wrap it in `new ArrayList<>(...)` and say so.

### T4. Sequenced maps

`SequencedMap` is the same idea for maps with an order: `LinkedHashMap` (insertion order) and
`TreeMap` (sorted by key). Before Java 21, getting the first entry of a `LinkedHashMap` meant
`map.entrySet().iterator().next()`, and the last meant walking every entry.

```java
import java.util.*;

public class Maps {
    public static void main(String[] args) {
        SequencedMap<String, Integer> stock = new LinkedHashMap<>();
        stock.put("apples", 3);
        stock.put("pears", 5);
        stock.put("figs", 2);

        System.out.println("map:          " + stock);
        System.out.println("firstEntry:   " + stock.firstEntry());
        System.out.println("lastEntry:    " + stock.lastEntry());

        stock.putFirst("plums", 7);                   // insert at the front (or move there)
        System.out.println("after putFirst: " + stock);

        System.out.println("pollLastEntry: " + stock.pollLastEntry() + "  now " + stock);
        System.out.println("keys reversed: " + stock.sequencedKeySet().reversed());

        SequencedMap<String, Integer> sorted = new TreeMap<>(stock);
        System.out.println("TreeMap first: " + sorted.firstEntry());
        try {
            sorted.putFirst("aaa", 1);
        } catch (UnsupportedOperationException e) {
            System.out.println("TreeMap.putFirst: " + e.getClass().getSimpleName());
        }
    }
}
```

```text
map:          {apples=3, pears=5, figs=2}                (verified 2026-11-23)
firstEntry:   apples=3
lastEntry:    figs=2
after putFirst: {plums=7, apples=3, pears=5, figs=2}
pollLastEntry: figs=2  now {plums=7, apples=3, pears=5}
keys reversed: [pears, apples, plums]
TreeMap first: apples=3
TreeMap.putFirst: UnsupportedOperationException
```

Same rule as T2: the `LinkedHashMap`'s order is yours, so `putFirst` works. The `TreeMap`'s order
belongs to its keys, so `putFirst` throws.

### T5. `HttpClient`: a client, a request, a response

**The problem.** Before Java 11, the JDK's only HTTP API was `HttpURLConnection` (Java 1.1): no
HTTP/2, a mutable object you configured with setters and then read from streams, and error
statuses reported by throwing from `getInputStream()`. Most projects added Apache HttpClient or
OkHttp instead. `java.net.http.HttpClient` (Java 11, JEP 321) is the replacement in the JDK.

It's three objects, each with one job:

- an **`HttpClient`** sends requests. It holds the connection pool and the threads, so it's meant
  to be created once and shared.
- an **`HttpRequest`** describes one request: the URI, the method, headers, a timeout. It's
  immutable, built with `HttpRequest.newBuilder(uri)`.
- an **`HttpResponse<T>`** is what comes back: `statusCode()`, `headers()`, and `body()`. The `T`
  is decided by the **body handler** you pass to `send`: `BodyHandlers.ofString()` gives a
  `String`, `ofByteArray()` a `byte[]`, `discarding()` throws the body away.

This program starts its own server on `127.0.0.1` and a free port (port `0` means "pick one"), so
it runs with no network. It asks for one path that exists and one that doesn't:

```java
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class Status {
    public static void main(String[] args) throws Exception {
        // A server inside this JVM, on a free port: no network needed.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hello", exchange -> {
            byte[] body = "hi there".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/gone", exchange -> {
            byte[] body = "<h1>Not here</h1>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        try (HttpClient client = HttpClient.newHttpClient()) {          // 1. the client
            for (String path : new String[] { "/hello", "/gone" }) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(base + path)).build();  // 2. the request
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());     // 3. send, with a body handler
                System.out.println(path + " -> status " + response.statusCode()
                        + ", body \"" + response.body() + "\"");
            }
        } finally {
            server.stop(0);
        }
    }
}
```

```text
/hello -> status 200, body "hi there"                    (verified 2026-11-23)
/gone -> status 404, body "<h1>Not here</h1>"
```

Look at the second line. That's the next section.

`HttpClient` is `AutoCloseable` since Java 21, which is why the sample can use try-with-resources:
`close()` shuts down its threads. In a real service you'd create one client and keep it, because
the whole point of it is the shared connections. Exercise 3 asks you to decide which one
`LocalHttp` should do.

### T6. What throws, and what doesn't

**A 404 doesn't throw.** In `Status`, `send` returned normally for `/gone`, with
`statusCode() == 404` and the server's error page as the body. If the code had only read
`body()`, the error page would have come back as though it were the answer.

`HttpClient` only throws when there's **no HTTP response at all**:

```java
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class Throws {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            try { Thread.sleep(2_000); } catch (InterruptedException e) { }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();

        try (HttpClient client = HttpClient.newHttpClient()) {
            // Nothing listening: there is no HTTP response at all.
            try {
                client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:1/")).build(),
                        HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                System.out.println("nothing listening: " + e.getClass().getName());
            }

            // Something listening, but too slow for this request's timeout.
            HttpRequest slow = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/slow"))
                    .timeout(Duration.ofMillis(300))
                    .build();
            try {
                client.send(slow, HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                System.out.println("too slow:          " + e.getClass().getName() + ": " + e.getMessage());
            }
        } finally {
            server.stop(0);
        }
    }
}
```

```text
nothing listening: java.net.ConnectException             (verified 2026-11-23)
too slow:          java.net.http.HttpTimeoutException: request timed out
```

(The empty `catch` in the handler is acceptable only in a throwaway demo; doc 07, T3, says why.)

So the split is:

| What happened | What `send` does |
| --- | --- |
| a response came back, any status: 200, 404, 500 | returns it; **you check `statusCode()`** |
| nothing is listening | throws `ConnectException` |
| the request's timeout expired | throws `HttpTimeoutException` |

The timeout is **per request**, set on the request builder. (There's also
`HttpClient.newBuilder().connectTimeout(...)`, which only covers making the connection.) The rule
to take away: **with `HttpClient`, an error status is data, not an exception.** Where the status
check lives in your code is a decision you have to make on purpose, because the API won't make it
for you.

### T7. Modules, in one page

A **module** (`module-info.java`, Java 9) is a named set of packages that says which other
modules it `requires` and which of its packages it `exports`. Everything else stays hidden, even
`public` classes. The JDK itself is split into modules, and you can ask any of them what it
declares:

```sh
java --describe-module java.net.http
```

```text
java.net.http@26.0.2.1                                   (verified 2026-11-23)
exports java.net.http
requires java.base mandated
contains jdk.internal.net.http
contains jdk.internal.net.http.common
...
```

`java.net.http` exports one package, the API, and hides fifteen `jdk.internal...` packages that
implement it. On the class path you never see any of this: all of the JDK is available, and
nothing checks which parts you use. The moment you add a `module-info.java` of your own, the JVM
starts checking, and every JDK module you use outside `java.base` has to be in a `requires` line.
Exercise 4 is finding out what that costs in this repo.

### T8. Which garbage collector you get

You don't choose a garbage collector unless you ask for one. The JVM picks one at startup by
looking at the machine (it calls this *ergonomics*), and you can see its choice:

```sh
java -XX:+PrintFlagsFinal -version | grep -E "Use(G1|Serial|Parallel|Z)GC "
```

```text
     bool UseG1GC          = true     {product} {ergonomic}    (verified 2026-11-23, 10 cores; spacing trimmed)
     bool UseParallelGC    = false    {product} {default}
     bool UseSerialGC      = false    {product} {default}
     bool UseZGC           = false    {product} {default}
```

`{ergonomic}` means the JVM chose it. Run the same command with `-XX:ActiveProcessorCount=1`, and
`UseSerialGC` becomes the `{ergonomic}` one. The default depends on the machine, which is why a
container with one CPU can behave differently from your laptop. Exercise 5 asks you to write down
what that means.

## Practice

### Exercise 1: `CircularList`

`CircularList.java` is a hand-written **sorted circular linked list**: `insert(n)` puts `n` in
order and returns the head, and the last node points back to the first. `CircularListTest` has
six tests, all green. They insert in ascending order, or into the middle. **None of them inserts
a value smaller than the head of a list that has two or more elements, and none inserts a
duplicate.** Both are broken (verified):

```text
new CircularList().insert(1).insert(2).insert(3).insert(4)    // 1,2,3,4,     correct
new CircularList().insert(5).insert(10).insert(20).insert(1)  // 5,10,20,1,   1 at the end
new CircularList().insert(5).insert(10).insert(1)             // 5,10,1,
new CircularList().insert(5).insert(10).insert(20).insert(10) // 5,10,20,10,  dup at the end
```

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
   it (a sorted set). **That choice is the same one as `ArrayList` vs `TreeSet` in T2.** The
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

**Done when:** the four commits are in, the step-1 assertions flipped in step 2 (not before), and
the comparison is written down.

### Exercise 2: `MyArrayList`, finish it and then compare

`algorithm/MyArrayList.java` is 28 lines and **unfinished**. Its javadoc asks for `add()`, `get()`,
`remove()` and `size()`, growing "when it reaches threshold". It has `add()` and `size()`, and
(verified):

```text
new MyArrayList().size()       // 10, though it holds nothing: it returns values.length
// the 11th add():               ArrayIndexOutOfBoundsException: Index 10 out of bounds for length 10
```

`MyArrayListTest` has two tests, both `@Disabled` and both naming this doc.

1. **Delete the first `@Disabled`, then fix `size()`.** It should return `lastIndex`. One line.
2. **Write the rest test-first, against the javadoc**: `get(int)`, `remove(int)`, and growth.
   Delete the second `@Disabled`, and add a test that adds 11 elements. The one design question
   is how much to grow by. `ArrayList` grows by half its current size: read `ArrayList.grow` in
   the JDK source and say why it isn't 1, and why it isn't 2×.
3. **Compare it with `ArrayList`**: nothing in the repo calls `MyArrayList`, so there's no caller
   to switch over. The deliverable is the comparison. `ArrayList` since Java 21 also has
   `getFirst`, `getLast`, `addFirst`, `removeLast` and `reversed` (T2, T3). Which of those would
   you have written for `MyArrayList`, and which would you have got wrong? (`addFirst` on an
   array-backed list is the expensive one: it shifts every element.)

**Done when:** both `@Disabled` lines are gone, the 11-element test passes, and the growth
question and the comparison are answered in writing.

### Exercise 3: `HttpClient`, on `httpclient/LocalHttp.java`

Nothing in the old code makes an HTTP call, so this is new code. **It runs entirely inside the
JVM**, against a `com.sun.net.httpserver` instance the test starts on `127.0.0.1` and a random
port, so it works with no network. Delete the `@Disabled` in `LocalHttpTest` first.

The shape is already in the file: `requestFor(uri)`, `stringBody()` and `client()` at lines 47-59
are the three pieces from T5. TODO 1 is putting them together, and checking the result against the
test's `"echo:/ping"`.

Before TODO 2, four facts, all verified on JDK 26 (T6 shows the first and third running):

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

**Done when:** `LocalHttpTest` is enabled and green, there's a test for the non-200 path, TODOs 1
to 3 are done, and the `send` vs `sendAsync` choice is written down.

### Exercise 4: modules, paper, no code committed

Add a `module-info.java` to `src/main/java`, make the build work, and write down what happened.
Then delete it again. Here's what to expect, verified on a scratch copy of the repo:

```java
module org.mandfer.practices {
    requires java.net.http;     // HttpClient
    requires java.desktop;      // java.awt.image, javax.imageio, for ForkBlur
    requires jdk.httpserver;    // com.sun.net.httpserver, for LocalHttp
}
```

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

**Done when:** you've seen the build fail with an empty declaration and pass with three
`requires`, written down the two real-codebase problems, and deleted `module-info.java` again.

### Exercise 5: garbage collectors, paper

One paragraph: which collector your JDK picks, what G1 is optimising for, and when ZGC would be
the right call. Check it on your own machine (T8):

```sh
java -XX:+PrintFlagsFinal -version | grep -E "Use(G1|Serial|Parallel|Z)GC "
```

Verified here, on a 10-core, 23 GB machine: **G1**. Run it again with `-XX:ActiveProcessorCount=1`
and the JVM picks **Serial** instead: the default depends on the machine, which is why a
container with one CPU can behave differently from your laptop. [Doc 31](31-runtime-defaults.md)
has the rest of the runtime defaults.

**Done when:** the paragraph is written, with your own machine's answer in it.

### Exercise 6: fill in "What got worse"

- _(fill this in)_

Prompts: `reversed()` is a view. When is that a feature, and when is it a bug waiting for someone
who expected a copy? `getFirst()` on a `List` is shorter than `get(0)`. Is it clearer, or just
newer? `HttpClient` doesn't throw on a 500: where does your status-code check live, and what
happens the day someone forgets it?

### Exercise 7: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. What is encounter order, and which of `ArrayList`, `LinkedHashSet`, `TreeSet` and `HashSet`
   has none? (T1)
2. Finish the rule: "a sequenced collection names an order the collection ..." Then use it to
   say why `HashSet` isn't sequenced. (T2)
3. Why does `TreeSet.addFirst(0)` throw, when the method is right there on the interface? (T2)
4. An empty `ArrayList`: what does `getFirst()` throw, and what does `get(0)` throw? Which one
   says what's actually wrong? (T2)
5. `rev = list.reversed()`, then `rev.add("z")`. Where in `list` does `"z"` end up, and why?
   (T3)
6. How do you get the first entry of a `LinkedHashMap` on Java 21, and how did you before? (T4)
7. Name the three objects in an `HttpClient` call, and say what decides the `T` in
   `HttpResponse<T>`. (T5)
8. A server answers 404. Does `send` throw? What are the two cases where it does? (T6)
9. Is a request timeout set on the client or on the request? (T6)
10. Your code runs fine on the class path. You add an empty `module-info.java`. What breaks, and
    why didn't it break before? (T7)
11. Why can the same application get a different garbage collector in a one-CPU container than on
    your laptop? (T8)

**Done when:** you can answer all eleven without looking.
