# 10. Sequenced collections, and HttpClient

Two unrelated topics, both shallow on purpose.

## Sequenced collections

**`CircularList.java`** is a hand-rolled sorted circular linked list, 112 lines, with `_value` and
`_next` fields and a `toString` that uses `StringBuffer` and concatenates inside a loop.

**`algorithm/MyArrayList.java`** is 28 lines and **unfinished**. Its own javadoc asks for `add()`,
`get()`, `remove()` and `size()`, and it has `add()` and a `size()` that returns
`values.length`, so it reports 10 when it holds nothing. `add()` carries a `//TODO: control size`
and throws `ArrayIndexOutOfBoundsException` on the eleventh element.

### The exercise

1. Finish `MyArrayList` against its own javadoc, test first. Fix `size()`.
2. Then throw it away and reimplement the callers against `SequencedCollection`. The point is the
   comparison: `getFirst`, `getLast`, `reversed` and `addFirst` are now on the interface, and the
   question is how much of your hand-rolled structure was carrying its own weight.
3. `StringBuffer` to `StringBuilder` to `String.join` in `CircularList.toString()`. Know why
   `StringBuffer` is synchronized and why nobody wants that here.

## HttpClient

Nothing in the old code makes an HTTP call, so this is new: `httpclient/LocalHttp.java`.
**It runs entirely inside the JVM**, against a `com.sun.net.httpserver` instance the test starts,
so it works with no network. Three TODOs.

## Paper exercises, no code

**JPMS.** Add a `module-info.java` and make the build work. Nothing here is written for it, which
is the exercise: you will meet the split-package problem and the automatic-module rules straight
away, and those are the two things that actually bite. Delete it again afterwards.

**Garbage collectors.** One paragraph below: which collector your JDK defaults to, what G1 is
optimising for, and when ZGC would be the right call. Check it with
`java -XX:+PrintFlagsFinal -version | grep -i useg1gc`.

## What got worse

- _(fill this in)_
