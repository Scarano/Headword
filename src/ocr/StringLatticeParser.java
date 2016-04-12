package ocr;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.math.util.FastMath;

import ocr.StringLattice.Edge;
import ocr.util.CommandLineParser;

public class StringLatticeParser {
	protected static final int LEFT = 0;
	protected static final int RIGHT = 1;
	protected static final int TRI = 0;
	protected static final int TRAP = 1;
	
	public static boolean debug = false;
	
	public static interface Scorer {
		public double scoreTrapezoid(Edge[] edges, int h, int a, int q, int d);
		public double scoreTriangle(Edge[] edges, int e, int s, int g, int d);
		public void saveModel(String fileName);
	}
	
	static class LetterScorer implements Scorer {
		@Override
		public double scoreTrapezoid(Edge[] edges, int h, int a, int q, int id) {
			int aval = edges[a].token.charAt(0) - ('a') + 1;
			
			if (h == 0)
				return (double) -aval;
				
			int hval = edges[h].token.charAt(0) - 'a' + 1;
			if (hval < aval)
				return hval - aval;
			else
				return (aval - hval) - 1;
		}
		@Override
		public double scoreTriangle(Edge[] edges, int e, int s, int g, int d) {
			return 0.0;
		}
		@Override
		public void saveModel(String fileName) {}
	}
	
	static class DMVScorer implements Scorer {
		DMVGrammar grammar;
		
		DMVScorer(DMVGrammar grammar) {
			this.grammar = grammar;
		}
		
		@Override
		public double scoreTrapezoid(Edge[] edges, int h, int a, int q, int d) {
			boolean left = d == LEFT;
			boolean hasChild = left ? edges[h].start != q : edges[h].end != q;
			double pCont = 0.0;
			if (h != 0)
				pCont = grammar.prob(edges[h].token, left, false, hasChild);

			double pArg = grammar.prob(edges[h].token, edges[a].token, left);
			
			// stop the argument's opposite-direction triangle
			boolean argLeft = !left;
			boolean argHasChild = argLeft ? edges[a].start != q : edges[a].end != q;
			double pStop = grammar.prob(edges[a].token, argLeft, true, argHasChild);
			
			double p = pCont + pArg + pStop;
			
			if (debug) {
				System.out.printf(
						"Trap(%d (%s) -> %d (%s) %d): %f = %f + %f + %f\n",
						h, edges[h].token, a, edges[a].token, q, p, pCont, pArg, pStop);
			}
			
			return p;
		}
		
		@Override
		public double scoreTriangle(Edge[] edges, int e, int s, int g, int d) {
			// stop the sub-triangle
			boolean left = d == LEFT;
			boolean hasChild = left ? edges[g].start != s : edges[g].end != s;
			double p = grammar.prob(edges[g].token, left, true, hasChild);
			
			if (debug) {
				System.out.printf(
						"Tri(%d %d %d %s) = stop(%d (%s), %s, %s) = %f\n", 
						e, s, g, dirStr(d), g, edges[g].token, dirStr(d), hasChild, p);
			}
			
			return p;
		}

		@Override
		public void saveModel(String fileName) {}
	}

	static class DMVVectorScorer implements Scorer {
		DMVVector model;
		
		DMVVectorScorer(DMVVector model) {
			this.model = model;
		}
		
		@Override
		public double scoreTrapezoid(Edge[] edges, int h, int a, int q, int d) {
			boolean left = d == LEFT;
			boolean hasChild = left ? edges[h].start != q : edges[h].end != q;
			double pCont = 0.0;
			if (h != 0)
				pCont = model.get(false, edges[h].token, d, hasChild);

			double pArg = model.get(edges[a].token, edges[h].token, d);
			
			// stop the argument's opposite-direction triangle
			boolean argLeft = !left;
			boolean argHasChild = argLeft ? edges[a].start != q : edges[a].end != q;
			double pStop = model.get(true, edges[a].token, argLeft ? 0 : 1, argHasChild);
			
			double p = pCont + pArg + pStop;
			
			if (debug) {
				System.out.printf(
						"Trap(%d (%s) -> %d (%s) %d): %f = %f + %f + %f\n",
						h, edges[h].token, a, edges[a].token, q, p, pCont, pArg, pStop);
			}
			
			return p;
		}
		
