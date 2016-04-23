package edu.neu.ccs.headword.scripts

def wordsFile = new File(args[0])
def latticeFile = new File(args[1])

wordsFile.eachLine('utf-8') { line ->
	line.split(/\s/).each { word ->
		latticeFile.withWriter('utf-8') { out ->
			out << "name test\n"
			out << "nodes 1 $word\n"
			out << "initial 0\n"
			out << "final 0\n"
			out << "transitions 0\n"
		}
		println word
		"""/Users/sam/src/srilm/bin/macosx/lattice-tool
			-in-lattice $latticeFile -out-lattice temp.out 
			-lm /Users/sam/thesis-data/wwp/prose2.1to30.lm.5kni-unk5.txt -order 3 -unk
		""".execute().waitForProcessOutput(System.out, System.err)
	}
}
