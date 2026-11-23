# 14. `Map` default methods, comparators and collectors

**Java 8** · stage 3 of [the release-ordered track](INDEX.md) · compiles at `--release 21`, no
build change

> **No safety net:** `FindDups` has no test. Write one first, in a separate commit, asserting
> what the code does today — bugs included. [`../testing-first.md`](../testing-first.md) says how,
> and why the order matters. `WordTally` is new code with its tests already written.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `WordTally`](#exercise-1-wordtally-five-ways-to-count) | Count words five ways: TODOs 1–5. | T2–T6 | Already written: delete `@Disabled` to begin. |
| [2. `FindDups`](#exercise-2-finddups-the-question-it-never-answered) | Add the counts it never reported, case-insensitively. Three commits. | T4 | Characterize first, with mixed-case input. |
| [3. `FairWorkLoad`](#exercise-3-fairworkload-and-the-right-to-say-no) | Say no, in one line. | T4 | None. |
| [4. What got worse](#exercise-4-fill-in-what-got-worse) | Write down what these APIs cost. | all | None. |
| [5. Review questions](#exercise-5-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the six sections in order: each one builds on the one before. Here is the whole story in one
paragraph, so you know where it's going.

> Java 8 needed to add methods to `Map` without breaking every class that already implemented it,
> so interfaces got method bodies: default methods (T1). The new `Map` methods are short, but each
> one's contract says exactly when your function runs, and the names mislead, especially around
> `null` (T2). Two of them do most of the work: `merge` for combining a new value with an old one,
> `computeIfAbsent` for creating a value once and reusing it (T3). Collectors do the same jobs on
> a stream, and the collector *inside* `groupingBy` decides what each group becomes (T4).
> `toMap` looks like `put` and isn't: it throws on a repeated key (T5). And sorting the result
> means building a comparator from parts, where the position of `reversed()` changes the answer
> and can break type inference (T6).

That gives one rule, and everything below follows from it:

> **Each of these methods' contracts says exactly when your function runs and when it doesn't.
> Read the contract, not the name.**

Every trap in this doc is a method doing what its javadoc says, which wasn't what its name
suggested.

Every code sample below is a complete program unless it says otherwise. Save it as the class name
plus `.java` and run it with `java Name.java`; no build needed. Outputs marked *verified* were run
on JDK 26, and every sample also compiles at `--release 21`, this stage's level.

### T1. Default methods: how `Map` got new methods without breaking anyone

Before Java 8, an interface could only declare methods; every class implementing it had to write
them all. That made interfaces impossible to grow. Add `getOrDefault` to `Map`, and every `Map`
implementation ever written, in every library, stops compiling.

Java 8's answer is the **default method**: an interface method with a body. Classes that
implement the interface get it for free, and may override it if they know better.

```java
public class Shapes {
    interface Shape {
        double area();                            // abstract: every implementation must write it

        default String describe() {               // default: comes with a body, so old classes still compile
            return getClass().getSimpleName() + " with area " + area();
        }
    }

    record Square(double side) implements Shape {
        public double area() { return side * side; }
    }

    record Circle(double radius) implements Shape {
        public double area() { return Math.round(Math.PI * radius * radius * 100) / 100.0; }
        @Override public String describe() { return "a circle, r=" + radius; }   // may override it
    }

    public static void main(String[] args) {
        System.out.println(new Square(3).describe());
        System.out.println(new Circle(1).describe());
    }
}
```

```text
Square with area 9.0                                     (verified 2026-11-23)
a circle, r=1.0
```

That's how `Map` gained `getOrDefault`, `putIfAbsent`, `merge`, `computeIfAbsent`, `compute`,
`forEach` and `replaceAll` in Java 8. They're written once, in the interface, in terms of `get`,
`put` and `remove`. (`HashMap` and `ConcurrentHashMap` override several of them with faster or
atomic versions.)

### T2. Absent, present, or `null`: read the contract

Each new `Map` method has to decide what to do with a key that is **absent**, a key that is
**present**, and a key that is present but **mapped to `null`**. They don't all decide the same
way. Here are five of them, on a `HashMap`:

```java
import java.util.HashMap;
import java.util.Map;

public class Contracts {
    public static void main(String[] args) {
        Map<String, Integer> m = new HashMap<>();

        m.put("x", null);                                         // present, but mapped to null
        System.out.println("getOrDefault(x, 0)   -> " + m.getOrDefault("x", 0));
        System.out.println("putIfAbsent(x, 1)    -> " + m.putIfAbsent("x", 1) + ", now x=" + m.get("x"));

        m.put("k", 1);
        System.out.println("merge(k, 100, +)     -> " + m.merge("k", 100, (o, n) -> o + n));
        System.out.println("merge(j, 100, +)     -> " + m.merge("j", 100, (o, n) -> {
            throw new AssertionError("never called: j was absent");
        }));

        m.put("y", 5);
        System.out.println("merge(y, 1, ->null)  -> " + m.merge("y", 1, (o, n) -> null)
                + ", containsKey(y)=" + m.containsKey("y"));

        try {
            m.merge("z", null, Integer::sum);
        } catch (NullPointerException e) {
            System.out.println("merge(z, null, sum)  -> NullPointerException");
        }

        System.out.println("computeIfAbsent(q, ->null) -> " + m.computeIfAbsent("q", key -> null)
                + ", containsKey(q)=" + m.containsKey("q"));
    }
}
```

```text
getOrDefault(x, 0)   -> null                             (verified 2026-11-23)
putIfAbsent(x, 1)    -> null, now x=1
merge(k, 100, +)     -> 101
merge(j, 100, +)     -> 100
merge(y, 1, ->null)  -> null, containsKey(y)=false
merge(z, null, sum)  -> NullPointerException
computeIfAbsent(q, ->null) -> null, containsKey(q)=false
```

Line by line:

- **`getOrDefault` and `putIfAbsent` disagree about `null`.** `getOrDefault` sees that `x` is
  present and returns its `null`, not the default. `putIfAbsent` treats a `null` value as absent:
  it returns `null` and **stores** 1.
- **`merge(key, value, fn)`** puts `value` if the key is absent, and otherwise stores
  `fn(old, value)`. So the second argument is only used when the key is **absent**, and the
  function is only called when the key is **present**: `fn(1, 100)` gave 101 for `k`, and the
  function for `j` was never called (it would have thrown). Nearly everyone reads it the other way
  round the first time.
- **A `merge` function that returns `null` removes the key.** `y` is gone.
- **`merge` refuses a `null` value**: `NullPointerException`.
- **`computeIfAbsent(key, fn)`** calls `fn(key)` only if the key is absent, stores the result, and
  returns whatever is now mapped. If `fn` returns `null`, it stores nothing: the map stays as it
  was.

Not one of those is what you'd guess from the name alone.

### T3. `merge` combines; `computeIfAbsent` creates

Those two are the workhorses, and they're for different jobs. **`merge`** is for "combine this new
value with whatever is there": totals, maximums, concatenations. **`computeIfAbsent`** is for
"make sure there's a value here, creating it the first time, and give it to me": usually a
mutable container you then add to.

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MergeAndCompute {
    record Sale(String customer, int amount) { }

    public static void main(String[] args) {
        // merge: combine a new value with the old one. Total spent per customer.
        List<Sale> sales = List.of(new Sale("ana", 30), new Sale("bo", 5),
                                   new Sale("ana", 12), new Sale("cy", 7), new Sale("bo", 20));
        Map<String, Integer> spent = new TreeMap<>();
        for (Sale s : sales) {
            spent.merge(s.customer(), s.amount(), Integer::sum);  // absent: store amount; present: old + amount
        }
        System.out.println("spent:    " + spent);

        // computeIfAbsent: create the value once, then reuse it. A multimap.
        List<String> words = List.of("fig", "pear", "fig", "apple", "pear", "fig", "kiwi");
        Map<Character, List<String>> byLetter = new TreeMap<>();
        for (String w : words) {
            byLetter.computeIfAbsent(w.charAt(0), k -> new ArrayList<>()).add(w);
        }
        System.out.println("byLetter: " + byLetter);
    }
}
```

```text
spent:    {ana=42, bo=25, cy=7}                          (verified 2026-11-23)
byLetter: {a=[apple], f=[fig, fig, fig], k=[kiwi], p=[pear, pear]}
```

The second half is what `computeIfAbsent` is actually for, the **multimap**. The function only
runs once per key, to create the list. Every later call returns the same list, and `.add(w)`
mutates it. That's the idiom that makes Guava's `Multimap` unnecessary for most code.

Notice what `computeIfAbsent` *can't* do on its own: change a value that's already there. Its
function never runs for a present key. Exercise 1, TODO 2 makes you work around that, on purpose.

### T4. Collectors: `groupingBy`, and the collector inside it

A loop with `merge` or `computeIfAbsent` builds a map one element at a time. A stream does the
same job with a **collector** ([doc 09](09-streams-and-optional.md) introduced them), and the one
for building maps of groups is `groupingBy`. It takes two decisions:

- a **classifier**: the function that picks each element's key;
- a **downstream collector**: the collector inside it, which decides what each group becomes. In
  `groupingBy(f, counting())`, `counting()` is the downstream.

Same cities, same classifier (the name's length), four downstreams:

```java
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class Downstream {
    public static void main(String[] args) {
        List<String> cities = List.of("Oslo", "Rome", "Lima", "Paris", "Quito", "Bern", "Tokyo", "Cairo", "Bogota");

        // The classifier picks the key; the downstream collector decides what each group becomes.
        Map<Integer, List<String>> lists = cities.stream()
                .collect(Collectors.groupingBy(String::length, TreeMap::new, Collectors.toList()));
        Map<Integer, Long> counts = cities.stream()
                .collect(Collectors.groupingBy(String::length, TreeMap::new, Collectors.counting()));
        Map<Integer, Integer> letters = cities.stream()
                .collect(Collectors.groupingBy(String::length, TreeMap::new,
                        Collectors.summingInt(String::length)));
        Map<Integer, String> joined = cities.stream()
                .collect(Collectors.groupingBy(String::length, TreeMap::new, Collectors.joining("/")));

        System.out.println("toList():     " + lists);
        System.out.println("counting():   " + counts + "  value type " + counts.get(4).getClass().getSimpleName());
        System.out.println("summingInt(): " + letters + "  value type " + letters.get(4).getClass().getSimpleName());
        System.out.println("joining(/):   " + joined);
    }
}
```

```text
toList():     {4=[Oslo, Rome, Lima, Bern], 5=[Paris, Quito, Tokyo, Cairo], 6=[Bogota]}   (verified 2026-11-23)
counting():   {4=4, 5=4, 6=1}  value type Long
summingInt(): {4=16, 5=20, 6=6}  value type Integer
joining(/):   {4=Oslo/Rome/Lima/Bern, 5=Paris/Quito/Tokyo/Cairo, 6=Bogota}
```

Three things to take from it:

- **The middle argument, `TreeMap::new`, chooses the map.** Without it you get a `HashMap`, whose
  printed order isn't something you can rely on.
- **`counting()` gives `Long`.** It's the downstream that fixes the value type, not the
  classifier. If you want `Integer`, a different downstream such as `summingInt(x -> 1)` gives it.
- **The key decides what "the same" means.** Everything with the same key goes in one group. If
  the key is the word itself, then `Cat` and `cat` are different groups, which matters in
  exercise 2.

### T5. `toMap` is not `put`: the duplicate key

`Collectors.toMap(keyFn, valueFn)` builds a map with one entry per element. It looks like a stream
of `put` calls. It isn't: `put` silently overwrites a repeated key, and two-argument `toMap`
**throws**, because it has nowhere to put a collision. The three-argument form takes a **merge
function** that says what to do when two elements land on the same key:

```java
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ToMap {
    record Person(String name, String city) { }

    public static void main(String[] args) {
        List<Person> people = List.of(new Person("ana", "Oslo"), new Person("bo", "Rome"),
                                      new Person("cy", "Oslo"));

        try {
            Map<String, String> byCity = people.stream()
                    .collect(Collectors.toMap(Person::city, Person::name));   // two arguments: strict
            System.out.println(byCity);
        } catch (IllegalStateException e) {
            System.out.println("strict:  " + e.getMessage());
        }

        Map<String, String> firstWins = people.stream()
                .collect(Collectors.toMap(Person::city, Person::name,
                        (earlier, later) -> earlier,                        // on a clash, keep the first
                        TreeMap::new));
        Map<String, String> bothKept = people.stream()
                .collect(Collectors.toMap(Person::city, Person::name,
                        (earlier, later) -> earlier + "+" + later,          // on a clash, keep both
                        TreeMap::new));
        System.out.println("first wins: " + firstWins);
        System.out.println("both kept:  " + bothKept);
    }
}
```

```text
strict:  Duplicate key Oslo (attempted merging values ana and cy)   (verified 2026-11-23)
first wins: {Oslo=ana, Rome=bo}
both kept:  {Oslo=ana+cy, Rome=bo}
```

This kind of bug passes every test written against distinct sample data, then fails in production
the first time a key repeats. **The sample data is the bug.** And with the lenient form, the merge
function is a decision, not boilerplate: whichever one you pass, say in a comment which value
wins.

### T6. Comparators from parts: where `reversed()` goes

Sorting a result usually means "by this, then by that". A **comparator combinator** builds that
comparator out of smaller ones: `comparing` (or `comparingInt`), `thenComparing`, `reversed`. The
trap is that `reversed()` reverses **everything built so far**, not just the last part.
[Doc 12](12-lambdas-and-method-references.md) introduced it; here it is on a ranking that wants
highest score first, ties by name A to Z:

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Ranking {
    record Player(String name, int score) { }

    public static void main(String[] args) {
        List<Player> players = List.of(new Player("dee", 7), new Player("al", 9),
                                       new Player("cat", 7), new Player("bob", 9));

        // Wanted: highest score first, ties by name A to Z.
        Comparator<Player> right = Comparator.comparingInt(Player::score).reversed()
                .thenComparing(Player::name);
        Comparator<Player> wrong = Comparator.comparingInt(Player::score)
                .thenComparing(Player::name).reversed();             // reverses the tie-break too

        List<Player> a = new ArrayList<>(players);
        a.sort(right);
        List<Player> b = new ArrayList<>(players);
        b.sort(wrong);
        System.out.println("reversed() then thenComparing: " + a.stream().map(Player::name).toList());
        System.out.println("thenComparing then reversed(): " + b.stream().map(Player::name).toList());
    }
}
```

```text
reversed() then thenComparing: [al, bob, cat, dee]       (verified 2026-11-23)
thenComparing then reversed(): [bob, al, dee, cat]
```

Both compile, both put the 9s before the 7s, and only the ties differ. **Only a test with a tie in
it can tell them apart.**

**The second trap is type inference.** Java works out a generic method's type arguments from the
*target type*: the variable or parameter the result is assigned to. Chain a method onto the call,
and the inner call is resolved *before* there's a target, so Java falls back to `Object`. This
program doesn't compile, on purpose:

```java
import java.util.Comparator;

public class Infer {
    record Player(String name, int score) { }

    public static void main(String[] args) {
        Comparator<Player> ok = Comparator.comparingInt(p -> p.score());          // target type known: fine
        Comparator<Player> broken = Comparator.comparingInt(p -> p.score()).reversed();
    }
}
```

```text
Infer.java:8: error: cannot find symbol                  (verified 2026-11-23)
        Comparator<Player> broken = Comparator.comparingInt(p -> p.score()).reversed();
                                                                  ^
  symbol:   method score()
  location: variable p of type Object
```

The first line infers fine because the call *is* the assigned value. In the second, `.reversed()`
is called on the result of `comparingInt(...)`, so that call has no target to learn from, and `p`
becomes an `Object`. The fixes are to give the lambda's type (`(Player p) -> p.score()`), use a
method reference (`Player::score`, as `Ranking` does), or give the type arguments explicitly with
a **type witness**: `Comparator.<Player>comparingInt(...)`. Exercise 1 meets this on
`Map.Entry`, where it's harder to see.

## Practice

### Exercise 1: `WordTally`, five ways to count

`collections/WordTally.java` is new code. `WordTallyTest` has ten tests on
`fig pear fig apple pear fig kiwi`. Delete its `@Disabled` to begin. They all pass against a
scratch implementation (verified), so a failure is yours, not the test's.

**TODOs 1–3 all produce `{fig=3, pear=2, apple=1, kiwi=1}`.** The point is the differences.

1. **TODO 1, `merge`.** Given T2 and T3, this is one line in a loop.
2. **TODO 2, `computeIfAbsent`.** It's the wrong tool for counting, and that's why it's a TODO.
   Its function only runs when the key is missing, so it can't increment anything (T3). Make it
   work anyway and notice what you had to add. **Choosing between TODO 1 and TODO 2 for a given
   job is the deliverable of this part.**
3. **TODO 3, `groupingBy` with `counting()`.** The test expects `Map<String, Long>`, because
   `counting()` returns a `Long` (T4). **A correction to the old version of this doc:** it said
   you "cannot ask it for `Integer`". You can: `groupingBy(w -> w, summingInt(w -> 1))` gives
   `Map<String, Integer>` (verified). The signature here fixes `Long`, so the question is which
   you'd choose at an API boundary, and why `counting()` returns `Long` in the first place.

**TODO 4, the `toMap` trap** (T5). Read `strict_to_map_rejects_a_duplicate_key` before you write
anything. Two-argument `Collectors.toMap` throws on a repeated key (verified):

```text
IllegalStateException: Duplicate key fig (attempted merging values 3 and 3)
```

Everyone expects it to overwrite, because `Map.put` does. It doesn't: it has nowhere to put a
collision. It's the same failure as `ArrayUtils.removeElement` in
[doc 09](09-streams-and-optional.md): *the sample data is the bug*. The lenient version takes a
merge function. Whichever one you pass, say in a comment which value wins.

**TODO 5, `topWords`** (T6). Most frequent first, ties alphabetical ascending. It's the
`reversed()` placement trap from [doc 12](12-lambdas-and-method-references.md), moved onto
`Map.Entry`, where it's harder to see. `apple` and `kiwi` both occur once. One natural chain gives
`[fig, pear, apple, kiwi]` and another, with `reversed()` in a different place, gives
`[fig, pear, kiwi, apple]` (verified). Both compile. Only a test with a tie in it can tell them
apart, which is why `top_words_break_ties_alphabetically_ascending` exists.

On the way you'll meet a type-inference error. **A correction to the old version:** it said
`Map.Entry.comparingByValue()` "needs that explicit type witness and will not infer". Assigned
directly to a `Comparator<Map.Entry<String, Long>>`, it infers fine (verified). It fails only when
you chain a method onto it, because Java infers type arguments from the target type, and
`.reversed()` is called before there is a target (verified; T6 shows the same thing on a lambda):

```java
l.sort(Map.Entry.comparingByValue().reversed());
// error: ... found: Comparator<Entry<Object,V#1>>
```

`Map.Entry.<String, Long>comparingByValue()` is the fix, and now you know why it's needed.

**Done when:** all ten `WordTallyTest` tests pass with `@Disabled` deleted, and you've written down
which of TODO 1 and TODO 2 you'd use for counting and why, and which value your lenient `toMap`
keeps.

### Exercise 2: `FindDups`, the question it never answered

`collections/FindDups.java:20-25` reports "Duplicate detected: cat" but never how many times.
[Doc 09](09-streams-and-optional.md) left exactly that question here.

**A correction to the old version:** it said doc 09 asks you to turn `FindDups` into
`distinct().count()`. Doc 09 now shows that `distinct()` is wrong for this file, because it's
case-sensitive and the original isn't. **The same trap is waiting here** (T4: the key decides what
"the same" means). Group `Cat dog cat Dog bird` by the word itself and you get **five** groups of
one (verified), while the original reports three distinct words:

```java
groupingBy(w -> w, counting())             // {bird=1, cat=1, Cat=1, Dog=1, dog=1}
groupingBy(w -> w, TreeMap::new, counting()) // sorted, but still 5 keys
```

Commits, in this order:

1. **Characterize**, as [`../testing-first.md`](../testing-first.md) describes. If you did doc 09
   first, the test exists. Make sure it has the mixed-case input.
2. **Add the counts**: a method returning word → occurrences, case-insensitive, matching the
   original's idea of "the same word". Decide which spelling is kept as the key and test it. That
   decision is a behaviour you choose, not syntax you translate.
3. **Decide what `main` prints**, now that it can say "cat ×2" instead of "Duplicate detected".

**Done when:** the three commits are in, the test with mixed-case input passes, and the spelling
kept as the key is a tested decision.

### Exercise 3: `FairWorkLoad`, and the right to say no

[Doc 09](09-streams-and-optional.md) part three already covers this file: its inner loop carries
state from one element to the next, so it isn't a stream, and converting its two helpers before
diagnosing `FairWorkloadTest.testMaxNumPerSixWorkers` would fix a twelve-year-old bug by accident.
**Don't touch it in this doc.** Nothing in it is a counting or grouping problem: the old version
suggested "a `groupingBy`", and there's nothing to group. Write one line saying why, and move on.
[DSA stage 06](../dsa/06-answer-space-search.md) has the diagnosis.

**Done when:** the one line is written and `FairWorkLoad` is unchanged.

### Exercise 4: fill in "What got worse"

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: what does `groupingBy` returning `Long` cost at a REST or JPA boundary, and would you
re-map it or change the callers? `merge` with a function returning `null` deletes the key: is that
a feature or a trap? The `ConcurrentHashMap.computeIfAbsent` javadoc forbids a mapping function
that updates the same map. **A correction to the old version:** it said doing it "deadlocks the
bin". On JDK 26, recursing on the same key throws `IllegalStateException: Recursive update`
(verified), and a plain `HashMap` throws `ConcurrentModificationException` (verified). A
different key in the same bin returned normally in my test (keys 0 and 16), which proves only that
it can work, not that it's allowed. Would you have found the rule without being told? At what input
size does the stream counter beat the `merge` loop, and did you measure or guess?

Three smaller Java 8 additions to try in `jshell`, all verified: `StringJoiner` with
`setEmptyValue("(none)")` (compare with `CircularList.toString`'s trailing comma in
[doc 10](10-sequenced-collections-and-httpclient.md)); `Base64.getEncoder()` vs
`getUrlEncoder()`, which turn the same three bytes into `+//+` and `-__-`; and
`Arrays.parallelSort`, which is worth timing against `Arrays.sort` on an array of ten thousand
before you believe it helps. `Map.of` throws on duplicate keys and nulls, and returns an immutable
map: when is that the wrong default? [Doc 16](16-collection-factories.md) is about exactly that.

### Exercise 5: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. Why did Java 8 need default methods to add `getOrDefault` to `Map`? (T1)
2. `m.put("x", null)`. What do `m.getOrDefault("x", 0)` and `m.putIfAbsent("x", 1)` each return,
   and which one changes the map? (T2)
3. In `merge(key, value, fn)`, when is `value` used, and when is `fn` called? (T2)
4. What does a `merge` function that returns `null` do to the map? (T2)
5. Why can't `computeIfAbsent` on its own increment a count that's already there? What is it
   for instead? (T3)
6. In `groupingBy(f, TreeMap::new, counting())`, what does each of the three arguments decide, and
   what's the value type? (T4)
7. What does two-argument `toMap` do on a repeated key, and how do you make it keep one value?
   (T5)
8. Why can a comparator with `reversed()` in the wrong place pass a test with no ties? (T6)
9. `Comparator.comparingInt(p -> p.score())` compiles when assigned, but not with `.reversed()`
   chained on. Why, and name two fixes. (T6)

**Done when:** you can answer all nine without looking.
