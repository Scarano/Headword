package ocr;

import java.io.IOException;
import java.util.List;

import ocr.SimpleTokenizer.Token;
import ocr.TopNList.ScoredItem;

public class LatticeBuilder {
	
	RunConfig config;
	
	SegmentModel segmentModel;
	double channelModelWeight;

	InexactDictionary dictionary;
	int maxDictionaryMatches;
	
	int maxWordCandidates;
	int maxWordMerges;
	int maxPuncMerges;
	boolean freeDehyph;
	
	public LatticeBuilder(
			RunConfig config, SegmentModel segmentModel, InexactDictionary dictionary
	) throws IOException {
		this.config = config;
		this.segmentModel = segmentModel;
		channelModelWeight = config.getDouble("channel.weight");
		this.dictionary = dictionary;
		maxDictionaryMatches = config.getInt("dictionary.max-matches");
		maxWordCandidates = config.getInt("lattice.word-candidates");
		maxWordMerges = config.getInt("lattice.max-word-merges");
		maxPuncMerges = config.getInt("lattice.max-punctuation-merges");
		freeDehyph = config.getBoolean("lattice.allow-free-dehyphenation");
		
		if (freeDehyph && maxPuncMerges < 3) {
			config.getLog().log("WARNING: lattice.allow-free-dehyphenation is switched on, "
					+ "but it will have no effect, because lattice.max-punctuation-merges is "
					+ "less than 3.");
		}
	}

	public StringLattice channelLattice(List<Token> tokens) {

		StringLattice lattice = new StringLattice(tokens.size() + 1);
		
		for (int start = 0; start < tokens.size(); start++) {
			int end = start + 1;
			String span = tokens.get(start).toString();
			
			for (;;) {
				
				if (
					freeDehyph &&
					end - start == 3 && 
					isHyphen(tokens.get(start + 1).toString())
				) {
					// Dehyphenation special case
					span = tokens.get(start).toString() + tokens.get(start+2).toString();
				}

				// Should the OCR token always be a candidate?
				// (The dictionary contains tokens from the OCR document, but not necessarily
				// the multi-token spans we create.)
				String[] candidates = dictionary.topMatches(span, maxDictionaryMatches);
				if (candidates.length == 0)
					candidates = new String[] {span.toString()};

				TopNList<String> topCandidates = new TopNList<String>(maxWordCandidates);
				for (String candidate: candidates) {
					if (candidate.contains("\u00e0")) {
						// The utf-8 of 'a' with a grave accent (unicode 0xe0) contains 0xa0, 
						// which is interpreted as space by SRILM. I'm excluding
						// words with that character as a quick and dirty way to avoid this problem.
						continue;
					}
					SegmentAligner aligner =
							new SegmentAligner(segmentModel, candidate, span, config);
					aligner.populate();
					double prob = aligner.probOfBestAlignment();
					if (prob == Double.NEGATIVE_INFINITY)
						continue;
					prob *= channelModelWeight;
					topCandidates.add(candidate, prob);
				}
				
				for (ScoredItem<String> candidate: topCandidates.items)
					lattice.addEdge(start, end, candidate.item, candidate.score);

				// If we haven't hit the token-merging limit yet, then add another input token 
				// and loop again to add candidates for the multi-token span.
				if (end < tokens.size()) {
					Token nextToken = tokens.get(end);
					if (nextToken.precededByWhitespace) {
						if (
								freeDehyph &&
								end - start == 2 &&
								isHyphen(tokens.get(end-1).toString())
						) {
							end++;
							span += nextToken;
						}
						else if (end - start < maxWordMerges) {
							end++;
							span += " " + nextToken;
						}
						else break;
					}
					else {
						if (end - start < maxPuncMerges) {
							end++;
							span += nextToken;
						}
						else break;
					}
				}
				else break;
			}
		}
		
		return lattice;
	}
	
	static boolean isHyphen(String s) {
		return s.equals("\u00ad") || s.equals("-");
	}
	
	
	public static void main(String[] args) {

	}

}



