package ocr;

import java.io.File;
import java.io.IOException;

import cern.colt.Arrays;
import ocr.LatticeParser.DMVScorer;
import ocr.util.RunConfig;

public class ParseRescorer implements Rescorer {
	RunConfig config;
	boolean caseless;
	Clustering clustering;
	DMV lexicalModel;
	LatticeParser parser;
	boolean combine;
	boolean marginalize;
	
	public ParseRescorer(RunConfig config)
		throws IOException
	{
		this.config = config;
		caseless = config.getString("clustering.caseless", "").equals("cl") ? true : false;
		assert !caseless;
		clustering = Clustering.fromConfig(config);
		combine = config.getBoolean("parser.combined-model");
		marginalize = config.getBoolean("parser.marginal-probability");

		if (!combine) {
			lexicalModel = new LexDMV(
					config.getDataFile("parser.lex-model-file").getPath(),
					config.getDouble("parser.lex-model-alpha"),
					0.0,
					config.getDouble("parser.lex-unk-prob"));
		}
		
		parser = LatticeParser.fromConfig(config);
	}

	@Override
	public double score(int i, SentenceCandidate sentence) throws IOException {
		int[] parse = parser.parse(sentence.tokens, clustering);
		if (combine) {
			return marginalize ? parser.sentProb() : parser.viterbiProb();
		}
		else {
			if (marginalize) {
				assert false;
				// warning! this currently makes no sense
				return /* parser.sentProb() + */
						parser.lexicalProbMarginalizedOverParses(lexicalModel);
			}
			else {
				return /* parser.viterbiProb() + */
						parser.lexicalProbGivenParse(sentence.tokens, parse, lexicalModel);
			}
		}
	}

	@Override
	public String summary() {
		return "";
	}
	
	@Override
	public void discard() throws IOException {
	}
	
	public static void main(String[] args) throws IOException {
		LatticeParser.debug = true;
		
		String clusterFile = args[0];
		String tagModelFile = args[1];
		String lexModelFile = args[2];
		String sent = args[3];
		
		String[] tokens = sent.split(" ");
		
		Clustering clustering = new Clustering(new File(clusterFile), true, true);
		DMV tagDmv = new TagDMV(tagModelFile, false, 1.0);
		DMV lexDmv = new LexDMV(lexModelFile, 1.0, 0.0, 1e-5);
		LatticeParser parser = new LatticeParser(new DMVScorer(tagDmv), true);
		int[] parse = parser.parse(tokens, clustering);
System.out.println(Arrays.toString(tokens));
System.out.println(Arrays.toString(parse));
		double p = parser.lexicalProbGivenParse(tokens, parse, lexDmv);
		System.out.printf("p(%s | %s) = %f (log = %f)\n", 
				sent, Arrays.toString(parse), Math.exp(p), p);
	}
}






