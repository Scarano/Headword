package ocr.scripts

class FilterByLength {

	static main(args) {
		def input = new File(args[0])
		def output = new File(args[1])
		def minLength = args[2].toInteger()
		def maxLength = args[3].toInteger()
		
		output.withWriter("utf-8") { out ->
			input.eachLine("utf-8") { line ->
				def tokens = line.split(/\s+/)
				if (tokens.length >= minLength && tokens.length <= maxLength)
					out.println line
			}
		}
	}

}
