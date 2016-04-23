package edu.neu.ccs.headword;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public interface Corrector {

	public abstract ArrayList<String> correctLines(List<String> ocrLines,
			List<String> transLines) throws IOException;

}