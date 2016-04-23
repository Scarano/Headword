package ocr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.Set;

import ocr.util.Counter;
import ocr.util.TopNList;
import ocr.util.Counter.Count;

public class InexactDictionary {
	
	public static final char BOUNDARY_CHAR = ' ';

	// TODO: Will using the following class for looking up char n-grams improve performance?
	public static class Substring {
		char[] superstring;
		int start;
		int length;
		
		public boolean equals(Object other) {
			Substring otherSubstring = (Substring) other;
			if (length != otherSubstring.length)
				return false;
			for (int i = 0; i < length; i++)
				if (superstring[start+i] != otherSubstring.superstring[otherSubstring.start+i])
					return false;
			return true;
		}
		
//		public int hashCode() {
//		}
		// ...
	}
	
	
	ArrayList<String> words = new ArrayList<String>();
	LinkedList<String> length1Words = new LinkedList<String>();
	LinkedList<String> length2Words = new LinkedList<String>();
	HashMap<String, Integer> wordIndexes = new HashMap<String, Integer>();
	
	HashMap<String, ArrayList<Integer>> invertedIndex =
		new HashMap<String, ArrayList<Integer>>();
	
	public InexactDictionary() {
		
	}
	
	public void addWord(String word) {
		word = word.intern();
		
		if (wordIndexes.containsKey(word))
			return;
		
		int index = words.size();
		words.add(word);
		wordIndexes.put(word, index);
		
		if (word.length() == 1)
			length1Words.add(word);
		if (word.length() == 2)
			length1Words.add(word);
		
		for (String substring: substrings(word, word.length() <= 5)) {
			ArrayList<Integer> entries = invertedIndex.get(substring);
			if (entries == null) {
				entries = new ArrayList<Integer>();
				invertedIndex.put(substring, entries);
			}
			entries.add(index);
		}
	}

	/**
	 * Call this after adding corpus, and before looking up words.
	 */
	public void complete() {
		for (ArrayList<Integer> wordList: invertedIndex.values()) {
			wordList.trimToSize();
			Collections.sort(wordList);
		}
	}
	
	public String[] topMatches(String word, int n) {
		// XXX: These hacks may not be ideal
		if (word.equals(" "))
			return new String[] { " " };
		if (word.length() == 1)
			return length1Words.toArray(new String[0]);
		
		// XXX: Optimization opportunity? (join shortest substring match lists first)
		Counter<Integer> wordMatches = new Counter<Integer>();
		for (String substring: substrings(word, word.length() <= 4)) {
			ArrayList<Integer> substringMatches = invertedIndex.get(substring);
			if (substringMatches == null)
				continue;
			
			for (Integer match: substringMatches)
				wordMatches.add(match, substring.length());
		}
		
		TopNList<Integer> topMatches = new TopNList<Integer>(n);
		for (Entry<Integer, Count> entry: wordMatches.entries())
			topMatches.add(entry.getKey(), entry.getValue().value);
		
		Iterator<Integer> topMatchesIterator = topMatches.iterator();
		String[] results = new String[Math.min(n, wordMatches.size())];
		for (int i = 0; i < results.length; i++)
			results[i] = words.get(topMatchesIterator.next());
		return results;
	}
	
	/**
	 * Build list of length-3 (and if word is 4 for fewer chars long, length-2) substrings.
	 */
	static Set<String> substrings(String word, boolean includeBigrams) {
		String boundedWord = BOUNDARY_CHAR + word + BOUNDARY_CHAR;
		
		HashSet<String> results = new HashSet<String>();
		
		for (int i = 0; i < boundedWord.length() - 2; i++)
			results.add(boundedWord.substring(i, i + 3));
		
		if (includeBigrams) {
			for (int i = 0; i < boundedWord.length() - 1; i++)
				results.add(boundedWord.substring(i, i + 2));
		}
		
		return results;
	}
	
	public static void main(String[] args) {
		simpleTest();
	}

	static void simpleTest() {
		InexactDictionary dict = new InexactDictionary();
		dict.addWord("cat");
		dict.addWord("hat");
		dict.addWord("hit");
		dict.addWord("abcd");
		dict.addWord("abcd");
		dict.addWord("abcde");
		dict.addWord("abcdef");
		dict.addWord("abcdeg");
		
		System.out.println(dict.invertedIndex);
		System.out.println(Arrays.toString(dict.topMatches("cdef", 10)));
		System.out.println(Arrays.toString(dict.topMatches("cdef", 1)));
		System.out.println(Arrays.toString(dict.topMatches("cat", 4)));
	}
}












