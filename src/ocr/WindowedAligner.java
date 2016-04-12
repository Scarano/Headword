package ocr;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;

import ocr.util.CommandLineParser;

public class WindowedAligner {
	
	static final boolean DEBUG = true;
	static final int MAX_CELLS = 2000;
	
	static enum Operation {
		SUBST, DELETE, INSERT
	}
		
	static final float SUBST_COST = 1;
	static final float DELETE_COST = 1;
	static final float INSERT_COST = 1;
	
	static class Cell {
		public float cost;
		public Cell(float cost) { this.cost = cost; }
	}
	
	static class Chart {
		int w, h;
		ArrayList<AnchoredArray<float[]>> rows;
		
		public Chart(int width, int height) {
			w = width; h = height;
			rows = new ArrayList<AnchoredArray<float[]>>(h);
			for (int y = 0; y < h; y++)
				rows.add(new AnchoredArray<float[]>());
		}

		public void set(int x, int y, float[] val) {
			rows.get(y).set(x, val);
		}
		public float[] get(int x, int y) {
			return rows.get(y).get(x);
		}
		
		public float cost(int x, int y) {
			if (y == -1) {
				if (x == -1)
					return 0f;
				else
					return (x+1)*INSERT_COST;
			}
			else {
				if (x == -1)
					return (y+1)*DELETE_COST;
				float[] cell = get(x, y);
				if (cell == null)
					return Float.POSITIVE_INFINITY;
				else
					return cell[0];
			}
		}
		
		public Operation operation(int x, int y, float substCost) {
			Operation op = Operation.SUBST;
			float cost = cost(x-1, y-1) + substCost;
			
			float deleteCost = cost(x, y-1) + DELETE_COST;
			if (deleteCost < cost) {
				op = Operation.DELETE;
				cost = deleteCost;
			}

			float insertCost = cost(x-1, y) + INSERT_COST;
			if (insertCost < cost) {
				op = Operation.INSERT;
				cost = insertCost;
			}
			
			return op;
		}

		public float[] populate(int x, int y, float substCost) {
			float cost = cost(x-1, y-1) + substCost;
			cost = Math.min(cost, cost(x, y-1) + DELETE_COST);
			cost = Math.min(cost, cost(x-1, y) + INSERT_COST);
			float[] cell = new float[] {cost};
			set(x, y, cell);
			return cell;
		}
		
	}

	String input;
	String output;
	Chart chart;
	float searchLimit;
	float costLimit;
	
	public WindowedAligner(String input, String output) {
		this(input, output, Float.POSITIVE_INFINITY);
	}
	public WindowedAligner(String input, String output, float searchLimit) {
		this(input, output, searchLimit, Float.POSITIVE_INFINITY);
	}
	public WindowedAligner(String input, String output, float searchLimit, float costLimit) {
		this.input =input;
		this.output = output;
		this.chart = new Chart(output.length(), input.length());
		this.searchLimit = searchLimit;
		this.costLimit = costLimit;
	}
	
	public static boolean editDistanceBelow(String a, String b, float threshold) {
		String input, output;
		if (a.length() > b.length()) {
			input = a;
			output = b;
		} else {
			input = b;
			output = a;
		}
		
		float maxCost = threshold * output.length();
		if (input.length() - output.length() > maxCost)
			return false;
		
		WindowedAligner aligner = new WindowedAligner(input, output, Float.POSITIVE_INFINITY, maxCost);
		return aligner.align() != null;
	}
	