		@Override
		public double scoreTriangle(Edge[] edges, int e, int s, int g, int d) {
			// stop the sub-triangle
			boolean left = d == LEFT;
			boolean hasChild = left ? edges[g].start != s : edges[g].end != s;
			double p = model.get(true, edges[g].token, d, hasChild);
			
			if (debug) {
				System.out.printf(
						"Tri(%d %d %d %s) = stop(%d (%s), %s, %s) = %f\n", 
						e, s, g, dirStr(d), g, edges[g].token, dirStr(d), hasChild, p);
			}
			
			return p;
		}
		
		@Override
		public void saveModel(String fileName) {
			new DMVGrammar(model).save(fileName);
		}
	}

	private static class Cell {
		int top;
		int bottom;
		int dir;
		int type;
		double viterbiProb = Double.NEGATIVE_INFINITY;
		double inProb = Double.NEGATIVE_INFINITY;
		double outProb = Double.NEGATIVE_INFINITY;
		double prob = Double.NEGATIVE_INFINITY;
		HashMap<Integer, Arc> arcs; // XXX Not efficient!
		Arc viterbiArc = null;
		
		Cell(int top, int bottom, int dir, int type, double prob) {
			this.top = top;
			this.bottom = bottom;
			this.dir = dir;
			this.type = type;
			this.viterbiProb = prob;
			this.inProb = prob;
			arcs = new HashMap<Integer, Arc>();
		}
	}
	private static class Arc {
		Cell lhs;
		Cell rhs;
		double prob;
		
		Arc(Cell lhs, Cell rhs, double prob) {
			this.lhs = lhs;
			this.rhs = rhs;
			this.prob = prob;
		}
	}

	boolean zeroBased;
	Scorer scorer;
	Cell[][][] trapezoids;
	Cell[][][] triangles;
	int states; // total states including added root state (positions in original lattice + 1)
	ArrayList<ArrayList<Integer>> edgesByStart;
	ArrayList<ArrayList<Integer>> edgesByEnd;
	Edge[] edges;
	
	public StringLatticeParser(Scorer scorer) {
		this(scorer, false);
	}
	public StringLatticeParser(Scorer scorer, boolean zeroBased) {
		this.scorer = scorer;
		this.zeroBased = zeroBased;
	}
	
	protected void initialize(StringLattice lattice) {
		states = lattice.numPositions + 1;
		edges = new Edge[lattice.edges.size()+1];
		edges[0] = new Edge(0, 1, DMVGrammar.ROOT, 0.0);
		for (int i = 0; i < lattice.edges.size(); i++) {
			Edge edge = lattice.edges.get(i);
			edges[i+1] = new Edge(edge.start + 1, edge.end + 1, edge.token, edge.logProb);
		}
		
		edgesByStart = new ArrayList<ArrayList<Integer>>(states);
		edgesByEnd = new ArrayList<ArrayList<Integer>>(states);
		for (int s = 0; s < states; s++) {
			edgesByStart.add(new ArrayList<Integer>());
			edgesByEnd.add(new ArrayList<Integer>());
		}
		for (int e = 0; e < edges.length; e++) {
			edgesByStart.get(edges[e].start).add(e);
			edgesByEnd.get(edges[e].end).add(e);
		}
		
		trapezoids = new Cell[edges.length][][];
		for (int i = 0; i < edges.length; i++) {
			trapezoids[i] = new Cell[edges.length][];
			for (int j = 0; j < edges.length; j++) {
				trapezoids[i][j] = new Cell[2];
			}
		}
		triangles = new Cell[edges.length][][];
		for (int i = 0; i < edges.length; i++) {
			triangles[i] = new Cell[states][];
			for (int j = 0; j < states; j++) {
				triangles[i][j] = new Cell[2];
			}
		}
		
		triangles[0][1][RIGHT] = new Cell(0, 1, RIGHT, TRI, 0.0);
		for (int e = 1; e < edges.length; e++) {
			triangles[e][edges[e].start][LEFT] = 
					new Cell(e, edges[e].start, LEFT, TRI, edges[e].logProb);
			triangles[e][edges[e].end][RIGHT] = 
					new Cell(e, edges[e].end, RIGHT, TRI, 0.0);
		}
	}
	
