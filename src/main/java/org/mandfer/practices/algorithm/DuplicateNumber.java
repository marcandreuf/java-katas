package org.mandfer.practices.algorithm;

import java.util.Arrays;
import java.util.List;

/**
 * 
 * @author marcandreuf
 */
public class DuplicateNumber {

    public int findDuplicateNumber(List<Integer> numbers){

        int highestNumber = numbers.size() - 1;
        int total = getSum(numbers);
        int duplicate = total - calcGaussTotal(highestNumber);
        return duplicate;
    }

    private int calcGaussTotal(int highestNumber) {
        return (highestNumber*(highestNumber+1)/2);
    }

    public int getSum(List<Integer> numbers){

        int sum = 0;
        for(int num:numbers){
            sum += num;
        }
        return sum;
    }

    public static void main(String a[]){

        Integer[] nums = {1,2,3,3,4,5};
        List<Integer> numbers = Arrays.asList(nums);

//        List<Integer> numbers = new ArrayList<Integer>();
//        for(int i=1;i<30;i++){
//            numbers.add(i);
//        }
        //add duplicate number into the list
//        numbers.add(21);

        DuplicateNumber dn = new DuplicateNumber();
        System.out.println("Duplicate Number: "+dn.findDuplicateNumber(numbers));
    }
}
