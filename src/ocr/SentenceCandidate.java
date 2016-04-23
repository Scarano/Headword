package ocr;

import java.util.Arrays;

import ocr.util.Util;

public class SentenceCandidate {
	
	private static final double LN_10 = Math.log(10);

	public double channelProb;
	public double languageProb;
	public double prob;
	public String[] tokens;
	
	public SentenceCandidate(String nbestFormatString) {
		String[] terms = nbestFormatString.split(" ");
		channelProb = Double.parseDouble(terms[0]) * LN_10;
		languageProb = Double.parseDouble(terms[1]) * LN_10;
		prob = channelProb + languageProb;
		tokens = Arrays.copyOfRange(terms, 4, terms.length - 1);
		
		// Interning here saves memory because we (unfortunately) keep all of
		// a document's SentenceCandidates in memory at once.
		// But it also means that token strings never get garbage collected. Is it worth it?
		// This should probably be removed if I ever split documents in to blocks to be
		// processed separately.
//		for (int i = 0; i < tokens.length; i++)
//			tokens[i] = tokens[i].intern();
	}
	
	public String toString() {
		return String.format("%4f (%4f + %4f): %s",
				prob, channelProb, languageProb,
				Util.join(" ", tokens));
	}
}


