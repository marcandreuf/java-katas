package org.mandfer.practices.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DecimalToBinaryTest {

    DecimalToBinary dtb = new DecimalToBinary();

    @Test
    void characterization_tests(){
        assertEquals("19", dtb.conversion(25));
        assertEquals("10", dtb.conversion(10));
        assertEquals("1515", dtb.conversion(255));
        assertEquals("", dtb.conversion(0));
        assertEquals("", dtb.conversion(-5));
    }
}