	public int[] parse(String[] sent) {
		return positionalParse(parse(new StringLattice(sent)));
	}
	public int[] positionalParse(int[] edgeParse) {
		int[] parse = new int[states - 2];
		for (int i = 0; i < parse.length; i++)
			parse[i] = -1; // These -1's show up for states that are not in the parsed path
		for (int e = 0; e < edgeParse.length; e++)
			parse[edges[e+1].start - 1] = edges[edgeParse[e]].start - (zeroBased ? 1 : 0);
		return parse;
	}
	
	public int[] parse(StringLattice lattice) {
		initialize(lattice);
		
		// Populate arcs, calculate viterbi and inside probabilities
		for (int m = 1; m < states; m++) {
			for (int s = 0; s < states; s++) {
				int t = s + m;
				if (t >= states)
					break;
				
				// Add arcs to trapezoids
				for (int e: edgesByStart.get(s)) {
					for (int f: edgesByEnd.get(t)) {
						for (int q = edges[e].end; q <= edges[f].start; q++) {
							Cell lhs = triangles[e][q][RIGHT];
							Cell rhs = triangles[f][q][LEFT];
							
							if (lhs != null && rhs != null) {
								addArc(e, f, RIGHT, TRAP, q, lhs, rhs,
									scorer.scoreTrapezoid(edges, e, f, q, RIGHT));
								
								if (e != 0) { // Don't attach the root to anything
									addArc(f, e, LEFT, TRAP, q, lhs, rhs,
										scorer.scoreTrapezoid(edges, f, e, q, LEFT));
								}
							}
						}
					}
				}
				
				if (s > 0) {
					// Add arcs to right-directed triangles
					for (int e: edgesByStart.get(s)) {
						for (int q = edges[e].end; q < t; q++) {
							for (int g: edgesByStart.get(q)) {
								if (edges[g].end <= t) {
									Cell lhs = trapezoids[e][g][RIGHT];
									Cell rhs = triangles[g][t][RIGHT];
									if (lhs != null && rhs != null) {
										addArc(e, t, RIGHT, TRI, g, lhs, rhs,
											scorer.scoreTriangle(edges, e, t, g, RIGHT));
									}
								}
							}
						}
					}
					
					// Add arcs to left-directed triangles
					for (int e: edgesByEnd.get(t)) {
						for (int q = s + 1; q <= edges[e].start; q++) {
							for (int g: edgesByEnd.get(q)) {
								if (edges[e].start >= s) {
									Cell lhs = triangles[g][s][LEFT];
									Cell rhs = trapezoids[e][g][LEFT];
									if (lhs != null && rhs != null) {
										addArc(e, s, LEFT, TRI, g, lhs, rhs,
											scorer.scoreTriangle(edges, e, s, g, LEFT));
									}
								}
							}
						}
					}
				}
			}
		}
		
		// Add final right-directed triangle covering entire lattice
		for (int q = 1; q < states; q++) {
			for (int g: edgesByStart.get(q)) {
				Cell lhs = trapezoids[0][g][RIGHT];
				Cell rhs = triangles[g][states-1][RIGHT];
				if (lhs != null && rhs != null) {
					addArc(0, states-1, RIGHT, TRI, g, lhs, rhs,
						scorer.scoreTriangle(edges, 0, states-1, g, RIGHT));
				}
			}
		}

		if (triangles[0][states-1][RIGHT] == null)
			throw new Error("Can't handle unparseable lattices yet");
		
		// populate outside probabilities
		triangles[0][states-1][RIGHT].outProb = 0.0;
		traverseBranches(new CellFunction() {
			@Override public void apply(Cell cell) {
				for (Arc arc: cell.arcs.values()) {
					arc.lhs.outProb = Util.logSum(arc.lhs.outProb, 
						cell.outProb + arc.rhs.inProb + arc.prob);
					arc.rhs.outProb = Util.logSum(arc.rhs.outProb,
						cell.outProb + arc.lhs.inProb + arc.prob);
				}
			}
		});
		
		// populate expectations (inside prob * outside prob)
		traverseBranches(new CellFunction() {
			@Override public void apply(Cell cell) {
				cell.prob = cell.inProb + cell.outProb;
			}
		});

		int[] parse = new int[edges.length];
		for (int i = 0; i < parse.length; i++)
			parse[i] = -1; // These -1's show up for edges that are not in the parsed path
		populateParse(parse, 0, states - 1, RIGHT, TRI);
		// Remove imaginary root edge
		int[] realParse = new int[edges.length - 1];
		for (int i = 0; i < realParse.length; i++)
			realParse[i] = parse[i+1];
		
		return realParse;
	}
	
