package org.mandfer.practices.concurrency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimpleThreadsTest {


    @Test
    void should_validate_the_usage_message(){
        assertEquals("Argument must be\n"+"an integer.", SimpleThreads.usage());
    }
}
