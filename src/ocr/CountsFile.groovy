package ocr

import ocr.ConditionalModel.Observer

class CountsFile {

	def observers = [:]
	
	CountsFile(String file) {
		new File(file).eachLine("utf-8") { line ->
			def (modelName, contextStr, event, countStr) = line.split("\t")
			def context = (contextStr.split(' ') as List).asImmutable()
			def count = countStr.toDouble()
			
			def observer = observers[modelName]
			if (observer == null) {
				observer = new Observer<ArrayList<String>, String>()
				observers[modelName] = observer
			}
			
			observer.observe(context, event, count)
		}
	}
	
	Observer<ArrayList<String>, String> getCounts(String modelName) {
		observers[modelName]
	}

	static main(args) {
	
	}

}
