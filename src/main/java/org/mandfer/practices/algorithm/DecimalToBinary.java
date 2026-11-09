package org.mandfer.practices.algorithm;

/**
 * 
 * @author marcandreuf
 */
public class DecimalToBinary {

    public void printBinaryFormat(int number){
        System.out.println(conversion(number));
    }

    public String conversion(int number){
        StringBuilder sbResult = new StringBuilder();
        int binary[] = new int[25];
        int index = 0;
        while(number > 0){
            binary[index++] = number%16;
            number = number/16;
        }
        for(int i = index-1;i >= 0;i--){
            sbResult.append(binary[i]);
        }
        return sbResult.toString();
    }

    static void main(){
        DecimalToBinary dtb = new DecimalToBinary();
        dtb.printBinaryFormat(25);
    }

}
