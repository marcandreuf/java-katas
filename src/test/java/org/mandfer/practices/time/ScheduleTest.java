package org.mandfer.practices.time;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("Exercise: docs/modernization/13-java-time.md. Delete this line to begin.")
class ScheduleTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /**
     * Noon the day before European summer time starts. The clocks go forward at 02:00 on
     * Sunday 29 March 2026, so the day that follows this instant is 23 hours long.
     */
    private static final ZonedDateTime BEFORE_SPRING_FORWARD =
            ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, MADRID);

    private final Schedule schedule =
            new Schedule(Clock.fixed(Instant.parse("2026-10-15T08:00:00Z"), ZoneOffset.UTC));

    @Test
    void same_time_tomorrow_keeps_the_wall_clock() {
        ZonedDateTime next = schedule.sameTimeTomorrow(BEFORE_SPRING_FORWARD);

        assertEquals(12, next.getHour(), "wall clock must still read 12");
        assertEquals(ZoneOffset.ofHours(2), next.getOffset(), "offset must have moved to CEST");
    }

    /** The assertion that makes the type choice unavoidable: one calendar day, 23 real hours. */
    @Test
    void same_time_tomorrow_is_only_23_hours_of_elapsed_time() {
        assertEquals(
                Duration.ofHours(23),
                Duration.between(BEFORE_SPRING_FORWARD, schedule.sameTimeTomorrow(BEFORE_SPRING_FORWARD)));
    }

    @Test
    void in_24_hours_moves_the_wall_clock_forward_by_one() {
        ZonedDateTime next = schedule.in24Hours(BEFORE_SPRING_FORWARD);

        assertEquals(13, next.getHour(), "24 real hours lands at 13:00, not 12:00");
        assertEquals(Duration.ofHours(24), Duration.between(BEFORE_SPRING_FORWARD, next));
    }

    @Test
    void until_reads_now_from_the_injected_clock() {
        assertEquals(
                Duration.ofMinutes(90),
                schedule.until(Instant.parse("2026-10-15T09:30:00Z")));
    }

    @Test
    void until_is_negative_for_a_deadline_already_passed() {
        assertEquals(
                Duration.ofMinutes(-30),
                schedule.until(Instant.parse("2026-10-15T07:30:00Z")));
    }

    @Test
    void stamp_renders_the_instant_in_the_requested_zone() {
        assertEquals(
                "2026-03-29 12:00:00 +02:00",
                Schedule.stamp(Instant.parse("2026-03-29T10:00:00Z"), MADRID));
    }

    @Test
    void stamp_renders_the_same_instant_differently_in_utc() {
        assertEquals(
                "2026-03-29 10:00:00 Z",
                Schedule.stamp(Instant.parse("2026-03-29T10:00:00Z"), ZoneOffset.UTC));
    }
}
