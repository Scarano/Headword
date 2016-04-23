package edu.neu.ccs.headword.util;

import java.util.Iterator;
import java.util.TreeSet;

/**
 * A set of scored items that only keeps the N with the highest score
 * 
 * TODO: Come back to this and make sure this is actually an efficient implementation....
 * 
 * Note that because ScoredItem does not implement equals() properly, duplicate items
 * Should not be added. Think of this as a list, not a set.
 */
public class TopNList<T extends Comparable<T>> implements Iterable<T> {
	
	public static class ScoredItem<T extends Comparable<T>>
		implements Comparable<ScoredItem<T>>
	{
		public T item;
		public double score;
		
		public ScoredItem(T item, double score) {
			this.item = item;
			this.score = score;
		}
		
		@Override
		public boolean equals(Object other) {
			// We don't really need a proper equals(). We won't be adding duplicate
			// objects to TopNList.
			return this == other;
		}
		
		@Override
		public int compareTo(ScoredItem<T> other) {
			if (other.score < this.score)
				return 1;
			else if (other.score > this.score)
				return -1;
			else
				return -this.item.compareTo(other.item);
		}
		
		@Override
		public String toString() {
			return item.toString() + "[" + score + "]";
		}
	}
	
	int N;
	TreeSet<ScoredItem<T>> items = new TreeSet<ScoredItem<T>>();
	double minScore = Double.MIN_VALUE;
	
	public TopNList(int N) {
		this.N = N;
	}
	
	public void add(T item, double score) {
		if (items.size() < N || score > minScore) {
			if (items.size() >= N)
				items.remove(items.first());
			items.add(new ScoredItem<T>(item, score));
			minScore = items.first().score;
		}
	}
	
	@Override
	public Iterator<T> iterator() {
		return new Iterator<T>() {
			final Iterator<ScoredItem<T>> iter = items.descendingIterator();

			@Override
			public boolean hasNext() { return iter.hasNext(); }

			@Override
			public T next() { return iter.next().item; }

			@Override
			public void remove() {}
		};
	}

	public Iterator<ScoredItem<T>> scoredItems() {
		 return items.descendingIterator();
	}
	
	public Iterable<ScoredItem<T>> scoredItemSet() {
		return items;
	}

	public static void main(String[] args) {
		TopNList<String> topNList = new TopNList<String>(3);
		
		topNList.add("b", -2);
		topNList.add("a", -1);
		topNList.add("c", -3);
		topNList.add("d", -4);
		topNList.add("e", -2); // should now be a, b, e
		topNList.add("f", -2); // should be same, "f" > "e"
		
		for (String s: topNList) {
			System.out.println(s);
		}
	}

}



