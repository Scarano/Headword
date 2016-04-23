package edu.neu.ccs.headword

import groovy.transform.CompileStatic;

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import edu.neu.ccs.headword.DMVGrammar.Bigram
import edu.neu.ccs.headword.util.Util;

import static edu.neu.ccs.headword.DMVGrammar.stopContinueIndex

@CompileStatic
class DMVCounter {
	static final boolean DEBUG = false
	
	double totalDepEvents = 0D
	double totalStopEvents = 0D
	
	Map<String, double[]> stopContinue = new HashMap<String, double[]>()
	Map<String, double[]> stopContinueDenom = new HashMap<String, double[]>()
	Map<Bigram, double[]> attach = new HashMap<Bigram, double[]>()
	Map<String, double[]> attachDenom = new HashMap<String, double[]>()
	
	DMVCounter() {
	}
	
	private static double[] ensureValue(Map<String, double[]> map, String key, int n) {
		def value = map.get(key)
		if (value == null) {
			value = new double[n]
			for (int i = 0; i < n; i++)
				value[i] = Double.NEGATIVE_INFINITY
			map.put(key, value)
		}
		value
	}
	private static double[] ensureValue(Map<Bigram, double[]> map, Bigram key, int n) {
		def value = map.get(key)
		if (value == null) {
			value = new double[n]
			for (int i = 0; i < n; i++)
				value[i] = Double.NEGATIVE_INFINITY
			map.put(key, value)
		}
		value
	}
	
	private static int stopContinueIndex(boolean left, boolean hasChild) {
		stopContinueIndex(left, hasChild, true)
	}

	void add(String head, String arg, boolean left, double logCount) {
		if (DEBUG) {
			printf "p($arg | $head, ${left ? 'L' : 'R'}) += %s\n", logStr(logCount)
			totalDepEvents += Math.exp(logCount)
		}
		
		def array = ensureValue(attach, new Bigram(head, arg), 2)
		def index = left ? 0 : 1
		array[index] = Util.logSum(array[index], logCount)
		
		array = ensureValue(attachDenom, head, 2)
		array[index] = Util.logSum(array[index], logCount)
	}
	void add(String word, boolean left, boolean stop, boolean hasChild, double logCount) {
		if (DEBUG) {
			printf "p(${stop ? 'STOP' : 'CONT'} | $word, ${left ? 'L' : 'R'}, $hasChild) += %s\n",
				logStr(logCount)
			if (stop) totalStopEvents += Math.exp(logCount)
		}

		def array = ensureValue(stopContinue, word, 8)
		def index = stopContinueIndex(left, hasChild, stop)
		array[index] = Util.logSum(array[index], logCount)

		array = ensureValue(stopContinueDenom, word, 4)
		index = stopContinueIndex(left, hasChild)
		array[index] = Util.logSum(array[index], logCount)
	}
	
	void saveCounts(String file) {
		new File(file).withWriter("utf-8") { BufferedWriter writer ->
			stopContinue.keySet().sort().each { String head ->
				[0, 1].each { d ->
					[0, 1].each { int c ->
//						writer.println "stop $d $c $head\t" +
//							Math.exp(stopContinueDenom[head][stopContinueIndex(d==0, c==1)])
						[true, false].each { boolean stop ->
							writer.println([
								'stop',
								"$d $c $head",
								stop,
								Math.exp(stopContinue[head][stopContinueIndex(d==0, c==1, stop)])
							].join("\t"))
						}
					}
				}
			}
			
//			attachDenom.keySet().sort().each { String head ->
//				[0, 1].each { int d ->
//					writer.println "attach $d $head\t" + Math.exp(attachDenom[head][d])
//				}
//			}
			attach.keySet().sort().each { Bigram bigram ->
				[0, 1].each { int d ->
					writer.println([
						"attach-$d",
						bigram.word1,
						bigram.word2,
						Math.exp(attach[bigram][(int) d])
					].join("\t"))
				}
			}
		}
	}
	
	DMVGrammar createGrammar() {
		def grammar = new DMVGrammar()
		
		stopContinue.each() { String head, double[] counts ->
			double[] denomCounts = stopContinueDenom[head]
			for (int i = 0; i < 8; i++) {
				def j = i & 3
				if (counts[i] != Double.NEGATIVE_INFINITY)
					grammar.stopContinue[i][head] = counts[i] - denomCounts[j]
			}
		}
		attach.each() { Bigram bigram, double[] counts ->
			double[] denomCounts = attachDenom[bigram.word1]
			for (int i = 0; i < 2; i++) {
				if (counts[i] != Double.NEGATIVE_INFINITY)
					grammar.attach[i][bigram] = counts[i] - denomCounts[i]
			}
		}
		
		grammar
	}
	
	void print(PrintStream out) {
		stopContinue.keySet().sort().each { String head ->
			[0, 1].each { d ->
				[false, true].each { boolean hasChild ->
					def num = stopContinue[head][stopContinueIndex(d==0, hasChild, true)]
					def denom = stopContinueDenom[head][stopContinueIndex(d==0, hasChild)]
					out.printf("p(STOP | $head, $d, $hasChild) = %s = %s / %s\n",
						logStr(num - denom),
						logStr(num),
						logStr(denom))
				}
			}
		}
		
		attach.keySet().sort().each { Bigram bigram ->
			[0, 1].each { int d ->
				def num = attach[bigram][(int)d]
				def denom = attachDenom[bigram.word1][(int)d]
				out.printf("p(${bigram.word2} | ${bigram.word1}, $d) = %s = %s / %s\n",
					logStr(num - denom),
					logStr(num),
					logStr(denom))
			}
		}
		
		if (DEBUG) {
			out.println "total dependencies = $totalDepEvents; total stops = $totalStopEvents"
		}
	}
	
	static String logStr(double log) {
		String.format("%.2f (%.3f)", log, Math.exp(log))
	}
	
	static main(args) {
		
	}

}





