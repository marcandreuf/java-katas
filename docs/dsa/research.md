# DSA research: what is load-bearing, and what is interview trivia

**Research document, not a stage.** No exercise code exists yet — no Java file and no test in
this repo was created or changed for it. It proposes a track;
[the stage docs are not written](#proposed-track-nine-stages).

## The question, and the answer

The organising question is **which DSA knowledge changes the code a working Java developer writes,
and which only changes how they do in an interview.** The answer this repo's own files argue for is
narrower than any syllabus: almost none of the *algorithms* are load-bearing, and almost all of the
*contracts* are. Nobody shipping Java implements a hash table, a red-black tree or Dijkstra — the
JDK did it, better, and [`HashMap`'s tree bins](#hashmap-the-thresholds-are-not-what-people-quote)
are a worked example of how much better. What does get shipped, repeatedly and wrongly, is a
`contains` inside a loop, a `binarySearch` on an unsorted list, a key whose `hashCode` disagrees
with its `equals`, a recursion whose depth is the size of the input, and a `LinkedList` chosen
because somebody said insertion is O(1). Every one of those is a *contract* failure, not an
algorithm failure, and every one of them is in this repo. The sharpest evidence is
`algorithm/BinarySearch.java`: it **cannot find five of the twelve elements in its own test
fixture**, and its eight tests are green. That is the whole thesis in one file — the algorithm is
the easy part, and the part nobody checked is the part that is wrong.

## Verdict summary

| Topic | Verdict | Reason in one line |
| --- | --- | --- |
| Which collection, and its real cost | **load-bearing** | It is a decision you make weekly and cannot change cheaply later. |
| Accidental quadratics | **load-bearing** | The single most common real performance bug, and invisible at test sizes. |
| `equals`/`hashCode` contract | **load-bearing** | Break it and a `HashMap` silently loses entries; a `List` still works, so tests pass. |
| `binarySearch` contracts (sorted input, `-(ip)-1`) | **load-bearing** | Wrong answer, no exception. Two separate silent-failure modes. |
| Binary search over an **answer space** | **load-bearing** | The one genuinely non-obvious technique here, and `FairWorkLoad.java` is already it. |
| Recursion depth and `StackOverflowError` | **load-bearing** | Depth is a function of input size and `-Xss`; there is no safe constant. |
| Integer overflow in index/sum arithmetic | **load-bearing** | Silent wrong answers in `NumberReverse` and `DuplicateNumber` today. |
| Views vs copies (`subList`, `asList`, `unmodifiableList`) | **load-bearing** | Aliasing bugs that outlive the method that created them. See [doc 16](../modernization/16-collection-factories.md). |
| Iteration-order guarantees (`PriorityQueue`, `Set.of`, `HashMap`) | **load-bearing** | Tests that pass on small inputs and fail on real ones. |
| Sort stability, and *why* primitives differ | **load-bearing** | Decides whether a two-key sort needs one pass or two. |
| BFS / reachability / topological order | **load-bearing, narrowly** | You write this as "what order do I do these in", not as a graph problem. |
| Prefix sums / running aggregates | **load-bearing, narrowly** | Turns an O(n²) report query into O(n) and is easy to recognise. |
| Big-O as a *recognition* tool | **load-bearing** | "This loop is inside that loop" is the whole of the useful version. |
| Big-O as *recitation* | interview-only | Knowing TimSort is O(n log n) has never changed a line of code. |
| Implementing a linked list | **interview-only** | `CircularList.java` is 112 lines and proves the hard part is the API, not the pointers. |
| Implementing a growable array | **interview-only** | `MyArrayList.java` is unfinished; [`ArrayList`'s real growth policy](#arraylist-growth-and-what-ensurecapacity-actually-saves) is the useful half. |
| Red-black tree rotations | **interview-only** | `HashMap` and `TreeMap` contain them; 25 years of production use beats your weekend. |
| Writing your own hash function | **interview-only** | `HashMap` degrades *gracefully* on bad hashes by design. The contract matters, the function does not. |
| Dijkstra / A\* by hand | **interview-only** | If you genuinely need weighted shortest path, you need a graph library and a correctness proof. |
| DP tabulation / memoisation tables | **interview-only** | *Noticing* exponential recursion is load-bearing; the table-filling ritual is not. |
| Amortised-analysis proofs | interview-only | The conclusion ("`ArrayList.add` is cheap") is all you use. |
| Manual balancing, tries, segment trees, union-find | interview-only | Zero of these have appeared in this repo in eleven years of commits. |
| Bit-twiddling tricks | interview-only | `Integer.bitCount`, `Math.clamp` and `>>>` cover the real cases. |

The two rows worth arguing with are **"implementing a linked list"** and **"DP"**, because both have
a load-bearing shadow. See [the verdicts in full](#4-the-verdicts-in-full).

---

# Review against current literature

Added after a second pass against authoritative sources rather than this repo alone: the Oracle
OCP Java SE 21 (1Z0-830) objectives, *Effective Java* 3rd edition, and the JEPs that own the
behaviour. **The thesis above survived** — the contracts are the load-bearing part — but the first
pass drew the contract boundary too narrowly. Six topics the literature treats as baseline were
missing, and all six are verified below on JDK 26.

## What was missing, and why it belongs

### 1. Treeification is a security mitigation, and it needs `Comparable` keys

The first pass verified *when* `HashMap` treeifies and never asked *why it exists*.
**[JEP 180](https://openjdk.org/jeps/180)** is the answer: it mitigates hash-flooding denial of
service (**CVE-2011-4858**), where crafted POST bodies collide every key into one bucket and turn a
lookup into a linear scan. The JEP's own framing is improving the worst case "from O(n) to
O(log n)" for "any key type that implements `Comparable`".

That last clause is the load-bearing part, and it is measurable. Two keys, both with
`hashCode() { return 42; }`, 20,000 put-then-get pairs, differing only in whether the key
implements `Comparable`:

| Key | Time |
| --- | --- |
| not `Comparable` | **2,735 ms** |
| `Comparable` | **42 ms** |

**65x, for implementing an interface that has nothing to do with hashing.** A tree bin needs an
ordering to be a tree; without `compareTo` it falls back to a worse path. Nobody teaches this and
it is a one-line fix on a key class.

### 2. The `Comparable`/`Comparator` contract, as its own topic

The first pass covered sort *stability* and not the *contract*, which is an explicit OCP objective
and *Effective Java* Item 14. Three verified failure modes, none of which throws at test size:

- **An intransitive comparator throws, but only above a size threshold.** Measured with a
  comparator that never returns 0: silent at n=8, 16, 31, 32, 33; then
  `IllegalArgumentException: Comparison method violates its general contract!` from n=63 upward.
  Below about 32, TimSort uses binary insertion sort and never merges, so it never checks.
  **Your unit test is under the threshold and production is over it.**
- **`(x, y) -> x - y` overflows.** Sorting `[MIN_VALUE, MAX_VALUE, 0]` with it yields
  `[0, 2147483647, -2147483648]` — wrong, silently. `Integer::compare` is correct. This is Item
  14's explicit advice against relational operators in `compareTo`.
- **`TreeMap` and `TreeSet` define equality by the comparator, not by `equals`.** With
  `String.CASE_INSENSITIVE_ORDER`, putting `"Key"`, `"KEY"` and `"key"` leaves **size 1**; the same
  three keys in a `HashMap` leave size 3. A `TreeSet` built that way answers `contains("KeY")` with
  `true`. Item 14's "consistent with equals" advice is this bug.

### 3. The null-handling contract matrix

Not covered at all, and it is pure contract. Verified:

| | null key | null value |
| --- | --- | --- |
| `HashMap`, `LinkedHashMap` | accepted | accepted |
| `TreeMap` | **NPE** | accepted |
| `ConcurrentHashMap` | **NPE** | **NPE** |
| `Map.of` | **NPE** | **NPE** |
| `HashSet` | accepted | — |
| `TreeSet` | **NPE** | — |
| `ArrayList` | accepted | — |
| `ArrayDeque` | **NPE** | — |

**`ArrayDeque` rejecting null is the one that catches people**, because it removes the option of a
null sentinel and it is the collection the first pass correctly recommends over `LinkedList`.
Swapping one for the other is not behaviour-preserving.

### 4. Structures you assemble from the JDK rather than implement

The first pass is right that implementing an LRU cache is interview-only, and then does not say
what to do instead. The literature's answer is a small set of JDK assemblies, all verified:

- **`LinkedHashMap` is an LRU cache** with `accessOrder=true` and an overridden
  `removeEldestEntry`. Capacity 3, put a/b/c, `get("a")`, put d, and the contents are
  `[c, a, d]`: `b` was evicted and `a` survived *because it was read*. A complete LRU in four lines.
- **`EnumMap` is an ordinal-indexed array**, no hashing. Inserting `SHIP, PLAN, BUILD` iterates
  `[PLAN, BUILD, SHIP]` — declaration order, guaranteed. The same keys in a `HashMap` iterate
  `[BUILD, PLAN, SHIP]`, which is unspecified.
- **`NavigableMap` range queries**: `floorKey(25)=20`, `ceilingKey(25)=30`,
  `subMap(10,false,30,true)={20,30}`, `descendingKeySet()`. This is the answer to "which band does
  this value fall into", which is otherwise written as a loop.
- **`ArrayDeque` is the stack.** `java.util.Stack` extends `Vector` and is synchronised; the
  `Deque` javadoc recommends `ArrayDeque` over it.

### 5. `Comparable` consistency as a `HashMap` *performance* property

Points 1 and 2 meet here, and neither source joins them up: the same `Comparable` interface Item 14
wants for correctness is what JEP 180 needs for the 65x. One interface, two unrelated reasons, and
a key class that omits it pays both.

## What the literature confirmed

- **Collection choice framed as *contracts*** — order guarantees, null policy, iteration
  stability — **rather than as Big-O**, which is the first pass's central claim.
- **Sequenced collections** (Java 21) are an OCP objective; modernization
  [doc 10](../modernization/10-sequenced-collections-and-httpclient.md) already owns them.
- **Nothing in the interview-only column was contradicted.** No authoritative Java source lists
  segment trees, union-find or manual red-black rotations as developer baseline. They appear only
  in interview-prep material, which is exactly the distinction this track draws.

## What this changed

The track grows from nine stages to **twelve**, and the numbering restarts at `01` because the DSA
track now has its own folder. Three stages are new: the contract matrix, the
`Comparable`/`Comparator` contracts, and the JDK-assembly stage. The nine-stage proposal below is
kept as the record of the first pass; [`INDEX.md`](INDEX.md) is the track as built.

---

# Findings

Everything below was run on **JDK 26.0.2.1 (Temurin)** against **`$JAVA_HOME/lib/src.zip`**, which
is the actual OpenJDK source for `java.base`. Line numbers are that source's. Measurements are
wall-clock, best-of-3 after warmup, one JVM — which [doc 34](../modernization/34-measuring-it-properly.md) tells you
to distrust. They are used here only for order-of-magnitude differences, never for percentages;
anywhere a percentage would matter, this document says "measure it with JMH".

## 1. The collections as they actually are

### `ArrayList`: growth, and what `ensureCapacity` actually saves

`ArrayList.java:119` sets `DEFAULT_CAPACITY = 10`. `grow` at `ArrayList.java:232-241` delegates to
`ArraysSupport.newLength(oldCapacity, minCapacity - oldCapacity, oldCapacity >> 1)` —
`jdk/internal/util/ArraysSupport.java:897-909`. The `oldCapacity >> 1` is the **preferred growth**,
so the factor is **1.5x, floored**, not 2x.

Measured, by reflecting on `elementData`:

    new ArrayList<>()     capacity 0        <- not 10
    size 1   -> capacity 10
    size 11  -> capacity 15
    size 16  -> capacity 22
    size 23  -> capacity 33
    size 34  -> capacity 49
    size 50  -> 73    size 74  -> 109   size 110 -> 163   size 164 -> 244

Three things fall out that people get wrong:

- **A fresh `ArrayList` holds a zero-length array.** `DEFAULTCAPACITY_EMPTY_ELEMENTDATA` is shared,
  so an empty list costs no backing array at all. The 10 appears on the first `add`.
- **`new ArrayList<>(0)` is a trap.** Measured: the first `add` gives it capacity **1**, and it then
  grows 1, 2, 3, 4, 6, 9 … Passing 0 to "save memory" buys a copy per element for the first few
  dozen.
- **`ensureCapacity(5)` on a default-constructed empty list does nothing.** `ArrayList.java:216-219`
  short-circuits when the array is still `DEFAULTCAPACITY_EMPTY_ELEMENTDATA` and
  `minCapacity <= DEFAULT_CAPACITY`. Measured: capacity stays 0. `ensureCapacity(1000)` gives you
  exactly 1000 and skips **eight** `Arrays.copyOf` calls.

So `ensureCapacity` saves `log(n / 10) / log(1.5)` array copies and the garbage from each. For
n = 1,000 that is 11 copies totalling ~2,800 element writes. **That is nothing.** `ensureCapacity`
earns its keep at millions of elements or when the copies are the thing generating GC pressure, and
nowhere else. `The100DoorsCyberDojoTest:46` already does it (`new ArrayList<Door>(100)`) and gains
precisely two skipped copies.

### `HashMap`: the thresholds are not what people quote

`HashMap.java:238, 250, 260, 267, 275`:

| Constant | Value |
| --- | --- |
| `DEFAULT_INITIAL_CAPACITY` | `1 << 4` = 16 |
| `DEFAULT_LOAD_FACTOR` | `0.75f` |
| `TREEIFY_THRESHOLD` | 8 |
| `UNTREEIFY_THRESHOLD` | 6 |
| `MIN_TREEIFY_CAPACITY` | 64 |

The commonly-repeated version is "eight collisions in a bucket and it becomes a red-black tree".
**Both halves of that are wrong**, and the source says so twice.

First, the trigger is at `HashMap.java:649-651`: `if (binCount >= TREEIFY_THRESHOLD - 1)` where
`binCount` starts at 0 and counts nodes *already* traversed — so it fires when the bin reaches
**nine** nodes, not eight.

Second, `treeifyBin` at `HashMap.java:762-766` refuses:

    if (tab == null || (n = tab.length) < MIN_TREEIFY_CAPACITY)
        resize();

With a table smaller than 64 it **resizes instead of treeifying**, on the reasonable bet that the
collisions are an artefact of a small table. Measured with keys whose `hashCode()` is a constant 0:

| colliding keys inserted | table length | tree bins | longest list |
| --- | --- | --- | --- |
| 8 | 16 | 0 | 8 |
| 9 | 32 | 0 | 9 |
| 10 | 64 | 0 | 10 |
| **11** | 64 | **1** | 0 |

So in a default-constructed `HashMap` it takes **eleven** perfectly-colliding keys. Pre-size it with
`new HashMap<>(64)` and it trees at the ninth, as measured separately. The threshold is not a number
you can quote without the table size.

**Why a bucket becomes a tree at all** is in the implementation note at `HashMap.java:148-233`,
which is worth reading in full. Its Poisson table is at `HashMap.java:183-200`:

    0:    0.60653066     3:    0.01263606     6:    0.00001316
    1:    0.30326533     4:    0.00157952     7:    0.00000094
    2:    0.07581633     5:    0.00015795     8:    0.00000006

With well-distributed hashes a bin of 8 is a **one-in-sixteen-million** event. Tree bins are not a
performance feature for normal maps; they are a **hash-flooding defence**, and the note says it at
`HashMap.java:167-170`: "performance degrades gracefully under accidental or malicious usages in
which `hashCode()` methods return values that are poorly distributed". It also prices the defence
honestly at `:172-175` — "we may waste about a factor of two in time and space compared to taking
no precautions" — for keys that are neither well-hashed nor `Comparable`.

**This is the reason "write your own hash function" is interview-only.** You cannot make `HashMap`
asymptotically bad any more. You can only make it twice as slow, and only if your keys are also
non-`Comparable`.

**Pre-sizing a `HashMap` is the one load-bearing number here**, and `new HashMap<>(n)` is the wrong
way to do it. `HashMap.java:438` and `:464` carry an `@apiNote` saying so, and
`calculateHashMapCapacity` at `:2564` is `ceil(numMappings / 0.75)`. Measured table sizes while
inserting 1,000 entries:

    new HashMap<>()           16 32 64 128 256 512 1024 2048    <- seven rehashes
    new HashMap<>(1000)       1024 2048                         <- one rehash, of 768 entries
    HashMap.newHashMap(1000)  2048                              <- none

`HashMap.newHashMap(int)` has existed since Java 19 and `new HashMap<>(n)` still rehashes, because
`n` is a *capacity* and you wanted a *count*. Same for `HashSet.newHashSet` and
`LinkedHashMap.newLinkedHashMap`.

### `LinkedList` vs `ArrayDeque`, and whether `LinkedList` is ever right

**No. Not in this repo, and probably not in yours.** `ArrayDeque.java:48-50` says it outright in the
class javadoc — "likely to be faster than `Stack` when used as a stack, and faster than
`LinkedList` when used as a queue".

Measured as a FIFO queue (add n, drain n):

| n | `LinkedList` | `ArrayDeque` |
| --- | --- | --- |
| 1,000 | 0.139 ms | 0.169 ms |
| 100,000 | 0.925 ms | 0.824 ms |
| 1,000,000 | 18.6 ms | 11.8 ms |

Modest — 1.6x at a million. If throughput were the whole story, `LinkedList` would be merely
unfashionable. The real cost is one 24-byte-plus `Node` object per element versus one array slot,
which is a GC and cache-locality argument this measurement cannot see.

The decisive result is **the one operation `LinkedList` is supposed to win**, insertion at the
middle:

| n insertions at `size()/2` | `LinkedList` | `ArrayList` |
| --- | --- | --- |
| 1,000 | 0.474 ms | 0.141 ms |
| 10,000 | 50.7 ms | 4.4 ms |
| 100,000 | 5,204 ms | 364 ms |

`LinkedList` is **14x slower at its own party.** The link splice is O(1), but *getting to the
middle* is O(n) dependent pointer loads, each a potential cache miss, while `ArrayList` does one
`System.arraycopy` of half the array — a bulk memory move the hardware is built for.
`LinkedList.java:43-44` is honest about the traversal ("from the beginning or the end, whichever is
closer"); the O(1) insertion claim is true and useless, because you can only use it from a
`ListIterator` you already have positioned.

**The `Collections.binarySearch` cliff.** `Collections.java:108` defines
`BINARYSEARCH_THRESHOLD = 5000`, and the dispatch at `:217` is:

    if (list instanceof RandomAccess || list.size() < BINARYSEARCH_THRESHOLD)
        return Collections.indexedBinarySearch(list, key);
    else
        return Collections.iteratorBinarySearch(list, key);

For a `LinkedList` **below** 5,000 elements it calls `list.get(mid)` in a loop — O(n log n) link
traversals. Measured, 2,000 searches:

| n | `LinkedList` | `ArrayList` | ratio |
| --- | --- | --- | --- |
| 1,000 | 5.84 ms | 0.222 ms | 26x |
| **4,999** | **33.9 ms** | 0.237 ms | 144x |
| **5,000** | **14.7 ms** | 0.150 ms | 98x |
| 100,000 | 339 ms | 0.299 ms | 1134x |

**Adding one element makes it 2.3x faster.** The threshold exists to avoid allocating a
`ListIterator` for small lists, and for a non-`RandomAccess` list that trade is backwards. A perfect
flag-this-with-a-test trap, and a reminder that "it's a binary search, it's O(log n)" is a statement
about comparisons, not about time.

### `PriorityQueue` is a binary heap, and its iteration order is not sorted

`PriorityQueue.java:96-102` documents the layout — "the two children of `queue[n]` are
`queue[2*n+1]` and `queue[2*(n+1)]`". `PriorityQueue.java:59-63` documents the consequence: the
iterator and spliterator "are *not* guaranteed to traverse the elements of the priority queue in any
particular order. If you need ordered traversal, consider using `Arrays.sort(pq.toArray())`."

Measured, after `addAll(List.of(5,1,9,2,8,3,7,4,6))`:

    toString()     : [1, 2, 3, 4, 8, 9, 7, 5, 6]
    stream().toList: [1, 2, 3, 4, 8, 9, 7, 5, 6]
    for-each       : 1 2 3 4 8 9 7 5 6
    poll() order   : 1 2 3 4 5 6 7 8 9        <- the only sorted one
    peek()         : 1                        <- guaranteed to be the minimum

**The trap is that the first four elements are in order.** A test on three or four elements passes.
A log line, a `toString()` in an error message, a `stream().toList()` in a REST response — all of
them look sorted until they are not. The only guarantees are `peek`/`poll` returning the least
element, and ties broken arbitrarily (`PriorityQueue.java:44-48`).

### `Arrays.sort`: two algorithms, and why

`Arrays.java:91` on `sort(int[])`: "@implNote The sorting algorithm is a Dual-Pivot Quicksort by
Vladimir Yaroslavskiy, Jon Bentley, and Joshua Bloch."

`Arrays.java:1001-1002` on `sort(Object[])`: "This sort is guaranteed to be *stable*: equal elements
will not be reordered as a result of the sort", implemented as TimSort (`Arrays.java:1004-1025`).
`Arrays.parallelSort(T[])` is stable too (`Arrays.java:769`).

**Why the two differ is not a performance footnote, it is a definition.** Equal `int`s are
*indistinguishable*. "Which 1 came first" is not a question you can ask about `int[]`, so stability
is unobservable, so the implementation is free to pick the faster, in-place, zero-extra-allocation
algorithm. Objects can be equal under the comparator and distinct in every other field, so stability
is observable and had to be specified. Measured:

    TimSort on Pair[] by key : [Pair[key=a, ord=2], Pair[key=a, ord=4], Pair[key=b, ord=1], ...]
    DualPivotQuicksort int[] : [1, 1, 2, 3, 3]   <- you cannot tell which 1 is which

The consequence that costs money: **stability is what lets you sort by two keys in two passes.**
Sort by `ord`, then sort by `key`, and the `ord` order survives inside each `key` group. Lose
stability and you need a composed `Comparator` and one pass. [Doc 14](../modernization/14-map-defaults-and-collectors.md)
already makes you meet the composed-comparator version and its `reversed()` placement trap.

Two more verified facts:

- **Quicksort's O(n²) worst case is defended against in the JDK.**
  `DualPivotQuicksort.java:281-286`, comment verbatim: "Switch to heap sort if execution time is
  becoming quadratic", guarded by `MAX_RECURSION_DEPTH = 64 * DELTA` at `:119-124`. The javadoc's
  "O(n log(n)) performance on all data sets" is true *because of this line*. The adversarial-input
  attack on quicksort is a real thing that the library already handled.
- **TimSort catches a broken comparator and quicksort cannot.** Measured: a comparator that returns
  `1` unconditionally sorts 64 `Integer`s without complaint, but
  `(x, y) -> x - y > 0 ? 1 : -1` over 256 elements throws
  `IllegalArgumentException: Comparison method violates its general contract!`. This is TimSort
  detecting that its merge invariants are impossible, and it is **size-dependent** — which is why it
  arrives in production and not in your test. The legacy escape hatch
  `-Djava.util.Arrays.useLegacyMergeSort=true` is still read at `Arrays.java:988-989`.

### Views, not copies

`List.java:712-713`: a `subList` "is backed by this list, so non-structural changes in the returned
list are reflected in this list, and vice-versa". `List.java:729-733`: the view's semantics "become
undefined if the backing list … is structurally modified in any way other than via the returned
list." Measured:

    base = [a, b, c, d, e];  view = base.subList(1, 4)
    view.set(0, "B")   ->  base = [a, B, c, d, e]
    view.clear()       ->  base = [a, e]              <- the documented range-delete idiom
    base.add("f")      ->  reading the view throws ConcurrentModificationException

`view.clear()` deleting from the backing list is not a bug, it is the idiom `List.java:721-724`
recommends. The hazard is a `subList` stored in a field and read later.

`Arrays.java:4168` is emphatic: "*The list returned by this method is modifiable.*" Measured:

    String[] arr = {"x","y","z"};  List<String> fixed = Arrays.asList(arr);
    fixed.set(0, "X")  ->  arr = [X, y, z]            <- writes into the array
    fixed.add("w")     ->  UnsupportedOperationException
    fixed.remove(0)    ->  UnsupportedOperationException

Fixed length, mutable contents, aliased to the array. [Doc 16](../modernization/16-collection-factories.md) covers
this and `Collections.unmodifiableList`'s window semantics already — **cross-reference it, do not
re-teach it.** The one thing doc 16 does not cover is the primitive-array version, measured:

    Arrays.asList(new int[]{1,2,3})       -> List<int[]> of size 1
    Arrays.asList(new Integer[]{1,2,3})   -> List<Integer> of size 3

`DuplicateNumber.java:35-36` gets this right by using `Integer[]`, almost certainly by accident.

### Fail-fast is explicitly best-effort

`ConcurrentModificationException.java:49-55`, verbatim: "fail-fast behavior cannot be guaranteed …
Fail-fast operations throw `ConcurrentModificationException` on a best-effort basis. Therefore, it
would be wrong to write a program that depended on this exception for its correctness:
*`ConcurrentModificationException` should be used only to detect bugs.*" The same paragraph is
repeated in `ArrayList`, `LinkedList`, `ArrayDeque`, `HashMap` and `TreeMap`.

Measured, removing one element from `[a, b, c, d]` inside a for-each loop:

| removed | result |
| --- | --- |
| `"a"` (index 0) | `ConcurrentModificationException` |
| `"b"` (index 1) | `ConcurrentModificationException` |
| **`"c"` (index 2)** | **no exception**, list is `[a, b, d]`, loop ends one element early |
| `"d"` (index 3) | `ConcurrentModificationException` |

Removing the **second-to-last** element never throws, because `ArrayList.Itr.hasNext()` compares
`cursor != size` and the decremented `size` makes it look finished. The loop silently skips the last
element. This is the load-bearing shape of the lesson: the fast-failure you rely on has a hole in it
at exactly one index, and a test with four elements finds it.

### `binarySearch`: two silent failures in one method

`Arrays.java:1636-1654` and the identical text on `Collections.binarySearch`:

- "The array must be sorted … **prior to making this call. If it is not sorted, the results are
  undefined.**"
- Returns "index of the search key, if it is contained in the array; otherwise,
  `(-(insertion point) - 1)`" — and "this guarantees that the return value will be >= 0 if and only
  if the key is found."
- "If the array contains multiple elements with the specified value, there is no guarantee which one
  will be found."

The implementation's last line is `Arrays.java:1633`: `return -(low + 1);`

Measured on `{10, 20, 30, 40}`:

    key  5 -> -1   insertion point 0        key 35 -> -4   insertion point 3
    key 10 ->  0   found                    key 40 ->  3   found
    key 15 -> -2   insertion point 1        key 50 -> -5   insertion point 4

`~r` recovers the insertion point from a negative result and is the idiom to recognise:
`-(-3 + 1)` and `~(-3)` are both 2.

And the one that ships:

    int[] u = {40, 10, 30, 20};
    Arrays.binarySearch(u, 10) ->  1    (correct by luck)
    Arrays.binarySearch(u, 40) -> -5    (40 is at index 0)

**No exception. A wrong answer that looks like a valid "not found".** Combined with "there is no
guarantee which one will be found" on duplicates — measured: `binarySearch({1,2,2,2,2,3}, 2)`
returns 2, not 1 — this is a method with three documented ways to mislead you and no way to
complain.

## 2. Accidental quadratic behaviour

This is the most load-bearing algorithmic skill there is, because it is the only one that regularly
takes production down. All four classic shapes, measured side by side with their linear replacement.
Times in **milliseconds**, best of 3 after warmup:

| | n=100 | n=1,000 | n=10,000 | n=50,000 | n=100,000 |
| --- | --- | --- | --- | --- | --- |
| `ArrayList.contains` in a loop | 0.015 | 0.356 | 29.6 | 698 | **2,852** |
| `HashSet.contains` in a loop | 0.008 | 0.059 | 0.611 | 1.17 | 2.00 |
| `ArrayList.remove(0)` to drain | 0.016 | 0.209 | 6.64 | 157 | **622** |
| `ArrayDeque.poll()` to drain | 0.014 | 0.136 | 0.655 | 1.15 | 0.774 |
| `s += "x"` in a loop | 0.006 | 0.087 | 8.09 | 213 | **614** |
| `StringBuilder.append` | 0.004 | 0.038 | 0.392 | 0.556 | 0.366 |
| `LinkedList.get(i)` index loop | 0.015 | 0.403 | 29.7 | 747 | **2,980** |
| `LinkedList` for-each | 0.013 | 0.124 | 0.647 | 0.849 | 0.926 |

**Where the crossover actually is, honestly:**

- **At n = 100, it does not matter.** Every quadratic is within 2x of its linear replacement and the
  absolute cost is 15 microseconds. Rewriting it is a style choice, and if the quadratic version is
  clearer, keep it. Saying this out loud is part of the point.
- **At n = 1,000, it still basically does not matter.** 2–7x, and under half a millisecond. Not
  worth a refactor on its own; worth noticing if it is in a request path hit a thousand times a
  second.
- **At n = 10,000 it starts to hurt**: 10–50x, tens of milliseconds, the range where a human notices
  a page is slow.
- **At n = 100,000 it is an outage.** Three seconds for a dedupe. 622 ms to drain a list.

**The dangerous property is not the slope, it is that the slope is invisible where you test.** The
ratio at n = 100 is 2x and at n = 100,000 it is 1,400x. A unit test with ten items and a production
input with a hundred thousand are on opposite sides of that. The skill is not "avoid quadratics" —
it is **knowing the input size**, and the follow-up question "what happens when this list is a
hundred times bigger" is the one that catches them.

Live instances in this repo, all currently harmless, all the right shape:

- **`algorithm/FairWorkLoad.java:30` and `:39`** — `line = line.concat(" " + n)` inside the inner
  loop, inside the binary search loop. String concatenation per element, per candidate median. The
  variable is used only for a `System.out.println` at `:52-55`.
- **`The100DoorsCyberDojoTest:83`** — `retVal = retVal + (doorNumber-1) + ","` in a `while` loop. At
  n = 10, as measured, it costs 6 microseconds. This is a correct decision in 2014 code and should
  be left alone with a comment saying why.
- **`algorithm/StringRecursiveReversal.java:11`** — `reverseString(str.substring(1)) + str.charAt(0)`
  is quadratic twice over: a fresh `String` of length n-1 per frame, plus a concatenation per frame.
  Measured against `new StringBuilder(s).reverse()`: at n = 4,000 it is 2.489 ms vs 0.0857 ms, a 29x
  gap, and it gets worse linearly.
- **`ProductsOfArrayElementsTest:60-66`** — `productsArray()` calls `otherValues` (an O(n) array
  copy) and `product` (an O(n) loop) once per element. O(n²) by construction. The O(n) answer is
  prefix and suffix products, which also avoids the division that breaks on a zero element.
  [Doc 00](../modernization/00-start-here.md) and [doc 09](../modernization/09-streams-and-optional.md) already own the
  `removeElement` duplicates trap in the same file — cross-reference, do not duplicate.

## 3. Complexity, honestly

**Where Big-O earns its keep:** exactly two places.

1. **Recognising that one loop is inside another**, including when the inner loop is hidden behind a
   method call — `contains`, `indexOf`, `remove(Object)`, `List.get` on a non-`RandomAccess` list,
   `String +`, a repository call, a `Collections.binarySearch` on a `LinkedList`. Every entry in the
   table above is this, and *every one of them reads as a single statement*. This is the whole skill
   and it is a reading skill, not a maths skill.
2. **Knowing the per-operation cost of the collection you picked**, to one significant figure.
   `ArrayList.get` cheap, `ArrayList.contains` linear, `HashMap.get` constant-ish,
   `TreeMap.get` logarithmic but with range queries, `ArrayDeque` both ends cheap,
   `PriorityQueue.peek` free and `PriorityQueue.contains` linear
   (`PriorityQueue.java:74-76` says so).

**Where it is theatre:**

- **Reciting the complexity of library algorithms.** `Arrays.sort` is O(n log n). You cannot act on
  that. The actionable facts about `Arrays.sort` are *stability* and *the stability difference
  between the primitive and object overloads*, and neither is a complexity fact.
- **Constant factors treated as noise.** The `LinkedList` middle-insert result above is two
  algorithms with the same asymptotics where one loses by 14x on memory layout alone. Big-O
  predicted a tie.
- **Space complexity in O() terms.** "O(n) extra space" does not tell you whether it is 4 bytes or
  48 bytes per element, and that is the number that decides whether it fits in a heap.
- **Amortised-analysis arguments.** The conclusion — "`ArrayList.add` is cheap, do not worry about
  it" — is the entire usable content.
- **Best/average/worst distinctions for library code.** The JDK already removed the interesting worst
  cases: `DualPivotQuicksort.java:281-286` for quicksort, `HashMap.java:762` for hash flooding.

**The honest framing for the track:** stop saying "what is the complexity of this" and start saying
**"how big is n, and what happens if it is a hundred times bigger"**. The second question catches
every bug the first one does, plus the constant-factor ones, and it has an answer you can write down.

## 4. The verdicts in full

Reasons, including the uncomfortable ones.

### Load-bearing, and why

- **Collection choice.** Weekly decision, expensive to reverse once it is in a published signature,
  and the costs are not guessable — see the `LinkedList` table. This is the highest-value item in
  the whole list and it involves implementing nothing.
- **`equals`/`hashCode`.** Verified failure mode: a class with `equals` and no `hashCode` is found
  by `List.contains` and **not** found by `HashMap.get`, and putting an equal key again grows the
  map to `size() == 2` holding two keys that are `equals` to each other. A mutable key mutated after
  insertion is unreachable even through the original reference, while still appearing in
  `keySet()` and counting toward `size()`. Both measured. There is no exception and no warning;
  records fix the common case and `HashMap` cannot fix the rest.
- **Accidental quadratics.** See above. The commonest real performance bug and the only one you can
  find by reading.
- **`binarySearch` contracts.** Three documented silent failures: unsorted input, the negative
  return encoding, duplicates. Two of them produce a plausible-looking wrong answer.
- **Binary search over an answer space.** Genuinely non-obvious, genuinely useful (rate limits,
  capacity planning, "largest batch size that fits in the timeout"), and
  **`algorithm/FairWorkLoad.java` already is one** — `low = max(folders)`, `high = sum(folders)`,
  and a monotone feasibility predicate in between. `BinarySearchTest:17` even states the formal
  condition in its javadoc: "binary search can be used if and only if for all x in S, p(x) implies
  p(y) for all y > x." That comment is the most valuable line of prose in the repo.
- **Recursion depth.** Measured frames before `StackOverflowError` for a trivial frame:

      -Xss256k   1,781 frames
      default   21,560 frames
      -Xss4m   207,869 frames

  **There is no safe recursion depth**, only a measured one for a given frame size and `-Xss`, and
  `-Xss` differs between your laptop, your container and your CI box. `StringRecursiveReversal`
  survived 16,000 characters and died at 20,000 on this JVM — a plausible input for a text field.
  The rule is: if depth is a function of input size, it must be a loop or an explicit stack.
- **Integer overflow.** Measured: `NumberReverse.reverseNumber(2147483647)` returns
  **-1126087180**, and `reverseNumber(1534236469)` returns 1056389759 because the true answer
  9646324351 does not fit. Silent. `Math.addExact` / `Math.multiplyExact` (Java 8) throw
  `ArithmeticException: integer overflow` instead, and that is the fix.
  `DuplicateNumber.java:21`'s Gauss formula `highestNumber * (highestNumber + 1) / 2` overflows above
  about 65,535 elements. `low + (high - low) / 2` in `BinarySearch.java:40` and
  `FairWorkLoad.java:74` is the *correct* overflow-safe midpoint, and whoever wrote it knew why —
  that is worth pointing at.
- **Views vs copies, and iteration-order guarantees.** Both are aliasing/assumption bugs that
  survive code review. Partly owned by [doc 16](../modernization/16-collection-factories.md).
- **BFS / reachability / topological order, narrowly.** You do write this, but never as "a graph
  problem" — you write it as "what order do I initialise these in", "is this config reachable",
  "did someone create a cycle in the parent links". A queue and a visited set. That is the whole
  technique and it is worth one stage.
- **Prefix sums, narrowly.** Worth knowing because it is easy to *recognise*: any "for each element,
  aggregate over its neighbours/prefix/suffix" is one pass, not n.

### Interview-only, and why — the part worth being blunt about

- **Implementing a linked list.** `CircularList.java` is the argument, 112 lines of it. The pointer
  work at `:57-88` is correct and took an afternoon. What it does not have, after eleven years, is
  `size()`, `get(i)`, `remove()`, `iterator()`, `equals`, `hashCode`, or a null policy — and its
  `insert` at `:19-47` returns a possibly-new head that the caller must reassign, because the node
  *is* the list with no container type. **The hard part of a linked list is the API and the
  invariants, which is exactly the part the interview question omits.** The load-bearing shadow is
  real and smaller than it looks: understand that a node is an object with a reference, so a
  thousand-element `LinkedList` is a thousand allocations scattered across the heap. That is one
  sentence, not an implementation.
- **Implementing a growable array.** `MyArrayList.java` is the argument: 28 lines, abandoned at a
  `//TODO: control size` on `:25`, with a `size()` at `:20-22` that returns `values.length` and so
  reports 10 when empty. The useful half of this topic is
  [`ArrayList`'s *actual* growth policy](#arraylist-growth-and-what-ensurecapacity-actually-saves) —
  1.5x, starts at 0, `ensureCapacity` has a short-circuit — and you get that by reading 20 lines of
  `ArrayList.java`, not by writing 200 of your own. [Doc 10](../modernization/10-sequenced-collections-and-httpclient.md)
  already owns the finish-it-then-delete-it exercise and that framing is the right one.
- **Red-black tree rotations.** They are in `HashMap.TreeNode` (`balanceInsertion`,
  `balanceDeletion`, and a `checkInvariants` at `HashMap.java:2534` that exists because even
  Doug Lea wanted a verifier) and in `TreeMap`. You will never write one. What you need from
  `TreeMap` is **the API you cannot get from a `HashMap`**: `floorKey`, `ceilingKey`, `headMap`,
  `subMap`, ordered iteration. Measured: `floorKey(25)` = 20, `subMap(15, 35)` = `{20=b, 30=c}`.
  Knowing those exist is worth more than knowing what a rotation is, and takes a minute.
- **Writing your own hash function.** `HashMap`'s Poisson note and its tree bins mean a bad
  `hashCode` costs you a factor of about two, not a factor of n. The contract (equal objects, equal
  hashes; stable while in a collection) is load-bearing. The *function* is `Objects.hash` or a
  record. The only time you write one is when profiling shows the hash itself is the hot spot, which
  will happen to approximately none of this repo.
- **Dijkstra / A\* by hand.** The honest reason is not "a library has it". It is that if you have a
  weighted-shortest-path problem you also have a graph that is loaded from somewhere, mutated
  concurrently, and larger than memory, and the algorithm is the 5% of that problem you can look up.
  Hand-rolling it means hand-rolling the `PriorityQueue` key-decrease workaround, and the fact that
  `PriorityQueue.remove(Object)` is linear (`PriorityQueue.java:74-76`) is the detail that turns a
  textbook O(E log V) into something slower.
- **DP tabulation.** The load-bearing shadow is **noticing** exponential recursion, and
  `PascalsTriangleRecursive.java:41-46` is a measured example — see stage 40 below. The fix is a
  `HashMap` cache or a loop; you need neither the word "tabulation" nor the 2-D array ritual. The
  reason the topic is interview-only is that real problems do not arrive pre-factored into optimal
  substructure, and recognising when they have been is a research skill, not an engineering one.
- **Union-find, tries, segment trees, Fenwick trees, manual AVL balancing, Floyd-Warshall, KMP.**
  Not one of them appears anywhere in this repo across eleven years. That is not proof they are
  useless, but it is evidence about the distribution of real work, and it is the only evidence
  available here. Learn them if you enjoy them. Do not learn them believing they are professional
  development.
- **Bit-twiddling.** `Integer.bitCount`, `Integer.highestOneBit`, `>>>` for the midpoint, and
  `Math.clamp` (Java 21, [doc 19](../modernization/19-randomness-and-numerics.md)) cover the real cases.
  `HashMap.tableSizeFor` is a nice thing to have read once.

## 5. Every existing file, mapped to a verdict

| File | Lines | Teaches | Verdict | Worth an exercise? |
| --- | --- | --- | --- | --- |
| `algorithm/BinarySearch.java` | 55 | binary search contracts, off-by-one | **load-bearing** | **Yes — the best one here.** Broken. |
| `algorithm/FairWorkLoad.java` | 81 | binary search on an answer space | **load-bearing** | **Yes.** The only non-trivial algorithm present. |
| `algorithm/StringRecursiveReversal.java` | 18 | recursion depth + quadratic allocation | **load-bearing** | **Yes.** Two real traps in four lines. |
| `algorithm/PascalsTriangleRecursive.java` | 49 | exponential recursion, memoisation | **load-bearing** | **Yes**, as the "measure before you optimise" case. |
| `algorithm/NumberReverse.java` | 23 | silent integer overflow | **load-bearing** | **Yes**, small. One test, `Math.*Exact`. |
| `algorithm/DecimalToBinary.java` | 26 | radix conversion; **prints hex, no test** | load-bearing as a *bug* | Owned by [doc 03](../modernization/03-pattern-matching.md). Cross-reference only. |
| `algorithm/SumSearch.java` | 34 | two-pointer on sorted input; argument mutation | **load-bearing**, for the side effect | **Yes**, as a precondition exercise. |
| `algorithm/DuplicateNumber.java` | 48 | arithmetic trick + unstated preconditions | mixed | **Yes**, as a *precondition* exercise, not an algorithm one. |
| `algorithm/MyArrayList.java` | 28 | growable array internals | **interview-only** | Owned by [doc 10](../modernization/10-sequenced-collections-and-httpclient.md). |
| `CircularList.java` | 112 | linked lists; API design | **interview-only** | Owned by [doc 10](../modernization/10-sequenced-collections-and-httpclient.md). |
| `algorithm/FindMiddleIndex.java` | 45 | equilibrium index, two-pointer | **toy** | No. Commented-out dead code at `:21-29`, `throws Exception` as control flow. |
| `algorithm/PascalsTriangle.java` | 56 | ASCII layout arithmetic | **toy** | No. It is a string-formatting exercise wearing a maths costume. |
| `The100DoorsCyberDojoTest` | 109 | O(n log n) loop vs a closed form; `+=` in a loop | **load-bearing** | **Yes.** `:89-107` asserts nothing at all. |
| `ProductsOfArrayElementsTest` | 67 | prefix/suffix products, O(n²) → O(n) | **load-bearing** | **Yes**, for the prefix-products idea. |
| `algorithm/RotateMatrix90DegreeInPlace.java` | 56 | in-place index arithmetic | **toy** | No, but it needs a home — see [doc 00](../modernization/00-start-here.md). |
| `mousemaze/` | 39 | grid traversal | n/a — **empty** | **Yes**, as greenfield. See the correction below. |
| `algorithm/L3_Synchronization_Udemy.java` | — | not DSA | n/a | No. Concurrency; [doc 07](../modernization/07-classic-concurrency.md). |

**The ones that are toys, plainly.** `FindMiddleIndex`, `PascalsTriangle` and
`RotateMatrix90DegreeInPlace` teach nothing a working developer will use. `FindMiddleIndex` is three
lines of real logic wrapped in commented-out alternatives and a checked `Exception` used as a return
value. `PascalsTriangle` spends 50 of its 56 lines computing how many spaces to print, which is why
`:17` reads `Long.valueOf(Math.round(Math.pow(2d, numRows))*10).toString().length()`.
`RotateMatrix90DegreeInPlace` is a studied blog solution with a `main` and no assertions. Leave all
three alone, or delete them; do not build stages on them.

### Corrections to the brief's inventory

- **`mousemaze/` has no implementation and no tests.** `src/main/java/.../mousemaze/` contains only
  `Rules.txt`. `src/test/java/.../mousemaze/MazeTest.java` (16 lines) and `CellTest.java` (23 lines)
  contain **zero `@Test` methods** — they are TODO comments. It is a greenfield slot with a
  written spec, which is better than legacy code for a graph stage, but it is not "grid traversal
  with tests".
- **`BinarySearchTest` passes.** Verified by running `mvn -Dtest=BinarySearchTest test`: green, eight
  tests. That is what makes it worth a stage.

### The two files with verified bugs nobody has written down

**`algorithm/BinarySearch.java` cannot find five of the twelve elements in its own fixture.** Run
verbatim against `BinarySearchTest.sequence = {0,5,13,19,22,41,55,68,72,81,85,98}`, searching for
each element in turn:

    value   0 at index  0 -> 1    ok          value  55 at index  6 -> 7    ok
    value   5 at index  1 -> -1   WRONG       value  68 at index  7 -> 8    ok
    value  13 at index  2 -> 3    ok          value  72 at index  8 -> -1   WRONG
    value  19 at index  3 -> -1   WRONG       value  81 at index  9 -> 10   ok
    value  22 at index  4 -> 5    ok          value  85 at index 10 -> -1   WRONG
    value  41 at index  5 -> -1   WRONG       value  98 at index 11 -> 12   ok

The cause is a **hybrid convention**: `:17` sets `high = sequence.length` (exclusive upper bound),
while `:52` does `high = mid - 1` (the inclusive-bound update). With an exclusive bound the correct
update is `high = mid`, so `high = mid - 1` discards index `mid - 1` along with `mid`.

Two more facts about the same 55 lines:

- **It throws on an empty array.** `withInRange()` at `:32-34` is
  `low < high || isFirstElement()`, and `isFirstElement()` at `:36-38` is `low == 0 && high == 0` —
  a special case added so a single-element array works. For a zero-length array it enters the loop
  and `sequence[0]` throws `ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0`.
  Verified.
- **It returns a one-based index** (`mid + oneBased`, `:22`) and `-1` for not-found, so it matches
  neither `Arrays.binarySearch`'s zero-based result nor its `-(insertion point) - 1` encoding. The
  whole test file is written to the one-based convention, so the convention is deliberate and
  undocumented.
- **`low`, `high` and `mid` are mutable instance fields** (`:11-13`), so one `BinarySearch` instance
  cannot be shared between threads. `BinarySearchTest` constructs a fresh one per test at `:38-41`,
  which hides it.

The eight existing tests pass because they only ever probe indices 0, 6 and 11 plus one genuine miss.
**A parameterised test over every element of the fixture is four lines and turns eight green tests
into five red ones.** That is the single best exercise available in this repo.

**`algorithm/FairWorkLoad.java:61-65` — `max()` does not compute a maximum.**

    private int max(int[] sequence){
        int max = sequence[0];
        for(int value:sequence) if(value>0) max = value;   // assignment, not comparison
        return max;
    }

It returns the **last positive element**. Verified: `max({5,3,1})` = 1, `max({9,9,2,1})` = 1,
`max({5,3,0})` = 3. It is right whenever the input happens to be ascending, which sample data often
is. **This document does not claim that is the cause of
`FairWorkloadTest.testMaxNumPerSixWorkers`**, which has been `@Disabled` since 2014 at
`FairWorkloadTest.java:114` and belongs to [doc 09](../modernization/09-streams-and-optional.md). It is stated here as
a verified fact about three lines of code and as the pattern worth generalising: **the binary search
skeleton is correct and the untested helper beside it is not.**

---

# Proposed track: nine stages

Numbering continues the append-only scheme — `34` is currently the last document, so this would be
**`35`–`43`**. Each stage lands on a named file. **None of these exist. No Java file, no test, and
no stage document has been written.** When they are, the convention from
[doc 00](../modernization/00-start-here.md) applies: the exercise starts by deleting a
`@Disabled("Exercise: docs/NN-….md. …")` annotation, and each stage ends with a
**"What got worse"** section and **Prompts:**.

### 35 — Which collection, and what it costs

- **Lands on:** `CircularList.java`, `algorithm/MyArrayList.java`, `The100DoorsCyberDojoTest:45-51`.
  Read [doc 10](../modernization/10-sequenced-collections-and-httpclient.md) first; it owns the rewrite.
- **Exercise:** mostly paper, one measurement. Build a decision table — `ArrayList`, `ArrayDeque`,
  `HashMap`, `LinkedHashMap`, `TreeMap`, `HashSet`, `PriorityQueue` — with the operations each makes
  cheap, the guarantee each gives on iteration order, and the one it does *not*. Then reproduce the
  `LinkedList` middle-insert measurement and say why Big-O predicted a tie.
- **Teaches:** the highest-value item in the whole track, and it requires implementing nothing. Also
  `HashMap.newHashMap(n)` versus `new HashMap<>(n)`, and `TreeMap`'s navigation API as the actual
  reason to reach for a sorted map.
- **Trap a test can catch:** assert the iteration order of a `PriorityQueue` with nine elements. Four
  elements passes, nine fails.

### 36 — Accidental quadratics, and where the crossover actually is

- **Lands on:** `FairWorkLoad.java:30,39`, `The100DoorsCyberDojoTest:83`,
  `StringRecursiveReversal.java:11`, `ProductsOfArrayElementsTest:60-66`.
- **Exercise:** write a parameterised test that runs the same operation at n = 100, 1,000 and 10,000
  and asserts a *ratio* rather than a time. Find the four shapes in the repo. Then fix exactly one
  and justify leaving the others — `The100DoorsCyberDojoTest:83` should be left alone with a comment.
- **Teaches:** that the quadratic is invisible at test sizes and fatal at production sizes, and that
  "n is 100, it does not matter" is a correct and professional answer. Re-answer it with JMH per
  [doc 34](../modernization/34-measuring-it-properly.md) and see which of your wall-clock numbers survive.
- **Trap a test can catch:** the ratio assertion. A timing assertion is flaky; a ratio assertion
  across two input sizes is not, and it is the only kind of performance test worth committing.

### 37 — Binary search: the contract, and the bug in `BinarySearch.java`

- **Lands on:** `algorithm/BinarySearch.java` and `BinarySearchTest`.
- **Exercise:** **bug first.** Add a `@ParameterizedTest` over every element of
  `BinarySearchTest.sequence` and watch eight green tests become five red ones. Add the empty-array
  case. Fix the convention — pick exclusive-or-inclusive and hold it. Then rewrite the signature to
  the JDK contract (zero-based, `-(insertion point) - 1`) and see what it does to the test file.
- **Teaches:** that a passing test suite is evidence about the test suite; that mixed bound
  conventions are the root cause of most binary search bugs; the `-(ip)-1` encoding and `~r`; why
  "sorted input" is a precondition with no runtime check.
- **Trap a test can catch:** all of it, which is the point.

### 38 — Binary search over an answer space

- **Lands on:** `algorithm/FairWorkLoad.java`, and the formal condition already written in
  `BinarySearchTest:17-22`.
- **Exercise:** paper plus one small refactor. Name the search space (`max(folders)` to
  `sum(folders)`), name the predicate ("can `workers` people each carry at most `median`?"), and
  prove it is monotone. Extract the predicate into its own tested method. Then write the same shape
  for a different problem — smallest batch size that completes inside a timeout.
  **Do not touch `FairWorkloadTest`**; the `@Disabled` test belongs to
  [doc 09](../modernization/09-streams-and-optional.md).
- **Teaches:** the one genuinely non-obvious technique that survives the interview-only filter.
  Separately: that `FairWorkLoad.java:61-65` is a three-line helper with no test and a comparison
  that is an assignment, and that is where bugs live.
- **Format:** "Write the answers here", since most of it is paper.

### 39 — `equals`, `hashCode`, and what `HashMap` does when you get it wrong

- **Lands on:** `Door.java`, `concurrency/Order.java`, `CircularList.java` (no `equals` at all),
  `DuplicateNumber.java:35-36`.
- **Exercise:** write a key class with `equals` and no `hashCode`, then assert that
  `List.contains` finds it and `HashMap.get` does not, and that putting an equal key twice gives
  `size() == 2`. Then make the key mutable, insert it, mutate it, and assert that `containsKey` is
  false while `keySet()` still shows it. Then fix both with a record and say what the record did and
  did not fix.
- **Teaches:** the contract as a *verified* failure rather than a rule; why records are the default
  answer; `Objects.hash`; why `List` and `Map` disagree. Also the `List.remove(int)` versus
  `List.remove(Object)` overload, measured: `remove(1)` removes index 1, `remove(Integer.valueOf(20))`
  removes the value.
- **Trap a test can catch:** the `size() == 2` assertion. Nothing else makes the failure visible.

### 40 — Recursion depth, and the exponential nobody measured

- **Lands on:** `StringRecursiveReversal.java:11`, `PascalsTriangleRecursive.java:41-46`.
- **Exercise:** find where `reverseString` dies on your JVM, then find where it dies under
  `-Xss256k` and `-Xss4m`, and write down all three. Then instrument
  `PascalsTriangleRecursive.cellValue` with a call counter and tabulate it by row. Add a `HashMap`
  cache and tabulate again. Decide whether `printPascal(6)` justified either change.
- **Teaches:** that there is no safe recursion depth, only a measured one, and that `-Xss` differs
  between laptop, container and CI. Verified reference numbers:

      StringRecursiveReversal       ok at n=16,000, StackOverflowError at n=20,000 (default -Xss)
      trivial frames to overflow    1,781 / 21,560 / 207,869 at -Xss256k / default / -Xss4m
      cellValue calls, row 6        120           0.01 ms
      cellValue calls, row 22       8,388,584     12.6 ms
      cellValue calls, row 30       2,147,483,616 3,241 ms
      cellValue calls, row 34       34,359,738,332 52,042 ms

  The honest conclusion is both halves at once: **at the repo's own input (`printPascal(6)`,
  120 calls, 0.01 ms) memoisation is not worth writing**, and the curve says row 30 is unusable. That
  is the shape of every real optimisation decision.
- **Trap a test can catch:** assert that reversing a 50,000-character string does not throw. It does.

### 41 — Integer overflow is an algorithm bug

- **Lands on:** `NumberReverse.java:9-17`, `DuplicateNumber.java:14-22`,
  `ProductsOfArrayElementsTest:46-52`, `BinarySearch.java:40`, `FairWorkLoad.java:74`.
- **Exercise:** write the first test `NumberReverse` has ever had, with `Integer.MAX_VALUE` as input.
  Verified expected failure: it returns `-1126087180`. Decide between `long`, `Math.multiplyExact`
  and a documented precondition, and say which you picked and why. Then state
  `DuplicateNumber`'s unstated preconditions — exactly the values 1..n with one repeat — and find
  the n at which its Gauss formula overflows. Then look at `low + (high - low) / 2` in
  `BinarySearch.java:40` and explain why it is already correct.
- **Teaches:** that overflow is silent, that `Math.*Exact` is the Java 8 fix, and that an algorithm
  with unstated preconditions is not an algorithm. Cross-reference
  [doc 03](../modernization/03-pattern-matching.md), which owns the `DecimalToBinary` hex bug, and say what
  `Integer.toString(n, radix)` and `HexFormat` (Java 17) do to the whole family.
- **Trap a test can catch:** all three, with one input each.

### 42 — Reachability, not Dijkstra

- **Lands on:** `mousemaze/Rules.txt`, and the two empty test classes
  `mousemaze/MazeTest.java` and `mousemaze/CellTest.java`. Greenfield with a written spec.
- **Exercise:** implement BFS shortest path over the maze grid with a queue and a visited set —
  `ArrayDeque`, not `LinkedList`, and say why. Then implement the same traversal depth-first
  *recursively* and find the grid size at which it overflows the stack (stage 40's lesson, applied).
  Then write one paragraph on what you would need to add for weights, and conclude that you would
  reach for a library.
- **Teaches:** the one graph technique that shows up in real work, under the names it actually
  appears as — dependency order, reachability, cycle detection in parent links. And the reason
  Dijkstra is interview-only, argued from the attempt rather than asserted.
- **Trap a test can catch:** DFS without a visited set loops forever on a cycle; BFS without one
  revisits. Assert the visited count equals the reachable cell count.

### 43 — What we are deliberately not teaching

- **Lands on:** nothing. Paper only, written last.
- **Exercise:** "Write the answers here". For each of red-black rotations, your own hash function,
  Dijkstra by hand, DP tabulation, and implementing a linked list: state the verdict, the reason, and
  **the one-sentence load-bearing shadow** that survives the verdict. Then do the harder half —
  name two items this document calls interview-only that you think are load-bearing, and defend
  them against the repo's own evidence.
- **Teaches:** the thing the whole track is for. A list of topics you can name is worth less than one
  topic you can decline to learn, with a reason.
- **Format:** numbered "Write the answers here" list, `_(fill in)_`, no code.

### Stage dependency order

`35` → `36` → `37` → `38` are a chain: collection costs, then the quadratics that follow from
getting them wrong, then the binary search contract, then the one technique worth having. `39`, `40`
and `41` are independent and can be taken in any order. `42` needs `40`. `43` is last by definition.
`36` should be re-answered after [doc 34](../modernization/34-measuring-it-properly.md), the same way doc 34
invalidates stages 06, 08, 14, 22 and 31.

### What needs which JDK

Everything here compiles at **`--release 21`**, the repo's original floor, with two exceptions worth
naming in the stage docs: `HashMap.newHashMap` is Java 19, and `HexFormat` is Java 17. `Math.clamp`
(Java 21) comes up in stage 41. Nothing needs preview features and nothing needs the build changed.

---

## What I could not verify

- **Allocation and cache-miss counts.** Every claim about `LinkedList`'s memory behaviour — node
  size, heap scatter, cache misses — is inference from the measured timings and from
  `LinkedList.Node`'s three fields. The actual per-node footprint depends on compact object headers
  (JEP 450/519, default in Java 27 per [the index](../modernization/INDEX.md)) and was not measured. Marked as
  inference, not fact.
- **Whether `-Xss` defaults differ across the platforms this repo is run on.** Measured on this Linux
  x86-64 JVM only: 21,560 trivial frames. The *conclusion* ("there is no safe constant") is
  strengthened, not weakened, by not knowing — but the specific numbers are one machine's.
- **The precise `StackOverflowError` boundary for `reverseString`.** Bracketed between 16,000 and
  20,000 characters on this JVM; not bisected, and it will move with JIT state, since an inlined or
  OSR-compiled frame is a different size from an interpreted one. Treat the number as an
  order of magnitude.
- **All timings.** Wall-clock, one JVM, best-of-3 after warmup. [Doc 34](../modernization/34-measuring-it-properly.md)
  explains why that is not a measurement. They are used only for ratios above 10x. The
  `ArrayDeque.poll()` row going *down* from n = 50,000 (1.15 ms) to n = 100,000 (0.774 ms) is visible
  proof that the harness is noisy at the sub-millisecond end, and it is left in the table on purpose.
- **Whether the `Collections.binarySearch` 5,000 threshold is still a good value.** The intent *is*
  documented, at `Collections.java:94-107`: "Often, the random access variant yields better
  performance on small sequential access lists … The values below were empirically determined to
  work well for `LinkedList`." The measured 4,999-versus-5,000 inversion contradicts that claim on
  this machine, which may say more about 2026 cache hierarchies than about the original decision.
  The behaviour is verified; whether the threshold is simply stale is not.
- **`PriorityQueue` iteration order as a stable fact.** It is heap array order *today*. The javadoc
  explicitly declines to specify it, so the measured sequence `[1,2,3,4,8,9,7,5,6]` must not be
  asserted in a test — which is itself the lesson.
- **Nothing was run on a JDK other than 26.0.2.1.** Where a constant could plausibly have changed
  (`TREEIFY_THRESHOLD`, `BINARYSEARCH_THRESHOLD`, `DEFAULT_CAPACITY`) the source line is cited so a
  future reader can re-check rather than trust.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: this document says nine stages are worth writing and calls three existing files toys —
if you had to cut it to **three** stages, which three, and what does the cut tell you about the
other six? The `ArrayList.contains` row costs 2,852 ms at n = 100,000 and 0.015 ms at n = 100: given
that you usually cannot know n at the time you write the loop, is "always use a `HashSet`" a better
rule than the judgement this document advocates, and what does that cost in readability? You have
just argued that implementing a linked list is interview-only — explain that to an interviewer who
has asked you to implement one, in a way that does not lose you the job, and then say whether your
answer was honest. `HashMap` degrades gracefully on bad hash codes by design; name the thing you
have stopped checking as a result, and what would have to be true for that to bite you. Stage 36
tells you to leave `The100DoorsCyberDojoTest:83` quadratic with a comment — write that comment, then
decide whether the next person will believe it or "fix" it. Doc 34 says every wall-clock number in
this document is suspect: pick the one table here you would actually re-measure with JMH, and say
what decision would change if the number moved by 30%.
