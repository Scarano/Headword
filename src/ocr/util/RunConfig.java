package ocr.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ocr.util.RunConfigGroup.Variable;

public class RunConfig {
	public static final String DATA_ROOT_KEY = "data-root";
	public static final String OUTPUT_ROOT_KEY = "output-root";
	public static final String TEMP_ROOT_KEY = "temp-root";
	public static final String OVERWRITE_OUTPUT_DIR_KEY = "overwrite-output-dir";
	
	RunConfigGroup group;
	private int variation;
	
	private File dataRoot;
	private File tempRoot;
	private File tempDir;
	private File outputRoot;
	private File outputDir;
	private File logFile;
	private Log log;
	private PhaseTimer timer;

	public RunConfig(RunConfigGroup group, int variation) {
		
		this.group = group;
		this.variation = variation;
		
	}
	
	public RunConfigGroup getGroup() {
		return group;
	}
	
	public int getVariation() {
		return variation;
	}

	public String getDescription() throws IOException {
		String description = "";
		for (String var: group.getVariableParameterNames()) {
			// abbreviate variable name
			String varName = var.replaceAll("(\\w)\\w+(\\W+)?", "$1");
			if (description.length() != 0) description += "_";
			description += varName + "=" + getString(var, "null");
		}
		return description;
	}

	public String buildJobDirName(String overrideGroupName) throws IOException {
		String name;
		if (overrideGroupName != null) {
			name = overrideGroupName;
		}
		else {
//			name = getString("group-date", 
			name = new SimpleDateFormat("yyyy-MM-dd-HHmmss").format(group.getRunDate());
		}
		
		return name + "_" + getDescription();
	}

	public void prepare() throws IOException {
		dataRoot = new File(getString(DATA_ROOT_KEY, null));
		outputRoot = new File(getString(OUTPUT_ROOT_KEY, null));
		tempRoot = new File(getString(TEMP_ROOT_KEY, null));
		
//		if (!tempRoot.getName().equals("temp")) {
//			throw new IOException(
//					"Rejecting non-\"temp\" temp root name (to avoid accidentally deleting files: "
//								+ tempRoot.getAbsolutePath());
//		}

		if (!tempRoot.exists())
			tempRoot.mkdirs();
		
		String jobDirName = buildJobDirName(null);
		
		outputDir = new File(outputRoot, jobDirName);
		if (outputDir.exists() && !getBoolean(OVERWRITE_OUTPUT_DIR_KEY, false)) {
			throw new IOException("Output dir already exists: '"
							+ outputDir.getAbsolutePath() + "'");
		}
		if (!outputDir.mkdirs()) {
			throw new IOException("Unable to create directory: '"
					+ outputDir.getAbsolutePath() + "'");
		}
		
		File latestOutputLink = new File(outputRoot, buildJobDirName("latest"));
		latestOutputLink.delete();
		Runtime.getRuntime().exec(
				"ln -sf " + jobDirName + " " + latestOutputLink.getAbsolutePath());

		Runtime.getRuntime().exec(
				"cp " + group.getConfigPath() + " " + 
						new File(outputDir, "group-config.txt").getAbsolutePath());
		
		File savedConfig = new File(outputDir, "config.txt");
		PrintWriter configWriter = new PrintWriter(savedConfig);
		for (String key: group.getParameterNames()) {
			configWriter.println(key + ": " + getString(key, ""));
		}
		configWriter.close();
		
		tempDir = new File(tempRoot, jobDirName);
		if (tempDir.isDirectory()) {
			for (File f: tempDir.listFiles())
				f.delete();
			if (!tempDir.delete()) {
				throw new IOException("Unable to clear temp dir " + tempDir.getAbsolutePath());
			}
		}
		if (!tempDir.mkdirs())
			throw new IOException("Unable to create temp dir " + tempDir.getAbsolutePath());
		
		Runtime.getRuntime().exec(
				"ln -s " + tempDir.getAbsolutePath() + " " + jobDirName + "/temp");

		logFile = new File(outputDir, "log.txt");
		log = new Log(getDescription(), logFile, System.out);
		
		timer = new PhaseTimer(new File(outputDir, "time.txt"), log);
	}
	
