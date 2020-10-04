package org.mandfer.practices.collections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class ArgsRandom {

	public static void main(String[] args){
		List<String> lstArgs = new ArrayList<String>(Arrays.asList(args));
		Collections.shuffle(lstArgs);		
		for(String str : lstArgs){
			System.out.println(str);
		}
		
	}
	
}
