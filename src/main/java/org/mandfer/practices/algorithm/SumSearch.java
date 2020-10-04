package org.mandfer.practices.algorithm;

import java.util.Arrays;

/**
 * 
 * @author marcandreuf
 */
public class SumSearch {

	public static String search(int[] sequence, int sumTarget) {
		String result = "";
		Arrays.sort(sequence);
		int leftPointer=0, leftValue=0, rightVaue=0, tmpSum=0;
		int rightPointer=sequence.length-1;
		
		while(result.equals("") && leftPointer < rightPointer){
			leftValue = sequence[leftPointer];
			rightVaue = sequence[rightPointer];
			tmpSum = leftValue+rightVaue;
			
			if(tmpSum > sumTarget){
				rightPointer--;
			}else if(tmpSum < sumTarget){
				leftPointer++;
			}else{
				result = leftValue+","+rightVaue;
			}
		}
		
		return result;
	}

}
