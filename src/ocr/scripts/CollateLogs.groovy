
package ocr.scripts

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

def totalRuns = 0

outputDirs.each { outputDir ->
	def m = outputDir =~ "$columnKey=([^_]*)"
	if (m.size() == 0) {
		println "Key '$columnKey' not found in '$outputDir'"
	}
	def columnValue = m[0][1]
	columnValues <<= columnValue
	
	def rowValues = rowKeys.collect {
		def m2 = (outputDir =~ "$it=([^_]*)")
		if (m2.size() == 0)
			throw new Exception("$it not found in $outputDir")
		m2[0][1]
	}
	
	def score = measure.startsWith("rel") ? 1.0D : 0.0D
	new File(new File(outputDir), "log.txt").eachLine { line ->
		if (measure == "WER") {
			m = line =~ /Corrected WER: ([\.\-\d]+)/
			if (m) {
				totalRuns++
				score = m[0][1].toDouble()
			}
		}
		else if (measure == "WERc") {
			m = line =~ /Corrected WER \(cheat\): ([\.\-\d]+)/
			if (m) {
				totalRuns++
				score = m[0][1].toDouble()
			}
		}
		else if (measure == "absWER") {
			m = line =~ /Corrected WER: ([\.\-\d]+)/
			if (m) {
				totalRuns++
				score -= m[0][1].toDouble()
			}
			m = line =~ /OCR WER: ([\.\-\d]+)/
			if (m)
				score += m[0][1].toDouble()
		}
		else if (measure == "relWERc") {
			m = line =~ /Corrected WER \(cheat\): ([\.\-\d]+)/
			if (m) {
				totalRuns++
				score /= m[0][1].toDouble()
			}
			m = line =~ /OCR WER \(cheat\): ([\.\-\d]+)/
			if (m)
				score *= m[0][1].toDouble()
		}
	}

	if (!rows.containsKey(rowValues))
		rows[rowValues] = [:]
	if (rows[rowValues].containsKey(columnValue)) {
		System.err.println "Duplicate for $rowValues; $columnValue"
		System.exit(-1)
	}
	rows[rowValues][columnValue] = score
	
}

System.err.println totalRuns

println rowKeys.join(",") + "," + columnValues.join(",")

rows.keySet()/*.sort(rowValueSort)*/.each { List<String> rowValues ->
	print rowValues.join(",") + ","
	println columnValues.collect { rows[rowValues][it] }.join(",");
}



