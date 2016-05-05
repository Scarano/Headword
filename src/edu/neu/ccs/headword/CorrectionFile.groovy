package edu.neu.ccs.headword

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.neu.ccs.headword.util.CommandLineParser
import edu.neu.ccs.headword.util.Util;

class CorrectionFile {
	
	def ocrSentences = []
	def transSentences = []
	def corrSentences = []
	
	CorrectionFile(File corrFile) {
		def lines = []
		corrFile.eachLine("utf-8") { lines << it }
		lines.collate(4).each { group ->
			transSentences << group[0]
			ocrSentences << group[1]
			corrSentences << group[2]
		}
	}
	
	def errorsBySentence() {
		WordAligner wordAligner = new WordAligner(true)

		def errorCounts = []
		
		[transSentences, corrSentences].transpose().each { trans, corr ->
			String dehyphenatedTrans = SimpleTokenizer.dehyphenate(trans);
			String[] dehyphWords = SimpleTokenizer.tokenize(dehyphenatedTrans, false);
			def errors = wordAligner.alignmentCost(
					dehyphWords, SimpleTokenizer.tokenize(corr, false));
			errorCounts << errors
			
//			println dehyphenatedTrans
//			println dehyphenatedTrans.contains("\u00ad")
//			println corr
//			println errors
//			println()
		}

		errorCounts
	}
	
	/**
	 * Returns [wins, losses], where:
	 * 		wins = number of times errors2[i] < errors1[i]
	 * 		losses = number of times errors2[i] > errors1[i]
	 */
	def static errorComparison(errors1, errors2) {
		def wins = 0
		def losses = 0
		[errors1, errors2].transpose().each { c1, c2 ->
			if (c1 > c2) wins ++
			else if (c2 > c1) losses++
		}
		return [wins, losses]
	}
	
	static main(args) {
		try {
			def w = args[0].toInteger()
			def l = args[1].toInteger()
//			println("combin = " + Util.combination((double) w, (double) l))
			println(signTest(w,l))
			return
		}
		catch (NumberFormatException e) {}
		
		def clp = new CommandLineParser("", args)
		def files = clp.args()
		def baselines = files[0..<files.size()/2]
		def tests = files[files.size()/2..-1]
		
		def totalBaseErrors = 0
		def totalTestErrors = 0
		def totalWins = 0
		def totalLosses = 0
		
		[baselines, tests].transpose().each { baseline, test ->
			println "Testing $test"
		
			def baseErrors = new CorrectionFile(new File(baseline)).errorsBySentence()
			def testErrors = new CorrectionFile(new File(test)).errorsBySentence()
			
			def baseErrorCount = baseErrors.inject(0) { a, b -> a + b }
			def testErrorCount = testErrors.inject(0) { a, b -> a + b }
			totalBaseErrors += baseErrorCount
			totalTestErrors += testErrorCount
		
			printf("Baseline errors: %f; test errors: %f\n", baseErrorCount, testErrorCount)
			
			def (wins, losses) = errorComparison(baseErrors, testErrors)
			totalWins += wins
			totalLosses += losses
			
			printf("Wins: %d; losses: %d\n", wins, losses);
			
			printf("p = %f\n\n", signTest(wins, losses))
		}

		printf("Total baseline errors: %f; total test errors: %f\n",
			totalBaseErrors, totalTestErrors)
		printf("Total wins: %d; total losses: %d\n", totalWins, totalLosses);
		printf("p = %f\n", signTest(totalWins, totalLosses))
		
	}
	
	static signTest(wins, losses) {
		double n = wins + losses
		double p = 0
		for (double i = 0; i < wins; i++) {
			p += Util.combination(n, i)
//			println p
		}
//		println new BigDecimal(2).pow(n)
		2*(1 - p/Math.pow(2, n))
	}
}







