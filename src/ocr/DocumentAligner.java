package ocr;

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import ocr.Aligner.Unit;

public class DocumentAligner {

	public static class LineUnit implements Unit {
		Token[] tokens;
		HashSet<String> tokenSet;
		
		public LineUnit(Token[] tokens) {
			this.tokens = tokens;
			tokenSet = new HashSet<String>();
			for (Token token: tokens)
				tokenSet.add(token.string);
		}

		@Override
		public float distanceTo(Unit u) {
			LineUnit other = (LineUnit) u;
			
			float commonTokens = 0.0F;
			for (Token otherToken: other.tokens) {
				if (tokenSet.contains(otherToken.string))
					commonTokens += 1.0F;
			}
			
			int maxLength = tokens.length > other.tokens.length ? tokens.length : other.tokens.length;
			
			return 1.0F - commonTokens / maxLength;
		}
	}
	
	public static class CharUnit implements Unit {
		
		static final float MISMATCH_PENALTY = 1.5F;
		static final float SPACE_MISMATCH_PENALTY = 2.0F;
		
		char c;
		
		public CharUnit(char c) {
			this.c = c;
		}

		@Override
		public float distanceTo(Unit u) {
			CharUnit other = (CharUnit) u;
			if (c == other.c)
				return 0.0F;
			if (c == ' ' || other.c == ' ')
				return SPACE_MISMATCH_PENALTY;
			return MISMATCH_PENALTY;
		}
		
		public String toString() {
			return new String(new char[] {c});
		}
	}
	
	public static int[] alignStrings(String startString, String endString) {
		CharUnit[] startUnits = new CharUnit[startString.length()];
		for (int i = 0; i < startString.length(); i++)
			startUnits[i] = new CharUnit(startString.charAt(i));
		CharUnit[] endUnits = new CharUnit[endString.length()];
		for (int i = 0; i < endString.length(); i++)
			endUnits[i] = new CharUnit(endString.charAt(i));
		
		Aligner aligner = new Aligner(startUnits, endUnits, true);

		return aligner.align();
	}
	
	public static int[] alignLineSequences(Token[][] startLines, Token[][] endLines) {
		LineUnit[] startUnits = new LineUnit[startLines.length];
		for (int i = 0; i < startLines.length; i++)
			startUnits[i] = new LineUnit(startLines[i]);
		LineUnit[] endUnits = new LineUnit[endLines.length];
		for (int j = 0; j < endLines.length; j++)
			endUnits[j] = new LineUnit(endLines[j]);
		
		Aligner aligner = new Aligner(startUnits, endUnits, true);

		return aligner.align();
	}
	
	public static class DocumentAlignment {
		public int[] lineAlignment;
		public int[][] charAlignments;
		int chars = 0;
		int matchingChars = 0;
	}

	public static DocumentAlignment alignDocuments(
			TokenizedDocument oldDoc, TokenizedDocument newDoc
	) {
		DocumentAlignment result = new DocumentAlignment();
		
		result.lineAlignment = alignLineSequences(oldDoc.lineTokens, newDoc.lineTokens);
		
		result.charAlignments = new int[oldDoc.lines.length][];
		for (int i = 0; i < oldDoc.lines.length; i++) {
			int j = result.lineAlignment[i];

			if (j == -1) {
//				charAlignments[i] = new int[oldDoc.lines[i].length()];
//				for (int k = 0; k < charAlignments[i].length; k++)
//					charAlignments[i][k] = -1;
				result.charAlignments[i] = null;
			}
			else {
				result.charAlignments[i] =
					alignStrings(oldDoc.lines[i], newDoc.lines[j]);
				
				result.chars += oldDoc.lines[i].length();
				for (int k = 0; k < oldDoc.lines[i].length(); k++) {
					int l = result.charAlignments[i][k];
					if (l != -1 && oldDoc.lines[i].charAt(k) == newDoc.lines[j].charAt(l))
						result.matchingChars++;
				}
			}
		}
		
		return result;
	}
	
	public static void printLineAlignment(Token[][] startLines, Token[][] endLines, int[] alignment) {
		int j = 0;
		for (int i = 0; i < startLines.length; i++) {
			if (alignment[i] == -1) {
				System.out.println("-" + Arrays.toString(startLines[i]));
			}
			else {
				for (; j < alignment[i]; j++) {
					System.out.println("+" + Arrays.toString(endLines[j]));
				}
				System.out.println("/" + Arrays.toString(startLines[i]));
				System.out.println("\\" + Arrays.toString(endLines[j++]));
			}
		}
		for (; j < endLines.length; j++) {
			System.out.println("+" + Arrays.toString(endLines[j]));
		}
	}
	
	public static class LineAlignment {
		public String oldLine;
		public String newLine;
		public int[] charAlignment;
//		public int[] revCharAlignment;
		
		public LineAlignment(String oldLine, String newLine, int[] charAlignment) {
			this.oldLine = oldLine;
			this.newLine = newLine;
			this.charAlignment = charAlignment;
		}
		
		public LineAlignment invert() {
			int[] newCharAlignment = new int[newLine.length()];
			Arrays.fill(newCharAlignment, -1);
			for (int i = 0; i < charAlignment.length; i++) {
				int j = charAlignment[i];
				if (j != -1)
					newCharAlignment[j] = i;
			}
			
			return new LineAlignment(newLine, oldLine, newCharAlignment);
		}
	}
	
