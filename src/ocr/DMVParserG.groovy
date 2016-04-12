package ocr

import static ocr.Util.nvl
import ocr.StringLattice.Edge;
import ocr.util.CommandLineParser;

class DMVParserG {
	static boolean debug = false
	
	static final int LEFT = 0
	static final int RIGHT = 1
	static final int TRI = 0
	static final int TRAP = 1
	
	static interface Scorer {
		double scoreTrapezoid(String[] sent, int h, int a, int q);
		double scoreTriangle(String[] sent, int s, int t, int q, boolean left);
	}
	
	static class LetterScorer implements Scorer {
		@Override double scoreTrapezoid(String[] sent, int h, int a, int q) {
			def aval = sent[a].charAt(0) - ('a' as char) + 1
			
			if (h == 0)
				return -aval
				
			def hval = sent[h].charAt(0) - ('a' as char) + 1
			if (hval < aval)
				hval - aval
			else
				(aval - hval) - 1
		}
		@Override double scoreTriangle(String[] sent, int s, int t, int q, boolean left) {
			0.0D
		}
	}
	
	static class DMVScorer implements Scorer {
		DMVGrammar grammar;
		
		DMVScorer(DMVGrammar grammar) {
			this.grammar = grammar;
		}
		
		@Override double scoreTrapezoid(String[] sent, int h, int a, int q) {
			def left = a < h
			def hasChild = h != (left ? q+1 : q)
			def pCont = 0.0D
			if (h != 0)
				pCont = grammar.prob(sent[h], left, false, hasChild)

			def pArg = 	grammar.prob(sent[h], sent[a], left)
			
			// stop the argument's opposite-direction triangle
			def argLeft = !left
			def argHasChild = a != (argLeft ? q+1 : q)
			def pStop = grammar.prob(sent[a], argLeft, true, argHasChild)
			
			def p = pCont + pArg + pStop
			
			if (debug) {
				printf "Trap($h (${sent[h]}) -> $a (${sent[a]}) $q): %f = %f + %f + %f\n",
					p, pCont, pArg, pStop
			}
			
			p
		}
		
		@Override double scoreTriangle(String[] sent, int s, int t, int q, boolean left) {
			// stop the sub-triangle
			def hasChild = q != (left ? s : t)
			def p = grammar.prob(sent[q], left, true, hasChild)
			
			if (debug) {
				printf "Tri($s $t $q %s) = stop($q (${sent[q]}), %s, $hasChild) = %f\n", 
					left ? 'left' : 'right', left ? 'left' : 'right', p
			}
			
			p
		}
	}

	static class Arc {
		int q // the split point between the lhs and rhs
		Cell lhs
		Cell rhs
		double prob
		
		Arc(int q, Cell lhs, Cell rhs, double prob) {
			this.q = q
			this.lhs = lhs
			this.rhs = rhs
			this.prob = prob
		}
	}
	static class Cell {
		int s
		int t
		int d
		int c
		double viterbiProb = Double.NEGATIVE_INFINITY
		double inProb = Double.NEGATIVE_INFINITY
		double outProb = Double.NEGATIVE_INFINITY
		double prob = Double.NEGATIVE_INFINITY
		Arc[] arcs
		Arc viterbiArc = null
		
		Cell(int s, int t, int d, int c, double prob=Double.NEGATIVE_INFINITY) {
			this.s = s
			this.t = t
			this.d = d
			this.c = c
			this.viterbiProb = prob
			this.inProb = prob
			arcs = new Arc[t+1]
		}
	}	

	boolean zeroBased
	Scorer scorer
	Cell[][][][] cells
	int sentLen // Not including root symbol
	String[] sent // Including root symbol -- contains sentLen + 1 items
	
	DMVParserG(Scorer scorer, boolean zeroBased=false) {
		this.scorer = scorer
		this.zeroBased = zeroBased
	}
	
