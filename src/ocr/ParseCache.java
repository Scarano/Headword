package ocr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;

import edu.cmu.cs.lti.ark.dageem.Alphabet;
import edu.cmu.cs.lti.ark.dageem.Chart;
import edu.cmu.cs.lti.ark.dageem.DMVGrammar;
import edu.cmu.cs.lti.ark.dageem.DMVParserViterbi;
import edu.cmu.cs.lti.ark.dageem.DependencyParser;
import edu.cmu.cs.lti.ark.dageem.EisnerSattaAlgorithm;
import edu.cmu.cs.lti.ark.dageem.EisnerSattaChart;
import edu.cmu.cs.lti.ark.dageem.GoalTerm;
import edu.cmu.cs.lti.ark.dageem.SentenceCorpus;
import edu.cmu.cs.lti.ark.dageem.SentenceDocument;
import ocr.util.RunConfig;
import ocr.util.Util;

public class ParseCache {
	
	public static class TagSequence {
		public String[] tags;
		
		/** Note that tags must be intern()ed Strings! **/
		public TagSequence(String[] tags) {
			this.tags = tags;
		}
		
		@Override
		public boolean equals(Object other) {
			if (other == null)
				return false;
			if (!(other instanceof TagSequence))
				return false;
			TagSequence otherTagSequence = (TagSequence) other;
			if (tags.length != otherTagSequence.tags.length)
				return false;
			for (int i = 0; i < tags.length; i++) {
				// Should be able to use == here, but that stopped working when I
				// added saving and loading. intern()ing in the loader doesn't work?
				if (!tags[i].equals(otherTagSequence.tags[i]))
					return false;
			}
			return true;
		}
		
		@Override
		public int hashCode() {
			return Arrays.hashCode(tags);
		}
	}
	
	public static class ParseEntry {
		public int refCount;
		public int[] parse;
		public double score;
		
		public ParseEntry(int[] parse, double score) {
			this.refCount = 1;
			this.parse = parse;
			this.score = score;
		}
		
		public ParseEntry(int refCount, int[] parse, double score) {
			this.refCount = refCount;
			this.parse = parse;
			this.score = score;
		}
		
	}
	
	RunConfig config;
	
	String grammarName;
	File cacheFile;
	
	private HashMap<TagSequence, ParseEntry> parses
		= new HashMap<TagSequence, ParseEntry>();
	private int unsavedParses = 0;
	private int requests = 0;
	private int hits = 0;
	private int[] hitsByLength = new int[11];
	
	Alphabet alphabet = new Alphabet();
	DMVGrammar grammar;
	DependencyParser parser;
	
	
	public ParseCache(RunConfig config) throws IOException {
		this.config = config;
		grammarName = config.getString("parser.grammar.name");
		cacheFile = config.getDataFile("parser.cache.file");
		String grammarFile = config.getDataFile("parser.grammar.file").toString();
		grammar = new DMVGrammar(alphabet);
		grammar.readGrammar(grammarFile, new SentenceCorpus(alphabet));
		parser = new DMVParserViterbi(grammar);
		
		if (cacheFile.exists()) {
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(new FileInputStream(cacheFile), "UTF-8"));
			while (reader.ready()) {
				String[] tags = reader.readLine().split(" ");
				for (int i = 0; i < tags.length; i++)
					tags[i] = tags[i].intern();
				TagSequence tagSequence = new TagSequence(tags);
				
				String[] parseTerms = reader.readLine().split(" ");
				int refCount = Integer.parseInt(parseTerms[0]);
				double score = Double.parseDouble(parseTerms[1]);
				int[] parse = new int[tags.length];
				for (int i = 0; i < parse.length; i++)
					parse[i] = Integer.parseInt(parseTerms[i+2]);
				ParseEntry parseEntry = new ParseEntry(refCount, parse, score);
				
				parses.put(tagSequence, parseEntry);
			}
			reader.close();
		}
	}

	/** tags must be intern()ed! */
	public double findParse(String[] tags, int[] parse) throws IOException {
		requests++;
		TagSequence tagSequence = new TagSequence(tags);
		ParseEntry entry = parses.get(tagSequence);
		if (entry == null) {
			entry = parse(tags);
			parses.put(tagSequence, entry);
			
			unsavedParses++;
			if (unsavedParses >= 100)
				save();
		}
		else {
			hits++;
			if (tags.length <= 10)
				hitsByLength[tags.length]++;
			entry.refCount++;
		}
		
		for (int i = 0; i < entry.parse.length; i++)
			parse[i] = entry.parse[i];
		return entry.score;
	}
	
	protected ParseEntry parse(String[] tags) {
		
		Chart.Semiring.setSemiringMax();
		grammar.cleanup();
		EisnerSattaChart chart = new EisnerSattaChart(grammar);
		EisnerSattaAlgorithm alg = new EisnerSattaAlgorithm(grammar, false);
		alg.setChart(chart);

		SentenceDocument sentence = new SentenceDocument(alphabet, tags);
		alg.assertSentence(sentence);

		while (alg.agendaIterator()) {
		}

		GoalTerm goalTerm = (GoalTerm) (chart.getTerm(GoalTerm.hashcode(0)));
		
		if (goalTerm == null) {
			System.err.println("Could not parse sentence: " + Arrays.toString(tags));
			return new ParseEntry(new int[tags.length], Double.NEGATIVE_INFINITY);
		} else {
			return new ParseEntry(alg.backtrack(), goalTerm.value());
		}
	}
	
	public void save() throws IOException {
		PrintWriter writer = new PrintWriter(cacheFile, "UTF-8");
		for (Entry<TagSequence, ParseEntry> entry: parses.entrySet()) {
			writer.println(Util.join(" ", entry.getKey().tags));
			
			ParseEntry parseEntry = entry.getValue();
			writer.print(parseEntry.refCount);
			writer.print(" " + parseEntry.score);
			for (int i = 0; i < parseEntry.parse.length; i++)
				writer.print(" " + parseEntry.parse[i]);
			writer.println();
		}
		writer.close();
		
		unsavedParses = 0;
	}
	
	public String summaryStats() {
		String summary = "\n";
		summary += "Requests: " + requests + "\n";
		summary += "Size: " + parses.size() + "\n";
		summary += String.format("Hits: %d (rate: %f)\n", hits, (double) hits / requests);
		for (int len = 1; len <= 10; len++)
			summary += String.format("Length %d hits: %d\n", len, hitsByLength[len]);
		return summary;
	}
}