	public static ArrayList<ArrayList<LineAlignment>> alignedRegions(
			TokenizedDocument oldDoc, TokenizedDocument newDoc)
	{
		return alignedRegions(alignDocuments(oldDoc, newDoc), oldDoc, newDoc);
	}
	
	public static ArrayList<ArrayList<LineAlignment>> alignedRegions(
			DocumentAlignment alignment,
			TokenizedDocument oldDoc, TokenizedDocument newDoc
	) {
		ArrayList<ArrayList<LineAlignment>> regions = new ArrayList<ArrayList<LineAlignment>>();
		
		ArrayList<LineAlignment> regionSoFar = new ArrayList<LineAlignment>();
		for (int i = 0; i < alignment.lineAlignment.length; i++) {
			int j = alignment.lineAlignment[i];
			if (j == -1 || oldDoc.lines[i].trim().length() == 0) {
				if (regionSoFar.size() > 0) {
					regions.add(regionSoFar);
					regionSoFar = new ArrayList<LineAlignment>();
				}
			}
			else {
				regionSoFar.add(
						new LineAlignment(oldDoc.lines[i], newDoc.lines[j], alignment.charAlignments[i]));
			}
		}
		if (regionSoFar.size() > 0)
			regions.add(regionSoFar);
	
		return regions;
	}
	
	public static void printDocumentAlignment(
			TokenizedDocument oldDoc, TokenizedDocument newDoc,
			DocumentAlignment alignment,
			PrintWriter out)
	{
		int j = 0;
		for (int i = 0; i < oldDoc.lines.length; i++) {
//			System.out.print("  /[" + i + "] " + oldDoc.lines[i]);
//			System.out.print("__\\[" + alignment.lineAlignment[i] + "] " + (alignment.lineAlignment[i] != -1 ? newDoc.lines[alignment.lineAlignment[i]] : "\n"));
//			if (i > -1) continue;
			
			if (alignment.lineAlignment[i] == -1) {
				out.println(oldDoc.lines[i]);
				out.println();

				out.println(alignment.lineAlignment[i]);
				if (alignment.lineAlignment[i] != -1) {
					for (int offset: alignment.charAlignments[i])
						out.print(offset + " ");
				}
				out.println();
				
				out.println(oldDoc.lines[i]);
				out.println(repeat("~", oldDoc.lines[i].length()));
			}
			else {
				while (j < alignment.lineAlignment[i]) {
					out.println();
					out.println(newDoc.lines[j]);
					out.println(j);
					out.println();
					out.println(repeat("~", newDoc.lines[j].length()));
					out.println(newDoc.lines[j]);
					out.println("___");
					j++;
				}
				
				out.println(oldDoc.lines[i]);
				out.println(newDoc.lines[j]);

				out.println(alignment.lineAlignment[i]);
				if (alignment.lineAlignment[i] != -1) {
					for (int offset: alignment.charAlignments[i])
						out.print(offset + " ");
				}
				out.println();
				
				String oldLine = oldDoc.lines[i];
				String newLine = newDoc.lines[j];
				int[] charAlignment = alignment.charAlignments[i];
				
				for (int k = 0, l = 0; k < oldLine.length(); k++) {
					
					while (l < charAlignment[k]) {
						out.print("~");
						l++;
					}
					
					out.print(oldLine.charAt(k));
					
					if (charAlignment[k] != -1)
						l++;
				}
				out.println();

				int l = 0;
				for (int k = 0; k < oldLine.length(); k++) {
					if (charAlignment[k] == -1) {
						out.print("~");
					}
					else {
						while (l <= charAlignment[k] && l < newLine.length())
							out.print(newLine.charAt(l++));
					}
				}
				out.println(newLine.substring(l));

				j++;
			}

			out.println("___");
		}
	}
	
	public static void main(String[] args) {
//		Aligner.debug = true;
//		stringAlignmentTest(args[0], args[1]);
		
		String ocrFile = args[0];
		String transcriptionFile = args[1];
		String alignmentFile = args[2];
		try {
			TokenizedDocument ocr = new TokenizedDocument(new FileInputStream(ocrFile));
			TokenizedDocument trans = new TokenizedDocument(new FileInputStream(transcriptionFile));
			DocumentAlignment alignment = alignDocuments(ocr, trans);
			PrintWriter writer= new PrintWriter(alignmentFile, "UTF-8");
			printDocumentAlignment(ocr, trans, alignment, writer);
			writer.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void stringAlignmentTest(String s1, String s2) {
		Aligner.debug = true;
//		String s1 = "abbccdeef";
//		String s2 = "bbxcceefgh";
		
		System.out.println(s1 + " -> " + s2 + ":");
		int[] alignment = alignStrings(s1, s2);
		System.out.println(Arrays.toString(alignment));

		System.out.println(s2 + " -> " + s1 + ":");
		alignment = alignStrings(s2, s1);
		System.out.println(Arrays.toString(alignment));
	}

	public static String repeat(String s, int n) {
		StringBuilder builder = new StringBuilder(n * s.length());
		for (int i = 0; i < n; i++)
			builder.append(s);
		return builder.toString();
	}
}
