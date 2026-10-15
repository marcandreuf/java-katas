package org.mandfer.practices.concurrency;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("Exercise: docs/modernization/26-scoped-values-and-structured-concurrency.md. Delete this line to begin.")
class TenantContextTest {

    @Test
    void nothing_is_bound_outside_a_scope() {
        assertEquals("none", TenantContext.current());
    }

    @Test
    void run_as_binds_for_the_duration_of_the_body() {
        AtomicReference<String> seen = new AtomicReference<>();

        TenantContext.runAs("acme", () -> seen.set(TenantContext.current()));

        assertEquals("acme", seen.get());
    }

    /** The binding unwinds on its own. There is no remove() to forget to call. */
    @Test
    void the_binding_is_gone_after_the_body_returns() {
        TenantContext.runAs("acme", () -> {
        });

        assertEquals("none", TenantContext.current());
    }

    @Test
    void call_as_returns_the_body_result() throws Exception {
        assertEquals("beta", TenantContext.callAs("beta", TenantContext::current));
    }

    /** An inner binding shadows an outer one and the outer one comes back. */
    @Test
    void bindings_nest_and_unwind() throws Exception {
        String outerAfterInner = TenantContext.callAs("outer", () -> {
            String inner = TenantContext.callAs("inner", TenantContext::current);
            assertEquals("inner", inner);
            return TenantContext.current();
        });

        assertEquals("outer", outerAfterInner);
    }

    /**
     * TODO 4, and the assertion worth the document.
     *
     * A ScopedValue is NOT inherited by a thread you start yourself, not even a virtual one.
     * Inheritance happens only through a StructuredTaskScope fork, where the scope's lifetime
     * is known to be inside the binding's. A plain executor submit has no such guarantee -- the
     * task could outlive the binding -- so the JDK refuses to pretend.
     *
     * This is the single most surprising thing about ScopedValue, and it is the thing that
     * makes it a replacement for ThreadLocal only once you also adopt structured concurrency.
     */
    @Test
    void a_scoped_value_is_not_inherited_by_a_plain_virtual_thread() throws Exception {
        String seenByChild = TenantContext.callAs("acme", TenantContext::tenantInsideAVirtualThread);

        assertEquals("none", seenByChild, "a plain virtual thread does not inherit the binding");
    }
}
