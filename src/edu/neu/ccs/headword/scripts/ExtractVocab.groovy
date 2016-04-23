package edu.neu.ccs.headword.scripts

import edu.neu.ccs.headword.util.CommandLineParser;

class ExtractVocab {

	static main(args) {
		def clp = new CommandLineParser("-add-root-symbol", args)
		def input = new File(clp.arg(0))
		def output = new File(clp.arg(1))
		def addRoot = clp.opt("-add-root-symbol")

		def vocab = [] as Set
		
		input.eachLine("utf-8") { line ->
			line.split(/\s+/).each { vocab << it }
		}
		output.withWriter("utf-8") { out ->
			if (addRoot)
				out.println 'ROOT'
			vocab.sort().each { out.println it }
		}
	}

}
