package ocr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class RunConfigGroup {

	private HashMap<String, String[]> parameters = new LinkedHashMap<String, String[]>();
	private HashMap<String, Variable> variables = new LinkedHashMap<String, Variable>();
	private int numVariations;

	private Date runDate;
	
	private String configPath;
	
	public static class Variable {
		public int numValues;
		public int cycleLength;
		
		public Variable(int numValues, int cycleLength) {
			this.numValues = numValues;
			this.cycleLength = cycleLength;
		}
	}
	
	public RunConfigGroup(String fileName) throws IOException {
		this(new File(fileName));
	}

	public RunConfigGroup(File file) throws IOException {
		configPath = file.getAbsolutePath();
		runDate = new Date();
		
		// First, build a map containing just the strings. This ensures that if a parameter
		// key is used more than once, we ignore all but the last string specified. This allows
		// the wrapper script to overwrite parameter value strings by appending to the 
		// config file.
		// As a LinkedHashMap, it preserves the order of the parameters, so that parameter
		// value loops are nested as specified.
		Map<String, String> paramStrings = new LinkedHashMap<String, String>();
		
		BufferedReader reader =
				new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
		try {
			while (reader.ready()) {
				String rawLine = reader.readLine();
				String line = rawLine.split("#", 2)[0]; // remove comments
				if (line.matches("^\\s*$")) continue; // skip blank lines
				
				// parse out key & value
				String[] keyvalue = line.split(":\\s*", 2);
				if (keyvalue.length != 2)
					throw new IOException("Unreadable line in configuration file: '" + rawLine + "'");
				
				String keyStr = keyvalue[0].trim();
				String valueStr = keyvalue[1].trim();
				
				paramStrings.put(keyStr, valueStr);
			}
		}
		finally {
			reader.close();
		}
		
		int nextCycleLength = 1;
		for (Entry<String, String> paramEntry: paramStrings.entrySet()) {
			String keyStr = paramEntry.getKey();
			String valueStr = paramEntry.getValue();
				
			if (valueStr.startsWith("[") && valueStr.endsWith("]")) {
				String[] values = valueStr.substring(1, valueStr.length() - 1).split(" +");
				variables.put(keyStr, new Variable(values.length, nextCycleLength));
				nextCycleLength *= values.length;
				parameters.put(keyStr, values);
			}
			else {
				parameters.put(keyStr, new String[] { valueStr });
			}
			
			if (keyStr.equals("group-date")) {
				try {
					runDate = new SimpleDateFormat("yyyy-MM-dd-HHmmss").parse(valueStr);
				}
				catch (ParseException e) {
					throw new IOException(e);
				}
			}
		}
		numVariations = nextCycleLength;
	}
	
	public String getConfigPath() {
		return configPath;
	}

	public Date getRunDate() {
		return runDate;
	}
	
	public int getNumVariations() {
		return numVariations;
	}
	
	public Iterable<RunConfig> variations() {
		
		final RunConfigGroup runConfigGroup = this;
		
		return new Iterable<RunConfig>() {
			private int variation = 0;
			private int numVariations = runConfigGroup.numVariations;

			@Override
			public Iterator<RunConfig> iterator() {
				return new Iterator<RunConfig>() {

					@Override public boolean hasNext() {
						return variation < numVariations;
					}

					@Override public RunConfig next() {
						if (variation < numVariations)
							return new RunConfig(runConfigGroup, variation++);
						else
							return null;
					}

					@Override public void remove() {}
				};
			}
			
		};
	}
	
	public Set<String> getParameterNames() {
		return parameters.keySet();
	}
	
	public String[] getParameterValues(String key) {
		return parameters.get(key);
	}
	
	public String getOnlyParameterValue(String key) {
		return parameters.get(key)[0];
	}
	
	public Set<String> getVariableParameterNames() {
		return variables.keySet();
	}
	
	public Variable getVariable(String key) {
		return variables.get(key);
	}
}
