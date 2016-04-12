package ocr;

import java.io.IOException;
import java.io.PrintStream;

import ocr.Alignment.Segment;

public class SmoothSegmentModel implements SegmentModel {
	static final double LOG_MIN_PROB = -50;
	static final double MIN_PROB = Math.exp(LOG_MIN_PROB);
	
	SegmentModel jaggedModel;
	
	public SmoothSegmentModel(SegmentModel jaggedModel) {
		this.jaggedModel = jaggedModel;
	}

	@Override
	public double prob(Segment segment, String input, int i) {
		double prob = jaggedModel.prob(segment, input, i);
		return Math.log(Math.exp(prob) + MIN_PROB);
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
