package ocr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.GZIPInputStream;

import ocr.SimpleTokenizer.Token;
import ocr.util.Log;
import ocr.util.PhaseTimer;
import ocr.util.RunConfig;
import ocr.util.Util;

public class SRILMNBestCorrector implements Corrector {
	
	private static final boolean DEBUG = false;
	
	RunConfig config;
	Log log;
	PhaseTimer timer;
	
	LatticeBuilder latticeBuilder;
	
	int maxSentenceCandidates;
	int lmOrder;
	String lmFile;
	String latticeToolPath;
	File tempDir;
	File latticeListFile;
	File refsFile;
	
	Rescorer rescorer;
	double rescorerWeight;
	boolean rescorerLogLinearMix;
	boolean rescorerNormalize;
			
	public SRILMNBestCorrector(
			RunConfig config, SegmentModel segmentModel, InexactDictionary dictionary,
			Rescorer rescorer
	) throws IOException
	{
		this.config = config;
		log = config.getLog();
		timer = config.getTimer();
		
		latticeBuilder = new LatticeBuilder(config, segmentModel, dictionary);
		
		maxSentenceCandidates = config.getInt("lm.sentence-candidates");
		lmOrder = config.getInt("lm.order");
		lmFile = config.getDataFile("srilm.model").getAbsolutePath();
		latticeToolPath = config.getString("srilm.bin") + "/lattice-tool";
		tempDir = config.getTempDir();
		latticeListFile = new File(tempDir, "lattice-list.txt");
		refsFile = new File(tempDir, "refs.txt");

		this.rescorer = rescorer;
		rescorerWeight = config.getDouble("rescore.weight");
		rescorerLogLinearMix = config.getBoolean("rescore.log-linear-mix", false);
		rescorerNormalize = config.getBoolean("rescore.normalize", false);
		
		// As a performance optimization to make it easier to efficiently run comparisons
		// of rescorer weights that include 0, if the rescorer weight is 0, don't bother
		// generating more than one candidate for each sentence
		if (rescorerWeight == 0.0)
			maxSentenceCandidates = 1;
	}
	

	/* (non-Javadoc)
	 * @see ocr.Corrector#correctLines(java.util.List, java.util.List)
	 */
	@Override
	public ArrayList<String> correctLines(List<String> ocrLines, List<String> transLines)
			throws IOException
	{
		
		List<List<Token>> ocrTokens = new ArrayList<List<Token>>(ocrLines.size());
		for (String ocr: ocrLines)
			ocrTokens.add(SimpleTokenizer.tokenizePreservingWhitespace(ocr));
		
		ArrayList<File> psfgFiles = new ArrayList<File>(ocrLines.size());
		for (List<Token> tokens: ocrTokens) {
			StringLattice lattice = latticeBuilder.channelLattice(tokens);
			
			int sentenceNumber = psfgFiles.size();
			File psfgFile = new File(tempDir, "s"+sentenceNumber);
			
			lattice.savePFSG(psfgFile, sentenceNumber);
			psfgFiles.add(psfgFile);
		}
		timer.completePhase("Generated PFSG files");
		
		PrintWriter writer = new PrintWriter(latticeListFile);
		for (File psfgFile: psfgFiles)
			writer.println(psfgFile.getPath());
		writer.close();
		
		// Print transcription tokens to a file, to be used to calculate oracle accuracy
		PrintStream refsOut = new PrintStream(refsFile, "UTF-8");
		for (String line: transLines) {
			refsOut.println(Util.join(" ", SimpleTokenizer.tokenize(line, false)));
		}
		refsOut.close();
		
		log.log("Oracle WER: " + oracleAccuracy(ocrLines.size()));
		log.emptyLine();
		
		ArrayList<String> results = new ArrayList<String>(ocrLines.size());
		generateNBest(ocrTokens);

		timer.completePhase("Ran SRILM lattice-tool.");

		double[] rescorerProbs = new double[maxSentenceCandidates];
		
		for (int i = 0; i < ocrLines.size(); i++) {
			
			FileInputStream is = new FileInputStream(new File(tempDir, "s" + i + ".gz"));
			GZIPInputStream gis = new GZIPInputStream(is);
			BufferedReader sentReader = new BufferedReader(new InputStreamReader(gis, "UTF-8"));
			
			ArrayList<SentenceCandidate> candidates = 
					new ArrayList<SentenceCandidate>(maxSentenceCandidates);
			String line;
			while ((line = sentReader.readLine()) != null) {
				candidates.add(new SentenceCandidate(line));
			}
			
			sentReader.close();
			
			double ngramTotal = Double.NEGATIVE_INFINITY;
			double rescorerTotal = Double.NEGATIVE_INFINITY;
			for (int k = 0; k < candidates.size(); k++) {
				SentenceCandidate candidate = candidates.get(k);
				ngramTotal = Util.logSum(ngramTotal, candidate.languageProb);
				rescorerProbs[k] = rescorer.score(i, candidate);
				rescorerTotal = Util.logSum(rescorerTotal, rescorerProbs[k]);
			}
			
			double bestScore = Double.NEGATIVE_INFINITY;
			SentenceCandidate bestCandidate = null;
			for (int k = 0; k < candidates.size(); k++) {
				SentenceCandidate candidate = candidates.get(k);
				double score;
				if (rescorerLogLinearMix) {
					score = 
						candidate.prob +
						rescorerWeight * rescorerProbs[k];
					if (DEBUG) { if (i % 100 == 0) {
						log.log(String.format("%d %d: %f = %f + %f * %f",
								i, k,
								score,
								candidate.prob,
								rescorerWeight,
								rescorer.score(i, candidate)));
					}}
				}
				else {
					double channelProb = candidate.channelProb;
					double ngramProb = candidate.languageProb;
					if (rescorerNormalize)
						ngramProb -= ngramTotal;
					double rescorerProb = rescorerProbs[k];
					if (rescorerNormalize)
						rescorerProb -= rescorerTotal;
					score = channelProb + Util.mixInLogSpace(
							ngramProb, rescorerProb, rescorerWeight);
					if (DEBUG) { if (i % 100 == 0) {
						log.log(String.format("%d %d: %f = %f + mix(%f, %f, %f)",
								i, k,
								score,
								channelProb,
								ngramProb,
								rescorerProb,
								rescorerWeight));
					}}
				}
				if (score > bestScore) {
					bestCandidate = candidate;
					bestScore = score;
				}
			}
			
			if (bestCandidate == null) {
				log.logf("Warning! No non-zero-score candidate found for: %s\n", ocrLines.get(i));
				bestCandidate = candidates.get(0);
			}
			results.add(Util.join(" ",  bestCandidate.tokens));
			
			if ((i+1) % 10 == 0)
				System.out.printf("Re-scored %d / %d sentences\r", (i+1), ocrLines.size());
		}
		
		timer.completePhase("Re-scored n-best lists.");

		return results;
	}
	
