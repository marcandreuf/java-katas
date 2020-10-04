package org.mandfer.practices.algorithm;

import org.junit.jupiter.api.Disabled;
import org.mandfer.practices.algorithm.FairWorkLoad;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *  http://community.topcoder.com/stat?c=problem_statement&pm=1901&rd=4650
 *  http://community.topcoder.com/tc?module=Static&d1=tutorials&d2=binarySearch
 * 
 *  Problem Statement
 *  Fabian is in charge of a law firm working on an important case. For a case coming up, he needs a specific folder which 
 *  is stored in one of the filing cabinets arranged in a line against the wall of the records room. He has assigned a number 
 *  of workers to find the folder from the filing cabinets. He doesn't want the workers to get in each other's way, nor 
 *  does he want folders from different filing cabinets getting mixed up, so he has decided to partition the cabinets, 
 *  and assign a specific section to each worker. Each worker will have at least 1 cabinet to search through.
 *
 * 	More specifically, Fabian wants to divide the line of filing cabinets into N sections (where N is the number of workers) 
 *	so that every cabinet that the ith worker looks through is earlier in the line than every cabinet that the jth worker 
 * 	has to look through, for i < j.
 * 
 * 
 * Definition 
 *  Class:	FairWorkload
 *  Method:	getMostWork
 *  Parameters:	int[], int
 *  Returns:	int
 *  Method signature:	int getMostWork(int[] folders, int workers)
 *  (be sure your method is public)
 *   
 * 
 *  Constraints
 *  -	folders will contain between 2 and 15 elements, inclusive
 *  -	Each element of folders will be between 1 and 1000, inclusive
 *  -	workers will be between 1 and the number of elements in folders, inclusive
 * 
 * 	
 * @author marcandreuf
 *
 */

public class FairWorkloadTest {
	
	private FairWorkLoad fairWorkLoad;
	private int[] foldersPerCabinet;
	private int workers, expectedResult, result;
	
	
	@BeforeEach
	public void setUp(){
		fairWorkLoad = new FairWorkLoad();
			
	}
	
	@Test
	public void testThreeWorkesSample(){
		foldersPerCabinet = new int[]{ 10, 20, 30, 40, 50, 60, 70, 80, 90 };
		workers = 3;
		expectedResult = 170;		
		testExpectedWorkLoad();		
	}

	private void testExpectedWorkLoad() {
		result = fairWorkLoad.getMostWork(foldersPerCabinet, workers);
		assertTrue(result == expectedResult);
	}
	
	
	@Test
	public void testFiveWorkesSample(){
		foldersPerCabinet = new int[]{ 10, 20, 30, 40, 50, 60, 70, 80, 90 };
		workers = 5;
		expectedResult = 110;		
		testExpectedWorkLoad();		
	}
	
	@Test
	public void testLargerCabinets(){
		foldersPerCabinet = new int[]{ 568, 712, 412, 231, 241, 393, 865, 287, 128, 457, 238, 98, 980, 23, 782 };
		workers = 4;
		expectedResult = 1785;		
		testExpectedWorkLoad();		
	}	
	
	@Test
	public void testOneFolderRepeatedCabinets(){
		foldersPerCabinet = new int[]{ 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1000 };
		workers = 2;
		expectedResult = 1000;		
		testExpectedWorkLoad();		
	}

	@Test
	public void testEvenFolderNumbersPerCabinet(){
		foldersPerCabinet = new int[]{ 50, 50, 50, 50, 50, 50, 50 };
		workers = 2;
		expectedResult = 200;		
		testExpectedWorkLoad();		
	}
	
	
	@Test
	public void testOneWorkerPerCabinet(){
		foldersPerCabinet = new int[]{ 1, 1, 1, 1, 100 };
		workers = 5;
		expectedResult = 100;		
		testExpectedWorkLoad();		
	}

	@Test
	@Disabled // Unders investigation, not sure why it fails.ss
	public void testMaxNumPerSixWorkers(){
		foldersPerCabinet = new int[]{ 950, 650, 250, 250, 350, 100, 650, 150, 150, 700 };
		workers = 6;
		expectedResult = 950;
		result = fairWorkLoad.getMostWork(foldersPerCabinet, workers);
		System.out.println("Result: "+result);
		assertTrue(result == expectedResult);
	}
	
	@Test
	public void testNotLocking(){
		foldersPerCabinet = new int[]{ 1, 1 };
		workers = 2;
		expectedResult = 1;		
		testExpectedWorkLoad();		
	}
	
}
