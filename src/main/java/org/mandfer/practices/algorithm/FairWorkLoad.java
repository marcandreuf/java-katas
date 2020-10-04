package org.mandfer.practices.algorithm;

/**
 * 
 * @author marcandreuf
 */
public class FairWorkLoad {

	private int low;
	private int high;
	private int median;
	private int requiredWorkers;
	private int currentLoad;
	
	public int getMostWork(int[] cabinetFolders, int workers) {
		low = max(cabinetFolders);
		high = sum(cabinetFolders);		
		
		while(withInRange()){
			calcMedian();						
			requiredWorkers = 1;
			currentLoad = 0;
			
			String line = "";
						
			for(int numFoldersInCabinet: cabinetFolders){				
				
				
				if(currentLoad + numFoldersInCabinet <= median){					
					line = line.concat(" "+numFoldersInCabinet);
					currentLoad += numFoldersInCabinet;
				}else{
					//TODO: Solve Test 6 failue and clean up sysout traces.
//					if(requiredWorkers==1 && numFoldersInCabinet > median){
//						System.out.println("Here is the strange case");
//						line = line.concat(" "+numFoldersInCabinet);
//						currentLoad = numFoldersInCabinet;
//					}else{				
						line = line.concat("| "+numFoldersInCabinet);
						requiredWorkers++;
						currentLoad = numFoldersInCabinet;
					//}
				}	
			}
			
			if(requiredWorkers <= workers){				
				high = median;
			}else{
				low = median+1;
			}
			
			if(requiredWorkers == workers) {
				System.out.println("For median: "+median);
				System.out.println(line);				
			}
		}		
		return low;
	}
	

	private int max(int[] sequence){
		int max = sequence[0];
		for(int value:sequence) if(value>0) max = value;
		return max;
	}
	
	private int sum(int[] sequence) {
		int sum = 0;
		for(int value:sequence) sum += value;
		return sum;
	}
	
	private void calcMedian() {
		median = low + (high-low)/2;
	}
	
	private boolean withInRange() {
		return low<high;
	}

}
