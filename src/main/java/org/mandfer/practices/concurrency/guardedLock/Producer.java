/* Oracle Java Tutorials sample code, modified. 3-clause BSD licence:
 * see licenses/oracle-bsd-3.txt, which must stay with this source. */

package org.mandfer.practices.concurrency.guardedLock;

import java.util.Random;
import java.util.concurrent.BlockingQueue;

//public class Producer implements Runnable {
public class Producer extends Thread {
    //private Drop drop;
	private BlockingQueue<String> blockingQueue;
	
    public Producer(BlockingQueue<String> blockingQueue) {
        this.blockingQueue = blockingQueue;
    }

    public void run() {
        String importantInfo[] = {
            "Mares eat oats",
            "Does eat oats",
            "Little lambs eat ivy",
            "A kid will eat ivy too"
        };
        Random random = new Random();

        for (int i = 0; i < importantInfo.length; i++) {
            System.out.println("Producer try ot add "+importantInfo[i]);
            try {
            	blockingQueue.put(importantInfo[i]);
                Thread.sleep(random.nextInt(5000));
            } catch (InterruptedException e) {
            	System.out.println(e.getMessage());
            }
        }
        try{
        	blockingQueue.put("DONE");
        } catch (InterruptedException e) {
        	System.out.println(e.getMessage());
        }	
    }
}