	void initialize(int len) {
		cells = new Cell[len][][][]
		(0..<len).each { int i ->
			cells[i] = new Cell[len][][]
			(0..<len).each { int j ->
				cells[i][j] = new Cell[2][]
				[LEFT, RIGHT].each { int d ->
					cells[i][j][d] = new Cell[2]
					[TRI, TRAP].each { int c ->
						def prob = i == j ? 0.0D : Double.NEGATIVE_INFINITY
						cells[i][j][d][c] = new Cell(i, j, d, c, prob)
					}
				}
			}
		}
	}
	
	int[] parse(String[] tokens) {
		this.sentLen = tokens.length

		sent = new String[tokens.length+1]
		sent[0] = DMVGrammar.ROOT
		for (int i = 0; i < tokens.length; i++)
			sent[i+1] = tokens[i]
			
		initialize(sent.length)
		
		// Populate arcs, calculate viterbi and inside probabilities
		for (int m = 1; m < sent.length; m++) {
			for (int s = 0; s < sent.length; s++) {
				int t = s + m
				if (t >= sent.length)
					break
				
				if (s > 0) {
					for (int q = s; q < t; q++) {
						addArc(
							s, t, LEFT, TRAP, q,
							cells[s][q][RIGHT][TRI], cells[q+1][t][LEFT][TRI],
							scorer.scoreTrapezoid(sent, t, s, q))
						addArc(
							s, t, RIGHT, TRAP, q,
							cells[s][q][RIGHT][TRI], cells[q+1][t][LEFT][TRI],
							scorer.scoreTrapezoid(sent, s, t, q))
					}
				} else {
					def q = 0
					addArc(
						s, t, RIGHT, TRAP, q,
						cells[s][q][RIGHT][TRI], cells[q+1][t][LEFT][TRI],
						scorer.scoreTrapezoid(sent, s, t, q))
				}
				
				if (s > 0) {
					for (int q = s; q < t; q++) {
						addArc(
							s, t, LEFT, TRI, q,
							cells[s][q][LEFT][TRI], cells[q][t][LEFT][TRAP],
							scorer.scoreTriangle(sent, s, t, q, true))
					}
					for (int q = s + 1; q <= t; q++) {
						addArc(
							s, t, RIGHT, TRI, q,
							cells[s][q][RIGHT][TRAP], cells[q][t][RIGHT][TRI],
							scorer.scoreTriangle(sent, s, t, q, false))
					}
				}
				else if (t == sent.length - 1) {
					for (int q = s + 1; q <= t; q++) {
						addArc(
							s, t, RIGHT, TRI, q,
							cells[s][q][RIGHT][TRAP], cells[q][t][RIGHT][TRI],
							scorer.scoreTriangle(sent, s, t, q, false))
					}
				}
			}
		}
		
		// populate outside probabilities
		cells[0][sent.length-1][RIGHT][TRI].outProb = 0.0D
		for (int m = sent.length - 1; m > 0; m--) {
			[TRI, TRAP].each { c ->
				for (int s = 0; s < sent.length; s++) {
					int t = s + m
					if (t >= sent.length)
						break

					[LEFT, RIGHT].each { d ->
						def cell = cells[s][t][d][c]
						for (int q = s; q <= t; q++) {
							def arc = cells[s][t][d][c].arcs[q]
							if (arc != null) {
								arc.lhs.outProb = Util.logSum(arc.lhs.outProb, 
									cell.outProb + arc.rhs.inProb + arc.prob)
								arc.rhs.outProb = Util.logSum(arc.rhs.outProb,
									cell.outProb + arc.lhs.inProb + arc.prob)
							}
						}
					}
				}
			}
		}
		
		// populate total probabilities
		for (int s = 0; s < sent.length; s++) {
			for (int t = s; t < sent.length; t++) {
				for (int c: [TRI, TRAP]) {
					for (int d: [LEFT, RIGHT]) {
						def cell = cells[s][t][d][c]
						cell.prob = cell.inProb + cell.outProb
					}
				}
			}
		}
		
		def parse = new int[sentLen]
		populateParse(parse, 0, sent.length - 1, RIGHT, TRI)
		if (zeroBased) {
			for (int i = 0; i < parse.length; i++)
				parse[i] = parse[i] - 1
		}
		
		parse
	}
	
