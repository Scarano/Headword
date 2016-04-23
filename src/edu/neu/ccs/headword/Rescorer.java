package edu.neu.ccs.headword;

import java.io.IOException;

public interface Rescorer {

	public double score(int sentenceNumber, SentenceCandidate sentence) throws IOException;

	String summary();

	public void discard() throws IOException;
}
