package ocr;

import java.util.Arrays;

public class AnchoredArray<T> {
	
	int anchor = Integer.MIN_VALUE;
	Object[] belowAnchor;
	int belowAnchorSize;
	Object[] aboveAnchor;
	int aboveAnchorSize;

	public AnchoredArray() {
		this(0);
	}
	public AnchoredArray(int initialCapacity) {
		belowAnchorSize = 0;
		belowAnchor = new Object[(initialCapacity + 1)/2];
		aboveAnchorSize = 0;
		aboveAnchor = new Object[(initialCapacity + 1)/2];
	}
	
	public void set(int index, T item) {
		if (anchor == Integer.MIN_VALUE)
			anchor = index;
		if (index >= anchor) {
			int i = index - anchor;
			if (i >= aboveAnchorSize) {
				aboveAnchorSize = i+1;
				if (i >= aboveAnchor.length)
					aboveAnchor = Arrays.copyOf(aboveAnchor, aboveAnchorSize*2);
			}
			aboveAnchor[i] = item;
		}
		else {
			int i = anchor - index - 1;
			if (i >= belowAnchorSize) {
				belowAnchorSize = i+1;
				if (i >= belowAnchor.length)
					belowAnchor = Arrays.copyOf(belowAnchor, belowAnchorSize*2);
			}
			belowAnchor[i] = item;
		}
	}
	
	@SuppressWarnings("unchecked")
	public T get(int index) {
		if (anchor == Integer.MIN_VALUE) {
			return null;
		}
		else if (index >= anchor) {
			int i = index - anchor;
			if (i >= aboveAnchorSize)
				return null;
			else
				return (T) aboveAnchor[i];
		}
		else {
			int i = anchor - index - 1;
			if (i >= belowAnchorSize)
				return null;
			else
				return (T) belowAnchor[i];
		}
	}
	
	public int size() {
		return belowAnchorSize + aboveAnchorSize;
	}
	
	public int minIndex() {
		return anchor - belowAnchorSize;
	}
	
	public String toString() {
		String result = "[";
		for (int i = minIndex(); i < minIndex() + size(); i++) {
			if (i > minIndex()) result += ", ";
			result += i + ": " + get(i);
		}
		result += "]";
		return result;
	}

	public static void main(String[] args) {
		AnchoredArray<Character> array = new AnchoredArray<Character>();
		System.out.println(array.toString());
		for (int c: new int[] {70, 72, 73, 69, 67}) {
			array.set(c, new Character((char) c));
			System.out.println(array.toString());
		}
	}

}










