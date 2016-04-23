package ocr;

import static ocr.util.Util.logSum;

import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

import ocr.Alignment.AlignedLine;
import ocr.Alignment.Segment;
import ocr.util.Counter;
import ocr.util.RunConfig;

public class SegmentAligner {
	
	public final boolean ALLOW_MERGE_SPLIT = false;
	
	static class Candidate {
		Cell cell;
		Segment segment;
		double prob = Double.NEGATIVE_INFINITY; // Will be set by forward-backward
		double segmentProb;
		double bestPartialPathProb;
		
		public Candidate(Cell cell, Segment segment,
				double segmentProb, double bestPartialPathProb)
		{
			this.cell = cell;
			this.segment = segment;
			this.segmentProb = segmentProb;
			this.bestPartialPathProb = bestPartialPathProb;
		}
		
		public void print(PrintStream out) {
			print(out, null);
		}
		
		public void print(PrintStream out, Candidate best) {
			String margin = this == best ? "* " : "  ";
			System.out.println(margin + segment + ": "
					+ Math.exp(prob) + " (" + prob + ") / "
					+ Math.exp(segmentProb));
		}
	}
	
	static class Cell {
		int i, j;
		double prob = Double.NEGATIVE_INFINITY; // will be set by forward-backward
		double bestPartialPathProb = Double.NEGATIVE_INFINITY;
		Candidate bestPartialPathCandidate = null;
		LinkedList<Candidate> candidates = new LinkedList<Candidate>();
		List<List<Candidate>> subspanPaths = null;
		
		public Cell(int i, int j) {
			this.i = i; this.j = j;
		}
		
		public void addCandidate(Candidate candidate) {
			if (candidate.bestPartialPathProb > bestPartialPathProb
					|| bestPartialPathCandidate == null)
			{
				bestPartialPathCandidate = candidate;
				bestPartialPathProb = candidate.bestPartialPathProb;
			}
			candidates.add(candidate);
		}
		
		public void print(PrintStream out) {
			out.println(i + ", " + j);
			out.println(Math.exp(prob) + " (" + prob + ") / "
					+ Math.exp(bestPartialPathProb) + " (" + bestPartialPathProb + ")");
			for (Candidate candidate: candidates)
				candidate.print(out, bestPartialPathCandidate);
			out.println();
		}
	}
	
	static class CellRow {
		final int startIndex;
		final int endIndex;
		final Cell[] cells;
		
		public CellRow(int startIndex, int endIndex) {
			this.startIndex = startIndex;
			this.endIndex = endIndex;
			cells = new Cell[endIndex - startIndex];
		}
		
		public boolean inRange(int j) {
			return j >= startIndex && j < endIndex;
		}
		
		public Cell get(int j) {
			return cells[j - startIndex];
		}
		
		public void put(int j, Cell cell) {
			cells[j - startIndex] = cell;
		}
	}

	private static Integer defaultSearchBeamWidth = null;
	private static int getSearchBeamWidth(RunConfig config) {
		if (defaultSearchBeamWidth == null) {
			try {
				defaultSearchBeamWidth = config.getInt("channel.beam-width", 3);
			} catch (IOException e) {}
		}
		return defaultSearchBeamWidth;
	}

	int searchBeamWidth;
	SegmentModel model;
	String input;
	String output;
	CellRow[] table;

	public SegmentAligner(SegmentModel model, String input, String output, RunConfig config) {
		this(model, input, output, getSearchBeamWidth(config));
	}
	public SegmentAligner(
			SegmentModel model, String input, String output, int searchBeamWidth) {
		this.searchBeamWidth = searchBeamWidth;
		this.model = model;
		this.input = input;
		this.output = output;
		table = new CellRow[input.length()+1];
		for (int i = 0; i < table.length; i++) {
			int beamStart = (int) (i * (float) output.length() / input.length()
					- (float) searchBeamWidth/2 + 0.5);
			int beamEnd = beamStart + searchBeamWidth;
			int jStart = Math.max(0, beamStart);
			int jEnd = Math.min(output.length() + 1, beamEnd);
			table[i] = new CellRow(jStart, jEnd);
			
//			int j = 0;
//			for (; j < jStart; j++)
//				System.out.print("-");
//			for (; j < jEnd; j++)
//				System.out.print("x");
//			for (; j < output.length() + 1; j++)
//				System.out.print("-");
//			System.out.println();
		}
	}
	
