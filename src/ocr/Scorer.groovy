package ocr

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.codehaus.groovy.control.ErrorCollector;

import ocr.LatticeParser.DMVScorer
import ocr.LatticeParser.DMVGrammarScorer
import ocr.util.CommandLineParser

class Scorer {
	
	def ocrSentences = []
	def transSentences = []
	def corrSentences = []
	
	Scorer(File corrFile) {
		def lines = []
		corrFile.eachLine("utf-8") { lines << it }
		lines.collate(4).each { group ->
			transSentences << group[0]
			ocrSentences << group[1]
			corrSentences << group[2]
		}
	}
	
	def score() {
		WordAligner wordAligner = new WordAligner(true)

		def ocrTokens = 0
		def ocrErrors = 0
		
		[ocrSentences, transSentences, corrSentences].transpose().each { ocr, trans, corr ->
			def transWords = SimpleTokenizer.tokenize(trans, false)
			def sentOcrErrors = wordAligner.alignmentCost(
					transWords, SimpleTokenizer.tokenize(ocr, false));

			ocrTokens += transWords.length
			ocrErrors += sentOcrErrors
				
			String dehyphenatedTrans = SimpleTokenizer.dehyphenate(trans);
			String[] dehyphWords = SimpleTokenizer.tokenize(dehyphenatedTrans, false);
			def errors = wordAligner.alignmentCost(
					dehyphWords, SimpleTokenizer.tokenize(corr, false));
			
			println trans
			println ocr
			printf("OCR errors: %d\n", sentOcrErrors);
//			println dehyphenatedTrans
//			println corr
			println()
		}

		printf("OCR error rate: %f", (double) ocrErrors / ocrTokens)
	}
	
	static main(args) {
		def clp = new CommandLineParser("", args)
		def corrFile = new File(clp.arg(0))
		
		new Scorer(corrFile).score()
	}
	
}







