package ocr

import static ocr.Util.nvl
import ocr.util.CommandLineParser;

class EisnerSattaViterbi {
	static final boolean DEBUG = false
	
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
				100 * (aval - hval)
		}
		@Override double scoreTriangle(String[] sent, int s, int t, int q, boolean left) {
			0.0
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
			def pCont = 0.0
			if (h != 0)
				pCont = grammar.prob(sent[h], left, false, hasChild)

			def pArg = 	grammar.prob(sent[h], sent[a], left)
			
			// stop the argument's opposite-direction triangle
			def argLeft = !left
			def argHasChild = a != (argLeft ? q+1 : q)
			def pStop = grammar.prob(sent[a], argLeft, true, argHasChild)
			
			def p = pCont + pArg + pStop
			
			if (DEBUG) {
				printf "Trap($h (${sent[h]}) -> $a (${sent[a]}) $q): %f = %f + %f + %f\n",
					p, pCont, pArg, pStop
			}
			
			p
		}
		
		@Override double scoreTriangle(String[] sent, int s, int t, int q, boolean left) {
			// stop the sub-triangle
			def hasChild = q != (left ? s : t)
			def p = grammar.prob(sent[q], left, true, hasChild)
			
			if (DEBUG) {
				printf "Tri($s $t $q %s) = stop($q (${sent[q]}), %s, $hasChild) = %f\n", 
					left ? 'left' : 'right', left ? 'left' : 'right', p
			}
			
			p
		}
	}

	static class Cell {
		int s
		int t
		int d
		int c
		Cell lhs
		Cell rhs
		double score
		
		Cell(int s, int t, int d, int c, Cell lhs, Cell rhs, double arcScore) {
			this.s = s
			this.t = t
			this.d = d
			this.c = c
			this.lhs = lhs
			this.rhs = rhs
			this.score = lhs.score + rhs.score + arcScore
		}
		Cell(int s, int t, int d, int c, double score=0.0) {
			this.s = s
			this.t = t
			this.d = d
			this.c = c
			this.lhs = null
			this.rhs = null
			this.score = score
		}
	}	

	Scorer scorer
	Cell[][][][] cells
	
	EisnerSattaViterbi(Scorer scorer) {
		this.scorer = scorer
	}
	
	void initialize(int len) {
		cells = new Cell[len][][][]
		(0..<len).each { int i ->
			cells[i] = new Cell[len][][]
			(0..<len).each { int j ->
				cells[i][j] = new Cell[2][]
				[LEFT, RIGHT].each { int d ->
					cells[i][j][d] = new Cell[2]
				}
			}
		}
		(0..<len).each { int i ->
			[LEFT, RIGHT].each { int d ->
				[TRI, TRAP].each { int c ->
					cells[i][i][d][c] = new Cell(i, i, d, c)
				}
			}
		}
	}
	
	double parse(String[] tokens, int[] parse) {
		def sent = new String[tokens.length+1]
		sent[0] = DMVGrammar.ROOT
		for (int i = 0; i < tokens.length; i++)
			sent[i+1] = tokens[i]
			
		initialize(sent.length)
		
		for (int m = 1; m <= sent.length; m++) {
			for (int s = 0; s < sent.length; s++) {
				int t = s + m
				if (t >= sent.length)
					break
				
				if (s > 0) {
					for (int q = s; q < t; q++) {
//System.out.println "$s $t LEFT TRAP $q"
						addCell(s, t, LEFT, TRAP, new Cell(
							s, t, LEFT, TRAP,
							cells[s][q][RIGHT][TRI], cells[q+1][t][LEFT][TRI],
							scorer.scoreTrapezoid(sent, t, s, q)))
//System.out.println "$s $t RIGHT TRAP $q"
						addCell(s, t, RIGHT, TRAP, new Cell(
							s, t, RIGHT, TRAP,
							cells[s][q][RIGHT][TRI], cells[q+1][t][LEFT][TRI],
							scorer.scoreTrapezoid(sent, s, t, q)))
					}
				} else {
					def q = 0
					addCell(s, t, RIGHT, TRAP, new Cell(
						s, t, RIGHT, TRAP,
						cells[s][q][RIGHT][TRI], cells[q+1][t][LEFT][TRI],
						scorer.scoreTrapezoid(sent, s, t, q)))
				}
				
				if (s > 0) {
					for (int q = s; q < t; q++) {
//System.out.println "$s $t LEFT TRI $q"
						addCell(s, t, LEFT, TRI, new Cell(
							s, t, LEFT, TRI,
							cells[s][q][LEFT][TRI], cells[q][t][LEFT][TRAP],
							scorer.scoreTriangle(sent, s, t, q, true)))
//System.out.println "$s $t RIGHT TRI $q"
						addCell(s, t, RIGHT, TRI, new Cell(
							s, t, RIGHT, TRI,
							cells[s][q+1][RIGHT][TRAP], cells[q+1][t][RIGHT][TRI],
							scorer.scoreTriangle(sent, s, t, q+1, false)))
					}
				}
				else if (t == sent.length - 1) {
					for (int q = s; q < t; q++) {
						addCell(s, t, RIGHT, TRI, new Cell(
							s, t, RIGHT, TRI,
							cells[s][q+1][RIGHT][TRAP], cells[q+1][t][RIGHT][TRI],
							scorer.scoreTriangle(sent, s, t, q+1, false)))
					}
				}
			}
		}
		
		populateParse(parse, 0, sent.length - 1, RIGHT, TRI)
		
		return cells[0][sent.length - 1][RIGHT][TRI].score
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
			Cell lhs = cell.lhs
			Cell rhs = cell.rhs
			populateParse(parse, lhs.s, lhs.t, lhs.d, lhs.c)
			populateParse(parse, rhs.s, rhs.t, rhs.d, rhs.c)
		}
	}
	
	void addCell(int i, int j, int d, int c, Cell cell) {
		if (cells[i][j][d][c] == null || cell.score > cells[i][j][d][c].score)
			cells[i][j][d][c] = cell
	}
	
	void printChart(PrintStream out) {
		def colWidth = 30
		def n = cells.length
		
		printPadded(out, "", colWidth)
		for (int t: 0..<n)
			printPadded(out, t.toString(), colWidth)
		out.println()
			
		for (int s: 0..<n) {
			printPadded(out, s.toString(), colWidth)
			for (int t: 0..<n)
				printPadded(out, cellString(s, t, LEFT, TRI), colWidth)
			out.println()
			
			printPadded(out, "", colWidth)
			for (int t: 0..<n)
				printPadded(out, cellString(s, t, RIGHT, TRI), colWidth)
			out.println()
			
			printPadded(out, "", colWidth)
			for (int t: 0..<n)
				printPadded(out, cellString(s, t, LEFT, TRAP), colWidth)
			out.println()
			
			printPadded(out, "", colWidth)
			for (int t: 0..<n)
				printPadded(out, cellString(s, t, RIGHT, TRAP), colWidth)
			out.println()
		}
	}
	String cellString(int s, int t, int d, int c) {
		def cell = cells[s][t][d][c]
		def children = "         "
		if (cell?.rhs != null) {
			children = String.format("%d%d%d%d %d%d%d%d",
				cell.lhs.s, cell.lhs.t, cell.lhs.d, cell.lhs.c,
				cell.rhs.s, cell.rhs.t, cell.rhs.d, cell.rhs.c)
		}
		String.format("%d%d: %s %.3f",
			d, c,
			children,
			cell != null ? cell.score : Double.NEGATIVE_INFINITY)
	}
	void printPadded(PrintStream out, String s, int padding) {
		out.print s
		for (int i = s.length(); i < padding; i++)
			out.print " "
	}

	static void main(String[] args) {
		CommandLineParser clp = new CommandLineParser("-model=s -sent=s", args)
		
		def model = clp.opt("-model", null as String)
		def sentArg = clp.opt("-sent", null as String)
		
		def scorer
		if (model == null)
			scorer = new LetterScorer()
		else
			scorer = new DMVScorer(new DMVGrammar(model))
			
		def test = new EisnerSattaViterbi(scorer)

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
		
		sentences.each { String str ->
			def sent = str.split(" ")
			def parse = new int[sent.length]
			def score = test.parse(sent, parse)
			
			println str
			printf "%f %s\n", score, parse.toString()
			if (DEBUG)
				test.printChart(System.out)
			println()
		}
	}
}





