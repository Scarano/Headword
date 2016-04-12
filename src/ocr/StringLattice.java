package ocr;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static ocr.Util.logSum;

public class StringLattice {

	static final String START = "<s>";
	static final String END = "</s>";
	static final String EPSILON = "NULL";
	
	public static class Edge {
		public final int start;
		public final int end;
		public final String token;
		public final double logProb;
		
		public Edge(int start, int end, String token, double logProb) {
			this.start = start;
			this.end = end;
			this.token = token;
			this.logProb = logProb;
		}
		
		public String toString() {
			return String.format("[%d %d %s %f]", start, end, token, logProb);
		}
	}
	
	int numPositions;
	List<Edge> edges;
	ArrayList<ArrayList<Edge>> outEdges = null;
	
	public StringLattice(int numPositions) {
		this.numPositions = numPositions;
		edges = new ArrayList<Edge>();
	}
	
	public StringLattice(String[] sent) {
		numPositions = sent.length + 1;
		edges = new ArrayList<Edge>(sent.length);
		for (int i = 0; i < sent.length; i++)
			edges.add(new Edge(i, i + 1, sent[i], 0.0));
	}
	
	public void addEdge(int start, int end, String token, double logProb) {
		edges.add(new Edge(start, end, token, logProb));
		outEdges = null;
	}
	
	public void complete() {
	}
	
	public Iterable<Edge> getEdges() {
		return edges;
	}
	
	public ArrayList<Edge> outEdges(int state) {
		if (outEdges == null)
			populateOutEdges();
		return outEdges.get(state);
	}
	
	protected void populateOutEdges() {
		outEdges = new ArrayList<ArrayList<Edge>>(numPositions);
		for (int i = 0; i < numPositions; i++)
			outEdges.add(new ArrayList<Edge>());
		for (Edge edge: edges)
			outEdges.get(edge.start).add(edge);
	}
	
	public StringLattice collapseToTags(Clustering clustering) {
		StringLattice tagLattice = new StringLattice(numPositions);
		
		for (Edge edge: edges) {
			tagLattice.addEdge(
					edge.start, edge.end, clustering.clusterOfWord(edge.token), edge.logProb);
		}
		
		return tagLattice.mergeEquivalentEdges();
	}
	
	public StringLattice mergeEquivalentEdges() {
		Map<String, List<Edge>> edgesByKey = new HashMap<String, List<Edge>>();
		for (Edge edge: edges) {
			String key = edge.start + ':' + edge.end + ':' + edge.token;
			if (!edgesByKey.containsKey(key))
				edgesByKey.put(key, new ArrayList<Edge>());
			edgesByKey.get(key).add(edge);
		}
		
		StringLattice newLattice = new StringLattice(numPositions);
		for (List<Edge> mergeableEdges: edgesByKey.values()) {
			double p = Double.NEGATIVE_INFINITY;
			for (Edge edge: mergeableEdges)
				p = logSum(p, edge.logProb);
			Edge edge0 = mergeableEdges.get(0);
			newLattice.addEdge(edge0.start, edge0.end, edge0.token, p);
		}
		
		return newLattice;
	}
	
	public static class Subpath {
		public double score;
		public Edge edge;
		public Subpath rest;
		public Subpath(Edge edge, Subpath rest) {
			this.score = (rest == null ? 0.0 : rest.score) + edge.logProb;
			this.edge=edge;
			this.rest = rest;
		}
		public List<Edge> addEdges(List<Edge> edges) {
			edges.add(edge);
			if (rest != null)
				rest.addEdges(edges);
			return edges;
		}
	}
	
	public List<Edge> bestPath(String[] seq) {
		Subpath path = bestPath(seq, 0, 0);
		if (path == null)
			return null;
		return path.addEdges(new ArrayList<Edge>());
	}
	
	// This recursive best-path search is exponential-time in the worst case, but
	// for the lattices I'll be feeding it, which are mostly "sausages", I don't think 
	// performance will be an issue.
	public Subpath bestPath(String[] seq, int nextItem, int state) {
//System.out.printf("bestPath(%s, %d, %d)\n", Arrays.toString(seq), nextItem, state);

		if (state == numPositions - 1)
			return null;
		else if (nextItem == seq.length)
			return null;
		
		Subpath bestSubpath = null;
		for (Edge edge: outEdges(state)) {
			if (edge.token.equals(seq[nextItem])) {
				if (nextItem == seq.length - 1 && edge.end == numPositions - 1) {
					bestSubpath = new Subpath(edge, null);
				}
				else {
					Subpath subPath = bestPath(seq, nextItem + 1, edge.end);
					if (subPath != null) {
						subPath = new Subpath(edge, subPath);
						if (bestSubpath == null || subPath.score > bestSubpath.score)
							bestSubpath = subPath;
					}
				}
			}
		}
		return bestSubpath;
	}
	
	
	public String toString() {
		StringBuilder builder = new StringBuilder();
		
		ArrayList<Edge> sortedEdges = new ArrayList<Edge>(edges);
		Collections.sort(sortedEdges, new Comparator<Edge>() {
			@Override public int compare(Edge a, Edge b) {
				if (a.start != b.start)
					return a.start - b.start;
				else if (a.end != b.end)
					return a.end - b.end;
				else if (!a.token.equals(b.token))
					return a.token.compareTo(b.token);
				else
					return 0;
			}
		});
		
		int lastStartState = 0;
		builder.append("0: ");
		for (Edge edge: sortedEdges) {
			if (edge.start != lastStartState) {
				builder.append("\n" + edge.start + ": ");
				lastStartState = edge.start;
			}
			builder.append(String.format("%s +%d->%d %.3f;  ", 
					edge.token, edge.end - edge.start, edge.end, Math.exp(edge.logProb)));
		}
		
		return builder.toString();
	}
	
