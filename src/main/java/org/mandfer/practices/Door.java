package org.mandfer.practices;

public class Door {

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

	public String toString() {
		if(open)
			return "open"; 
		else 
			return "close";
	}
}
