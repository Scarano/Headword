package ocr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import ocr.Counter.Count;

public class Evaluator {
	
	static void addTrainVocabulary(InexactDictionary dict, File modelFile)
			throws IOException
	{
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(modelFile), "UTF-8"));
		
		while (reader.ready() && !reader.readLine().contains("1-gram"));
		
		if (!reader.ready()) {
			reader.close();
			throw new IOException("Invalid model file");
		}
		
		while (reader.ready()) {
			String line = reader.readLine().trim();
			if (line.length() == 0)
				break;
			dict.addWord(line.split("\t")[1]);
		}
		
		reader.close();
	}

	static void addTestVocabulary(
			InexactDictionary dict, List<String> ocrSentences, Tokenizer tokenizer, int minFrequency)
	{
		Counter<String> words = new Counter<String>();
		for (String line: ocrSentences) {
			for (String token: tokenizer.tokenize(line))
				words.increment(token);
		}
		
		for (Entry<String, Count> wordCount: words) {
			if (wordCount.getValue().value >= minFrequency)
				dict.addWord(wordCount.getKey());
		}
	}
	
	public static void evaluate(RunConfig config) throws Exception {
		
		File testFile = config.getDataFile("test-set.file");
		int minSentLength = config.getInt("test-set.min-sentence-length", 0);
		int maxSentLength = config.getInt("test-set.max-sentence-length", 999);
		SentenceAlignment testAlignment = 
				new SentenceAlignment(testFile, minSentLength, maxSentLength);
		
		String tokenizerMethod = config.getString("tokenizer.method");
		Tokenizer tokenizer;
		if (tokenizerMethod.equals("simple"))
			tokenizer = new SimpleTokenizer();
		else if (tokenizerMethod.equals("ptb"))
			tokenizer = new PTBLikeTokenizer();
		else
			throw new IOException("Parameter tokenizer.method unspecified");
		
		File vocabFile = config.getDataFile("srilm.model");
		InexactDictionary dict = new InexactDictionary();
		addTrainVocabulary(dict, vocabFile);
		config.getTimer().completePhase("Read in training set vocab.");
		addTestVocabulary(dict, testAlignment.ocrSentences, tokenizer, 1);
		config.getTimer().completePhase("Read in test set vocab.");
		
		SegmentModel segmentModel = new InterpolatedSegmentModel(
				config, new MergeSplitSegmentModel(config));
		
		Corrector corrector;
		
		String rescoreMethod = config.getString("rescore.method", null);
		Rescorer rescorer = null;
		int lsc = config.getInt("lm.sentence-candidates", 0);
		if (lsc > 0) {
			if ("tag-oracle".equals(rescoreMethod)) {
				rescorer = new TagOracleRescorer(config, testAlignment.transcriptionSentences);
			}
			else if ("tag-ngram".equals(rescoreMethod)) {
				rescorer = new TagNGramRescorer(config);
			}
			else if ("parse".equals(rescoreMethod)) {
				rescorer = new ParseCacheRescorer(config);
			}
			else if ("lex-parse".equals(rescoreMethod)) {
				rescorer = new ParseRescorer(config);
			}
			else {
				throw new Exception("Invalid rescore.method: " + rescoreMethod);
			}

			corrector = new SRILMNBestCorrector(config, segmentModel, dict, rescorer);
		}
		else if (lsc == -1) {
			corrector = new ParserCorrector(config, segmentModel, dict);
		}
		else {
			corrector = new SRILMCorrector(config, segmentModel, dict);
//			corrector = new SRILM2StepCorrector(config, segmentModel, dict);
		}
		
		ArrayList<String> correctedLines = corrector.correctLines(
				testAlignment.ocrSentences, testAlignment.transcriptionSentences);
		
		File outputFile = config.getOutputFile("output-file");
		PrintWriter outputWriter = new PrintWriter(outputFile, "UTF-8");
		
		for (int i = 0; i < testAlignment.ocrSentences.size(); i++) {
			String transcriptionLine = testAlignment.transcriptionSentences.get(i);
			String ocrLine = testAlignment.ocrSentences.get(i);
			String correctedLine = correctedLines.get(i);

			outputWriter.println(transcriptionLine);
			outputWriter.println(ocrLine);
			outputWriter.println(correctedLine);
			outputWriter.println();
		}
		outputWriter.close();
		
		WordAligner wordAligner = new WordAligner();
		WordAligner wordAlignerCheat = new WordAligner(true);
		
		int ocrDistance = 0;
		int correctedDistance = 0;
		int testSetLength = 0;
		int testSetWordCount = 0;
		double ocrWordErrors = 0;
		double ocrWordErrorsCheat = 0;
		double correctedWordErrors = 0;
		double correctedWordErrorsCheat = 0;
		for (int i = 0; i < correctedLines.size(); i++) {
			String trans = testAlignment.transcriptionSentences.get(i);
			String ocr = testAlignment.ocrSentences.get(i);
			
			// TODO: Currently CER should only be used to compare two variants of
			// the system; it is not very meaningful in *absolute* terms, because of
			// tokenization differences, de-hyphenation, and maybe other things I'm not
			// thinking of....
			testSetLength += trans.length();
			ocrDistance += editDistance(trans, ocr, true);
			correctedDistance += editDistance(trans, correctedLines.get(i), true);
			
			String[] testSetWords = tokenizer.tokenize(trans);
			String dehyphenatedTrans = SimpleTokenizer.dehyphenate(trans);
			String[] dehyphWords = tokenizer.tokenize(dehyphenatedTrans);
			testSetWordCount += testSetWords.length;
			ocrWordErrors += wordAligner.alignmentCost(
					testSetWords, tokenizer.tokenize(ocr));
			ocrWordErrorsCheat += wordAlignerCheat.alignmentCost(
					testSetWords, tokenizer.tokenize(ocr));
			correctedWordErrors += wordAligner.alignmentCost(
					dehyphWords, tokenizer.tokenize(correctedLines.get(i)));
			correctedWordErrorsCheat += wordAlignerCheat.alignmentCost(
					dehyphWords, tokenizer.tokenize(correctedLines.get(i)));
		}
		
		if (rescorer != null) {
			String summary = rescorer.summary();
			if (summary != null) {
				config.getLog().emptyLine();
				config.getLog().log(rescorer.summary());
			}
			
			rescorer.discard();
		}

		config.getLog().emptyLine();
		config.getLog().log("Word count: " + testSetWordCount);
		
		config.getLog().emptyLine();
		config.getLog().log("OCR edit distance: " + ocrDistance);
		config.getLog().log("Corrected edit distance: " + correctedDistance);
		config.getLog().log("Edit distance improvement: " + 
					(1.0 - (double) correctedDistance/ocrDistance));
		
		double ocrErrorRate = (double) ocrDistance / testSetLength;
		double correctedErrorRate = (double) correctedDistance / testSetLength;
		double errorRateReduction = (ocrErrorRate - correctedErrorRate)
				/ ocrErrorRate;
		config.getLog().emptyLine();
		config.getLog().log("OCR CER: " + ocrErrorRate);
		config.getLog().log("Corrected CER: " + correctedErrorRate);
		config.getLog().log("CER improvement: " + errorRateReduction);
		
		double ocrWordErrorRate = ocrWordErrors / testSetWordCount;
		double ocrWordErrorRateCheat = ocrWordErrorsCheat / testSetWordCount;
		double correctedWordErrorRate = correctedWordErrors / testSetWordCount;
		double correctedWordErrorRateCheat = 
				correctedWordErrorsCheat / testSetWordCount;
		double wordErrorRateReduction =
				(ocrWordErrorRate - correctedWordErrorRate) / ocrWordErrorRate;
		double wordErrorRateReductionCheat = 
				(ocrWordErrorRateCheat - correctedWordErrorRateCheat) / ocrWordErrorRateCheat;
		config.getLog().emptyLine();
		config.getLog().log("OCR WER: " + ocrWordErrorRate);
		config.getLog().log("Corrected WER: " + correctedWordErrorRate);
		config.getLog().log("WER improvement: " + wordErrorRateReduction);
		config.getLog().log("OCR WER (cheat): " + ocrWordErrorRateCheat);
		config.getLog().log("Corrected WER (cheat): " + correctedWordErrorRateCheat);
		config.getLog().log("WER improvement (cheat): " + wordErrorRateReductionCheat);

		config.getLog().emptyLine();
		
		config.getTimer().completePhase("Scored performance.");
	}
	
	public static class EvaluatorThread implements Runnable {
		
		RunConfig config;
		
		public EvaluatorThread(RunConfig config) {
			this.config = config;
		}

		@Override
		public void run() {
			try {
				System.out.println("Starting: " + this);
				config.prepare();
				evaluate(config);
				config.cleanUp();
			}
			catch (Exception e) {
				e.printStackTrace();
				if (config.getLog() != null)
					config.getLog().log("Caught Exception: " + e);
			}
		}
		
		@Override
		public String toString() {
			try {
				return "Run #" + config.getVariation() + "/"
					+ config.group.getNumVariations() + ": "
					+ config.getDescription();
			}
			catch (Exception e) {
				return "Exception thrown by RunConfig.getDescription()";
			}
		}
	}

	public static void main(String[] args) throws Exception {
		String configFile = args[0];
		int parallel = args.length < 2 ? 1 : Integer.parseInt(args[1]);
		
		ExecutorService executorService = Executors.newFixedThreadPool(parallel);
		for (RunConfig config: RunConfig.readRunConfigs(configFile))
			executorService.execute(new EvaluatorThread(config));

		executorService.shutdown();
		executorService.awaitTermination(99, TimeUnit.DAYS);
	}



	private static int min3(int a, int b, int c) {
		return Math.min(Math.min(a, b), c);
	}

	public static int editDistance(String str1, String str2, boolean ignoreWhitespace) {
		if (ignoreWhitespace)
			return editDistance(str1.replace(" ", ""), str2.replace(" ", ""), false);

		int[][] distance = new int[str1.length() + 1][str2.length() + 1];

		for (int i = 0; i <= str1.length(); i++)
			distance[i][0] = i;
		for (int j = 1; j <= str2.length(); j++)
			distance[0][j] = j;

		for (int i = 1; i <= str1.length(); i++)
			for (int j = 1; j <= str2.length(); j++)
				distance[i][j] = min3(
						distance[i - 1][j] + 1,
						distance[i][j - 1] + 1,
						distance[i - 1][j - 1]
								+ ((str1.charAt(i - 1) == str2.charAt(j - 1)) ? 0 : 1));

		return distance[str1.length()][str2.length()];
	}
}


