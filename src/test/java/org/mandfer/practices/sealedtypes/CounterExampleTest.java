package org.mandfer.practices.sealedtypes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class CounterExampleTest {

    // Door status variants carry extra data as the angle degrees of the door.
    sealed interface DoorState permits DoorState.Open, DoorState.Closed {
        record Open(long angle) implements DoorState {}
        record Closed(long angle) implements DoorState {
            Closed(){
                this(0);
            }
        }
    }

    // Use simple enums when the variants do not have extra data.
    enum EDoorState {OPEN, CLOSED}

    /**
     Enums can also carry data, passed in through a constructor. The difference from sealed types is
    that each enum constant is a single, fixed instance: its data is set once and every constant has
    the same fields. A sealed type fixes the set of subtypes instead, but you can create any number
    of instances of them, each with its own data (for example a new Open state with an angle of 45),
    and each subtype can define different fields.
     */
    enum AngledEDoorState {
        OPEN(90),
        CLOSED(0);

        private final int angle;

        AngledEDoorState(int angle) {      // enum constructors are implicitly private
            this.angle = angle;
        }

        int angle() {
            return angle;
        }
    }


    @Test
    void exercise_door_states(){
        DoorState openState = new DoorState.Open(10);
        String result = switch (openState){
            case DoorState.Open s -> "open:"+s.angle;
            case DoorState.Closed s -> "closed:"+s.angle;
        };
        assertEquals("open:10", result);

        EDoorState eOpenState = EDoorState.OPEN;
        String eResult = switch (eOpenState){
            case OPEN -> "eOpen";
            case CLOSED -> "eClose";
        };
        assertEquals("eOpen", eResult);

        AngledEDoorState angledEDoorState = AngledEDoorState.OPEN;
        String angledResult = switch (angledEDoorState){
            case OPEN -> "open at " + angledEDoorState.angle();
            case CLOSED -> "closed";
        };
        assertEquals("open at 90", angledResult);
    }
}
