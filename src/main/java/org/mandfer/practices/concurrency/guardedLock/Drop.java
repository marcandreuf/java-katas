/* Oracle Java Tutorials sample code, modified. 3-clause BSD licence:
 * see licenses/oracle-bsd-3.txt, which must stay with this source. */

package org.mandfer.practices.concurrency.guardedLock;

public class Drop {
    // Message sent from producer
    // to consumer.
    private String message;
    // True if consumer should wait
    // for producer to send message,
    // false if producer should wait for
    // consumer to retrieve message.
    private boolean empty = true;

    public synchronized String take() {
        // Wait until message is
        // available.
        while (empty) {
            try {
            	System.out.println("Consumer take waits");            	
                wait();
            } catch (InterruptedException e) {}
        }
        // Toggle status.
        System.out.println("Consumer take toggle status to true");
        empty = true;
        // Notify producer that
        // status has changed.
        System.out.println("Consumer take notifyall.");        
        notifyAll();
        
        System.out.println("Consumer take message returns "+message);
        return message;
    }

    public synchronized void put(String message) {
        // Wait until message has
        // been retrieved.
        while (!empty) {
            try { 
            	System.out.println("Producer put waits");
                wait();
            } catch (InterruptedException e) {}
        }
        // Toggle status.
        System.out.println("Producer put toggle empty to false");
        empty = false;
        // Store message.
        
        System.out.println("Producer put message "+message);
        this.message = message;
        // Notify consumer that status
        // has changed.
        
        System.out.println("Producer put notifyall.");
        notifyAll();
    }
}
