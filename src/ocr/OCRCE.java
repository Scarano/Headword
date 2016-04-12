package ocr;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static java.lang.System.arraycopy;
import edu.stanford.nlp.optimization.DiffFunction;
import edu.stanford.nlp.optimization.QNMinimizer;
import ocr.Counter.Count;
import ocr.DMVCE.NeighborhoodFunction;
import ocr.LatticeParser.DMVVectorScorer;
import ocr.util.CommandLineParser;
import static ocr.GUtil.loadUtf8Lines;

class OCRCE {
	static boolean debug = false;
	
	static class LengthNeighborhood implements NeighborhoodFunction {
		public final boolean optimize;
		Map<String, Double> wordCounts;
		String[] nonRootVocab;

		public LengthNeighborhood(
				Vocabulary vocab, boolean optimize, Map<String, Double> wordCounts)
		{
			this.optimize = optimize;
			this.wordCounts = wordCounts;
			nonRootVocab = new String[vocab.size() - 1];
			for (int i = 1; i < vocab.size(); i++)
				nonRootVocab[i-1] = vocab.wordString(i);
		}

		@Override
		public StringLattice lattice(int sentNum, String[] sent) {
			return lattice(sent.length);
		}
		
		public StringLattice lattice(int sentLength) {
			if (wordCounts == null)
				return StringLattice.lengthLattice(nonRootVocab, sentLength);
			else
				return StringLattice.weightedLengthLattice(
						nonRootVocab, sentLength, wordCounts);
		}
	}
	
	static class ErrorNeighborhood implements NeighborhoodFunction {
		Vocabulary vocab;
		Map<String, Double> wordCounts;
		String[] nonRootVocab;
		double[] dist;
		int numErrors;
		Random random;

		public ErrorNeighborhood(
				Vocabulary vocab, Map<String, Double> wordCounts, int numErrors)
		{
			this.vocab = vocab;
			this.wordCounts = wordCounts;
			nonRootVocab = new String[vocab.size() - 1];
			for (int i = 1; i < vocab.size(); i++)
				nonRootVocab[i-1] = vocab.wordString(i);
			dist = new double[nonRootVocab.length];
			if (wordCounts != null) {
				for (int i = 0; i < nonRootVocab.length; i++)
					if (wordCounts.containsKey(nonRootVocab[i]))
						dist[i] = wordCounts.get(nonRootVocab[i]);
				Util.normalize(dist);
			}
			else {
				for (int i = 0; i < nonRootVocab.length; i++)
					dist[i] = 1.0/nonRootVocab.length;
			}
			this.numErrors = numErrors;
			this.random = new Random(0);
		}

		@Override
		public StringLattice lattice(int sentNum, String[] sent) {
			StringLattice lattice = new StringLattice(sent.length + 1);
			for (int j = 0; j < sent.length; j++) {
				double p = random.nextDouble();
				lattice.addEdge(j, j+1, sent[j], Math.log(p));
				
				double[] nextDist = Arrays.copyOf(dist, dist.length);
				removeWordFromDist(nextDist, vocab.wordID(sent[j]) - 1);
				String word = nonRootVocab[Util.sampleMultinomial(nextDist, random)];
				lattice.addEdge(j, j+1, word, Math.log(1.0 - p));
				assert !word.equals(sent[j]);
			}
			return lattice;
		}
		
		static void removeWordFromDist(double[] dist, int word) {
			dist[word] = 0.0;
			Util.normalize(dist);
		}
	}
	
	static class TransNeighborhood implements NeighborhoodFunction {
		@Override
		public StringLattice lattice(int sentNum, String[] sent) {
			StringLattice lattice = new StringLattice((sent.length + 1) * 3);
			
			for (int j = 0; j < sent.length; j++) {

				if (j < sent.length - 1)
					lattice.addEdge(state(j, 0), state(j + 1, 0), sent[j], 0.0);
				else
					lattice.addEdge(state(j, 0), state(j + 1, 2), sent[j], 0.0);
				
				if (j > 0)
					lattice.addEdge(state(j-1, 0), state(j, 1), sent[j], 0.0);
				
				if (j < sent.length - 1)
					lattice.addEdge(state(j+1, 1), state(j+2, 2), sent[j], 0.0);
				
				if (j > 1)
					lattice.addEdge(state(j, 2), state(j+1, 2), sent[j], 0.0);
			}
			
			return lattice;
		}
		
