package org.mandfer.practices.sealedtypes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReplyFlowTest {

    @Test
    void no_sealed_class_requires_a_switch_default_option() {
        ReplyFlowNoSealed runs = new ReplyFlowNoSealed.Accepted(200);
        String result = switch (runs) {
            case ReplyFlowNoSealed.Accepted a -> "accepted #" + a.id();
            case ReplyFlowNoSealed.Rejected x -> "rejected: " + x.why();
            default -> throw new IllegalStateException("Unexpected value: " + runs);
        };
        assertEquals("accepted #200", result);
    }

    @Test
    void sealed_class_does_not_need_default_switch_option(){
        String sampleErrorMsg = "This is a test";
        ReplyFlow runs = new ReplyFlow.Rejected(sampleErrorMsg);
        String result = switch (runs) {
            case ReplyFlow.Accepted a -> "accepted #" + a.id();
            case ReplyFlow.Rejected r -> "rejected: " + r.why();
            case ReplyFlow.Pending _ -> "pending";
        };
        // Strict test
        assertEquals("rejected: "+sampleErrorMsg, result);
        // Flexible test
        assertTrue(result.contains(sampleErrorMsg));
    }
}