	void generateNBest(List<List<Token>> ocrTokens) throws IOException {
		String command = latticeToolPath 
				+ " -in-lattice-list " + latticeListFile
				+ " -nbest-decode " + maxSentenceCandidates
				+ " -out-nbest-dir " + tempDir
				+ " -lm " + lmFile
				+ " -order " + lmOrder
				+ " -zeroprob-word <unk>";
		log.log("Executing " + command);
		Process proc = Runtime.getRuntime().exec(command);
		BufferedReader reader 
			= new BufferedReader(new InputStreamReader(proc.getInputStream()));
		BufferedReader errReader
			= new BufferedReader(new InputStreamReader(proc.getErrorStream()));

		String errLine;
		while ((errLine = errReader.readLine()) != null) {
			if (
					errLine.length() > 0 &&
					!errLine.startsWith("processing") &&
					!errLine.contains("non-zero probability for <unk> in closed-vocabulary LM")
			) {
				log.log("Error from lattice-tool: " + errLine);
			}
		}
		reader.close();
		errReader.close();
		System.out.println();
	}

	double oracleAccuracy(int numSentences) throws IOException {
		String command = latticeToolPath 
				+ " -in-lattice-list " + latticeListFile
				+ " -ref-file " + refsFile;
		log.log("Executing " + command);
		Process proc = Runtime.getRuntime().exec(command);
		BufferedReader reader
			= new BufferedReader(new InputStreamReader(proc.getInputStream()));
		BufferedReader errReader
			= new BufferedReader(new InputStreamReader(proc.getErrorStream()));

		int totalWords = 0;
		int totalErrors = 0;
		
		String line;
		while ((line = reader.readLine()) != null) {
			// Consume stderr -- otherwise, buffer will fill & process will block
			while (errReader.ready()) {
				String errorLine = errReader.readLine();
				if (errorLine.length() > 0 && !errorLine.startsWith("processing"))
					log.log("Error from lattice-tool: " + errorLine);
			}

			HashMap<String, Integer> accuracyInfo = new HashMap<String, Integer>();
			String[] terms = line.split(" ");
			if (terms.length % 2 != 0)
				throw new IOException("Unexpected output from SRI-LM: '" + line + "'");
			for (int i = 0; i < terms.length; i += 2)
				accuracyInfo.put(terms[i], Integer.parseInt(terms[i+1]));

			totalWords += accuracyInfo.get("words");
			totalErrors += accuracyInfo.get("wer");
//System.out.println(line + ": " + accuracyInfo.get("wer") + " / " + accuracyInfo.get("words"));
		}
		reader.close();
		errReader.close();
		
		return (double) totalErrors / totalWords;
	}

	public static void main(String[] args) {
		
	}

}




