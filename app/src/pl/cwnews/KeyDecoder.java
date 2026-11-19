package pl.cwnews;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Morse from key times, however uneven. It learns the sender's dot, dash and gaps from what it has heard (or starts
 * from a saved fist profile), and keeps both readings where the timing cannot decide, so that Heard can pick the one that
 * fits what the partner expects. No Android imports.
 */
final class KeyDecoder {
    /** Rules for a straight key. On in the app; tests switch each off to show that it matters. */
    static boolean rule1 = true, rule2 = true, rule3 = true, rule4 = true, rule5 = true;
    private static final double ALPHA = 0.15, AMBIG = Math.log(1.4);

    /** The five numbers of a sender's hand, in milliseconds. */
    static final class Fist5 {
        final double dot, dash, inner, letter, word;
        Fist5(double dot, double dash, double inner, double letter, double word) { this.dot = dot; this.dash = dash; this.inner = inner; this.letter = letter; this.word = word; }
        @Override public String toString() { return String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f,%.1f,%.1f", dot, dash, inner, letter, word); }
        static Fist5 parse(String s) {
            if (s == null) return null;
            String[] p = s.split(",");
            if (p.length != 5) return null;
            try { return new Fist5(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), Double.parseDouble(p[4])); }
            catch (NumberFormatException e) { return null; }
        }
        /** How long your dashes are, in dots. */
        double dashDots() { return dash / dot; }

        /**
         * Learns a hand from what was sent on purpose. marks and gaps are the durations of the marks and of the gaps between
         * them (one fewer); knownText is the text of one send, and the lists may hold several sends one after another.
         * Returns null when too little could be matched.
         */
        static Fist5 learn(List<Long> marks, List<Long> gaps, String knownText) {
            List<int[]> exp = new ArrayList<>();   // per expected mark: {isDash, gapAfter: 0 inner, 1 letter, 2 word}
            List<List<String>> words = Morse.parse(knownText);
            for (int w = 0; w < words.size(); w++) for (int c = 0; c < words.get(w).size(); c++) {
                String code = words.get(w).get(c);
                for (int e = 0; e < code.length(); e++) exp.add(new int[]{code.charAt(e) == '-' ? 1 : 0, e < code.length() - 1 ? 0 : c < words.get(w).size() - 1 ? 1 : 2});
            }
            int per = exp.size(), n = marks.size();
            if (per == 0 || n < 6) return null;
            int copies = Math.max(1, (int) Math.round(n / (double) per));
            List<int[]> all = new ArrayList<>();
            for (int k = 0; k < copies; k++) all.addAll(exp);
            // Which observed marks look like dashes: the longer cluster.
            double[] v = new double[n];
            for (int i = 0; i < n; i++) v[i] = marks.get(i);
            double[] c = twoMeans(v, n, true);
            double bound = Math.sqrt(c[0] * c[1]);
            int m = all.size();
            int[][] d = new int[n + 1][m + 1];
            for (int i = 0; i <= n; i++) d[i][0] = i;
            for (int j = 0; j <= m; j++) d[0][j] = j;
            for (int i = 1; i <= n; i++) for (int j = 1; j <= m; j++) {
                int obs = v[i - 1] > bound ? 1 : 0;
                d[i][j] = Math.min(Math.min(d[i - 1][j], d[i][j - 1]) + 1, d[i - 1][j - 1] + (obs == all.get(j - 1)[0] ? 0 : 1));
            }
            int[] match = new int[n]; Arrays.fill(match, -1);
            for (int i = n, j = m; i > 0 && j > 0; ) {
                int obs = v[i - 1] > bound ? 1 : 0;
                if (d[i][j] == d[i - 1][j - 1] + (obs == all.get(j - 1)[0] ? 0 : 1)) { if (obs == all.get(j - 1)[0]) match[i - 1] = j - 1; i--; j--; }
                else if (d[i][j] == d[i - 1][j] + 1) i--; else j--;
            }
            double[] sum = new double[5]; int[] cnt = new int[5];
            int matched = 0;
            for (int i = 0; i < n; i++) {
                if (match[i] < 0) continue;
                matched++;
                int cls = all.get(match[i])[0] == 1 ? 1 : 0;
                sum[cls] += v[i]; cnt[cls]++;
                if (i + 1 < n && i < gaps.size() && match[i + 1] == match[i] + 1) {
                    int g = 2 + all.get(match[i])[1];
                    sum[g] += gaps.get(i); cnt[g]++;
                }
            }
            if (matched < n / 2 || cnt[0] == 0 && cnt[1] == 0) return null;
            double dot = cnt[0] > 0 ? sum[0] / cnt[0] : sum[1] / cnt[1] / 3;
            double dash = cnt[1] > 0 ? sum[1] / cnt[1] : 3 * dot;
            double inner = cnt[2] > 0 ? sum[2] / cnt[2] : dot;
            double letter = cnt[3] > 0 ? sum[3] / cnt[3] : Math.max(3 * dot, 2 * inner);
            double word = cnt[4] > 0 ? sum[4] / cnt[4] : Math.max(7 * dot, 1.6 * letter);
            return new Fist5(dot, Math.max(dash, 1.5 * dot), inner, Math.max(letter, 1.5 * inner), Math.max(word, 1.4 * letter));
        }
    }

