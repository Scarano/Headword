package edu.neu.ccs.headword;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

public class OCRText {
	String[][] lines;
	String[] tokens;

	public OCRText(File file) throws Exception {
		List<String[]> lineList = new LinkedList<String[]>();
		List<String> tokenList = new LinkedList<String>();
		BufferedReader reader = new BufferedReader(new FileReader(file));
		while (reader.ready()) {
			String line = reader.readLine();
			if (line.matches("\\s*"))
				continue;

			String[] tokens = line.split("\\s+");
			if (tokens.length > 0) {
				lineList.add(tokens);
				for (String token: tokens)
					tokenList.add(token);
			}
		}
		reader.close();
		
		lines = lineList.toArray(new String[0][]);
		tokens = tokenList.toArray(new String[0]);
	}
	
	public void printLines(PrintStream out) {
		for (String[] line: lines) {
			for (String token: line) {
				out.print(token);
				out.print(" ");
			}
			out.println();
		}
	}
	
	public static void main(String[] args) {
		String filename = args[0];
		
		try {
			OCRText ocr = new OCRText(new File(filename));
			ocr.printLines(System.out);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

}