	public static SegmentModel reestimate(List<AlignedLine> lines, SegmentModel model,
			PrintStream log, int searchBeamWidth, double[] avgLikelihood)
	{
		Counter<Segment> softCounts = new Counter<Segment>();

		double likelihood = 0.0;
		double chars = 0.0;
		
		int numLines = 0;
		for (AlignedLine line: lines) {
			numLines ++;
			chars += line.input.length();
			
			SegmentAligner aligner =
				new SegmentAligner(model, line.input, line.output, searchBeamWidth);
			aligner.populate();
			likelihood += aligner.forwardBackward();
			aligner.addSoftCounts(softCounts);
			
			if (numLines % 1000 == 0)
				System.out.println("Trained on " + numLines + " lines.");

			if (log != null) {
				line.print(log);
				log.println();
				log.println("alignment table:");
				aligner.print(log);
				log.println("soft counts:");
				softCounts.print(log);
				log.println();
			}
		}
		
		printSoftCountDebugInfo(softCounts);
		
		avgLikelihood[0] = likelihood / chars;
		
		return new MergeSplitSegmentModel(lines, softCounts);
	}
	
	static void printSoftCountDebugInfo(Counter<Segment> softCounts) {
	}
	
	public void populate() {
		table[0].put(0, new Cell(0, 0));
		table[0].get(0).bestPartialPathProb = 0;
		
		for (int i = 0; i < input.length() + 1; i++) {
			CellRow row = table[i];
			for (int j = row.startIndex; j < row.endIndex; j++) {
				if (i > 0 || j > 0)
					row.put(j, new Cell(i, j));
				
				// Substitute
				addToTable(i, j, 1, 1);
				// Insert
				addToTable(i, j, 0, 1);
				// Delete
				addToTable(i, j, 1, 0);
				if (ALLOW_MERGE_SPLIT) {
					// Split
					addToTable(i, j, 1, 2);
					// Merge
					addToTable(i, j, 2, 1);
				}
			}
		}
	}

	private void addToTable(int i, int j, int inputChunkSize, int outputChunkSize) {
		if (i - inputChunkSize < 0 || j - outputChunkSize < 0)
			return;
		
		if (!table[i - inputChunkSize].inRange(j - outputChunkSize))
			return;
		Cell fromCell = table[i - inputChunkSize].get(j - outputChunkSize);
		
		Segment segment = new Segment(
				input.substring(i - inputChunkSize, i),
				output.substring(j - outputChunkSize, j));
		
		double prob = model.prob(segment, input, i - inputChunkSize);
		double bestPartialPathProb = prob + fromCell.bestPartialPathProb;

		Cell cell = table[i].get(j);
		cell.addCandidate(new Candidate(cell, segment, prob, bestPartialPathProb));
	}

	public AlignedLine align() {
		LinkedList<Segment> optimalPath = new LinkedList<Segment>();
		int i = input.length();
		int j = output.length();
		while (i > 0 || j > 0) {
			Candidate candidate = table[i].get(j).bestPartialPathCandidate;
			Segment segment = candidate.segment;
			optimalPath.addFirst(segment);
			i -= segment.input.length();
			j -= segment.output.length();
		}
		
		return new AlignedLine(input, output, optimalPath);
	}
	
	public double probOfBestAlignment() {
		return table[input.length()].get(output.length()).bestPartialPathProb;
	}
	
