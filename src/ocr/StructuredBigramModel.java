package ocr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import ocr.LatticeParser.DMVVectorScorer;
import ocr.NGramLanguageModel.NGram;
import ocr.ParsedCorpus.ParsedSentence;
import ocr.util.CommandLineParser;
import ocr.util.Counter;
import ocr.util.Util;
import ocr.util.Counter.Count;

public class StructuredBigramModel {
	
	private static boolean debug = false;
	private static boolean stopContinue = false;
	private static boolean directed = false;
	
	static final double LN_10 = Math.log(10);
	static final String ROOT_SYM = "<ROOT>";
	static final String STOP_SYM = "<STOP>";
	static final String CONT_SYM = "<CONT>";
	static final String UNK_SYM = "<unk>";
	
	Counter<NGram> nGramCounts = new Counter<NGram>();
	Counter<String> stopCounts = new Counter<String>();
	Counter<NGram> logCounts = null;
	// Number of times we saw each word as a head word
	Counter<String> condTokenCounts = new Counter<String>();
	HashMap<String, Double> lambdas = new HashMap<String, Double>();
	double totalTokens = 0.0;
	
	static NGram bigramForPosition(String[] words, int[] parse, int pos) {
		if (parse[pos] == 0) {
			return new NGram(ROOT_SYM, words[pos]);
		}
		else {
			String word1 = words[parse[pos]-1];
			word1 = parse[pos] < pos ? right(word1) : left(word1);
			return new NGram(word1, words[pos]);
		}
	}
	
	double logCount(String word) {
		return logCounts.get(new NGram(new String[] { word }), Double.NEGATIVE_INFINITY);
	}
	double logCount(String word1, String word2) {
		return logCounts.get(
				new NGram(new String[] { word1, word2 }),
				Double.NEGATIVE_INFINITY);
	}
	
	private static String right(String token) {
		if (directed)
			return "R:" + token;
		else
			return token;
	}
	private static String left(String token) {
		if (directed)
			return "L:" + token;
		else
			return token;
	}
			
	public void addTree(String[] words, int[] parse) {
		assert logCounts == null;
		
		// Add stop & continue counts
		if (directed) {
			int[] leftChildCounts = new int[words.length];
			int[] rightChildCounts = new int[words.length];
			for (int j = 0; j < words.length; j++) {
				if (parse[j] != 0 && parse[j] - 1 < j)
					rightChildCounts[parse[j] - 1]++;
				else if (parse[j] - 1 > j)
					leftChildCounts[parse[j] - 1]++;
			}
			for (int j = 0; j < words.length; j++) {
				if (leftChildCounts[j] == 0)
					stopCounts.increment(left(words[j]));
				if (rightChildCounts[j] == 0)
					stopCounts.increment(right(words[j]));
			}
		}
		else {
			int[] childCounts = new int[words.length];
			for (int j = 0; j < words.length; j++)
				if (parse[j] != 0)
					childCounts[parse[j] - 1]++;
			for (int j = 0; j < words.length; j++)
				if (childCounts[j] == 0)
					stopCounts.increment(words[j]);
		}

		// add root count
		nGramCounts.increment(new NGram(ROOT_SYM));
		
		// add attachment counts
		for (int j = 0; j < words.length; j++) {
			nGramCounts.increment(new NGram(words[j]));
			NGram bigram = bigramForPosition(words, parse, j);
			nGramCounts.increment(bigram);
			condTokenCounts.increment(bigram.words[0]);
		}
		
		totalTokens += words.length;
	}
	
	public void addRightBranchingTree(String[] words) {
		addTree(words, rightBranchingParse(words.length));
	}
	
	public void complete() {
		logCounts = nGramCounts.toLogSpace();
		
		HashMap<String, HashSet<String>> condVocab = 
				new HashMap<String, HashSet<String>>();
		for (Entry<NGram, Count> ngram: nGramCounts) {
			if (ngram.getKey().words.length == 2) {
				String word1 = ngram.getKey().words[0];
				String word2 = ngram.getKey().words[1];
				if (!condVocab.containsKey(word1))
					condVocab.put(word1, new HashSet<String>());
				condVocab.get(word1).add(word2);
			}
		}
		for (String word: condVocab.keySet()) {
			double N = condTokenCounts.get(word);
			double V = condVocab.get(word).size();
			lambdas.put(word, N / (N + V));
		}
	}
	
