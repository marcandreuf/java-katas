package org.mandfer.practices;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CircularListTest {
	
	private static CircularList emptyList;
	
	@BeforeAll
	public static void setup_for_all_tests(){
		System.out.println("Init circular linked list.");
		emptyList = new CircularList();
	}
	
	@Test
	public void empty_list(){
		assertTrue(emptyList.isEmpty());
		assertTrue(emptyList._next==null);
	}
	
	@Test
	public void one_element_into_list(){			
		CircularList oneList = emptyList.insert(1);
		
		System.out.println(oneList.toString());
		
		assertTrue(oneList._value==1);
		assertTrue(oneList._next._value==1);		
	}
	
	@Test
	public void insert_after_one_element_list(){
		CircularList oneList = emptyList.insert(1);
		CircularList twoNodesList = oneList.insert(2);
		
		System.out.println(twoNodesList.toString());
		
		assertTrue(twoNodesList._value==1);
		assertTrue(twoNodesList._next._value==2);
		assertTrue(twoNodesList._next._next._value==1);
	}
	
	@Test
	public void insert_inverse_node_two(){
		CircularList oneElementList = emptyList.insert(2);
		CircularList twoNodesList = oneElementList.insert(1);
		
		System.out.println(twoNodesList);		
		
		assertTrue(oneElementList._value==2);
		assertTrue(twoNodesList._next._value==2);
		assertTrue(twoNodesList._next._next._value==1);
	}
	
	@Test
	public void insert_last_long_list(){
		CircularList longList = emptyList.insert(1).insert(2).insert(3).insert(4);				
		
		System.out.println(longList);		
		
		assertListOrder(longList);		
	}

	private void assertListOrder(CircularList longList) {
		assertTrue(longList._value==1);
		assertTrue(longList._next._value==2);
		assertTrue(longList._next._next._value==3);
		assertTrue(longList._next._next._next._value==4);
	}
	
	@Test
	public void insert_between_long_list(){
		CircularList longList = emptyList.insert(1).insert(4).insert(2).insert(3);				
		
		System.out.println(longList);		
		
		assertListOrder(longList);		
	}
	

}

