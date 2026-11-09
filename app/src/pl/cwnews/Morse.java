package pl.cwnews;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** International Morse tables. The character set follows cwtool (MIT, see THIRD_PARTY.md). */
final class Morse {
    private Morse() {}
    private static final Map<Character, String> CODE = new HashMap<>();
    private static final Map<String, String> PROSIGN = new HashMap<>();

    static {
        String[] t = {"A.-", "B-...", "C-.-.", "D-..", "E.", "F..-.", "G--.", "H....", "I..", "J.---", "K-.-", "L.-..", "M--", "N-.", "O---", "P.--.", "Q--.-", "R.-.", "S...", "T-", "U..-", "V...-", "W.--", "X-..-", "Y-.--", "Z--..",
            "1.----", "2..---", "3...--", "4....-", "5.....", "6-....", "7--...", "8---..", "9----.", "0-----",
            ",--..--", "?..--..", ":---...", "--....-", "\".-..-.", "(-.--.", ")-.--.-", "=-...-", "..-.-.-", ";-.-.-.", "/-..-.", "'.----.", "_..--.-", "+.-.-.", "@.--.-.", "!-.-.--", "$...-..-"};
        for (String s : t) CODE.put(s.charAt(0), s.substring(1));
        PROSIGN.put("BT", "-...-");
        PROSIGN.put("AR", ".-.-.");
        PROSIGN.put("SK", "...-.-");
        PROSIGN.put("KN", "-.--.");
    }

    static boolean supported(char c) { return CODE.containsKey(c); }
    static String code(char c) { return CODE.get(c); }

    /** Splits text into words, each a list of codes. A prosign written as &lt;BT&gt; is one token with no inner letter gaps. */
    static List<List<String>> parse(String text) {
        List<List<String>> words = new ArrayList<>();
        List<String> word = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                if (!word.isEmpty()) { words.add(word); word = new ArrayList<>(); }
            } else if (c == '<') {
                int end = text.indexOf('>', i);
                if (end < 0) continue;
                String p = PROSIGN.get(text.substring(i + 1, end));
                if (p != null) word.add(p);
                i = end;
            } else {
                String code = CODE.get(c);
                if (code != null) word.add(code);
            }
        }
        if (!word.isEmpty()) words.add(word);
        return words;
    }
}
