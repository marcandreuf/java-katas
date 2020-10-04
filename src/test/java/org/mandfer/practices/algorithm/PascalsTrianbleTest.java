package org.mandfer.practices.algorithm;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 
 * @author marcandreuf
 */
public class PascalsTrianbleTest {

    private PascalsTriangle pascalsTriangle = 
      new PascalsTriangle();


    @Test
    public void shouldPrintAWellFormattedTriangleOfNLines(){

        String triangle = pascalsTriangle.printPascal(6);

        String[] lines = triangle.split("\n");
        assertTrue(lines[0].startsWith("         1"));
        assertTrue(lines[1].startsWith("       1  1"));
        assertTrue(lines[2].startsWith("      1  2  1"));
        assertTrue(lines[3].startsWith("    1  3  3  1"));
        assertTrue(lines[4].startsWith("   1  4  6  4  1"));
        assertTrue(lines[5].startsWith(" 1  5  10 10 5  1"));
        assertTrue(lines[6].startsWith("1  6  15 20 15 6  1"));
    }
}
