package org.mandfer.practices.concurrency;

public class SimpleThreads {

	static void printMsgWithCurrentThreadsName(String message) {
		String threadName = Thread.currentThread().getName();
		System.out.println(threadName+": "+message);
	}

	private static class MessageLoop implements Runnable {
		public void run() {
			String importantInfo[] = { "Mares eat oats", "Does eat oats",
					"Little lambs eat ivy", "A kid will eat ivy too" };
			try {
				for (String msg : importantInfo) {
					Thread.sleep(4000);
					printMsgWithCurrentThreadsName(msg);
				}
			} catch (InterruptedException e) {
				printMsgWithCurrentThreadsName("I wasn't done!");
			}
		}
	}

	public static void main(String args[]) throws InterruptedException {
		long defaultDelayInMiliseconds = 1000 * 60 * 60;

		if (args.length > 0) {
			try {
				defaultDelayInMiliseconds = Long.parseLong(args[0]) * 1000;
			} catch (NumberFormatException e) {
				System.err.println("Argument must be an integer.");
				System.exit(1);
			}
		}

		printMsgWithCurrentThreadsName("Starting MessageLoop thread");
		long startTime = System.currentTimeMillis();
		Thread t = new Thread(new MessageLoop());
		t.start();

		printMsgWithCurrentThreadsName("Waiting for MessageLoop thread to finish");
		while (t.isAlive()) {
			printMsgWithCurrentThreadsName("Still waiting...");
			t.join(1000);
			if (((System.currentTimeMillis() - startTime) > defaultDelayInMiliseconds)
					&& 
				  t.isAlive()) {
				printMsgWithCurrentThreadsName("Tired of waiting!");
				t.interrupt();
				t.join();
			}
		}
		printMsgWithCurrentThreadsName("Finally!");
	}

}