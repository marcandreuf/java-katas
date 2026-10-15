package org.mandfer.practices.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Four operations on time, chosen because picking the wrong type makes each of them wrong in a
 * way a test can catch.
 *
 * There is no java.time anywhere in this repo:
 *
 *     grep -rn --include='*.java' 'java.time' src     -> nothing
 *
 * What there is instead is long arithmetic on System.currentTimeMillis(), in
 * SimpleThreads:38,46 and ForkBlur:143-145. Those are the rewrite targets; this class is the
 * place to learn the types first. See docs/13-java-time.md.
 *
 * TODO 1: implement sameTimeTomorrow(). "Same wall-clock time, one calendar day later."
 * TODO 2: implement in24Hours(). "Exactly 86400 seconds later."
 *         TODO 1 and TODO 2 are NOT the same operation, and the test proves it. One of
 *         Duration and Period is right for each. Getting them the wrong way round compiles.
 * TODO 3: implement until(), using the injected clock and never System.currentTimeMillis().
 *         The Clock field is the entire reason this class is testable without sleeping.
 * TODO 4: implement stamp(). Declare the DateTimeFormatter as a static final field and
 *         reuse it. Then write down why that is safe here and was a data race with
 *         SimpleDateFormat.
 */
public final class Schedule {

    // TODO 4: a static final DateTimeFormatter goes here.

    private final Clock clock;

    public Schedule(Clock clock) {
        this.clock = clock;
    }

    /** Same wall-clock time, next calendar day. Across a DST boundary this is not 24 hours. */
    public ZonedDateTime sameTimeTomorrow(ZonedDateTime from) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** Exactly 24 hours of elapsed time. Across a DST boundary the wall clock shifts by one. */
    public ZonedDateTime in24Hours(ZonedDateTime from) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** How long from now until the deadline, where "now" comes from the injected clock. */
    public Duration until(Instant deadline) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** The instant rendered in the given zone as {@code yyyy-MM-dd HH:mm:ss XXX}. */
    public static String stamp(Instant instant, ZoneId zone) {
        throw new UnsupportedOperationException("TODO 4");
    }
}
