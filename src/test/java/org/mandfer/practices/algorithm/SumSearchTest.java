package org.mandfer.practices.algorithm;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SumSearchTest {
	
	private String expectedValue;
	private int sumTarget;		
	private int[] sequence = {1,2,4,6};		
	
	@Test
	public void shouldReturnAPairWithFirstElementOfTheList(){
		expectedValue = "1,4";
		sumTarget = 5;
		runTestSample(expectedValue, sumTarget, sequence);		
	}

	private void runTestSample(String expectedValue, int sumTarget,
			int[] sequence) {
		String pair = SumSearch.search(sequence, sumTarget);	
		System.out.println(pair);
		assertTrue(pair.equals(expectedValue));
	}
	
	
	@Test
	public void shouldReturnAPairWithSecondElementOfTheList(){
		expectedValue = "2,6";
		sumTarget = 8;		
		runTestSample(expectedValue, sumTarget, sequence);		
	}
	
	
	@Test
	public void shouldReturnEmptyStringAsNonMatchingSum() {
		expectedValue = "";
		sumTarget = 12;
		runTestSample(expectedValue, sumTarget, sequence);
	}

	@Test
	public void shouldReturnEmptyStringAlsoNonMatchingSum() {
		expectedValue = "";
		sumTarget = -3;
		runTestSample(expectedValue, sumTarget, sequence);
	}

}
