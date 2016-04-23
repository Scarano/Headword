package edu.neu.ccs.headword;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import edu.neu.ccs.headword.util.GUtil;

public class Vocabulary implements Iterable<String> {
	
	private ArrayList<String> wordStrings = new ArrayList<String>();
	private HashMap<String, Integer> wordIDs = new HashMap<String, Integer>();
	private boolean complete = false;
	
	public Vocabulary() {
	}
	
	public Vocabulary(String vocabFile) {
		for (String word: GUtil.loadUtf8Lines(vocabFile))
			add(word);
		complete();
	}

	public int add(String wordString) {
		if (complete)
			throw new Error("Attempt to add to vocabulary already marked as complete.");
		
		Integer id = wordIDs.get(wordString);
		if (id == null) {
			id = wordStrings.size();
			wordIDs.put(wordString, id);
			wordStrings.add(wordString);
		}
		return id;
	}
	
	public Integer wordID(String wordString) {
		return wordIDs.get(wordString);
	}
	public String wordString(int id) {
		return wordStrings.get(id);
	}

	@Override
	public Iterator<String> iterator() {
		return wordStrings.iterator();
	}
	
	public int size() {
		if (!complete)
			throw new Error("Attempt to get size of vocabulary before complete.");
		return wordStrings.size();
	}
	
	public void complete() {
		complete = true;
	}

	public static void main(String[] args) {

	}

}
