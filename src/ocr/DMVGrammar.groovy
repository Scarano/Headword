package ocr

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

class DMVGrammar {
	
	public static final String ROOT = 'ROOT'
	
	public static class Bigram implements Comparable<Bigram> {
		public String word1;
		public String word2;
		
		public Bigram(String word1, String word2) {
			this.word1 = word1;
			this.word2 = word2;
		}
		
		@Override public int hashCode() {
			return word1.hashCode() + 31*word2.hashCode();
		}
		
		@Override public boolean equals(Object other) {
			if (other == null)
				return false;
			
			if (!(other instanceof Bigram))
				return false;
			
			Bigram otherBigram = (Bigram) other;
			return word1.equals(otherBigram.word1) && word2.equals(otherBigram.word2);
		}
		
		@Override public int compareTo(Bigram other) {
			int result = word1.compareTo(other.word1)
			if (result == 0)
				result = word2.compareTo(other.word2)
			result
		}
		
		@Override public String toString() {
			return word1 + " " + word2;
		}
	}
	
	List<Map<String, Double>> stopContinue = new ArrayList<Map<String, Double>>()
	List<Map<Bigram, Double>> attach = new ArrayList<Map<Bigram, Double>>()
	
	DMVGrammar() {
		for (int i = 0; i < 8; i++)
			stopContinue << new HashMap<String, Double>()
		for (int i = 0; i < 2; i++)
			attach << new HashMap<Bigram, Double>()
	}
	
	DMVGrammar(String fileName) {
		this(new File(fileName))
	}
	
	DMVGrammar(DMVVector vect) {
		this()
		
		vect.getVocabulary().grep { it != ROOT }.each { word ->
			[0, 1].each { dir ->
				[false, true].each { hasChild ->
					[false, true].each { stop ->
						def val = vect.get(stop, word, dir, hasChild)
						if (val != Double.NEGATIVE_INFINITY)
							stopContinue[stopContinueIndex(dir==0, hasChild, stop)][word] = val
					}
				}
			}
		}
		for (String head: vect.getVocabulary()) {
			for (String arg: vect.getVocabulary().grep { it != ROOT }) {
				(head == ROOT ? [1] : [0, 1]).each { dir ->
					def val = vect.get(arg, head, dir)
					if (val != Double.NEGATIVE_INFINITY)
						attach[dir][new Bigram(head, arg)] = val
				}
			}
		}
	}
	
	DMVGrammar(File file) {
		this()
		
		file.eachLine("UTF-8") { String line ->
			def terms = line.split(/ +/)
			if (terms[0] == 'leftattach') {
				assert terms[2] == '<-' && terms[4] == ':'
				attach[0][new Bigram(terms[3], terms[1])] = terms[5].toDouble()
			}
			else if (terms[0] in 'rightattach') {
				assert terms[2] == '->' && terms[4] == ':'
				attach[1][new Bigram(terms[1], terms[3])] = terms[5].toDouble()
			}
			else if (terms[0] == 'root') {
				assert terms[2] == ':'
				attach[1][new Bigram(ROOT, terms[1])] = terms[3].toDouble()
			}
			else {
				assert terms[0] in ['leftstop', 'leftcontinue', 'rightstop', 'rightcontinue']
				assert terms[2] in ['haschild', 'nochild']
				assert terms[3] == ':'
				stopContinue[stopContinueIndex(
					terms[0].startsWith('left'),
					terms[2] == 'haschild',
					terms[0].endsWith('stop')
				)][terms[1]] = terms[4].toDouble()
			}
		}
	}
	
	void save(String fileName) {
		new File(fileName).withWriter("utf-8") { BufferedWriter out ->
			[false, true].each { boolean stop ->
				def stopStr = stop ? 'stop' : 'continue'
				[false, true].each { boolean left ->
					def dirStr = left ? 'left' : 'right'
					[false, true].each { boolean hasChild ->
						def childStr = hasChild ? 'haschild' : 'nochild'
						def map = stopContinue[stopContinueIndex(left, hasChild, stop)]
						map.each { Map.Entry<String, Double> entry ->
							def word = entry.getKey()
							def prob = entry.getValue()
							if (word != ROOT && prob != Double.NEGATIVE_INFINITY)
								out.println "$dirStr$stopStr $word $childStr : $prob"
						}
					}
				}
			}
			attach[0].each { Map.Entry<Bigram, Double> entry ->
				def bigram = entry.getKey()
				def prob = entry.getValue()
				if (prob != Double.NEGATIVE_INFINITY) {
					out.println "leftattach ${bigram.word2} <- ${bigram.word1} : $prob"
				}
			}
			attach[1].each { Map.Entry<Bigram, Double> entry ->
				def bigram = entry.getKey()
				def prob = entry.getValue()
				if (prob != Double.NEGATIVE_INFINITY) {
					if (bigram.word1 == ROOT)
						out.println "root ${bigram.word2} : $prob"
					else
						out.println "rightattach ${bigram.word1} -> ${bigram.word2} : $prob"
				}
			}
		}
	}
	
	double prob(String head, String arg, boolean left) {
		logZeroIfNull(attach[left ? 0 : 1][new Bigram(head, arg)])
	}
	double prob(String head, boolean left, boolean stop, boolean hasChild) {
		logZeroIfNull(stopContinue[(left ? 0 : 1) + (hasChild ? 2 : 0) + (stop ? 0 : 4)][head])
	}
	
	Vocabulary buildVocabulary() {
		Vocabulary vocab = new Vocabulary()
		vocab.add(ROOT)
		attach.each { Map<Bigram, Double> map ->
			map.keySet().each { Bigram bigram ->
				vocab.add(bigram.word1)
				vocab.add(bigram.word2)
			}
		}
		vocab.complete()
		vocab
	}

	DMVVector asVector() {
		asVector(buildVocabulary())
	}
	DMVVector asVector(Vocabulary vocab) {
		DMVVector vector = new DMVVector(vocab, Double.NEGATIVE_INFINITY)

		[false, true].each { boolean stop ->
			[false, true].each { boolean left ->
				[false, true].each { boolean hasChild ->
					stopContinue[stopContinueIndex(left, hasChild, stop)].each
					{ Map.Entry<String, Double> entry ->
						vector.set(stop, entry.getKey(), left ? 0 : 1, hasChild, entry.getValue())
					}
					vector.set(stop, ROOT, left ? 0 : 1, hasChild, 0.0D);
				}
			}
		}
		
		[0, 1].each { int dir ->
			attach[dir].each { Map.Entry<Bigram, Double> entry ->
				def bigram = entry.getKey()
				def val = entry.getValue()
				vector.set(bigram.word2, bigram.word1, dir, val)
			}
			vocab.wordStrings.each { word ->
				vector.set(ROOT, word, dir, 0.0D)
			}
		}
		vocab.wordStrings.each { word ->
			vector.set(word, ROOT, 0, 0.0D)
		}
		
		vector
	}
	
	static int stopContinueIndex(boolean left, boolean hasChild, boolean stop) {
		(left ? 0 : 1) + (hasChild ? 2 : 0) + (stop ? 0 : 4)
	}

	static double logZeroIfNull(Double d) {
		d == null ? Double.NEGATIVE_INFINITY : d
	}
	
	static void main(String[] args) {
		new DMVGrammar(new DMVGrammar(args[0]).asVector()).save(args[1])
	}

}





