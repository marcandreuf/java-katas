/* Oracle Java Tutorials sample code, modified. 3-clause BSD licence:
 * see licenses/oracle-bsd-3.txt, which must stay with this source. */

package org.mandfer.practices.concurrency.guardedLock;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;

public class ProducerConsumerExample {
    public static void main(String[] args) {
        //Drop drop = new Drop(); //Is the shared object.
        
    	BlockingQueue<String> bq = new SynchronousQueue<>();
    	
        Consumer c = new Consumer(bq);
        Producer p = new Producer(bq);
        
        c.start();
        p.start();
        
        //(new Thread(new Producer(drop))).start();
        //(new Thread(new Consumer(drop))).start();        
    }
}
