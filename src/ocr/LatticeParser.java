package ocr;

import java.io.BufferedReader;
import java.io.File;
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
import java.util.Random;

import org.apache.commons.math.util.FastMath;

import ocr.TaggedLattice.Edge;
import ocr.TaggedLattice.StringToken;
import ocr.TaggedLattice.TaggedToken;
import ocr.util.CommandLineParser;
import ocr.util.RunConfig;
import ocr.util.Util;

public class LatticeParser {
	protected static final int LEFT = 0;
	protected static final int RIGHT = 1;
	protected static final int TRI = 0;
	protected static final int TRAP = 1;
	
	protected static final TaggedToken ROOT_TOKEN = new TaggedToken(DMVGrammar.ROOT);
	
	public static boolean debug = false;
	
	public static interface Scorer {
		public double scoreTrapezoid(Edge[] edges, int h, int a, int q, int d);
		public double scoreTriangle(Edge[] edges, int e, int s, int g, int d);
		public void saveModel(String fileName);
	}
	
	/* For debugging & testing only.
	 * LetterScorer assumes every word is a letter (a sentence might be "c b a d"), and assigns
	 * log prob proportional to ord(head) - ord(arg). */
	static class LetterScorer implements Scorer {
		@Override
		public double scoreTrapezoid(Edge[] edges, int h, int a, int q, int id) {
			int aval = edges[a].token.getString().charAt(0) - ('a') + 1;
			
			if (h == 0)
				return (double) -aval;
				
			int hval = edges[h].token.getString().charAt(0) - 'a' + 1;
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

	
	static class DMVGrammarScorer implements Scorer {
		DMVGrammar grammar;
		
		DMVGrammarScorer(DMVGrammar grammar) {
			this.grammar = grammar;
		}
		
		@Override
		public double scoreTrapezoid(Edge[] edges, int h, int a, int q, int d) {
			boolean left = d == LEFT;
			boolean hasChild = left ? edges[h].start != q : edges[h].end != q;
			double pCont = 0.0;
			if (h != 0)
				pCont = grammar.prob(edges[h].token.getTag(), left, false, hasChild);

			double pArg = grammar.prob(edges[h].token.getTag(), edges[a].token.getTag(), left);
			
			// stop the argument's opposite-direction triangle
			boolean argLeft = !left;
			boolean argHasChild = argLeft ? edges[a].start != q : edges[a].end != q;
			double pStop = grammar.prob(edges[a].token.getTag(), argLeft, true, argHasChild);
			
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
			double p = grammar.prob(edges[g].token.getTag(), left, true, hasChild);
			
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

	static class DMVScorer implements Scorer {
		DMV model;
		
		DMVScorer(DMV model) {
			this.model = model;
		}
		
		@Override
		public double scoreTrapezoid(Edge[] edges, int h, int a, int q, int d) {
			boolean left = d == LEFT;
			boolean hasChild = left ? edges[h].start != q : edges[h].end != q;
			double pCont = 0.0;
			if (h != 0)
				pCont = model.logProb(edges[h].token, left, false, hasChild);

			double pArg = model.logProb(edges[h].token, edges[a].token, left);
			
			// stop the argument's opposite-direction triangle
			boolean argLeft = !left;
			boolean argHasChild = argLeft ? edges[a].start != q : edges[a].end != q;
			double pStop = model.logProb(edges[a].token, argLeft, true, argHasChild);
			
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
			double p = model.logProb(edges[g].token, left, true, hasChild);
			
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
				pCont = model.get(false, edges[h].token.getTag(), d, hasChild);

			double pArg = model.get(edges[a].token.getTag(), edges[h].token.getTag(), d);
			
			// stop the argument's opposite-direction triangle
			boolean argLeft = !left;
			boolean argHasChild = argLeft ? edges[a].start != q : edges[a].end != q;
			double pStop = model.get(true, edges[a].token.getTag(), argLeft ? 0 : 1, argHasChild);
			
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
			double p = model.get(true, edges[g].token.getTag(), d, hasChild);
			
			if (debug) {
				System.out.printf(
						"Tri(%d %d %d %s) = stop(%d (%s), %s, %s) = %f\n", 
						e, s, g, dirStr(d), g, edges[g].token.getTag(), dirStr(d), hasChild, p);
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
		HashMap<Integer, Arc> arcs; // XXX May need to do something more efficient than this
		Arc viterbiArc = null;
		int viterbiSplit = -1;
		List<Integer> viterbiSplitCandidates = new ArrayList<Integer>();
		
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
	boolean rightBranching;
	Scorer scorer;
	Random tieBreaker = new Random(0); // initialize deterministically for reproducible results
	Cell[][][] trapezoids;
	Cell[][][] triangles;
	int states; // total states including added root state (positions in original lattice + 1)
	ArrayList<ArrayList<Integer>> edgesByStart;
	ArrayList<ArrayList<Integer>> edgesByEnd;
	Edge[] edges;
	
	public LatticeParser(Scorer scorer) {
		this(scorer, false, false);
	}
	public LatticeParser(Scorer scorer, boolean zeroBased) {
		this(scorer, zeroBased, false);
	}
	public LatticeParser(Scorer scorer, boolean zeroBased, boolean rightBranching) {
		this.scorer = scorer;
		this.zeroBased = zeroBased;
		this.rightBranching = rightBranching;
	}
	
	protected void initialize(TaggedLattice lattice) {
		states = lattice.numPositions + 1;
		edges = new Edge[lattice.edges.size()+1];
		edges[0] = new Edge(0, 1, ROOT_TOKEN, 0.0);
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
	
	public void populateWithParse(String[] sent, int[] parse) {
		populateWithParse(new TaggedLattice(sent), parse);
	}
	public void populateWithParse(String[] sent, Clustering clustering, int[] parse) {
		populateWithParse(new TaggedLattice(sent, clustering), parse);
	}
	public void populateWithParse(TaggedLattice lattice, int[] parse) {
		initialize(lattice);

		assert parse.length == states - 2;
		assert edges.length == states - 1; // Only support single-path lattices
		
		Cell[][] longestTriangleFrom = new Cell[edges.length][2];
		for (int e = 0; e < edges.length; e++) {
			longestTriangleFrom[e][LEFT] = triangles[e][edges[e].start][LEFT];
			longestTriangleFrom[e][RIGHT] = triangles[e][edges[e].end][RIGHT];
		}
		
		for (int m = 1; m < edges.length; m++) {
			for (int f = 1; f < edges.length; f++) {
				int e = parse[f-1]+1;
				int dir = f > e ? RIGHT : LEFT;
				
				if (Math.abs(f - e) == m) {
					Cell lhs, rhs;
					if (dir == RIGHT) {
						lhs = longestTriangleFrom[e][RIGHT];
						rhs = longestTriangleFrom[f][LEFT];
					}
					else {
						lhs = longestTriangleFrom[f][RIGHT];
						rhs = longestTriangleFrom[e][LEFT];
					}
					addArc(e, f, dir, TRAP, lhs.bottom, lhs, rhs, 0.0);
				}

				Cell trap = trapezoids[e][f][dir];
				if (trap != null) {
					Cell tri = longestTriangleFrom[f][dir];
					int g = (dir == RIGHT ? edgesByEnd : edgesByStart).get(tri.bottom).get(0);
					if (Math.abs(e - g) == m) {
						if (dir == RIGHT)
							addArc(e, tri.bottom, dir, TRI, trap.bottom, trap, tri, 0.0);
						else
							addArc(e, tri.bottom, dir, TRI, trap.bottom, tri, trap, 0.0);
						longestTriangleFrom[e][dir] = triangles[e][tri.bottom][dir];
					}
				}
			}
		}

		breakViterbiTiesRandomly(0, states-1, RIGHT, TRI);
	}
	
	public int[] parse(String[] sent) {
		return positionalParse(parse(new TaggedLattice(sent)));
	}
	public int[] parse(String[] sent, Clustering clustering) {
		return positionalParse(parse(new TaggedLattice(sent, clustering)));
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
		return parse(new TaggedLattice(lattice));
	}
	
	public int[] parse(TaggedLattice lattice) {
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
						if (rightBranching && edges[e].end != edges[f].start)
							continue;
						for (int q = edges[e].end; q <= edges[f].start; q++) {
							Cell lhs = triangles[e][q][RIGHT];
							Cell rhs = triangles[f][q][LEFT];
							
							if (lhs != null && rhs != null) {
								addArc(e, f, RIGHT, TRAP, q, lhs, rhs,
									scorer.scoreTrapezoid(edges, e, f, q, RIGHT));
								
								if (!rightBranching && e != 0) { // Don't attach the root to anything
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
		
		breakViterbiTiesRandomly(0, states-1, RIGHT, TRI);

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
//		traverseBranches(new CellFunction() {
//			@Override public void apply(Cell cell) {
//				cell.prob = cell.inProb + cell.outProb;
//			}
//		});
		
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
//System.out.println("Adding tri " + top + " " + bottom);
			cell = triangles[top][bottom][dir];
			if (cell == null) {
				cell = new Cell(top, bottom, dir, TRI, Double.NEGATIVE_INFINITY);
				triangles[top][bottom][dir] = cell;
			}
		}
		else {
//System.out.println("Adding trap " + top + " " + bottom);
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
			// This arc is better than any we've seen for this cell before. Clear list of 
			// Viterbi arcs, and use only this one.
			cell.viterbiSplitCandidates.clear();
			cell.viterbiSplitCandidates.add(split);
			cell.viterbiProb = viterbiProb;
		}
		else if (viterbiProb == cell.viterbiProb) {
			// This arc is tied for first place. Add it to the list, and break the tie randomly
			// later.
			cell.viterbiSplitCandidates.add(split);
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
	
	protected void breakViterbiTiesRandomly(int top, int bottom, int d, int type) {
		Cell cell = (type == TRAP) 
				? trapezoids[top][bottom][d]
				: triangles[top][bottom][d];
				
		if (cell.viterbiSplitCandidates.size() == 0)
			return;
		
		if (cell.viterbiSplitCandidates.size() == 1) {
			cell.viterbiSplit = cell.viterbiSplitCandidates.get(0);
		}
		else {
			cell.viterbiSplit = cell.viterbiSplitCandidates.get(
					tieBreaker.nextInt(cell.viterbiSplitCandidates.size()));
		}
		cell.viterbiArc = cell.arcs.get(cell.viterbiSplit);
		
		Cell lhs = cell.viterbiArc.lhs;
		Cell rhs = cell.viterbiArc.rhs;
		breakViterbiTiesRandomly(lhs.top, lhs.bottom, lhs.dir, lhs.type);
		breakViterbiTiesRandomly(rhs.top, rhs.bottom, rhs.dir, rhs.type);
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
//			scorer.saveModel("debug-dump.dmv");
			return;
		}
		Cell lhs = cell.viterbiArc.lhs;
		Cell rhs = cell.viterbiArc.rhs;
		populateParse(parse, lhs.top, lhs.bottom, lhs.dir, lhs.type);
		populateParse(parse, rhs.top, rhs.bottom, rhs.dir, rhs.type);
	}
	
	protected void reestimateViterbi(DMVCounter tagCounter, DMVCounter lexCounter) {
		reestimateViterbi(0, states - 1, RIGHT, TRI, tagCounter, lexCounter);
	}
	protected void reestimateViterbi(int top, int bottom, int d, int type,
		DMVCounter tagCounter, DMVCounter lexCounter)
	{
		boolean left = d==LEFT;
		Cell cell;
		if (type == TRI) {
			cell = triangles[top][bottom][d];
			
			if (cell.arcs.size() == 0)
				return;
			
			int split = cell.viterbiSplit;
			
			// Record stop event for child triangle
			boolean hasChild = left
					? edges[split].start != cell.bottom
					: edges[split].end != cell.bottom;
			tagCounter.add(edges[split].token.getTag(), left, true, hasChild, 0.0);
		}
		else {
			cell = trapezoids[top][bottom][d];
			int split = cell.viterbiSplit;

			if (split == -1) {
				System.err.println("Warning! No viterbi parse found.");
				System.err.println(Arrays.toString(edges));
//				scorer.saveModel("debug-dump.dmv");
				return;
			}

			// Record continue event for this trapezoid
			if (cell.top != 0) {
				boolean hasChild = left
						? edges[cell.top].start != split
						: edges[cell.top].end != split;
				tagCounter.add(edges[cell.top].token.getTag(), left, false, hasChild, 0.0);
			}
			
			// Record attachment event for trapezoid's head & argument
			tagCounter.add(edges[cell.top].token.getTag(), 
					edges[cell.bottom].token.getTag(), left, 0.0);
			
			// If there is also a lexical counter, add the token string attachment event
			if (lexCounter != null) {
				lexCounter.add(edges[cell.top].token.getString(), 
						edges[cell.bottom].token.getString(), left, 0.0);
			}
			
			// Record stop event for argument triangle
			boolean argLeft = !left;
			boolean argHasChild = argLeft
					? edges[cell.bottom].start != split
					: edges[cell.bottom].end != split;
			tagCounter.add(edges[cell.bottom].token.getTag(),
					argLeft, true, argHasChild, 0.0);
		}
		Cell lhs = cell.viterbiArc.lhs;
		Cell rhs = cell.viterbiArc.rhs;
		reestimateViterbi(lhs.top, lhs.bottom, lhs.dir, lhs.type, tagCounter, lexCounter);
		reestimateViterbi(rhs.top, rhs.bottom, rhs.dir, rhs.type, tagCounter, lexCounter);
	}
	
	void reestimate(DMVCounter dmvCounter) {
		reestimate(dmvCounter, null);
	}
	void reestimate(DMVCounter dmvCounter, DMVCounter lexCounter) {
		
		final DMVCounter counter = dmvCounter;
		final DMVCounter lCounter = lexCounter;
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
						counter.add(edges[split].token.getTag(), left, true, hasChild, count);
					}
					else {
						// Record continue event for this trapezoid
						if (cell.top != 0) {
							boolean hasChild = left
									? edges[cell.top].start != split
									: edges[cell.top].end != split;
							counter.add(edges[cell.top].token.getTag(), left, false, hasChild, count);
						}
						
						// Record attachment event for trapezoid's head & argument
						counter.add(edges[cell.top].token.getTag(), 
								edges[cell.bottom].token.getTag(), left, count);
						
						// If there is also a lexical counter, add the token string attachment event
						if (lCounter != null) {
							lCounter.add(edges[cell.top].token.getString(), 
									edges[cell.bottom].token.getString(), left, count);
						}
						
						// Record stop event for argument triangle
						boolean argLeft = !left;
						boolean argHasChild = argLeft
								? edges[cell.bottom].start != split
								: edges[cell.bottom].end != split;
						counter.add(edges[cell.bottom].token.getTag(),
								argLeft, true, argHasChild, count);
					}
				}
			}
		});
	}
	
	/** 
	 * Unlike reestimate(), this adds in non-log space (vector stores actual probabilities).
	 * This also subtly differs from reestimate() in that it only counts events; reestimate()
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
						v.add(true, edges[split].token.getTag(), cell.dir, hasChild, count);
					}
					else {
						// Record continue event for this trapezoid
						if (cell.top != 0) {
							boolean hasChild = left
									? edges[cell.top].start != split
									: edges[cell.top].end != split;
							v.add(false, edges[cell.top].token.getTag(), cell.dir, hasChild, count);
						}
						
						// Record attachment event for trapezoid's head & argument
						v.add(edges[cell.bottom].token.getTag(),
								edges[cell.top].token.getTag(), cell.dir, count);
						
						// Record stop event for argument triangle
						boolean argLeft = !left;
						boolean argHasChild = argLeft
								? edges[cell.bottom].start != split
								: edges[cell.bottom].end != split;
						v.add(true, edges[cell.bottom].token.getTag(),
								argLeft ? 0 : 1, argHasChild, count);
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
	
	public double lexicalProbGivenParse(String[] tokens, int[] parse, DMV lexModel) {
		double pSent = 0.0;
		for (int i = 0; i < tokens.length; i++) {
			ocr.TaggedLattice.Token arg = new StringToken(tokens[i]);
			ocr.TaggedLattice.Token head = parse[i] == -1 
					? ROOT_TOKEN 
					: new StringToken(tokens[parse[i]]);
			double p = lexModel.logProb(head, arg, parse[i] > i);
			
			if (debug)
				System.out.printf("p(%s | %s) = %f (log = %f)\n", arg, head, Math.exp(p), p);
			
			pSent += p;
		}
		return pSent;
	}
	
	/** Warning! This function is not done, and the approach to how to do this
	 * needs to be reconsidered */
	public double lexicalProbMarginalizedOverParses(final DMV lexModel) {
		final double[] pSent = new double[] {0.0};
		
		traverseBranches(new CellFunction() {
			@Override public void apply(Cell cell) {
				if (cell.type == TRAP) {
					ocr.TaggedLattice.Token arg = edges[cell.bottom].token;
					ocr.TaggedLattice.Token head = edges[cell.top].token;
					// Not done yet!
					double p = lexModel.logProb(head, arg, cell.dir == 0);
					if (debug)
						System.out.printf("p(%s | %s) = %f (log = %f)\n",
								arg, head, Math.exp(p), p);
					pSent[0] += p;
				}
			}
		});
		
		return pSent[0];
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

			String w = edges[i].token.getTag();
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
			return String.format("%s: %s  %9.3f %9.3f",
					dirStr(d),
					children,
					cell.viterbiProb,
					Math.exp(cell.viterbiProb));
//			return String.format("%s: %s %7.3f %7.3f",
//					dirStr(d),
//					children,
//					cell.prob,
//					cell.outProb);
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
	
	static Map<String, Scorer> scorers = new HashMap<String, Scorer>();
	
	public synchronized static LatticeParser fromConfig(RunConfig config) throws IOException {
		Scorer scorer;
		if (config.getBoolean("parser.combined-model")) {
			String tagModelFile = config.getDataFile("parser.tag-model-file").getPath();
			double tagModelAlpha = config.getDouble("parser.tag-model-alpha");
			String lexModelFile = config.getDataFile("parser.lex-model-file").getPath();
			double lexModelAlpha = config.getDouble("parser.lex-model-alpha");
			double lexModelLambda = config.getDouble("parser.lex-model-lambda");
			double lexUnkProb = config.getDouble("parser.lex-unk-prob");
			
			String key = "combined::" + tagModelFile + "::" + tagModelAlpha + "::"
					+ "::" + lexModelFile + "::" + lexModelAlpha + "::" + lexModelLambda
					+ "::" + lexUnkProb;
			scorer = scorers.get(key);
			if (scorer == null) {
				DMV tagDmv = new TagDMV(tagModelFile, false, tagModelAlpha);
				DMV lexDmv = new LexDMV(
						lexModelFile, lexModelAlpha, tagDmv, lexModelLambda, lexUnkProb);
				scorer = new DMVScorer(lexDmv);
				scorers.put(key, scorer);
			}
		}
		else {
			String tagModelFile = config.getDataFile("parser.tag-model-file").getPath();
			if (tagModelFile.endsWith(".dmv")) {
				DMVGrammar tagDmv = new DMVGrammar(tagModelFile);
				scorer = new DMVGrammarScorer(tagDmv);
			}
			else {
				DMV tagDmv = new TagDMV(tagModelFile, false,
						config.getDouble("parser.tag-model-alpha"));
				scorer = new DMVScorer(tagDmv);
			}
		}
		
		return new LatticeParser(scorer, true, config.getBoolean("parser.right-branching"));
	}
	
	static void supervisedTraining(String trainingFile, Clustering clustering, 
			String outputModel, String outputLexModel, int minLength, int maxLength
	) throws IOException
	{
		LatticeParser parser = new LatticeParser(null);
		
		DMVCounter tagCounter = new DMVCounter();
		DMVCounter lexCounter = outputLexModel == null ? null : new DMVCounter();
		
		BufferedReader sentReader = new BufferedReader(
				new InputStreamReader(new FileInputStream(trainingFile), "UTF-8"));
		while (sentReader.ready()) {
			String[] text = sentReader.readLine().split(" ");
			sentReader.readLine(); // String[] tags = sentReader.readLine().split(" ");
			String[] parseStr = sentReader.readLine().split(" ");
			int[] parse = new int[parseStr.length];
			for (int i = 0; i < parse.length; i++)
				parse[i] = Integer.parseInt(parseStr[i]);
			
			if (text.length < minLength || text.length > maxLength)
				continue;
			
			parser.populateWithParse(text, clustering, parse);
			if (debug)
				parser.printChart(System.out);

			parser.reestimateViterbi(tagCounter, lexCounter);
		}
		sentReader.close();
		
		if (outputModel != null) {
			if (debug)
				tagCounter.print(System.out);

			if (outputModel.endsWith(".dmv")) {
				DMVGrammar grammar = tagCounter.createGrammar();
				grammar.save(outputModel);
			}
			else {
				tagCounter.saveCounts(outputModel);
			}
		}
		if (outputLexModel != null)
			lexCounter.saveCounts(outputLexModel);
	}

	public static void main(String[] args) throws IOException {
		CommandLineParser clp = new CommandLineParser(
			"-string -sent=s -model=s -counts=s -tag-smoothing=f"
			+ " -lex-counts=s -lex-smoothing=f -lambda=f -right-branching "
			+ " -clustering=s -normalize -reestimate=s -estimate-lex=s -viterbi"
			+ " -min-length=i -max-length=i -quiet -debug -pretty"
			+ " -supervised-training=s",
			args);

		String sentFile = clp.args().length > 0 ? clp.arg(0) : null;
		String sentArg = clp.opt("-sent", null);
		String modelFile = clp.opt("-model", null);
		String countsFile = clp.opt("-counts", null);
		double tagAlpha = clp.opt("-tag-smoothing", 0.0);
		String lexCountsFile = clp.opt("-lex-counts", null);
		double lexAlpha = clp.opt("-lex-smoothing", 0.0);
		double lambda = clp.opt("-lambda", 0.0); // weight of lexical model
		boolean rightBranching = clp.opt("-right-branching");
		String clusterFile = clp.opt("-clustering", null);
		String outputModel = clp.opt("-reestimate", null);
		String outputLexModel = clp.opt("-estimate-lex", null);
		boolean viterbi = clp.opt("-viterbi");
		int minLength = clp.opt("-min-length", 0);
		int maxLength = clp.opt("-max-length", 9999);
		boolean quiet = clp.opt("-quiet");
		debug = clp.opt("-debug");
		boolean pretty = clp.opt("-pretty");
		String supervisedTrainingFile = clp.opt("-supervised-training", null);

		Clustering clustering = null;
		if (clusterFile != null) clustering = new Clustering(new File(clusterFile), true, true);
	
		if (supervisedTrainingFile != null) {
			supervisedTraining(supervisedTrainingFile, clustering, outputModel, outputLexModel,
					minLength, maxLength);
			return;
		}
		
		Scorer scorer;
		if (modelFile != null) {
			DMVGrammar grammar = new DMVGrammar(modelFile);
			DMVVector vector = grammar.asVector(grammar.buildVocabulary());
			scorer = new DMVVectorScorer(vector);
		}
		else if (countsFile != null) {
			DMV model = new TagDMV(countsFile, false, tagAlpha);
			
			if (lexCountsFile != null)
				model = new LexDMV(lexCountsFile, lexAlpha, model, lambda, 1e-5);
			
			scorer = new DMVScorer(model);
		}
		else {
			scorer = new LetterScorer();
		}
		
		LatticeParser parser = new LatticeParser(scorer, false, rightBranching);
		
		if (clp.opt("-string")) {
			parseStrings(
					parser, sentArg, sentFile, clustering, outputModel, outputLexModel, viterbi, 
					minLength, maxLength, pretty, quiet);
		}
		else {
			for (TaggedLattice lattice: exampleLattices()) {
				int[] parse = parser.parse(lattice);
				double score = parser.viterbiProb();
				
				System.out.printf("%f %s\n", score, Arrays.toString(parse));
				if (pretty)
					DMVParserG.printParseTagged(System.out, lattice.edges, parse);
				if (debug)
					parser.printChart(System.out);
				System.out.println();
			}
		}
	}
	
	static void parseStrings(LatticeParser parser, String sentArg, String sentFile,
			Clustering clustering, String outputModel, String outputLexModel, boolean viterbi,
			int minLength, int maxLength, boolean prettyPrint, boolean quiet
	) throws IOException
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
		
		DMVCounter tagCounter = new DMVCounter();
		DMVCounter lexCounter = outputLexModel == null ? null : new DMVCounter();

		for (String str: sentences) {
			String[] sent = str.split(" ");
			
			if (sent.length < minLength || sent.length > maxLength)
				continue;
			
			TaggedLattice lattice;
			if (clustering == null)
				lattice = new TaggedLattice(sent);
			else
				lattice = new TaggedLattice(sent, clustering);
			
			int[] parse = parser.parse(lattice);
			double score = parser.viterbiProb();
			
			if (outputModel != null || outputLexModel != null) {
				if (viterbi) {
					parser.reestimateViterbi(tagCounter, lexCounter);
				}
				else {
					parser.reestimate(tagCounter, lexCounter);
				}
			}

			if (!quiet) {
				System.out.println(str);
				System.out.printf("%f %s\n", score, Arrays.toString(parse));
				if (prettyPrint)
					DMVParserG.printParseTagged(System.out, lattice.edges, parse);
				if (debug)
					parser.printChart(System.out);
				System.out.println();
			}
		}
		
		if (outputModel != null) {
			if (debug)
				tagCounter.print(System.out);

			if (outputModel.endsWith(".dmv")) {
				DMVGrammar grammar = tagCounter.createGrammar();
				grammar.save(outputModel);
			}
			else {
				tagCounter.saveCounts(outputModel);
			}
		}
		if (outputLexModel != null)
			lexCounter.saveCounts(outputLexModel);
	}
	
	static List<TaggedLattice> exampleLattices() {
		List<TaggedLattice> lattices = new LinkedList<TaggedLattice>();
		TaggedLattice lattice;
		
		lattice = new TaggedLattice(3);
		lattice.addEdge(0, 1, "a", 0);
		lattice.addEdge(1, 2, "b", 0);
		lattices.add(lattice);
		
		lattice = new TaggedLattice(3);
		lattice.addEdge(0, 1, "b", 0);
		lattice.addEdge(1, 2, "a", 0);
		lattices.add(lattice);
		
		lattice = new TaggedLattice(4);
		lattice.addEdge(0, 1, "a", -1);
		lattice.addEdge(1, 3, "b", -1);
		lattice.addEdge(0, 2, "c", -1);
		lattice.addEdge(2, 3, "d", -1);
		lattices.add(lattice);
		
		lattice = new TaggedLattice(4);
		lattice.addEdge(0, 1, "a", -1);
		lattice.addEdge(1, 2, "b", -1);
		lattice.addEdge(2, 3, "c", -1);
		lattice.addEdge(0, 3, "d", -1);
		lattices.add(lattice);
		
		lattice = new TaggedLattice(4);
		lattice.addEdge(0, 1, "a", -1);
		lattice.addEdge(1, 2, "b", -1);
		lattice.addEdge(2, 3, "c", -1);
		lattice.addEdge(0, 3, "d", -3);
		lattices.add(lattice);
		
		lattice = new TaggedLattice(4);
		lattice.addEdge(0, 1, "a", -1);
		lattice.addEdge(1, 2, "b", -4);
		lattice.addEdge(1, 2, "e", -1);
		lattice.addEdge(2, 3, "c", -1);
		lattice.addEdge(0, 3, "d", -5);
		lattices.add(lattice);
		
		lattice = new TaggedLattice(4);
		lattice.addEdge(0, 1, "a", -1);
		lattice.addEdge(0, 2, "d", -1.1);
		lattice.addEdge(1, 2, "b", -1);
		lattice.addEdge(1, 3, "e", -1);
		lattice.addEdge(2, 3, "c", -1);
		lattices.add(lattice);
		
		lattice = new TaggedLattice(4);
		lattice.addEdge(0, 1, "a", -1);
		lattice.addEdge(0, 2, "d", -1);
		lattice.addEdge(1, 2, "b", -1.1);
		lattice.addEdge(1, 3, "e", -1);
		lattice.addEdge(2, 3, "c", -1);
		lattices.add(lattice);
		
		return lattices;
	}
}













