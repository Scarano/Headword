package edu.neu.ccs.headword.scripts

import edu.neu.ccs.headword.Clustering


def (cache1Filename, cache2Filename, diffFilename) = args

def cache2Parses = [:]
def tagSequence = null
new File(cache2Filename).eachLine { line ->
	if (tagSequence == null) {
		tagSequence = line
	}
	else {
		cache2Parses[tagSequence] = line
		tagSequence = null
	}
}

def matches = 0
def differences = 0

new File(diffFilename).withWriter { out ->
	new File(cache1Filename).eachLine { line ->
		if (tagSequence == null) {
			tagSequence = line
		}
		else {
			def parse1 = line
			def parse2 = cache2Parses[tagSequence]
			if (parse2 != null) {
				matches++
				
				if (parse1.split(" ").size() < 3)
					println parse1
				if (parse2.split(" ").size() < 3)
					println parse2

				if (parse2.split(" ")[2..-1] != parse1.split(" ")[2..-1]) {
					differences++
					out.println tagSequence
					out.println parse1
					out.println tagSequence
					out.println parse2
				}
			}
			tagSequence = null
		}
	}
}

println "$differences / $matches"








