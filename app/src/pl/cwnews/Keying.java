package pl.cwnews;

import java.util.List;

/** A text turned into on/off runs in samples. Positive means tone, negative means silence. */
final class Keying {
    final int[] runs;
    final int[] wordStart;
    final long totalSamples;

    private Keying(int[] runs, int[] wordStart, long total) { this.runs = runs; this.wordStart = wordStart; totalSamples = total; }

    static Keying build(String text, Timing t, int rate) { return build(text, t, rate, null, null); }

    /** A steady carrier of that many seconds, as from someone tuning up. */
    static Keying carrier(double seconds, int rate) {
        int n = (int) Math.round(seconds * rate), tail = rate / 10;
        return new Keying(new int[]{n, -tail}, new int[]{0}, n + tail);
    }

    /** With a fist, the timing is bent the way that operator's hand does it; without one it is exact. */
    static Keying build(String text, Timing t, int rate, Fist fist, Dsp.Rng r) {
        List<List<String>> words = Morse.parse(text);
        double[] d = new double[count(words)];
        int[] starts = new int[words.size()];
        int n = 0;
        for (int w = 0; w < words.size(); w++) {
            starts[w] = n;
            List<String> chars = words.get(w);
            for (int c = 0; c < chars.size(); c++) {
                String code = chars.get(c);
                boolean last = c == chars.size() - 1;
                for (int e = 0; e < code.length(); e++) {
                    boolean dah = code.charAt(e) == '-';
                    double exact = (dah ? 3 : 1) * t.dot;
                    d[n++] = fist == null ? exact : fist.element(dah, exact, r);
                    if (e < code.length() - 1) d[n++] = -(fist == null ? t.dot : fist.inner(t.dot, r));
                }
                // One gap follows each character, so gaps are never counted twice.
                if (fist == null) d[n++] = -(last ? t.wordGap : t.charGap);
                else d[n++] = -(last ? fist.word(t.wordGap, t.dot, r) : fist.letter(t.charGap, r));
            }
        }
        // Each boundary is rounded from the running time, so rounding never accumulates.
        int[] runs = new int[n];
        double time = 0; long prev = 0;
        for (int i = 0; i < n; i++) {
            time += Math.abs(d[i]);
            long at = Math.round(time * rate);
            int len = (int) (at - prev);
            runs[i] = d[i] > 0 ? len : -len;
            prev = at;
        }
        return new Keying(runs, starts, prev);
    }

    private static int count(List<List<String>> words) {
        int n = 0;
        for (List<String> w : words) for (String code : w) n += 2 * code.length();
        return n;
    }
}
