package pl.cwnews;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What one over of the user's contained, read leniently: letters may be wrong, words may run together, the callsign may be
 * a little off. The partner uses it to decide what to answer. No Android imports.
 */
final class Heard {
    /** One decoded letter, with the other readings its timing allowed (used to re-segment a word that did not match). A word break has ch " ". */
    static final class Letter {
        String code, ch;
        List<String> alternatives = new ArrayList<>();
        long startMs, endMs;
        Letter(String ch, String... alternatives) { this.ch = ch; this.alternatives.addAll(Arrays.asList(alternatives)); }
    }

    /** The words the partner expects to hear now. */
    static final class Expect {
        final Set<String> words = new LinkedHashSet<>();
        /** Names and places: valid values after NAME, QTH, RIG and the like, and words to match against. */
        final Set<String> names = new LinkedHashSet<>();
        String userCall, partnerCall;

        static Expect of(String partnerCall, String partnerName, String partnerQth, Profile user) {
            Expect e = new Expect();
            e.words.addAll(Arrays.asList(VOCAB));
            e.words.addAll(Abbrev.keys());
            e.partnerCall = partnerCall;
            if (user != null) { e.userCall = user.call; e.addNames(user.name); e.addNames(user.qth); e.addNames(user.rig); e.addNames(user.ant); }
            e.addNames(partnerName); e.addNames(partnerQth);
            return e;
        }
        void addNames(String s) { if (s != null) for (String w : s.toUpperCase().split("\\s+")) if (w.length() > 0) names.add(w); }
    }

    static final String[] VOCAB = ("CQ DE K KN BK SK AR BT R RR OK TU TNX FB UR RST NAME OP QTH RIG ANT WX PWR HR ES GM GA GE GN 73 88 CUL HPE AGN PSE QRS QRQ QSB QRM QRN QSL HW CPY SRI NIL NW AGE HOBBY HOBBIES JOB WORK OCC OCCUPATION KEY CONDX YES NO 5NN 599 GB WHAT").split(" ");
    private static final Set<String> QUESTIONS = new HashSet<>(Arrays.asList("NAME", "QTH", "RST", "RIG", "PWR", "ANT", "WX", "AGE", "HOBBY", "JOB", "KEY", "QSL", "CONDX"));
    private static final Set<String> VALUE_KEYS = new HashSet<>(Arrays.asList("NAME", "QTH", "RIG", "ANT", "WX", "PWR"));
    private static final Set<String> STOP = new HashSet<>(Arrays.asList("K", "KN", "BK", "DE", "ES", "UR", "RST", "73", "TU", "SK", "?", "HW", "CQ", "AGN", "QRS", "QRQ", "HOBBY", "JOB", "AGE", "KEY", "QSL", "CONDX", "NAME", "QTH", "RIG", "ANT", "WX", "PWR"));
    private static final Set<String> CLOSINGS = new HashSet<>(Arrays.asList("73", "SK", "TU", "GB", "CUL"));
    private static final Set<String> REQUESTS = new HashSet<>(Arrays.asList("QRS", "QRQ", "AGN", "RPT"));
    private static final Set<String> TURNS = new HashSet<>(Arrays.asList("K", "KN", "BK"));
    private static final Pattern REPORT = Pattern.compile("[1-5][1-9NT][1-9NT]C?");
    private static final Pattern RUNON = Pattern.compile("[EISH5]{6,}");

    // What was found.
    String text = "";
    final List<String> tokens = new ArrayList<>();
    String toCall, fromCall, report, turn, greeting;
    boolean userCallHeard, callUnsure, errorSign, unreadable;
    final Map<String, String> values = new HashMap<>();
    final Set<String> questions = new LinkedHashSet<>(), requests = new LinkedHashSet<>(), closings = new LinkedHashSet<>();
    double known;

    static Heard parse(String text, Expect expect) { return read(text, expect, null); }

    /** Like {@link #parse(String, Expect)}, but a word that does not match may be re-read from the other readings the timing allowed. */
    static Heard parse(List<Letter> letters, Expect expect) {
        StringBuilder b = new StringBuilder();
        for (Letter l : letters) b.append(l.ch.startsWith("<") || l.ch.equals("*") ? " " + l.ch + " " : l.ch);
        return read(b.toString(), expect, letters);
    }

    // ---- Morse-aware matching ----

