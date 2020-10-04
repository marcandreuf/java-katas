package org.mandfer.practices;


public final class CircularList {
	
	int _value;
	CircularList _next;
	
	public CircularList(){
		_value = 0;
		_next = null;
	}
	
	public CircularList(int n){
		_value = n;
		_next = this;
	}	

	public CircularList insert(int n) {
		
		if(this.isEmpty()){
			return newNode(n);
		}else{	
			CircularList current = this;					
			if(isOneElementList(current)){
				if(isBiggerValue(n, current)){
					insertAfter(current, n);		
				}else{
					current = insertBefore(current, n);
				}
			}else{		
				CircularList head = this;
				do{					
					if(isBetweenCurrentAndNextValue(n, current)){						
						insertBetween(n, current);
						break;
					}else if(isLastElement(current, head)) {
						insertAtEnd(current, n, head);						
						break;
					}			
					current = current._next;					
				}while(current != head);
				current = head;
			}			
			return current;
		}
	}

	private boolean isBiggerValue(int n, CircularList current) {
		return current._value < n;
	}

	private CircularList newNode(int n) {
		return new CircularList(n);
	}

	private void insertAtEnd(CircularList current, int n, CircularList head) {
		CircularList newNode = newNode(n);
		newNode._next = head;
		current._next = newNode;		
	}

	private boolean isLastElement(CircularList current, CircularList head) {
		return current._next == head;
	}

	private void insertBetween(int n, CircularList current) {
		CircularList newNode = newNode(n);						
		newNode._next = current._next;
		current._next = newNode;
	}

	private void insertAfter(CircularList current, int n) {
		CircularList newNode = newNode(n);
		newNode._next = current;
		current._next = newNode;
	}
	
	private boolean isBetweenCurrentAndNextValue(int n, CircularList current) {
		return isBiggerValue(n, current) && n < current._next._value;
	}

	private CircularList insertBefore(CircularList current, int n) {
		CircularList newHead = newNode(n);
		newHead._next = current;
		current._next = newHead;
		return newHead;
	}

	private boolean isOneElementList(CircularList current) {
		return current._next == this;
	}
	
	
	public boolean isEmpty() {
		return _next == null;
	}
			
	public String toString(){
		StringBuffer sb = new StringBuffer();
		if(this._next != null){
			CircularList head = this;
			CircularList current = this;
			do{
				sb.append(current._value+",");
				current = current._next;
			}while(current != head);
		}
		return sb.toString();
	}
	
}
