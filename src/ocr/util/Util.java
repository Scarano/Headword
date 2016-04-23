package ocr.util;

import java.util.List;
import java.util.Random;

import org.apache.commons.math.util.FastMath;

public class Util {

	public static double nvl(Double x, double defaultVal) {
		if (x == null)
			return defaultVal;
		else
			return x;
	}

	static final double LOGSUM_THRESHOLD = 745.0;
	// (Experimenting with lower values to trade precision for speed was not fruitful.)

	// Empirically (on 2013 Macbook Pro 13" retina, Java 7), FastMath.exp is faster
    // than Math.exp; but FastMath.log1p is slower than Math.log1p.
	public static double logSum(double lx, double ly) {
	    if (lx == Double.NEGATIVE_INFINITY) return ly;
	    if (ly == Double.NEGATIVE_INFINITY) return lx;
	    double d = lx - ly;
	    if (d >= 0) {
	        if (d > LOGSUM_THRESHOLD) return lx;
	        else return lx + Math.log1p(FastMath.exp(-d));
	    }
	    else {
	        if (d < -LOGSUM_THRESHOLD) return ly;
	        else return ly + Math.log1p(FastMath.exp(d));
	    }
	}

	public static double mixInLogSpace(double loga, double logb, double lambda) {
		return logSum(loga + Math.log(1-lambda), logb + Math.log(lambda));
	}
	
	public static void mixInLogSpaceTest() {
		for (double a: new double[] {0, 0.5, 1}) {
			for (double b: new double[] {0, 0.5, 1}) {
				for (double lambda: new double[] {0.1, 0.5, 0.9}) {
					System.out.printf("mix(%f, %f, %f) = %f\n",
							a, b, lambda, 
							Math.exp(mixInLogSpace(Math.log(a), Math.log(b), lambda)));
				}
			}
		}
		for (double loga: new double[] {Double.NEGATIVE_INFINITY, -1000.0, 0.0}) {
			for (double logb: new double[] {Double.NEGATIVE_INFINITY, -1000.0, 0.0}) {
				for (double lambda: new double[] {0, 0.5, 1}) {
					System.out.printf("mix(exp %f, exp %f, %f) = exp %f\n",
							loga, logb, lambda, mixInLogSpace(loga, logb, lambda));
				}
			}
		}
	}

	public static void normalize(double[] dist) {
		double sum = 0.0;
		for (int i = 0; i < dist.length; i++)
			sum += dist[i];
		for (int i = 0; i < dist.length; i++)
			dist[i] = dist[i] / sum;
	}
	
	public static int sampleMultinomial(double[] dist, Random random) {
		double y = random.nextDouble();
		double acc = 0.0;
		for (int i = 0; i < dist.length - 1; i++) {
			if (y - acc < dist[i])
				return i;
			acc += dist[i];
		}
		return dist.length - 1;
	}
	
	static void sampleMultinomialTest() {
		Random random = new Random(0);
		Random random2 = new Random(0);
		double[] dist = new double[] {0.1, 0.1, 0.6, 0.1, 0.1};
		for (int i = 0; i < 20; i++) {
			int x = sampleMultinomial(dist, random);
			System.out.printf("y = %f; x = %d\n", random2.nextDouble(), x);
		}
	}
		
	public static String join(String sep, String[] strings) {
		if (strings.length == 0)
			return "";
		
		StringBuilder builder = new StringBuilder(100);
		builder.append(strings[0]);
		for (int i = 1; i < strings.length; i++) {
			builder.append(sep);
			builder.append(strings[i]);
		}
		
		return builder.toString();
	}

	public static String join(String sep, List<String> strings) {
		if (strings.size() == 0)
			return "";
		
		StringBuilder builder = new StringBuilder(100);
		builder.append(strings.get(0));
		for (int i = 1; i < strings.size(); i++) {
			builder.append(sep);
			builder.append(strings.get(i));
		}
		
		return builder.toString();
	}

	public static String[] concatenateArrays(String[] a, String[] b) {
		String[] result = new String[a.length + b.length];
		for (int i = 0; i < a.length; i++)
			result[i] = a[i];
		for (int i = 0; i < b.length; i++)
			result[a.length + i] = b[i];
		return result;
	}

	public static void main(String[] args) {
//		logTest();
//		mixInLogSpaceTest();
//		sampleMultinomialTest();
		logSumSpeedTest(Integer.parseInt(args[0]));
	}
	
	static void logSumSpeedTest(int size) {
		double sum = Double.NEGATIVE_INFINITY;
		for (double x = 0; x < size; x++) {
			for (double y = 0; y < size; y++) {
				sum = logSum(sum, logSum(-size-4 + x, -size-4.5 + y));
			}
		}
		System.out.println(sum);
	}

	
	public static double combination(double n, double k) {
		double c = 1;
		for (double i = 1.0; i <= k; i += 1.0) {
			c *= (n + 1 - i)/i;
//			System.out.printf("%f: %f\n", i, c);
		}
		return c;
	}
//	public static BigDecimal combination(BigDecimal n, BigDecimal k) {
//		BigDecimal c = BigDecimal.ONE;
//		for (BigDecimal i = BigDecimal.ONE; i.compareTo(k) <= 0; i = i.add(BigDecimal.ONE)) {
//			c = c.multiply(n.add(BigDecimal.ONE).subtract(i).divide(i));
////			System.out.printf("%d = %d * (%d + 1 - %d)/i\n", i.intValue(), c.intValue());
//		}
//		return c;
//	}
}




