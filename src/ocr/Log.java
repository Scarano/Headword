package ocr;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Log {
	
	private String name;
	private PrintStream fileStream;
	private PrintStream extraStream;
	private SimpleDateFormat dateFormat;
	
	public Log(String name, File file, PrintStream extraStream) throws IOException {
		this.name = name;
		this.fileStream = new PrintStream(file);
		this.extraStream = extraStream;
		this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	}
	
	public void log(String message) {
		String timeStr = dateFormat.format(new Date());
		fileStream.println(timeStr + " " + message);
		fileStream.flush();
		
		if (extraStream != null) {
			extraStream.println(name + " " + timeStr + " " + message);
			extraStream.flush();
		}
	}
	
	public void logf(String format, Object... items) {
		log(String.format(format, items));
	}
	
	public void emptyLine() {
		fileStream.println();
	}
	
	public void close() {
		fileStream.close();
	}
}
