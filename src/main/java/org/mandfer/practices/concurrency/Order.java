package org.mandfer.practices.concurrency;

/**
 *
 * @author marcandreuf
 */
public class Order {
    
    private final String name;
    private final double code;

    public Order(String name, double code) {
        this.name = name;
        this.code = code;
    }  
    
}
