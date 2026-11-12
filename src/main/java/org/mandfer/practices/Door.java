package org.mandfer.practices;

public class Door {
	public static String OPEN = "open";
	public static String CLOSED = "closed";

	private boolean open;

	public Door() {
		open = false;
	}

	public boolean isOpen() {
		return open;
	}

	public void toggle() {
		open = !open;
	}

	@Override
	public String toString() {
		if(open)
			return OPEN;
		else 
			return CLOSED;
	}
}
