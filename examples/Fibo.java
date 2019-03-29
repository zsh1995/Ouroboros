import com.mrzsh.optmz.TailRecursion;
import java.util.function.*;

public class Fibo {
	
	@TailRecursion
	public static long fibo(int n, long a, long b) {
		if(n <= 2) return a + b;
		return fibo(n - 1, b, a + b);
	}
	
	public static long fiboNoOptmz(int n, long a, long b) {
		if(n <= 2) return a + b;
		return fiboNoOptmz(n - 1, b, a + b);
	}
	
	 private <T> T test(Function<Integer, T> func, int arg) {
		long start = System.nanoTime();
		T result = func.apply(arg);
		System.out.println(String.format("used: %d ns", System.nanoTime() - start));
		return result;
	}
	
	public static void main(String[] args) {
		int val = Integer.valueOf(args[0]);
		Fibo fibos = new Fibo();
		System.out.println(fibos.<Long>test((arg)-> fibo(arg, 0, 1), val));
		System.out.println(fibos.<Long>test((arg)-> fiboNoOptmz(arg, 0, 1), val));
	}
}