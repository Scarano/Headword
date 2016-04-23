
package edu.neu.ccs.headword.scripts

def items = []
new File(args[0]).eachLine { items << it.trim() }

def width = args[1].toInteger()

def matrix = []
def x = width
items.each { item ->
	if (x == width) {
		matrix << []
		x = 0
	}
	matrix[-1] << item
	x++
}

matrix.transpose().each { row ->
	println "\t\t" + row.join(' & ') + " \\\\"
}