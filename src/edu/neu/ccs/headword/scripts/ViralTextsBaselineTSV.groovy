package edu.neu.ccs.headword.scripts

/* BUGGY! (because data contains non-delimiting tab characters) */

import edu.neu.ccs.headword.SimpleTokenizer;
import edu.neu.ccs.headword.WordAligner
import edu.neu.ccs.headword.util.CommandLineParser

class ViralTextsBaselineTSV {

	static main(args) {
		def clp = new CommandLineParser("-minprop=f -maxprop=f", args)
		def inputFile = new File(clp.arg(0))
		def outputFile = new File(clp.arg(1))
		def minProp = clp.opt("-minprop", 0D)
		def maxProp = clp.opt("-maxprop", 1D)
		
		def input = inputFile.getText().split(/\n/)
		
		def colNames = input[0].split(/\t/)*.replaceAll(/\s/, '')
		def itemStrings = input[1..-1]
		def items = []
		itemStrings.each { itemStr ->
			def item = [:]
			[colNames, itemStr.split(/\t/)].transpose().each { k, v -> item[k] = v }
			items << item
		}
		
		items = items.findAll {
//			it.ID == 'nwen_nwen0008-6' &&
			it.prop1.toDouble() > minProp && it.prop1.toDouble() <= maxProp
		}
		
		colNames << 'WER'

		WordAligner wordAligner = new WordAligner(true)
		
		int tokens = 0
		int errors = 0
		List<Double> werByDoc = []

		outputFile.withWriter { out ->
			items.each { item ->
//				def cLines = item.ClusterText.split(/\r/).length
//				def rLines = item.ReferenceText.split(/\r/).length
//				if (cLines != rLines)
//					println "$cLines != $rLines"
//		
				
				def ocrText = item.ClusterText.replaceAll(/\r/, ' ').replaceAll(/['"]/, "\ufffd")
				def refText = item.ReferenceText.replaceAll(/\r/, ' ').replaceAll(/['"]/, "\ufffd")
				def ocrWords = SimpleTokenizer.tokenize(ocrText, false)
				def refWords = SimpleTokenizer.tokenize(refText, false)
				def docErrors = wordAligner.alignmentCost(refWords, ocrWords)

				tokens += refWords.length				
				errors += docErrors
				double wer = ((double) docErrors)/refWords.length
				werByDoc << wer
				
				item['WER'] = wer
				
				printf("%s: prop1 = %s; WER = %f\n", item['ID'], item['prop1'], wer)
				
				colNames.each { col ->
					out.println "$col: ${item[col]}"
				}
				out.println()
			}
			
			def mean = werByDoc.inject (0D) { a, b -> a + b } / werByDoc.size()
			def stddev = 0D
			werByDoc.each { stddev += (it - mean) * (it - mean) }
			stddev = Math.sqrt(stddev/werByDoc.size())
		
			printf("docs: %d; words: %d; errors: %d; WER: %f; mean: %f; stddev: %f\n", 
				items.size(), tokens, errors, ((double) errors)/tokens, mean, stddev)
		}
	}
	
	
}
