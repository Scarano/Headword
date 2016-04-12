package ocr

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import ocr.DocumentAligner.DocumentAlignment;
import ocr.DocumentAligner.LineAlignment;

class SentenceAlignment {
	
	public List<String> ocrSentences = []
	public List<String> transcriptionSentences = []
	
	public SentenceAlignment(File file, int minSentLength, int maxSentLength) {
		file.withReader("UTF-8") { reader ->
			while (reader.ready()) {
				def transcriptionSentence = reader.readLine()
				def ocrSentence = reader.readLine()
				def length = ocrSentence.split(" ").length;
				if (length >= minSentLength && length <= maxSentLength) {
					ocrSentences << ocrSentence
					transcriptionSentences << transcriptionSentence
				}
			}
		}
	}
	
	public SentenceAlignment(TokenizedDocument transcriptionDoc, TokenizedDocument ocrDoc) {
		SentenceAlignment(null, transcriptionDoc, ocrDoc)
	}
	public SentenceAlignment(
			DocumentAlignment alignment,
			TokenizedDocument transcriptionDoc, TokenizedDocument ocrDoc)
	{
		def regions
		if (alignment != null)
			regions = DocumentAligner.alignedRegions(alignment, transcriptionDoc, ocrDoc)
		else
			regions = DocumentAligner.alignedRegions(transcriptionDoc, ocrDoc)

		regions.eachWithIndex { regionLines, regionNum ->

			// This is easier if we invert the line alignments (turn ocr -> trans 
			// into trans -> ocr), because we examine the OCR for line-breaks, and then
			// need to know the corresponding character positions in the transcription.
			regionLines = regionLines*.invert()
			
			def ocrSentenceSpans = []
			def ocrSentenceInProgress = null
			regionLines.eachWithIndex { line, lineNum ->
				tokenSpans(line.oldLine).each { ocrSpan ->
					def ocrToken = line.oldLine.substring(ocrSpan)
					if (ocrSentenceInProgress == null)
						ocrSentenceInProgress = [lineNum, ocrSpan[0]]
					
					if (probablyEndsSentence(ocrToken)) {
						ocrSentenceSpans << [ocrSentenceInProgress, [lineNum, ocrSpan[1]]]
						ocrSentenceInProgress = null
					}					
				}
			}
			
			if (ocrSentenceSpans.size() == 0)
				return

			// Throw out first sentence if it looks like a fragment
			if (!likelyBeginsSentence(regionLines[0].oldLine.split(/\s+/)[0]))
				ocrSentenceSpans.remove(0)
				
			def transSentenceSpans = ocrSentenceSpans.collect { spanStart, spanEnd ->
				[	[spanStart[0], matchingTransLoc(spanStart[1], regionLines[spanStart[0]])],
					[spanEnd[0], matchingTransLoc(spanEnd[1] - 1, regionLines[spanEnd[0]]) + 1]]
			}
			
			retrieveSpans(ocrSentences, ocrSentenceSpans, regionLines*.oldLine)
			retrieveSpans(transcriptionSentences, transSentenceSpans, regionLines*.newLine)
			
//			println "$regionNum / ${regions.size()}: ${ocrSentences[-1]}"
		}
	}
	
	def static matchingTransLoc(ocrPos, LineAlignment lineAlignment) {
		def transPos = -1
		while (transPos == -1 && ocrPos >= 0)
			transPos = lineAlignment.charAlignment[ocrPos--]
		if (transPos == -1) transPos = 0
		return transPos
	}
	
//	def static matchingTranscriptionLoc(ocrLoc, List<LineAlignment> regionLines) {
//		def (line, ocrPos) = ocrLoc
//		def regionLine = regionLines[line]
//println "$line/$ocrPos ${regionLine.oldLine.length()} ${regionLine.newLine.length()} ${regionLine.charAlignment.length}"
//		def transPos = -1
//		while (transPos == -1 && ocrPos >= 0)
//			transPos = regionLine.charAlignment[ocrPos--]
//		if (transPos == -1) transPos = 0
//	}
	
	def static retrieveSpans(List acc, List spans, List<String> lines) {
		spans.each { start, end ->
			def (startLine, startPos) = start
			def (endLine, endPos) = end
			def spanLines = (startLine..endLine).collect { lineNum ->
				def line = lines[lineNum]
				def lineStartPos = lineNum == startLine ? startPos : 0
				def lineEndPos = lineNum == endLine ? endPos : line.length()
				line.substring(lineStartPos, lineEndPos)
			}
			acc << spanLines.join(" ")
		}
	}
	
	public print(BufferedWriter out) {
		[transcriptionSentences, ocrSentences].transpose().each { transSent, ocrSent->
			out.writeLine(transSent)
			out.writeLine(ocrSent)
		}
	}
	
	def static List tokenSpans(String s) {
		def chars = s.toCharArray() as List
		def spans = []
		// Iterate over pairs of adjacent characters
		List<List<Character>> charPairs =
			[[' ' as Character] + chars, chars + [' ' as Character]].transpose()
		charPairs.eachWithIndex { pair, i ->
			def (c1, c2) = pair
			if (c1.isWhitespace() && !c2.isWhitespace())
				spans << [i]
			else if (!c1.isWhitespace() && c2.isWhitespace())
				spans[-1] << i
		}
		return spans
	}
	
//	def List tokenSpans(String s) {
//		def result = []
//		def inToken = false
//		s.toCharArray().eachWithIndex { c, i ->
//			if (inToken && c.isWhitespace()) {
//				result[-1] << i
//				inToken = false
//			}
//			else if (!c.isWhitespace()) {
//				result << [i]
//				inToken = true
//			}
//		}
//	}
	
//	static findAlignedRegions(

	static main(args) {
		def ocrFile = new File(args[0]);
		def transcriptionFile = new File(args[1]);
		def alignmentFile = new File(args[2]);
		
		def alignment = new SentenceAlignment(
			new TokenizedDocument(ocrFile),
			new TokenizedDocument(transcriptionFile));
		
		alignmentFile.withWriter("UTF-8") { BufferedWriter out ->
			alignment.print(out)
		} 
	}

	static Set nonSentenceEndingTokens;
	
	static {
		nonSentenceEndingTokens = """
			Mr Mrs Ms Mssr Mssrs Patr Matr Dr Rev Capt Col Gen
			St Ave
			etc &c
			no No
		""".trim().split(/\s+/) as Set
		nonSentenceEndingTokens += nonSentenceEndingTokens.collect() {
			String s -> s.toUpperCase()
		}
	}

	public static boolean probablyEndsSentence(String token) {
		if (
			token.endsWith(".") || token.endsWith("?") || token.endsWith("!") ||
			token.endsWith(".\"") || token.endsWith(".'") || token.endsWith(".)") ||
			token.endsWith(";") // XXX good idea? bad idea?
		) {
			String word = (token =~ /[\.\?\!;].*/).replaceFirst("")
			if (word.length() == 1 || word in nonSentenceEndingTokens) {
				return false
			}
			return true
		}
		return false
	}
	
	public static boolean likelyBeginsSentence(String token) {
//		def result = likelyBeginsSentence_(token)
//		println "likelyBeginsSentence($token): $result"
//		return result
//	}
//	public static boolean likelyBeginsSentence_(String token) {
		if (token.length() == 0)
			return false;
		else if (token.length() == 1)
			return token.charAt(0).isUpperCase();
		else
			return token.charAt(0).isUpperCase() && token.charAt(1).isLowerCase();
	}
}





