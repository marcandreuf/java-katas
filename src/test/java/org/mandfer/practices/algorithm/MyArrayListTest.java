package org.mandfer.practices.algorithm;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 
 * @author marcandreuf
 */
public class MyArrayListTest {

    private MyArrayList myArrayList;

    @BeforeEach
    public void setUp(){
        myArrayList = new MyArrayList();
    }

    @Test
    @Disabled("Exercise: docs/modernization/10-sequenced-collections-and-httpclient.md. MyArrayList.size() returns the array length, so this reports 10 when empty.")
    public void testGetSize(){
        int size = myArrayList.size();
        assertTrue(size == 0);
    }

    @Test
    @Disabled("Exercise: docs/modernization/10-sequenced-collections-and-httpclient.md. MyArrayList is unfinished: no get(), no remove(), and add() overflows at 10.")
    public void testAddElement(){
        myArrayList.add("hello");
        assertTrue(myArrayList.size()>0);
    }
}
