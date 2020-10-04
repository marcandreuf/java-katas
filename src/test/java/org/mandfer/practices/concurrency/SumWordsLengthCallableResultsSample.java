package org.mandfer.practices.concurrency;


import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * This is a sample exercise about how to use Callable objects.
 * 
 * @author marcandreuf@gmail.com
 *
 */
public class SumWordsLengthCallableResultsSample {
	
	private static final String inputWords = "hello goodbye this is marc";
	
	@Test
	public void sumWordsLength(){
		int charsRangeInit = 10;
		int charsRangeEnd = 30;
		
		int totalCharacters = 0;
		
		SumWordsLength sumWordsLength = new SumWordsLength();
		totalCharacters = sumWordsLength.calculate(inputWords);
		
		assertThat("The total chars lenght test.", totalCharacters,
				is(both(greaterThan(charsRangeInit)).and(lessThan(charsRangeEnd))));
	}
	
	
	@Test
	public void calcWordLength(){
		
		SumWordsLength.WordLengthCallable wordLengthCallable = 
				new SumWordsLength.WordLengthCallable("hello");
		
		int wordLength;
		try {
			wordLength = wordLengthCallable.call();
			assertThat("Word leght calculation", wordLength, is(equalTo(5)));
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
				
	}
	
	@Test
	public void splitStringOfWordsBySpace(){		
		String[] arInputWrods = inputWords.split(" ");
		
		assertThat("Split of words by space works", arInputWrods.length, is(equalTo(5)));		
	}

}
