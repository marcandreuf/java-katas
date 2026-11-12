package org.mandfer.practices.concurrency;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

public class OrderTest {

    @Test
    void should_not_create_orders_with_blank_names(){
        assertThrowsExactly(IllegalArgumentException.class, ()->new Order(null,"1"));
        assertThrows(IllegalArgumentException.class, ()->new Order(" ","1"));
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

    @Test
    void recordPatterVsAccessors(){
        Order order = new Order("book", "A1");

        String a = order.name() + " : " +order.code();

        /*
        * A Pattern matching with nothing to choose between is just a longer way to get the value of the object.
        * */
        String b = switch(order){
            case Order(var n, var c) -> n+" : "+c;
        };
    }


}
