package edu.neu.ccs.headword.scripts

import edu.neu.ccs.headword.util.CommandLineParser

class RevisedScores {
	static main(args) {
		def clp = new CommandLineParser("-parallel=i")
		def parallel = clp.opt("-parallel", 1)
		
	}
}