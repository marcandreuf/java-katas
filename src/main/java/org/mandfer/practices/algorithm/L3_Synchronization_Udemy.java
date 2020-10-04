package org.mandfer.practices.algorithm;

/**
 * @author marcandreuf on 27/10/2014.
 */
public class L3_Synchronization_Udemy {

    private volatile int count = 0;

    private synchronized void increment(){
        count++;
    }

    private synchronized void read(){
        System.out.println(count);
    }

    public static void main(String[] args) {
        L3_Synchronization_Udemy test = new L3_Synchronization_Udemy();
        test.doWork();
    }

    public void doWork() {

        Thread one =  new Thread(new Runnable(){

            @Override
            public void run() {
                //for(int i= 0; i<10000; i++){
                    //increment();
                  while (count < 10000000) {
                      System.out.println(count);
                      //read();
                      try {
                          Thread.sleep(10);
                      } catch (InterruptedException e) {
                          System.out.println(e.getMessage());
                      }
                  }
                //}
            }
        });


        Thread two = new Thread(new Runnable(){

            @Override
            public void run() {
                for(int i=0; i<10000000; i++){
                    count++;
                }
            }
        });


        one.start();
        two.start();

        try {
            one.join();
            two.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("Count is: " + count);

    }
}
