package org.mandfer.practices.concurrency;

import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

public class OrderProcessor implements Runnable {
    public static final Order STOP_PILL = new Order("POISON_PILL","000");
    private final BlockingQueue<Order> queue;
    private final Consumer<Order> handler;

    public OrderProcessor(BlockingQueue<Order> queue, Consumer<Order> handler) {
        this.queue = queue;
        this.handler = handler;
    }

    public void run() {
        try {
            while(true){
                //System.out.println("Order processor takes from queue");
                Order o = queue.take();
                if(o == STOP_PILL) return;
                handler.accept(o);
            }
        } catch (InterruptedException e) {
            String threadName = Thread.currentThread().getName();
            System.out.println("Order processor thread '"+threadName+"' interrupted.");
            //Restore the flag as we can not throw the exception from inside the run method of a Runnable Object.
            Thread.currentThread().interrupt();
        }
    }
}

