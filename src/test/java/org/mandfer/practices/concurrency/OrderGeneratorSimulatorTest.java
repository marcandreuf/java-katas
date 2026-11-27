package org.mandfer.practices.concurrency;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

public class OrderGeneratorSimulatorTest {

    @Test
    void should_add_new_orders_to_the_list(){
        BlockingQueue<Order> queue = new LinkedBlockingQueue<>();
        int ordersCount = 5;
        OrdersGeneratorSimulator producer = new OrdersGeneratorSimulator(queue, ordersCount);
        producer.run();

        assertEquals(ordersCount, queue.size(), "Orders queue is missing items.");
    }

    @Test
    void should_process_all_the_orders_and_stop_at_the_pill(){
        BlockingQueue<Order> queue = new LinkedBlockingQueue<>();
        Order o1 = new Order("testOrder", "order-1");
        Order o2 = new Order("testOrder", "order-2");
        Order o3 = new Order("testOrder", "order-3");
        queue.add(o1);
        queue.add(o2);
        queue.add(o3);
        queue.add(OrderProcessor.STOP_PILL);

        List<Order> processed = new ArrayList<>();
        /*With method reference*/
        //Consumer<Order> orderHandler = processed::add;

        /*With Consumer class implementation*/
        //Consumer<Order> orderHandler = new TracingOrderHandler(processed);

        /*With Lambda*/
        Consumer<Order> orderHandler = order -> {
            System.out.println("Lambda processing order "+order.code());
            processed.add(order);
        };

        OrderProcessor consumer = new OrderProcessor(queue, orderHandler);

        assertTimeoutPreemptively(Duration.ofSeconds(1), consumer::run);

        assertEquals(List.of(o1,o2,o3), processed);
    }

    class TracingOrderHandler implements Consumer<Order> {
        private final List<Order> processed;

        TracingOrderHandler(List<Order> processed){
            this.processed = processed;
        }

        @Override
        public void accept(Order order) {
            System.out.println("Processing order: "+order.name()+": "+order.code());
            processed.add(order);
        }
    }

    @Test
    void should_keep_the_interrupted_flag_when_consumer_gets_interrupted() throws InterruptedException {
        BlockingQueue<Order> queue = new LinkedBlockingQueue<>();
        List<Order> processed = new ArrayList<>();
        OrderProcessor consumer = new OrderProcessor(queue, processed::add);

        Thread interruptedConsumer = Thread.ofVirtual().name("Interrupted_test").start(consumer::run);
        interruptedConsumer.interrupt();

        assertEquals(0, queue.size());
        assertTrue(interruptedConsumer.join(Duration.ofSeconds(1)),
                "The consumer thread did not get interrupted.");
        assertTrue(interruptedConsumer.isInterrupted(),
                "The interrupted thread has not been flagged as interrupted.");

    }
}
