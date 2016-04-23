package ocr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
//import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedList;
//import java.util.List;
//import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ocr.util.Util;

//import edu.stanford.nlp.ling.CoreLabel;
//import edu.stanford.nlp.ling.Word;
//import edu.stanford.nlp.process.CoreLabelTokenFactory;
//import edu.stanford.nlp.process.PTBTokenizer;
//import edu.stanford.nlp.process.WordTokenFactory;

public class SimpleTokenizer implements Tokenizer {
	
	public static class Token {
		public final String s;
		public final boolean precededByWhitespace;
		
		public Token(String s, boolean precededByWhitespace) {
			this.s = s;
			this.precededByWhitespace = precededByWhitespace;
		}
		
		public String toString() {
			return this.s;
		}
	}
	
	static boolean onlySymbols(String s) {
		for (int i = 0; i < s.length(); i++)
			if (Character.isLetter(s.charAt(i)))
				return false;
		return true;
	}
	
	static String[] tokenizeDiscardWhitespace(String s) {
		LinkedList<String> tokens = new LinkedList<String>();
		for (String word: s.split("\\s*\\b\\s*")) {
			if (word.matches("\\s*")) {
				continue;
			}
			else if (onlySymbols(word)) {
				for (int i = 0; i < word.length(); i++)
					tokens.add(word.substring(i, i + 1));
			}
			else {
				tokens.add(word);
			}
		}
		
		return tokens.toArray(new String[0]);
	}

	public String[] tokenize(String s) {
		return tokenize(s, false);
	}

	static String[] tokenize(String s, boolean keepWhitespace) {
		LinkedList<String> tokens = new LinkedList<String>();
		int i = 0;
		int j = 0;
		boolean inWord = false;
		boolean inSpace = false;
		while (j < s.length()) {
			char c = s.charAt(j);
			if (Character.isLetterOrDigit(c)) {
				if (!inWord) {
					if (j > 0 && (keepWhitespace || !inSpace))
						tokens.add(s.substring(i, j));
					inWord = true;
					inSpace = false;
					i = j;
				}
			}
			else if (Character.isWhitespace(c)) {
				if (!inSpace) {
					if (j > 0)
						tokens.add(s.substring(i, j));
					inWord = false;
					inSpace = true;
					i = j;
				}
			}
			else {
				if (j > 0 && (keepWhitespace || !inSpace))
					tokens.add(s.substring(i, j));
				inWord = false;
				inSpace = false;
				i = j;
			}
			
			j++;
		}
		if (keepWhitespace || !inSpace)
			tokens.add(s.substring(i, j));
		
		return tokens.toArray(new String[0]);
	}

//	static Pattern specialChar = Pattern.compile(
//			"[_'\"\u2018\u2019\u201c\u201d\\(\\)\\$%\\-\u00ad\u2014,;:\\.\\?\\!]");
//	static String[] tokenizePTB(String s) {
//		String[] tokens1 = s.split("\\s+");
//		List<String> tokens2 = new ArrayList<String>();
//		for (int i = 0; i < tokens1.length; i++) {
//			String token = tokens1[i];
//			while (token.length() > 0) {
//				if (token.equals("<num>")) {
//					tokens2.add(token);
//					break;
//				}
//				
//				Matcher m = specialChar.matcher(token);
//				if (m.find()) {
//					int j = m.start();
//					char c = token.charAt(j);
//					if (c == '\'' || c == '\u2019') {
//						if (j > 1 && j >= token.length() - 3) {
//							if (token.charAt(j - 1) == 'n') {
//								tokens2.add(token.substring(0, j-2));
//								token = token.substring(j-2);
//							}
//							else {
//								tokens2.add(token.substring(0, j-1));
//								token = token.substring(j-1);
//							}
//						}
//					}
//					else if (c == '\'' || c == '\u2018' || c == '\u2019'
//							|| c == '"' || c == '\u201c' || c == '\u201d')
//					{
//						if (j == 0 || j == token.length()) {
//						}
//					}
//				}
//			}
//		}
//	}
	
//	static String[] tokenizePTB(String s) {
//		s = s.trim();
//		LinkedList<String> tokens = new LinkedList<String>();
//		int i = 0;
//		int j = 0;
//		boolean inWord = false;
//		boolean inSpace = false;
//		while (j < s.length()) {
//			char c = s.charAt(j);
//			if (c == '.' && j == s.length() - 1) {
//				if (!inSpace)
//					tokens.add(s.substring(i, j));
//				inWord = false;
//				inSpace = false;
//				i = j;
//			}
//			else if (Character.isLetterOrDigit(c)) {
//				if (!inWord) {
//					if (j > 0 && !inSpace)
//						tokens.add(s.substring(i, j));
//					inWord = true;
//					inSpace = false;
//					i = j;
//				}
//			}
//			else if (Character.isWhitespace(c)) {
//				if (!inSpace) {
//					if (j > 0)
//						tokens.add(s.substring(i, j));
//					inWord = false;
//					inSpace = true;
//					i = j;
//				}
//			}
//			else {
//				if (j > 0 && !inSpace)
//					tokens.add(s.substring(i, j));
//				inWord = false;
//				inSpace = false;
//				i = j;
//			}
//			
//			j++;
//		}
//		if (!inSpace)
//			tokens.add(s.substring(i, j));
//		
//		return tokens.toArray(new String[0]);
//	}
	
//	static boolean isPTBAlpha(char c) {
//		return Character.isLetterOrDigit(c) || c == '-' || c == '.'
//	}
	
	static ArrayList<Token> tokenizePreservingWhitespace(String s) {
		String[] tokenStrs = tokenize(s, true);
		ArrayList<Token> tokens = new ArrayList<Token>(tokenStrs.length);
		
		boolean precededByWhitespace = false; 
		for (String tokenStr: tokenStrs) {
			if (Character.isWhitespace(tokenStr.charAt(0))) {
				precededByWhitespace = true;
			}
			else {
				tokens.add(new Token(tokenStr, precededByWhitespace));
				precededByWhitespace = false;
			}
		}
		
		return tokens;
	}
	
	static final Pattern hyphenPattern = Pattern.compile("\\s*\u00ad\\s*");
	public static String dehyphenate(String s) {
		return hyphenPattern.matcher(s).replaceAll("");
	}
	
	public static void main(String[] args) throws Exception {
		if (args[0].equals("-test")) {
			test();
			return;
		}
		File textFile = new File(args[1]);
		File tokenFile = new File(args[2]);

		BufferedReader reader =
				new BufferedReader(new InputStreamReader(new FileInputStream(textFile), "UTF-8"));
		PrintWriter writer = new PrintWriter(tokenFile, "UTF-8");
		
		Tokenizer tokenizer = new SimpleTokenizer();
		while (reader.ready()) {
			String[] tokens = tokenizer.tokenize(reader.readLine());
			writer.println(Util.join(" ", tokens));
		}
		
		reader.close();
		writer.close();
	}
	
	static void test() {
		Tokenizer tokenizer = new SimpleTokenizer();
		for (String s: new String[] {
					"Mr. \u017Fhaw\u2019s ``gift.\u201d",
					".",
					"x",
					"x.",
					"xxx.",
					"x .",
					"x...",
					"x ...",
					"f'gh^ijkl!.pqo (bar.)",
					"d1dn't.",
					"better-than-average--much bet\u00adter\u2014earnings",
					"sep \u00ad arat \u00aded hy\u00ad phens",
					"hard\u2010hyphen \u2010"})
		{
			System.out.println(s + ": " + Util.join("_", tokenizer.tokenize(s)));
		}
	}
}









