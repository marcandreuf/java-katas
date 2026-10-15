package org.mandfer.practices.concurrency;

import java.util.concurrent.Callable;

/**
 * ScopedValue: the replacement for ThreadLocal that exists because virtual threads broke
 * ThreadLocal's cost model.
 *
 * docs/modernization/06-virtual-threads.md ends with the prompt "what happens to a ThreadLocal when there
 * are a million threads?" -- this is the answer. A ThreadLocal is a mutable map entry per
 * thread, lives until it is removed, and is inherited by copying. At a million threads that
 * is a million map entries nobody clears. A ScopedValue is immutable, bound for the duration
 * of one call, and unbound automatically when that call returns.
 *
 * Final in Java 25 (JEP 506), after six releases of incubation and preview.
 * See docs/modernization/26-scoped-values-and-structured-concurrency.md.
 *
 * NOTE: needs --release 25.
 *
 * TODO 1: current() -- the bound tenant, or "none". Use isBound(); get() on an unbound value
 *         throws NoSuchElementException rather than returning null, which is deliberate.
 * TODO 2: runAs()  -- ScopedValue.where(...).run(...). Note there is no set() and no remove().
 *         You cannot mutate a binding, only establish a narrower one. Write down why that is
 *         the entire safety argument.
 * TODO 3: callAs() -- the value-returning form, for a body that throws.
 * TODO 4: tenantInsideAVirtualThread() -- fork a plain virtual thread from inside a binding
 *         and return what IT sees. PREDICT THE ANSWER FIRST. The test asserts it, and it is
 *         not what most people guess.
 */
public final class TenantContext {

    public static final ScopedValue<String> TENANT = ScopedValue.newInstance();

    private TenantContext() {
    }

    /** The tenant bound in this scope, or "none". */
    public static String current() {
        throw new UnsupportedOperationException("TODO 1");
    }

    /** Runs body with tenant bound. The binding is gone when this returns. */
    public static void runAs(String tenant, Runnable body) {
        throw new UnsupportedOperationException("TODO 2");
    }

    /** Calls body with tenant bound and returns its result. */
    public static <T> T callAs(String tenant, Callable<T> body) throws Exception {
        throw new UnsupportedOperationException("TODO 3");
    }

    /**
     * Submits current() to a virtual thread from inside the current binding and returns
     * whatever that thread saw.
     */
    public static String tenantInsideAVirtualThread() throws Exception {
        throw new UnsupportedOperationException("TODO 4");
    }
}
