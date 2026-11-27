package org.mandfer.practices.concurrency;

/**
 *
 * @author marcandreuf
 */
public record Order(String name, String code){
    public Order{
        if(name == null || name.isBlank()){
            throw new IllegalArgumentException("Name can not be blank.");
        }
    }
}