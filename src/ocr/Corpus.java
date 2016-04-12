package ocr;

import java.util.ArrayList;
import java.util.List;

public class Corpus {
	
	public static class MatchedLine {
		public String input;
		public String output;
	}
	
	ArrayList<MatchedLine> lines;
	
	public Corpus(List<MatchedLine> lines) {
		this.lines = new ArrayList<MatchedLine>(lines);
	}

	public static void main(String[] args) {
	}
}
