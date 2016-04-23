package edu.neu.ccs.headword;

public class WordAligner {
	
	double insCost = 1.0;
	double delCost = 1.0;
	double subCost = 1.0;
	
	boolean cheat = false;
	
	public WordAligner() {
		this(false);
	}
	public WordAligner(boolean cheat) {
		this.cheat = cheat;
	}
	
	public double alignmentCost(String[] s1, String[] s2) {
		double[][] costMatrix = new double[s1.length+1][s2.length + 1];
		
		for (int i = 0; i <= s1.length; i++)
			costMatrix[i][0] = i*delCost;
		for (int j = 0; j < s2.length; j++)
			costMatrix[0][j] = j*insCost;
		
		for (int i = 1; i <= s1.length; i++) {
			for (int j = 1; j <= s2.length; j++) {
				double cost = costMatrix[i - 1][j - 1];
				if (cheat ? !closeEnough(s1[i-1], s2[j-1]) : !s1[i-1].equals(s2[j-1]))
					cost += subCost;
				cost = Math.min(cost, costMatrix[i - 1][j] + delCost);
				cost = Math.min(cost, costMatrix[i][j - 1] + insCost);
				costMatrix[i][j] = cost;
			}
		}
		
		return costMatrix[s1.length][s2.length];
	}
	
	public static boolean closeEnough(String s1, String s2) {
		if (s1.length() != s2.length()) return false;
		
		for (int i = 0; i < s1.length(); i++) {
			char c1 = s1.charAt(i);
			char c2 = s2.charAt(i);
			if (equivalanceClass(c1) == equivalanceClass(c2)) continue;
			return false;
		}
		return true;
	}

	public static char equivalanceClass(char c) {
		if (c < 128)
			return c; // Not logically necessary but short-circuit may speed execution (?)
		
		if (c == 0x017f) // long s
			return 's';
		else if (c == '\u2018' || c == '\u2019') // single-quote
			return '\'';
		else if (c == '\u201c' || c == '\u201d') // double-quote
			return '"';
		else
			return c;
	}

	public static void main(String[] args) {
		System.out.println(new WordAligner(true)
			.alignmentCost(args[0].split("\\s+"), args[1].split("\\s+")));
		
	}

}





