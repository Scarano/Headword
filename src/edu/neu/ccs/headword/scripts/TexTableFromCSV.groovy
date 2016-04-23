
package edu.neu.ccs.headword.scripts

def rows = []
new File(args[0]).eachLine { line ->
	line = line + ","
	def items = []
	while (line.length() > 0) {
		def item
		if (line.startsWith('"'))
			(item, line) = line.substring(1).split(/",/, 2)
		else
			(item, line) = line.split(/,/, 2)
		items << item
	}
	rows << items
}

rows.each { row ->
	row = row.collect { it.replaceAll(/\%/, '\\%') }
	println "\t\t" + row.join(' & ') + " \\\\"
}