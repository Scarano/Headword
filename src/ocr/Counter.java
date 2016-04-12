package ocr;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

class Counter<T> implements Iterable<Entry<T, Counter.Count>> {
	static class Count {
		public double value;
		
		public Count(double value) {
			this.value = value;
		}

		public String toString() { return Double.toString(value); }
	}

	HashMap<T, Count> counts;

	public Counter() {
		counts = new HashMap<T, Count>();
	}
	
	public void increment(T x) {
		add(x, 1);
	}

	public void add(T x, double value) {
		Count count = counts.get(x);
		if (count == null)
			counts.put(x, new Count(value));
		else
			count.value += value;
	}
	
	public double get(T x) {
		return counts.get(x).value;
	}
	
	public double get(T x, double defVal) {
		Count count = counts.get(x);
		if (count == null)
			return defVal;
		else
			return count.value;
	}
	
	public Double tryToGet(T x) {
		Count count = counts.get(x);
		if (count == null)
			return null;
		else
			return count.value;
	}
	
	public Set<Entry<T, Count>> entries() {
		return counts.entrySet();
	}
	
	@Override
	public Iterator<Entry<T, Count>> iterator() {
		return counts.entrySet().iterator();
	}

	public int size() {
		return counts.size();
	}
	
	public Set<T> events() {
		return counts.keySet();
	}
	
	public HashMap<T, Double> asMap() {
		HashMap<T, Double> map = new HashMap<T, Double>();
		for (Entry<T, Count> entry: this)
			map.put(entry.getKey(), entry.getValue().value);
		return map;
	}
	
	public Counter<T> toLogSpace() {
		Counter<T> logCounter = new Counter<T>();
		for (Entry<T, Count> entry: this)
			logCounter.counts.put(entry.getKey(), new Count(Math.log(entry.getValue().value)));
		return logCounter;
	}

	public void print(PrintStream out) {
		double total = 0;
		for (Entry<T, Count> count: counts.entrySet()) {
			out.println(count.getKey() + ": " + count.getValue());
			total += count.getValue().value;
		}
		out.println("Total: " + total);
	}

}









