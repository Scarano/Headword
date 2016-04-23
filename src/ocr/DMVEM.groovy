package ocr

import static ocr.util.Util.nvl;

import java.io.File;
import java.io.IOException;

import ocr.util.CommandLineParser
import ocr.LatticeParser.DMVGrammarScorer;
import ocr.LatticeParser.DMVScorer
import ocr.LatticeParser.DMVVectorScorer;

class DMVEM {
	public static boolean debug = false

	static void main(String[] args) {
		CommandLineParser clp = new CommandLineParser(
			"-min-length=i -max-length=i -input-model=s -epsilon=f " +
			"-clustering=s -viterbi -unk-prob=f -lex " + 
			"-tag-smoothing=f -lex-model=s -lex-smoothing=s -lambda=f " +
			"-debug -save-all", args)
		
		def inputModel = clp.opt('-input-model', null as String)
		def dataFile = clp.arg(0)
		def outputPrefix = clp.arg(1)
		def startIteration = clp.arg(2).toInteger()
		def iterations = clp.arg(3).toInteger()
		def minLength = clp.opt("-min-length", 0);
		def maxLength = clp.opt("-max-length", 9999);
		def epsilon = clp.opt('-epsilon', 1e-5D)
		def viterbi = clp.opt("-viterbi", null)
		def clusteringFile = clp.opt("-clustering", null)
		def unkProb = clp.opt("-unk-prob", -2D)
		def lexicalized = clp.opt("-lex")
		def tagAlpha = clp.opt("-tag-smoothing", 0D);
		def lexCountsFile = clp.opt("-lex-model", null);
		def lexAlpha = clp.opt("-lex-smoothing", 0D);
		def lambda = clp.opt("-lambda", Double.NaN); // weight of lexical model
		debug = clp.opt("-debug")
		def saveAll = clp.opt("-save-all")
		
		def clustering = null
		if (clusteringFile != null)
			clustering = new Clustering(new File(clusteringFile), unkProb)
		
		def scorer
		if (startIteration != 1) {
			inputModel = outputPrefix + (startIteration - 1) + '.dmv'
			
			scorer = new DMVScorer(new DMVGrammar(inputModel))

//			DMVGrammar grammar = new DMVGrammar(inputModel)
//			scorer = new DMVVectorScorer(grammar.asVector(grammar.buildVocabulary()))
		}
		else if (inputModel == 'harmonic') {
			inputModel = outputPrefix + '0.dmv'
			writeHarmonicModel(inputModel, dataFile, clustering, minLength, maxLength)
			
			scorer = new DMVGrammarScorer(new DMVGrammar(inputModel))
		}
		else if (inputModel == null) {
			scorer = new LatticeParser.LetterScorer()
		}
		else if (inputModel.endsWith(".dmv")) {
			scorer = new DMVGrammarScorer(new DMVGrammar(inputModel))
		}
		else {
			def tagModel = new TagDMV(inputModel + ".cnt", false, tagAlpha)
			File lexModelFile = new File(inputModel+".lcnt")
			if (lexModelFile.exists()) {
				def lexModel = new LexDMV(
					lexModelFile, lexAlpha, tagModel, lambda, Math.exp(unkProb))
				scorer = new DMVScorer(lexModel)
			}
			else {
				scorer = new DMVScorer(tagModel)
			}
		}
		

		def sentences = []			
		new File(dataFile).eachLine("utf-8") { sentences << it.split(" ") }
		sentences = sentences.grep { it.length >= minLength && it.length <= maxLength }
		
		def startTime = System.currentTimeMillis()

		def lastLikelihood = Double.NEGATIVE_INFINITY
		
		for (def t = startIteration; t <= iterations; t++) {
			def iterStartTime = System.currentTimeMillis()
			
			def parser = new LatticeParser(scorer)
			def counter = new DMVCounter()
			def lexCounter = new DMVCounter()
			
			def viterbiProb = 0.0D
			def likelihood = 0.0D
			
			sentences.each { String[] sent ->
				def parse
				if (clustering == null)
					parse = parser.parse(sent)
				else
					parse = parser.parse(sent, clustering)
				
				if (!viterbi)
					parser.reestimate(counter)
				else
					parser.reestimateViterbi(counter, lexicalized ? lexCounter : null)
					
				viterbiProb += parser.viterbiProb()
				likelihood += parser.sentProb()
				
				if (debug) {
					println sent.join(' ')
					printf "%f %f %s\n",
						parser.viterbiProb(),
						parser.sentProb(),
						parse.toString()
					println()
				}
			}

			if (!lexicalized) {
				def countPath = outputPrefix + (saveAll ? t + '.cnt' : 'cnt')
				counter.saveCounts(countPath);

				def newGrammar = counter.createGrammar()
				def modelPath = outputPrefix + (saveAll ? t + '.dmv' : 'dmv')
				newGrammar.save(modelPath)
				
				try {
					String modelFile = new File(modelPath).getName();
					Runtime.getRuntime().exec(
							"ln -sf " + modelFile + " " + outputPrefix + "last.dmv");
				} catch (IOException e) {
					System.err.println(e);
				}
				
				scorer = new DMVGrammarScorer(newGrammar)
			}
			else {
				def modelPath = outputPrefix + (saveAll ? t + '.cnt' : 'cnt')
				def lexModelPath = outputPrefix + (saveAll ? t + '.lcnt' : 'lcnt')
				
				counter.saveCounts(modelPath)
				lexCounter.saveCounts(lexModelPath)

				try {
					def modelFile = new File(modelPath).getName();
					Runtime.getRuntime().exec(
							"ln -sf " + modelFile + " " + outputPrefix + "last.cnt");
					modelFile = new File(lexModelPath).getName();
					Runtime.getRuntime().exec(
							"ln -sf " + modelFile + " " + outputPrefix + "last.lcnt");
				} catch (IOException e) {
					System.err.println(e);
				}
				
				def tagModel = new TagDMV(modelPath, false, tagAlpha)
				def lexModel = new LexDMV(lexModelPath, lexAlpha, tagModel, lambda)
				scorer = new DMVScorer(lexModel)
			}

			def improvement = (lastLikelihood - likelihood)/lastLikelihood
			
			printf "Iteration %d: mean viterbi score = %f; mean likelihood = %f (ratio %f)\n",
				t, viterbiProb / sentences.size(), likelihood / sentences.size(), improvement
			
			printf "  iteration time: %.2f minutes; total time: %.2f minutes\n",
				(System.currentTimeMillis() - iterStartTime) / 1000D / 60D,
				(System.currentTimeMillis() - startTime) / 1000D / 60D

			if (t > 2 && epsilon != 0.0D && improvement < epsilon)
				break

			lastLikelihood = likelihood
		}		
	}
	
	static void writeHarmonicModel(
		modelFile, corpusFile, Clustering clustering, int minLength, int maxLength)
	{
		def alphabet = new edu.cmu.cs.lti.ark.dageem.Alphabet()
		def corpus = new edu.cmu.cs.lti.ark.dageem.SentenceCorpus(alphabet)
		new File(corpusFile).eachLine("utf-8") { String line ->
			def tokens = line.split(/ /)
			if (tokens.length >= minLength && tokens.length <= maxLength) {
				if (clustering != null)
					tokens = tokens.collect { clustering.clusterOfWord(it) }
				corpus.add(tokens.join(' '))
			}
		}
		def grammar = new edu.cmu.cs.lti.ark.dageem.DMVGrammar(alphabet)
		grammar.setToHarmonicInitializer(corpus)
		grammar.writeGrammar(modelFile)
	}
}