		// "columns" and "rows" correspond to Figure 1.b. in the contrastive
		// estimation paper.
		private static int state(int column, int row) {
			return 3*column + row;
		}
	}
	
	static class DMVFunction implements DiffFunction {

		NeighborhoodFunction N;
		boolean lengthOptimization;
		Vocabulary vocab;
		String[][] data;
		String outputPrefix;
		
		StringLattice[] neighborhoods;

		int iteration;
		int maxIterations;
		double[] currentModel;
		double negLikelihood = Double.NaN;
		double[] negGradient = null;
		long startTime;
		
		public DMVFunction(
				NeighborhoodFunction neighborhoodFunc, Vocabulary vocab, String[][] data, 
				String outputPrefix, int startIteration, int maxIterations)
		{
			this.N = neighborhoodFunc;
			this.vocab = vocab;
			this.data = data;
			this.outputPrefix = outputPrefix;
			this.iteration = startIteration;
			this.maxIterations = maxIterations;
			currentModel = new double[DMVVector.vectorSize(vocab.size())];
			currentModel[0] = Double.NaN; // make sure this "cache" is considered "dirty"
			
			if (neighborhoodFunc instanceof LengthNeighborhood)
				lengthOptimization = ((LengthNeighborhood) neighborhoodFunc).optimize;
			
			if (!lengthOptimization) {
				neighborhoods = new StringLattice[data.length];
				for (int i = 0; i < data.length; i++)
					neighborhoods[i] = N.lattice(i, data[i]);
			}

			startTime = System.currentTimeMillis();
		}

		@Override
		public int domainDimension() {
			return currentModel.length;
		}

		@Override
		public double valueAt(double[] x) {
			if (debug)
				System.out.printf("valueAt([%f %f %f ... norm=%f])\n", 
						x[0], x[1], x[2], norm(x));
			
			if (!Arrays.equals(x, currentModel))
				compute(x);

			if (debug)
				System.out.printf("  value: %f\n", negLikelihood);
			return negLikelihood;
		}

		@Override
		public double[] derivativeAt(double[] x) {
			if (debug)
				System.out.printf("derivativeAt([%f %f %f ... norm=%f])\n", 
						x[0], x[1], x[2], norm(x));
			
			if (!Arrays.equals(x, currentModel))
				compute(x);
			
			return negGradient;
		}
		
		void compute(double[] x) {
			if (debug)
				System.out.printf("compute([%f %f %f ... norm=%f])\n", 
						x[0], x[1], x[2], norm(x));
			
			if (Double.isNaN(x[0]))
				throw new Error("Input model is bogus!");
			
			arraycopy(x, 0, currentModel, 0, x.length);
			
			DMVVector model = new DMVVector(vocab, x);
			String modelPath = outputPrefix + iteration + ".dmv";
			new DMVGrammar(model).save(modelPath);
			try {
				String modelFile = new File(modelPath).getName();
				Runtime.getRuntime().exec(
						"ln -fs " + modelFile + " " + outputPrefix + "last.dmv");
			} catch (IOException e) {
				System.err.println(e);
			}

			
			DMVVectorScorer scorer = new DMVVectorScorer(model);
			LatticeParser parser = new LatticeParser(scorer);

			negLikelihood = 0.0;
			DMVVector negDmvGradient = new DMVVector(vocab);
			
			Counter<Integer> lengthCounter = new Counter<Integer>();
			
			for (int i = 0; i < data.length; i++) {
				String[] sent = data[i];
				
				parser.parse(sent);
				negLikelihood -= parser.sentProb();
				parser.addSoftCounts(negDmvGradient, -1.0);
//System.out.printf("sent prob: %f\n", parser.sentProb());
//new DMVGrammar(negDmvGradient).save(outputPrefix + iteration + ".numGradient");

				if (lengthOptimization) {
					lengthCounter.increment(sent.length);
				}
				else {
					parser.parse(neighborhoods[i]);
//System.out.printf("neighborhood prob: %f\n", parser.sentProb());
					negLikelihood += parser.sentProb();
					parser.addSoftCounts(negDmvGradient, 1.0);
				}
//new DMVGrammar(negDmvGradient).save(outputPrefix + iteration + ".denGradient");
//break;
			}

			if (lengthOptimization) {
				for (Map.Entry<Integer, Count> lengthCount: lengthCounter) {
					int length = lengthCount.getKey();
					double count = lengthCount.getValue().value;
					
					StringLattice lattice = ((LengthNeighborhood) N).lattice(length);
					parser.parse(lattice);
					negLikelihood += count * parser.sentProb();
					parser.addSoftCounts(negDmvGradient, count);
				}
			}
			
			negGradient = negDmvGradient.vector;
			if (debug)
				new DMVGrammar(negDmvGradient).save(outputPrefix + iteration + ".gradient");
			
			iteration++;
		}
	}
	
