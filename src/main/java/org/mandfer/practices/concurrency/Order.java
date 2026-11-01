package org.mandfer.practices.concurrency;

/**
 *
 * @author marcandreuf
 */
record Order(String name, String code){
    Order{
        if(name == null || name.isBlank()){
            throw new IllegalArgumentException("Name can not be blank.");
        }
    }
}