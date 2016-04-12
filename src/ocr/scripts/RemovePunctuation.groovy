package ocr.scripts


def input = new File(args[0])
def output = new File(args[1])

output.withWriter("utf-8") { out ->
	input.eachLine("utf-8") { line ->
		def tokens = line.split(" ")
		tokens = tokens.findAll { token ->
			token.toCharArray().any { c -> c.isLetterOrDigit() }
		}
		out.println tokens.join(" ")
	}
}



	