	private void addArc(
			int top, int bottom, int dir, int type, int split, Cell lhs, Cell rhs, double prob)
	{
		Cell cell;
		if (type == TRI) {
			cell = triangles[top][bottom][dir];
			if (cell == null) {
				cell = new Cell(top, bottom, dir, TRI, Double.NEGATIVE_INFINITY);
				triangles[top][bottom][dir] = cell;
			}
		}
		else {
			cell = trapezoids[top][bottom][dir];
			if (cell == null) {
				cell = new Cell(top, bottom, dir, TRAP, Double.NEGATIVE_INFINITY);
				trapezoids[top][bottom][dir] = cell;
			}
		}
		
		Arc arc = new Arc(lhs, rhs, prob);
		cell.arcs.put(split, arc);
		
		double viterbiProb = lhs.viterbiProb + rhs.viterbiProb + prob;
		if (viterbiProb > cell.viterbiProb) {
			cell.viterbiArc = arc;
			cell.viterbiProb = viterbiProb;
		}
		
		double inProb = lhs.inProb + rhs.inProb + prob;
		cell.inProb = Util.logSum(cell.inProb, inProb);
	}
	
	public static interface CellFunction {
		public void apply(Cell cell);
	}
	
	/**
	 * Visit all cells (triangles and trapezoids) except for the "leaf"
	 * (single-lattice-edge) triangles, in an order which guarantees that each cell is visited
	 * before any of its children.
	 */
	public void traverseBranches(CellFunction func) {
		for (int m = states - 1; m > 0; m--) {
			for (int s = 0; s < states; s++) {
				int t = s + m;
				if (t >= states)
					break;
				
				for (int e: edgesByStart.get(s)) {
					Cell cell = triangles[e][t][RIGHT];
					if (cell != null)
						func.apply(cell);
				}
				for (int e: edgesByEnd.get(t)) {
					Cell cell = triangles[e][s][LEFT];
					if (cell != null)
						func.apply(cell);
				}
				
				for (int e: edgesByStart.get(s)) {
					for (int f: edgesByEnd.get(t)) {
						Cell cell = trapezoids[e][f][RIGHT];
						if (cell != null)
							func.apply(cell);
						cell = trapezoids[f][e][LEFT];
						if (cell != null)
							func.apply(cell);
					}
				}
			}
		}
	}
	
