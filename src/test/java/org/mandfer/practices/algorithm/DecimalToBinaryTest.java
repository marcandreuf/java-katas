package org.mandfer.practices.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DecimalToBinaryTest {

    DecimalToBinary dtb = new DecimalToBinary();

    @Test
    void characterization_Hex_tests(){
        assertEquals("19", dtb.conversion(25));
        assertEquals("a", dtb.conversion(10));
        assertEquals("ff", dtb.conversion(255));
        assertEquals("0", dtb.conversion(0));
    }

    @Test
    void characterization_Binary_tests(){
        assertEquals("11001", dtb.conversion(25,2));
        assertEquals("1010", dtb.conversion(10,2));
        assertEquals("11111111", dtb.conversion(255,2));
        assertEquals("0", dtb.conversion(0,2));
        assertEquals("10000000000000000000000000",
                dtb.conversion(33554432, 2));
        assertEquals("1111111111111111111111111111111",
                dtb.conversion(Integer.MAX_VALUE, 2)); // All 31 digits.
    }

    @Test
    void only_positive_conversions(){
        assertThrows(IllegalArgumentException.class, ()-> dtb.conversion(-5));
        assertThrows(IllegalArgumentException.class, ()-> dtb.conversion(-5, 2));

    }
}