    /** True when the two characters' codes differ by exactly one element added, removed or flipped. */
    static boolean near(char a, char b) {
        String x = Morse.code(a), y = Morse.code(b);
        if (x == null || y == null || x.equals(y)) return false;
        if (x.length() == y.length()) { int d = 0; for (int i = 0; i < x.length(); i++) if (x.charAt(i) != y.charAt(i)) d++; return d == 1; }
        if (Math.abs(x.length() - y.length()) != 1) return false;
        String l = x.length() > y.length() ? x : y, s = x.length() > y.length() ? y : x;
        for (int i = 0; i < l.length(); i++) if ((l.substring(0, i) + l.substring(i + 1)).equals(s)) return true;
        return false;
    }

    /** Edit distance where swapping two letters whose codes differ by one element costs 0.5. */
    static double cost(String a, String b) { return distance(a, b, true); }
    static double plain(String a, String b) { return distance(a, b, false); }
    private static double distance(String a, String b, boolean morse) {
        double[][] m = new double[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) m[i][0] = i;
        for (int j = 0; j <= b.length(); j++) m[0][j] = j;
        for (int i = 1; i <= a.length(); i++) for (int j = 1; j <= b.length(); j++) {
            char x = a.charAt(i - 1), y = b.charAt(j - 1);
            double sub = x == y ? 0 : morse && near(x, y) ? 0.5 : 1;
            m[i][j] = Math.min(Math.min(m[i - 1][j] + 1, m[i][j - 1] + 1), m[i - 1][j - 1] + sub);
        }
        return m[a.length()][b.length()];
    }
    /** Cost 0 always; up to 0.5 for 3 letters (one Morse-near slip); up to 1 for 4 or more; up to 2 for 6 or more. */
    private static boolean acceptable(double c, int len) { return c == 0 || (len == 3 && c <= 0.5) || (len >= 4 && c <= 1) || (len >= 6 && c <= 2); }

    private static final int WORD = 0, CALL = 1, USER = 2, REPORT_K = 3, PRO = 4, NAMEW = 5, UNKNOWN = 6;

    private static String best(String tok, Set<String> pool, double[] out) {
        String bw = null; double bc = 1e9;
        for (String w : pool) {
            if (Math.abs(w.length() - tok.length()) > 2) continue;
            double c = cost(tok, w);
            if (c < bc) { bc = c; bw = w; }
        }
        out[0] = bc;
        return bw;
    }

    // ---- Splitting words that ran together ----

    private static boolean known(String t, Expect e) {
        return e.words.contains(t) || e.names.contains(t) || Profile.CALL.matcher(t).matches() || REPORT.matcher(t).matches();
    }

    /** Splits a long unknown token into the cheapest run of vocabulary words, callsign-shaped pieces and at most one stray letter. */
    private static List<String> split(String t, Expect e) {
        int n = t.length();
        double[] c = new double[n + 1]; int[] from = new int[n + 1];
        Arrays.fill(c, 1e9); c[0] = 0;
        for (int i = 0; i < n; i++) {
            if (c[i] >= 1e9) continue;
            if (c[i] + 1 < c[i + 1]) { c[i + 1] = c[i] + 1; from[i + 1] = i; }
            for (int j = i + 2; j <= n; j++) {
                String p = t.substring(i, j);
                double pc = e.words.contains(p) || e.names.contains(p) ? 0 : j - i >= 4 && Profile.CALL.matcher(p).matches() ? 0.5 + 0.02 * (j - i) : 1e9;
                if (c[i] + pc < c[j]) { c[j] = c[i] + pc; from[j] = i; }
            }
        }
        List<String> out = new ArrayList<>();
        // Only split when nearly all of it is explained: a stray letter or two, not a heap of small words found in noise.
        if (c[n] > 1.0) { out.add(t); return out; }
        StringBuilder run = new StringBuilder();
        List<String> rev = new ArrayList<>();
        for (int j = n; j > 0; j = from[j]) rev.add(t.substring(from[j], j));
        for (int k = rev.size() - 1; k >= 0; k--) {
            String p = rev.get(k);
            if (p.length() == 1 && !e.words.contains(p)) run.append(p);
            else { if (run.length() > 0) { out.add(run.toString()); run.setLength(0); } out.add(p); }
        }
        if (run.length() > 0) out.add(run.toString());
        return out;
    }

    // ---- The reader ----

