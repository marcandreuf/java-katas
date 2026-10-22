# 13. `java.time`

**Java 8** (`java.time`, JSR 310) · `InstantSource` is **Java 17** · stage 2 of
[the release-ordered track](INDEX.md) · compiles at `--release 21`, no build change

> **No safety net:** `ForkBlur`, `SimpleThreads` have no tests. Write them first, in a separate
> commit, asserting what the code does today — bugs included. [`../testing-first.md`](../testing-first.md)
> says how, and why the order matters. `Schedule` is new code with its tests already written.

## What this stage is actually about

Five words, defined once:

- an **`Instant`** is a point on the timeline, counted from 1970 in UTC. It has no time zone and
  no calendar. "When did this happen" is an `Instant`.
- a **`ZonedDateTime`** is a wall-clock reading (date and time of day) **plus** a zone's rules,
  such as `Europe/Madrid`. It can always tell you which `Instant` it is.
- a **`LocalDateTime`** is a wall-clock reading with **no** zone. It can't tell you which
  `Instant` it is, because 09:00 in Madrid and 09:00 in New York are six hours apart.
- a **`Duration`** is an amount of elapsed time in seconds and nanoseconds. A **`Period`** is an
  amount of calendar time in years, months and days. A day in a `Duration` is always 86,400
  seconds; a day in a `Period` is however long that calendar day turns out to be.
- a **`Clock`** is where "now" comes from. `Clock.systemUTC()` reads the machine;
  `Clock.fixed(...)` always returns the same instant. Since Java 17, `InstantSource` is the
  one-method interface for the same idea.

That gives one rule, and everything below follows from it: **the type you choose records what
you meant, so choose the type before you write the arithmetic.** `millis + 86_400_000` doesn't
say whether you meant "24 hours" or "tomorrow". The `java.time` types make you say which, and
when you pick the wrong one, a test fails instead of a user noticing twice a year.

## The worked example

`LocalDateTime` is the type that looks like the safe default and isn't. Two wall-clock times in
Madrid in 2026 (verified, JDK 26, tzdata 2026b):

    LocalDateTime.of(2026, 3, 29, 2, 30).atZone(MADRID)
        // 2026-03-29T03:30+02:00   02:30 never happened that night: the clocks jumped 02:00 -> 03:00
    LocalDateTime.of(2026, 10, 25, 2, 30).atZone(MADRID)
        // 2026-10-25T02:30+02:00   happened twice: this is the first of the two
        //   .withLaterOffsetAtOverlap() -> 02:30+01:00, one hour later on the timeline

    MADRID.getRules().getValidOffsets(...)   // March: []   October: [+02:00, +01:00]

So a `LocalDateTime` is sometimes **no** instant and sometimes **two**, and `atZone` resolves both
cases silently, by shifting the time forward or picking the earlier offset. No exception, no
warning. And the same `LocalDateTime` in two zones (verified):

    LocalDateTime.of(2026, 10, 15, 9, 0)
        .atZone(Europe/Madrid)     -> 2026-10-15T07:00:00Z
        .atZone(America/New_York)  -> 2026-10-15T13:00:00Z

Store a `LocalDateTime` in a database, read it back in another region, and those six hours are
gone, with nothing left that could recover them. `LocalDateTime` is right for a fact that is
*about* a wall clock: a birthday, a shop's opening time. It's wrong for anything that happened.

## Part one: `Schedule`, the types

`time/Schedule.java` is new code with four TODOs, and `ScheduleTest` already exists with seven
tests. Delete its `@Disabled` to begin.

**TODO 1 and TODO 2 are the same sentence in English and different code**:

- `sameTimeTomorrow`: "same time tomorrow", one calendar day later
- `in24Hours`: "in 24 hours", 86,400 elapsed seconds later

For most of the year they give the same answer. The test picks the day when they don't:
**Europe/Madrid, noon on 28 March 2026**, the day before summer time starts. What the tests expect
(verified against a scratch implementation):

| Method | Expected result | Elapsed from `from` |
| --- | --- | --- |
| `sameTimeTomorrow` | `2026-03-29T12:00+02:00` | **23 hours** |
| `in24Hours` | `2026-03-29T13:00+02:00` | 24 hours |

Which of `Duration` and `Period` gives which row is the exercise. Both compile. If you pick the
wrong one for TODO 1, the test says (verified):

    same_time_tomorrow_keeps_the_wall_clock: wall clock must still read 12 ==> expected: <12> but was: <13>
    same_time_tomorrow_is_only_23_hours_of_elapsed_time: expected: <PT23H> but was: <PT24H>

