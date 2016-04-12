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

class ParseTranscription {
	static main(args) {
		CommandLineParser clp = new CommandLineParser(
			"-counts=s -tag-smoothing=f"
			+ " -lex-counts=s -lex-smoothing=f -lambda=f"
			+ " -clustering=s -min-length=i -max-length=i"
			+ " -words -tags -head-array -pretty -tex",
			args);

		def alignmentFile = new File(clp.arg(0))
		String countsFile = clp.opt("-counts", null);
		double tagAlpha = clp.opt("-tag-smoothing", 0.0);
		String lexCountsFile = clp.opt("-lex-counts", null);
		double lexAlpha = clp.opt("-lex-smoothing", 0.0);
		double lambda = clp.opt("-lambda", 0.0); // weight of lexical model
		String clusterFile = clp.opt("-clustering", null);
		int minLength = clp.opt("-min-length", 0);
		int maxLength = clp.opt("-max-length", 9999);
		boolean printWords = clp.opt("-words");
		boolean headArray = clp.opt("-head-array");
		boolean tags = clp.opt("-tags");
		boolean pretty = clp.opt("-pretty");
		boolean tex = clp.opt("-tex");
		
		def clustering = new Clustering(new File(clusterFile), 1e-7);

		DMV model = new TagDMV(countsFile, false, tagAlpha);
		
		if (lexCountsFile != null)
			model = new LexDMV(lexCountsFile, lexAlpha, model, lambda, 1e-7);
		
		def scorer = new DMVScorer(model);
		def parser = new LatticeParser(scorer)
		
		def totalArcs = 0
		def shortRightArcs = 0
		def shortLeftArcs = 0
		
		boolean transcription = false
		alignmentFile.eachLine("utf-8") { line ->
			// Skip OCR (every other line)
			transcription = !transcription
			if (!transcription)
				return
			
			def dehyph = SimpleTokenizer.dehyphenate(line)
			def words = SimpleTokenizer.tokenize(dehyph, false);

			if (words.length < minLength || words.length > maxLength)
				return
		
			TaggedLattice lattice = new TaggedLattice(words, clustering);
			int[] parse = parser.parse(lattice);
			
			totalArcs += words.length - 1;
			for (int a: 1..words.length) {
				int h = parse[a-1]
				if (h != 0) {
					if (h == a - 1) shortRightArcs++
					if (h == a + 1) shortLeftArcs++
				}
			}

			if (printWords)
				println words.join(" ")
			
			if (tags)
				println clustering.clusterSequence(words).join(" ")
			
			if (headArray)
				println parse*.toString().join(" ")
				
			if (pretty)
				DMVParserG.printParseTagged(System.out, lattice.edges, parse)
				
			if (tex)
				println texParse(words, clustering.clusterSequence(words), parse)
			
			if (printWords || tags || headArray || pretty || tex)
				println()
		}
		
		printf("Right-branching: %f\nLeft-branching: %f\n",
			(double) shortRightArcs / totalArcs, (double) shortLeftArcs / totalArcs)
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






