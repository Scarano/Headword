package ocr;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import ocr.SimpleTokenizer.Token;
import ocr.Util;

public class SRILMCorrector implements Corrector {
	
	RunConfig config;
	Log log;
	PhaseTimer timer;
	
	LatticeBuilder latticeBuilder;
	
	int lmOrder;
	String lmFile;
	String latticeToolPath;
	File tempDir;
	File latticeListFile;
	File refsFile;
			
	public SRILMCorrector(
			RunConfig config, SegmentModel segmentModel, InexactDictionary dictionary)
		throws IOException
	{
		this.config = config;
		log = config.getLog();
		timer = config.getTimer();

		latticeBuilder = new LatticeBuilder(config, segmentModel, dictionary);
		
		lmOrder = config.getInt("lm.order");
		lmFile = config.getDataFile("srilm.model").getAbsolutePath();
		latticeToolPath = config.getString("srilm.bin") + "/lattice-tool";
		tempDir = config.getTempDir();
		latticeListFile = new File(tempDir, "lattice-list.txt");
		refsFile = new File(tempDir, "refs.txt");
	}
	
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
// This is commented out because it is no longer accurate. For one thing, it needs to be
// updated to use the correct tokenizer.
//		PrintStream refsOut = new PrintStream(refsFile, "UTF-8");
//		for (String line: transLines) {
//			refsOut.println(Util.join(" ", SimpleTokenizer.tokenize(line, false)));
//		}
//		refsOut.close();
//		
//		log.log("Oracle WER: " + oracleAccuracy(ocrLines.size()));
//		log.emptyLine();
		
		ArrayList<String> results = new ArrayList<String>(ocrLines.size());
		String[][] correctedLines = decode(ocrLines.size());
		for (int i = 0; i < correctedLines.length; i++) {
			String[] correctedTokenStrs = correctedLines[i];
			for (int j = 0; j < correctedTokenStrs.length; j++) {
				if (correctedTokenStrs[j].equals("<unk>")) {
//if (j > 0 && j < correctedTokenStrs.length - 1)
//	log.log("<unk>\n in: "
//		+ correctedTokenStrs[j-1]
//		+ " " + correctedTokenStrs[j]
//		+ " " + correctedTokenStrs[j+1]
//		+ "\n  -> "
//		+ ocrTokens.get(i).get(j-1).toString()
//		+ " " + ocrTokens.get(i).get(j).toString()
//		+ " " + ocrTokens.get(i).get(j+1).toString());
					correctedTokenStrs[j] = ocrTokens.get(i).get(j).toString();
				}
			}
			results.add(Util.join(" ",  correctedTokenStrs));
		}
		
		timer.completePhase("Ran SRILM lattice-tool.");

		return results;
	}
	
	String[][] decode(int numSentences) throws IOException {
		String command = latticeToolPath 
				+ " -in-lattice-list " + latticeListFile
				+ " -viterbi-decode "
				+ " -lm " + lmFile
				+ " -order " + lmOrder
				+ " -zeroprob-word <unk>";
//				+ " -unk" ;
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
				if (errorLine.length() > 0 && !errorLine.startsWith("processing"))
					log.log("Error from lattice-tool: " + errorLine);
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




