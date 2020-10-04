package org.mandfer.practices.concurrency;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 *
 * @author marcandreuf
 */
public class OrdersProcessingSystem {
    
    private static final ConcurrentLinkedQueue<Order> orderReceiverQueue = 
      new ConcurrentLinkedQueue<>();
    
    
    public static void main(String[] args){    
        OrdersProcessingSystem ordersProcessingSystem = new OrdersProcessingSystem();
        ordersProcessingSystem.start();        
    }
    

    private void start() {
        
    }
    
    
    class OrdersGeneratorSimulator implements Runnable{

        @Override
        public void run() {
            
        }
        
    }
    
    
}