	void addArc(int s, int t, int d, int c, int q, Cell lhs, Cell rhs, double prob) {
		def cell = cells[s][t][d][c]
		def arc = new Arc(q, lhs, rhs, prob)
		cell.arcs[q] = arc
		
		def viterbiProb = lhs.viterbiProb + rhs.viterbiProb + prob
		if (viterbiProb > cell.viterbiProb) {
			cell.viterbiArc = arc
			cell.viterbiProb = viterbiProb
		}
		
		def inProb = lhs.inProb + rhs.inProb + prob
		cell.inProb = Util.logSum(cell.inProb, inProb)
	}
	
	void populateParse(int[] parse, int s, int t, int d, int c) {
		if (t > s) {
			if (c == TRAP) {
				if (d == RIGHT)
					parse[t-1] = s
				else
					parse[s-1] = t
			}
			Cell cell = cells[s][t][d][c]
			Cell lhs = cell.viterbiArc.lhs
			Cell rhs = cell.viterbiArc.rhs
			populateParse(parse, lhs.s, lhs.t, lhs.d, lhs.c)
			populateParse(parse, rhs.s, rhs.t, rhs.d, rhs.c)
		}
	}
	
	void addSoftCounts(DMVCounter estimator) {
		double Z = sentProb()
		
		for (int s = 0; s <= sentLen; s++) {
			for (int t = s; t <= sentLen; t++) {
				for (int d: [LEFT, RIGHT]) {
					for (int c: [TRI, TRAP]) {
						def left = d==0
						def cell = cells[s][t][d][c]
						for (int q = s; q <= t; q++) {
							def arc = cell.arcs[q]
							if (arc != null) {
								def count = cell.outProb + arc.lhs.inProb + arc.rhs.inProb +
									arc.prob - Z

								if (c == 0) {
									// Handle the triangle arc (s, t, d, 0, q)
									def hasChild = q != (left ? s : t)
									estimator.add(sent[q], left, true, hasChild, count)
								}
								else {
									// Handle the trapezoid arc (s, t, d, 1, q)
									def h = left ? t : s
									def a = left ? s : t
									def hasChild = h != (left ? q+1 : q)
									
									if (h != 0)
										estimator.add(sent[h], left, false, hasChild, count)
									
									estimator.add(sent[h], sent[a], left, count)
									
									def argLeft = !left
									def argHasChild = a != (argLeft ? q+1 : q)
									estimator.add(sent[a], argLeft, true, argHasChild, count)
								}
							}
						}
					}
				}
			}
		}
	}
	
	double sentProb() {
		cells[0][sentLen][RIGHT][TRI].inProb
	}
	
	double viterbiProb() {
		cells[0][sentLen][RIGHT][TRI].viterbiProb
	}
	
	void printChart(PrintStream out) {
		def colWidth = 45
		def n = cells.length
		
		printPadded(out, "", 8)
		for (int t: 0..<n)
			printPadded(out, t.toString(), colWidth)
		out.println()
			
		for (int s: 0..<n) {
			out.println()
			[TRI, TRAP].each { c ->
				[LEFT, RIGHT].each { d ->
					if (c == TRI && d == LEFT)
						printPadded(out, s.toString(), 8)
					else
						printPadded(out, "", 8)
						
					for (int t: 0..<n)
						printPadded(out, cellString(s, t, d, c), colWidth)
					out.println()
				}
			}
		}
	}
	String cellString(int s, int t, int d, int c) {
		def cell = cells[s][t][d][c]
		if (cell == null) {
			String.format("%d%d:", d, c)
		}
		else {
			def arc = cell.viterbiArc
			def children = "         "
			if (arc?.rhs != null) {
				children = String.format("%d%d%d%d %d%d%d%d",
					arc.lhs.s, arc.lhs.t, arc.lhs.d, arc.lhs.c,
					arc.rhs.s, arc.rhs.t, arc.rhs.d, arc.rhs.c)
			}
			String.format("%d%d: %s  %9.3f %9.3f %6.4f",
				d, c,
				children,
				cell.inProb,
				cell.outProb,
				Math.exp(cell.prob - sentProb()))
		}
	}
	
