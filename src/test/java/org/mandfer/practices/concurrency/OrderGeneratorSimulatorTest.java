package org.mandfer.practices.concurrency;

import org.junit.jupiter.api.Test;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OrderGeneratorSimulatorTest {

    @Test
    void should_add_new_orders_to_the_list(){
        BlockingQueue<Order> queue = new LinkedBlockingQueue<>();
        int ordersCount = 5;
        OrdersGeneratorSimulator producer = new OrdersGeneratorSimulator(queue, ordersCount);
        producer.run();

        assertEquals(ordersCount, queue.size(), "Orders queue is missing items.");
    }




}
