package ocr;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import ocr.StringLattice.Edge;
import ocr.util.Log;
import ocr.util.PhaseTimer;
import ocr.util.RunConfig;
import ocr.util.Util;
import ocr.SimpleTokenizer.Token;


/**
 * 
 * Like SRILMCorrector, except it uses a second, tag-based n-gram language model
 * to re-score, in addition to the token-based n-gram language model. First, SRILM
 * lattice-tool is used to create lattices with channel + lm1 probabilities. Those
 * lattices are then run through lattice-tool again and viterbi-decoded using lm2.
 * 
 * (Unfortunately, SRILM doesn't seem to allow you to mix class-based and non-class-based
 * language models, so this must be done with separate invocations of lattice-tool.)
 */
public class SRILM2StepCorrector implements Corrector {
	
	static final double LN_10 = Math.log(10);
	
	RunConfig config;
	Log log;
	PhaseTimer timer;
	
	LatticeBuilder latticeBuilder;
	
	Clustering clustering;
	
	String latticeToolPath;
	
	int lmOrder;
	String lmFile;
	
	int tagLMOrder;
	String tagLMFile;
	double tagLMWeight;
	
	File tempDir;
	File refsFile;
	File psfgListFile;
	File htkDir;
	File htkListFile;
	File htkLM1Dir;
	File htkLM1PosteriorDir;
	File htkLM1PosteriorListFile;
//	File htkLM2Dir;
//	File htkLM2ListFile;
	File classesFile;
	
	public SRILM2StepCorrector(
			RunConfig config, SegmentModel segmentModel, InexactDictionary dictionary)
		throws IOException
	{
		this.config = config;
		log = config.getLog();
		timer = config.getTimer();
		
		latticeBuilder = new LatticeBuilder(config, segmentModel, dictionary);
		
		clustering = Clustering.fromConfig(config);

		latticeToolPath = config.getString("srilm.bin") + "/lattice-tool";
		
		lmOrder = config.getInt("lm.order");
		lmFile = config.getDataFile("srilm.model").getPath();

		tagLMOrder = config.getInt("tag-ngram.order", 0);
		tagLMFile = config.getDataFile("tag-ngram.model", "").getPath();
		tagLMWeight = config.getDouble("tag-ngram.weight", 0.0);

		tempDir = config.getTempDir();
		refsFile = new File(tempDir, "refs.txt");
		psfgListFile = new File(tempDir, "psfg-list.txt");
		htkDir = new File(tempDir, "htk");
		htkListFile = new File(tempDir, "htk-list.txt");
		htkLM1Dir = new File(tempDir, "htk-lm1");
		htkLM1PosteriorDir = new File(tempDir, "htk-lm1-posterior");
		htkLM1PosteriorListFile = new File(tempDir, "htk-lm1-posterior-list.txt");
//		htkLM2Dir = new File(tempDir, "htk-lm2");
//		htkLM2ListFile = new File(tempDir, "htk-lm2-list.txt");
		classesFile = new File(tempDir, "tag-classes.txt");
	}
	
	public ArrayList<String> correctLines(List<String> ocrLines, List<String> transLines)
			throws IOException
	{
		HashSet<String> usedWords = new HashSet<String>();
		
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
			
			// Also keep track of which words actually appear in the lattice, in case
			// we need to output their classes (clusters) for class-based language modeling
			for (Edge edge: lattice.getEdges())
				usedWords.add(edge.token);
		}
		timer.completePhase("Generated PFSG files");
		
		PrintWriter writer = new PrintWriter(psfgListFile);
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
		timer.completePhase("Computed oracle accuracy");
		
		convertToHTK(ocrLines.size());
		timer.completePhase("Output HTK lattices");
		
		if (tagLMOrder > 0) {
			clustering.saveSRILMClasses(classesFile, usedWords);
			scoreWithLM1(ocrLines.size());
			timer.completePhase("Computed tag-LM-scored lattices");
		}
		
		ArrayList<String> results = new ArrayList<String>(ocrLines.size());
		String[][] correctedLines = decode(ocrLines.size());
		for (int i = 0; i < correctedLines.length; i++) {
			String[] correctedTokenStrs = correctedLines[i];
			for (int j = 0; j < correctedTokenStrs.length; j++) {
				if (correctedTokenStrs[j].equals("<unk>")) {
					log.log("<unk> bug");
//					correctedTokenStrs[j] = ocrTokens.get(i).get(j).toString();
				}
			}
			results.add(Util.join(" ",  correctedTokenStrs));
		}
		
