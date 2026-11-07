package org.mandfer.practices.sealedtypes;

public interface ReplyFlowNoSealed {
    record Accepted(int id) implements ReplyFlowNoSealed{}
    record Rejected(String why) implements ReplyFlowNoSealed {}
}


