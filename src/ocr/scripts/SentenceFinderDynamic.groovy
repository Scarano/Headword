package ocr.scripts

import groovyx.gprof.Profiler;

class SentenceFinderDynamic {

	def static nonSentenceEndingTokens;
	
	static {
		nonSentenceEndingTokens = """
			Mr Mrs Ms Mssr Mssrs Patr Matr Dr Rev Capt Col Gen
			St Ave
			etc &c
			no No
		""".split(/\s+/) as Set
		nonSentenceEndingTokens += nonSentenceEndingTokens.collect() { it.toUpperCase() }
	}

	static main(args) {
		if (args[0].equals("--profile")) {
			def prof = new Profiler()
			prof.start()
			main([args[1], args[2]])
			prof.stop()
			prof.report.prettyPrint()
			System.exit(0)
		}

		def (tokenFilename, sentenceFilename) = args
		
		def tokenFile = new File(tokenFilename)
		def sentenceFile = new File(sentenceFilename)
		
		def sentences = []
		def sentence = []
		
		tokenFile.eachLine("UTF-8") { line ->
			line.split(/\s+/).each {
				sentence.add(it)
				if (endsSentence(it)) {
					sentences.add(sentence)
					sentence = []
				}
			}
		}
		if (sentence.size() > 0)
			sentences.add(sentence)
		
		def sentenceSizeCounts = [:]
		sentences.collect { it.size() }.each {
			sentenceSizeCounts[it] = (sentenceSizeCounts[it] ?: 0) + 1
		}
//		sentenceSizeCounts.sort { it.key }.each { size, count ->
//			println "$size: $count"
//		}
		
		sentenceFile.withWriter("UTF-8") { out -> 
			sentences.each {
				out.writeLine(it.join(" "))
			}
		}
	}
	
	static boolean endsSentence(String token) {
		if (
			token.endsWith(".") || token.endsWith("?") || token.endsWith("!") ||
			token.endsWith(".\"") || token.endsWith(".'") || token.endsWith(".)") ||
			token.endsWith(";") // XXX good idea? bad idea?
		) {
			String word = (token =~ /[\.\?\!;].*/).replaceFirst("")
			if (word.length() == 1 || word in nonSentenceEndingTokens) {
				return false
			}
			return true
		}
		return false
	}
}





