package org.mandfer.practices.sealedtypes;

import javax.tools.Tool;

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

/**
 * What got worse:
 *
 * Rule of thumb: seal when the hierarchy is something your module controls and the variants rarely change,
 * such as protocol messages, AST nodes, or state machines like your DoorState. Don’t seal when the hierarchy
 * is meant to be extended by code you don’t own.
 */

sealed interface ToolCallResult permits ToolCallResult.Ok, ToolCallResult.Denied,
        ToolCallResult.Failed, ToolCallResult.Timeout, ToolCallResult.Cancelled {

    final class Ok implements ToolCallResult {
        private String payload;
        Ok(String payload){
            this.payload = payload;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload.concat(this.payload);
        }
    }
    record Denied(String reason) implements ToolCallResult {}
    record Failed(String message, int attempts) implements ToolCallResult {}
    record Timeout(long afterMillis) implements ToolCallResult {}
    record Cancelled() implements ToolCallResult {}

    static String route(ToolCallResult result) {
        return switch (result){
            case ToolCallResult.Ok o -> { o.setPayload("set:ok:"); yield o.getPayload();}
            case ToolCallResult.Denied d -> "denied:"+d.reason;
            case ToolCallResult.Failed f -> "failed:"+f.message+":"+f.attempts;
            case ToolCallResult.Timeout t -> "timeout:"+t.afterMillis;
            case ToolCallResult.Cancelled _ -> "cancelled";
        };
    }
}
