package edu.neu.ccs.headword.scripts

import edu.neu.ccs.headword.Alignment;
import edu.neu.ccs.headword.SentenceAlignment
import edu.neu.ccs.headword.SimpleTokenizer;

def files = args.collect { new File(it) }

def totalTokens = 0
def totalLines = 0

files.each { File file ->
	def tokens = 0
	def lines = 0

	def lineStrings
	if (file.getName().endsWith('.salign')) {
		def alignment = new SentenceAlignment(file, 1, 999)
		lineStrings = alignment.transcriptionSentences
	}
	else {
		def alignment = new Alignment(file.getPath())
		lineStrings = alignment.lines()*.input
	}
	
	lineStrings.each { line ->
		tokens += SimpleTokenizer.tokenize(line, false).length
		lines += 1
	}
	
	totalTokens += tokens
	totalLines += lines
	
	println "$lines,$tokens,${file.getName()}"
}

println "$totalLines,$totalTokens,total"

