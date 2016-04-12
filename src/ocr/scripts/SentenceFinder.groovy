package ocr.scripts

import ocr.SentenceAlignment;
import ocr.SimpleTokenizer;
import groovy.transform.CompileStatic;
//import groovyx.gprof.Profiler;

//@CompileStatic
class SentenceFinder {

	static void main(String[] args) {
//		if (args[0].equals("--profile")) {
//			def prof = new Profiler()
//			prof.start()
//			main([args[1], args[2]] as String[])
//			prof.stop()
//			prof.report.prettyPrint()
//			System.exit(0)
//		}
		
		String tokenFilename = args[0]
		String sentenceFilename = args[1]
		boolean dehyphenate = args.length < 3 ? false : (args[2] == "-dehyphenate")
		
		File tokenFile = new File(tokenFilename)
		File sentenceFile = new File(sentenceFilename)
		
		List sentences = []
		List sentence = []
		
		tokenFile.eachLine("UTF-8") { String line ->
			line.split(/\s+/).each { String token ->
				sentence.add(token)
				if (SentenceAlignment.probablyEndsSentence(token)) {
					sentences.add(sentence)
					sentence = []
				}
			}
		}
		if (sentence.size() > 0)
			sentences.add(sentence)
			
		Map<Integer, Integer> sentenceSizeCounts = [:]
		sentences.collect { List s -> s.size() }.each { int size ->
			sentenceSizeCounts.put(size, (sentenceSizeCounts[size] ?: 0) + 1)
		}
		sentenceSizeCounts.sort { Map.Entry<Integer, Integer> e -> e.key }
//		sentenceSizeCounts.each { Map.Entry<Integer, Integer> e ->
//			println "$e.key: $e.value"
//		}
		
		sentenceFile.withWriter("UTF-8") { BufferedWriter out -> 
			sentences.each { List s ->
				if (dehyphenate as boolean)
					out.writeLine(SimpleTokenizer.dehyphenate(s.join(" ")))
				else
					out.writeLine(s.join(" "))
			}
		}
	}
	
}





