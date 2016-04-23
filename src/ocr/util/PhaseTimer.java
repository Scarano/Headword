package ocr.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

public class PhaseTimer {
	PrintStream output;
	Log log;
	long phaseStartTime;
	long jobStartTime;
	
	public PhaseTimer(File outputFile, Log log) throws FileNotFoundException {
		output = new PrintStream(outputFile);
		this.log = log;
		jobStartTime = phaseStartTime = System.currentTimeMillis();
	}
	
	public void completePhase(String name) {
		long now = System.currentTimeMillis();
		long phaseTime = now - phaseStartTime;
		
        output.println(intervalString(phaseTime) + ": " + name);
        output.flush();

        log.log(name + " (" + phaseTime/1000 + " sec)");
        log.emptyLine();
		
		phaseStartTime = now;
	}
	
	public void close() {
		long jobTime = System.currentTimeMillis() - jobStartTime;
		output.println(intervalString(jobTime) + ": Total execution time");
		output.close();
	}
	
	static String intervalString(long millis) {
		long h = millis/3600000;
		long m = (millis%3600000)/60000;
		long s = (millis%60000)/1000;
		long ms = millis%1000;
        return String.format("%.3f (%02d:%02d:%02d.%03d)",
        		(float)millis/1000, h, m, s, ms);
	}
}
