package edu.neu.ccs.headword.scripts


def input = new File(args[0])
def output = new File(args[1])

output.withWriter("utf-8") { out ->
	input.eachLine("utf-8") { line ->
		out.println line.toLowerCase()
	}
}



	