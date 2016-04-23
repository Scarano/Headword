package edu.neu.ccs.headword;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.neu.ccs.headword.SimpleTokenizer.Token;
import edu.neu.ccs.headword.util.Log;
import edu.neu.ccs.headword.util.PhaseTimer;
import edu.neu.ccs.headword.util.RunConfig;
import edu.neu.ccs.headword.util.Util;

public class ParserCorrector implements Corrector {
	
	private static final boolean DEBUG = false;
	
	RunConfig config;
	Log log;
	PhaseTimer timer;
	
	Clustering clustering;
	
	LatticeBuilder latticeBuilder;
	
	LatticeParser parser;
			
	public ParserCorrector(
			RunConfig config, SegmentModel segmentModel, InexactDictionary dictionary
	) throws IOException
	{
		this.config = config;
		log = config.getLog();
		timer = config.getTimer();
	
		clustering = Clustering.fromConfig(config);
		
		latticeBuilder = new LatticeBuilder(config, segmentModel, dictionary);
		
		parser = LatticeParser.fromConfig(config);
	}
	

	@Override
	public ArrayList<String> correctLines(List<String> ocrLines, List<String> transLines)
			throws IOException
	{
		ArrayList<String> results = new ArrayList<String>();
		
		List<List<Token>> ocrTokens = new ArrayList<List<Token>>(ocrLines.size());
		for (String ocr: ocrLines)
			ocrTokens.add(SimpleTokenizer.tokenizePreservingWhitespace(ocr));

		int sentNum = 0;
		
		for (List<Token> tokens: ocrTokens) {
			StringLattice lattice = latticeBuilder.channelLattice(tokens);
			
			TaggedLattice taggedLattice = new TaggedLattice(lattice, clustering);
			
			int[] parse = parser.parse(taggedLattice);
			
			List<String> usedWords = new ArrayList<String>();
			for (int i = 0; i < parse.length; i++) {
				if (DEBUG) {
					System.out.printf("%s %d\n",
							taggedLattice.edges.get(i).token.getString(), parse[i]);
				}
				if (parse[i] != -1)
					usedWords.add(taggedLattice.edges.get(i).token.getString());
			}
			
			results.add(Util.join(" ",  usedWords));
			
			sentNum++;
			
			if (sentNum % 10 == 0) {
				System.out.printf("Processed sentence %d of %d  \r",
						sentNum, ocrTokens.size());
			}
		}
		
		timer.completePhase("Corrected OCR using LatticeParser");

		return results;
	}
	

	public static void main(String[] args) {
		for (int i = 0; i < 10; i++) {
			System.out.printf("%d  \r", i);
		}
		System.out.println();
	}

}




