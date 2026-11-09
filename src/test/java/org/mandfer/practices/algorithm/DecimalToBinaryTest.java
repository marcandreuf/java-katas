package org.mandfer.practices.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DecimalToBinaryTest {

    DecimalToBinary dtb = new DecimalToBinary();

    @Test
    void characterization_Hex_tests(){
        assertEquals("19", dtb.conversion(25));
        assertEquals("10", dtb.conversion(10));
        assertEquals("1515", dtb.conversion(255));
        assertEquals("", dtb.conversion(0));
        assertEquals("", dtb.conversion(-5));
    }

    @Test
    void characterization_Binary_tests(){
        assertEquals("11001", dtb.conversion(25,2));
        assertEquals("1010", dtb.conversion(10,2));
        assertEquals("11111111", dtb.conversion(255,2));
        assertEquals("", dtb.conversion(0,2));
        //assertEquals("", dtb.conversion(-5,2));
    }
}
