
package edu.neu.ccs.headword.scripts

def rowValueSort = { List a, List b ->
	if (a.size() == 0 || b.size() == 0)
		return 0
	int val = 0
	if (a[0].isDouble() && b[0].isDouble())
		val = Math.signum(a[0].toDouble() - b[0].toDouble())
	else
		val = b[0].compareTo a[0]
	if (val != 0)
		return val
	return rowValueSort(a[1..-1], b[1..-1])
}

def measure = args[0]
def columnKey = args[1]
def rowKeys = args[2].split(/,/)
def outputDirs = args[3..-1]

def columnValues = [] as Set
def rows = [:]

outputDirs.each { outputDir ->
	outputDir.split('_').each { segment ->
		def m = segment =~ /([^=]*)=([^=]*)/
		
	}
	def columnValue = (outputDir =~ "$columnKey=([^_]*)")[0][1]
	columnValues <<= columnValue
	
	def rowValues = rowKeys.collect {
		(outputDir =~ "$it=([^_]*)")[0][1]
	}
	
	def WER = 0.0
	new File(new File(outputDir), "log.txt").eachLine { line ->
		def m = line =~ /Corrected WER \(Cheat\): ([\.\-\d]+)/
		if (m) {
			score = m[0][1].toDouble()
		}
	}

	if (!rows.containsKey(rowValues))
		rows[rowValues] = [:]
	rows[rowValues][columnValue] = score
	
}

System.err.println totalRuns

println rowKeys.join(",") + "," + columnValues.join(",")

rows.keySet().sort(rowValueSort).each { List<String> rowValues ->
	print rowValues.join(",") + ","
	println columnValues.collect { rows[rowValues][it] }.join(",");
}



