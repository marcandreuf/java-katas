package org.mandfer.practices.algorithm;

/**
 * 
 * @author marcandreuf
 */
public class BinarySearch {
	
	private static final int oneBased = 1;
	
	private int low;
	private int high;
	private int mid;	

	public int search(int[] sequence, int target) {
		low = 0;
		high = sequence.length;
		
		while (withInRange()){
			calcMedian();
			if(targetFound(sequence, target)){
				return mid + oneBased;
			}else if(targetIsBigger(sequence, target)){
				moveLowerBoundUp();				
			}else{
				moveHigherBoundDown();
			}
		}
		return -1;
	}

	private boolean withInRange() {
		return low < high || isFirstElement();
	}

	private boolean isFirstElement() {
		return low==0 && high==0;
	}
	private void calcMedian() {
		mid = low + (high-low)/2;
	}
	private boolean targetFound(int[] sequence, int target) {
		return sequence[mid] == target;
	}
	private boolean targetIsBigger(int[] sequence, int target) {
		return sequence[mid] < target;
	}
	private void moveLowerBoundUp() {
		low = mid + 1;
	}
	private void moveHigherBoundDown() {
		high = mid - 1;
	}

}
