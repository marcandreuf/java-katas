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
    @Disabled
    public void testGetSize(){
        int size = myArrayList.size();
        assertTrue(size == 0);
    }

    @Test
    @Disabled
    public void testAddElement(){
        myArrayList.add("hello");
        assertTrue(myArrayList.size()>0);
    }
}