    private final int sendWpm;
    private final Fist5 profile;
    private double[] start = new double[64], end = new double[64];
    private int n;
    private double open = -1, lastUp = -1;
    private boolean dirty = true;
    private final List<Heard.Letter> letters = new ArrayList<>();
    private String text = "";
    private double dot = 80, wordMean = 560, letterMean = 240, dashMean = 240, innerMean = 80;

    KeyDecoder(int sendWpm, Fist5 profile) { this.sendWpm = Math.max(5, sendWpm); this.profile = profile; dot = 1200.0 / this.sendWpm; }

    void reset() { n = 0; open = -1; lastUp = -1; dirty = true; letters.clear(); text = ""; }

    /** A key event at time t in milliseconds. */
    void key(boolean down, double tMs) {
        if (down) { if (open < 0) open = tMs; return; }
        if (open < 0) return;
        if (n == start.length) { start = Arrays.copyOf(start, 2 * n); end = Arrays.copyOf(end, 2 * n); }
        start[n] = open; end[n] = tMs; n++;
        open = -1; lastUp = tMs; dirty = true;
    }

    int marks() { return n; }
    boolean keyDown() { return open >= 0; }
    double lastUpMs() { return lastUp; }
    /** The raw timings, for the feedback screen. */
    double markStartMs(int i) { return start[i]; }
    double markEndMs(int i) { return end[i]; }

    List<Heard.Letter> letters() { if (dirty) run(); return letters; }
    String text() { if (dirty) run(); return text; }
    double dotMs() { if (dirty) run(); return dot; }
    double wpm() { return 1200 / dotMs(); }
    double dashMs() { if (dirty) run(); return dashMean; }
    double letterGapMs() { if (dirty) run(); return letterMean; }
    double wordGapMs() { if (dirty) run(); return wordMean; }
    double innerGapMs() { if (dirty) run(); return innerMean; }

    /** True when the over has ended: a turn marker and 1.5 s of quiet; or, in Patient manners, 4 s of quiet (2.5 s in Normal) and at least three word gaps. */
    boolean overEnded(double nowMs, int manners) {
        if (n == 0 || open >= 0) return false;
        double quiet = nowMs - lastUp;
        String t = text().trim();
        int sp = t.lastIndexOf(' ');
        String last = sp < 0 ? t : t.substring(sp + 1);
        boolean turn = last.equals("K") || last.equals("KN") || last.equals("BK") || last.equals("SK") || last.equals("AR") || last.equals("?") || last.equals("<KN>") || last.equals("<SK>") || last.equals("<AR>");
        if (turn && quiet >= 1500) return true;
        if (manners == Profile.PATIENT) return quiet >= Math.max(4000, 3 * wordGapMs());
        if (manners == Profile.NORMAL) return quiet >= Math.max(2500, 3 * wordGapMs());
        return false;
    }

    // ---- the reading ----

