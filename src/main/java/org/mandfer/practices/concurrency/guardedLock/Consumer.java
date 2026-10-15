/* Oracle Java Tutorials sample code, modified. 3-clause BSD licence:
 * see licenses/oracle-bsd-3.txt, which must stay with this source. */

package org.mandfer.practices.concurrency.guardedLock;

import java.util.Random;
import java.util.concurrent.BlockingQueue;

//public class Consumer implements Runnable {
public class Consumer extends Thread {
	
    //private Drop drop;
	private BlockingQueue<String> blockingQueue;

    public Consumer(BlockingQueue<String> blockingQueue) {
        this.blockingQueue = blockingQueue;
    }

    public void run() {
        Random random = new Random();
        String message = "";
        try {
	        for (message = blockingQueue.take(); ! message.equals("DONE");
	             message = blockingQueue.take()) {
	            System.out.format("MESSAGE RECEIVED: %s%n", message);
	                Thread.sleep(random.nextInt(5000));
	        }
        } catch (InterruptedException e) {}
        System.out.println(message);
    }
}
