package ocr;

import static ocr.util.Util.nvl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ocr.Alignment.AlignedLine;
import ocr.Alignment.Segment;
import ocr.util.Counter;
import ocr.util.RunConfig;
import ocr.util.Counter.Count;

public class MergeSplitSegmentModel implements SegmentModel {

	HashMap<String, Double> mergeProbs = new HashMap<String, Double>();
	HashMap<String, Double> nonmergeProbs = new HashMap<String, Double>();
	HashMap<Segment, Double> segmentProbs = new HashMap<Segment, Double>();
	
	public MergeSplitSegmentModel(List<AlignedLine> lines, Counter<Segment> segmentCounts) {
		// Count the number of times each unigram and bigram are seen in the text
		double totalInputCharacters = 0;
		Counter<String> inputCounts = new Counter<String>();
		for (AlignedLine line: lines) {
			for (int i = 0; i < line.input.length(); i++)
				inputCounts.increment(line.input.substring(i, i + 1));
			for (int i = 0; i < line.input.length() - 1; i++)
				inputCounts.increment(line.input.substring(i, i + 2));
			totalInputCharacters += line.input.length();
		}
		
		// For each bigram, count the number of merges
		Counter<String> merges = new Counter<String>();
		for (Entry<Segment, Count> segmentCount: segmentCounts.entries()) {
			Segment segment = segmentCount.getKey();
			if (segment.input.length() == 2)
				merges.add(segment.input, segmentCount.getValue().value);
		}
		// Store the probability of merging each bigram
		for (Entry<String, Count> mergeCount: merges.entries()) {
			String bigram = mergeCount.getKey();
			double mergeProb = mergeCount.getValue().value / inputCounts.get(bigram);
			if (!Double.isNaN(mergeProb)) {
				mergeProbs.put(bigram, Math.log(mergeProb));
				nonmergeProbs.put(bigram, Math.log(1 - mergeProb));
			}
		}
		
		// Calculate number of times each segment's input string is seen:
		Counter<String> segmentInputCounts = new Counter<String>();
		for (Entry<Segment, Count> segmentCount: segmentCounts.entries()) {
			segmentInputCounts.add(segmentCount.getKey().input,
					segmentCount.getValue().value);
		}

		// Store conditional probabilities (output given input) for each segment
		for (Entry<Segment, Count> segmentCount: segmentCounts.entries()) {
			Segment segment = segmentCount.getKey();
			double jointCount = segmentCount.getValue().value;
			double inputCount = segment.input.length() == 0
				? totalInputCharacters
				: segmentInputCounts.get(segment.input);
			if (inputCount > 0.0)
				segmentProbs.put(segment, Math.log(jointCount / inputCount));
		}
	}
	
	@Override
	public double prob(Segment segment, String input, int i) {
		if (segment.input.length() == 0) {
			return nvl(segmentProbs.get(segment), Double.NEGATIVE_INFINITY);
		}
		else {
			Double prob = nvl(segmentProbs.get(segment), Double.NEGATIVE_INFINITY);
			
			// Unless this is the last character of the line also model the decision of
			// whether to merge or not.
			if (i < input.length() - 1) {
				if (segment.input.length() == 2)
					prob += nvl(mergeProbs.get(segment.input), Double.NEGATIVE_INFINITY);
				else if (segment.input.length() == 1)
					prob += nvl(nonmergeProbs.get(input.substring(i, i + 2)), 0.0);
			}
			
			return prob;
		}
	}
	
	@Override
	public void print(PrintStream out) {
		// Build map from each possible input to all the segment / prob pairs for that input
		Map<String, Map<Segment, Double>> probsByInput = 
			new HashMap<String, Map<Segment, Double>>();
		for (Entry<Segment, Double> segmentProb: segmentProbs.entrySet()) {
			String input = segmentProb.getKey().input;
			Map<Segment, Double> inputMap = probsByInput.get(input);
			if (inputMap == null) {
				inputMap = new HashMap<Segment, Double>();
				probsByInput.put(input, inputMap);
			}
			inputMap.put(segmentProb.getKey(), segmentProb.getValue());
		}
		
		// sort the list of all inputs
		String[] inputs = probsByInput.keySet().toArray(new String[0]);
		Arrays.sort(inputs);
		
		for (String input: inputs) {
			double mergeProb = nvl(mergeProbs.get(input), Double.NEGATIVE_INFINITY);
			out.println("p(<merge>|" + input + ") = " + Math.exp(mergeProb) + 
					" (log = " + mergeProb + ")");
			Map<Segment, Double> inputMap = probsByInput.get(input);
			double sum = 0;
			
			ArrayList<Entry<Segment, Double>> segmentCounts = 
				new ArrayList<Entry<Segment, Double>>(inputMap.entrySet());
			Collections.sort(segmentCounts, new Comparator<Entry<Segment, Double>>() {
				public int compare(Entry<Segment, Double> a, Entry<Segment, Double> b) {
					return -a.getValue().compareTo(b.getValue());
				}
			});
			for (Entry<Segment, Double> segmentCount: segmentCounts) {
				String output = segmentCount.getKey().output;
				double logProb = segmentCount.getValue();
				double prob = Math.exp(logProb);
				sum += prob;
				out.println("p(" + output + "|" + input + ") = " + prob +
						" (log = " + logProb + ")");
			}
			out.println("sum = " + sum);
			out.println();
		}
	}

	@Override
	public void print(String filename) throws IOException {
		print(new PrintStream(new FileOutputStream(filename)));
	}

	@Override
	public void save(String filename) throws IOException {
		ObjectOutputStream out = new ObjectOutputStream(
				new FileOutputStream(filename));
		out.writeObject(mergeProbs);
		out.writeObject(nonmergeProbs);
		out.writeObject(segmentProbs);
		out.close();
	}
	
	public MergeSplitSegmentModel(RunConfig config) throws IOException, ClassNotFoundException {
		this(config.getDataFile("channel.model"));
	}

	@SuppressWarnings("unchecked")
	public MergeSplitSegmentModel(File modelFile)
		throws IOException, ClassNotFoundException
	{
		ObjectInputStream in = new ObjectInputStream(new FileInputStream(modelFile));
		mergeProbs = (HashMap<String, Double>) in.readObject();
		nonmergeProbs = (HashMap<String, Double>) in.readObject();
		segmentProbs = (HashMap<Segment, Double>) in.readObject();
		in.close();
	}
}





















