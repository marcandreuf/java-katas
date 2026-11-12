package org.mandfer.practices.sealedtypes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// @Disabled("Exercise: docs/modernization/02-sealed-types.md. Delete this line to begin.")
class ToolCallResultTest {

    @Test
    void routes_every_variant() {
        assertEquals("set:ok:hello", ToolCallResult.route(new ToolCallResult.Ok("hello")));
        assertEquals("denied:no scope", ToolCallResult.route(new ToolCallResult.Denied("no scope")));
        assertEquals("gave up:above", ToolCallResult.route(new ToolCallResult.Failed("above", 5)));
        assertEquals("failed:boom:3", ToolCallResult.route(new ToolCallResult.Failed("boom", 3)));
        assertEquals("timeout:250", ToolCallResult.route(new ToolCallResult.Timeout(250L)));
        assertEquals("cancelled", ToolCallResult.route(new ToolCallResult.Cancelled()));
    }
}
