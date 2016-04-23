package ocr;

import java.io.IOException;

import ocr.util.RunConfig;
import ocr.util.Util;

public class TagNGramRescorer implements Rescorer {
	private static final boolean DEBUG = false;
	
	RunConfig config;
	boolean generateFully;
	Clustering clustering;
	NGramLanguageModel tagModel;
	
	public TagNGramRescorer(RunConfig config) throws IOException {
		this.config = config;
		generateFully = config.getBoolean("tag-ngram.generate-fully", true);
		clustering = Clustering.fromConfig(config);
		tagModel = NGramLanguageModel.getInstance(
				config.getDataFile("tag-ngram.model"),
				config.getInt("tag-ngram.order"));
	}

	@Override
	public double score(int i, SentenceCandidate sentence) throws IOException {
		double prWordGivenTags = 0;
		
		if (generateFully) {
			for (String word: sentence.tokens)
				prWordGivenTags += clustering.probOfWordGivenCluster(word);
		}
		
		String[] tagSequence = clustering.clusterSequence(sentence.tokens);
		if (DEBUG) {
			config.getLog().log("Rescoring: " + Util.join(" ", sentence.tokens));
			config.getLog().log("      tags: " + Util.join(" ", tagSequence));
		}
		
		return prWordGivenTags + tagModel.probOfSentence(tagSequence);
	}

	@Override
	public String summary() {
		return null;
	}
	
	@Override
	public void discard() throws IOException {
	}
}






