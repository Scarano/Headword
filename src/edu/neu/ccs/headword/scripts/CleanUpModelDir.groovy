package edu.neu.ccs.headword.scripts

import java.text.SimpleDateFormat;

import edu.neu.ccs.headword.util.CommandLineParser

class CleanUpModelDir {

	static main(args) {
		def clp = new CommandLineParser("-dry", args)
		def dry = clp.opt("-dry")
		def dir = new File(clp.arg(0))
		
		['dmv', 'cnt', 'lcnt', 'gradient'].each { suffix ->
			def maxIter = null
			dir.eachFile { file ->
				(file.getName() =~ /\.(\d+)\.$suffix/).each { m ->
					def iter = m[1].toInteger()
					if (maxIter == null || iter > maxIter)
						maxIter = iter
				}
			}
			if (maxIter != null) {
				println "Keeping ...${maxIter}.$suffix"
				dir.eachFile { file ->
					(file.getName() =~ /\.(\d+)\.$suffix/).each { m ->
						def iter = m[1].toInteger()
						if (iter < maxIter) {
							if (dry)
								println "Would delete $file"
							else
								file.delete()
						}
					}
				}
			}
		}
	}
}






