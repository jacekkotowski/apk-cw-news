package pl.cwnews;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A story as a script of overs between stations: a radio net ("LV-426 Net" format, see docs/STORIES.md) or a plain
 * book. Pure Java, so it runs in the tests on a PC.
 */
final class Script {
    /** One over. text is ready for Morse (prosigns as {@code <BT>}); pauseS is the silence before it; episode indexes {@link #episodes}. */
    static final class Line {
        final String speaker, to, text;
        final double pauseS;
        final int episode;
        Line(String speaker, String to, String text, double pauseS, int episode) { this.speaker = speaker; this.to = to; this.text = text; this.pauseS = pauseS; this.episode = episode; }
    }
    /** An episode: its title and the index of its first line. */
    static final class Episode {
        final String title;
        final int first;
        Episode(String title, int first) { this.title = title; this.first = first; }
    }

    String title = "";
    final List<Line> lines = new ArrayList<>();
    final List<Episode> episodes = new ArrayList<>();
    String[] calls = new String[0];
    String[] names = new String[0];

    private static final Pattern STAMPED = Pattern.compile("^\\s*[\\d.]+ min  (.+)$");
    private static final Pattern MARKER = Pattern.compile("^===\\s*EP\\s*(\\d+)\\s*(.*?)\\s*===$");
    private static final Pattern BODY = Pattern.compile("^(\\S+) -> (\\S+)\\s+(.*)$");
    private static final Pattern LEGEND = Pattern.compile("^@\\s*(\\S+)\\s*=\\s*(.+)$");
    private static final Pattern PROSIGN = Pattern.compile("<[A-Z]{2}>");
    private static final Pattern HEADING = Pattern.compile("(?i)^(CHAPTER|BOOK|PART|ACT|SCENE|SECTION|PROLOGUE|EPILOGUE|PREFACE|INTRODUCTION)\\b.*");
    private static final int MAX_WORDS = 40;

    static Script parse(String fileName, String text) {
        Script s = new Script();
        if (text == null) text = "";
        if (text.length() > 0 && text.charAt(0) == '﻿') text = text.substring(1);
        String[] raw = text.split("\r\n|\r|\n", -1);
        boolean net = false;
        for (String l : raw) {
            Matcher m = STAMPED.matcher(l);
            if (m.matches()) {
                String b = m.group(1).trim();
                if (MARKER.matcher(b).matches() || BODY.matcher(b).matches()) { net = true; break; }
            }
        }
        String base = fileName == null ? "" : fileName.replaceAll("^.*[/\\\\]", "").replaceAll("\\.[A-Za-z0-9]{1,5}$", "").replace('_', ' ').trim();
        if (net) parseNet(s, raw, base); else parseBook(s, raw, base);
        return s;
    }

