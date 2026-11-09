package pl.cwnews;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns feed text into plain text the Morse tables can send. Characters without a Morse code are dropped. */
final class TextNorm {
    private TextNorm() {}
    private static final Map<String, String> ENTITY = new HashMap<>();
    static {
        String[][] e = {{"amp", "&"}, {"lt", "<"}, {"gt", ">"}, {"quot", "\""}, {"apos", "'"}, {"nbsp", " "}, {"ndash", "-"}, {"mdash", "-"}, {"hellip", "..."}, {"lsquo", "'"}, {"rsquo", "'"}, {"ldquo", "\""}, {"rdquo", "\""}, {"oacute", "ó"}, {"Oacute", "Ó"}, {"eacute", "é"}, {"aacute", "á"}, {"uuml", "ü"}, {"ouml", "ö"}, {"auml", "ä"}, {"szlig", "ß"}, {"euro", " EUR "}, {"pound", " GBP "}};
        for (String[] p : e) ENTITY.put(p[0], p[1]);
    }
    private static final Pattern SCRIPT = Pattern.compile("(?is)<(script|style)\\b.*?</\\1\\s*>");
    private static final Pattern BLOCK = Pattern.compile("(?i)</?(p|br|div|li|tr|h[1-6]|blockquote)\\b[^>]*>");
    private static final Pattern TAG = Pattern.compile("(?s)<[^>]{0,2000}>");
    private static final Pattern ENT = Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,8});");
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    private static final Pattern[] BOILER = {
        Pattern.compile("(?is)\\bthe post\\b.{0,300}\\bappeared first on\\b.*$"),
        Pattern.compile("(?i)\\b(read more|continue reading|click here|full story)\\b[.:\\s]*"),
        Pattern.compile("(?i)\\b(article url|comments url|points|# comments)\\s*:\\s*\\S*"),
        Pattern.compile("(?i)^\\s*comments\\s*$"),
    };

    static String decodeEntities(String s) {
        Matcher m = ENT.matcher(s);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            String n = m.group(1), r = null;
            try {
                if (n.startsWith("#x") || n.startsWith("#X")) r = new String(Character.toChars(Integer.parseInt(n.substring(2), 16)));
                else if (n.startsWith("#")) r = new String(Character.toChars(Integer.parseInt(n.substring(1))));
                else r = ENTITY.get(n);
            } catch (IllegalArgumentException ignored) { }
            m.appendReplacement(b, Matcher.quoteReplacement(r == null ? " " : r));
        }
        m.appendTail(b);
        return b.toString();
    }

    /** Removes markup, entities, links and common feed boilerplate. Keeps normal case for display. */
    static String cleanHtml(String html) {
        if (html == null) return "";
        String s = decodeEntities(html); // feeds often escape their markup
        s = SCRIPT.matcher(s).replaceAll(" ");
        s = BLOCK.matcher(s).replaceAll(" ");
        s = TAG.matcher(s).replaceAll("");
        s = decodeEntities(s);
        s = URL.matcher(s).replaceAll(" ");
        for (Pattern p : BOILER) s = p.matcher(s).replaceAll(" ");
        return s.replaceAll("\\s+", " ").trim();
    }

    /** Upper-case text containing only characters that exist in Morse. */
    static String forMorse(String text) {
        if (text == null) return "";
        StringBuilder a = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case 'Ą': case 'ą': a.append('A'); break;
                case 'Ć': case 'ć': a.append('C'); break;
                case 'Ę': case 'ę': a.append('E'); break;
                case 'Ł': case 'ł': a.append('L'); break;
                case 'Ń': case 'ń': a.append('N'); break;
                case 'Ó': case 'ó': a.append('O'); break;
                case 'Ś': case 'ś': a.append('S'); break;
                case 'Ź': case 'ź': case 'Ż': case 'ż': a.append('Z'); break;
                case 'ß': a.append("SS"); break;
                case '‘': case '’': case '`': a.append('\''); break;
                case '“': case '”': a.append('"'); break;
                case '–': case '—': case '−': a.append(" - "); break;
                case '…': a.append('.'); break;
                case '&': a.append(" AND "); break;
                case '%': a.append(" PERCENT "); break;
                default: a.append(Character.isWhitespace(c) || Character.isSpaceChar(c) ? ' ' : c);
            }
        }
        String d = Normalizer.normalize(a, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toUpperCase(java.util.Locale.ROOT);
        StringBuilder out = new StringBuilder(d.length());
        for (int i = 0; i < d.length(); i++) {
            char c = d.charAt(i);
            if (c == ' ' || Morse.supported(c)) out.append(c);
        }
        // A dash or stray mark standing alone would be sent as noise between words.
        return (" " + out + " ").replaceAll("(?<= )[-.,:;'\"_=+/]+(?= )", " ").replaceAll("\\s+", " ").trim();
    }

    /** Short station name, in the Morse News manner: "BBC World (BBC)" is sent as BBC. */
    static String shortSource(String name) {
        if (name == null) return "RSS";
        Matcher m = Pattern.compile("\\(([^()]{1,16})\\)\\s*$").matcher(name);
        String s = forMorse(m.find() ? m.group(1) : name);
        return s.isEmpty() ? "RSS" : s;
    }

    static final int HEADLINE = 0, HEADLINE_SUMMARY = 1;

    /** The text sent for one story: DE SOURCE BT HEADLINE BT SUMMARY AR. */
    static String story(String source, String title, String summary, int mode, boolean announce) {
        String t = forMorse(title), body = mode == HEADLINE_SUMMARY ? forMorse(summary) : "";
        StringBuilder b = new StringBuilder();
        if (announce) b.append("DE ").append(shortSource(source)).append(" <BT> ");
        b.append(t);
        if (!body.isEmpty() && !body.equals(t)) b.append(" <BT> ").append(body);
        b.append(" <AR>");
        return b.toString();
    }
}
