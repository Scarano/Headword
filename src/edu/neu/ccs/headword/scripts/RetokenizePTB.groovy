package edu.neu.ccs.headword.scripts

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.neu.ccs.headword.ParsedCorpus;
import edu.neu.ccs.headword.ParsedCorpus.ParsedSentence;
import edu.neu.ccs.headword.SimpleTokenizer;
import edu.neu.ccs.headword.util.CommandLineParser;

class RetokenizePTB {
	
	static class Token {
		int pos
		String text
		String tag
		Token head
		
		Token(int pos, String text, String tag) {
			this.pos = pos; this.text= text; this.tag = tag
		}
		
		int headPos() {
			head == null ? -1 : head.pos
		}
	}
	
	static List<Token> tokensOf(ParsedCorpus.ParsedSentence sentence) {
		List<Token> tokens = []
		for (int i = 0; i < sentence.text.length; i++)
			tokens << new Token(i, sentence.text[i], sentence.tags[i])
		for (int i = 0; i < tokens.size(); i++)
			tokens[i].head = sentence.parse[i] == -1 ? null : tokens[sentence.parse[i]]
		tokens
	}
	
	static simpleRetokenize(ParsedCorpus corpus) {
		def outputCorpus = new ParsedCorpus()
		
		corpus.sentences.each { sentence ->
			def tokens = tokensOf(sentence)
			List<Token> newTokens = []
			for (int i = 0; i < tokens.size(); i++) {
				def token = tokens[i]
				
				if (i + 1 < tokens.size() && tokens[i+1].text == "n't")
					token.text += 'n'
				
				if (token.text == /--/)
					token.text = '\u2014'
				else if (token.text == /`/ && token.tag == /``/)
					token.text = '\u2018'
				else if (token.text == /'/ && token.tag == /''/)
					token.text = '\u2019'
				else if (token.text == /``/ && token.tag == /``/)
					token.text = '\u201c'
				else if (token.text == /''/ && token.tag == /''/)
					token.text = '\u201d'
				else if (token.text == "n't")
					token.text = "'t"

				if (token.text.startsWith("'")) {
					def aposToken = new Token(0, "'", "'")
					aposToken.head = token
					newTokens << aposToken
					
					token.text = token.text.substring(1)
					newTokens << token
				}
				else if (token.text == '<num>') {
					newTokens << token
				}
				else {
					def subTokenTexts = SimpleTokenizer.tokenize(token.text, false)
					if (subTokenTexts.length == 1) {
						newTokens << token
					}
					else if (
						!subTokenTexts[-1].charAt(0).isLetterOrDigit()
						&& subTokenTexts[0].charAt(0).isLetterOrDigit()
					) {
						token.text = subTokenTexts[0]
						newTokens << token
						for (int j = 1; j < subTokenTexts.length; j++) {
							def subToken = new Token(0, subTokenTexts[j], "?")
							subToken.head = newTokens[-1]
							newTokens << subToken
						}
					}
					else {
						token.text = subTokenTexts[subTokenTexts.length - 1]
						LinkedList<Token> subTokens = [token]
						for (int j = subTokenTexts.length - 2; j >= 0; j--) {
							def subToken = new Token(0, subTokenTexts[j], "?")
							subToken.head = subTokens[0]
							subTokens.addFirst(subToken)
						}
						subTokens.each { newTokens << it }
					}
				}
			}
			
			newTokens.eachWithIndex { token, i -> token.pos = i }
			
			outputCorpus.sentences << new ParsedCorpus.ParsedSentence(
				newTokens*.text as String[],
				newTokens*.tag as String[],
				newTokens*.headPos() as int[])
		}

		return outputCorpus
	}
	
	static retokenize(ParsedCorpus corpus) {
		def outputCorpus = new ParsedCorpus()
		
		corpus.sentences.each { sentence ->
			def tokens = tokensOf(sentence)
			List<Token> newTokens = []
			for (int i = 0; i < tokens.size(); i++) {
				def token = tokens[i]
				
//				if (i + 1 < tokens.size() && tokens[i+1].text == "n't")
//					token.text += 'n'
				
				if (token.text == /--/)
					token.text = '\u2014'
				else if (token.text == /`/ && token.tag == /``/)
					token.text = '\u2018'
				else if (token.text == /'/ && token.tag == /''/)
					token.text = '\u2019'
				else if (token.text == /``/ && token.tag == /``/)
					token.text = '\u201c'
				else if (token.text == /''/ && token.tag == /''/)
					token.text = '\u201d'
//				else if (token.text == "n't")
//					token.text = "'t"

				newTokens << token
			}
			
			newTokens.eachWithIndex { token, i -> token.pos = i }
			
			outputCorpus.sentences << new ParsedCorpus.ParsedSentence(
				newTokens*.text as String[],
				newTokens*.tag as String[],
				newTokens*.headPos() as int[])
		}

		return outputCorpus
	}
	
	static main(args) {
		CommandLineParser clp = new CommandLineParser("-simple", args)
		def inputFile = new File(clp.arg(0))
		def outputPrefix = clp.arg(1)
		boolean simple = clp.opt("-simple")
		
		def inputCorpus = new ParsedCorpus(inputFile)
		def outputCorpus = simple ? simpleRetokenize(inputCorpus) : retokenize(inputCorpus)
		
		outputCorpus.write(new File(outputPrefix + ".parses"))
		new File(outputPrefix + ".tags").withWriter("utf-8") { out ->
			outputCorpus.sentences.each { sentence ->
				out.println(sentence.tags.join(' '))
			}
		}
		new File(outputPrefix + ".text").withWriter("utf-8") { out ->
			outputCorpus.sentences.each { sentence ->
				out.println(sentence.text.join(' '))
			}
		}
	}

}





