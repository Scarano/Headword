package ocr;

import java.util.ArrayList;

import ocr.ConditionalModel.Observer;

public class LexicalDMVCounter {
	
	ArrayList<Observer<String, String>> attach;

	public LexicalDMVCounter() {
		attach = new ArrayList<Observer<String, String>>(2);
		attach.add(new Observer<String, String>());
		attach.add(new Observer<String, String>());
	}
	
	public void add(String head, String arg, boolean left, double count) {
		attach.get(left ? 0 : 1).observe(head, arg, count);
	}

	public static void main(String[] args) {
	}

}
