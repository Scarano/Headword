package edu.neu.ccs.headword;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import edu.neu.ccs.headword.Alignment.AlignedLine;
import edu.neu.ccs.headword.Alignment.Segment;

public class NaiveChannelModel {
	
	private static class Count {
		public double count = 1.0;
		public String toString() { return Double.toString(count); }
	}
	
	int maxSegmentLength;
	double totalSegments = 0.0;
	double totalInputChars = 0.0;
	HashMap<String, Count> inputCharCounts = new HashMap<String, Count>();
	double totalOutputChars = 0.0;
	HashMap<String, Count> outputCharCounts = new HashMap<String, Count>();
	HashMap<String, HashMap<String, Count>> segmentCounts =
		new HashMap<String, HashMap<String, Count>>();
	
	HashMap<String, Double> inputProbs = null;
	HashMap<String, Double> outputProbs = null;
	HashMap<Segment, Double> segmentProbs = null;

	/**
	 * 
	 * @param maxSegmentLength
	 * 		Do not consider insertions/deletions of more than this number of characters.
	 * 		Note that the current implementation won't actually work if maxSegmentLength &gt; 1.
	 */
	public NaiveChannelModel(int maxSegmentLength) {
		this.maxSegmentLength = maxSegmentLength;
	}
	
	public void addLine(AlignedLine line) {
		for (Segment segment: line.segments) {
			if (
					segment.input.length() > maxSegmentLength ||
					segment.output.length() > maxSegmentLength
			) {
				continue;
			}
			
			totalSegments++;

			totalInputChars += segment.input.length();
			
			Count charCount = inputCharCounts.get(segment.input);
			if (charCount == null)
				inputCharCounts.put(segment.input, new Count());
			else
				charCount.count++;
			
			totalOutputChars += segment.output.length();

			charCount = outputCharCounts.get(segment.output);
			if (charCount == null)
				outputCharCounts.put(segment.output, new Count());
			else
				charCount.count++;
			
			HashMap<String, Count> outputCounts = segmentCounts.get(segment.input);
			if (outputCounts == null) {
				outputCounts = new HashMap<String, Count>();
				segmentCounts.put(segment.input, outputCounts);
			}
			Count outputCount = outputCounts.get(segment.output);
			if (outputCount == null)
				outputCounts.put(segment.output, new Count());
			else
				outputCount.count++;
		}
	}
	
	public void computeModel() {
		inputProbs = new HashMap<String, Double>();
		for (String input: inputCharCounts.keySet())
			inputProbs.put(input, Math.log(inputCharCounts.get(input).count / totalInputChars));

		outputProbs = new HashMap<String, Double>();
		for (String output: outputCharCounts.keySet())
			outputProbs.put(output, Math.log(outputCharCounts.get(output).count / totalInputChars));
		
		segmentProbs = new HashMap<Segment, Double>();
		for (String input: segmentCounts.keySet()) {
			Map<String, Count> outputCounts = segmentCounts.get(input);
			for (String output: outputCounts.keySet()) {
				segmentProbs.put(new Segment(input, output),
						Math.log(outputCounts.get(output).count / totalSegments));
			}
		}
	}
	
	public double segmentProb(String input, String output) {
		Double p = segmentProbs.get(new Segment(input, output));
		if (p == null)
			return Double.NEGATIVE_INFINITY;
		else
			return p;
	}
	
	static class scoredCandidate<T> {
		public T value;
		public double score;
		
		public scoredCandidate(T value, double score) {
			this.value = value;
			this.score = score;
		}
		
		public String toString() {
			return value.toString() + " [" + score + " ]";
		}
	}
	
	static class CandidateComparator<T> implements Comparator<scoredCandidate<T>> {
		@Override
		public int compare(scoredCandidate<T> a, scoredCandidate<T> b) {
			return (a.score < b.score) ? 1 : -1;
		}
	}
	
	public void printStats() {
		PrintStream out = System.out;
		
		out.println("totalInputChars = " + totalInputChars);
		out.println("inputCharCounts: " + inputCharCounts);
		out.println("totalOutputChars = " + totalOutputChars);
		out.println("outputCharCounts: " + outputCharCounts);
		out.println("segmentCounts: " + segmentCounts);
		out.println("inputProbs: " + inputProbs);
		out.println("outputProbs: " + outputProbs);
		out.println("outputGivenInputProbs: " + segmentProbs);
	}
	
	public void printTopCandidates() {
		PrintStream out = System.out;
		
		ArrayList<Map.Entry<String, Count>> outputCharsAndCounts =
			new ArrayList<Map.Entry<String, Count>>(outputCharCounts.entrySet());
		Collections.sort(outputCharsAndCounts, new Comparator<Map.Entry<String, Count>>() {
			public int compare(Entry<String, Count> a, Entry<String, Count> b) {
				return (a.getValue().count < b.getValue().count) ? 1 : -1;
			}
		});
		String[] outputChars = new String[outputCharsAndCounts.size()];
		for (int i = 0; i < outputChars.length; i++)
			outputChars[i] = outputCharsAndCounts.get(i).getKey();
		for (String output: outputChars) {
			ArrayList<scoredCandidate<String>> candidates =
				new ArrayList<scoredCandidate<String>>();
			for (String input: inputCharCounts.keySet())
				candidates.add(new scoredCandidate<String>(input, segmentProb(input, output)));
			Collections.sort(candidates, new CandidateComparator<String>());
			
			out.print(output + ": ");
			for (int i = 0; i < 4; i++) {
				out.print(candidates.get(i).value + "[" + candidates.get(i).score + "] ");
			}
			out.println();
		}
	}
	
	public static void main(String[] args) {
		String alignmentFile = args[0];
		try {
			Alignment alignment = new Alignment(alignmentFile);
//alignment.print(System.out);

			NaiveChannelModel model = new NaiveChannelModel(1);

			for (AlignedLine line: alignment.lines) {
//System.out.println(line.score);
				if (line.score > 0.75) {
					model.addLine(line);
				}
			}
			
			model.computeModel();
//model.printStats();

			model.printTopCandidates();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}
