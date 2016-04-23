package ocr;

import java.util.Iterator;

import ocr.NGramModel.TokenHistory;
import ocr.util.TopNList;
import ocr.util.Util;

public class UnigramCorrector {

	SegmentModel segmentModel;
	NGramModel wordModel;
	InexactDictionary dictionary;
	
	public UnigramCorrector(
			SegmentModel segmentModel,
			NGramModel wordModel,
			InexactDictionary dictionary)
	{
		this.segmentModel = segmentModel;
		this.wordModel = wordModel;
		this.dictionary = dictionary;
	}

	public String correctLine(String ocr) {
		String[] tokens = SimpleTokenizer.tokenize(ocr, true);
		
		String[] correctedTokens = new String[tokens.length];
		for (int i = 0; i < tokens.length; i++) {
			String token = tokens[i];
			
			String[] candidates = dictionary.topMatches(token, 20);
			if (candidates.length == 0) {
				correctedTokens[i] = token;
				continue;
			}
			
			TopNList<String> topCandidates = new TopNList<String>(1);
			for (String candidate: candidates) {
				SegmentAligner aligner = new SegmentAligner(segmentModel, token, candidate, 3);
				aligner.populate();
				double prob = aligner.probOfBestAlignment();
				if (prob == Double.NEGATIVE_INFINITY)
					continue;
				prob += wordModel.logProb(new TokenHistory(candidate));
				topCandidates.add(candidate, prob);
			}
			
			Iterator<String> iter = topCandidates.iterator();
			if (iter.hasNext())
				correctedTokens[i] = iter.next();
			else
				correctedTokens[i] = token;
		}
		
		return Util.join("", correctedTokens);
	}


	public static void main(String[] args) {
		
	}

}




