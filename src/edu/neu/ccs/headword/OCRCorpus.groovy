package edu.neu.ccs.headword

import groovy.transform.TupleConstructor;

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.neu.ccs.headword.DocumentAligner.DocumentAlignment;
import edu.neu.ccs.headword.util.CommandLineParser;

class OCRCorpus {
	
	static ocrSuffix
	
	@TupleConstructor()
	static class Entry {
		def transName
		def transAuthor
		def transTitle
		def ocrName
		def ocrAuthor
		def ocrTitle
		def fetched
		def value
		def notes
		
		def File ocrText(File ocrDir) {
//			new File(ocrDir, ocrName + '_djvu.txt')
			new File(ocrDir, ocrName + ocrSuffix + '.txt')
		}
		
		def File transText(File transDir) {
			new File(transDir, transName + ".txt")
		}
	}
	
	
	def entries
	
	OCRCorpus(File ocrCorpusFile) {
		entries = []
		ocrCorpusFile.eachLine { line, num ->
			if (num > 1) {
				def fields = line.split(/\t/).collect { String s ->
					if (s.startsWith('"') && s.endsWith('"'))
						s.substring(1, s.length() - 2)
					else
						s
				}
				entries << new Entry(
					fields[0], fields[1], fields[2],
					fields[3], fields[4], fields[5],
					fields[6] == 'Y', (fields[7] ?: 0).toInteger(),
					fields[0])
			}
		}
	}
	
	def writeAlignments(File transDir, File ocrDir, File alignDir, File reportFile,
		boolean channelSetOnly
	) {
		reportFile.withWriter { report ->
			entries.each { Entry entry ->
				if (entry.fetched && entry.value == (channelSetOnly ? 1 : 2)) { 
					def alignedLines
					
					TokenizedDocument trans = new TokenizedDocument(
						new FileInputStream(entry.transText(transDir)));
					TokenizedDocument ocr 
					try {
						ocr = new TokenizedDocument(
							new FileInputStream(entry.ocrText(ocrDir)));
					} catch (e) {
						println "Cannot open ${entry.ocrName}: $e"
						return
					}
					DocumentAlignment alignment = 
						DocumentAligner.alignDocuments(trans, ocr);
					PrintWriter writer= new PrintWriter(
						new File(alignDir, entry.ocrName + '.align'), "UTF-8");
					DocumentAligner.printDocumentAlignment(trans, ocr, alignment, writer);
					writer.close();
					
					alignedLines = alignment.lineAlignment.length
					
					if (!channelSetOnly) {
						def sentAlignment = new SentenceAlignment(
							alignment,
							new TokenizedDocument(entry.transText(transDir)),
							new TokenizedDocument(entry.ocrText(ocrDir)));	
						
						new File(alignDir, entry.ocrName + '.salign').withWriter('utf-8') { out ->
							sentAlignment.print(out)
						}
					}

					def quality = (float) alignment.matchingChars / alignment.chars
										
					report.println([
						entry.transName, entry.ocrName, entry.ocrTitle, 
						alignedLines, alignment.chars, alignment.matchingChars, quality
					].join("\t"))
					report.flush()
					
					println "$entry.transName $entry.ocrName " +
						"$alignedLines $alignment.chars $alignment.matchingChars $quality"
				}
			}
		}
	}
	
	static main(args) {
		if (args[0] == '-align') {
			align(args[1..-1] as String[])
		}
		else if (args[0] == '') {
			
		}
	}

	static align(String[] args) {
		// -channel-set does two things: it filters in corpus entries with '1' in the 'value'
		// column (only '2' is used otherwise); and it saves line-by-line .align files, instead
		// of sentence-by-sentence '.salign' files.
		CommandLineParser clp = new CommandLineParser("-channel-set -ocr-suffix=s", args)
		
		def ocrCorpusFile = new File(clp.arg(0))
		def transDir = new File(clp.arg(1))
		def ocrDir = new File(clp.arg(2))
		def alignDir = new File(clp.arg(3))
		def reportFile = new File(clp.arg(4))
		def channelSet = clp.opt("-channel-set")
		ocrSuffix = clp.opt("-ocr-suffix", '_djvu')
		
		def corpus = new OCRCorpus(ocrCorpusFile)
		
		corpus.writeAlignments(transDir, ocrDir, alignDir, reportFile, channelSet)
	}

}





