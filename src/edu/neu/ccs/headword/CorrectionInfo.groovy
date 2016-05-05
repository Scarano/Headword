package edu.neu.ccs.headword

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.neu.ccs.headword.util.CommandLineParser

class CorrectionInfo {
	
	static main(args) {
		def clp = new CommandLineParser("-verbose", args)
		def verbose = clp.opt("-verbose")
		def filename = clp.arg(0)
		
		def totalTokens = 0D
		def totalOCRErrors = 0D
		def totalCorrErrors= 0D
		
		def file = new CorrectionFile(new File(filename))

		WordAligner wordAligner = new WordAligner(true)
		
		[file.transSentences, file.ocrSentences, file.corrSentences].transpose().each {
			trans, ocr, corr ->
			
			String dehyphTrans = SimpleTokenizer.hyphenNormalized(trans)
			String[] dehyphWords = SimpleTokenizer.tokenize(dehyphTrans, false)
			
			String dehyphOCR = SimpleTokenizer.hyphenNormalized(ocr)
			String dehyphCorr = SimpleTokenizer.hyphenNormalized(corr)
			
			def ocrErrorCount = wordAligner.alignmentCost(
				dehyphWords, SimpleTokenizer.tokenize(dehyphOCR, false))
			def corrErrorCount = wordAligner.alignmentCost(
				dehyphWords, SimpleTokenizer.tokenize(dehyphCorr, false))
			
			if (verbose) {
				println(trans)
				printf("%d: %s\n", (int) ocrErrorCount, ocr)
				printf("%d: %s\n", (int) corrErrorCount, corr)
				println(corrErrorCount - ocrErrorCount)
				println()
			}
			
			totalTokens += dehyphWords.length
			totalOCRErrors += ocrErrorCount
			totalCorrErrors += corrErrorCount
		}

		printf("Word count: %d\n", (int) totalTokens)
		printf("OCR WER (cheat): %f\n", totalOCRErrors / totalTokens) 
		printf("Corrected WER (cheat): %f\n", totalCorrErrors / totalTokens)
		printf("WER improvement (cheat): %f\n", (totalOCRErrors - totalCorrErrors) / totalOCRErrors)
	}
}







