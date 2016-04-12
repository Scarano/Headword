package ocr;

import ocr.NGramModel.TokenHistory;

public class Chart {

	public static interface Node {
		public int getStart();
		public int getEnd();
		public double getLogProb();
		public Node getLHS();
		public Node getRHS();
	}
	
	public static class NGramNode implements Node {
		int start, end;
		double logProb;
		NGramNode lhs, rhs;
		TokenHistory history;
		
		public NGramNode(NGramNode lhs, NGramNode rhs, double logProb) {
			this.start = lhs.start;
			this.end = rhs.end;
			this.logProb = logProb;
			this.lhs = lhs;
			this.rhs = rhs;
			// Note that this only makes sense if right-branching (rhs is depth-0)
			this.history = lhs.history.add(rhs.history.token);
		}

		@Override public int getStart() { return start; }

		@Override public int getEnd() { return end; }

		@Override public double getLogProb() { return logProb; }

		@Override public Node getLHS() { return lhs; }

		@Override public Node getRHS() { return rhs; }
	}
	
	
	
	public Chart(String[] tokens) {
		
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