	public void cleanUp() {
		log.close();
		timer.close();
	}
	
	public String getString(String key) throws IOException {
		String value = getString(key, null);
		if (value == null)
			throw new IOException("Required parameter '" + key + "' not specified.");
		return value;
	}
	
	public String getString(String key, String defaultValue) throws IOException {
		String value = null;
		Variable variable = group.getVariable(key);
		if (variable == null) {
			String[] valueArray = group.getParameterValues(key);
			if (valueArray == null)
				value = defaultValue;
			else
				value = valueArray[0];
		}
		else {
			int valueIndex = variation / variable.cycleLength % variable.numValues;
			value = group.getParameterValues(key)[valueIndex];
		}
		
		if (value != null) {
//System.out.println("About to process '"+value+"'");
			// Perform variable interpolation
			Pattern pattern = Pattern.compile("([^\\$]*)\\$\\{([^\\}]+)\\}(.*)");
			Matcher matcher;
			while ((matcher = pattern.matcher(value)).matches()) {
//System.out.printf("Interpolating '%s' to '%s'\n", matcher.group(2), getString(matcher.group(2)));
				String interpolatedValue = getString(matcher.group(2));
				value = matcher.group(1) + interpolatedValue + matcher.group(3);
			}
		}
		
		return value;
	}
	
	public int getInt(String key) throws IOException {
		return Integer.parseInt(getString(key));
	}
	
	public int getInt(String key, int defaultValue) throws IOException {
		String value = getString(key, null);
		if (value == null)
			return defaultValue;
		else
			return Integer.parseInt(value);
	}
	
	public double getDouble(String key) throws IOException {
		return Double.parseDouble(getString(key));
	}
	
	public double getDouble(String key, double defaultValue) throws IOException {
		String value = getString(key, null);
		if (value == null)
			return defaultValue;
		else
			return Double.parseDouble(value);
	}
	
	public boolean getBoolean(String key) throws IOException {
		String value = getString(key);
		if (Character.toUpperCase(value.charAt(0)) == 'T')
			return true;
		if (Character.toUpperCase(value.charAt(0)) == 'F')
			return false;
		throw new IOException(
				String.format("Boolean parameter '%s' has invalid value '%s'", key, value));
	}
	
	public boolean getBoolean(String key, boolean defaultValue) throws IOException {
		String value = getString(key, null);
		if (value == null)
			return defaultValue;
		if (Character.toUpperCase(value.charAt(0)) == 'T')
			return true;
		if (Character.toUpperCase(value.charAt(0)) == 'F')
			return false;
		throw new IOException(
				String.format("Boolean parameter '%s' has invalid value '%s'", key, value));
	}
	
	public File getDataFile(String key) throws IOException {
		return new File(dataRoot, getString(key));
	}
	
	public File getDataFile(String key, String defaultRelativePath) throws IOException {
		String value = getString(key, defaultRelativePath);
		if (value == null) return null;
		return new File(dataRoot, value);
	}
	
	public File getOutputFile(String key) throws IOException {
		return new File(outputDir, getString(key));
	}
	
	public File getTempDir() {
		return tempDir;
	}
	
	public File getOutputDir() {
		return outputDir;
	}
	
	public Log getLog() {
		return log;
	}
	
	public PhaseTimer getTimer() {
		return timer;
	}
	
	public static Iterable<RunConfig> readRunConfigs(String configFile) throws IOException {
		RunConfigGroup runConfigGroup = new RunConfigGroup(new File(configFile));
		return runConfigGroup.variations();
	}

	public static void main(String[] args) throws IOException {
		for (RunConfig config: readRunConfigs(args[0])) {
			config.prepare();
			config.getTimer().completePhase("initialized");
			config.getLog().log("variation " + config.variation);
			config.getTimer().completePhase("wrote output");
			config.cleanUp();
		}
	}

}
