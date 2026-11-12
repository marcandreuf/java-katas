package org.mandfer.practices.mousemaze;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DirectionTest {

    private static final Direction dRight = Direction.RIGHT;

    @Test
    void old_switch_value_selection(){
        String directionName;
        switch (dRight){
            case RIGHT: directionName = Direction.RIGHT.name()+"1"; break;
            case DOWN: directionName = Direction.DOWN.name()+"2"; break;
            default: directionName = "No direction";
        }
        assertEquals("RIGHT1", directionName);
    }

    @Test
    void new_switch_value_selection(){
        String directionName = switch (dRight){
            case RIGHT -> dRight.name()+"1";
            case DOWN -> dRight.name()+"0";
            case LEFT -> dRight.name()+"2";
            case UP -> dRight.name()+"3";
        };
        assertEquals("RIGHT1", directionName);
    }

    @Test
    void should_return_the_opposite_with_switch_statements(){
        assertEquals(Direction.UP, Direction.DOWN.opposite());
        assertEquals(Direction.RIGHT, Direction.LEFT.opposite());
        assertEquals(Direction.DOWN, Direction.UP.opposite());
        assertEquals(Direction.LEFT, Direction.RIGHT.opposite());

        assertEquals(Direction.UP, Direction.DOWN.opposite1996());
        assertEquals(Direction.RIGHT, Direction.LEFT.opposite1996());
        assertEquals(Direction.DOWN, Direction.UP.opposite1996());
        assertEquals(Direction.LEFT, Direction.RIGHT.opposite1996());
    }
}