		timer.completePhase("Ran SRILM lattice-tool.");

		return results;
	}
	
	void convertToHTK(int numSentences) throws IOException {
		String command = latticeToolPath 
				+ " -in-lattice-list " + psfgListFile
				+ " -write-htk "
				+ " -out-lattice-dir " + htkDir;
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
					!errLine.contains("non-zero probability for <unk> in closed-vocabulary LM") &&
					!errLine.contains("to be dumped to")
			) {
				log.log("Error from lattice-tool: " + errLine);
			}
		}
		reader.close();
		errReader.close();
		
		PrintWriter writer = new PrintWriter(htkListFile);
		for (int i = 0; i < numSentences; i++) {
			writer.println(new File(htkDir, "s"+i).getPath());
		}
		writer.close();
	}

	void scoreWithLM1(int numSentences) throws IOException {
		String command = latticeToolPath 
				+ " -read-htk -in-lattice-list " + htkListFile
				+ " -simple-classes -classes " + classesFile
				+ " -lm " + tagLMFile
				+ " -order " + tagLMOrder
				+ " -zeroprob-word <unk>"
				+ " -write-htk -out-lattice-dir " + htkLM1Dir;
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
					!errLine.contains("non-zero probability for <unk> in closed-vocabulary LM") &&
					!errLine.contains("to be dumped to")
			) {
				log.log("Error from lattice-tool: " + errLine);
			}
		}
		reader.close();
		errReader.close();
		
		htkLM1PosteriorDir.mkdir();
		PrintWriter writer = new PrintWriter(htkLM1PosteriorListFile);
		for (int i = 0; i < numSentences; i++) {
			File htkLM1 = new File(htkLM1Dir, "s"+i);
			File htkLM1Posterior = new File(htkLM1PosteriorDir, "s"+i);
			
			HTKLattice.setAcousticToPosterior(htkLM1, htkLM1Posterior, tagLMWeight);
			
			writer.println(htkLM1Posterior.getPath());
		}
		writer.close();
	}

	String[][] decode(int numSentences) throws IOException {
		File inputLatticeListFile = tagLMOrder == 0 ? htkListFile : htkLM1PosteriorListFile;
		String command = latticeToolPath 
				+ " -read-htk -in-lattice-list " + inputLatticeListFile
				+ " -viterbi-decode "
				+ " -lm " + lmFile
				+ " -order " + lmOrder
				+ " -zeroprob-word <unk>";
		log.log("Executing " + command);
		Process proc = Runtime.getRuntime().exec(command);
		BufferedReader reader 
			= new BufferedReader(new InputStreamReader(proc.getInputStream()));
		BufferedReader errReader
			= new BufferedReader(new InputStreamReader(proc.getErrorStream()));

		String[][] results = new String[numSentences][];
		
		int lineNum = 0;
		String line;
		while ((line = reader.readLine()) != null) {
			// Consume stderr -- otherwise, buffer will fill & process will block
			while (errReader.ready()) {
				String errorLine = errReader.readLine();
				if (
					errorLine.length() > 0 &&
					!errorLine.startsWith("processing") &&
					!errorLine.contains("non-zero probability for <unk> in closed-vocabulary LM")
				) {
					log.log("Error from lattice-tool: " + errorLine);
				}
			}

			if (line.startsWith("s")) {
				String[] words = line.split(" ");
				results[lineNum++] = Arrays.copyOfRange(words, 2, words.length - 1);
			}
			if (lineNum % 10 == 0)
				System.out.print(lineNum + " / " + numSentences + "    \r");
		}
		reader.close();
		errReader.close();
		System.out.println();
		
		if (lineNum != numSentences) {
			throw new IOException(String.format(
					"lattice-tool only output %d lines (%d expected)", lineNum, numSentences));
		}
		
		return results;
	}

	double oracleAccuracy(int numSentences) throws IOException {
		String command = latticeToolPath 
				+ " -in-lattice-list " + psfgListFile
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




