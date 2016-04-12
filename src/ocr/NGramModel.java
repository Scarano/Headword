package ocr;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map.Entry;

import static ocr.Util.nvl;

public class NGramModel {
	
	private static final String SPECIAL_TOTAL_TOKEN = " ";
	
	public static class TokenHistory {
		String token;
		TokenHistory prev = null;
		
		public TokenHistory(String token) {
			this.token = token;
		}
		
		public TokenHistory(String token, TokenHistory prev) {
			this.token = token;
			this.prev = prev;
		}
		
		public TokenHistory add(String token) {
			return new TokenHistory(token, this);
		}
		
		public String nGramString(int n) {
			if (n == 1 || prev == null)
				return token;
			else
				return prev.nGramString(n - 1) + " " + token;
		}
		
		public String toString() {
			if (prev == null) 
				return token;
			else
				return prev.toString() + " " + token;
		}
	}
	
	int n;
	HashMap<String, Counter<String>> counts;
	NGramModel submodel = null;
	
	// TODO: Create special unigram model as performance optimization
	public NGramModel(int n) {
		this.n = n;
		counts = new HashMap<String, Counter<String>>();
		if (n > 1)
			submodel = new NGramModel(n - 1);
	}
	
	public void addTokens(String[] tokens) {
		TokenHistory history = null;
		for (String token: tokens) {
			if (!Character.isWhitespace(token.charAt(0))) {
				history = new TokenHistory(token, history);
				addTokenHistory(history);
			}
		}
	}
	
	String conditionKey(TokenHistory history) {
		if (history.prev == null || n == 1)
			return "";
		else
			return history.prev.nGramString(n - 1);
	}
	
	public void addTokenHistory(TokenHistory history) {
		String conditionKey = conditionKey(history);
		Counter<String> counter = counts.get(conditionKey);
		if (counter == null) {
			counter = new Counter<String>();
			counts.put(conditionKey, counter);
		}
		counter.increment(history.token);
		counter.increment(SPECIAL_TOTAL_TOKEN);

		if (submodel != null)
			submodel.addTokenHistory(history);
	}
	
	public double prob(TokenHistory history) {
//System.out.println(history);
		double p = 0.0;
		String conditionKey = conditionKey(history);
		Counter<String> counter = counts.get(conditionKey);
		if (counter != null) {
			double V = counter.size() - 1.0;
			double N = counter.get(SPECIAL_TOTAL_TOKEN);
			p = nvl(counter.tryToGet(history.token), 0.0) / N;
			double pBackoff = 1/(V+1.0); // Default for unigram model XXX not right - get this right
			if (submodel != null)
				pBackoff = submodel.prob(history);
			double lambda = N / (N + V); // XXX
//System.out.println(lambda + " * " + p + " + " + (1.0-lambda) + " * " + pBackoff);
			return lambda*p + (1.0 - lambda)*pBackoff + 0.2; // XXX 
		}
		else {
			return submodel.prob(history);
		}
	}
	
	public double logProb(TokenHistory history) {
		return Math.log(prob(history));
	}
	
	public void print(PrintStream out) {
		out.println(n + "-gram model:");
		for (Entry<String, Counter<String>> counterEntry: counts.entrySet()) {
			out.println("For model conditioned on '" + counterEntry.getKey() + "':");
			for (Entry<String, Counter.Count> count: counterEntry.getValue()) {
				String token = count.getKey();
				double value = count.getValue().value;
				out.println("  count(" + token + ") = " + value);
			}
		}
		if (submodel != null)
			submodel.print(out);
	}

	public static void main(String[] args) {
		NGramModel model = new NGramModel(3);
		model.addTokens("a a a b b".split(" "));
		model.print(System.out);
		
		TokenHistory history = new TokenHistory("a");
		System.out.println("p(" + history.toString() + ") = " + model.prob(history));
		history = history.add("a");
		System.out.println("p(" + history.toString() + ") = " + model.prob(history));
		history = history.add("a");
		System.out.println("p(" + history.toString() + ") = " + model.prob(history));
		history = history.add("b");
		System.out.println("p(" + history.toString() + ") = " + model.prob(history));
		history = history.add("a");
		System.out.println("p(" + history.toString() + ") = " + model.prob(history));
		history = history.add("x");
		System.out.println("p(" + history.toString() + ") = " + model.prob(history));
		history = history.add("a");
		System.out.println("p(" + history.toString() + ") = " + model.prob(history));
	}

}









