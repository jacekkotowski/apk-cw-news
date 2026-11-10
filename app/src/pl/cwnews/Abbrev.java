package pl.cwnews;

import java.util.HashMap;
import java.util.Map;

/**
 * Ham CW abbreviations. One table feeds both the text replacement and the reference list in the app.
 * Level 1 (light) is for the ones almost every CW operator knows; level 2 (full) adds the rarer ones;
 * level 0 is reference only and never replaces anything. Ambiguous ones (R for "are", K, N) are left out of replacement.
 */
final class Abbrev {
    private Abbrev() {}
    static final int OFF = 0, LIGHT = 1, FULL = 2;

    /** abbreviation | meaning (matched as plain words when level is 1 or 2) | level */
    private static final String[] TABLE = {
        "ES|and|1", "FER|for|1", "TNX|thanks|1", "TU|thank you|1", "GUD|good|1", "VY|very|1", "UR|your|1", "U|you|1",
        "ABT|about|1", "AGN|again|1", "PSE|please|1", "WX|weather|1", "HR|here|1", "HW|how|1", "FM|from|1", "B4|before|1", "INFO|information|1",
        "GM|good morning|1", "GA|good afternoon|1", "GE|good evening|1", "GN|good night|1", "GL|good luck|1",
        "CU|see you|1", "CUL|see you later|2", "73|best regards|1", "OM|old man|1", "FB|fine business|1",
        "WID|with|2", "SRI|sorry|2", "HPE|hope|2", "MSG|message|2", "RCVD|received|2", "YR|year|2", "YRS|years|2",
        "WKD|worked|2", "WKG|working|2", "ANT|antenna|2", "NR|number|2", "RPT|report|2", "TEMP|temperature|2", "DEG|degrees|2",
        "WUD|would|2", "CUD|could|2", "TMW|tomorrow|2", "THRU|through|2", "TIL|until|2", "CONDX|conditions|2", "VERT|vertical|2",
        "CQ|calling any station|0", "DE|this is (from)|0", "K|go ahead|0", "KN|go ahead, only the station named|0", "R|received, roger|0",
        "AR|end of message|0", "BT|pause, new paragraph|0", "SK|end of contact|0", "BK|break, back to you|0",
        "QTH|my location|0", "QRM|man-made interference|0", "QRN|static|0", "QRP|low power|0", "QRO|high power|0",
        "QRS|send slower|0", "QRQ|send faster|0", "QSB|fading|0", "QSL|message received, confirmed|0", "QSO|a contact|0",
        "QSY|change frequency|0", "QRL|frequency is in use|0", "QRZ|who is calling me|0", "RST|signal report: readability, strength, tone|0",
        "5NN|599, a contest report (N is 9)|0", "DX|long distance|0", "HI|laughter|0", "RIG|radio equipment|0", "OP|operator|0",
        "YL|young lady|0", "XYL|wife|0", "OB|old boy|0", "HAM|amateur radio operator|0", "88|love and kisses|0", "TX|transmit|0", "RX|receive|0",
    };

    private static int maxWords = 1;
    private static final Map<String, String>[] BY_LEVEL = build();

    @SuppressWarnings("unchecked")
    private static Map<String, String>[] build() {
        Map<String, String>[] m = new Map[3];
        for (int i = 0; i < 3; i++) m[i] = new HashMap<>();
        for (String row : TABLE) {
            String[] p = row.split("\\|");
            int level = Integer.parseInt(p[2]);
            if (level == 0) continue;
            String words = p[1].toUpperCase(java.util.Locale.ROOT);
            maxWords = Math.max(maxWords, words.split(" ").length);
            for (int l = level; l <= FULL; l++) m[l].put(words, p[0]);
        }
        return m;
    }

    /** Replaces whole words or phrases in upper-case morse text. Punctuation after a word is kept. */
    static String apply(String text, int level) {
        if (level <= OFF || text == null || text.isEmpty()) return text == null ? "" : text;
        Map<String, String> map = BY_LEVEL[Math.min(level, FULL)];
        String[] t = text.split(" ");
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < t.length; i++) {
            String hit = null, tail = "";
            int used = 1;
            for (int n = Math.min(maxWords, t.length - i); n >= 1 && hit == null; n--) {
                StringBuilder key = new StringBuilder();
                boolean ok = true;
                for (int j = 0; j < n && ok; j++) {
                    String w = t[i + j];
                    if (j == n - 1) {
                        int e = w.length();
                        while (e > 0 && ",.;:!?".indexOf(w.charAt(e - 1)) >= 0) e--;
                        tail = w.substring(e); w = w.substring(0, e);
                    }
                    if (w.isEmpty() || !w.chars().allMatch(c -> c >= 'A' && c <= 'Z')) ok = false;
                    if (j > 0) key.append(' ');
                    key.append(w);
                }
                if (ok && map.containsKey(key.toString())) { hit = map.get(key.toString()); used = n; }
            }
            if (out.length() > 0) out.append(' ');
            if (hit != null) { out.append(hit).append(tail); i += used - 1; } else out.append(t[i]);
        }
        return out.toString();
    }

    /** The whole table as JSON for the reference list: [{a, m, l}, ...]. */
    static String json() {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < TABLE.length; i++) {
            String[] p = TABLE[i].split("\\|");
            if (i > 0) b.append(',');
            b.append("{\"a\":\"").append(p[0]).append("\",\"m\":\"").append(p[1]).append("\",\"l\":").append(p[2]).append('}');
        }
        return b.append(']').toString();
    }
}