	double probOfTree(String[] words, int[] parse) {
		assert logCounts != null;
		
		if (debug)
			System.out.println();

		double p = 0.0;
		
		// multiply in stop & continue probabilities
		if (stopContinue) {
			if (directed) {
				int[] leftChildCounts = new int[words.length];
				int[] rightChildCounts = new int[words.length];
				for (int j = 0; j < words.length; j++) {
					if (parse[j] != 0 && parse[j] - 1 < j)
						rightChildCounts[parse[j] - 1]++;
					else if (parse[j] - 1 > j)
						leftChildCounts[parse[j] - 1]++;
				}
				for (int j = 0; j < words.length; j++) {
					double denom = nGramCounts.get(new NGram(words[j]), 0.0);

					double numer = stopCounts.get(left(words[j]), 0.0);
					// use add-0.5 smoothing
					double pWord = (numer + 0.5) / (denom +1.0);
					if (leftChildCounts[j] != 0)
						pWord = 1.0 - pWord;
					if (debug) {
						System.out.printf("p(stop=%s | %s) = %f\n", 
								leftChildCounts[j] == 0, left(words[j]), pWord);
					}
					p += Math.log(pWord);

					numer = stopCounts.get(right(words[j]), 0.0);
					// use add-0.5 smoothing
					pWord = (numer + 0.5) / (denom +1.0);
					if (rightChildCounts[j] != 0)
						pWord = 1.0 - pWord;
					if (debug) {
						System.out.printf("p(stop=%s | %s) = %f\n", 
								rightChildCounts[j] == 0, right(words[j]), pWord);
					}
					p += Math.log(pWord);
				}
			}
			else {
				int[] childCounts = new int[words.length];
				for (int j = 0; j < words.length; j++)
					if (parse[j] != 0)
						childCounts[parse[j] - 1]++;
				for (int j = 0; j < words.length; j++) {
					double numer = stopCounts.get(words[j], 0.0);
					double denom = nGramCounts.get(new NGram(words[j]), 0.0);
					// use add-0.5 smoothing
					double pWord = (numer + 0.5) / (denom +1.0);
					if (childCounts[j] != 0)
						pWord = 1.0 - pWord;
					if (debug) {
						System.out.printf("p(stop=%s | %s) = %f\n", 
								childCounts[j] == 0, words[j], pWord);
					}
					p += Math.log(pWord);
				}
			}
		}

		// multiply in attachment probabilities
		for (int j = 0; j < words.length; j++) {
			if (nGramCounts.tryToGet(new NGram(words[j])) == null)
				continue; // OOV words are free, which is OK for comparing two models
			
			double pWord;
			String word1 = parse[j] == 0 ? ROOT_SYM
					: parse[j] < j ? right(words[parse[j] - 1]) : left(words[parse[j] - 1]);
			String word2 = words[j];
			NGram bigram = new NGram(word1, word2);
			
			double pUnigram = nGramCounts.get(new NGram(word2), 0.0) / totalTokens;
			double pBigram = nGramCounts.get(bigram, 0.0)
					/ condTokenCounts.get(word1, 0.0);
			Double lambda = lambdas.get(word1);
			if (lambda == null) {
				pWord = pUnigram;
			}
			else {
				pWord = lambda * pBigram + (1-lambda) * pUnigram;
			}
			
			if (debug) {
				if (lambda == null) lambda = 0.0;
				System.out.printf("p(%s | %s) = %f * %f + %f * %f = %f\n",
						word2, word1, lambda, pBigram, 1-lambda, pUnigram, pWord);
			}
			
			p += Math.log(pWord);
		}
		
		assert p != Double.NEGATIVE_INFINITY;
		
		return p;
	}
	
	double probGivenRightBranchingTree(String[] words) {
		return probOfTree(words, rightBranchingParse(words.length));
	}

	static int[] rightBranchingParse(int len) {
		int[] parse = new int[len];
		for (int j = 0; j < parse.length; j++)
			parse[j] = j;
		return parse;
	}
	
