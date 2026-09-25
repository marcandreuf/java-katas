package org.mandfer.practices.sealedtypes;

/**
 * Four outcomes of one call, modelled as a closed set of types.
 *
 * Nothing else in this repo has a type hierarchy worth sealing, which is why this
 * exercise is new code rather than a rewrite of something older.
 *
 * TODO 1: make this a sealed interface permitting exactly Ok, Denied, Failed and Timeout.
 * TODO 2: keep each variant a record implementing it.
 * TODO 3: write route() as an exhaustive switch with NO default branch. If you cannot
 *         delete the default, the type is not actually sealed yet.
 * TODO 4: add a fifth variant and watch the compiler point at route(). That failure is
 *         the entire value of this exercise. Then delete the fifth variant again.
 */
public interface ToolCallResult {

    record Ok(String payload) implements ToolCallResult {}
    record Denied(String reason) implements ToolCallResult {}
    record Failed(String message, int attempts) implements ToolCallResult {}
    record Timeout(long afterMillis) implements ToolCallResult {}

    static String route(ToolCallResult result) {
        throw new UnsupportedOperationException("TODO 3");
    }
}
