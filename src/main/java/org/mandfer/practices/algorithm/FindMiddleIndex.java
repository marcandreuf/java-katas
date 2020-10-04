package org.mandfer.practices.algorithm;

/**
 * 
 * @author marcandreuf
 */
public class FindMiddleIndex {

    public static int findMiddleIndex(int[] numbers) throws Exception {

        int endIndex = numbers.length - 1;
        int startIndex = 0;
        int sumLeft = 0;
        int sumRight = 0;
        while (startIndex <= endIndex) {
            if (sumLeft > sumRight) {
                sumRight += numbers[endIndex--];
            } else {
                sumLeft += numbers[startIndex++];
            }
            //if (startIndex > endIndex) {
                if (startIndex >= endIndex && sumLeft == sumRight) {
                    return endIndex;
                }
                //else {
                //    throw new Exception(
                //            "Please pass proper array to match the requirement");
                //}
            //}
        }
        throw new Exception(
                "Please pass proper array to match the requirement");
    }

    public static void main(String a[]) {
        int[] num = { 2, 4, 4, 5, 4, 1 };
        try {
            System.out.println("Starting from index 0, adding numbers till index "
                    + findMiddleIndex(num) + " and");
            System.out.println("adding rest of the numbers can be equal");
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }
}
