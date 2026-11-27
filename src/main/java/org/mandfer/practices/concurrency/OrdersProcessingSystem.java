package org.mandfer.practices.concurrency;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *
 * @author marcandreuf
 */
public class OrdersProcessingSystem {
    
    private static final BlockingQueue<Order> blockingQueue =
      new LinkedBlockingQueue<>();
    
    
    public static void main(String[] args){    
        OrdersProcessingSystem ordersProcessingSystem = new OrdersProcessingSystem();
        ordersProcessingSystem.start();        
    }
    

    private void start() {
        
    }


}
