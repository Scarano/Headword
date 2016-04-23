package ocr.util


class GUtil {
	public static String[] loadUtf8Lines(String fileName) {
		def lines = []
		new File(fileName).eachLine("utf-8") { lines << it }
		return lines as String[]
	}
	public static String loadUtf8String(String fileName) {
		def builder = new StringBuilder()
		new File(fileName).eachLine("utf-8") { builder.append(it); builder.append("\n") }
		return builder.toString()
	}
}