    /** Two clusters of the values, in the log domain (log true) or as they are; returns the two means, low first. */
    static double[] twoMeans(double[] v, int cnt, boolean log) {
        double[] x = new double[cnt];
        double lo = 1e300, hi = -1e300;
        for (int i = 0; i < cnt; i++) { x[i] = log ? Math.log(Math.max(v[i], 1e-3)) : v[i]; lo = Math.min(lo, x[i]); hi = Math.max(hi, x[i]); }
        double a = lo, b = hi;
        for (int it = 0; it < 15; it++) {
            double mid = (a + b) / 2, sa = 0, sb = 0; int ca = 0, cb = 0;
            for (int i = 0; i < cnt; i++) if (x[i] < mid) { sa += x[i]; ca++; } else { sb += x[i]; cb++; }
            if (ca > 0) a = sa / ca;
            if (cb > 0) b = sb / cb;
        }
        return log ? new double[]{Math.exp(a), Math.exp(b)} : new double[]{a, b};
    }

    /** Clusters in the log domain, k at most, low first; clusters closer than 1.6 times are joined. */
    private static List<Double> clusters(double[] v, int cnt, int k) {
        List<Double> c = new ArrayList<>();
        if (cnt == 0) return c;
        double[] x = new double[cnt]; double lo = 1e300, hi = -1e300;
        for (int i = 0; i < cnt; i++) { x[i] = Math.log(Math.max(v[i], 1e-3)); lo = Math.min(lo, x[i]); hi = Math.max(hi, x[i]); }
        double[] cen = new double[k];
        for (int j = 0; j < k; j++) cen[j] = k == 1 ? (lo + hi) / 2 : lo + (hi - lo) * j / (k - 1);
        for (int it = 0; it < 20; it++) {
            double[] s = new double[k]; int[] q = new int[k];
            for (int i = 0; i < cnt; i++) { int bj = 0; for (int j = 1; j < k; j++) if (Math.abs(x[i] - cen[j]) < Math.abs(x[i] - cen[bj])) bj = j; s[bj] += x[i]; q[bj]++; }
            for (int j = 0; j < k; j++) if (q[j] > 0) cen[j] = s[j] / q[j];
        }
        double[] s = new double[k]; int[] q = new int[k];
        for (int i = 0; i < cnt; i++) { int bj = 0; for (int j = 1; j < k; j++) if (Math.abs(x[i] - cen[j]) < Math.abs(x[i] - cen[bj])) bj = j; s[bj] += x[i]; q[bj]++; }
        for (int j = 0; j < k; j++) if (q[j] > 0) c.add(s[j] / q[j]);
        java.util.Collections.sort(c);
        for (int i = 0; i + 1 < c.size(); ) { if (c.get(i + 1) - c.get(i) < Math.log(1.6)) { c.set(i, (c.get(i) + c.get(i + 1)) / 2); c.remove(i + 1); i = 0; } else i++; }
        for (int i = 0; i < c.size(); i++) c.set(i, Math.exp(c.get(i)));
        return c;
    }

    private double[] initial(double[] len, double[] gap, int g) {
        if (profile != null) return new double[]{profile.dot, profile.dash, profile.inner, profile.letter, profile.word};
        double dot0 = 1200.0 / sendWpm, d, da;
        double lo = 1e300, hi = 0, sum = 0;
        for (int i = 0; i < len.length; i++) { lo = Math.min(lo, len[i]); hi = Math.max(hi, len[i]); sum += len[i]; }
        int use = Math.min(len.length, 24);
        if (len.length >= 2 && hi > 1.7 * lo) {
            double[] c = twoMeans(len, len.length, true);
            d = c[0]; da = c[1];
        } else {
            double mean = sum / Math.max(1, len.length);
            if (mean < 2 * dot0) { d = mean; da = 3 * mean; } else { da = mean; d = mean / 3; }
        }
        double inner = d, letter = 3 * d, word = 7 * d;
        // A gap far shorter than a dot is contact bounce, not a gap: it must not pull the gap clusters down.
        double[] real = new double[g]; int rc = 0;
        for (int i = 0; i < g; i++) if (gap[i] >= 0.35 * d) real[rc++] = gap[i];
        List<Double> c = clusters(real, rc, 3);
        int at = 0;
        if (!c.isEmpty() && c.get(0) <= 1.9 * d) { inner = c.get(0); at = 1; }
        if (c.size() - at >= 2) { letter = c.get(at); word = c.get(at + 1); }
        else if (c.size() - at == 1) { double x = c.get(at); if (x <= 5 * d) { letter = x; word = Math.max(2.33 * x, 1.6 * x); } else { word = x; letter = Math.max(x / 2.33, 2 * inner); } }
        return new double[]{d, Math.max(da, 2 * d), inner, Math.max(letter, 2 * inner), Math.max(word, 1.6 * letter)};
    }

