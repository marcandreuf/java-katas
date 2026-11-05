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
        /*
          The caller keeps the list. Build an ArrayList holding "apple", pass it to a Basket,
          then add "pear" to your list. Assert what basket.items() contains.
          The reader gets the list. Call basket.items().add("plum"). Does it compile?
          Does it throw? What does the basket hold now?
          Yes it compiles and the list can be modified.
         */
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
        /*
          Break a HashSet. Put a basket in a HashSet, then mutate the list it was built from.
          Ask the set contains(basket), about the very same object.
          Then explain the answer using what Part one taught you about how a record's hashCode is computed.
          (This is the HashMap key warning from Part one, step 2, arriving by a side door.)
          Answer: A record is its values, so the hashCode of a record with a mutable list changes when
          the list contents change.
         */
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
        /*
        Answers to the Experiment B section:

        1. Without IDE it is difficult to create the record with the right order of arguments.
        2. Swapping arguments and having to add tests is a rabbit hole that we do not need to go.
        An option to validate the record is to use static validation blocks.
        3. Adding one component could have a major impact to the whole codebase if the records is
        widely used.
        4. If we go into the route of implementing a Builder for a record we better implement a normal
        Shipment class so we can control the private visibility of the constructors, and we have full
        control of the code.
        The fix is to create more record types and group attributes in a more semantic way.
         */

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
        /*
        1. To add a status component to the Order record and avoid major refactoring we could
        add multiple constructors of the record where we can define the default value
        of any new component.
         */
        OrderShipment orderShipmentWithStatus = new OrderShipment("01", "yes",
                10, OrderStatus.DELIVERED);

        OrderShipment orderShipmentDefaultStatus = new OrderShipment("02", "no", 12);

        /*
        * 2. Well records are targeted to be used as immutable value objects, so if we need to change state we
        * may need to consider refactoring to adapting the record to a normal class.
        * */

        /*
        3. The OrderShipment records with different status will be not equals.
         */
        OrderShipment orderShipmentShipped = orderShipmentDefaultStatus.shipped();
        assertNotEquals(orderShipmentDefaultStatus, orderShipmentShipped);

        /*
        4 and 5. Yes going back to a class we go back to mutability and all the necessary implementations
        for all the methods that we get with a records type.
         */

    }

}