One trap on the way: `Duration.ofDays(1).equals(Duration.ofHours(24))` is `true` (verified). The
method name says "days", but the type means seconds.

## Part two: the clock, and what it doesn't fix

**TODO 3 is why `Schedule` takes a `Clock` in its constructor.** `until()` must read "now" from
that clock, and never from `System.currentTimeMillis()`. The test builds `Schedule` with
`Clock.fixed(2026-10-15T08:00:00Z)`, so its two `until` tests run in milliseconds with no
`sleep` (verified, against a scratch implementation).

Then look at the old code that needs the same thing, `concurrency/SimpleThreads.java:38-53`. It
waits for a thread, and it gives up after a timeout built from subtracted millis:

    long startTime = System.currentTimeMillis();                       // line 38
    ...
    while (t.isAlive()) {
        t.join(1000);                                                  // line 45
        if (((System.currentTimeMillis() - startTime) > defaultDelayInMiliseconds) && t.isAlive()) {
            t.interrupt(); t.join();
        }
    }

**A correction to the old version of this doc.** It said this loop is "untestable without waiting
four seconds in real time", and that `Clock.fixed` is the fix. Neither is right, and the reason is
the lesson of this part:

- **It's already fast to run.** The timeout comes from `args[0]` in seconds, and the default is
  one hour. `SimpleThreads 0` times out after one `join(1000)`: **1.06 s** in total (verified).
  The message thread finishes on its own after 16 s (four `sleep(4000)`s).
- **`Clock.fixed` breaks it.** I extracted the loop into a method taking a `Clock` and a patience
  of 5 s, in a scratch copy. With a fixed clock, elapsed time is always zero, so it never times
  out: **16,003 ms** (verified). With a fake clock that jumps an hour every time it is read, it
  times out on the first check, but still after **1,002 ms** (verified), because
  `t.join(1000)` waits on the real clock whatever `Clock` says.

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

## Part three: formatting

**TODO 4**: one `static final DateTimeFormatter`, shared. The tests pin `stamp()` for the same
`Instant` in two zones (verified):

    2026-03-29T10:00:00Z  in Europe/Madrid  ->  "2026-03-29 12:00:00 +02:00"
    2026-03-29T10:00:00Z  in UTC            ->  "2026-03-29 10:00:00 Z"

The pattern letter `XXX` prints `Z` at a zero offset, not `+00:00`. The lower-case `xxx` prints
`+00:00` (verified). That difference has broken log parsers. And a trap: a pattern-based formatter
can't format a bare `Instant`, because an `Instant` has no year until you say which zone you're
in. Try it and you get `UnsupportedTemporalTypeException: Unsupported field: YearOfEra`
(verified).

Then answer the question, because it's the most-asked `java.time` interview question: **why is a
shared `static final DateTimeFormatter` safe, when a shared `SimpleDateFormat` wasn't?** Here's
the evidence. Eight threads, each doing 20,000 parse-then-format round trips on one shared
instance of each (verified, three runs, timed under load):

    SimpleDateFormat:   ~140,000 of 160,000 wrong, plus 280 to 4,000 exceptions per run
    DateTimeFormatter:  0 wrong

**A correction to the old version:** it said the race produces "silently wrong dates under load
rather than an exception". It produces both, and mostly the silent kind. The exceptions are the
lucky ones. `SimpleDateFormat` keeps a mutable `Calendar` inside it, and every call writes to it.
Every `java.time` type is immutable, so thread safety comes out as a consequence, not as a
feature someone had to remember.

## Part four: the old code that should *not* use `java.time`

`concurrency/forkjoin/ForkBlur.java:115-117` times the blur with two `System.currentTimeMillis()`
calls. Rewrite it with `Instant.now()` and `Duration.between`, **then throw that away and use
`System.nanoTime()`.** `Instant.now()` reads the wall clock, the same as `currentTimeMillis()`,
so it has the same NTP problem as part two. Measuring an interval needs the monotonic clock.
`java.time` being newer doesn't make it the right tool here. That's the best "what got worse"
line in this document, and [doc 34](34-measuring-it-properly.md) is about measuring properly.
(Leave the blur itself alone: [doc 11](11-jvm-diagnostics.md) uses it as a workload.)

`collections/RandomFileLines.java:32` also reads the clock, as `new Random(System.currentTimeMillis())`.
That's using the time as a seed, not as a time, and [doc 19](19-randomness-and-numerics.md) owns
it.

## What got worse

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
