package edu.neu.ccs.headword;


import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import edu.neu.ccs.headword.Alignment.Segment;

public interface SegmentModel {

	public double prob(Segment segment, String input, int i);
	
	public void print(PrintStream out);

	public void print(String filename) throws IOException;
	
	public void save(String filename) throws IOException;
	
	public static class UniformSegmentModel implements SegmentModel {
		
		double[][] segmentProbs = new double[][] {
			new double[3], new double[3], new double[3]
		};
		double reproduceProb;
		
		public UniformSegmentModel(
				double reproProb,
				double substProb,
				double deleteProb,
				double insertProb,
				double mergeProb,
				double splitProb)
		{
			segmentProbs[0][1] = Math.log(insertProb);
			segmentProbs[1][0] = Math.log(deleteProb);
			segmentProbs[1][1] = Math.log(substProb);
			segmentProbs[1][2] = Math.log(splitProb);
			segmentProbs[2][1] = Math.log(mergeProb);
			reproduceProb = reproProb;
		}
		
		@Override
		public double prob(Segment segment, String input, int i) {
			if (segment.input.equals(segment.output))
				return reproduceProb;
			return segmentProbs[segment.input.length()][segment.output.length()];
		}
		
		@Override
		public void print(PrintStream out) {
			out.println("p(rep) = " + Math.exp(reproduceProb));
			out.println("p(sub) = " + Math.exp(segmentProbs[1][1]));
			out.println("p(del) = " + Math.exp(segmentProbs[1][0]));
			out.println("p(ins) = " + Math.exp(segmentProbs[0][1]));
			out.println("p(mer) = " + Math.exp(segmentProbs[2][1]));
			out.println("p(spl) = " + Math.exp(segmentProbs[1][2]));
		}
		
		@Override
		public void print(String filename) throws IOException {
			print(new PrintStream(new FileOutputStream(filename)));
		}

		@Override
		public void save(String filename) {
			throw new Error("UniformSegmentModel.save() unimplemented");
		}
	}

}






