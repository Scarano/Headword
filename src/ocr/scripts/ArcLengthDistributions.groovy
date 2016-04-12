package ocr.scripts

import java.io.File;

import ocr.Clustering;
import ocr.DMV;
import ocr.DMVParserG;
import ocr.LexDMV;
import ocr.SimpleTokenizer;
import ocr.TagDMV;
import ocr.LatticeParser;
import ocr.TaggedLattice;
import ocr.LatticeParser.DMVScorer;
import ocr.util.CommandLineParser;

class ArcLengthDistributions {
	static main(args) {
		CommandLineParser clp = new CommandLineParser(
			"-parses=s -text=s -counts=s -tag-smoothing=f"
			+ " -lex-counts=s -lex-smoothing=f -lambda=f"
			+ " -clustering=s -min-length=i -max-length=i"
			+ " -words -tags -head-array -pretty -tex",
			args);

		String parsesFile = clp.opt("-parses", null)
		String textFile = clp.opt("-text", null)
		File inputFile = new File((parsesFile ?: textFile) as String)
		String countsFile = clp.opt("-counts", null);
		double tagAlpha = clp.opt("-tag-smoothing", 0.0);
		String lexCountsFile = clp.opt("-lex-counts", null);
		double lexAlpha = clp.opt("-lex-smoothing", -1.0);
		double lambda = clp.opt("-lambda", 0.0); // weight of lexical model
		String clusterFile = clp.opt("-clustering", null);
		int minLength = clp.opt("-min-length", 0);
		int maxLength = clp.opt("-max-length", 9999);
		boolean printWords = clp.opt("-words");
		boolean headArray = clp.opt("-head-array");
		boolean tags = clp.opt("-tags");
		boolean pretty = clp.opt("-pretty");
		boolean tex = clp.opt("-tex");
		
		Clustering clustering = clusterFile == null ? null : new Clustering(new File(clusterFile), 1e-7)

		DMV model = new TagDMV(countsFile, false, tagAlpha);
		
		if (lexCountsFile != null)
			model = new LexDMV(lexCountsFile, lexAlpha, model, lambda, 1e-7);
		
		def scorer = new DMVScorer(model);
		def parser = new LatticeParser(scorer)

		def goldLengthCounts = new double[maxLength * 2 + 1]
		def lengthCounts = new double[maxLength * 2 + 1]		
		
		def sentences = []
		def parses = []
		
		inputFile.withReader("utf-8") { input ->
			for (;;) {
				def text = input.readLine()
				if (text == null)
					break
				def tagSeq = null
				def goldParse = null
				if (parsesFile != null) {
					tagSeq = input.readLine()
					goldParse = input.readLine().split(/ /)*.toInteger().collect { it + 1 } as int[]
				}
					
				def sentence
				if (clustering == null)
					sentence = tagSeq.split(/ /)
				else
					sentence = SimpleTokenizer.tokenize(text, false);
				
				if (sentence.length < minLength || sentence.length > maxLength)
					continue

				TaggedLattice lattice = clustering == null \
					? new TaggedLattice(sentence)
					: new TaggedLattice(sentence, clustering)
				int[] parse = parser.parse(lattice);

				if (goldParse != null)
					addArcLengthCounts(goldLengthCounts, maxLength, goldParse)
				addArcLengthCounts(lengthCounts, maxLength, parse)
		
				if (printWords)
					println sentence.join(" ")
				
				if (tags && clustering != null)
					println clustering.clusterSequence(sentence).join(" ")
				
				if (headArray) {
					if (goldParse != null)
						println goldParse*.toString().join(" ")
					println parse*.toString().join(" ")
				}
					
				if (pretty) {
					if (goldParse != null)
						DMVParserG.printParseTagged(System.out, lattice.edges, goldParse)
					DMVParserG.printParseTagged(System.out, lattice.edges, parse)
				}
					
//				if (tex)
//					println texParse(sentence, clustering.clusterSequence(words), parse)
				
				if (printWords || tags || headArray || pretty || tex)
					println()
			}
		}
		
		printf("Gold:\n%s\n\nPredicted:\n%s\n", 
			goldLengthCounts*.toString().join(','), lengthCounts*.toString().join(','))
	}
	
	static void addArcLengthCounts(double[] lengthCounts, int maxLength, int[] parse) {
		for (int a: 0..<parse.length) {
			int h = parse[a] - 1
			if (h != -1)
				lengthCounts[maxLength + a - h]++
		}
	}
	
	static String texParse(String[] words, String[] tags, int[] parse) {
		words = words.collect {texLongS(it)}
		tags = tags.collect {texLongS(it)}
		
		def result = "\\begin{dependency}[theme = simple]\n"
		result += "  \\begin{deptext}[column sep=1em]\n"
		result += "    " + tags.collect { "\\textsc{$it}" }.join(" \\& ") + " \\\n"
		result += "    " + words.join(" \\& ") + " \\\n"
		
		result += "  \\end{deptext}\n"
		result += "\\end{depenedency}\n"
		
		return result
	}
	
	static texLongS(String s) {
		s.replaceAll('\u017f', '\\longs ')
	}
}






