package org.mandfer.practices.async;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Exercise: docs/modernization/15-completable-future.md. Delete this line to begin.")
class FanoutTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdown() throws InterruptedException {
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "pool did not stop");
    }

    @Test
    void all_collects_every_result() {
        List<Supplier<String>> tasks = List.of(() -> "a", () -> "b", () -> "c");

        assertEquals(List.of("a", "b", "c"), Fanout.all(tasks, pool).join());
    }

    /**
     * Input order, not completion order. The latch forces the second task to finish strictly
     * before the first, so a result list built from completion order comes back reversed.
     * No sleeps: this is deterministic, not merely usually right.
     */
    @Test
    void all_preserves_input_order_not_completion_order() {
        CountDownLatch secondFinished = new CountDownLatch(1);

        Supplier<String> waitsForTheOther = () -> {
            try {
                assertTrue(secondFinished.await(5, TimeUnit.SECONDS), "latch never opened");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return "first";
        };
        Supplier<String> finishesEarly = () -> {
            secondFinished.countDown();
            return "second";
        };

        assertEquals(
                List.of("first", "second"),
                Fanout.all(List.of(waitsForTheOther, finishesEarly), pool).join());
    }

    @Test
    void all_of_nothing_completes_with_an_empty_list() {
        assertEquals(List.of(), Fanout.all(List.<Supplier<String>>of(), pool).join());
    }

    /** One failed task fails the whole fan-out. There is no partial result. */
    @Test
    void all_fails_if_any_task_fails() {
        List<Supplier<String>> tasks = List.of(
                () -> "fine",
                () -> {
                    throw new IllegalStateException("boom");
                });

        CompletionException thrown =
                assertThrows(CompletionException.class, () -> Fanout.all(tasks, pool).join());

        assertInstanceOf(IllegalStateException.class, thrown.getCause());
    }

    @Test
    void then_apply_maps_the_value() {
        assertEquals(5, Fanout.lengthWithThenApply(CompletableFuture.completedFuture("hello")).join());
    }

    @Test
    void then_compose_flattens_a_nested_future() {
        assertEquals(5, Fanout.lengthWithThenCompose(CompletableFuture.completedFuture("hello")).join());
    }

    @Test
    void or_else_substitutes_on_failure() {
        CompletableFuture<String> failed = CompletableFuture.failedFuture(new IllegalStateException("boom"));

        assertEquals("fallback", Fanout.orElse(failed, "fallback").join());
    }

    @Test
    void or_else_leaves_a_successful_value_alone() {
        assertEquals("real", Fanout.orElse(CompletableFuture.completedFuture("real"), "fallback").join());
    }

    @Test
    void root_cause_unwraps_the_completion_exception() {
        IllegalStateException original = new IllegalStateException("boom");

        Throwable cause = Fanout.rootCauseOf(CompletableFuture.failedFuture(original));

        assertEquals(original, cause, "expected the original exception, not a wrapper");
    }
}
