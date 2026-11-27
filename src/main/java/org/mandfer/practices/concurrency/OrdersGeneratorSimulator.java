package org.mandfer.practices.concurrency;

import java.util.concurrent.BlockingQueue;

class OrdersGeneratorSimulator implements Runnable {
    private final BlockingQueue<Order> queue;
    private final int count;

    OrdersGeneratorSimulator(BlockingQueue<Order> queue, int count){
        this.queue = queue;
        this.count = count;
    }
    @Override
    public void run() {
        for (int i = 0; i < count; i++) {
            Order order = new Order("test_" + i, "code:" + i);
            queue.add(order);
        }
    }

}
