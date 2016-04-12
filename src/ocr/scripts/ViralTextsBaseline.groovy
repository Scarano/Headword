package ocr.scripts

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser

import ocr.SimpleTokenizer;
import ocr.WordAligner
import ocr.util.CommandLineParser

class ViralTextsBaseline {

	static main(args) {
		def clp = new CommandLineParser("-minprop=f -maxprop=f", args)
		def inputFile = new File(clp.arg(0))
		def outputFile = new File(clp.arg(1))
		def minProp = clp.opt("-minprop", 0D)
		def maxProp = clp.opt("-maxprop", 1D)

		def items = []
		inputFile.withReader { input ->
			def csv = new CSVParser(input, 
				CSVFormat.EXCEL.withHeader())
			csv.each { record ->
				def item = [:]
				['f', 'score', 'prop1', 'ID', 'Cluster Text', 'Reference Text'].each { k ->
					item[k] = record.get(k)
				}
				items << item
			}
		}
		
		items = items.findAll {
//			it.ID == 'nwen_nwen0008-6' &&
			it.prop1.toDouble() > minProp && it.prop1.toDouble() <= maxProp
		}
		
//		colNames << 'WER'

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
				
				def ocrText = item['Cluster Text'].replaceAll(/\r/, ' ').replaceAll(/[^\x00-\xff]/, '_')
				def refText = item['Reference Text'].replaceAll(/\r/, ' ').replaceAll(/[^\x00-\xff]/, '_')
				def ocrWords = SimpleTokenizer.tokenize(ocrText, false)
				def refWords = SimpleTokenizer.tokenize(refText, false)
				def docErrors = wordAligner.alignmentCost(refWords, ocrWords)

				tokens += refWords.length				
				errors += docErrors
				double wer = ((double) docErrors)/refWords.length
				werByDoc << wer
				
				item['WER'] = wer
				
				printf("%s: prop1 = %s; WER = %f\n", item['ID'], item['prop1'], wer)
				
				['f', 'score', 'prop1', 'ID', 'Cluster Text', 'Reference Text', 'WER'].each { k ->
					out.println "$k: ${item[k]}"
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
