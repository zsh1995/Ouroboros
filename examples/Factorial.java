import java.math.BigInteger;

import com.mrzsh.optmz.TailRecursion;

/**
 * Factorial
 */
public class Factorial {

    private static final BigInteger ONE = BigInteger.ONE;

    @TailRecursion
    public BigInteger factorial(BigInteger n, BigInteger res) {
        if(n.longValue() <= 1) return res;
        return factorial(n.subtract(ONE), res.multiply(n));
    }


    public static void main(String[] args) {
        System.out.println(new Factorial().factorial(BigInteger.valueOf(Long.valueOf(args[0])), ONE));
    }
}