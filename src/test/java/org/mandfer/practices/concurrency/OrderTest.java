package org.mandfer.practices.concurrency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OrderTest {

    @Test
    void constructor_without_complaint(){
        assertDoesNotThrow(()->new Order(null,"1"));
        assertDoesNotThrow(()->new Order(" ","1"));
    }

    @Test
    void two_equal_orders(){
        Order o1 = new Order("a","1");
        Order o2 = new Order("a","1");
        assertEquals(o1, o2); // With records the equality is provided and compares value attributes.
    }

    @Test
    void print_order_to_string_has_at_symbol(){
        String o1String = new Order("a","1").toString();
        System.out.println("o1string: "+o1String);
        assertTrue(o1String.contains("name=a")); // With records the toString is also provided and prints the values
    }


}