	public int[] align() {
		int xStart = 0;
		float minCost = 0f;
		int xMinCost = 0;
		
		for (int y = 0; y < input.length(); y++) {
			float maxCost = Math.min(minCost + searchLimit, costLimit);
			
			int nextXStart = -1;
			
			minCost = Float.POSITIVE_INFINITY;
			int nextXMinCost = -1;
			
			int x = xStart;
			for (; x < output.length(); x++) {
				float cost = chart.populate(x, y, substCost(x, y))[0];
				
				if (cost < minCost) {
					minCost = cost;
					nextXMinCost = x;
				}
				
				if (nextXStart == -1 && cost < maxCost)
					nextXStart = x;
				
				if (x > xMinCost && cost >= maxCost)
					break;
				
//				if (x - xStart > MAX_CELLS) {
//					System.err.printf("Next row exceeds %d cells. Aborting.\n", MAX_CELLS);
//					AnchoredArray<float[]> row = chart.rows.get(y-1);
//					System.err.printf("Cells starting from %d: ", row.minIndex());
//					for (int i = row.minIndex(); i < row.minIndex() + row.size(); i++)
//						System.err.printf("%d, ", (int) row.get(i)[0]);
//					System.err.println();
//					throw new Error("MAX_CELLS exceeded");
//				}

				if (x - xStart > MAX_CELLS) {
//					nextXStart++;
					break;
				}
			}
			
			if (minCost >= costLimit)
				return null;
			
			if (DEBUG)
				System.err.printf("Row %d: %d cells\n", y, chart.rows.get(y).size());
			
			xStart = nextXStart;
			xMinCost = nextXMinCost;
		}
		
		int[] alignment = new int[input.length()];
		int x = output.length() - 1;
		int y = input.length() - 1; 
		while (x >= 0 && y >= 0) {
			Operation op = chart.operation(x, y, substCost(x, y));
			if (op == Operation.SUBST) {
				alignment[y] = x;
				x--;
				y--;
			}
			else if (op == Operation.DELETE) {
				alignment[y] = -1;
				y--;
			}
			else {
				x--;
			}
		}
		while (y >= 0)
			alignment[y--] = -1;
		
		return alignment;
	}
	
	public float alignmentCost() {
		return chart.get(output.length() - 1, input.length() - 1)[0];
	}
	
	public void printChart(PrintStream out) {
		int cellWidth = 10;
		String sFormat = "%"+cellWidth+"."+cellWidth+"s";
		
		out.printf(sFormat, "");
		for (int x = 0; x < output.length(); x++)
			out.printf(sFormat, output.substring(x, x+1));
		out.println();
		
		for (int y = 0; y < input.length(); y++) {
			out.printf(sFormat, input.substring(y, y+1));
			for (int x = 0; x < output.length(); x++)
				out.printf(sFormat, Float.toString(chart.cost(x, y)));
			out.println();
		}
	}
	
	static void printPairedByOutputLine(
			String input, String output, int[] alignment, float maxMismatch, PrintStream out)
	{
		String inLine = "";
		String outLine = "";
		int j = 0;
		for (int i = 0; i < input.length(); i++) {
			while (j < alignment[i]) {
				if (output.charAt(j) == '\n') {
					printPairIfSufficientlyMatched(inLine, outLine, maxMismatch, out);
					inLine = "";
					outLine = "";
				} else {
					outLine += output.charAt(j);
				}
				j++;
			}
			if (alignment[i] == -1) {
				inLine += input.charAt(i);
			}
			else {
				if (output.charAt(j) == '\n') {
					printPairIfSufficientlyMatched(inLine, outLine, maxMismatch, out);
					inLine = "";
					outLine = "";
				} else {
					inLine += input.charAt(i);
					outLine += output.charAt(j);
				}
				j++;
			}
		}
		while (j < output.length()) {
			if (output.charAt(j) == '\n') {
				printPairIfSufficientlyMatched(inLine, outLine, maxMismatch, out);
				inLine = "";
				outLine = "";
			} else {
				outLine += output.charAt(j);
			}
			j++;
		}
		if (outLine.length() > 0)
			printPairIfSufficientlyMatched(inLine, outLine, maxMismatch, out);
	}
	static void printPairIfSufficientlyMatched(
			String input, String output, float maxMismatch, PrintStream out)
	{
		if (editDistanceBelow(input, output, maxMismatch))
			out.print(input.replaceAll(" *\n *", " ") + "\n" + output + "\n");
	}
	
