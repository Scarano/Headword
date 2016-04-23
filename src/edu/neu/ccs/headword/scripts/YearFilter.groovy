package edu.neu.ccs.headword.scripts

import java.io.File;

import edu.neu.ccs.headword.Clustering;
import edu.neu.ccs.headword.DMV;
import edu.neu.ccs.headword.DMVParserG;
import edu.neu.ccs.headword.LexDMV;
import edu.neu.ccs.headword.SimpleTokenizer;
import edu.neu.ccs.headword.TagDMV;
import edu.neu.ccs.headword.LatticeParser;
import edu.neu.ccs.headword.TaggedLattice;
import edu.neu.ccs.headword.LatticeParser.DMVScorer;
import edu.neu.ccs.headword.util.CommandLineParser;

class YearFilter {
	static main(args) {
		CommandLineParser clp = new CommandLineParser("", args)

		def metadataFile = new File(clp.arg(0))
		def textDir = new File(clp.arg(1))
		def minYear = clp.arg(2).toInteger()

		def allowedDocs = []
		metadataFile.eachLine("UTF-8") { line ->
			def (name, author, title, year) = line.split(/::/)
			
			if (year.toInteger() < minYear) {
//				println "Excluding $year / $name"
			}
			else {
				allowedDocs << name
			}
		}
		
		def size = 0
		allowedDocs.each { name ->
			def file = new File(textDir, name+".txt")
			size += file.length()
		}
		
		println size
	}
	
}






