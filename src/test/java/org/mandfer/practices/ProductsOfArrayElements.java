package org.mandfer.practices;

import java.util.Arrays;
import org.apache.commons.lang.ArrayUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Given an input array of integers, return an array where the ith element is
 *  the product of the elements of the input array except the ith element.
 *  
 *  
 * @author marcandreuf@gmail.com
 *
 */

public class ProductsOfArrayElements {
	
	
	private int[] arInput = {1,3,5,2};
	
	
	@Test
	public void otherElementsOfTheArray(){		
		int[] arOutput = otherValues(arInput, 1);		
		assertTrue(Arrays.equals(new int[]{3,5,2}, arOutput));		
	}
	
	private int[] otherValues(int[] inputArray, int elem){
	    return ArrayUtils.removeElement(inputArray, elem);
	}
	
	@Test
	public void productOfArrayElements(){
		arInput = new int[]{3,5,2};
		int product = product(arInput);
		assertTrue(product == 30);
		
		arInput = new int[]{1,5,2};
		product = product(arInput);
		assertTrue(product == 10);
	}


	private int product(int[] inputArray){  
	   Integer retVal = 1; 
	   for(int elem : inputArray){
	        retVal = retVal * elem;
	    }
	   return retVal;
	}
	
	@Test
	public void productsOfWholeArray(){		
		int[] arOutput = productsArray();		
		assertTrue(Arrays.equals(arOutput, new int[]{30,10,6,15}));		
	}

	private int[] productsArray(){
	    int[] arOutupt = new int[arInput.length];
	    for(int i=0; i<arInput.length; i++){    
	       arOutupt[i] = product(otherValues(arInput, arInput[i]));
	    }
	    return arOutupt;
	}	

}