	static void printAlignmentStringByOutputLine(
			String input, String output, int[] alignment, PrintStream out)
	{
		String inLine = "";
		String outLine = "";
		int j = 0;
		for (int i = 0; i < input.length(); i++) {
			while (j < alignment[i]) {
				if (output.charAt(j) == '\n') {
					out.print(inLine + "\n" + outLine + "\n");
					inLine = "";
					outLine = "";
				} else {
					inLine += "~";
					outLine += output.charAt(j);
				}
				j++;
			}
			if (alignment[i] == -1) {
				inLine += input.charAt(i);
				outLine += "~";
			}
			else {
				if (output.charAt(j) == '\n') {
					out.print(inLine + "\n" + outLine + "\n");
					inLine = "";
					outLine = "";
				} else {
					inLine += input.charAt(i);
					outLine += output.charAt(j);
				}
				j++;
			}
		}
		while (j < output.length()) {
			if (output.charAt(j) == '\n') {
				out.print(inLine + "\n" + outLine + "\n");
				inLine = "";
				outLine = "";
			} else {
				inLine += "~";
				outLine += output.charAt(j);
			}
			j++;
		}
		if (outLine.length() > 0)
			out.print(inLine + "\n" + outLine + "\n");
	}
	
	static String alignmentString(String input, String output, int[] alignment) {
		String inLine = "";
		String outLine = "";
		int j = 0;
		for (int i = 0; i < input.length(); i++) {
			while (j < alignment[i]) {
				inLine += "~";
				outLine += output.charAt(j++);
			}
			if (alignment[i] == -1) {
				inLine += input.charAt(i);
				outLine += "~";
			}
			else {
				inLine += input.charAt(i);
				outLine += output.charAt(j++);
			}
		}
		while (j < output.length()) {
			inLine += "~";
			outLine += output.charAt(j++);
		}
		return inLine + "\n" + outLine + "\n";
	}
	
	float substCost(int x, int y) {
		return input.charAt(y) == output.charAt(x) ? 0f : SUBST_COST;
	}

	public static void main(String[] args) {
		CommandLineParser clp = new CommandLineParser(
				"-test -debug -limit=f -paired=f -pretty -alignment", args);
		float searchLimit = (float) clp.opt("-limit", Double.POSITIVE_INFINITY);
		boolean dumpAlignment = clp.opt("-alignment");
		boolean pretty = clp.opt("-pretty");
		float matchThresh = (float) clp.opt("-paired", Double.NaN);
		boolean paired = !Float.isNaN(matchThresh);
		
		if (clp.opt("-test")) {
			test();
		}
		else if (clp.opt("-debug")) {
			printAlignment(clp.arg(0), clp.arg(1), 
					searchLimit, true, true, 1f, true, true);
		}
		else {
			String input = GUtil.loadUtf8String(clp.arg(0));
			String output = GUtil.loadUtf8String(clp.arg(1));
			printAlignment(input, output, 
					searchLimit, dumpAlignment, paired, matchThresh, pretty, false);
		}
	}
	
	public static void printAlignment(String input, String output, float searchLimit, 
			boolean dumpAlignment, boolean paired, float matchThresh, boolean pretty, boolean chart)
	{
		WindowedAligner aligner = new WindowedAligner(input, output, searchLimit);
		int[] alignment = aligner.align();
		if (paired)
			printPairedByOutputLine(input, output, alignment, matchThresh, System.out);
		if (dumpAlignment)
			System.out.println(Arrays.toString(alignment));
		if (pretty)
			printAlignmentStringByOutputLine(input, output, alignment, System.out);
		if (chart)
			aligner.printChart(System.out);
	}

	static void test() {
		verify("abcdef", "xxabcxdefxx", new int[] {2, 3, 4, 6, 7, 8});
		verify("xxabcxdefxx", "abcdef", new int[] {-1, -1, 0, 1, 2, -1, 3, 4, 5, -1, -1});
		verify("abcdefghabcdefgh", "bhxbg",
				new int[] {-1, 0, -1, -1, -1, -1, -1, 1, 2, 3, -1, -1, -1, -1, 4, -1});
		verify("bhxbg", "abcdefghabcdefgh", new int[] {1, 7, 8, 9, 14});
		System.out.println("Tests completed.");
	}
	
	static void verify(String a, String b, int[] alignment) {
		verify(a, b, Float.POSITIVE_INFINITY, alignment);
	}
	static void verify(String a, String b, float searchLimit, int[] alignment) {
		WindowedAligner aligner = new WindowedAligner(a, b, searchLimit);
		if (!Arrays.equals(alignment, aligner.align()))
			System.err.printf("TEST FAILED: '%s' '%s' %s\n", a, b, Arrays.toString(alignment));
	}
}
