	double forwardBackward() {
		double forwProb[][] = new double[input.length() + 1][];
		double backProb[][] = new double[input.length() + 1][];
		for (int i = 0; i < input.length() + 1; i++) {
			forwProb[i] = new double[output.length() + 1];
			backProb[i] = new double[output.length() + 1];
			for (int j = 0; j < output.length() + 1; j++) {
				backProb[i][j] = Double.NEGATIVE_INFINITY;
			}
		}
		
		forwProb[0][0] = 0;
		for (int i = 0; i < input.length() + 1; i++) {
			CellRow row = table[i];
			for (int j = row.startIndex; j < row.endIndex; j++) {
				if (i == 0 && j == 0)
					continue;
				
				double p = Double.NEGATIVE_INFINITY;
				Cell cell = row.get(j);
				for (Candidate candidate: cell.candidates) {
					int iStart = i - candidate.segment.input.length();
					int jStart = j - candidate.segment.output.length();
					p = logSum(p, candidate.segmentProb + forwProb[iStart][jStart]); 
				}
				forwProb[i][j] = p;
			}
		}

		backProb[input.length()][output.length()] = 0;
		for (int i = input.length(); i >= 0; i--) {
			CellRow row = table[i];
			for (int j = row.endIndex - 1; j >= row.startIndex; j--) {
				Cell cell = row.get(j);
				double cellBackProb = backProb[i][j];
				for (Candidate candidate: cell.candidates) {
					int iStart = i - candidate.segment.input.length();
					int jStart = j - candidate.segment.output.length();
					backProb[iStart][jStart] = logSum(backProb[iStart][jStart],
							cellBackProb + candidate.segmentProb);
//System.out.println("backProb[" + iStart + "][" + jStart + "] = " + backProb[iStart][jStart]);
				}
			}
		}
		
		double totalProb = backProb[0][0];
//System.out.println("forward prob = " + Math.exp(forwProb[input.length()][output.length()]));
//System.out.println("backward prob = " + Math.exp(backProb[0][0]));

		for (int i = 0; i < input.length() + 1; i++) {
			CellRow row = table[i];
			for (int j = row.startIndex; j < row.endIndex; j++) {
				row.get(j).prob = forwProb[i][j] + backProb[i][j] - totalProb;
				for (Candidate candidate: row.get(j).candidates) {
					int iStart = i - candidate.segment.input.length();
					int jStart = j - candidate.segment.output.length();
					double originProb = forwProb[iStart][jStart];
					candidate.prob = 
						originProb + candidate.segmentProb + backProb[i][j] - totalProb;
				}
			}
		}
		
		return totalProb;
	}
	
	public void addSoftCounts(Counter<Segment> softCounts) {
		for (int i = 0; i < input.length() + 1; i++) {
			CellRow row = table[i];
			for (int j = row.startIndex; j < row.endIndex; j++) {
				for (Candidate candidate: row.get(j).candidates) {
//System.out.println(candidate.segment + ": " + Math.exp(candidate.prob));
					softCounts.add(candidate.segment, Math.exp(candidate.prob));
				}
			}
		}
	}
	
	/**
	 * Break the list of candidates in the optimal path into "subspans" such that
	 * each subspan ends with an input/output match. If we assume the alignment
	 * is correct where the input and output characters match, then we can assume
	 * that all uncertainty about the path correctness is within each subspan, so
	 * each can be analyzed independently.
	 */
	static List<List<Candidate>> subspans(List<Candidate> path) {
		List<List<Candidate>> subspans = new LinkedList<List<Candidate>>();
		List<Candidate> subspan = new LinkedList<Candidate>();
		for (Candidate candidate: path) {
			subspan.add(candidate);
			if (candidate.segment.input.equals(candidate.segment.output)) {
				subspans.add(subspan);
				subspan = new LinkedList<Candidate>();
			}
		}
		if (subspan.size() > 0)
			subspans.add(subspan);
		return subspans;
	}
	
