package org.mandfer.practices.async;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Fanning out N tasks and waiting for all of them, which is the only CompletableFuture shape
 * most code ever needs, plus the three places people get it wrong.
 *
 * The old code does this job with raw Futures: SumWordsLength:28-40 submits one Callable per
 * word, collects Future<Integer> into a HashSet, then blocks on get() in a loop -- and
 * swallows the ExecutionException, returning a partial sum as though it were correct.
 * docs/06-virtual-threads.md owns that file. This class is where to learn the composition API
 * before deciding whether you want it. See docs/15-completable-future.md.
 *
 * TODO 1: implement all(). Run every supplier on the given Executor and complete with the
 *         results IN INPUT ORDER, not completion order. allOf() is the tool and it returns
 *         CompletableFuture<Void> -- work out what that means for how you get the values back.
 * TODO 2: implement lengthWithThenApply() and lengthWithThenCompose(). They return the same
 *         answer. Write thenApply first with a mapper that returns a CompletableFuture and
 *         look at the type you get. That type is the entire reason thenCompose exists.
 * TODO 3: implement orElse(). Pick between exceptionally(), handle() and whenComplete() and
 *         say what the other two would have done differently.
 * TODO 4: implement rootCauseOf(). Unwrap whatever CompletableFuture wrapped the original
 *         exception in. Note that join() and get() do not wrap it in the same type.
 */
public final class Fanout {

    private Fanout() {
    }

    /** Runs every task on ex; completes with results in the order the suppliers were given. */
    public static <T> CompletableFuture<List<T>> all(List<Supplier<T>> tasks, Executor ex) {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** Maps the upstream value to its length with a plain function. */
    public static CompletableFuture<Integer> lengthWithThenApply(CompletableFuture<String> upstream) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** Same answer, but the mapper itself returns a CompletableFuture<Integer>. */
    public static CompletableFuture<Integer> lengthWithThenCompose(CompletableFuture<String> upstream) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** Any failure upstream becomes the fallback value. Success passes through untouched. */
    public static CompletableFuture<String> orElse(CompletableFuture<String> upstream, String fallback) {
        throw new UnsupportedOperationException("TODO 3");
    }

    /** The original exception a failed stage carried, with the CompletableFuture wrapper removed. */
    public static Throwable rootCauseOf(CompletableFuture<?> failed) {
        throw new UnsupportedOperationException("TODO 4");
    }
}
