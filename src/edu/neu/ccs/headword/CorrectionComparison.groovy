package edu.neu.ccs.headword

import groovy.transform.TupleConstructor;

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.math.stat.inference.TTestImpl

import edu.neu.ccs.headword.util.CommandLineParser
import edu.neu.ccs.headword.util.Util

class CorrectionComparison {
	
	@TupleConstructor
	static class CorrectionPair {
		String trans
		String ocr
		String baseCorr
		String testCorr
	}
	
	List<CorrectionPair> goodLines = []
	List<CorrectionPair> badLines = []
	
	List<Double> pairedErrors = [[], []]
	
	def add(File baselineFile, File testFile) {
		
		baselineFile.withReader("utf-8") { baseReader ->
			testFile.withReader("utf-8") { testReader ->
				while (baseReader.ready()) {
					def trans = baseReader.readLine()
					if (testReader.readLine() != trans)
						throw new Exception("Transcription in $testFile does not match " +
							"baseline sentence '$trans'");
					def ocr = baseReader.readLine()
					if (testReader.readLine() != ocr)
						throw new Exception("OCR in $testFile does not match " +
							"baseline sentence '$ocr'");
					def baseCorr = baseReader.readLine();
					def testCorr = testReader.readLine();
					baseReader.readLine();
					testReader.readLine();
					
					def baseErrors = errorCount(trans, baseCorr)
					def testErrors = errorCount(trans, testCorr)
//	println trans
//	printf("%f %f\n\n", baseErrors, testErrors)
					if (baseErrors > testErrors)
						badLines << new CorrectionPair(trans, ocr, baseCorr, testCorr)
					else if (testErrors > baseErrors)
						goodLines << new CorrectionPair(trans, ocr, baseCorr, testCorr)
						
					pairedErrors[0] << baseErrors
					pairedErrors[1] << testErrors
				}
			}
		}
	}
	
	static double errorCount(String transSentence, String corrSentence) {
		WordAligner wordAligner = new WordAligner(true)
		String dehyphenatedTrans = SimpleTokenizer.dehyphenate(transSentence);
		String[] dehyphWords = SimpleTokenizer.tokenize(dehyphenatedTrans, false);
		return wordAligner.alignmentCost(
				dehyphWords, SimpleTokenizer.tokenize(corrSentence, false));
	}
		
	static main(args) {
		def clp = new CommandLineParser("", args)
		def files = clp.args()
		def baselines = files[0..<files.size()/2]
		def tests = files[files.size()/2..-1]

		def comparison = new CorrectionComparison()
				
		[baselines, tests].transpose().each { baseline, test ->
			println "Reading $test"
		
			comparison.add(new File(baseline), new File(test))
		}

		def totalTokens = new double[2]
		def totalSentences = new double[2]
		def totalOCRErrors = new double[2]

		[0, 1].each { int i ->
			[comparison.badLines, comparison.goodLines][i].each { CorrectionPair pair ->
				totalSentences[i] += 1D
				totalTokens[i] += SimpleTokenizer.tokenize(pair.trans, false).length
				totalOCRErrors[i] += errorCount(pair.trans, pair.ocr)
			}
		}
		
		[0, 1].each { int i ->
			println(["Bad sentences:", "Good sentences:"][i])
			println("Sentences: ${totalSentences[i]}")
			printf("Mean length: %f\n", totalTokens[i] / totalSentences[i])
			printf("OCR WER: %f\n", totalOCRErrors[i] / totalTokens[i])
			println()
		}
		
		println("Two-tailed paired t-test:")
		println("p < " + (new TTestImpl()).pairedTTest(comparison.pairedErrors[0] as double[],
		                                               comparison.pairedErrors[1] as double[]))
		println()
	}
	
}







