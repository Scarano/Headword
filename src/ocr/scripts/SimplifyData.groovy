package ocr.scripts

class SimplifyData {

	static main(args) {
		def input = new File(args[0])
		def output = new File(args[1])
		
		output.withWriter("utf-8") { out ->
			input.eachLine("utf-8") { line ->
				out.println line.split(/\s+/).collect { token ->
					if (token.startsWith('N') || token == 'PRP')
						'N'
					else if (token in ['VB', 'VBZ', 'VBD', 'VBP'])
						'V'
					else if (token == 'IN')
						'P'
					else
						null
				}.grep().join(' ')
			}
		}
	}

}
