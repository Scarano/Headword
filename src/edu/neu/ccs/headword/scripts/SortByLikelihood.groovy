package edu.neu.ccs.headword.scripts

import groovy.transform.TupleConstructor;

import java.io.File;

import edu.neu.ccs.headword.ConditionalModel.Observer;
import edu.neu.ccs.headword.ConditionalModel.UnsmoothedConditionalModel;
import edu.neu.ccs.headword.ConditionalModel.WBConditionalModel;
import edu.neu.ccs.headword.ParsedCorpus;
import edu.neu.ccs.headword.ParsedCorpus.ParsedSentence;
import edu.neu.ccs.headword.util.CommandLineParser;

class SortByLikelihood {
	
	def static final START = "<s>"
	def static final END = "</s>"
	
	@TupleConstructor()
	static class SentenceLikelihood implements Comparable<SentenceLikelihood>{
		ParsedSentence sentence
		double likelihood

		@Override
		public int compareTo(SentenceLikelihood other) {
			Math.signum(other.likelihood - this.likelihood)
		}
	}
	
	static main(args) {
		CommandLineParser clp = new CommandLineParser(
			"-tag-likelihood -limit=i -output-tags", args)

		def parseFile = new File(clp.arg(0))
		def outputFile = new File(clp.arg(1))
		def limit = clp.opt("-limit", Integer.MAX_VALUE)
		def tagLikelihood = clp.opt("-tag-likelihood")
		def tagsOnly = clp.opt("-output-tags")
		
		def corpus = new ParsedCorpus(parseFile)
		def observer = new Observer<String, String>()
		
		corpus.sentences.each { sent ->
			def sequence = (tagLikelihood ? sent.tags : sent.text) as List
			def pairs = [[START] + sequence, sequence + [END]].transpose()
			pairs.each { c, e ->
				observer.observe(c, e)
			}
		}
		
		def model = new WBConditionalModel<String, String>(observer, 1e-5D)
		
		[
			"president",
			"source fulton prebon u.s.a inc",
			"skoal daze"
		].each { s ->
			println("\n$s")
			def sequence = s.split(/ /) as List
			def pairs = [[START] + sequence, sequence + [END]].transpose()
			println pairs
			def prob = pairs.collect { c, e ->
				printf("%s %s: %f\n", c, e, Math.log(model.prob(c, e)));
				Math.log(model.prob(c, e))
			}.sum()
			printf("%f\n", prob)
		}

		List<SentenceLikelihood> sentLs = []
		corpus.sentences.each { sent ->
			def sequence = (tagLikelihood ? sent.tags : sent.text) as List
			def pairs = [[START] + sequence, sequence + [END]].transpose()
			def prob = pairs.sum { c, e -> Math.log(model.prob(c, e)) }
			sentLs << new SentenceLikelihood(sent, prob)
		}
		sentLs = sentLs.sort()
		if (sentLs.size() > limit)
			sentLs = sentLs[0..<limit]
		
		outputFile.withWriter("UTF-8") { out ->
			sentLs.each { sent ->
				out.println(sent.likelihood)
				if (!tagsOnly)
					out.println(sent.sentence.text.join(' '))
				out.println(sent.sentence.tags.join(' '))
				if (!tagsOnly)
					out.println(sent.sentence.parse*.toString().join(' '))
			}
		}
	}
}






