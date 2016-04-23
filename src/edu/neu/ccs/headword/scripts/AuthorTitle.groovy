package edu.neu.ccs.headword.scripts

def dir = new File(args[0])
def setFile = new File(args[1])

def inputFiles = []
setFile.eachLine { docName ->
	def author = null
	def title = null
	def date = null
	
	def file = new File(dir, docName+'.xml')
	file.eachLine("utf-8") { line ->
		if (author == null) {
			(line =~ /persName.*>(.*)<\/persName>/).each { author = it[1] }
		}
		if (title == null) {
			(line =~ /<title[^>]*>([^<>]*)<\/title>/).each { title = it[1] }
		}
		if (date == null) {
			(line =~ /<date when="(\d+)">/).each { date = it[1] }
		}
	}

	if (author == null)
		System.err.println "No author found in $file."
	if (title == null)
		System.err.println "No title found in $file."
	if (date == null) {
		def m = title =~ / (\d\d\d\d)$/
		if (m.size() > 0) {
			date = m[0][1]
		} else {
			System.err.println "No date found in $file."
			date = ""
		}
	}

	if (author != null && title != null) {
		println "$docName::$author::$title::$date"
	}
}







