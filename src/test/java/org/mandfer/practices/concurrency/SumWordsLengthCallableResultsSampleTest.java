package org.mandfer.practices.concurrency;


import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * This is a sample exercise about how to use Callable objects.
 * 
 * @author marcandreuf
 *
 */
public class SumWordsLengthCallableResultsSampleTest {
	
	private static final String inputWords = "hello goodbye this is marc";
	
	@Test
	public void should_calculate_the_sum_of_lengths_of_words_in_a_sentance() throws ExecutionException, InterruptedException {
		int totalCharacters;
		
		SumWordsLength sumWordsLength = new SumWordsLength();
		totalCharacters = sumWordsLength.calculate(inputWords);

		assertThat("The total chars length test.",
				totalCharacters,
				is(equalTo(22)));
	}
	
	
	@Test
	public void should_calculate_the_length_of_a_word() throws Exception{
		SumWordsLength.WordLengthCallable wordLengthCallable =
				new SumWordsLength.WordLengthCallable("hello");
		
		int wordLength = wordLengthCallable.call();
		assertThat("Word length calculation",
				wordLength,
				is(equalTo(5)));
	}

	@Test
	public void should_throw_when_the_thread_is_interrupted(){
		assertThrows(InterruptedException.class, ()->{
			Thread.currentThread().interrupt();
			new SumWordsLength().calculate(inputWords);
		});

	}
	

}
