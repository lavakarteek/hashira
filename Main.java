import java.math.BigInteger;
import java.util.*;
import java.util.regex.*;

public class Main {

    // Rational number (fraction) using BigInteger for precision
    static class Fraction {
        BigInteger num, den;

        Fraction(BigInteger num, BigInteger den) {
            if (den.signum() == 0) throw new ArithmeticException("Denominator zero");
            // Normalize sign to the numerator
            if (den.signum() < 0) {
                num = num.negate();
                den = den.negate();
            }
            BigInteger g = num.gcd(den);
            this.num = num.divide(g);
            this.den = den.divide(g);
        }

        Fraction add(Fraction other) {
            BigInteger newNum = this.num.multiply(other.den).add(other.num.multiply(this.den));
            BigInteger newDen = this.den.multiply(other.den);
            return new Fraction(newNum, newDen);
        }

        Fraction multiply(Fraction other) {
            return new Fraction(this.num.multiply(other.num), this.den.multiply(other.den));
        }
    }

    // Represents a single share (a point on the polynomial)
    // Added an 'id' to track the original share number from JSON
    static class Point {
        String id;
        BigInteger x, y;
        Point(String id, BigInteger x, BigInteger y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    /**
     * Calculates the secret using Lagrange Interpolation at x=0.
     * @param points A list of k points to use for interpolation.
     * @return The calculated secret as a BigInteger.
     */
    public static BigInteger lagrangeAtZero(List<Point> points) {
        Fraction secret = new Fraction(BigInteger.ZERO, BigInteger.ONE);
        int k = points.size();
        for (int j = 0; j < k; j++) {
            Fraction term = new Fraction(points.get(j).y, BigInteger.ONE);
            for (int m = 0; m < k; m++) {
                if (m == j) continue;
                term = term.multiply(new Fraction(points.get(m).x, points.get(m).x.subtract(points.get(j).x)));
            }
            secret = secret.add(term);
        }
        return secret.num.divide(secret.den);
    }

    /**
     * A recursive helper to generate all combinations and track which points produce which secret.
     */
    private static void findCombinations(int offset, int k, List<Point> combination, List<Point> points, Map<BigInteger, List<List<Point>>> secretCombinations) {
        if (k == 0) {
            BigInteger secret = lagrangeAtZero(combination);
            secretCombinations.computeIfAbsent(secret, key -> new ArrayList<>()).add(new ArrayList<>(combination));
            return;
        }

        for (int i = offset; i <= points.size() - k; i++) {
            combination.add(points.get(i));
            findCombinations(i + 1, k - 1, combination, points, secretCombinations);
            combination.remove(combination.size() - 1);
        }
    }

    /**
     * The main solver function. It orchestrates the process of finding the secret
     * by testing all combinations of k points and then identifies the corrupt share.
     */
    public static void solve(List<Point> allPoints, int k) {
        Map<BigInteger, List<List<Point>>> secretCombinations = new HashMap<>();
        
        findCombinations(0, k, new ArrayList<>(), allPoints, secretCombinations);

        BigInteger finalSecret = BigInteger.ZERO;
        int maxCount = 0;
        List<List<Point>> winningCombinations = new ArrayList<>();

        for (Map.Entry<BigInteger, List<List<Point>>> entry : secretCombinations.entrySet()) {
            if (entry.getValue().size() > maxCount) {
                maxCount = entry.getValue().size();
                finalSecret = entry.getKey();
                winningCombinations = entry.getValue();
            }
        }

        System.out.println("Secret: " + finalSecret);

        // --- Logic to find the corrupt share ---
        Set<String> validShareIds = new HashSet<>();
        for (List<Point> combo : winningCombinations) {
            for (Point p : combo) {
                validShareIds.add(p.id);
            }
        }

        List<String> corruptShareIds = new ArrayList<>();
        for (Point p : allPoints) {
            if (!validShareIds.contains(p.id)) {
                corruptShareIds.add(p.id);
            }
        }
        
        if (corruptShareIds.isEmpty()) {
            System.out.println("No corrupt shares detected.");
        } else {
            System.out.println("Corrupt share ID(s): " + String.join(", ", corruptShareIds));
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        StringBuilder sb = new StringBuilder();
        while (sc.hasNextLine()) sb.append(sc.nextLine());
        String jsonInput = sb.toString();

        Pattern keysPattern = Pattern.compile("\"n\"\\s*:\\s*(\\d+),\\s*\"k\"\\s*:\\s*(\\d+)");
        Matcher km = keysPattern.matcher(jsonInput);
        if (!km.find()) { 
            System.out.println("Could not find n and k in JSON."); 
            return; 
        }
        int k = Integer.parseInt(km.group(2));

        List<Point> points = new ArrayList<>();
        Pattern sharePattern = Pattern.compile("\"(\\d+)\"\\s*:\\s*\\{\\s*\"base\"\\s*:\\s*\"(\\d+)\",\\s*\"value\"\\s*:\\s*\"([0-9A-Za-z]+)\"\\s*}");
        Matcher sm = sharePattern.matcher(jsonInput);
        while (sm.find()) {
            String id = sm.group(1);
            BigInteger x = new BigInteger(id);
            int base = Integer.parseInt(sm.group(2));
            String valueStr = sm.group(3);
            BigInteger y = new BigInteger(valueStr, base);
            points.add(new Point(id, x, y));
        }

        if (points.size() < k) {
            System.out.println("Not enough shares provided to solve for the secret.");
            return;
        }
        
        solve(points, k);
    }
}