    private static List<String> normalise(String text, Expect e) {
        List<String> out = new ArrayList<>();
        String up = text.toUpperCase().replace("?", " ? ");
        for (String t : up.trim().split("\\s+")) {
            if (t.isEmpty()) continue;
            if (RUNON.matcher(t).matches()) { out.add("<HH>"); continue; }
            if (t.equals("BT")) t = "<BT>"; else if (t.equals("KN")) t = "<KN>"; else if (t.equals("SK") || t.equals("VA")) t = "<SK>"; else if (t.equals("AR")) t = "<AR>";
            if (t.length() > 4 && !t.startsWith("<") && !known(t, e)) out.addAll(split(t, e)); else out.add(t);
        }
        return out;
    }

    private static Heard read(String text, Expect e, List<Letter> letters) {
        Heard h = new Heard();
        h.text = text;
        List<String> raw = normalise(text, e);
        int n = raw.size();
        String[] canon = new String[n]; int[] kind = new int[n]; boolean[] ok = new boolean[n];
        double userCost = 1e9;
        double[] c = new double[1];
        for (int i = 0; i < n; i++) {
            String t = raw.get(i);
            canon[i] = t; kind[i] = UNKNOWN;
            if (t.startsWith("<") && t.endsWith(">")) { kind[i] = PRO; ok[i] = true; if (t.equals("<HH>")) h.errorSign = true; continue; }
            if (t.equals("*") || t.equals("?")) { if (t.equals("?")) ok[i] = true; continue; }
            if (REPORT.matcher(t).matches()) {
                kind[i] = REPORT_K; ok[i] = true; canon[i] = t.replace('N', '9').replace('T', '0'); continue;
            }
            if (e.words.contains(t)) { kind[i] = WORD; ok[i] = true; continue; }
            if (e.names.contains(t)) { kind[i] = NAMEW; ok[i] = true; continue; }
            if (t.length() >= 3 && hasDigit(t)) {
                if (e.userCall != null) { double uc = cost(t, e.userCall); if (uc <= 2) { kind[i] = USER; ok[i] = true; canon[i] = e.userCall; userCost = Math.min(userCost, uc); continue; } }
                if (e.partnerCall != null && cost(t, e.partnerCall) <= 1) { kind[i] = CALL; ok[i] = true; canon[i] = e.partnerCall; continue; }
            }
            String w = best(t, e.words, c);
            if (w != null && acceptable(c[0], t.length())) { kind[i] = WORD; ok[i] = true; canon[i] = w; continue; }
            String nm = best(t, e.names, c);
            if (nm != null && acceptable(c[0], t.length())) { kind[i] = NAMEW; ok[i] = true; canon[i] = nm; continue; }
            if (Profile.CALL.matcher(t).matches()) { kind[i] = CALL; ok[i] = true; continue; }
            // Re-read from the timing: a word made of ambiguous letters may match when read the other way.
            if (letters != null) {
                String r = reread(t, letters, e);
                if (r != null) { canon[i] = r; kind[i] = e.names.contains(r) ? NAMEW : WORD; ok[i] = true; }
            }
        }
        for (int i = 0; i < n; i++) h.tokens.add(canon[i]);
        h.userCallHeard = userCost < 1e9; h.callUnsure = userCost > 0 && userCost < 1e9;
        // Callsigns: X DE Y.
        int de = h.tokens.indexOf("DE");
        if (de >= 0) {
            if (de > 0 && isCall(kind[de - 1])) h.toCall = canon[de - 1];
            if (de + 1 < n && isCall(kind[de + 1])) h.fromCall = canon[de + 1];
        } else for (int i = 0; i < n; i++) if (isCall(kind[i])) { h.fromCall = canon[i]; break; }
        List<String> reports = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String t = canon[i];
            if (kind[i] == REPORT_K) reports.add(t);
            String k = alias(t);
            boolean word = kind[i] == WORD;
            if (word && TURNS.contains(t)) h.turn = t;
            if (kind[i] == PRO && (t.equals("<KN>"))) h.turn = "KN";
            if (word && CLOSINGS.contains(t) || kind[i] == PRO && t.equals("<SK>")) h.closings.add(t.startsWith("<") ? "SK" : t);
            if (word && REQUESTS.contains(t)) h.requests.add(t);
            if (word && (t.equals("GM") || t.equals("GA") || t.equals("GE")) && h.greeting == null) h.greeting = t;
            if (t.equals("?") && (i == 0 || !(kind[i - 1] == WORD && QUESTIONS.contains(alias(canon[i - 1]))))) h.requests.add("?");
            if (!word || !QUESTIONS.contains(k)) continue;
            boolean asks = i + 1 < n && canon[i + 1].equals("?");
            if (!asks && i > 0 && (canon[i - 1].equals("UR") || canon[i - 1].equals("WHAT") || canon[i - 1].equals("HW"))) {
                boolean bare = i + 1 >= n || kind[i + 1] == PRO || STOP.contains(canon[i + 1]) || TURNS.contains(canon[i + 1]);
                asks = bare && !(k.equals("RST") && i + 1 < n && kind[i + 1] == REPORT_K);
            }
            if (asks) { h.questions.add(k); continue; }
            if (VALUE_KEYS.contains(k)) {
                StringBuilder v = new StringBuilder(); String last = null; int cnt = 0;
                for (int j = i + 1; j < n && cnt < 4; j++) {
                    if (kind[j] == PRO || STOP.contains(canon[j]) || kind[j] == REPORT_K && k.equals("NAME") || canon[j].equals(last)) break;
                    if (v.length() > 0) v.append(' ');
                    v.append(canon[j]); last = canon[j]; cnt++; ok[j] = true;
                }
                if (v.length() > 0 && !h.values.containsKey(k)) h.values.put(k, v.toString());
            }
        }
        // A report sent twice: take the one heard most often, the first if they differ.
        int bestVotes = 0;
        for (String rp : reports) { int v = 0; for (String x : reports) if (x.equals(rp)) v++; if (v > bestVotes) { bestVotes = v; h.report = rp; } }
        int good = 0;
        for (boolean b : ok) if (b) good++;
        h.known = n == 0 ? 0 : good / (double) n;
        h.unreadable = h.known < 0.3 && h.toCall == null && h.fromCall == null && !h.userCallHeard && h.report == null;
        return h;
    }

    private static boolean isCall(int k) { return k == CALL || k == USER; }
    private static boolean hasDigit(String t) { for (int i = 0; i < t.length(); i++) if (Character.isDigit(t.charAt(i))) return true; return false; }
    private static String alias(String t) {
        switch (t) {
            case "OP": return "NAME";
            case "HOBBIES": return "HOBBY";
            case "WORK": case "OCC": case "OCCUPATION": return "JOB";
            default: return t;
        }
    }

    /** Tries the other readings of the ambiguous letters that made up this token, at most 10 of them, and returns the best accepted word. */
    private static String reread(String token, List<Letter> letters, Expect e) {
        // Find the run of letters that spells this token (alternatives are text, so the token is matched by its letters' current reading).
        StringBuilder cur = new StringBuilder(); int start = -1, end = -1;
        for (int i = 0; i < letters.size() && end < 0; i++) {
            Letter l = letters.get(i);
            if (l.ch.equals(" ") || l.ch.startsWith("<")) { cur.setLength(0); start = -1; continue; }
            if (start < 0) start = i;
            cur.append(l.ch);
            if (cur.toString().equals(token)) end = i + 1;
            else if (cur.length() > token.length()) { cur.setLength(0); start = -1; }
        }
        if (end < 0) return null;
        List<Integer> amb = new ArrayList<>();
        for (int i = start; i < end; i++) if (!letters.get(i).alternatives.isEmpty()) amb.add(i);
        if (amb.isEmpty() || amb.size() > 10) return null;
        String bestWord = null; double bestCost = 1e9;
        Set<String> pool = new LinkedHashSet<>(e.words); pool.addAll(e.names);
        int combos = 1;
        for (int i : amb) combos *= 1 + letters.get(i).alternatives.size();
        for (int m = 0; m < combos; m++) {
            int rem = m, changes = 0; StringBuilder b = new StringBuilder();
            for (int i = start; i < end; i++) {
                Letter l = letters.get(i);
                if (!amb.contains(i)) { b.append(l.ch); continue; }
                int opts = 1 + l.alternatives.size(), pick = rem % opts; rem /= opts;
                b.append(pick == 0 ? l.ch : l.alternatives.get(pick - 1));
                if (pick != 0) changes++;
            }
            // A report-shaped reading counts as a hit, the more so the fewer letters had to be changed.
            if (REPORT.matcher(b).matches() && 0.4 + 0.05 * changes < bestCost) { bestCost = 0.4 + 0.05 * changes; bestWord = b.toString(); continue; }
            double[] c = new double[1];
            String w = best(b.toString(), pool, c);
            if (w != null && acceptable(c[0], b.length()) && c[0] + 0.05 * changes < bestCost) { bestCost = c[0] + 0.05 * changes; bestWord = w; }
        }
        return bestWord;
    }
}