	public static void main(String[] args) throws IOException {
		CommandLineParser clp = new CommandLineParser(
				"-verbose -debug -stop-continue -directed -substitution -parser=s", args);
		String trainFile = clp.arg(0);
		String testFile = clp.arg(1);
		boolean verbose = clp.opt("-verbose");
		debug = clp.opt("-debug");
		stopContinue = clp.opt("-stop-continue");
		directed = clp.opt("-directed");
		boolean substitution = clp.opt("-substitution");
		String modelFile = clp.opt("-parser", null);
		
		ParsedCorpus train = new ParsedCorpus(new File(trainFile), true);
		ParsedCorpus test = train;
		if (testFile != null)
			test = new ParsedCorpus(new File(testFile), true);
		
		LatticeParser parser = null;
		if (modelFile != null) {
			DMVGrammar grammar = new DMVGrammar(modelFile);
			DMVVector vector = grammar.asVector(grammar.buildVocabulary());
			parser = new LatticeParser(new DMVVectorScorer(vector));
		}
		
		StructuredBigramModel linearModel = new StructuredBigramModel();
		StructuredBigramModel structModel = new StructuredBigramModel();
		for (ParsedSentence sent: train.getSentences()) {
			linearModel.addRightBranchingTree(sent.getText());
			
			int[] parse = sent.getParse();
			if (parser != null)
				parse = parser.parse(sent.getTags());
			structModel.addTree(sent.getText(), parse);
		}
		linearModel.complete();
		structModel.complete();

		if (substitution) {
			System.out.printf("%s %s %s%s%s",
					trainFile, testFile, modelFile,
					stopContinue ? " stop-continue" : "",
					directed ? " directed" : "");
					
			List<String> vocab = new ArrayList<String>();
			for (NGram bigram: structModel.nGramCounts.countMap().keySet())
				if (bigram.words.length == 1)
					vocab.add(bigram.words[0]);
			
			for (int modelNum = 0; modelNum <= 1; modelNum++) {
				StructuredBigramModel model = modelNum == 0
						? linearModel
						: structModel;
				
				Random random = new Random(0);
				
				double pCorpus = 0.0;

				int totalTokens = 0;
				double diff = 0.0;
				double successes = 0.0;
				
				for (ParsedSentence sent: test.getSentences()) {
					String[] tokens = sent.getText();
					totalTokens += tokens.length;
					
					int[] parse = sent.getParse();
					if (parser != null)
						parse = parser.parse(sent.getTags());
					
					double pCorrect = modelNum == 0
							? model.probGivenRightBranchingTree(tokens)
							: model.probOfTree(tokens, parse);

					pCorpus += pCorrect;

					for (int j = 0; j < sent.getText().length; j++) {
						String[] bogusTokens = Arrays.copyOf(tokens, tokens.length);
						bogusTokens[j] = vocab.get(random.nextInt(vocab.size()));
						double pBogus = modelNum == 0
								? model.probGivenRightBranchingTree(bogusTokens)
								: model.probOfTree(bogusTokens, parse);
						diff += pCorrect - pBogus;
						if (pCorrect > pBogus)
							successes++;
					}
				}
				
//				System.out.printf("pCorrect - pBogus = %f\n", diff / totalTokens);
//				System.out.printf("success rate = %f\n", successes / totalTokens);
				
				System.out.printf(",%f,%f,%f",
						-pCorpus / totalTokens,
						diff / totalTokens,
						successes / totalTokens);
				
			}
			System.out.println();
		}
		else {
			double pCorpusStruct = 0.0;
			double pCorpusLinear = 0.0;
			int tokens = 0;
			
			for (ParsedSentence sent: test.getSentences()) {
				tokens += sent.getText().length;
				
				double pLinear = linearModel.probGivenRightBranchingTree(sent.getText());
				if (verbose)
					System.out.printf("p_linear(%s) = %f\n", Util.join(" ", sent.getText()), pLinear);
				pCorpusLinear += pLinear;
				
				double pStruct = structModel.probOfTree(sent.getText(), sent.getParse());
				if (verbose)
					System.out.printf("p_struct(%s) = %f\n", Util.join(" ", sent.getText()), pStruct);
				pCorpusStruct += pStruct;
	
				if (verbose)
					System.out.println();
			}
			
			System.out.printf("perp_linear(corpus) = %f\n", -pCorpusLinear/tokens);
			System.out.printf("perp_struct(corpus) = %f\n", -pCorpusStruct/tokens);
			System.out.println();
		}
	}

}

	
	
	
	
	
	
	