package edu.neu.ccs.headword.util

class CommandLineParser {
	
	def options = [:]
	def args = []
	
	CommandLineParser(String optionDefs, String[] args) {
		def optionTypes = [:]
		optionDefs.split(/\s+/).each { optionDef ->
			if (optionDef.contains("=")) {
				def (name, type) = optionDef.split("=")
				optionTypes[name] = type
			}
			else {
				optionTypes[optionDef] = "b"
			}
		}
		def expectingValueFor = null
		args.each { arg ->
			if (expectingValueFor != null) {
				switch (optionTypes[expectingValueFor]) {
				case "s":
					options[expectingValueFor] = arg; break
				case "i":
					options[expectingValueFor] = arg.toInteger(); break
				case "f":
					options[expectingValueFor] = arg.toDouble(); break
				}
				expectingValueFor = null
			}
			else if (arg.startsWith("-")) {
				if (!optionTypes.containsKey(arg))
					throw new IOException("Unrecognized option: " + arg)
				if (optionTypes[arg] == "b")
					options[arg] = true
				else
					expectingValueFor = arg
			}
			else {
				this.args <<= arg
			}
		}
	}
	
	String[] args() { args }
	
	String arg(int i) {
		if (i < args.size())
			args[i]
		else
			null
	}
	
	String arg(int i, String defaultVal) {
		if (i < args.size())
			args[i]
		else
			defaultVal
	}
	
	boolean opt(String key) { options.containsKey(key) }
	
	String stringOpt(String key) { options[key] }
	String opt(String key, String defaultVal) {
		options.containsKey(key) ? options[key] : defaultVal
	}
		
	Integer intOpt(String key) { options[key] }
	int opt(String key, int defaultVal) {
		options.containsKey(key) ? options[key] : defaultVal
	}
		
	Double floatOpt(String key) { options[key] }
	double opt(String key, double defaultVal) {
		options.containsKey(key) ? options[key] : defaultVal
	}
		
	static main(args) {
		CommandLineParser clp = new CommandLineParser(
			"-opt -string=s -int=i -float=f", args)
		
		println clp.args().join("\n")
		
		println "-opt: " + clp.opt("-opt")
		
		println "-string: " + clp.opt("-string", "null")
		
		println "-int: " + clp.opt("-int", 0)
		
		println "-float: " + clp.opt("-float", 0.0)
	}

}
