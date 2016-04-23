package edu.neu.ccs.headword;

import java.io.IOException;
import java.util.List;

import edu.neu.ccs.headword.util.RunConfig;

public class TagOracleRescorer implements Rescorer {
	String[][] correctTags;
	Clustering clustering;
	ArrayAligner aligner;
	
	public TagOracleRescorer(RunConfig config, List<String> transcriptionSentences)
		throws IOException
	{
		clustering = new Clustering(config.getDataFile("clustering.file"), true, true);
		aligner = new ArrayAligner();
		
		correctTags = new String[transcriptionSentences.size()][];
		for (int i = 0; i < correctTags.length; i++) {
			String[] tokens = SimpleTokenizer.tokenizeDiscardWhitespace(
					transcriptionSentences.get(i));
			correctTags[i] = clustering.clusterSequence(tokens);
		}
	}

	@Override
	public double score(int i, SentenceCandidate sentence) {
		String[] tagSequence = clustering.clusterSequence(sentence.tokens);
		return -aligner.alignmentCost(correctTags[i], tagSequence);
	}

	@Override
	public String summary() {
		return null;
	}

	@Override
	public void discard() {
		
	}
}

