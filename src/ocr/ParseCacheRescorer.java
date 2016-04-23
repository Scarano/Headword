package ocr;

import java.io.IOException;

import ocr.util.RunConfig;

public class ParseCacheRescorer implements Rescorer {
	RunConfig config;
	boolean caseless;
	Clustering clustering;
	ArrayAligner aligner;
	ParseCache cache;
	
	public ParseCacheRescorer(RunConfig config)
		throws IOException
	{
		this.config = config;
		caseless = config.getString("clustering.caseless", "").equals("cl") ? true : false;
		clustering = new Clustering(config.getDataFile("clustering.file"), caseless, true);
		aligner = new ArrayAligner();
		cache = new ParseCache(config);
	}

	@Override
	public double score(int i, SentenceCandidate sentence) throws IOException {
		String[] tagSequence = clustering.clusterSequence(sentence.tokens);
		int[] parse = new int[tagSequence.length];
		double score = -cache.findParse(tagSequence, parse);
		if (Double.isInfinite(score))
			return Double.NEGATIVE_INFINITY;
		else
			return score;
	}

	@Override
	public String summary() {
		return cache.summaryStats();
	}
	
	@Override
	public void discard() throws IOException {
		cache.save();
	}
}






