package edu.neu.ccs.headword;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

public class TokenizedDocument {
	String[] lines;
	Token[][] lineTokens;
	Token[] tokens;
	int size;
	
	public TokenizedDocument(File file) throws Exception {
		this(new FileInputStream(file));
	}
	
	public TokenizedDocument(InputStream input) throws Exception {
		BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
		int position = 0;

		List<String> lineList = new LinkedList<String>();
		List<Token[]> lineTokenList = new LinkedList<Token[]>();
		List<Token> tokenList = new LinkedList<Token>();
		while (reader.ready()) {
			String line = reader.readLine();
			
			lineList.add(line.replaceAll("\\s+$",	""));
			
			LinkedList<Token> tokens = new LinkedList<Token>();
			int offset = 0;
			while (offset < line.length()) {
				while (offset < line.length() && line.charAt(offset) == ' ') offset++;
				int tokenStart = offset;
				while (offset < line.length() && line.charAt(offset) != ' ') offset++;
				if (offset - tokenStart > 0)
					tokens.add(new Token(line.substring(tokenStart, offset), position + tokenStart));
			}
			lineTokenList.add(tokens.toArray(new Token[0]));
			tokenList.addAll(tokens);
			
			position += line.length() + 1;
		}
		reader.close();
		
		lines = lineList.toArray(new String[0]);
		lineTokens = lineTokenList.toArray(new Token[0][]);
		tokens = tokenList.toArray(new Token[0]);
		size = position;
	}
	
	public void printLines(PrintStream out) {
		for (Token[] line: lineTokens) {
			for (Token token: line) {
				out.print(token + "["+token.start+"]" + " ");
			}
			out.println();
		}
	}
	
	public static void main(String[] args) {
		String filename = args[0];
		
		try {
			TokenizedDocument doc = new TokenizedDocument(new FileInputStream(filename));
			doc.printLines(System.out);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}