	void printPadded(PrintStream out, String s, int padding) {
		out.print s
		for (int i = s.length(); i < padding; i++)
			out.print " "
	}
	
	static void printParse(PrintStream out, String[] sent, String[] tags, int[] parse) {
		def wordWidth = 8
		def arcs = []
		def search = [0]
		while (search.size() > 0) {
			def newSearch = []
			parse.eachWithIndex { h, i ->
				def a = i + 1
				if (h in search) {
					arcs << [h, a]
					newSearch << a
				}
			}
			search = newSearch
		}
		arcs.each { h, a ->
			def arrow = '-' * (Math.abs(h - a) * wordWidth - 1)
			if (h > a)
				arrow = '<' + arrow
			else
				arrow = arrow + '>'
			out.println ' ' * ([h, a].min() * wordWidth + 5) + arrow
		}
		def cutoff = wordWidth - 1
		out.print ' ' * wordWidth
		tags.eachWithIndex { tag, i ->
			def wordStr = ''
			if (parse[i] != -1) wordStr = i + ' ' +  tag
			out.printf "%${cutoff}.${cutoff}s ", wordStr
		}
		out.println()
		out.print ' ' * wordWidth
		sent.eachWithIndex { word, i ->
			out.printf "%${cutoff}.${cutoff}s ", word
		}
		out.println()
		if (-1 in parse) {
			// Print unused lattice edges
			out.print ' ' * wordWidth
			sent.eachWithIndex { word, i ->
				def wordStr = ''
				if (parse[i] == -1) wordStr = i + ' ' +  word
				out.printf "%${cutoff}.${cutoff}s ", wordStr
			}
			out.println()
		}
	}

	static void printParse(PrintStream out, List<Edge> edges, int[] parse) {
		printParse(out, edges.collect { it.token } as String[], parse)
	}
	static void printParseTagged(PrintStream out, List<TaggedLattice.Edge> edges, int[] parse) {
		def words = edges.collect { it.token.getString() } as String[]
		def tags = edges.collect { it.token.getTag() } as String[]
		printParse(out, words, tags, parse)
	}

	static void main(String[] args) {
		CommandLineParser clp = new CommandLineParser(
			"-model=s -sent=s -reestimate=s -debug", args)
		
		def model = clp.opt("-model", null as String)
		def sentArg = clp.opt("-sent", null as String)
		def outputModel = clp.opt("-reestimate", null as String)
		debug = clp.opt("-debug")
		
		def scorer
		if (model == null)
			scorer = new LetterScorer()
		else
			scorer = new DMVScorer(new DMVGrammar(model))
			
		def parser = new DMVParserG(scorer)

		def sentences = []
		if (sentArg != null) {
			sentences << sentArg
		}
		else if (clp.args().length > 0) {
			new File(clp.arg(0)).eachLine("utf-8") { sentences << it }
		}
		else {
			sentences = [
				"a b c",
				"c b a",
				"b a c",
				"c b a b",
				"b c a b"]
		}
		
		def estimator = new DMVCounter()
		
		sentences.each { String str ->
			def sent = str.split(" ")
			def parse = parser.parse(sent)
			def score = parser.viterbiProb()
			
			if (outputModel != null)
				parser.addSoftCounts(estimator)
			
			println str
			printf "%f %s\n", score, parse.toString()
			if (debug)
				parser.printChart(System.out)
			println()
		}
		
		if (outputModel != null) {
			if (debug)
				estimator.print(System.out)
		
			estimator.createGrammar().save(outputModel)
		}
		
	}
}