	static class PFSGEdge {
		public int fromNode;
		public int toNode;
		public double cost;
		
		public PFSGEdge(int fromNode, int toNode, double cost) {
			this.fromNode = fromNode; this.toNode = toNode; this.cost = cost;
		}
	}
	
	// Save as a PFSG file. PFSG puts outputs at nodes, not edges, so new nodes must be 
	// added for each edge, and sentence positions become epsilon nodes.
	public void savePFSG(File file, int sentenceNumber) throws IOException {
		PrintWriter writer = new PrintWriter(file, "UTF-8");
		
		List<String> nodes = new LinkedList<String>();
		List<PFSGEdge> transitions = new LinkedList<PFSGEdge>();
		
		nodes.add(START);
		for (int i = 0; i < numPositions - 2; i++)
			nodes.add(EPSILON);
		int finalNode = nodes.size();
		nodes.add(END);

		for (Edge edge: edges) {
			int nodeID = nodes.size();
			nodes.add(edge.token);
			
			transitions.add(new PFSGEdge(edge.start, nodeID, edge.logProb));
			transitions.add(new PFSGEdge(nodeID, edge.end, 0));
		}
		
		
		writer.println("name s" + sentenceNumber);
		
		writer.print("nodes " + nodes.size());
		for (String node: nodes)
			writer.print(" " + node);
		writer.println();
		
		writer.println("initial 0");
		writer.println("final " + finalNode);
		
		writer.println("transitions " + transitions.size());
		for (PFSGEdge transition: transitions)
			writer.println(
				transition.fromNode + " " + transition.toNode + " " + 10000.5 * transition.cost);
		
		writer.close();
	}
	
	public static StringLattice lengthLattice(String[] vocab, int length) {
		StringLattice lattice = new StringLattice(length + 1);
		double p = Math.log(1.0/vocab.length);
		for (int i = 0; i < length; i++) {
			for (String word: vocab)
				lattice.addEdge(i, i+1, word, p);
		}
		return lattice;
	}
	
	public static StringLattice weightedLengthLattice(
			String[] vocab, int length, Map<String, Double> counts)
	{
		double total = 0.0;
		for (String word: vocab)
			if (counts.containsKey(word))
				total += counts.get(word);
		
		StringLattice lattice = new StringLattice(length + 1);
		for (int i = 0; i < length; i++) {
			for (String word: vocab)
				if (counts.containsKey(word))
					lattice.addEdge(i, i+1, word, Math.log(counts.get(word)/total));
		}
		return lattice;
	}
	
	public static void main(String[] args) {
//		collapseToTagTest(args[0]);
		bestPathTest();
	}

	static void collapseToTagTest(String clusterFile) {
		Clustering clustering = new Clustering(new File(clusterFile), -5.0);
		StringLattice lattice = new StringLattice(3);
		lattice.addEdge(0, 1, "The", Math.log(0.8));
		lattice.addEdge(0, 1, "They", Math.log(0.1));
		lattice.addEdge(1, 2, "thing", Math.log(0.9));
		lattice.addEdge(1, 2, "pontificate", Math.log(0.1));
		lattice.addEdge(0, 2, "Thething", Math.log(0.1));
		System.out.println(lattice.collapseToTags(clustering).toString());
	}
	
	static void bestPathTest() {
		StringLattice lattice = new StringLattice(4);
		lattice.addEdge(0, 1, "a", -10);
		lattice.addEdge(0, 1, "b", -10);
		lattice.addEdge(1, 3, "c", -5);
		lattice.addEdge(1, 3, "d", -15);
		lattice.addEdge(0, 2, "a", -10);
		lattice.addEdge(2, 3, "c", -11);
		lattice.addEdge(2, 3, "d", -1);
		
		for (String s: new String[] {"a", "c", "a b", "a c", "a d", "b d", "a c d"}) {
			String[] seq = s.split(" ");
			Subpath path = lattice.bestPath(seq, 0, 0);
			if (path == null)
				System.out.printf("%s: no path\n", s);
			else
				System.out.printf("%s: %f; %s\n",
						s, path.score, path.addEdges(new ArrayList<Edge>()).toString());
		}
	}
	static void bestPathTest2() {
		StringLattice lattice = new StringLattice(5);
		lattice.addEdge(0, 1, "a", -1);
		lattice.addEdge(1, 2, "b", -1);
		lattice.addEdge(2, 3, "c", -1);
		lattice.addEdge(3, 4, "d", -1);
		
		for (String s: new String[] {"a", "a b", "a b c", "a b c d", "a b c d e", "a b c d e f"}) {
			String[] seq = s.split(" ");
			Subpath path = lattice.bestPath(seq, 0, 0);
			if (path == null)
				System.out.printf("%s: no path\n", s);
			else
				System.out.printf("%s: %f; %s\n",
						s, path.score, path.addEdges(new ArrayList<Edge>()).toString());
		}
	}
}









