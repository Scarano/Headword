package edu.neu.ccs.headword.scripts

import java.text.SimpleDateFormat;

import edu.neu.ccs.headword.util.CommandLineParser

class SubmitJob {

	static main(args) {
		def clp = new CommandLineParser("-bsub -dry -parallel=i -queue=s -overrides=s", args)
		def bsub = clp.opt("-bsub")
		def dry = clp.opt("-dry")
		def parallel = clp.opt("-parallel", 1)
		def queue = clp.opt("-queue", "ser-par-10g")
		def config = new File(clp.arg(0))
		def outputDir = new File(clp.arg(1))
		
		def overrides = []
		String overridesStr = clp.opt("-overrides", null)
		if (overridesStr != null) {
			overridesStr = overridesStr.replaceAll(/_/, ' ')
			println "overrides: $overridesStr"
			overridesStr.split(/,/).each {
				overrides << it.split(/[=:]/)*.trim() as List
			}
		}
		
		def java = System.getenv('JAVA') ?: '/scratch/scarano/bin/runjava.sh'
		
		def processes = (int) parallel
		
		def timeStr = new SimpleDateFormat("yyyy-MM-dd-HHmmss").format(new Date())
		def newConfig = new File(outputDir, timeStr + "-config.txt")
		
		newConfig.withWriter { out ->
			config.eachLine { out.println it }
			
			out.println()
			overrides.each { k, v ->
				out.println "$k: $v"
			}
			
			out.println()
			out.println "output-root: " + outputDir.getAbsolutePath();
			out.println "group-date: " + timeStr;
		}

		def command =
			"$java edu.neu.ccs.headword.Evaluator " +
				"${newConfig.getAbsolutePath()} $parallel"
		if (bsub)
			// -R $processes*{mem>60000}
			command = "bsub -R \"span[ptile=$processes]\" -n $processes -q $queue " +
				"-J eval-$timeStr " +
				"-o ${outputDir.getAbsolutePath()}/${timeStr}.out " + command

		if (dry) {
			println command
		}
		else {
			command.execute().waitForProcessOutput(System.out, System.err)
		}
	}

	
}
