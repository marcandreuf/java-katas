/* Oracle Java Tutorials sample code, modified. 3-clause BSD licence:
 * see licenses/oracle-bsd-3.txt, which must stay with this source. */

package org.mandfer.practices.collections;

import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

public class FindDups {
    public static void main(String[] args) {
    	
    	Comparator<String> comparator = new Comparator<>() {
			@Override
			public int compare(String s1, String s2) {
				return s1.compareToIgnoreCase(s2);
			}    		
    	};
    	
        SortedSet<String> s = new TreeSet<>(comparator);
        for (String a : args)
            if (!s.add(a))
                System.out.println("Duplicate detected: " + a);

        System.out.println(s.size() + " distinct words: " + s);
    }
}