    private static String clean(String body) {
        String up = body.toUpperCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(up.length() + 8);
        Matcher m = PROSIGN.matcher(up);
        int at = 0;
        while (m.find()) {
            out.append(TextNorm.forMorse(up.substring(at, m.start()))).append(' ').append(m.group()).append(' ');
            at = m.end();
        }
        out.append(TextNorm.forMorse(up.substring(at)));
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private static void parseNet(Script s, String[] raw, String base) {
        for (String l : raw) { if (l.trim().length() > 0) { s.title = l.trim(); break; } }
        if (s.title.length() == 0) s.title = base;
        Map<String, String> legend = new HashMap<>();
        List<String> order = new ArrayList<>();
        Random rnd = new Random(s.title.hashCode());
        boolean seenTitle = false, newEp = false;
        for (String l : raw) {
            String t = l.trim();
            if (t.length() == 0) continue;
            if (!seenTitle) { seenTitle = true; continue; }
            Matcher lg = LEGEND.matcher(t);
            if (lg.matches()) {
                String c = lg.group(1).toUpperCase(Locale.ROOT);
                if (!legend.containsKey(c)) { legend.put(c, lg.group(2).trim()); order.add(c); }
                continue;
            }
            Matcher st = STAMPED.matcher(l);
            if (!st.matches()) continue;
            String b = st.group(1).trim();
            Matcher mk = MARKER.matcher(b);
            if (mk.matches()) {
                String et = mk.group(2).length() > 0 ? mk.group(2) : "EP " + mk.group(1);
                s.episodes.add(new Episode(et, s.lines.size()));
                newEp = true;
                continue;
            }
            Matcher bd = BODY.matcher(b);
            if (!bd.matches()) continue;
            String txt = clean(bd.group(3));
            if (txt.length() == 0) continue;
            String from = bd.group(1).toUpperCase(Locale.ROOT), to = bd.group(2).toUpperCase(Locale.ROOT);
            if (s.episodes.isEmpty()) s.episodes.add(new Episode(s.title, 0));
            double pause = s.lines.isEmpty() ? 0 : newEp ? 10 : Math.round((1.5 + 1.5 * rnd.nextDouble()) * 10) / 10.0;
            newEp = false;
            s.lines.add(new Line(from, to, txt, pause, s.episodes.size() - 1));
            if (!legend.containsKey(from) && !order.contains(from)) order.add(from);
        }
        s.calls = order.toArray(new String[0]);
        s.names = new String[s.calls.length];
        for (int i = 0; i < s.calls.length; i++) s.names[i] = legend.containsKey(s.calls[i]) ? legend.get(s.calls[i]) : s.calls[i];
    }

    private static boolean isHeading(String p) {
        if (p.length() == 0 || p.length() > 60 || p.indexOf('\n') >= 0) return false;
        if (HEADING.matcher(p).matches() && p.split("\\s+").length <= 10) return true;
        boolean letter = false;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (Character.isLetter(c)) { letter = true; if (Character.isLowerCase(c)) return false; }
        }
        return letter && p.split("\\s+").length <= 8 && !p.matches(".*[.!?]$");
    }

