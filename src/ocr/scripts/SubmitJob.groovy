package ocr.scripts

import java.text.SimpleDateFormat;

import ocr.util.CommandLineParser

class SubmitJob {

	static main(args) {
		def clp = new CommandLineParser("-bsub -dry -parallel=i -overrides=s", args)
		def bsub = clp.opt("-bsub")
		def dry = clp.opt("-dry")
		def parallel = clp.opt("-parallel", 1)
		def config = new File(clp.arg(0))
		def outputDir = new File(clp.arg(1))
		
		def overrides = []
		def overridesStr = clp.opt("-overrides", null)
		if (overridesStr != null) {
			overridesStr = overridesStr.replaceAll(/_/, ' ')
			println "overrides: $overridesStr"
			overridesStr.split(/,/).each {
				overrides << it.split(/[=:]/)*.trim() as List
			}
		}
		
		def java = System.getenv('JAVA') ?: '/scratch/scarano.s/bin/runjava.sh'
		
		def processes = (int) (1 + parallel)
		
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
			"$java ocr.Evaluator " +
				"${newConfig.getAbsolutePath()} $parallel"
		if (bsub)
			command = "bsub -R $processes*{mem>60000} -n $processes -q ser-par-10g " +
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
