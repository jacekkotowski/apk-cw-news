package pl.cwnews;

import java.util.Arrays;

/**
 * Reads Morse from key times, however uneven. The dot length and the gap kinds come from clusters in the over so far,
 * not from fixed ratios, so Farnsworth spacing, a bug's long dashes and a beginner's hesitations all read. No Android imports.
 */
final class Decoder {
    private double[] start = new double[64], end = new double[64];
    private int n;
    private double open = -1, dotMemory = 0.08, dot = 0.08;
    private String text = "";
    private boolean dirty;

    void reset() { n = 0; open = -1; text = ""; dirty = false; dotMemory = dot; }

    /** A key event at time t in seconds. */
    void key(boolean down, double t) {
        if (down) { if (open < 0) open = t; return; }
        if (open < 0) return;
        if (t - open >= 0.005) {
            if (n == start.length) { start = Arrays.copyOf(start, 2 * n); end = Arrays.copyOf(end, 2 * n); }
            start[n] = open; end[n] = t; n++;
        }
        open = -1; dirty = true;
    }

    int marks() { return n; }
    double lastUp() { return n == 0 ? -1 : end[n - 1]; }
    boolean keyDown() { return open >= 0; }
    /** Raw timings for the feedback screen: start and end of mark i, in seconds. */
    double markStart(int i) { return start[i]; }
    double markEnd(int i) { return end[i]; }

    String text() { if (dirty) run(); return text; }
    /** Dot length in seconds from the dits heard so far. */
    double dot() { if (dirty) run(); return dot; }
    double wpm() { return 1.2 / dot(); }

    private static double[] twoMeans(double[] v, int m, boolean log) {
        double[] x = new double[m];
        double lo = Double.MAX_VALUE, hi = 0;
        for (int i = 0; i < m; i++) { x[i] = log ? Math.log(v[i]) : v[i]; lo = Math.min(lo, x[i]); hi = Math.max(hi, x[i]); }
        double a = lo, b = hi;
        for (int it = 0; it < 12; it++) {
            double mid = (a + b) / 2, sa = 0, sb = 0; int ca = 0, cb = 0;
            for (int i = 0; i < m; i++) if (x[i] < mid) { sa += x[i]; ca++; } else { sb += x[i]; cb++; }
            if (ca > 0) a = sa / ca;
            if (cb > 0) b = sb / cb;
        }
        return log ? new double[]{Math.exp(a), Math.exp(b)} : new double[]{a, b};
    }

    private void run() {
        dirty = false;
        if (n == 0) { text = ""; return; }
        double[] len = new double[n];
        double lo = Double.MAX_VALUE, hi = 0, sum = 0;
        for (int i = 0; i < n; i++) { len[i] = end[i] - start[i]; lo = Math.min(lo, len[i]); hi = Math.max(hi, len[i]); sum += len[i]; }
        double d, markThr;
        if (n >= 2 && hi > 1.7 * lo) {
            double[] c = twoMeans(len, n, false);
            d = c[0]; markThr = (c[0] + c[1]) / 2;
        } else {
            double mean = sum / n;
            if (mean < 2 * dotMemory) { d = mean; markThr = 2 * mean; } else { d = mean / 3; markThr = 0.6 * mean; }
        }
        dot = d;
        // Gaps between marks: short ones sit inside a letter, the rest end letters or words.
        double intra = 2 * d;
        double[] gl = new double[Math.max(0, n - 1)];
        int g = 0;
        for (int i = 0; i + 1 < n; i++) { double gap = start[i + 1] - end[i]; if (gap >= intra) gl[g++] = gap; }
        double wordThr = Double.MAX_VALUE;
        if (g >= 2) {
            double glo = Double.MAX_VALUE, ghi = 0;
            for (int i = 0; i < g; i++) { glo = Math.min(glo, gl[i]); ghi = Math.max(ghi, gl[i]); }
            if (ghi > 2.0 * glo) {
                double[] c = twoMeans(gl, g, true);
                if (c[1] > 1.75 * c[0]) wordThr = Math.sqrt(c[0] * c[1]);
            }
        }
        if (wordThr == Double.MAX_VALUE && g >= 1) {
            double m = 0; for (int i = 0; i < g; i++) m += gl[i];
            if (m / g > 25 * d) wordThr = 0;
        }
        StringBuilder out = new StringBuilder(), code = new StringBuilder();
        for (int i = 0; i < n; i++) {
            code.append(len[i] > markThr ? '-' : '.');
            double gap = i + 1 < n ? start[i + 1] - end[i] : Double.MAX_VALUE;
            if (gap >= intra) {
                letter(code, out);
                if (i + 1 < n && gap >= wordThr && out.length() > 0) out.append(' ');
            }
        }
        text = out.toString();
    }

    private static void letter(StringBuilder code, StringBuilder out) {
        String c = code.toString();
        code.setLength(0);
        String s = Morse.decode(c);
        if (s == null) s = c.length() >= 6 && c.indexOf('-') < 0 ? "<HH>" : "*";
        out.append(s);
    }

    /** True when the over has ended: a turn marker followed by 1.5 s of quiet, or 4 s of quiet in patient manners. */
    static boolean overEnded(String text, double silence, boolean patient) {
        if (text.isEmpty()) return false;
        if (patient && silence >= 4) return true;
        return silence >= 1.5 && text.matches("(?s).*(^|\\s)(K|KN|BK|SK|<KN>|<SK>)$");
    }
}
