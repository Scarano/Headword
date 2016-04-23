package edu.neu.ccs.headword;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class WWPDocument {

	// These element types are automatically surrounded by newlines.
	static final List<String> PARAGRAPH_LIKE_ELEMENTS = Arrays.asList(
		"div", "p", "l",
		"head",
		"titlePart",
		"item",
		"figure", "figDesc",
		"opener", "closer",
		"signed"
	);
	
	// Additionally, these elements must be surrounded by word breaks, even
	// if the transcription doesn't include a space (i.e., they can't occur inside
	// words)
	static final List<String> WORD_BREAKING_ELEMENTS = Arrays.asList(
		"rs",
		"said"/*,
		"persName", "placeName" */
	);
	
	Document document;
	Element root;
	Node text;
	String[][] lines;
	boolean clean;
	String saidPre;
	String saidPost;

	public WWPDocument(File file, boolean clean) throws Exception {
		this.clean = clean;
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = dbf.newDocumentBuilder();
		document = builder.parse(file);

		root = document.getDocumentElement();

		NodeList textNodes = root.getElementsByTagName("text");
		if (textNodes.getLength() == 0)
			throw new Exception("No text node found in " + file.getName());
		text = textNodes.item(0);
		
		saidPre = null;
		saidPost = null;
		NodeList renditionNodes = root.getElementsByTagName("rendition");
		for (int i = 0; i < renditionNodes.getLength(); i++) {
			Node node = renditionNodes.item(i);
			Node xmlid = node.getAttributes().getNamedItem("xml:id");
			if (xmlid != null && xmlid.getNodeValue().contains("r.q")) {
				String s = node.getTextContent();
				s = s.replaceAll("bestow.*\\)\\)", "");
				if (s.contains("pre(\u2018)"))
					saidPre = "\u2018";
				if (s.contains("pre(\u201c)"))
					saidPre = "\u201c";
				if (s.contains("post(\u2019)"))
					saidPost = "\u2019 ";
				if (s.contains("post(\u201d)"))
					saidPost = "\u201d ";
			}
		}
		
		lines = lines(text).toArray(new String[0][]);
	}
	
	public void printLines(PrintStream out) {
		for (String[] line: lines) {
			for (int i = 0; i < line.length - 1; i++)
				out.print(line[i] + " ");
			out.print(line[line.length - 1] + "\n");
		}
	}
	
	List<String> segments(Node node) {
		return segments(node, false);
	}
	
	List<String> segments(Node node, boolean caps) {
		List<String> result = new LinkedList<String>();

		if (node.getNodeType() == Node.TEXT_NODE) {
			String text = node.getNodeValue();
			if (caps)
				text = text.toUpperCase();
			result.add(text);
		}
		else if (node.getNodeType() == Node.ELEMENT_NODE) {
			if (
					"corr".equals(node.getNodeName()) ||
					"note".equals(node.getNodeName())
			) {
				// Throw out annotated corrections not in the original text,
				// and footnotes (since they are difficult to re-insert at the correct
				// position on the page).
			}
			else if (
					clean && (
						"mw".equals(node.getNodeName()) ||
						"lg".equals(node.getNodeName()) ||
						"list".equals(node.getNodeName())
					)
			) {
				// If in "clean" mode, also throw out:
				//   - page numbers and "catch" text (<mw>)
				//   - verse (<lg>)
				//   - lists (<list>)
			}
			else if ("lb".equals(node.getNodeName())) {
				// Insert line break
				result.add(LINEBREAK_STR);
			}
			else {
				// For other elements, just continue recursively adding the text
				
				boolean addLinebreaks =
						PARAGRAPH_LIKE_ELEMENTS.contains(node.getNodeName());
				boolean addSpaces =
						WORD_BREAKING_ELEMENTS.contains(node.getNodeName());
				
				String pre = null;
				String post = null;
				if ("said".equals(node.getNodeName())) {
					if (node.getAttributes().getNamedItem("prev") == null)
						pre = saidPre;
					if (node.getAttributes().getNamedItem("next") == null)
						post = saidPost;
				}
				
				// Determine if the node is specifying that the enclosed text is in all-caps
				NamedNodeMap attrs = node.getAttributes();
				if (attrs != null) {
					Node rend = attrs.getNamedItem("rend");
					if (rend != null) {
						String s = rend.getNodeValue().replaceAll("bestow.*\\)\\)", "");
						if (
							s.contains("case(allcaps)") ||
							s.contains("case(smallcaps)")
						) {
							caps = true;
						}
						if (s.contains("pre(\u2018)"))
							pre = "\u2018";
						if (s.contains("pre(\u201c)"))
							pre = "\u201c";
						if (s.contains("post(\u2019)"))
							post = "\u2019";
						if (s.contains("post(\u201d)"))
							post = "\u201d";
					}
				}

				if (addLinebreaks)
					result.add(LINEBREAK_STR);
				else if (addSpaces)
					result.add(" ");
				if (pre != null)
					result.add(pre);
				
				NodeList children = node.getChildNodes();
				for (int i = 0; i < children.getLength(); i++)
					result.addAll(segments(children.item(i), caps));
				
				if (post != null)
					result.add(post);
				if (addLinebreaks)
					result.add(LINEBREAK_STR);
				else if (addSpaces)
					result.add(" ");
			}
		}
		
		return result;
	}

	List<String[]> lines(Node text) {
		
		List<String> segments = segments(text);
		segments.add(LINEBREAK_STR);
		
		// Remove redundant linebreaks
		List<String> normalizedSegments = new LinkedList<String>();
		boolean lastSegmentLinebreak = true;
		for (String s: segments) {
			if (LINEBREAK_STR.equals(s)) {
				if (!lastSegmentLinebreak) {
					lastSegmentLinebreak = true;
					normalizedSegments.add(s);
				}
			} else { // if (!s.matches("^\\s*$")){
				lastSegmentLinebreak = false;
				normalizedSegments.add(s);
			}
		}
		
		List<String[]> result = new LinkedList<String[]>();
		StringBuilder lineBuilder = new StringBuilder();
		for (String s: normalizedSegments) {
			if (LINEBREAK_STR.equals(s)) {
				String line = lineBuilder.toString().trim();
				if (line.length() > 0)
					result.add(line.split("\\s+"));
				lineBuilder = new StringBuilder();
			}
			else {
				lineBuilder.append(s);
			}
		}
		
		return result;
	}

	
	public static void main(String[] args) {
		String wwpDocFilename = args[0];
		String outputFilename = args[1];
		
		// "clean" means that cleanliness is preferred to fidelity. This tries to
		// throw out some things that are not expected to be (or be part of) 
		// grammatical sentences, like page numbers and poetry.
		// This option should be used for building training sets for language models.
		boolean clean = args.length < 3 ? false : (args[2].equals("-clean"));

		try {
			WWPDocument doc = new WWPDocument(new File(wwpDocFilename), clean);
			PrintStream output = new PrintStream(outputFilename, "UTF-8");
			doc.printLines(output);
			output.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	private static final String LINEBREAK_STR = new String(new char[] {30});

	static void stringVsArrayPerformanceTest() {
		Random random = new Random(0);
		char[] randomArray = new char[5000];
		for (int i = 0; i < 5000; i++) {
			randomArray[i] = (char) random.nextInt(128);
		}
		String randomString = new String(randomArray);
		
		int[] randomInts = new int[1000];
		for (int i = 0; i < 1000; i++)
			randomInts[i] = random.nextInt(5000);
		
		final int maxIter = 10000000;

		long startTime = System.currentTimeMillis();
		int matches = 0;
		for (int i = 0; i < maxIter; i++) {
			if (randomString.charAt(randomInts[i % 1000]) == 'a')
				matches++;
//			if (i % 20 == 0)
//				System.out.println(matches);
		}
		long execTime = System.currentTimeMillis() - startTime;
		
		System.out.println("String version: " + execTime + "\n");
		
		startTime = System.currentTimeMillis();
		for (int i = 0; i < maxIter; i++) {
			if (randomArray[randomInts[i % 1000]] == 'a')
				matches++;
		}
		
		execTime = System.currentTimeMillis() - startTime;
		System.out.println("Array version: " + execTime + "\n");
		
		System.out.println(matches);
	}
}