    private static String decode(String code) {
        String s = Morse.letter(code);
        if (s == null) return null;
        return s.length() > 1 ? "<" + s + ">" : s;
    }

    private void run() {
        dirty = false;
        letters.clear();
        if (n == 0) { text = ""; return; }
        double[] st = Arrays.copyOf(start, n), en = Arrays.copyOf(end, n);
        int cnt = n;
        double[] len = new double[cnt], gap = new double[Math.max(0, cnt - 1)];
        for (int pass = 0; pass < 2; pass++) {
            len = new double[cnt]; gap = new double[Math.max(0, cnt - 1)];
            for (int i = 0; i < cnt; i++) len[i] = en[i] - st[i];
            for (int i = 0; i + 1 < cnt; i++) gap[i] = st[i + 1] - en[i];
            double[] m0 = initial(len, gap, gap.length);
            if (!rule2 || pass == 1) break;
            // Contact bounce: a gap far shorter than an inner gap is not a gap; the two marks are one.
            boolean merged = false;
            double limit = 0.5 * Math.min(m0[2], m0[0]);
            double[] st2 = new double[cnt], en2 = new double[cnt]; int c2 = 0;
            for (int i = 0; i < cnt; i++) {
                if (c2 > 0 && st[i] - en2[c2 - 1] < limit) { en2[c2 - 1] = en[i]; merged = true; }
                else { st2[c2] = st[i]; en2[c2] = en[i]; c2++; }
            }
            if (!merged) break;
            st = st2; en = en2; cnt = c2;
        }
        double[] m = initial(len, gap, gap.length);
        double dotM = m[0], dashM = m[1], innerM = m[2], letterM = m[3], wordM = m[4];

        boolean[] dash = new boolean[cnt], flip = new boolean[cnt], bad = new boolean[cnt];
        int[] gk = new int[cnt];            // 0 inner, 1 letter, 2 word, after mark i; the last is a word end
        boolean[] near = new boolean[cnt];  // the gap after mark i is near the inner/letter boundary
        for (int i = 0; i < cnt; i++) {
            double bound = Math.sqrt(dotM * dashM);
            dash[i] = len[i] > bound;
            flip[i] = Math.abs(Math.log(len[i] / bound)) < AMBIG;
            if (!rule1 && len[i] > 1.6 * dashM) bad[i] = true;
            if (dash[i]) dashM += ALPHA * (len[i] - dashM); else dotM += ALPHA * (len[i] - dotM);
            dashM = Math.max(dashM, 2 * dotM);
            if (i + 1 >= cnt) { gk[i] = 2; break; }
            double g = gap[i], b1 = Math.sqrt(innerM * letterM), b2 = Math.sqrt(letterM * wordM);
            if (rule4) b2 = Math.max(b2, 1.3 * letterM);
            gk[i] = g < b1 ? 0 : g < b2 ? 1 : 2;
            near[i] = Math.abs(Math.log(g / b1)) < AMBIG;
            if (gk[i] == 0) innerM += ALPHA * (g - innerM); else if (gk[i] == 1) letterM += ALPHA * (g - letterM); else wordM += ALPHA * (g - wordM);
            letterM = Math.max(letterM, 2 * innerM); wordM = Math.max(wordM, 1.6 * letterM);
        }
        dot = dotM; dashMean = dashM; innerMean = innerM; letterMean = letterM; wordMean = wordM;

        // Letters.
        StringBuilder code = new StringBuilder(), out = new StringBuilder();
        int first = 0;
        List<Heard.Letter> ls = new ArrayList<>();
        List<Boolean> joinedAfter = new ArrayList<>();   // for each letter: is the gap after it near the boundary and a letter gap
        for (int i = 0; i < cnt; i++) {
            code.append(dash[i] ? '-' : '.');
            if (gk[i] == 0) continue;
            ls.add(makeLetter(code.toString(), first, i, dash, flip, bad, gk, near, gap, st, en));
            joinedAfter.add(gk[i] == 1 && near[i]);
            if (gk[i] == 2 && i + 1 < cnt) { Heard.Letter w = new Heard.Letter(" "); w.startMs = (long) en[i]; w.endMs = (long) st[i + 1]; ls.add(w); joinedAfter.add(false); }
            code.setLength(0); first = i + 1;
        }
        // Where two letters meet at a doubtful gap, or an unknown code sits beside a lone E or T, keep the joined reading too.
        for (int i = 0; i + 1 < ls.size(); i++) {
            Heard.Letter a = ls.get(i), b = ls.get(i + 1);
            if (a.ch.equals(" ") || b.ch.equals(" ")) continue;
            String joined = decode(a.code + b.code);
            if (joined != null && joinedAfter.get(i)) { add(a, joined); add(b, ""); }
            if (rule5 && joined != null && (b.ch.equals("E") || b.ch.equals("T")) && a.ch.equals("*")) { add(a, joined); add(b, ""); }
            if (rule5 && joined != null && (a.ch.equals("E") || a.ch.equals("T")) && b.ch.equals("*")) { add(a, ""); add(b, joined); }
        }
        for (Heard.Letter l : ls) out.append(l.ch);
        letters.addAll(ls);
        text = out.toString();
    }

