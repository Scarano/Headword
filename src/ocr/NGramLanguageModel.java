package ocr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;

import ocr.util.Util;

public class NGramLanguageModel {
	
	private static final boolean DEBUG = false;
	
	static final double LN_10 = Math.log(10);
	static final String START_SYM = "<s>";
	static final String END_SYM = "</s>";
	static final String UNK_SYM = "<unk>";
	
	public static class NGram {
		public String[] words;
		
		public NGram(String[] words) {
			this(words, 0, words.length);
		}
		
		public NGram(String[] words, int start, int end) {
			this.words = new String[end - start];
			for (int i = 0; i < this.words.length; i++)
				this.words[i] = words[start + i];
		}
		
		public NGram(String word) {
			this(new String[] { word });
		}
		
		public NGram(String word1, String word2) {
			this(new String[] { word1, word2 });
		}
		
		@Override public int hashCode() {
			return Arrays.hashCode(words);
		}
		
		@Override public boolean equals(Object other) {
			if (other == null)
				return false;
			
			if (!(other instanceof NGram))
				return false;
			
			NGram otherNGram = (NGram) other;
			if (words.length != otherNGram.words.length)
				return false;
			for (int i = 0; i < words.length; i++)
				if (!words[i].equals( otherNGram.words[i]))
					return false;
			
			return true;
		}
		
		@Override public String toString() {
			return Util.join(" ", words);
		}
	}
	
	public static class NGramStats {
		public final double prob;
		public final Double backoff;
		
		public NGramStats(double prob, Double backoff) {
			this.prob = prob;
			this.backoff = backoff;
		}
	}
	
	int maxOrder;
	HashMap<NGram, NGramStats> nGrams;
	
	public NGramLanguageModel(File file, int order) throws IOException {
		this.maxOrder = order;
		nGrams = new HashMap<NGram, NGramStats>();

		BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(file), "UTF-8"));
		int state = 0;
		String line = "";
		try {
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("\\")) {
					if (line.contains("gram")) {
						state = Integer.parseInt(line.substring(1, line.indexOf('-')));
						if (state > order)
							break;
					}
				}
				else if (state > 0 && !line.isEmpty()) {
					String[] terms = line.split("\t");
					
					NGram nGram = new NGram(terms[1].split(" "));
					
					double probLog10 = Double.parseDouble(terms[0]);
					if (probLog10 == -99) probLog10 = Double.NEGATIVE_INFINITY;
					double probLn = probLog10 * LN_10;
					Double backoffLn = null;
					if (terms.length == 3)
						backoffLn = Double.parseDouble(terms[2]) * LN_10;
					NGramStats stats = new NGramStats(probLn, backoffLn);
					
					nGrams.put(nGram, stats);
				}
			}
		}
		catch (Exception e) {
			System.err.printf("Error while processing line '%s' %s:\n", 
					line, Arrays.toString(line.split("\t")));
			e.printStackTrace(System.err);
			throw new IOException(e);
		}
		finally {
			reader.close();
		}
	}
	
	public double probOfSentence(String[] sequence) {
		return probOfSentence(sequence, true);
	}
	
	public double probOfSentence(String[] sequence, boolean mapOovToUnk) {
		String[] delimitedSequence = new String[sequence.length+2];
		delimitedSequence[0] = START_SYM;
		for (int i = 1; i < delimitedSequence.length - 1; i++) {
			if (mapOovToUnk && !nGrams.containsKey(new NGram(sequence, i-1, i))) {
				delimitedSequence[i] = UNK_SYM;
			} else {
				delimitedSequence[i] = sequence[i-1];
			}
		}
		delimitedSequence[delimitedSequence.length-1] = END_SYM;
		
		return probOfSequence(delimitedSequence);
	}
	
	protected double probOfSequence(String[] sequence) {
		if (DEBUG)
			System.out.printf("p(%s):\n", Util.join(" ", sequence));
		
		double p = 0.0;
		for (int i = 1; i < sequence.length; i++)
			p += probOfWord(sequence, i, maxOrder);
		
		if (DEBUG)
			System.out.printf("p(%s) = %f\n\n", Util.join(" ", sequence), p);
		
		return p;
	}
	
	protected double probOfWord(String[] sequence, int pos, int order) {
		int end = pos + 1;
		if (order > end)
			order = end;
		int start = end - order;
		
		NGram nGram = new NGram(sequence, start, end);
		NGramStats stats = nGrams.get(nGram);
		if (stats != null) {
			if (DEBUG)
				System.out.printf("  p(%s) = %f\n", nGram.toString(), stats.prob);
			return stats.prob;
		}
		
		if (order == 1) {
			if (DEBUG)
				System.out.printf("  p(%s) = -inf\n", nGram.toString());
			return Double.NEGATIVE_INFINITY;
		}
		
		NGram prefix = new NGram(sequence, start, pos);
		double backoff = getBackoffWeight(prefix);
		double pBackedOff = probOfWord(sequence, pos, order - 1);
		double p = backoff + pBackedOff;
		
		if (DEBUG) {
			System.out.printf("  p(%s) = %f = %f + p(%s)\n",
					nGram.toString(), p, backoff, new NGram(sequence, start+1, end));
		}
		
		return p;
		
//		for (; start < end; start++) {
//			NGram nGram = new NGram(sequence, start, end);
//			NGramStats stats = nGrams.get(nGram);
//			if (stats != null) {
//				if (DEBUG)
//					System.out.printf("  p(%s) = %f\n", nGram.toString(), stats.prob);
//				return stats.prob;
//			}
//		}
//		return Double.NEGATIVE_INFINITY;
	}
	
	protected double getBackoffWeight(NGram nGram) {
		NGramStats stats = nGrams.get(nGram);
		if (stats == null || stats.backoff == null)
			return 0.0;
		else
			return stats.backoff;
	}
	
	public boolean isInVocab(String word) {
		return nGrams.containsKey(new NGram(new String[] {word}));
	}
	
	
	private static HashMap<String, NGramLanguageModel> instances =
			new HashMap<String, NGramLanguageModel>();
	
	public static synchronized NGramLanguageModel getInstance(File file, int order) 
		throws IOException
	{
		String key = file.getPath() + ":" + order;
		NGramLanguageModel instance = instances.get(key);
		if (instance == null) {
			instance = new NGramLanguageModel(file, order);
			instances.put(key, instance);
		}
		return instance;
	}

	public static void main(String[] args) throws IOException {
		NGramLanguageModel lm = new NGramLanguageModel(
				new File(args[0]), Integer.parseInt(args[1]));
		
		System.out.println("Loaded language model");
		
		double pDoc = 0.0;
		
		for (int t = 0; t < 100; t++) {
			System.err.printf("%d\r", t);
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(new FileInputStream(args[2]), "UTF-8"));
			while (reader.ready()) {
				String line = reader.readLine();
				double p = lm.probOfSentence(line.split(" "));
				pDoc += p;
//				System.out.println(line);
//				System.out.println(p);
//				System.out.println();
			}
			reader.close();
		}
		
		System.out.printf("p(doc) = %f", pDoc);
	}

}

	
	
	
	
	
	
	