package ocr

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ocr.util.CommandLineParser;
import edu.cmu.cs.lti.ark.dageem.Alphabet;
import edu.cmu.cs.lti.ark.dageem.DMVGrammar;
import edu.cmu.cs.lti.ark.dageem.DMVParserViterbi;
import edu.cmu.cs.lti.ark.dageem.DependencyParser;
import edu.cmu.cs.lti.ark.dageem.SentenceDocument;

class ParsedCorpus {
	
	static class ParsedSentence {
		String[] text
		String[] tags
		int[] parse
		
		public ParsedSentence(String[] text, String[] tags, int[] parse) {
			this.text = text
			this.tags = tags
			this.parse = parse
		}
	}
	
	List<ParsedSentence> sentences
	boolean oneBased
	
	ParsedCorpus(File file, boolean oneBased=false) {
		this.oneBased = oneBased
		sentences = new ArrayList<ParsedSentence>()
		file.withReader("utf-8") { reader ->
			while (reader.ready()) {
				def text = reader.readLine().split(" ")
				def tags = reader.readLine().split(" ")
				def parse = reader.readLine().split(" ")*.toInteger()
				// automatically convert file if using 0-based index
				if (oneBased && -1 in parse) parse = parse.collect { it + 1 }
				sentences << new ParsedSentence(text, tags, parse as int[])
			}
		}
	}
	
	ParsedCorpus(boolean oneBased=false) {
		this.oneBased = oneBased
		sentences = []
	}
	
	/**
	 * Caution - current implementation does not obey oneBased setting
	 */
	def write(File file) {
		file.withWriter { out ->
			sentences.each { sentence ->
				out.println(sentence.text.join(' '))
				out.println(sentence.tags.join(' '))
				out.println(sentence.parse*.toString().join(' '))
			}
		}
	}
	
	static main(args) {
	}

}





