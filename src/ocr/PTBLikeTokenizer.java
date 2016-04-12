package ocr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;

public class PTBLikeTokenizer implements Tokenizer {
	
	public String[] tokenize(String s) {
		s = s.replaceAll("\u00ad ", "\u00ad");
		
		PTBTokenizer<CoreLabel> tokenizer = new PTBTokenizer<CoreLabel>(
				new StringReader(s),
				new CoreLabelTokenFactory(),
				"invertible=false,ptb3Escaping=false");
		
		List<CoreLabel> words = tokenizer.tokenize();
		String[] result = new String[words.size()];
		for (int i = 0; i < words.size(); i++)
			result[i] = words.get(i).toString();
		
		return result;
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
		
		Tokenizer tokenizer = new PTBLikeTokenizer();
		while (reader.ready()) {
			String[] tokens = tokenizer.tokenize(reader.readLine());
			writer.println(Util.join(" ", tokens));
		}
		
		reader.close();
		writer.close();
	}
	
	static void test() {
		Tokenizer tokenizer = new PTBLikeTokenizer();
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









