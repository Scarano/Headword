package ocr.scripts

import java.io.File;

import ocr.Clustering;
import ocr.DMV;
import ocr.DMVParserG;
import ocr.LexDMV;
import ocr.SimpleTokenizer;
import ocr.TagDMV;
import ocr.LatticeParser;
import ocr.TaggedLattice;
import ocr.LatticeParser.DMVScorer;
import ocr.util.CommandLineParser;

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






