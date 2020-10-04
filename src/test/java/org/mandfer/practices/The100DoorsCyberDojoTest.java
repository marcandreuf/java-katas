package org.mandfer.practices;

import org.junit.jupiter.api.Test;
import org.mandfer.practices.Door;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 100 doors in a row are all initially closed. You make
 * 100 passes by the doors. The first time through, you
 * visit every door and toggle the door (if the door is
 * closed, you open it; if it is open, you close it).
 * The second time you only visit every 2nd door (door
 * #2, #4, #6, ...). The third time, every 3rd door
 * (door #3, #6, #9, ...), etc, until you only visit
 * the 100th door.
 * 
 * Question: What state are the doors in after the last
 * pass? Which are open, which are closed?
 * 
 * [Source http://rosettacode.org]
 * 
 * @author marcandreuf@gmail.com
 *
 */

public class The100DoorsCyberDojoTest{

	@Test
	public void theDoorIsInitiallyOpen() {
		Door d = new Door();
		assertFalse(d.isOpen());
	}

	@Test
	public void create100ClosedDoors() {
		List<Door> doors = initDoors();
		for (Door d : doors) {
			assertFalse(d.isOpen());
		}
	}

	private List<Door> initDoors() {
		List<Door> lstDoors = new ArrayList<Door>(100);
		for(int i=0; i<100; i++){
			lstDoors.add(i, new Door());
		}
		return lstDoors;
	}

	@Test
	public void toggleADoor() {
		Door d = new Door();
		assertFalse(d.isOpen());
		d.toggle();
		assertTrue(d.isOpen());
		d.toggle();
		assertFalse(d.isOpen());
	}
	
	@Test
	public void loopDoorsInOrderOfN(){
		String loop1 = getLoop(1, 10);		
		System.out.println(loop1);		
		assertTrue("0,1,2,3,4,5,6,7,8,9,".equals(loop1));
		
		String loop2 = getLoop(2, 10);
		System.out.println(loop2);
		assertTrue("1,3,5,7,9,".equals(loop2));
		
		String loop3 = getLoop(3, 10);
		System.out.println(loop3);
		assertTrue("2,5,8,".equals(loop3));		
	}
	
	private String getLoop(int number, int size){
		String retVal="";
		int doorNumber;
			doorNumber=number;
			while(doorNumber <= size){
				retVal = retVal+(doorNumber-1)+",";
				doorNumber=doorNumber+number;
			};
		return retVal;
	}

	@Test
	public void goThroughAndToggleTheDoors() {
		StringBuilder sb = new StringBuilder();
		List<Door> doors = initDoors();
		Door currentDoor;
		int doorNumber;
		for (int i=1; i<=doors.size(); i++) {
			doorNumber=i;
			while(doorNumber <= doors.size()){
				currentDoor = doors.get(doorNumber-1);
				currentDoor.toggle();
				doorNumber=doorNumber+i;
			};
		}
		for(int i=0; i<doors.size(); i++){
			sb.append((i+1)+":"+doors.get(i)+"\n");
		}
		System.out.println(sb.toString());
	}

}