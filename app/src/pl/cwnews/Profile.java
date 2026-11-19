package pl.cwnews;

import java.util.regex.Pattern;

/** The user's own station: who they are on the air, and how they like contacts to go. No Android imports. */
final class Profile {
    static final Pattern CALL = Pattern.compile("[A-Z0-9]{1,3}[0-9][A-Z0-9]{0,3}[A-Z](/[A-Z0-9]+)?");
    static final int CALL_MAX = 10, TEXT_MAX = 20;
    static final int PATIENT = 0, NORMAL = 1, REALISTIC = 2;

    final String call, name, qth, rig, ant;
    final int power, sendWpm, manners, sendWith;

    Profile(String call, String name, String qth, String rig, String ant, int power, int sendWpm, int manners, int sendWith) {
        this.call = call; this.name = name; this.qth = qth; this.rig = rig; this.ant = ant;
        this.power = power;
        this.sendWpm = Math.max(5, Math.min(30, sendWpm));
        this.manners = Math.max(PATIENT, Math.min(REALISTIC, manners));
        this.sendWith = sendWith == 1 ? 1 : 0;
    }

    /** A made-up but valid station, so that contacts work before anything is entered. */
    static Profile defaults() { return new Profile("SP0CWN", "JACEK", "KRAKOW", "FT891", "DIPOLE", 10, 15, PATIENT, 0); }

    /** Upper-case, only characters that Morse can send (and single spaces), cut to the limit for that field. */
    static String clean(String key, String raw) {
        if (raw == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toUpperCase(raw.charAt(i));
            if (c == ' ') { if (b.length() > 0 && b.charAt(b.length() - 1) != ' ') b.append(c); }
            else if (Morse.supported(c)) b.append(c);
        }
        String s = b.toString().trim();
        int max = key.equals("mycall") ? CALL_MAX : TEXT_MAX;
        return s.length() > max ? s.substring(0, max).trim() : s;
    }

    /** "" when the cleaned value is acceptable, otherwise a message for the page. */
    static String check(String key, String clean) {
        if (key.equals("mycall") && !CALL.matcher(clean).matches()) return "That is not a callsign. A call looks like SP9ABC.";
        return "";
    }

    /** A rough stand-in for a country: the call up to its last digit, ignoring a portable prefix or suffix (SP9ABC/P, DL/SP9ABC). */
    static String country(String call) {
        String c = call == null ? "" : call;
        int slash = c.indexOf('/');
        if (slash > 0) { String a = c.substring(0, slash), b = c.substring(slash + 1); c = b.length() > a.length() ? b : a; }
        int d = c.length() - 1;
        while (d >= 0 && !Character.isDigit(c.charAt(d))) d--;
        return d > 0 ? c.substring(0, d) : c;
    }
}
