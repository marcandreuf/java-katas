package org.mandfer.practices.algorithm;

/**
 * Created by marcandreuf on 20/10/2014.
 *
 * Write a program to implement your own ArrayList class. It should
 * contain add(), get(), remove(), size() methods. Use dynamic array logic.
 * It should increase its size when it reaches threshold.
 *
 */
public class MyArrayList {

    private String[] values;
    private int lastIndex;

    public MyArrayList(){
        values = new String[10];
    }

    public int size() {
        return values.length;
    }

    public void add(String value) {
        //TODO: control size ..
        values[lastIndex++] = value;
    }
}
