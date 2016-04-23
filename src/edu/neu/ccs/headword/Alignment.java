package edu.neu.ccs.headword;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class Alignment {
	
	// Only keep insertions of at most this many characters
	int maxInsertionSize = 3;
	// Only keep deletions of at most this many characters
	int maxDeletionSize = 3;
	// Only keep lines where at least this portion of characters align to themselves
	double minLineAlignmentScore = 0.5;
	
	static class Segment implements Serializable {
		private static final long serialVersionUID = -2848065190707080753L;

		String input;
		String output;
		
		public Segment(String input, String output) {
			this.input = input;
			this.output = output;
		}
		
		public boolean isIdentity() {
			return input.length() == 1 && input.equals(output);
		}
		
		@Override
		public int hashCode() {
			return 31*input.hashCode() + output.hashCode();
		}
		
		@Override
		public boolean equals(Object other) {
			Segment otherSegment = (Segment) other;
			return input.equals(otherSegment.input) && output.equals(otherSegment.output);
		}
		
		@Override
		public String toString() {
			return "<" + input + "/" + output + ">";
		}
	}
	public static class AlignedLine {
		public String input = null;
		public String output = null;
		public int[] alignment = null;
		public List<Segment> segments = null;
		double score = 0;
		
		public AlignedLine() {
		}
		
		public AlignedLine(String input, String output, List<Segment> segments) {
			this.input = input;
			this.output = output;
			this.segments = segments;
		}
		
		public void print(PrintStream out) {
			out.println(input);
			out.println(output);
			
			for (Segment segment: segments) {
				if (segment.input.equals("")) {
					for (int i = 0; i < segment.output.length(); i++)
						out.print("~");
				}
				else {
					out.print(segment.input);
					if (segment.output.length() > segment.input.length())
						out.print(" ");
				}
				out.print("_");
			}
			out.println();
			
			for (Segment segment: segments) {
				if (segment.output.equals("")) {
					for (int i = 0; i < segment.input.length(); i++)
						out.print("~");
				}
				else {
					out.print(segment.output);
					if (segment.output.length() < segment.input.length())
						out.print(" ");
				}
				out.print("_");
			}
			out.println();
		}
	}
	
	LinkedList<AlignedLine> lines;
	
	public Alignment(String file) throws Exception {
		BufferedReader reader =
			new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

		lines = new LinkedList<AlignedLine>();
		while (reader.ready()) {
			AlignedLine alignedLine = new AlignedLine();
			alignedLine.input = reader.readLine();
			alignedLine.output = reader.readLine();
//System.out.println("i: " + alignedLine.input);
//System.out.println("o: " + alignedLine.output);
			String outputLineNumber = reader.readLine();
			String[] alignmentStrings = reader.readLine().split("\\s+");
			reader.readLine(); // consume human-readable alignment output
			reader.readLine(); // consume human-readable alignment output
			reader.readLine(); // consume extra blank line
			
			if (outputLineNumber.equals("-1") || alignmentStrings[0].length() == 0)
				continue;

//System.out.println(Arrays.toString(alignmentStrings));
			int[] alignment = new int[alignmentStrings.length];
			alignedLine.alignment = alignment;
			for (int i = 0; i < alignment.length; i++)
				alignment[i] = Integer.parseInt(alignmentStrings[i]);
			
			alignedLine.segments = new LinkedList<Segment>();
			int j = 0; // position in output string
			for (int i = 0; i < alignment.length; i++) {
				if (alignment[i] == -1) {
					int deletionStart = i;
					while (i < alignment.length - 1 && alignment[i + 1] == -1) i++;
					alignedLine.segments.add(
							new Segment(alignedLine.input.substring(deletionStart, i + 1), ""));
				}
				else {
					if (alignment[i] > j) {
						alignedLine.segments.add(
								new Segment("", alignedLine.output.substring(j, alignment[i])));
						j = alignment[i];
					}
					alignedLine.segments.add(new Segment(
							alignedLine.input.substring(i, i+1), 
							alignedLine.output.substring(j, j+1)));
					j++;
				}
			}
			for (; j < alignedLine.output.length(); j++)
				alignedLine.segments.add(new Segment("", alignedLine.output.substring(j, j+1)));
			
			int unchangedCharacters = 0;
			for (Segment segment: alignedLine.segments) {
				if (segment.isIdentity())
					unchangedCharacters++;
			}
			alignedLine.score = (double) unchangedCharacters /
					Math.max(alignedLine.input.length(), alignedLine.output.length());
			
			lines.add(alignedLine);
		}
		reader.close();
	}

	public LinkedList<AlignedLine> lines() {
		return lines;
	}
	
	public LinkedList<AlignedLine> filterLines(
			double maxMismatchRate, double maxLengthError)
	{
		LinkedList<AlignedLine> results = new LinkedList<AlignedLine>();
line:	for (AlignedLine line: lines) {
//System.out.println("_____");
//System.out.println(line.input);
//System.out.println(line.output);
			int inputLength = line.input.length();
			int outputLength = line.output.length();
			
			if (inputLength < 4 || outputLength < 4)
				continue;

			int diff = Math.abs(inputLength - outputLength);
//System.out.println(diff + "; " + (maxLengthError*inputLength));
			if (diff > maxLengthError * inputLength)
				continue;
			
			int maxMismatches = (int) (maxMismatchRate * inputLength + 0.5);
			int mismatches = 0;
			for (Segment segment: line.segments) {
				if (!segment.input.equals(segment.output)) {
					mismatches++;
					if (mismatches > maxMismatches) {
//System.out.println(mismatches + " > " + maxMismatches);
						continue line;
					}
				}
			}
			
//System.out.println(mismatches + " <= " + maxMismatches);
			
			results.add(line);
		}
		return results;
	}
	
	public void print(PrintStream out) {
		for (AlignedLine line: lines) {
			line.print(out);
			out.println();
		}
	}
	
	public static void main(String[] args) {
		
		String alignmentFile = args[0];
		try {
			new Alignment(alignmentFile).print(System.out);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}













