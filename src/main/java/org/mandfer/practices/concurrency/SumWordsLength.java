package org.mandfer.practices.concurrency;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;

public class SumWordsLength {

	public static final class WordLengthCallable implements Callable<Integer> {

		private final String word;
		
		public WordLengthCallable(String word){
			this.word = word;
		}
		
		@Override
		public Integer call() throws Exception {
			System.out.println("Calculating word "+this.word);
			return word.length();
		}

	}

	public int calculate(String inputWords) {		
		int sum = 0;
		ExecutorService threadsPool = Executors.newFixedThreadPool(3);
		Set<Future<Integer>> setThreadResults = new HashSet<Future<Integer>>();
		
		for(String word: inputWords.split(" ")){
			Callable<Integer> callableTask = new WordLengthCallable(word);
			Future<Integer> callableTaskFutureResult = threadsPool.submit(callableTask);
			setThreadResults.add(callableTaskFutureResult);
		}

		try {
			for(Future<Integer> result: setThreadResults){
				sum += result.get();
			}
			System.out.println("The sum of lengths is "+sum);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		
		return sum;
	}
	
	
	

}