    private static void parseBook(Script s, String[] raw, String base) {
        List<String> body = new ArrayList<>();
        int start = -1, end = -1;
        String gTitle = null;
        for (int i = 0; i < raw.length; i++) {
            String t = raw[i].trim();
            if (start < 0 && t.startsWith("*** START OF")) start = i;
            else if (start >= 0 && end < 0 && t.startsWith("*** END OF")) end = i;
            if (start < 0 && gTitle == null && t.startsWith("Title:")) gTitle = t.substring(6).trim();
        }
        for (int i = start + 1; i < (end < 0 ? raw.length : end); i++) body.add(raw[i]);
        // paragraphs
        List<String> paras = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String l : body) {
            String t = l.trim();
            if (t.length() == 0) { if (cur.length() > 0) { paras.add(cur.toString()); cur.setLength(0); } }
            else { if (cur.length() > 0) cur.append(" "); cur.append(t); }
        }
        if (cur.length() > 0) paras.add(cur.toString());
        s.title = gTitle != null && gTitle.length() > 0 ? gTitle : base.length() > 0 ? base : "";
        if (s.title.length() == 0) for (String p : paras) { s.title = p.length() > 60 ? p.substring(0, 60) : p; break; }
        String call = shortCall(s.title);
        boolean any = false;
        for (String p : paras) if (!isHeading(p) && clean(p).length() > 0) { any = true; break; }
        if (any) {
            s.calls = new String[]{call};
            s.names = new String[]{s.title.length() > 0 ? s.title : call};
        }
        Random rnd = new Random(s.title.hashCode());
        boolean newEp = false;
        for (String p : paras) {
            if (isHeading(p)) {
                s.episodes.add(new Episode(clean(p).length() > 0 ? clean(p) : p, s.lines.size()));
                newEp = true;
                continue;
            }
            String txt = clean(p);
            if (txt.length() == 0) continue;
            for (String over : cut(txt)) {
                if (s.episodes.isEmpty()) s.episodes.add(new Episode(s.title, 0));
                double pause = s.lines.isEmpty() ? 0 : newEp ? 10 : Math.round((1.5 + 1.5 * rnd.nextDouble()) * 10) / 10.0;
                newEp = false;
                s.lines.add(new Line(call, "ALL", over + " <BT>", pause, s.episodes.size() - 1));
            }
        }
    }

    private static String shortCall(String title) {
        Matcher m = Pattern.compile("[A-Z0-9]+").matcher(TextNorm.forMorse(title));
        String c = "";
        while (m.find()) { if (!m.group().equals("THE") && !m.group().equals("A") && !m.group().equals("AN")) { c = m.group(); break; } }
        if (c.length() > 6) c = c.substring(0, 6);
        return c.length() == 0 ? "BOOK" : c;
    }

    /** Cuts a paragraph into overs of at most about MAX_WORDS words, at sentence ends. */
    private static List<String> cut(String txt) {
        List<String> sentences = new ArrayList<>();
        Matcher m = Pattern.compile("[^.?!]*[.?!]+(?=\\s|$)|[^.?!]+$").matcher(txt);
        while (m.find()) { String t = m.group().trim(); if (t.length() > 0) sentences.add(t); }
        List<String> overs = new ArrayList<>();
        StringBuilder o = new StringBuilder();
        int words = 0;
        for (String sen : sentences) {
            String[] w = sen.split(" ");
            if (w.length > MAX_WORDS) {
                if (words > 0) { overs.add(o.toString()); o.setLength(0); words = 0; }
                for (int i = 0; i < w.length; i += MAX_WORDS) {
                    StringBuilder part = new StringBuilder();
                    for (int j = i; j < Math.min(w.length, i + MAX_WORDS); j++) { if (part.length() > 0) part.append(' '); part.append(w[j]); }
                    overs.add(part.toString());
                }
                continue;
            }
            if (words > 0 && words + w.length > MAX_WORDS) { overs.add(o.toString()); o.setLength(0); words = 0; }
            if (o.length() > 0) o.append(' ');
            o.append(sen);
            words += w.length;
        }
        if (words > 0) overs.add(o.toString());
        return overs;
    }

    /**
     * Pitch offsets in Hz from the story frequency, one per call (parallel to {@link #calls}). Six slots, {@link #SLOTS}.
     * Calls are placed in order of how much they talk; each takes the slot that keeps it {@link #APART} Hz or more from
     * the partners it talks to most (then the farthest, then the least used). The result is then improved by a seeded
     * search so the least talk is left between calls closer than that. Three mutual partners fit (-75, -15, 45).
     */
    /** Pitch slots in Hz from the story frequency, 30 Hz apart (the ear tells 20 Hz apart; this only has to tell stations apart), and the least gap kept between partners. */
    static final int[] SLOTS = {-75, -45, -15, 15, 45, 75};
    static final int APART = 60;

    private static long cost(int[][] w, int[] slot, int[] slots) {
        long c = 0;
        for (int a = 0; a < slot.length; a++)
            for (int b = a + 1; b < slot.length; b++)
                if (w[a][b] > 0 && Math.abs(slots[slot[a]] - slots[slot[b]]) < APART) c += w[a][b];
        return c;
    }

    static int[] offsetsHz(Script s) {
        final int[] slots = SLOTS;
        int n = s.calls.length;
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < n; i++) idx.put(s.calls[i], i);
        int[] talk = new int[n];
        int[][] w = new int[n][n];
        for (Line l : s.lines) {
            Integer a = idx.get(l.speaker), b = idx.get(l.to);
            if (a == null) continue;
            talk[a]++;
            if (b != null && !b.equals(a)) { w[a][b]++; w[b][a]++; }
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        final int[] tk = talk;
        java.util.Arrays.sort(order, new java.util.Comparator<Integer>() {
            public int compare(Integer x, Integer y) { return tk[x] != tk[y] ? tk[y] - tk[x] : x - y; }
        });
        int[] slot = new int[n];
        java.util.Arrays.fill(slot, -1);
        int[] used = new int[slots.length];
        for (int c : order) {
            int best = 0;
            long bc = Long.MAX_VALUE, bd = -1;
            int bu = Integer.MAX_VALUE;
            for (int k = 0; k < slots.length; k++) {
                long conflict = 0, dist = 0;
                for (int p = 0; p < n; p++) {
                    if (slot[p] < 0 || w[c][p] == 0) continue;
                    int d = Math.abs(slots[k] - slots[slot[p]]);
                    if (d < APART) conflict += w[c][p];
                    dist += (long) w[c][p] * d;
                }
                if (conflict < bc || (conflict == bc && (dist > bd || (dist == bd && used[k] < bu)))) { best = k; bc = conflict; bd = dist; bu = used[k]; }
            }
            slot[c] = best;
            used[best]++;
        }
        // Improve it: hill climbing from the greedy result and from seeded random starts, keeping the least talk between
        // calls closer than 100 Hz. Deterministic, as the seed is the title.
        int[] bestSlot = slot.clone();
        long bestCost = cost(w, slot, slots);
        Random rnd = new Random(s.title.hashCode());
        for (int start = 0; start < 60 && n > 1; start++) {
            int[] t = bestSlot.clone();
            if (start > 0) for (int i = 0; i < n; i++) t[i] = rnd.nextInt(slots.length);
            for (int pass = 0; pass < 20; pass++) {
                boolean moved = false;
                for (int c = 0; c < n; c++) {
                    int keep = t[c];
                    long bc = cost(w, t, slots);
                    for (int k = 0; k < slots.length; k++) {
                        if (k == keep) continue;
                        t[c] = k;
                        long cf = cost(w, t, slots);
                        if (cf < bc) { bc = cf; keep = k; moved = true; }
                    }
                    t[c] = keep;
                }
                if (!moved) break;
            }
            long c = cost(w, t, slots);
            if (c < bestCost) { bestCost = c; bestSlot = t; }
        }
        slot = bestSlot;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) out[i] = slots[slot[i]];
        return out;
    }

    /** A safe name for an added file in the app's own folder: letters, digits, space, dot, dash and underscore, ending in .txt. */
    static String fileName(String display) {
        String n = display == null ? "" : display.replaceAll("^.*[/\\\\]", "").replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
        n = n.replaceAll("[^A-Za-z0-9 ._-]", "_").replaceAll("\\.{2,}", ".").replaceAll("^[ .]+|[ .]+$", "");
        if (n.length() > 60) n = n.substring(0, 60).trim();
        return (n.isEmpty() ? "story" : n) + ".txt";
    }

    /** Whether a story id is one this app can open: asset:NAME or file:NAME with a plain name, no folders. */
    static boolean validId(String id) {
        if (id == null) return false;
        String n = id.startsWith("asset:") ? id.substring(6) : id.startsWith("file:") ? id.substring(5) : null;
        return n != null && !n.isEmpty() && !n.contains("/") && !n.contains("\\") && !n.equals(".") && !n.equals("..");
    }

    /** This story for the Settings menu and the Episode menu: {"id","title","added","total","episodes":[{"t","first"}]}. */
    String json(String id) {
        StringBuilder b = new StringBuilder("{\"id\":").append(quote(id)).append(",\"title\":").append(quote(title))
            .append(",\"added\":").append(id.startsWith("file:")).append(",\"total\":").append(lines.size()).append(",\"episodes\":[");
        for (int i = 0; i < episodes.size(); i++) {
            if (i > 0) b.append(',');
            b.append("{\"t\":").append(quote(episodes.get(i).title)).append(",\"first\":").append(episodes.get(i).first).append('}');
        }
        return b.append("]}").toString();
    }

    static String quote(String t) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < ' ') b.append(' ');
            else b.append(c);
        }
        return b.append('"').toString();
    }
}
