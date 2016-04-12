package ocr;

public class ArrayAligner {
	
	double insCost = 1.0;
	double delCost = 1.0;
	double subCost = 1.0;
	
	boolean useEquals = true;
	
	public ArrayAligner() {
		this(true);
	}
	// Call with useEquals = false to optimize for intern()ed Strings
	public ArrayAligner(boolean useEquals) {
		this.useEquals = useEquals;
	}
	
	public double alignmentCost(Object[] s1, Object[] s2) {
		double[][] costMatrix = new double[s1.length+1][s2.length + 1];
		
		for (int i = 0; i <= s1.length; i++)
			costMatrix[i][0] = i*delCost;
		for (int j = 0; j < s2.length; j++)
			costMatrix[0][j] = j*insCost;
		
		for (int i = 1; i <= s1.length; i++) {
			for (int j = 1; j <= s2.length; j++) {
				double cost = costMatrix[i - 1][j - 1];
				if (useEquals ? !s1[i-1].equals(s2[j-1]) : s1[i-1] == s2[j-1])
					cost += subCost;
				cost = Math.min(cost, costMatrix[i - 1][j] + delCost);
				cost = Math.min(cost, costMatrix[i][j - 1] + insCost);
				costMatrix[i][j] = cost;
			}
		}
		
		return costMatrix[s1.length][s2.length];
	}
	
	public static void main(String[] args) {
		
		System.out.println(new ArrayAligner(false)
			.alignmentCost(internedTokens(args[0]), internedTokens(args[1])));
	}

	private static String[] internedTokens(String s) {
		String[] tokens = s.split("\\s+");
		for (int i = 0; i < tokens.length; i++)
			tokens[i] = tokens[i].intern();
		return tokens;
	}
}