    private static void add(Heard.Letter l, String alt) { if (!l.alternatives.contains(alt) && !alt.equals(l.ch)) l.alternatives.add(alt); }

    private Heard.Letter makeLetter(String code, int first, int last, boolean[] dash, boolean[] flip, boolean[] bad, int[] gk, boolean[] near, double[] gap, double[] st, double[] en) {
        String ch = decode(code);
        boolean broken = false;
        for (int i = first; i <= last; i++) if (bad[i]) broken = true;
        if (ch == null || broken) ch = code.length() >= 6 && code.indexOf('-') < 0 && !broken ? "<HH>" : "*";
        Heard.Letter l = new Heard.Letter(ch);
        l.code = code; l.startMs = (long) st[first]; l.endMs = (long) en[last];
        // A dot or a dash that could be the other one.
        for (int i = first; i <= last; i++) if (flip[i]) {
            char[] c = code.toCharArray(); c[i - first] = c[i - first] == '-' ? '.' : '-';
            String alt = decode(new String(c));
            if (alt != null) add(l, alt);
        }
        // A gap inside the letter that could be between two letters.
        int best = -1; double bestGap = 0;
        for (int i = first; i < last; i++) {
            if (gap[i] > bestGap) { bestGap = gap[i]; best = i; }
            if (near[i]) { String a = decode(code.substring(0, i - first + 1)), b = decode(code.substring(i - first + 1)); if (a != null && b != null) add(l, a + b); }
        }
        // An unknown code is also tried split at its longest inner gap.
        if (rule3 && l.ch.equals("*") && best >= 0) {
            String a = decode(code.substring(0, best - first + 1)), b = decode(code.substring(best - first + 1));
            if (a != null && b != null) add(l, a + b);
        }
        return l;
    }

    // ---- saved overs, so that real ones can become test data ----

    /** The timings of the over so far, and what was meant, as JSON. */
    String timingsJson(String meant) {
        StringBuilder m = new StringBuilder(), g = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) m.append(',');
            m.append(Math.round(end[i] - start[i]));
            if (i + 1 < n) { if (i > 0) g.append(','); g.append(Math.round(start[i + 1] - end[i])); }
        }
        return "{\"marks\":[" + m + "],\"gaps\":[" + g + "],\"meant\":\"" + meant.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    /** A saved over: mark and gap durations in ms, and what was meant. */
    static final class Saved { final List<Long> marks = new ArrayList<>(), gaps = new ArrayList<>(); String meant = ""; }

    static Saved fromJson(String json) {
        Saved s = new Saved();
        s.marks.addAll(longs(json, "marks")); s.gaps.addAll(longs(json, "gaps"));
        Matcher m = Pattern.compile("\"meant\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        if (m.find()) s.meant = m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        return s;
    }
    private static List<Long> longs(String json, String key) {
        List<Long> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(json);
        if (m.find()) for (String p : m.group(1).split(",")) if (!p.trim().isEmpty()) out.add(Long.parseLong(p.trim()));
        return out;
    }
    /** Feeds a saved over into this decoder. */
    void feed(Saved s) {
        double t = 0;
        for (int i = 0; i < s.marks.size(); i++) {
            key(true, t); t += s.marks.get(i); key(false, t);
            if (i < s.gaps.size()) t += s.gaps.get(i);
        }
    }
}
