package edu.neu.ccs.headword

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.neu.ccs.headword.util.CommandLineParser
import edu.neu.ccs.headword.util.RunConfig

class Clustering {
	
	def static clusterings = [:]
	
	public static final String MISC_CLUSTER = "<misc>"
	public static final String COLON_CLUSTER = "<colon>"
	
	boolean caseless
	double unkProb
	Map<String, String> clusterMap
	Map<String, Double> wordProbs // P(w|c)
	
	Clustering(File file, double unkProb) {
		this(file, true, true)
		this.unkProb = unkProb
	}
	
	/**
	 * 
	 * @param file
	 * @param useMostCommonWord
	 * 		true: Represent cluster by most common word
	 * 		false: Represent cluster by binary path string
	 */
	Clustering(File file, boolean caseless, boolean useMostCommonWord) {
		this.caseless = caseless
		unkProb = Double.NEGATIVE_INFINITY
		
		def pathMap = new HashMap<String, String>()
		def mcw = new HashMap<String, String>()
		def mcwFreq = new HashMap<String, Integer>()
		def wordFreqs = new HashMap<String, Integer>()
		def clusterFreqs = new HashMap<String, Integer>()
		
		file.eachLine("UTF-8") { line ->
			def (cluster, word, freq) = line.split(/\t/)
			cluster = cluster.intern() // I should try getting rid of these intern()s...
			word = word.intern()
			freq = freq.toInteger()
			pathMap[word] = cluster
			clusterFreqs[cluster] = (clusterFreqs[cluster] ?: 0) + freq
			wordFreqs[word] = freq
			if (mcwFreq[cluster] == null || mcwFreq[cluster] < freq) {
				mcw[cluster] = word
				mcwFreq[cluster] = freq
			}
		}
		
		if (useMostCommonWord) {
			clusterMap = new HashMap<String, String>()
			pathMap.each { word, path ->
				clusterMap[word] = mcw[path]
			}
		}
		else {
			clusterMap = pathMap
		}
		
		wordProbs = new HashMap<String, Double>()
		wordFreqs.each { word, freq ->
			wordProbs[word] = Math.log(((double) freq) / clusterFreqs[pathMap[word]])
		}
	}
	
	static synchronized Clustering fromConfig(RunConfig config) {
		File file = config.getDataFile("clustering.file")
		double unkProb = Math.log(config.getDouble("clustering.unk-prob"))
		String clusteringKey = file.getAbsolutePath() + ":" + unkProb
		Clustering clustering = clusterings[clusteringKey]
		if (clustering == null) {
			clustering = new Clustering(file, unkProb)
			clusterings[clusteringKey] = clustering
		}
		return clustering
	}
	
	double probOfWordGivenCluster(String word) {
		def wordKey = caseless ? word.toLowerCase() : word
		def prob = wordProbs[wordKey]
		
		if (prob == null)
			unkProb
		else
			prob
	}
	
	String clusterOfWord(String word) {
		def wordKey = caseless ? word.toLowerCase() : word
		def cluster = clusterMap[wordKey]
		if (cluster == null)
			MISC_CLUSTER
		else if (cluster.equals(":")) // Colons not allowed in Dageem grammar file
			COLON_CLUSTER
		else
			cluster
	}
	
	String[] clusterSequence(String[] words) {
		String[] tags = new String[words.length]
		for (int i = 0; i < words.length; i++)
			tags[i] = clusterOfWord(words[i])
		tags
	}
	
	boolean isInVocab(String word) {
		return clusterMap.containsKey(word);
	}
	
	String[][] loadCorpus(String file, int minLength, int maxLength) {
		def sentences = []
		new File(file).eachLine("UTF-8") { line ->
			String[] tokens = line.split(' ')
			if (tokens.length >= minLength && tokens.length <= maxLength) {
				sentences << tokens.collect { clusterOfWord(it) } as String[]
			}
		}
		sentences
	}
	
	/**
	 * Save as SRILM word classes file (to be used with lattice-tool)
	 * 
	 * @param usedVocab
	 * 		Only usedVocab is output. This isn't just a performance optimization; usedVocab
	 * 		may include words that are not in the cluster map, which will be mapped to
	 * 		MISC_CLUSTER automatically.
	 */
	void saveSRILMClasses(File file, Iterable<String> usedVocab) {
		file.withWriter("UTF-8") { out ->
			usedVocab.each { word ->
				out.println "${clusterOfWord(word)} 1 $word"
			}
		}
	}
	void saveSRILMClasses(File file) {
		saveSRILMClasses(file, clusterMap.keySet())
	}
	
	void saveVocab(File file, boolean includeRoot) {
		file.withWriter("UTF-8") { out ->
			if (includeRoot)
				out.println "ROOT"
			
			out.println MISC_CLUSTER
			
			(clusterMap.values() as Set).each {
				if (it == ':')
					out.println COLON_CLUSTER
				else
					out.println it
			}
		}
	}
	

	/**
	 * Substitute all words in inputFile with the corresponding cluster token specified in 
	 * clusterFile.
	 * Only include sentences with between minLin and maxLen tokens.
	 * @param args
	 */
	static main(args) {
		if (args[0] == '-extract-vocab') {
			extractVocab(args)
		}
		else {
			substitute(args)
		}
	}
	
	static substitute(String[] args) {
		def clusterFile = new File(args[0])
		def inputFile = new File(args[1])
		def outputFile = new File(args[2])
		def minLen = args.length < 4 ? 0 : args[3].toInteger()
		def maxLen = args.length < 5 ? Integer.MAX_VALUE : args[4].toInteger()
		def caseless = args.length < 6 ? false : (args[5] == "-cl")
		def debug = args.length < 7 ? false : (args[6] == "-debug")
		
		def clustering = new Clustering(clusterFile, caseless, true)
		
		outputFile.withWriter("UTF-8") { BufferedWriter out ->
			inputFile.eachLine("UTF-8") { line ->
				def words = line.split(/\s+/)
				if (words.length >= minLen && words.length <= maxLen) {
					def clusters = words.collect { clustering.clusterOfWord(it) }
					if (debug)
						out.println(line)
					out.println(clusters.join(" "))
				}
			}
		} 
	}
	
	static extractVocab(String[] args) {
		def clusterFile = new File(args[1])
		def vocabFile = new File(args[2])
		
		def clustering = new Clustering(clusterFile, true, true)
		clustering.saveVocab(vocabFile, true)
	}

}





