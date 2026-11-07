package org.mandfer.practices.sealedtypes;

sealed interface ReplyFlow {
    record Accepted(int id) implements ReplyFlow {}
    record Rejected(String why) implements ReplyFlow {}
    record Pending() implements ReplyFlow{}
}
