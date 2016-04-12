package ocr.scripts

def matchesFile = new File(args[0])
def dir = new File(args[1])

def names = [] as Set

def inputFiles = []
matchesFile.eachLine { matchLine ->
	def name = matchLine.split(/\t/)[3]
	names << name
}

successes = [] as Set

names.each { name ->

	try {
		def url = "http://ia600208.us.archive.org/9/items/$name/${name}_djvu.txt"
	
		def text = new URL(url).getText()
		
		def file = new File(dir, "${name}_djvu.txt")
		file.withWriter('UTF-8') { out ->
			out.write(text)
		}
		
		successes << name
	}
	catch (e) {
		System.err.println "Could not fetch $name"
	}
	
}

matchesFile.eachLine { matchLine ->
	def name = matchLine.split(/\t/)[3]
	println matchLine + '\t' + ((name in successes) ? 'Y' : 'N')
}






