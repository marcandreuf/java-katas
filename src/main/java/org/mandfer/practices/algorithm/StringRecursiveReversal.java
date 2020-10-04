package org.mandfer.practices.algorithm;

/**
 * 
 * @author marcandreuf
 */
public class StringRecursiveReversal {

    public String reverseString(String str){
        if(str.isEmpty()) return str;
        return reverseString(str.substring(1)) + str.charAt(0);
    }

    public static void main(String a[]){
        StringRecursiveReversal srr = new StringRecursiveReversal();
        System.out.println("Result: "+srr.reverseString("Java2novice"));
    }
}
