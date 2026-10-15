# 13. `java.time`

**Java 8** · stage 2 of [the release-ordered track](INDEX.md) · compiles at `--release 21`, no
build change

## Where it lands

    grep -rn --include='*.java' 'java.time' src     # nothing

What the repo has instead is `long` arithmetic on the wall clock:

| File | What is there |
| --- | --- |
| `concurrency/SimpleThreads.java:38,46` | `long startTime = System.currentTimeMillis()`, then `(System.currentTimeMillis() - startTime) > defaultDelayInMiliseconds` |
| `concurrency/forkjoin/ForkBlur.java:143-145` | start/end millis around the blur, subtracted for a duration |
| `collections/RandomFileLines.java:32` | `new Random(System.currentTimeMillis())` — a clock used as entropy, which is its own bug; [stage 8](INDEX.md) owns that one |

New code for the type distinctions: **`time/Schedule.java`**, four TODOs.

## The one idea worth the whole document

`java.time` has thirty-odd types and you need four. What makes it a real improvement over
`Date`/`Calendar` is not the API surface, it is that **the type you choose records what you meant**,
so the wrong meaning fails to compile or fails a test instead of being wrong twice a year.

| Type | Means | Use when |
| --- | --- | --- |
| `Instant` | a point on the physics timeline, UTC | logging, timestamps, durations, anything stored |
| `ZonedDateTime` | wall clock **plus** the zone's rules | "9am Madrid time", scheduling, anything a human reads |
| `LocalDateTime` | wall clock with **no** zone | almost never — see below |
| `Duration` / `Period` | elapsed seconds / calendar days | see TODO 1 and 2 |

**`LocalDateTime` is the one that causes outages.** It looks like the obvious default and it is
the only one of the three that cannot answer "what instant is that?". It is correct for a birthday
or a shop's opening hours — facts that are *about* a wall clock — and wrong for everything else. If
you store one in a database and read it back in another region, you have lost information that no
later code can recover.

## The exercise

### Part one, the types

`time/Schedule.java`. **TODO 1 and TODO 2 are the same sentence in English and different code**:

- `sameTimeTomorrow` — "same time tomorrow" — one calendar day later
- `in24Hours` — "in 24 hours" — 86400 elapsed seconds

Over most of the year these agree. Over the night the clocks change they do not, and
`ScheduleTest` pins the gap at **Europe/Madrid, 28 March 2026**, the day before European summer
time starts:

| | Result | Elapsed |
| --- | --- | --- |
| `from` | `2026-03-28T12:00+01:00` | — |
| `plus(Period.ofDays(1))` | `2026-03-29T12:00+02:00` | **23 hours** |
| `plus(Duration.ofHours(24))` | `2026-03-29T13:00+02:00` | 24 hours |

Both compile. Both are "add a day". One of them is what the user asked for and you have to decide
which, every time. **This is the single most useful thing in `java.time`**: `Duration` is physics,
`Period` is calendars, and the library refuses to pretend they are the same thing the way
`millis + 86400000` does.

The test asserts `Duration.between(from, sameTimeTomorrow) == 23 hours`. If you reach for
`Duration` in TODO 1, that line is what tells you.

### Part two, the clock

**TODO 3 is why the class takes a `Clock` in its constructor.** `until()` must read "now" from the
injected clock and never from `System.currentTimeMillis()`, which is what makes
`ScheduleTest.until_reads_now_from_the_injected_clock` a 2-millisecond test with no `sleep` in it
and no flake in it.

Then go and look at `SimpleThreads.java:43-53`. That loop is a timeout, it is built from
subtracted millis, and **it is untestable without waiting four seconds in real time.** There is no
test for it. There was never going to be. `Clock.fixed` is the fix, and the lesson is not the API —
it is that an injected clock turns a timing bug into something a test can hold still.

`Clock.fixed`, `Clock.offset` and `Clock.tick` are the three you will use. Know that
`Clock.systemUTC()` is the production one and that taking `Clock` as a constructor parameter costs
you one field.

### Part three, formatting

**TODO 4**: one `static final DateTimeFormatter`, shared.

Then answer this, because it is the most-asked `java.time` interview question and the answer is a
genuine design lesson: **`SimpleDateFormat` is mutable and not thread-safe, and
`DateTimeFormatter` is immutable and safe.** A `static final SimpleDateFormat` shared across
threads is a real data race that produces silently wrong dates under load rather than an
exception — which is the worst failure mode available. `java.time` made every type immutable, and
thread-safety came out as a consequence rather than as a feature.

The test pins `stamp()` for the same `Instant` in two zones:

    2026-03-29T10:00:00Z  in Europe/Madrid  ->  "2026-03-29 12:00:00 +02:00"
    2026-03-29T10:00:00Z  in UTC            ->  "2026-03-29 10:00:00 Z"

One instant, two renderings, and the pattern letter `XXX` prints `Z` rather than `+00:00` at zero
offset. That last detail has broken log parsers.

### Part four, the old code

Rewrite `ForkBlur.java:143-145` with `Instant.now()` and `Duration`, **then throw it away and use
`System.nanoTime()` instead.** `Instant.now()` is wall-clock time; it can jump backwards when NTP
corrects the machine, so it is the wrong tool for measuring an interval. `java.time` being the
newer API does not make it the right answer here. Write that down — it is the best "what got
worse" line available in this document, and [stage 23](INDEX.md) is about measuring properly.

## What got worse

- _(fill this in)_
- _(fill this in)_
- _(fill this in)_

Prompts: what does taking a `Clock` parameter do to the call sites of a method that never needed
one? What does `java.time` cost at a JDBC or JSON boundary that still speaks `java.util.Date`, and
who writes the converters? Which of `Instant` and `ZonedDateTime` would you store in a database
column, and what breaks on the other choice? `ZoneId.of("Europe/Madrid")` depends on the tzdata
bundled with your JDK — what happens to a scheduled job when a government changes its DST rules
with six weeks' notice?
