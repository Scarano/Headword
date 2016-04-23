package ocr;

import static ocr.util.Util.logSum;

import org.apache.commons.math.util.FastMath;

public class DMVVector {
	
	Vocabulary vocab;
	int vocabSize;
	public double[] vector;
	
	public DMVVector(Vocabulary vocab) {
		this(vocab, 0.0);
	}
	public DMVVector(Vocabulary vocab, double initialValue) {
		this.vocab = vocab;
		vocabSize = vocab.size();
		vector = new double[vectorSize(vocabSize)];
		for (int i = 0; i < vector.length; i++)
			vector[i] = initialValue;
	}
	
	public DMVVector(Vocabulary vocab, double[] vector) {
		this.vocab = vocab;
		vocabSize = vocab.size();
		this.vector = vector;
	}
	
	public Vocabulary getVocabulary() {
		return vocab;
	}
	
	public void add(DMVVector v) {
		for (int i = 0; i < vector.length; i++)
			vector[i] += v.vector[i];
	}
	
	public double get(boolean stop, String word, int dir, boolean hasChild) {
		return vector[eventIndex(stop, word, dir, hasChild)];
	}
	public double get(String arg, String head, int dir) {
		return vector[eventIndex(arg, head, dir)];
	}
	
	public void set(boolean stop, String word, int dir, boolean hasChild, double val) {
		vector[eventIndex(stop, word, dir, hasChild)] = val;
	}
	public void set(String arg, String head, int dir, double val) {
		vector[eventIndex(arg, head, dir)] = val;
	}
	
	public void add(boolean stop, String word, int dir, boolean hasChild, double val) {
		vector[eventIndex(stop, word, dir, hasChild)] += val;
	}
	public void add(String arg, String head, int dir, double val) {
		vector[eventIndex(arg, head, dir)] += val;
	}
	
	public void logAdd(boolean stop, String word, int dir, boolean hasChild, double val) {
		int idx = eventIndex(stop, word, dir, hasChild);
		vector[idx] = logSum(vector[idx], val);
	}
	public void logAdd(String arg, String head, int dir, double val) {
		int idx = eventIndex(arg, head, dir);
		vector[idx] = logSum(vector[idx], val);
	}
	
	public void convertToLog() {
		for (int i = 0; i < vector.length; i++)
			vector[i] = FastMath.log(vector[i]);
	}
	public void convertFromLog() {
		for (int i = 0; i < vector.length; i++)
			vector[i] = FastMath.exp(vector[i]);
	}
	
	public int eventIndex(boolean stop, String word, int dir, boolean hasChild) {
		return eventIndex(stop, vocab.wordID(word), dir, hasChild);
	}
	public int eventIndex(String arg, String head, int dir) {
		return eventIndex(vocab.wordID(arg), vocab.wordID(head), dir, vocabSize);
	}
	public int vectorSize() {
		return vector.length;
	}

	public static int eventIndex(boolean stop, int word, int dir, boolean hasChild) {
		return 8 * word + dir + (hasChild ? 2 : 0) + (stop ? 0 : 4);
	}
	public static int eventIndex(int arg, int head, int dir, int V) {
		return 8 * V +
			2 * (V * arg + head) + dir;
	}
	public static int vectorSize(int V) {
		return 8 * V + 2 * V * V;
	}

	public static void main(String[] args) {
		
	}

}
