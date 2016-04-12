package ocr

class HTKLattice {
	
	def private static htkArcALPattern = ~/(.*)\ta=(\S+)\tl=(\S+)$/
	def private static htkArcLPattern = ~/(.*)\tl=(\S+)$/
	
	/**
	 * In order to mix in probabilities of a second language model, an HTK
	 * lattice needs the acoustic/channel probabilities to be multiplied by the LM
	 * probabilities. (Otherwise, the LM probabilities of the first language model
	 * are just replaced.)
	 * 
	 * This just reads in an HTK lattice, multiplies the "acoustic" probabilities by
	 * the LM probabilities, sets the LM probabilities to 0, and outputs the result.
	 */
	static void setAcousticToPosterior(File inputLattice, File outputLattice, double lmWeight)
		throws IOException
	{
		outputLattice.withWriter("utf-8") { out ->
			inputLattice.eachLine("utf-8") { line ->
				def matcher = line =~ htkArcALPattern
				if (matcher.matches()) {
					def prefix = matcher[0][1]
					def aProb = readDouble(matcher[0][2])
					def lProb = readDouble(matcher[0][3])
					aProb += lmWeight * lProb
					out.println "$prefix\ta=${writeDouble(aProb)}\tl=0"
					return
				}
				
				matcher = line =~ htkArcLPattern
				if (matcher.matches()) {
					def prefix = matcher[0][1]
					def lProb = readDouble(matcher[0][2])
					def aProb = lmWeight * lProb
					out.println "$prefix\ta=${writeDouble(aProb)}\tl=0"
					return
				}
				
				out.println line
			}
		}
	}
	
	static double readDouble(String s) {
		if (s == "-inf")
			Double.NEGATIVE_INFINITY
		else
			s.toDouble()
	}
	
	static String writeDouble(double x) {
		if (Double.isNaN(x))
			"-inf"
		else
			Double.toString(x)
	}
	
	static main(args) {
		setAcousticToPosterior(new File(args[0]), new File(args[1]))
	}

}





