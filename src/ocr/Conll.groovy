package ocr

import ocr.util.CommandLineParser;

class Conll {
	
	static class AnnotatedToken {
		int position
		String text
		String tag
		int head
		
		AnnotatedToken(int position, String text, String tag, int head) {
			this.position = position
			this.text = text
			this.tag = tag
			this.head = head
		}
		
		boolean isWord() {
			if (tag == "SYM")
				return false
				
			if (tag == "\$")
				return true // Because currency symbols tend to be heads of numbers
				
			return tag.charAt(0).isLetterOrDigit()
		}
	}
	
	List<List<AnnotatedToken>> sentences = []
	
	Conll(File file, int minLength, int maxLength) {
		def buffer = []
		file.eachLine("utf-8") { line ->
			if (line.isEmpty()) {
				if (buffer.size() >= minLength && buffer.size() <= maxLength) {
					sentences << buffer.collect {
						def fields = it.split("\t")
						def position = fields[0].toInteger()-1
//						def text = fields[1] == "<num>" ? fields[2] : fields[1]
						def text = fields[1]
						def tag = fields[4]
						if (tag == ":") tag = "<colon>" // Dageem can't handle colons
						def head = fields[6].toInteger()-1
						new AnnotatedToken(position, text, tag, head)
					}
				}
				buffer = []
			}
			else {
				buffer << line
			}
		}
	}
	
	void filterByLength(int minLength, int maxLength) {
		sentences = sentences.grep { it.size() >= minLength && it.size() <= maxLength }
	}
	
	void removePunctuation() {
		def newSentences = []
		sentences.each { s ->
			def newPositions = new int[s.size()]
			def nextPosition = 0
			s.eachWithIndex { token, i ->
				if (token.isWord())
					newPositions[i] = nextPosition++
				else
					newPositions[i] = -2
			}
			def newSentence = []
			s.eachWithIndex { token, i ->
				if (newPositions[i] != -2) {
					token.position = newPositions[i]
					token.head = token.head > -1 ? newPositions[token.head] : -1
					if (token.head == -2) {
						System.err.println(
							"Warning: punctuation as head of " +
							"${token.position+1}[${token.text}] in [${s*.text.join(' ')}]")
						token.head = token.position > 0 \
							? token.position - 1 // default to right-branching
							: 1 // (or left-branching if first token)
					}
					newSentence << token
				}
			}
			newSentences << newSentence
		}
		sentences = newSentences.grep { it.size() > 0 }
	}
	
	void writeLines(File file, boolean writeText, boolean writeTags, boolean writeParses) {
		file.withWriter("utf-8") { out ->
			sentences.each { s ->
				if (writeText)
					out.println s*.text.join(" ")
				if (writeTags)
					out.println s*.tag.join(" ")
				if (writeParses)
					out.println s*.head.join(" ")
			}
		}
	}

	static void main(String[] args) {
		def clp = new CommandLineParser(
			"-text -tags -parses " +
			"-exclude-punctuation -pre-exclude-punctuation -min=i -max=i", args)
		def input = clp.arg(0)
		def output = clp.arg(1)
		def minLength = clp.opt("-min", 0)
		def maxLength = clp.opt("-max", 9999)
		
		def conll;
		if (clp.opt("-pre-exclude-punctuation")) {
			conll = new Conll(new File(input), 0, 9999)
			conll.removePunctuation()
			conll.filterByLength(minLength, maxLength)
		}
		else {
			conll = new Conll(new File(input), minLength, maxLength)
			if (clp.opt("-exclude-punctuation"))
				conll.removePunctuation()
		}
		
		conll.writeLines(
			new File(output), clp.opt("-text"), clp.opt("-tags"), clp.opt("-parses"))
	}
	
}





