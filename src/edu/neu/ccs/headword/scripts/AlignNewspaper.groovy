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

class AlignNewspaper {
	static main(args) {
		CommandLineParser clp = new CommandLineParser("", args)

		def inputFile = new File(clp.arg(0))
		def outputFile = new File(clp.arg(1))
		
		def regionInProgress = []
		def regions = []
		inputFile.eachLine("UTF-8") { line ->
			line = line.trim()
			if (line == "") {
				regions << regionInProgress
				regionInProgress = []
			}
			else {
				regionInProgress << line
			}
		}
		if (regionInProgress.size() > 0)
			regions << regionInProgress
			
		// Filter out regions that appear to erroneously span 2 columns:
		regions = regions.findAll { List<String> region ->
			average(region.collect { (double) it.length() }) < 65
		}
		
		regions.each { List<String> region ->
			
		}
			
		outputFile.withWriter("UTF-8") { out ->
			regions.each { List<String> region ->
				out << "[ " << region.join("\n") << " ]\n\n"
			}
		}
		
		
	}
	
	static double average(List<Double> list) {
		list.inject { a, b -> a + b } / list.size()
	}
}






