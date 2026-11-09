package org.mandfer.practices.algorithm;

/**
 * 
 * @author marcandreuf
 */
public class DecimalToBinary {

    public void printHexFormat(int number){
        System.out.println(conversion(number, 16));
    }

    public void printBinaryFormat(int number){
        System.out.println(conversion(number, 2));
    }

    public String conversion(int number){
        return conversion(number, 16);
    }

    public String conversion(int number, int base){
        StringBuilder sbResult = new StringBuilder();
        int arrInts[] = new int[25];
        int index = 0;
        while(number > 0){
            arrInts[index++] = number%base;
            number = number/base;
        }
        for(int i = index-1;i >= 0;i--){
            sbResult.append(arrInts[i]);
        }
        return sbResult.toString();
    }

    static void main(){
        DecimalToBinary dtb = new DecimalToBinary();
        dtb.printBinaryFormat(25);
        dtb.printHexFormat(25);
    }

}
