package edu.neu.ccs.headword;

public class Token {
	public final String string;
	public final int start;
	
	public Token(String string, int start) {
		this.string = string;
		this.start = start;
	}
	
	public String toString() {
		return string;
	}
	
	public static String join(String sep, Token[] tokens) {
		if (tokens.length == 0)
			return "";
		
		StringBuilder builder = new StringBuilder(100);
		builder.append(tokens[0].string);
		for (int i = 1; i < tokens.length; i++) {
			builder.append(sep);
			builder.append(tokens[i].string);
		}
		
		return builder.toString();
	}

}
