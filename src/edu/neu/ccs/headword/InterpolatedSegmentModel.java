package edu.neu.ccs.headword;

import java.io.IOException;
import java.io.PrintStream;

import edu.neu.ccs.headword.Alignment.Segment;
import edu.neu.ccs.headword.util.RunConfig;

public class InterpolatedSegmentModel implements SegmentModel {
	
	SegmentModel foregroundModel;
	SegmentModel backgroundModel;
	double lambda;
	
	public InterpolatedSegmentModel(SegmentModel fg, SegmentModel bg, double lambda) {
		this.foregroundModel = fg;
		this.backgroundModel = bg;
		this.lambda = lambda;
	}

	public InterpolatedSegmentModel(RunConfig config, SegmentModel fg) 
			throws IOException, ClassNotFoundException
	{
		this.foregroundModel = fg;
		
		double substProb = config.getDouble("channel.smoothing.subst-prob");
		double reproProb = config.getDouble("channel.smoothing.repro-prob");
		double insProb = substProb * config.getDouble("channel.smoothing.ins-prob-factor");
		double delProb = substProb * config.getDouble("channel.smoothing.del-prob-factor");
		this.backgroundModel = new UniformSegmentModel(
				reproProb, substProb, insProb, delProb, 0.0, 0.0);
		
		this.lambda = config.getDouble("channel.smoothing.lambda");
	}

	@Override
	public double prob(Segment segment, String input, int i) {
		return Math.log(lambda * Math.exp(foregroundModel.prob(segment, input, i)) +
			(1.0-lambda) * Math.exp(backgroundModel.prob(segment, input, i)));
	}

	@Override
	public void print(PrintStream out) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void print(String filename) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void save(String filename) throws IOException {
		// TODO Auto-generated method stub
		
	}

}
