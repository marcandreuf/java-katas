package org.mandfer.practices.concurrency;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimpleThreadsTest {


    @Test
    void should_validate_the_usage_message(){
        assertEquals("Argument must be\n"+"an integer.", SimpleThreads.usage());
    }

    @Test
    void should_start_three_vThread_tasks(){
        int maxNumTasks = 3;
        ThreadFactory tFactory = Thread.ofVirtual().name("task-",0).factory();
        try(var executor = Executors.newThreadPerTaskExecutor(tFactory)){
            for (int taskNum = 0; taskNum < maxNumTasks; taskNum++){
                executor.submit(()->SimpleThreads.printMsgWithCurrentThreadsName("I am a task."));
            }
        }
    }
}
