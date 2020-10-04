package com.java2novice.algos;

/**
 * Created by testingbjss on 07/10/2014.
 */
public class MyDeadlock {

    String str1 = "Java";
    String str2 = "UNIX";

//    Thread trd1 = new Thread("My Thread 1"){
//        public void run(){
//            while(true){
//                synchronized(str1){
//                    synchronized(str2){
//                        System.out.println("Thread 1");
//                    }
//                }
//            }
//        }
//    };
//
//    Thread trd2 = new Thread("My Thread 2"){
//        public void run(){
//            while(true){
//                synchronized(str2){
//                    synchronized(str1){
//                        System.out.println("Thread 2");
//                    }
//                }
//            }
//        }
//    };

    //TODO: not locking implementaion , pending to review m. threading ..
    Thread trd3 = new Thread(new MyLockingTread(str1, str2));
    Thread trd4 = new Thread(new MyLockingTread(str1, str2));

    private class MyLockingTread implements Runnable {

        private final String string1;
        private final String string2;

        public MyLockingTread(String str1, String str2){
            this.string1 = str1;
            this.string2 = str2;
        }

        @Override
        public void run() {
            while(true){
                synchronized (string1){
                    System.out.println(this.toString()+" "+string1 + string2+"1");
                    synchronized (string2){
                        System.out.println(this.toString()+" "+string1 + string2+"2");
                    }
                }
            }
        }
    }

    public static void main(String a[]){
        MyDeadlock mdl = new MyDeadlock();
        mdl.trd3.start();
        mdl.trd4.start();
    }
}