	protected void populateParse(int[] parse, int top, int bottom, int d, int type) {
		Cell cell;
		if (type == TRAP) {
			parse[bottom] = top;
			cell = trapezoids[top][bottom][d];
		}
		else {
			cell = triangles[top][bottom][d];
			if (cell.arcs.size() == 0)
				return;
		}
		if (cell.viterbiArc == null) {
			System.err.println("Warning! No viterbi parse found.");
			System.err.println(Arrays.toString(edges));
			scorer.saveModel("debug-dump.dmv");
			return;
		}
		Cell lhs = cell.viterbiArc.lhs;
		Cell rhs = cell.viterbiArc.rhs;
		populateParse(parse, lhs.top, lhs.bottom, lhs.dir, lhs.type);
		populateParse(parse, rhs.top, rhs.bottom, rhs.dir, rhs.type);
	}
	
	void reestimate(DMVCounter dmvCounter) {
		
		final DMVCounter counter = dmvCounter;
		final double Z = sentProb();
		
		traverseBranches(new CellFunction() {
			@Override public void apply(Cell cell) {
				boolean left = cell.dir == LEFT;
				for (Map.Entry<Integer, Arc> entry: cell.arcs.entrySet()) {
					int split = entry.getKey();
					Arc arc = entry.getValue();
					
					double count = cell.outProb + arc.lhs.inProb + arc.rhs.inProb + arc.prob - Z;

					if (cell.type == TRI) {
						// Record stop event for child triangle
						boolean hasChild = left
								? edges[split].start != cell.bottom
								: edges[split].end != cell.bottom;
						counter.add(edges[split].token, left, true, hasChild, count);
					}
					else {
						// Record continue event for this trapezoid
						if (cell.top != 0) {
							boolean hasChild = left
									? edges[cell.top].start != split
									: edges[cell.top].end != split;
							counter.add(edges[cell.top].token, left, false, hasChild, count);
						}
						
						// Record attachment event for trapezoid's head & argument
						counter.add(edges[cell.top].token, edges[cell.bottom].token, left, count);
						
						// Record stop event for argument triangle
						boolean argLeft = !left;
						boolean argHasChild = argLeft
								? edges[cell.bottom].start != split
								: edges[cell.bottom].end != split;
						counter.add(edges[cell.bottom].token, argLeft, true, argHasChild, count);
					}
				}
			}
		});
	}
	
	/** 
	 * Unlike reestimate(), this adds in non-log space (vector stores actual probabilities).
	 * This also subtlely differs from reestimate() in that it only counts events; reestimate()
	 * also adds up denominators for MLE of conditional probabilities (this is done in
	 * {@link DMVCounter#add}).
	 */
	void addSoftCounts(DMVVector vector, double weight) {
		
		final DMVVector v = vector;
		final double w = weight;
		final double Z = sentProb();
		
		traverseBranches(new CellFunction() {
			@Override public void apply(Cell cell) {
				boolean left = cell.dir == LEFT;
				for (Map.Entry<Integer, Arc> entry: cell.arcs.entrySet()) {
					int split = entry.getKey();
					Arc arc = entry.getValue();
					
					double count = w * FastMath.exp(
							cell.outProb + arc.lhs.inProb + arc.rhs.inProb + arc.prob - Z);

					if (cell.type == TRI) {
						// Record stop event for child triangle
						boolean hasChild = left
								? edges[split].start != cell.bottom
								: edges[split].end != cell.bottom;
						v.add(true, edges[split].token, cell.dir, hasChild, count);
					}
					else {
						// Record continue event for this trapezoid
						if (cell.top != 0) {
							boolean hasChild = left
									? edges[cell.top].start != split
									: edges[cell.top].end != split;
							v.add(false, edges[cell.top].token, cell.dir, hasChild, count);
						}
						
						// Record attachment event for trapezoid's head & argument
						v.add(
								edges[cell.bottom].token, edges[cell.top].token, cell.dir, count);
						
						// Record stop event for argument triangle
						boolean argLeft = !left;
						boolean argHasChild = argLeft
								? edges[cell.bottom].start != split
								: edges[cell.bottom].end != split;
						v.add(
								true, edges[cell.bottom].token, argLeft ? 0 : 1, argHasChild, count);
					}
				}
			}
		});
	}
	
