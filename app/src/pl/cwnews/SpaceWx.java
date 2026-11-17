package pl.cwnews;

/**
 * Reads NOAA's planetary K index, the measure of geomagnetic disturbance. It looks at the text directly, so it needs
 * no JSON library and runs on a PC. Pure Java; nothing here touches the network.
 */
final class SpaceWx {
    private SpaceWx() {}

    /** The address the app fetches, once per refresh and only when the user has switched real space weather on. */
    static final String URL = "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json";

    /** The newest Kp in the document, 0 to 9, or NaN when there is none or the text is not what was expected. */
    static double latestKp(String json) {
        if (json == null) return Double.NaN;
        // Current layout: a list of objects with a "Kp" number. The newest entry is the last.
        double kp = Double.NaN;
        int at = 0;
        while ((at = json.indexOf("\"Kp\"", at)) >= 0) {
            at += 4;
            int colon = json.indexOf(':', at);
            if (colon < 0 || json.substring(at, colon).trim().length() > 0) continue;
            double v = number(json, colon + 1);
            if (!Double.isNaN(v)) kp = v;
        }
        if (!Double.isNaN(kp)) return kp;
        // Older layout: a list of lists whose first row names the columns.
        int open = json.indexOf("[["), headEnd = json.indexOf(']', Math.max(0, open));
        if (open < 0 || headEnd < 0) return Double.NaN;
        String[] head = json.substring(open + 2, headEnd).split(",");
        int col = -1;
        for (int i = 0; i < head.length; i++) if (head[i].replace("\"", "").trim().equals("Kp")) col = i;
        if (col < 0) return Double.NaN;
        int last = json.lastIndexOf('['), end = json.indexOf(']', last);
        if (last <= open + 1 || end < 0) return Double.NaN;
        String[] row = json.substring(last + 1, end).split(",");
        return col < row.length ? number(row[col], 0) : Double.NaN;
    }

    /** A number, possibly in quotes, starting at the given place; NaN if it is not one or is outside 0 to 9. */
    private static double number(String s, int from) {
        int i = from;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '"')) i++;
        int j = i;
        while (j < s.length() && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) j++;
        if (j == i) return Double.NaN;
        try {
            double v = Double.parseDouble(s.substring(i, j));
            return v >= 0 && v <= 9 ? v : Double.NaN;
        } catch (NumberFormatException e) { return Double.NaN; }
    }
}
