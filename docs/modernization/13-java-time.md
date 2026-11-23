# 13. `java.time`

**Java 8** (`java.time`, JSR 310) · `InstantSource` is **Java 17** · stage 2 of
[the release-ordered track](INDEX.md) · compiles at `--release 21`, no build change

> **No safety net:** `ForkBlur` has no tests, and `SimpleThreadsTest` only covers `usage()` (doc 04), not the wait loop. Write them first, in a separate
> commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md)
> says how, and why the order matters. `Schedule` is new code with its tests already written.

## What you do in this doc

Read the **Theory** sections first. They change no code. Then do the **Practice** exercises, in
order. Each one ends with a **Done when** line.

| Exercise | What you do | Theory it uses | Tests |
| --- | --- | --- | --- |
| [1. `Schedule`, TODOs 1 and 2](#exercise-1-schedule-todos-1-and-2-the-types) | Pick `Duration` or `Period` for "tomorrow" and "in 24 hours". | T2, T3, T4 | `ScheduleTest` exists: delete its `@Disabled`. |
| [2. The clock](#exercise-2-the-clock-and-what-it-doesnt-fix) | TODO 3, then give `SimpleThreads`' wait loop its seams. Three commits. | T5 | Characterize `SimpleThreads` first. |
| [3. Formatting](#exercise-3-formatting-todo-4) | TODO 4, and the thread-safety question in writing. | T6 | `ScheduleTest` covers it. |
| [4. Code that should *not* use `java.time`](#exercise-4-the-old-code-that-should-not-use-javatime) | Time `ForkBlur` with `Instant`, then throw it away. | T5 | None: it's a measurement. |
| [5. What got worse](#exercise-5-fill-in-what-got-worse) | Write down what `java.time` cost. | all | None. |
| [6. Review questions](#exercise-6-review-questions) | Answer without looking, then check against the theory. | all | None. |

## Theory

Read the six sections in order: each one builds on the one before. Here is the whole story in one
paragraph, so you know where it's going.

> Old Java code does time as a `long` of milliseconds, and a `long` can't say what it means
> (T1). `java.time` splits "time" into separate types: a moment on the timeline, a reading on a
> wall clock, and a wall clock plus the zone that gives it meaning (T2). The wall-clock type
> looks like the safe default, but twice a year a wall-clock time is either no moment at all or
> two (T3). Amounts of time split the same way: elapsed seconds or calendar days, and they are not
> the same thing (T4). "Now" becomes something you pass in, a `Clock`, which makes code testable
> but doesn't make every timing problem go away (T5). And because every one of these types is
> immutable, the formatter is too, which fixed a race that `SimpleDateFormat` had for twenty years
> (T6).

Every code sample below is a complete program unless it says otherwise. Save it as the class name
plus `.java` and run it with `java Name.java`; no build needed. None of them reads the real clock,
so they print the same thing every time (except T6's race, which says so). Outputs marked
*verified* were run on JDK 26 with tzdata 2026b, and use nothing above `--release 21`.

### T1. The problem: a `long` doesn't say what it means

Before Java 8, time in Java was mostly a `long`: milliseconds since 1970, from
`System.currentTimeMillis()`, with arithmetic done by hand.

```java
import java.time.Instant;

public class Millis {
    public static void main(String[] args) {
        long created = 1_792_051_200_000L;        // some moment, as millis since 1970
        long later = created + 86_400_000;        // 24 hours? "tomorrow"? the code doesn't say

        System.out.println("created " + Instant.ofEpochMilli(created));
        System.out.println("later   " + Instant.ofEpochMilli(later));
        System.out.println("apart   " + (later - created) + " ms");
    }
}
```

```text
created 2026-10-15T08:00:00Z                             (verified 2026-11-23)
later   2026-10-16T08:00:00Z
apart   86400000 ms
```

The arithmetic is fine. The problem is that `+ 86_400_000` doesn't record whether the author
meant "24 hours later" or "same time tomorrow". Most days those are the same. On the two days a
year when the clocks change, they aren't, and nothing in the code says which one was intended.
`java.util.Date` and `Calendar` didn't help much: a `Date` is that same `long` in a wrapper, and
both are mutable.

`java.time` answers with one rule, and everything below follows from it:

> **The type you choose records what you meant, so choose the type before you write the
> arithmetic.**

When you pick the wrong type, a test fails instead of a user noticing twice a year.

### T2. A moment, a wall clock, and a wall clock with a zone

The first split is between *when something happened* and *what a clock on the wall said*.

- an **`Instant`** is a point on the timeline, counted from 1970 in UTC. It has no time zone and
  no calendar. "When did this happen" is an `Instant`.
- a **`LocalDateTime`** is a wall-clock reading (date and time of day) with **no** zone. It can't
  tell you which `Instant` it is, because 09:00 in Madrid and 09:00 in New York are six hours
  apart.
- a **`ZonedDateTime`** is a wall-clock reading **plus** a zone's rules, such as
  `Europe/Madrid`. It can always tell you which `Instant` it is.

(`LocalDate` and `LocalTime` are the date-only and time-only halves of `LocalDateTime`.
`OffsetDateTime` is a wall clock plus a fixed offset like `+02:00`, without the zone's rules for
when that offset changes.)

You can see the split by going both ways: one `Instant` read on two wall clocks, and one wall-clock
reading placed in two zones.

```java
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class Zones {
    public static void main(String[] args) {
        ZoneId madrid = ZoneId.of("Europe/Madrid");
        ZoneId newYork = ZoneId.of("America/New_York");

        // One Instant: one moment, read on two wall clocks.
        Instant landing = Instant.parse("2026-07-01T16:45:00Z");
        System.out.println("instant         " + landing);
        System.out.println("  in Madrid     " + landing.atZone(madrid));
        System.out.println("  in New York   " + landing.atZone(newYork));

        // One LocalDateTime: one wall-clock reading, two different moments.
        LocalDateTime nineAm = LocalDateTime.of(2026, 7, 1, 9, 0);
        System.out.println("local           " + nineAm);
        System.out.println("  as Madrid     " + nineAm.atZone(madrid).toInstant());
        System.out.println("  as New York   " + nineAm.atZone(newYork).toInstant());
    }
}
```

```text
instant         2026-07-01T16:45:00Z                     (verified 2026-11-23)
  in Madrid     2026-07-01T18:45+02:00[Europe/Madrid]
  in New York   2026-07-01T12:45-04:00[America/New_York]
local           2026-07-01T09:00
  as Madrid     2026-07-01T07:00:00Z
  as New York   2026-07-01T13:00:00Z
```

The same is true in October (verified):

```text
LocalDateTime.of(2026, 10, 15, 9, 0)
    .atZone(Europe/Madrid)     -> 2026-10-15T07:00:00Z
    .atZone(America/New_York)  -> 2026-10-15T13:00:00Z
```

Store a `LocalDateTime` in a database, read it back in another region, and those six hours are
gone, with nothing left that could recover them. `LocalDateTime` is right for a fact that is
*about* a wall clock: a birthday, a shop's opening time. It's wrong for anything that happened.

### T3. The wall clock that skips and repeats

`LocalDateTime` looks like the safe default, because it has no zone to get wrong. It isn't, and
the reason is daylight saving time. Twice a year, a zone's wall clock jumps:

- in **spring** it skips an hour, so some wall-clock times **never happen**;
- in **autumn** it repeats an hour, so some wall-clock times **happen twice**.

Here are both in Madrid in 2026:

```java
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class Gaps {
    public static void main(String[] args) {
        ZoneId madrid = ZoneId.of("Europe/Madrid");

        LocalDateTime gap = LocalDateTime.of(2026, 3, 29, 2, 30);       // clocks jump 02:00 -> 03:00
        LocalDateTime overlap = LocalDateTime.of(2026, 10, 25, 2, 30);  // clocks go 03:00 -> 02:00

        System.out.println("gap      valid offsets " + madrid.getRules().getValidOffsets(gap));
        System.out.println("         atZone        " + gap.atZone(madrid));

        System.out.println("overlap  valid offsets " + madrid.getRules().getValidOffsets(overlap));
        ZonedDateTime first = overlap.atZone(madrid);
        System.out.println("         atZone        " + first);
        System.out.println("         later offset  " + first.withLaterOffsetAtOverlap());
    }
}
```

```text
gap      valid offsets []                                (verified 2026-11-23)
         atZone        2026-03-29T03:30+02:00[Europe/Madrid]
overlap  valid offsets [+02:00, +01:00]
         atZone        2026-10-25T02:30+02:00[Europe/Madrid]
         later offset  2026-10-25T02:30+01:00[Europe/Madrid]
```

Read it line by line:

- **The gap.** 02:30 on 29 March has **no** valid offset: it never happened that night. `atZone`
  doesn't complain. It shifts the time forward by the length of the gap, to 03:30.
- **The overlap.** 02:30 on 25 October has **two** valid offsets. `atZone` silently picks the
  earlier one, `+02:00`. `withLaterOffsetAtOverlap()` gives the other, which is one hour later on
  the timeline.

So a `LocalDateTime` is sometimes **no** instant and sometimes **two**, and `atZone` resolves both
cases silently. No exception, no warning. Code that stores wall-clock times and converts them
later inherits those silent choices.

### T4. Two kinds of "a day": `Duration` and `Period`

Amounts of time split the same way moments do.

- a **`Duration`** is an amount of **elapsed** time, in seconds and nanoseconds. A day in a
  `Duration` is always 86,400 seconds. It's what you measure between two `Instant`s.
- a **`Period`** is an amount of **calendar** time, in years, months and days. A day in a `Period`
  is however long that calendar day turns out to be; a month is however long that month is.

```java
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

public class Amounts {
    public static void main(String[] args) {
        // Duration: elapsed time, between two Instants.
        Instant start = Instant.parse("2026-05-04T09:00:00Z");
        Instant end = Instant.parse("2026-05-05T11:30:15Z");
        Duration took = Duration.between(start, end);
        System.out.println("Duration.between      " + took + " = " + took.toSeconds() + " s");
        System.out.println("ofDays(1) == ofHours(24)? " + Duration.ofDays(1).equals(Duration.ofHours(24)));

        // Period: calendar time, between two LocalDates.
        LocalDate hired = LocalDate.of(2023, 11, 30);
        LocalDate today = LocalDate.of(2026, 2, 14);
        System.out.println("Period.between        " + Period.between(hired, today));

        // A month is not a fixed length: the calendar decides.
        LocalDate jan31 = LocalDate.of(2026, 1, 31);
        System.out.println("jan 31 + 1 month      " + jan31.plus(Period.ofMonths(1)));
        System.out.println("jan 31 + 30 days      " + jan31.plusDays(30));
    }
}
```

```text
Duration.between      PT26H30M15S = 95415 s              (verified 2026-11-23)
ofDays(1) == ofHours(24)? true
Period.between        P2Y2M15D
jan 31 + 1 month      2026-02-28
jan 31 + 30 days      2026-03-02
```

Three things to notice:

- **`Duration` prints in hours, not days** (`PT26H30M15S`), because it only knows seconds.
- **`Duration.ofDays(1).equals(Duration.ofHours(24))` is `true`.** The method name says "days",
  but the type means 86,400 seconds. That's the trap: `ofDays` reads like calendar time and isn't.
- **A `Period` of one month from 31 January lands on 28 February**, because the calendar has no
  31 February. Thirty days lands somewhere else. The calendar decides what a month is.

Now put T3 and T4 together. A `ZonedDateTime` accepts both a `Duration` and a `Period`, and on most
days they give the same answer. On the day a zone's clocks change, one of them keeps the wall
clock and the other keeps the elapsed time. **Working out which does which is exercise 1**, and
T3's gap is the day to try it on.

### T5. Where "now" comes from: `Clock`

Code that calls `System.currentTimeMillis()` or `Instant.now()` reads the machine's clock
directly, and a test can't control it. The test either waits in real time or doesn't test the
time-dependent part at all.

`java.time`'s answer is to make "now" an object you pass in. A **`Clock`** is where "now" comes
from:

- `Clock.systemUTC()` reads the machine;
- `Clock.fixed(instant, zone)` always returns the same instant;
- `Clock.offset(clock, duration)` is another clock, shifted;
- since Java 17, **`InstantSource`** is the one-method interface (`instant()`) for the same idea,
  and every `Clock` is one.

Every "now" method takes one: `Instant.now(clock)`, `LocalDate.now(clock)`,
`ZonedDateTime.now(clock)`.

```java
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneOffset;

public class Clocks {
    public static void main(String[] args) {
        Clock fixed = Clock.fixed(Instant.parse("2026-12-24T18:00:00Z"), ZoneOffset.UTC);
        System.out.println("fixed, read twice:   " + Instant.now(fixed) + " " + Instant.now(fixed));

        Clock aDayLater = Clock.offset(fixed, Duration.ofDays(1));
        System.out.println("offset by a day:     " + Instant.now(aDayLater));

        InstantSource source = fixed;                 // Java 17: a Clock is an InstantSource
        System.out.println("as InstantSource:    " + source.instant());
    }
}
```

```text
fixed, read twice:   2026-12-24T18:00:00Z 2026-12-24T18:00:00Z   (verified 2026-11-23)
offset by a day:     2026-12-25T18:00:00Z
as InstantSource:    2026-12-24T18:00:00Z
```

A class that takes a `Clock` in its constructor can be run by a test at any moment in history,
instantly. That's the whole reason `Schedule` takes one.

**Two things a `Clock` doesn't fix.** Both come back in exercises 2 and 4:

- **Not every wait reads the clock.** `Thread.sleep(1000)` and `t.join(1000)` wait on real time,
  whatever `Clock` you injected. A fixed clock can make code *think* no time has passed while it
  really waits.
- **`Clock.systemUTC()` is wall-clock time**, the same as `System.currentTimeMillis()`. If NTP sets
  the machine's clock back, wall-clock time goes backwards. The JDK has no monotonic `Clock`. For
  measuring an elapsed interval, `System.nanoTime()` is the monotonic source (per its javadoc).
  `java.time` being newer doesn't make it the right tool for a stopwatch.

### T6. Formatting, and why immutability made it thread-safe

A **`DateTimeFormatter`** turns a date-time into text and back. Pattern letters say what to print,
and a few of them differ in ways that matter:

```java
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class Formats {
    // Immutable, so one shared instance is safe for every thread.
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEE d MMM uuuu, HH:mm", Locale.ENGLISH);

    public static void main(String[] args) {
        OffsetDateTime utc = OffsetDateTime.of(2026, 6, 1, 8, 15, 0, 0, ZoneOffset.UTC);
        System.out.println("XXX at +00:00  " + DateTimeFormatter.ofPattern("HH:mm XXX").format(utc));
        System.out.println("xxx at +00:00  " + DateTimeFormatter.ofPattern("HH:mm xxx").format(utc));

        // Same moment, moved to Tokyo's wall clock, then formatted.
        System.out.println("in Tokyo       " + DAY.format(utc.atZoneSameInstant(ZoneId.of("Asia/Tokyo"))));

        try {
            DAY.format(Instant.parse("2026-06-01T08:15:00Z"));   // no zone: no day, no hour
        } catch (RuntimeException e) {
            System.out.println("bare Instant   " + e);
        }
    }
}
```

```text
XXX at +00:00  08:15 Z                                   (verified 2026-11-23)
xxx at +00:00  08:15 +00:00
in Tokyo       Mon 1 Jun 2026, 17:15
bare Instant   java.time.temporal.UnsupportedTemporalTypeException: Unsupported field: DayOfWeek
```

- **`XXX` prints `Z` at a zero offset, not `+00:00`.** The lower-case `xxx` prints `+00:00`. That
  difference has broken log parsers.
- **A pattern-based formatter can't format a bare `Instant`**, because an `Instant` has no day,
  year or hour until you say which zone you're in (T2). The exception names the first field the
  pattern asked for: `DayOfWeek` here, `YearOfEra` for a pattern that starts with the year.

**Why one shared formatter is safe.** The old formatter, `SimpleDateFormat`, keeps a mutable
`Calendar` inside it, and every `parse` and `format` call writes to it. Share one between threads
and they overwrite each other's work. Every `java.time` type is immutable, `DateTimeFormatter`
included, so a shared `static final` one has nothing to overwrite. Thread safety comes out as a
consequence, not as a feature someone had to remember.

You can watch the difference. Eight threads, each doing 20,000 parse-then-format round trips on one
shared instance of each:

```java
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SharedFormatter {
    static final SimpleDateFormat OLD = new SimpleDateFormat("yyyy-MM-dd");
    static final DateTimeFormatter NEW = DateTimeFormatter.ofPattern("uuuu-MM-dd");

    public static void main(String[] args) throws Exception {
        AtomicInteger oldWrong = new AtomicInteger(), oldThrew = new AtomicInteger(), newWrong = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int t = 0; t < 8; t++) {
            int day = t + 1;
            String text = "2026-01-0" + day;            // each thread has its own date
            pool.submit(() -> {
                for (int i = 0; i < 20_000; i++) {
                    try {
                        if (!OLD.format(OLD.parse(text)).equals(text)) oldWrong.incrementAndGet();
                    } catch (Exception e) {
                        oldThrew.incrementAndGet();
                    }
                    if (!NEW.format(LocalDate.parse(text, NEW)).equals(text)) newWrong.incrementAndGet();
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(1, java.util.concurrent.TimeUnit.MINUTES);
        System.out.println("SimpleDateFormat:  " + oldWrong + " wrong, " + oldThrew + " threw, of 160000");
        System.out.println("DateTimeFormatter: " + newWrong + " wrong");
    }
}
```

Three runs of this program printed (verified 2026-11-23; the `SimpleDateFormat` numbers change
every run):

```text
SimpleDateFormat:  135036 wrong, 865 threw, of 160000
SimpleDateFormat:  101352 wrong, 10265 threw, of 160000
SimpleDateFormat:  112068 wrong, 1793 threw, of 160000
DateTimeFormatter: 0 wrong                                (all three runs)
```

The earlier experiment behind this doc, with the same shape, recorded (verified, three runs):

```text
SimpleDateFormat:   144,000 to 156,000 of 160,000 wrong, plus 370 to 2,300 exceptions per run
DateTimeFormatter:  0 wrong
```

The exact counts depend on the code and the machine. The shape doesn't: **the race produces both
wrong dates and exceptions, mostly the silent kind.** The exceptions are the lucky ones.
(**A correction to the old version of this doc:** it said the race produces "silently wrong dates
under load rather than an exception". It produces both.)

## Practice

### Exercise 1: `Schedule`, TODOs 1 and 2: the types

`time/Schedule.java` is new code with four TODOs, and `ScheduleTest` already exists with seven
tests. Delete its `@Disabled` to begin.

**TODO 1 and TODO 2 are the same sentence in English and different code**:

- `sameTimeTomorrow`: "same time tomorrow", one calendar day later
- `in24Hours`: "in 24 hours", 86,400 elapsed seconds later

For most of the year they give the same answer. The test picks the day when they don't:
**Europe/Madrid, noon on 28 March 2026**, the day before summer time starts (T3). What the tests
expect (verified against a scratch implementation):

| Method | Expected result | Elapsed from `from` |
| --- | --- | --- |
| `sameTimeTomorrow` | `2026-03-29T12:00+02:00` | **23 hours** |
| `in24Hours` | `2026-03-29T13:00+02:00` | 24 hours |

Which of `Duration` and `Period` gives which row is the exercise (T4). Both compile. If you pick
the wrong one for TODO 1, the test says (verified):

```text
same_time_tomorrow_keeps_the_wall_clock: wall clock must still read 12 ==> expected: <12> but was: <13>
same_time_tomorrow_is_only_23_hours_of_elapsed_time: expected: <PT23H> but was: <PT24H>
```

One trap on the way: `Duration.ofDays(1).equals(Duration.ofHours(24))` is `true` (verified, T4).
The method name says "days", but the type means seconds.

**Done when:** the three `sameTimeTomorrow` and `in24Hours` tests are green, and you can say in one
sentence why each method uses the type it does.

### Exercise 2: the clock, and what it doesn't fix

**TODO 3 is why `Schedule` takes a `Clock` in its constructor** (T5). `until()` must read "now"
from that clock, and never from `System.currentTimeMillis()`. The test builds `Schedule` with
`Clock.fixed(2026-10-15T08:00:00Z)`, so its two `until` tests run in milliseconds with no `sleep`
(verified, against a scratch implementation).

Then look at the old code that needs the same thing, `concurrency/SimpleThreads.java:44-59`. It
waits for a thread, and it gives up after a timeout built from subtracted millis:

```java
long startTime = System.currentTimeMillis();                       // line 44
...
while (t.isAlive()) {
    t.join(1000);                                                  // line 51
    if (((System.currentTimeMillis() - startTime) > defaultDelayInMiliseconds) && t.isAlive()) {
        t.interrupt(); t.join();
    }
}
```

**A correction to the old version of this doc.** It said this loop is "untestable without waiting
four seconds in real time", and that `Clock.fixed` is the fix. Neither is right, and the reason is
the lesson of this exercise:

- **It's already fast to run.** The timeout comes from `args[0]` in seconds, and the default is
  one hour. `SimpleThreads 0` times out after one `join(1000)`: **1.04 s** in total (verified).
  The message thread finishes on its own after 16 s (four `sleep(4000)`s).
- **`Clock.fixed` breaks it.** I extracted the loop into a method taking a `Clock` and a patience
  of 5 s, in a scratch copy. With a fixed clock, elapsed time is always zero, so it never times
  out: **16,004 ms** (verified). With a fake clock that jumps an hour every time it is read, it
  times out on the first check, but still after **1,000 ms** (verified), because
  `t.join(1000)` waits on the real clock whatever `Clock` says (T5).

So the clock is **one of two** real-time dependencies in that loop. The other is the `join(1000)`
poll, and no `java.time` type reaches it. Making this loop fast to test means a seam for both.
That's for you to design; write down where you'd put the second one.

And one more thing the injected clock doesn't fix: `Clock.systemUTC()` is wall-clock time, the
same as `System.currentTimeMillis()`. If NTP sets the machine's clock back during the wait, the
timeout fires late, and that's true of both versions. The JDK has no monotonic `Clock`. For an
elapsed-time check like this one, `System.nanoTime()` is the monotonic source (per its javadoc;
not reproduced here, because making NTP jump on demand isn't practical).

Commits, in this order:

1. **Characterize `SimpleThreads`.** Extract the wait loop into a method that returns whether it
   gave up, and test it with a timeout of 0 s: it gives up, after about a second. That's
   today's behaviour, and it's testable today.
2. **Add the seams** (the clock, and whatever you chose for the poll) without changing behaviour.
   The test from step 1 stays green and gets faster.
3. **Write the fake-clock test**, the one that can't be written in step 1: patience of an hour, a
   clock that says an hour has passed, no real waiting.

**Done when:** both `until` tests are green, the three `SimpleThreads` commits are in, the
fake-clock test runs without real waiting, and you've written down where the second seam went.

### Exercise 3: formatting, TODO 4

**TODO 4**: one `static final DateTimeFormatter`, shared (T6). The tests pin `stamp()` for the
same `Instant` in two zones (verified):

```text
2026-03-29T10:00:00Z  in Europe/Madrid  ->  "2026-03-29 12:00:00 +02:00"
2026-03-29T10:00:00Z  in UTC            ->  "2026-03-29 10:00:00 Z"
```

The pattern letter `XXX` prints `Z` at a zero offset, not `+00:00`. The lower-case `xxx` prints
`+00:00` (verified). That difference has broken log parsers. And a trap: a pattern-based formatter
can't format a bare `Instant`, because an `Instant` has no year until you say which zone you're
in. Try it and you get `UnsupportedTemporalTypeException: Unsupported field: YearOfEra`
(verified).

Then answer the question, because it's the most-asked `java.time` interview question: **why is a
shared `static final DateTimeFormatter` safe, when a shared `SimpleDateFormat` wasn't?** T6 has
the evidence and the mechanism. Write the answer in your own words, in two sentences.

**Done when:** both `stamp` tests are green, the formatter is a `static final` field, and your
two-sentence answer is written down.

### Exercise 4: the old code that should *not* use `java.time`

`concurrency/forkjoin/ForkBlur.java:115-117` times the blur with two `System.currentTimeMillis()`
calls. Rewrite it with `Instant.now()` and `Duration.between`, **then throw that away and use
`System.nanoTime()`.** `Instant.now()` reads the wall clock, the same as `currentTimeMillis()`,
so it has the same NTP problem as exercise 2 (T5). Measuring an interval needs the monotonic
clock. `java.time` being newer doesn't make it the right tool here. That's the best "what got
worse" line in this document, and [doc 34](34-measuring-it-properly.md) is about measuring
properly. (Leave the blur itself alone: [doc 11](11-jvm-diagnostics.md) uses it as a workload.)

`collections/RandomFileLines.java:32` also reads the clock, as `new Random(System.currentTimeMillis())`.
That's using the time as a seed, not as a time, and [doc 19](19-randomness-and-numerics.md) owns
it.

**Done when:** `ForkBlur`'s timing uses `System.nanoTime()`, the blur is unchanged, and you can say
why the `Instant` version was the wrong one.

### Exercise 5: fill in "What got worse"

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: what does taking a `Clock` parameter do to the call sites of a method that never needed
one? `SimpleThreads` needed two seams, not one: what does that say about "just inject the clock"?
What does `java.time` cost at a JDBC or JSON boundary that still speaks `java.util.Date`, and who
writes the converters? Which of `Instant` and `ZonedDateTime` would you store in a database
column, and what breaks with the other choice? `ZoneId.of("Europe/Madrid")` depends on the tzdata
bundled with your JDK (2026b here): what happens to a scheduled job when a government changes its
DST rules with six weeks' notice?

### Exercise 6: review questions

Answer each one in a line or two, without looking. Then check against the section in brackets.
Any you got wrong, reread that section.

1. What can't `created + 86_400_000` tell a reader, and on which days does it matter? (T1)
2. Which of `Instant`, `LocalDateTime` and `ZonedDateTime` can't tell you which moment it is, and
   why? (T2)
3. Which type would you use for a shop's opening time, and which for when an order was placed?
   (T2)
4. In Madrid, 02:30 on 29 March 2026 has how many valid offsets? And 02:30 on 25 October? What
   does `atZone` do in each case? (T3)
5. What does `Duration.ofDays(1).equals(Duration.ofHours(24))` return, and why is that a trap?
   (T4)
6. What is 31 January plus a `Period` of one month? (T4)
7. Why does `Schedule` take a `Clock` in its constructor? (T5)
8. Name two timing problems an injected `Clock` doesn't fix. (T5)
9. What do `XXX` and `xxx` print at a zero offset? (T6)
10. Why can't a pattern-based formatter format a bare `Instant`? (T6)
11. Why is a shared `DateTimeFormatter` safe when a shared `SimpleDateFormat` isn't? (T6)

**Done when:** you can answer all eleven without looking.