	public double sentProb() {
		return triangles[0][states-1][RIGHT].inProb;
	}
	
	public double viterbiProb() {
		return triangles[0][states-1][RIGHT].viterbiProb;
	}
	
	public void printChart(PrintStream out) {
		out.println("Triangles:");
		printChart(out, TRI);
		out.println();
		out.println("Trapezoids:");
		printChart(out, TRAP);
	}
	public void printChart(PrintStream out, int type) {
		int colWidth = 37;
//		int colWidth = 44;
		int cols = type == TRI ? states : edges.length;
		
		printPadded(out, "", 8);
		for (int j = 1; j < cols; j++)
			printPadded(out, j + "", colWidth);
		out.println();
			
		for (int i = 0; i < edges.length; i++) {
			out.println();

			String w = edges[i].token;
			printPadded(out, i + " " + w.substring(0, Math.min(w.length(), 6)), 8);
			
			for (int j = 1; j < cols; j++)
				printPadded(out, cellString(i, j, RIGHT, type), colWidth);
			out.println();
				
			printPadded(out, "", 8);

			for (int j = 1; j < cols; j++)
				printPadded(out, cellString(i, j, LEFT, type), colWidth);
			out.println();
		}
	}
	private String cellString(int top, int bottom, int d, int type) {
		Cell cell;
		if (type == TRI)
			cell = triangles[top][bottom][d];
		else
			cell = trapezoids[top][bottom][d];
		if (cell == null) {
			return dirStr(d) + ":";
		}
		else {
			Arc arc = cell.viterbiArc;
			String children = "           ";
			if (arc != null && arc.rhs != null) {
				children = String.format("%s%d%d%s %s%d%d%s",
					typeStr(arc.lhs.type), arc.lhs.top, arc.lhs.bottom, dirStr(arc.lhs.dir),
					typeStr(arc.rhs.type), arc.rhs.top, arc.rhs.bottom, dirStr(arc.rhs.dir));
			}
//			return String.format("%s: %s  %9.3f %9.3f",
//					dirStr(d),
//					children,
//					cell.viterbiProb,
//					cell.inProb);
			return String.format("%s: %s %7.3f %7.3f",
					dirStr(d),
					children,
					cell.prob,
					cell.outProb);
		}
	}
	
	static void printPadded(PrintStream out, String s, int padding) {
		out.print(s);
		for (int i = s.length(); i < padding; i++)
			out.print(" ");
	}
	
	static String dirStr(int dir) {
		return dir == LEFT ? "L" : "R";
	}
	static String typeStr(int type) {
		return type == TRI ? "Tl" : "Tz";
	}

	public static void main(String[] args) throws IOException {
		CommandLineParser clp = new CommandLineParser(
			"-string -model=s -normalize -sent=s -reestimate=s -debug -pretty", args);
		
		String model = clp.opt("-model", null);
		String sentArg = clp.opt("-sent", null);
		String outputModel = clp.opt("-reestimate", null);
		debug = clp.opt("-debug");
		boolean pretty = clp.opt("-pretty");
		
		Scorer scorer;
		if (model == null) {
			scorer = new LetterScorer();
		}
		else {
			DMVGrammar grammar = new DMVGrammar(model);
			DMVVector vector = grammar.asVector(grammar.buildVocabulary());
			scorer = new DMVVectorScorer(vector);
		}
		
		StringLatticeParser parser = new StringLatticeParser(scorer);
		
		if (clp.opt("-string")) {
			parseStrings(parser, sentArg, clp.args().length > 0 ? clp.arg(0) : null, outputModel);
		}
		else {
			for (StringLattice lattice: exampleLattices()) {
				int[] parse = parser.parse(lattice);
				double score = parser.viterbiProb();
				
				System.out.printf("%f %s\n", score, Arrays.toString(parse));
				if (pretty)
					DMVParserG.printParse(System.out, lattice.edges, parse);
				if (debug)
					parser.printChart(System.out);
				System.out.println();
			}
		}
	}
	
