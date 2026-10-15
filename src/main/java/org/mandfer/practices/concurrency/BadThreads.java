/* Oracle Java Tutorials sample code, modified. 3-clause BSD licence:
 * see licenses/oracle-bsd-3.txt, which must stay with this source. */

package org.mandfer.practices.concurrency;

public class BadThreads {

    //static String message;
	static EncapsulatedMessage message = new EncapsulatedMessage();
	
    private static class CorrectorThread extends Thread {

        public void run() {
            //try {
               // sleep(3000); 
            //} catch (InterruptedException e) {}
            // Key statement 1:
            //message = "Mares do eat oats.";
            message.setMessage("Mares do eat oats.");
        }
    }
    
    public static class EncapsulatedMessage {
    	
    	private String message = "";
    	
    	public synchronized void setMessage(String msg){
    		message = msg;
    	}
    	
    	public synchronized String getMessage(){
    		return message;
    	}
    }
    

    public static void main(String args[]) throws InterruptedException {
    	Thread correctorThread = new CorrectorThread();
    	correctorThread.start();    	
    	correctorThread.join();
    	
    	//message = "Mares do not eat oats.";
    	message.setMessage("Mares do not eat oats.");
        //Thread.sleep(2000);
        // Key statement 2:
        
        System.out.println(message.getMessage());
    }
}
