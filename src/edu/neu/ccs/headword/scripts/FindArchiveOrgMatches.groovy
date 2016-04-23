package edu.neu.ccs.headword.scripts

def metadataFile = new File(args[0])
def dir = new File(args[1])

def hits = [:]

def inputFiles = []
metadataFile.eachLine { metadataLine ->
	def (name, author, title) = metadataLine.split(/::/)
	
	title = title.replaceFirst(/\d\d\d\d$/, '')
	title = title.replaceAll(/,/, ' ')
	
	def query = URLEncoder.encode(author + ' ' + title, 'UTF-8')
	def url = "https://archive.org/search.php?query=$query"

	def resultPage = new URL(url).getText()
	resultPage.eachLine { line ->
		(line =~ /hitRow.*href="\/details\/([^"]*)">(.*) - (.*)<br\/>/).each {
				_, archName, archTitle, archAuthor ->
			archAuthor = archAuthor.replaceAll(/<br\/>.*/, '')
			archAuthor = archAuthor.replaceAll(/<[^>]*>/, '')
			archTitle = archTitle.replaceAll(/<[^>]*>/, '')
			
			println([name, author, title, archName, archAuthor, archTitle].join("\t"))
			
			hits[archName] = [name, author, title, archAuthor, archTitle]
		}
	}
	
}