	static double norm(double[] x) {
		double sum = 0.0;
		for (int i = 0; i < x.length; i++)
			sum += x[i];
		return sum / x.length;
	}

	public static void main(String[] args) {
		CommandLineParser clp = new CommandLineParser(
			"-vocab=s -hood=s -lambda=f -weighted -input-model=s -debug", args);
		
		String vocabFile = clp.opt("-vocab", null);
		String neighborhoodType = clp.opt("-hood", null);
		double lambda = clp.opt("-lambda", Double.NaN);
		boolean weighted = clp.opt("-weighted");
		String inputModel = clp.opt("-input-model", null);
		String dataFile = clp.arg(0);
		String outputPrefix = clp.arg(1);
		int startIteration = Integer.parseInt(clp.arg(2));
		int maxIterations = Integer.parseInt(clp.arg(3, "9999"));
		debug = clp.opt("-debug");
		
		String[] inputLines = loadUtf8Lines(dataFile);
		String[][] sentences = new String[inputLines.length][];
		for (int i = 0; i < inputLines.length; i++)
			sentences[i] = inputLines[i].split(" ");

		Vocabulary vocab = null;
		if (vocabFile != null)
			vocab = new Vocabulary(vocabFile);
		
		Map<String, Double> counts = null;
		if (weighted)
			counts = wordCounts(sentences);

		NeighborhoodFunction neighborhood = null;
		if (neighborhoodType == null || neighborhoodType.startsWith("length")) {
			boolean optimize = !neighborhoodType.equals("length-slow");
			neighborhood = new LengthNeighborhood(vocab, optimize, counts);
		}
		else if (neighborhoodType.equals("error")) {
			neighborhood = new ErrorNeighborhood(vocab, counts, 1);
		}
		else if (neighborhoodType.equals("trans")) {
			neighborhood = new TransNeighborhood();
		}
		else {
			System.err.println("Invalid neighborhood type: " + neighborhoodType);
			System.exit(-1);
		}

		if (inputModel == null && startIteration > 0)
			inputModel = outputPrefix + (startIteration - 1) + ".dmv";

		if ("harmonic".equals(inputModel) && startIteration == 0) {
			inputModel = outputPrefix + "0.dmv";
			DMVEM.writeHarmonicModel(inputModel, dataFile, null, 0, 9999);
		}
		
		double[] initialVector;
		if (inputModel != null) {
			DMVGrammar initialModel = new DMVGrammar(inputModel);
			if (vocab == null)
				vocab = initialModel.buildVocabulary();
			initialVector = initialModel.asVector(vocab).vector;
			for (int i = 0; i < initialVector.length; i++)
				if (initialVector[i] == Double.NEGATIVE_INFINITY) initialVector[i] = -20;
		}
		else {
			initialVector = new double[DMVVector.vectorSize(vocab.size())];
		}

		DMVFunction dmvFunction = new DMVFunction(neighborhood,
				vocab, sentences, outputPrefix, startIteration, maxIterations);
		
		QNMinimizer minimizer = new QNMinimizer(10, true);
		if (!Double.isNaN(lambda))
			minimizer.useOWLQN(true, lambda);
		minimizer.minimize(dmvFunction, 1e-5, initialVector, 10000);

	}
	
	static Map<String, Double> wordCounts(String[][] sentences) {
		Map<String, Double> counts = new HashMap<String, Double>();
		for (String[] sentence: sentences)
			for (String word: sentence)
				counts.put(word, (counts.get(word) != null ? counts.get(word) : 0.0) + 1.0);
		return counts;
	}
}








