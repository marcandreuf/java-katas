package org.mandfer.practices.algorithm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mandfer.practices.algorithm.BinarySearch;

import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * This is a simple practice of binary search just for fun and play.
 * 
 * Based on this post.
 * http://community.topcoder.com/tc?module=Static&d1=tutorials&d2=binarySearch
 * 
 * Binary search formal definition:
 * [ binary search can be used if and only if for all x in S, p(x) implies p(y) for all y > x. ]
 * 
 * Meaning that p(x)=true=p(y) for all y >= x and also !p(x)=false=!p(y) for all y < x
 * 
 * Calc media = lo + (hi-lo)/2 this way the division is always positive and the rounding 
 * is down because we are looking for the minimum x that p(x)=true.
 * 
 * @author marcandreuf
 *
 */

public class BinarySearchTest {
	
	public static final int[] boundarySequenceSingleElement = {0};
	public static final int[] boundaryMinSequence = {0,5};
	public static final int[] oddSequence = {0,5,13,19,22,41,55,68,72,81,85};
	public static final int[] sequence = {0,5,13,19,22,41,55,68,72,81,85,98};
	public static final int[] negativeSequence = {-52,-45,-30,-16,-4,0,5,13,19,22,41,55,68,72,81,85,98};
	
	private BinarySearch binarySearch;
	
	@BeforeEach
	public void setUpBinarySearch(){
		binarySearch = new BinarySearch();	
	}
		
	@Test
	public void testMiddleElementBinarySearch(){		
		int target = 55;		
		int index = binarySearch.search(sequence, target);	
		assertTrue(index == 7);			
	}
	
	@Test
	public void testLowerBoundBinarySearch(){		
		int target = 0;			
		int index = binarySearch.search(sequence, target);
		assertTrue(index == 1);		
		int oddindex = binarySearch.search(oddSequence, target);
		assertTrue(oddindex == 1);
	}
	
	@Test
	public void testUpperBoundBinarySearch(){		
		int target = 98;			
		int index = binarySearch.search(sequence, target);
		assertTrue(index == 12);			
	}
	
	@Test
	public void testElementNotFoundBoundBinarySearch(){		
		int target = 225;			
		int index = binarySearch.search(sequence, target);
		assertTrue(index == -1);			
	}
	
	@Test
	public void testSmallerSequenceBoundBinarySearch(){		
		int target = 0;			
		int index = binarySearch.search(boundarySequenceSingleElement, target);
		assertTrue(index == 1);			
	}
	
	@Test
	public void testMinSequenceBoundBinarySearch(){		
		int target = 5;			
		int index = binarySearch.search(boundaryMinSequence, target);
		assertTrue(index == 2);			
	}
	
	@Test
	public void testNegativeSeqMiddleTargetBinarySearch(){		
		int target = 5;			
		int index = binarySearch.search(negativeSequence, target);
		assertTrue(index == 7);			
	}
	
	@Test
	public void testNegativeSeqLowerBoundTargetBinarySearch(){		
		int target = -52;			
		int index = binarySearch.search(negativeSequence, target);
		System.out.println("index neg: "+index);
		assertTrue(index == 1);		
	}

}
