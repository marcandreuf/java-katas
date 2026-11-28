package org.mandfer.practices.modern;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Part 3 of 01-records.md
 */
public class RecordLimitsTest {

    enum OrderStatus {PLACED, PAID, SHIPPED, DELIVERED, CANCELLED}

    record Basket(List<String> items) {}
    record OrderShipment(String orderId, String express, int weightGrams, OrderStatus orderStatus){

        OrderShipment(String orderId, String express, int weightGrams){
            this(orderId, express, weightGrams, OrderStatus.PLACED);
        }

        public OrderShipment shipped(){
            return new OrderShipment(orderId(), express(), weightGrams(), OrderStatus.SHIPPED);
        }

    }
    record CustomerShipment(String customer){}
    record AddressShipment(String street, String city, String postcode){}

    @Test
    public void experiment_A_shallow_immutability() {
        ArrayList<String> fruits1 = createAppleFruitsList();
        Basket b1 = new Basket(fruits1);

        assertTrue(b1.items().contains("apple"));
        assertEquals(1, b1.items().size());

        fruits1.add("pear");

        assertEquals(2, b1.items().size());
        assertTrue(b1.items().contains("pear"));

        b1.items().add("plum");
        assertEquals(3, b1.items().size());
    }

    @Test
    public void experiment_A_basket_in_hash_set() {
        ArrayList<String> fruits1 = createAppleFruitsList();
        Basket b1 = new Basket(fruits1);

        HashSet<Basket> hb = new HashSet<>();
        hb.add(b1);

        assertTrue(hb.contains(b1));
        System.out.println("Contains the basket? " + hb.contains(b1));
        fruits1.add("pear");
        assertFalse(hb.contains(b1));
        System.out.println("Contains the basket? " + hb.contains(b1));
    }

    private static ArrayList<String> createAppleFruitsList() {
        ArrayList<String> fruits1 = new ArrayList<>();
        fruits1.add("apple");
        return fruits1;
    }

    @Test
    public void experiment_B_multiple_components() {

        record Shipment(String orderId, String customer, String street, String city,
                        String postcode, int weightGrams, boolean express) {

            // Optional: validation runs no matter how the record is created
            Shipment {
                java.util.Objects.requireNonNull(orderId, "orderId");
                if (weightGrams < 0) throw new IllegalArgumentException("weightGrams < 0");
            }
        }


        record ShipmentCompose(OrderShipment orderShipment, CustomerShipment customerShipment,
                        AddressShipment addressShipment){}


    }

    @Test
    public void experiment_C_becoming_mutable(){
        OrderShipment orderShipmentWithStatus = new OrderShipment("01", "yes",
                10, OrderStatus.DELIVERED);

        OrderShipment orderShipmentDefaultStatus = new OrderShipment("02", "no", 12);


        OrderShipment orderShipmentShipped = orderShipmentDefaultStatus.shipped();
        assertNotEquals(orderShipmentDefaultStatus, orderShipmentShipped);


    }

}
