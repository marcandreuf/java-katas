package org.mandfer.practices.collections;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RandomFileLines {
	
	private static final int avgCharsPerLine = 50;
	
	public static void main(String[] args){
		String fileName = "src/main/resources/randomLinesText.txt";
		StringBuilder sb = new StringBuilder();
		int numLinesToPrint = Integer.valueOf(5);
				
		try {		
			List<String> lstLines = readFileLines(fileName);	        
			printInitialSummary(sb, numLinesToPrint, lstLines);	        
	        printRandomLines(sb, numLinesToPrint, lstLines);
	        System.out.println(sb.toString());
	    }catch (Exception e){
	    	System.out.println(e.getMessage());
	    }
	}

	private static void printRandomLines(StringBuilder sb, int numLinesToPrint,
			List<String> lstLines) {
		Random rline = new Random(System.currentTimeMillis());
		int index = 0;
		for(int i=0; i<numLinesToPrint;i++){
			index = rline.nextInt(lstLines.size()-1);
			sb.append(lstLines.get(index)+"\n");
		}
	}

	private static void printInitialSummary(StringBuilder sb,
			int numLinesToPrint, List<String> lstLines) {
		sb.append("Total file lines: "+lstLines.size()+"\n");
		sb.append("Num of random lines: "+numLinesToPrint+"\n");
		sb.append("\n");
	}

	private static List<String> readFileLines(String fileName) 
			throws Exception {
		BufferedReader br = null;
		try{
			File fin = new File(fileName);
			FileReader freader = new FileReader(fin);			
			long listSize = (fin.length()/avgCharsPerLine)*2;			
			List<String> lstLines = new ArrayList<String>((int)listSize);
			br = new BufferedReader(freader);	    
			String line = "";
	
			while ((line = br.readLine()) != null) {
			    if(!line.trim().isEmpty()){
			    	lstLines.add(line);	      
			    }
			}
			return lstLines;
		}catch(Exception e){
			System.out.println(e.getMessage());
			throw e;
		}finally {
	        try {
				br.close();
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}
	    }
	}

}