	static void parseStrings(
			StringLatticeParser parser, String sentArg, String sentFile, String outputModel) 
			throws IOException
	{
		List<String> sentences = new ArrayList<String>();
		if (sentArg != null) {
			sentences.add(sentArg);
		}
		else if (sentFile != null) {
			BufferedReader sentReader = new BufferedReader(
					new InputStreamReader(new FileInputStream(sentFile), "UTF-8"));
			while (sentReader.ready())
				sentences.add(sentReader.readLine());
			sentReader.close();
		}
		else {
			sentences = Arrays.asList(new String[] {
				"a b c",
				"c b a",
				"b a c",
				"c b a b",
				"b c a b"});
		}
		
		DMVCounter estimator = new DMVCounter();

		for (String str: sentences) {
			String[] sent = str.split(" ");
			int[] parse = parser.parse(sent);
			double score = parser.viterbiProb();
			
			if (outputModel != null)
				parser.reestimate(estimator);
			
			System.out.println(str);
			System.out.printf("%f %s\n", score, Arrays.toString(parse));
			if (debug)
				parser.printChart(System.out);
			System.out.println();
		}
		
		if (outputModel != null) {
			if (debug)
				estimator.print(System.out);
		
			DMVGrammar grammar = estimator.createGrammar();
			grammar.save(outputModel);
		}
	}
	
	static List<StringLattice> exampleLattices() {
		List<StringLattice> lattices = new LinkedList<StringLattice>();
		StringLattice lattice;
		
		lattice = new StringLattice(3);
		lattice.addEdge(0, 1, "a", 0);
		lattice.addEdge(1, 2, "b", 0);
		lattices.add(lattice);
		
		lattice = new StringLattice(3);
		lattice.addEdge(0, 1, "b", 0);
		lattice.addEdge(1, 2, "a", 0);
		lattices.add(lattice);
		
		lattice = new StringLattice(4);
		lattice.addEdge(0, 1, "a", -1);
		lattice.addEdge(1, 3, "b", -1);
		lattice.addEdge(0, 2, "c", -1);
		lattice.addEdge(2, 3, "d", -1);
		lattices.add(lattice);
		
		lattice = new StringLattice(4);
		lattice.addEdge(0, 1, "a", -1);
		lattice.addEdge(1, 2, "b", -1);
		lattice.addEdge(2, 3, "c", -1);
		lattice.addEdge(0, 3, "d", -1);
		lattices.add(lattice);
		
		lattice = new StringLattice(4);
		lattice.addEdge(0, 1, "a", -1);
		lattice.addEdge(1, 2, "b", -1);
		lattice.addEdge(2, 3, "c", -1);
		lattice.addEdge(0, 3, "d", -3);
		lattices.add(lattice);
		
		lattice = new StringLattice(4);
		lattice.addEdge(0, 1, "a", -1);
		lattice.addEdge(1, 2, "b", -4);
		lattice.addEdge(1, 2, "e", -1);
		lattice.addEdge(2, 3, "c", -1);
		lattice.addEdge(0, 3, "d", -5);
		lattices.add(lattice);
		
		lattice = new StringLattice(4);
		lattice.addEdge(0, 1, "a", -1);
		lattice.addEdge(0, 2, "d", -1.1);
		lattice.addEdge(1, 2, "b", -1);
		lattice.addEdge(1, 3, "e", -1);
		lattice.addEdge(2, 3, "c", -1);
		lattices.add(lattice);
		
		lattice = new StringLattice(4);
		lattice.addEdge(0, 1, "a", -1);
		lattice.addEdge(0, 2, "d", -1);
		lattice.addEdge(1, 2, "b", -1.1);
		lattice.addEdge(1, 3, "e", -1);
		lattice.addEdge(2, 3, "c", -1);
		lattices.add(lattice);
		
		return lattices;
	}
}