	public void print(PrintStream out) {
		for (int i = 0; i < input.length() + 1; i++) {
			CellRow row = table[i];
			for (int j = row.startIndex; j < row.endIndex; j++) {
				row.get(j).print(out);
			}
		}
	}
	
//	static class Path {
//		Candidate candidate;
//		Path prev;
//
//		public Path(Candidate candidate, Path prev) {
//			this.candidate = candidate;
//			this.prev = prev;
//		}
//	}
//	
//	public List<Path> subspanPaths(int i, int j, int iStart, int jStart) {
//		
//		Cell cell = table[i][j];
//		if (cell.subspanPaths != null)
//			return cell.subspanPaths;
//		
//		List<List<Candidate>> paths = new LinkedList<List<Candidate>>();
//		
//		if (i == iStart && j == jStart) {
//			return new 
//		}
//		
//		for (Candidate candidate: table[i][j].candidates) {
//			int iPrev = i - candidate.segment.input.length();
//			int jPrev = j - candidate.segment.output.length();
//			if (iPrev < iStart || jPrev < jStart)
//				continue;
//			Cell prevCell = table[iPrev][jPrev];
//			if (prevCell == null)
//				continue;
//			
//			List<List<Candidate>> prevPaths = subspanPaths(iPrev, jPrev, iStart, jStart);
//			
//			for (List<Candidate> prevPath: prevPaths) {
//				List<Candidate> path = prevPath.
//				paths.add
//		}
//		
//		return paths;
//	}
//	
//	public void assignProbabilitiesWithinSubspan(List<Segment> subspan) {
//		// (log of) sum of path probabilities; divide by this to normalize.
//		double Z = Double.NEGATIVE_INFINITY;
//		
//		
//	}
	
	public static void main(String[] args) {
		String trainFile = args[0];
		String modelFilePrefix = args[1];
		int T = Integer.parseInt(args[2]);
		String testFile = null;
		if (args.length > 3) testFile = args[3];
		int searchBeamWidth = 15;
		
		try {
			Alignment trainAlignment = new Alignment(trainFile);
			Alignment testAlignment = null;
			if (testFile != null)
				testAlignment = new Alignment(testFile);
			
			LinkedList<AlignedLine> goodTraining = trainAlignment.filterLines(0.1, .05);
//goodTraining = alignment.lines;
			
			System.out.println("Training on " + goodTraining.size() + " lines " +
					"(from original " + trainAlignment.lines.size() + ").");
			
			int numTokens = 0;
			for (AlignedLine line: goodTraining)
				numTokens += line.input.split(" +").length;
			System.out.println("Number of transcription tokens: " + numTokens);
				
			SegmentModel[] models = new SegmentModel[T+1];
			models[0] = new SegmentModel.UniformSegmentModel(
					0.9, 0.001, 0.001, 0.001, 0.0, 0.0);

			SegmentModel[] smoothedModels = new SegmentModel[T+1];
			smoothedModels[0] = models[0];

			double[] avgLikelihood = new double[] {0.0};
			double lastAvgLikelihood = 0.1;
			for (int t = 1; t < T + 1; t++) {
//System.out.println("____________________________________");
//System.out.println("t = " + t);
				models[t] = SegmentAligner.reestimate(
						goodTraining, smoothedModels[t-1], null /*System.out */, searchBeamWidth,
						avgLikelihood);
				models[t].save(modelFilePrefix + "." + t + ".javaobj");
				models[t].print(modelFilePrefix + "." + t + ".txt");
				smoothedModels[t] = new SmoothSegmentModel(models[t]);
				System.out.println("Trained model " + t);
				
				double improvement = 
						(Math.exp(avgLikelihood[0]) - lastAvgLikelihood) / lastAvgLikelihood;
				System.out.printf("avg likelihood = %f (log: %f); improvement = %f\n", 
						Math.exp(avgLikelihood[0]), avgLikelihood[0], improvement);
				lastAvgLikelihood = Math.exp(avgLikelihood[0]);
				
				if (Math.abs(improvement) < 1e-5)
					break;
				
//System.out.println("Model:");
//models[t].print(System.out);
			}

			if (testAlignment != null) {
				for (AlignedLine line: testAlignment.lines) {
					System.out.println("Original alignment:");
					line.print(System.out);
					for (int t = 1; t < T + 1; t++) {
						System.out.println("Model " + t + ":");
						SegmentAligner aligner = 	new SegmentAligner(
								smoothedModels[t], line.input, line.output, searchBeamWidth);
						aligner.populate();
						AlignedLine lineAlignment = aligner.align();
						lineAlignment.print(System.out);
						System.out.println();
					}
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}



