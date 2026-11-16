package pl.cwnews;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Makes up one amateur radio contact in CW, the way operators really send it. Each operator has a personality that
 * sets speed, how much they say and what about; a contact is put together from topic modules instead of one script.
 * Things that should agree do: the report follows the signal the listener hears, the greeting follows the other
 * station's clock, the weather follows the season and the time of day.
 * Pure Java, driven by a seeded random source, so a test can repeat any contact.
 */
final class Qso {
    static final int RAGCHEW = 0, QUICK = 1, CQ = 2, SKED = 3, TUNE = 4, KINDS = 5;
    /** Personalities. */
    static final int CASUAL = 0, CHATTY = 1, TERSE = 2, NOVICE = 3, OUTDOOR = 4;

    /** One transmission: who sends and the text, with prosigns written as &lt;BT&gt;. */
    static final class Tx {
        final int who; final String text;
        /** Speed as a factor of the listener's speed setting, and the silence before this transmission in seconds. */
        final double speed, pause;
        /** Seconds of plain carrier instead of text, from someone tuning up; 0 for a normal transmission. */
        final double carrier;
        Tx(int who, String text, double speed, double pause) { this(who, text, speed, pause, 0); }
        Tx(int who, String text, double speed, double pause, double carrier) { this.who = who; this.text = text; this.speed = speed; this.pause = pause; this.carrier = carrier; }
    }

    /** When and where the band is being heard. Without a place there is no propagation: every station is equally likely. */
    static final class Scene {
        final int month, hourUtc; final boolean here, weekend, contest; final double lat, lon;
        Scene(int month, int hourUtc) { this(month, hourUtc, false, 0, 0, false, false); }
        Scene(int month, int hourUtc, double lat, double lon, boolean weekend, boolean contest) { this(month, hourUtc, true, lat, lon, weekend, contest); }
        private Scene(int month, int hourUtc, boolean here, double lat, double lon, boolean weekend, boolean contest) {
            this.month = month; this.hourUtc = hourUtc; this.here = here; this.lat = lat; this.lon = lon; this.weekend = weekend; this.contest = contest;
        }
    }
    /** Parts of the band where kinds of stations gather: anywhere, the fast and DX end, the QRP corner, the slow end. */
    static final int ANYWHERE = 0, FAST_END = 1, QRP_CORNER = 2, SLOW_END = 3;

    final int kind;
    /** Where on the band this contact belongs (one of the constants above). */
    int zone;
    final List<Tx> tx = new ArrayList<>();
    /** One entry per operator in the contact. Hours are local, 0 to 23. Loss is dB below a full-strength signal. */
    String[] call; boolean[] qrp, yl; int[] style, hour; double[] lossDb, qsbDb;
    /** Each operator's place, and the part of the loss that is theirs alone (power, antenna), without the path. */
    double[] lat, lon, baseDb;
    /** The key each operator uses (a Fist constant) and faults of their signal: chirp, a rough note, drift. */
    int[] key; boolean[] chirp, rough, drift;

    private Qso(int kind) { this.kind = kind; }

    private static final class Country {
        final String[] prefix, city, sota, om, yl; final int[] utc; final double[] lat, lon; final int digits, warm; final boolean us, south; final String pota;
        /** How many operators it has on the air, roughly, against a middling country as 1. */
        final double hams;
        /**
         * Cities may carry their own UTC offset and place as NAME@-5@42@-71. warm shifts the seasonal temperature in
         * degrees C. Names are "men|women". The place string is "latitude,longitude,hams".
         */
        Country(String prefixes, int digits, int utc, int warm, String cities, String names, String pota, String sota, boolean south, String place) {
            String[] at0 = place.split(",");
            hams = Double.parseDouble(at0[2]);
            prefix = prefixes.split(","); this.digits = digits; this.warm = warm; this.pota = pota; this.south = south;
            om = names.substring(0, names.indexOf('|')).split(","); yl = names.substring(names.indexOf('|') + 1).split(",");
            us = pota.equals("US");
            this.sota = sota == null ? new String[0] : sota.split(",");
            city = cities.split(","); this.utc = new int[city.length]; lat = new double[city.length]; lon = new double[city.length];
            for (int i = 0; i < city.length; i++) {
                String[] part = city[i].split("@");
                city[i] = part[0];
                this.utc[i] = part.length > 1 ? Integer.parseInt(part[1]) : utc;
                lat[i] = Double.parseDouble(part.length > 3 ? part[2] : at0[0]);
                lon[i] = Double.parseDouble(part.length > 3 ? part[3] : at0[1]);
            }
        }
    }

    private static final String EN = "JOHN,TOM,PAUL,MARK,MIKE,ANDY,DAVE,BOB,STEVE,KEN,BILL,JIM,JOE,RICK,GREG,NICK,FRED,GEORGE|SUE,LISA,RUTH,ELLEN,ANN,MARY",
        DE = "HANS,KARL,PETER,ULI,STEFAN,DIETER,KLAUS,WOLF,JURGEN,HEINZ|HELGA,INGA,CLARA,ANNA,EVA", PL = "JAN,TOMEK,MAREK,PIOTR,JACEK,ADAM,ANDRZEJ,KRZYS,WOJTEK,STAN|KASIA,ANNA,MARTA,EWA",
        FR = "PIERRE,JEAN,MICHEL,ALAIN,LUC,YVES|MARIE,CLAIRE,ANNE", IT = "LUIGI,MARCO,LUCA,PAOLO,GIANNI,FRANCO|MARIA,ANNA,LUCIA", ES = "CARLOS,PABLO,JUAN,LUIS,PEDRO,JOSE|MARIA,ANA,CARMEN",
        PT = "PAULO,JOAO,CARLOS,LUIS,PEDRO|MARIA,ANA", CZ = "JIRI,PETR,PAVEL,KAREL,TOMAS|EVA,JANA", FI = "PAAVO,JUHA,MATTI,TIMO,ARI|ANNA,LIISA", NORDIC = "SVEN,LARS,ERIK,OLE,NILS,BJORN,PER|INGA,ANNA,KARIN",
        NL = "JAN,PIET,HENK,KEES,WIM|ANNA,ELS", HU = "LASZLO,ISTVAN,GABOR,ZOLI,FERI|EVA,KATI", RO = "ION,DAN,ADRIAN,MIHAI|MARIA,ELENA", BG = "IVAN,GEORGI,DIMO,PETAR|MARIA,ELENA",
        GR = "NIKOS,KOSTAS,YANNIS,GEORGE|MARIA,ELENI", JP = "HIRO,TAKA,KEN,YOSHI,KAZU,AKI|YUKI,KEIKO";

    private static final Country[] COUNTRIES = {
        new Country("W,K,N,KB,WA,AA,KD", 10, -6, 2, "BOSTON@-5@42@-71,DENVER@-7@40@-105,AUSTIN@-6@30@-98,OMAHA@-6@41@-96,TAMPA@-5@28@-82,SEATTLE@-8@48@-122,BOISE@-7@44@-116,DAYTON@-5@40@-84,ALBANY@-5@43@-74,TULSA@-6@36@-96,RENO@-8@40@-120,PORTLAND@-8@46@-123,MADISON@-6@43@-89", EN, "US", "W7W/KG,W4C/WM,W2/GC,W0C/FR,W6/CT", false, "39,-95,3"),
        new Country("DL,DJ,DK,DF,DH", 9, 1, 0, "BERLIN,HAMBURG,MUNICH,KOELN,BREMEN,LEIPZIG,DRESDEN,KIEL,ESSEN", DE, "DE", "DL/AL,DM/BW,DM/TH", false, "51,10,2"),
        new Country("G,M", 9, 0, 0, "LONDON,LEEDS,BRISTOL,YORK,DERBY,DOVER,EXETER,OXFORD,CARDIFF", EN, "GB", "G/LD,G/SP,G/NP", false, "53,-2,1.5"),
        new Country("SP,SQ,SO", 9, 1, -1, "WARSAW,KRAKOW,GDANSK,LODZ,POZNAN,WROCLAW,LUBLIN,SZCZECIN,KATOWICE,BIALYSTOK", PL, "PL", "SP/BZ,SP/BW", false, "52,19,1.2"),
        new Country("F", 9, 1, 2, "PARIS,LYON,NANTES,LILLE,NICE,BREST,TOULOUSE", FR, "FR", "F/AM,F/PO", false, "47,2,1"),
        new Country("I,IK", 8, 1, 4, "ROME,MILAN,TURIN,BARI,GENOA,NAPLES,VERONA", IT, "IT", "I/LO,I/AA,I/PM", false, "43,12,1.2"),
        new Country("EA", 9, 1, 6, "MADRID,SEVILLE,VALENCIA,BILBAO,MALAGA", ES, "ES", "EA1/LE,EA2/NV,EA4/MD", false, "40,-4,1"),
        new Country("OK", 9, 1, -1, "PRAGUE,BRNO,OSTRAVA,PLZEN", CZ, "CZ", "OK/LI,OK/JM", false, "50,15,0.8"),
        new Country("OH", 9, 2, -6, "HELSINKI,TURKU,OULU,TAMPERE", FI, "FI", null, false, "62,25,0.7"),
        new Country("SM", 8, 1, -4, "STOCKHOLM,MALMO,UMEA,GOTEBORG", NORDIC, "SE", null, false, "60,16,0.7"),
        new Country("LA", 9, 1, -4, "OSLO,BERGEN,TROMSO", NORDIC, "NO", "LA/TM,LA/HL", false, "61,9,0.6"),
        new Country("OZ", 9, 1, -1, "COPENHAGEN,AARHUS,ODENSE", NORDIC, "DK", null, false, "56,10,0.6"),
        new Country("PA", 9, 1, 0, "AMSTERDAM,UTRECHT,DELFT,GRONINGEN", NL, "NL", "PA/PA", false, "52,5,0.8"),
        new Country("ON", 9, 1, 0, "BRUSSELS,GHENT,LEUVEN", FR, "BE", "ON/ON", false, "51,4,0.6"),
        new Country("HB", 9, 1, -1, "ZURICH,BERN,BASEL,LUGANO", DE, "CH", "HB/GR,HB/BE,HB/VS", false, "47,8,0.6"),
        new Country("OE", 9, 1, -1, "VIENNA,GRAZ,LINZ,SALZBURG", DE, "AT", "OE/TI,OE/ST,OE/SB,OE/KT", false, "47.5,14,0.6"),
        new Country("HA", 9, 1, 1, "BUDAPEST,GYOR,DEBRECEN", HU, "HU", "HA/KD", false, "47,19,0.6"),
        new Country("YO", 9, 2, 1, "BUCHAREST,CLUJ,IASI", RO, "RO", "YO/EC,YO/MC", false, "46,25,0.6"),
        new Country("LZ", 9, 2, 2, "SOFIA,PLOVDIV,VARNA", BG, "BG", "LZ/RO", false, "43,25,0.6"),
        new Country("SV", 9, 2, 6, "ATHENS,PATRAS,VOLOS", GR, "GR", "SV/AT", false, "39,22,0.5"),
        new Country("JA", 10, 9, 3, "TOKYO,OSAKA,NAGOYA,SAPPORO,KYOTO", JP, "JP", "JA/NN,JA/ST", false, "36,138,2"),
        new Country("VE", 8, -5, -5, "TORONTO@-5@44@-79,OTTAWA@-5@45@-76,CALGARY@-7@51@-114,MONTREAL@-5@46@-74", EN, "CA", "VE2/LR,VE7/GV", false, "45,-76,0.8"),
        new Country("VK", 8, 10, 6, "SYDNEY@10@-34@151,PERTH@8@-32@116,HOBART@10@-43@147,ADELAIDE@9@-35@139", EN, "AU", "VK3/VC,VK2/BM,VK1/AC", true, "-34,147,0.6"),
        new Country("ZL", 4, 12, 2, "AUCKLAND,WELLINGTON,DUNEDIN", EN, "NZ", "ZL1/AK,ZL3/CB", true, "-41,174,0.3"),
        new Country("ZS", 6, 2, 6, "CAPE TOWN,DURBAN,PRETORIA", EN, "ZA", "ZS/WC", true, "-29,26,0.3"),
        new Country("PY", 8, -3, 10, "SAO PAULO,CURITIBA,RECIFE", PT, "BR", null, true, "-22,-46,0.6"),
        new Country("LU", 8, -3, 5, "BUENOS AIRES,CORDOBA,ROSARIO", ES, "AR", null, true, "-34,-60,0.5"),
    };

    private static final String[] QRP_RIGS = {"KX3", "KX2", "K1", "FT-817", "FT-818", "IC-705", "IC-703", "MCHF", "TRUSDX", "UBITX", "QCX MINI", "MTR-3B", "NORCAL 40A", "SOFTROCK", "HOMEBREW 2 TRANSISTOR RIG", "RS-918 KIT", "QMX"};
    private static final String[] BIG_RIGS = {"IC-7300", "FT-991", "TS-590", "FT-DX10", "K3", "IC-7610", "TS-480", "FT-450", "TS-120S", "IC-756", "HW-101", "FT-710"};
    /** A trailing ^ marks an antenna whose height is worth mentioning. */
    private static final String[] ANTS = {"DIPOLE^", "INVERTED V^", "END FED HALF WAVE^", "G5RV^", "VERTICAL", "GROUND PLANE", "MAGNETIC LOOP", "DELTA LOOP^", "LONG WIRE^", "ROTARY DIPOLE^", "WINDOM^", "RANDOM WIRE", "LINKED DIPOLE^", "ZS6BKW^", "EFHW IN A TREE", "FAN DIPOLE^"};
    private static final String[] FIELD_ANTS = {"EFHW ON FISHING POLE", "LINKED DIPOLE ON A MAST", "VERTICAL WID 4 RADIALS", "RANDOM WIRE IN A TREE", "INVERTED V ON CARBON POLE", "WHIP ES COUNTERPOISE"};
    private static final String[] DAY_SKY = {"SUNNY", "CLOUDY", "RAIN", "FOG", "WINDY", "OVERCAST", "LIGHT RAIN", "NICE"};
    private static final String[] NIGHT_SKY = {"CLEAR SKY", "CLOUDY", "RAIN", "FOG", "WINDY", "OVERCAST", "LIGHT RAIN", "CALM"};
    private static final String[] HOBBIES = {"GARDENING", "HIKING", "CYCLING", "FISHING", "SOTA AND POTA", "MODEL TRAINS", "ASTRONOMY", "HOME BREWING", "WOODWORKING", "COOKING", "BIRDWATCHING", "SAILING", "GLIDING", "CHESS", "PHOTOGRAPHY", "3D PRINTING", "CODING", "READING", "SKIING", "MUSHROOM PICKING", "BEEKEEPING", "KAYAKING", "BUILDING KITS", "OLD RADIOS", "BAKING BREAD"};
    private static final String[] JOBS = {"ENGINEER", "TEACHER", "DRIVER", "NURSE", "DOCTOR", "FARMER", "PROGRAMMER", "ELECTRICIAN", "PILOT", "BAKER", "SAILOR", "CARPENTER"};
    private static final String[] KEYS = {"STRAIGHT KEY", "BUG", "PADDLE", "PADDLE", "SIDESWIPER"};
    private static final String[] DX = {"JA", "VK", "ZL", "W6", "PY", "ZS", "VE7", "KH6"};
    private static final String[] FACTS = {
        "FIRST MORSE MESSAGE IN 1844 WAS WHAT HATH GOD WROUGHT",
        "SOS IS NOT AN ABBREVIATION IT IS JUST EASY TO COPY",
        "TITANIC SENT CQD FIRST AND LATER SOS",
        "PARIS IS THE STANDARD WORD FOR WORDS PER MINUTE",
        "AT 20 WPM ONE DOT LASTS 60 MS",
        "MARCONI SENT THE LETTER S ACROSS THE ATLANTIC IN 1901",
        "FIRST TWO WAY AMATEUR LINK ACROSS THE ATLANTIC WAS IN 1923",
        "THE ISS HAS A HAM STATION CALLED ARISS",
        "73 MEANS BEST REGARDS AND IS OLD TELEGRAPH CODE",
        "SHIPS STOPPED USING MORSE FOR DISTRESS IN 1999",
        "1 W ON CW CAN CROSS AN OCEAN WITH GOOD CONDX",
        "W1AW SENDS CODE PRACTICE FROM NEWINGTON CT",
        "40 M IS BEST FOR DX AT NIGHT AND FOR LOCALS BY DAY",
        "SOLAR CYCLE 25 PEAKED ABOUT 2024",
        "E IS ONE DOT BECAUSE IT IS THE MOST COMMON LETTER",
        "A GOOD OP CAN COPY CW BELOW THE NOISE FLOOR",
    };

    private static final int RIG = 0, ANT = 1, WX = 2, AGE = 3, KEY = 4, HOBBY = 5, WORK = 6, QSL = 7, PET = 8, PROJECT = 9, NEWS = 10, FACT = 11,
        CONDX = 12, DXING = 13, SITE = 14, FIRST = 15, WEEK = 16, PLAN = 17, TOPICS = 18;
    /** Topics up to here are ones the other operator usually answers with their own. */
    private static final int LAST_MIRRORED = QSL;

    private static final class Op {
        int id, style, hour, temp, power, age, years;
        Country c;
        String call, name, city, rig, ant, wx, hobby, key, job, site, ref;
        boolean qrp, yl, storm, snow, chirp, rough, drift;
        double speed, lossDb, qsbDb, lat, lon, baseDb;
        final List<Integer> plan = new ArrayList<>();
    }

    /** GM, GA or GE for a local hour. */
    static String greeting(int localHour) { return localHour >= 5 && localHour < 12 ? "GM" : localHour >= 12 && localHour < 18 ? "GA" : "GE"; }

    /** The report a signal that many dB down deserves: 2 dB is 599, 30 dB is about 339. */
    static String rst(double lossDb) {
        int s = Math.max(3, Math.min(9, 9 - (int) Math.round((lossDb - 2) / 5)));
        return (s >= 5 ? "5" : s == 4 ? "4" : "3") + s + "9";
    }

    /** Shortens a headline to a few words that can be sent in a breath. */
    static String shortHeadline(String title) {
        String t = TextNorm.forMorse(title).replaceAll("[^A-Z0-9 ']", " ").replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return null;
        String[] w = t.split(" ");
        if (w.length < 3) return null;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(w.length, 9); i++) b.append(i > 0 ? " " : "").append(w[i]);
        return b.toString();
    }

    /**
     * @param month 0 to 11, so the weather talk fits the season
     * @param hourUtc 0 to 23, so greetings and weather fit each station's time of day
     * @param headline a short headline to mention, or null
     */
    static Qso generate(Dsp.Rng r, int month, int hourUtc, String headline) { return generate(r, new Scene(month, hourUtc), headline); }

    /** @param scene when and where the band is heard; with a place, who is on and how strong follows propagation */
    static Qso generate(Dsp.Rng r, Scene scene, String headline) {
        double k = r.uniform();
        // On a contest weekend most of the band is contest stations.
        int kind = scene.contest ? (k < 0.7 ? QUICK : k < 0.82 ? RAGCHEW : k < 0.92 ? CQ : k < 0.96 ? SKED : TUNE)
            : k < 0.48 ? RAGCHEW : k < 0.69 ? QUICK : k < 0.83 ? CQ : k < 0.95 ? SKED : TUNE;
        Qso q = new Qso(kind);
        Gen g = new Gen(r, scene, headline, q.tx);
        if (q.kind == CQ) g.loneCq();
        else if (q.kind == TUNE) g.tuneUp();
        else if (q.kind == QUICK) g.quick();
        else g.ragchew(q.kind == SKED);
        q.zone = g.zone;
        int n = g.ops.size();
        q.call = new String[n]; q.qrp = new boolean[n]; q.yl = new boolean[n]; q.style = new int[n]; q.hour = new int[n]; q.lossDb = new double[n]; q.qsbDb = new double[n];
        q.key = new int[n]; q.chirp = new boolean[n]; q.rough = new boolean[n]; q.drift = new boolean[n];
        q.lat = new double[n]; q.lon = new double[n]; q.baseDb = new double[n];
        for (int i = 0; i < n; i++) {
            Op o = g.ops.get(i);
            q.call[i] = o.call; q.qrp[i] = o.qrp; q.yl[i] = o.yl; q.style[i] = o.style; q.hour[i] = o.hour; q.lossDb[i] = o.lossDb; q.qsbDb[i] = o.qsbDb;
            q.key[i] = o.key.equals("BUG") ? Fist.BUG : o.key.equals("STRAIGHT KEY") ? Fist.STRAIGHT : o.key.equals("SIDESWIPER") ? Fist.SIDESWIPER : Fist.KEYER;
            q.chirp[i] = o.chirp; q.rough[i] = o.rough; q.drift[i] = o.drift;
            q.lat[i] = o.lat; q.lon[i] = o.lon; q.baseDb[i] = o.baseDb;
        }
        return q;
    }

    /** Builds one contact. */
    private static final class Gen {
        final Dsp.Rng r; final Scene scene; final int month, hourUtc; final List<Tx> tx;
        String headline;
        int zone;
        final List<Op> ops = new ArrayList<>();
        final double[] temp = new double[COUNTRIES.length];
        /** Current sending speed of each operator, and a slower speed they were asked for (0 if none). */
        final double[] spd = new double[8], want = new double[8];

        Gen(Dsp.Rng r, Scene scene, String headline, List<Tx> tx) {
            this.r = r; this.scene = scene; this.month = scene.month; this.hourUtc = scene.hourUtc; this.headline = headline; this.tx = tx;
            Arrays.fill(temp, Double.NaN);
        }

        String pick(String... a) { return a[r.nextInt(a.length)]; }
        static String join(List<String> seg) { return String.join(" <BT> ", seg); }

        // ---- operators ----

        int style() {
            double u = r.uniform();
            return u < 0.38 ? CASUAL : u < 0.58 ? CHATTY : u < 0.72 ? TERSE : u < 0.84 ? NOVICE : OUTDOOR;
        }

        String callsign(Country c) {
            StringBuilder b = new StringBuilder(c.prefix[r.nextInt(c.prefix.length)]);
            if (b.toString().equals("HB")) b.append('9');
            else b.append(c.digits == 10 ? r.nextInt(10) : 1 + r.nextInt(Math.min(c.digits, 9)));
            int letters = r.chance(0.7) ? 3 : 2;
            for (int i = 0; i < letters; i++) b.append((char) ('A' + r.nextInt(26)));
            return b.toString();
        }

        /** Path loss between two operators right now, or 0 when the scene has no place. */
        double path(Op a, Op b) { return scene.here ? Prop.pathLossDb(a.lat, a.lon, b.lat, b.lon, month, hourUtc) : 0; }
        /** How many dB down one operator's signal is at the other's station. */
        double between(Op heard, Op me) { return Math.min(60, heard.baseDb + path(heard, me)); }

        /**
         * Picks a country and city for a new operator. With a place in the scene the choice follows propagation: the
         * first operator is one the listener can hear, later ones are stations the given operator can hear. Big
         * countries and places where it is not the middle of the night come up more often.
         */
        int[] place(Op near) {
            if (!scene.here) { int ci = r.nextInt(COUNTRIES.length); return new int[]{ci, r.nextInt(COUNTRIES[ci].city.length)}; }
            double fromLat = near == null ? scene.lat : near.lat, fromLon = near == null ? scene.lon : near.lon, total = 0;
            double[][] w = new double[COUNTRIES.length][];
            for (int i = 0; i < w.length; i++) {
                Country c = COUNTRIES[i];
                w[i] = new double[c.city.length];
                for (int k = 0; k < w[i].length; k++) {
                    // Cities of one country share its place unless they carry their own, so work those out once.
                    if (k > 0 && c.lat[k] == c.lat[0] && c.lon[k] == c.lon[0] && c.utc[k] == c.utc[0]) { w[i][k] = w[i][0]; }
                    else {
                        double loss = Prop.pathLossDb(fromLat, fromLon, c.lat[k], c.lon[k], month, hourUtc);
                        w[i][k] = c.hams / w[i].length * Prop.awake(hourUtc + c.utc[k], scene.weekend) * Math.max(0.004, Math.pow(10, -loss / 12));
                    }
                    total += w[i][k];
                }
            }
            double u = r.uniform() * total;
            for (int i = 0; i < w.length; i++) for (int k = 0; k < w[i].length; k++) { u -= w[i][k]; if (u <= 0) return new int[]{i, k}; }
            return new int[]{0, 0};
        }

        Op op(int style) { return op(style, null); }
        Op op(int style, Op near) {
            Op o = new Op();
            int[] where = place(near);
            int ci = where[0];
            Country c = COUNTRIES[ci];
            o.id = ops.size(); o.style = style; o.c = c;
            do {
                o.call = callsign(c);
            } while (taken(o.call));
            o.yl = r.chance(0.15);
            o.name = o.yl ? c.yl[r.nextInt(c.yl.length)] : c.om[r.nextInt(c.om.length)];
            int city = where[1];
            o.city = c.city[city]; o.lat = c.lat[city]; o.lon = c.lon[city];
            o.hour = ((hourUtc + c.utc[city]) % 24 + 24) % 24;

            double qrpOdds = style == OUTDOOR ? 0.9 : style == TERSE ? 0.15 : 0.42;
            if (r.chance(qrpOdds)) { o.rig = QRP_RIGS[r.nextInt(QRP_RIGS.length)]; o.power = new int[]{1, 2, 3, 5, 5, 5, 10}[r.nextInt(7)]; }
            else { o.rig = BIG_RIGS[r.nextInt(BIG_RIGS.length)]; o.power = new int[]{20, 50, 100, 100, 100}[r.nextInt(5)]; }
            o.qrp = o.power <= 10;
            // Simple and home-made rigs are the ones that chirp or have a rough note; old valve rigs drift.
            boolean simple = o.rig.contains("HOMEBREW") || o.rig.contains("KIT") || o.rig.equals("UBITX") || o.rig.equals("NORCAL 40A") || o.rig.equals("SOFTROCK");
            o.chirp = r.chance(simple ? 0.35 : 0.04);
            o.rough = r.chance(simple ? 0.25 : 0.02);
            o.drift = r.chance(o.rig.equals("HW-101") || o.rig.equals("TS-120S") || simple ? 0.5 : 0.05);
            if (style == OUTDOOR) {
                o.ant = FIELD_ANTS[r.nextInt(FIELD_ANTS.length)];
                double u = r.uniform();
                o.site = u < 0.4 && c.sota.length > 0 ? "SOTA" : u < 0.8 ? "POTA" : "FIELD";
                if (o.site.equals("SOTA")) o.ref = c.sota[r.nextInt(c.sota.length)] + "-" + pad(1 + r.nextInt(150), 3);
                if (o.site.equals("POTA")) o.ref = c.pota + "-" + pad(1 + r.nextInt(c.us ? 9000 : 900), 4);
                o.call += "/P";
            } else {
                String a = ANTS[r.nextInt(ANTS.length)];
                boolean high = a.endsWith("^");
                if (high) a = a.substring(0, a.length() - 1);
                int m = 5 + r.nextInt(20);
                o.ant = a + (high && r.chance(0.7) ? " UP ABT " + (c.us ? Math.round(m * 3.3) + " FT" : m + " M") : "");
            }

            if (Double.isNaN(temp[ci])) {
                double m = c.south ? (month + 6) % 12 : month;
                double swing = Math.max(5, Math.min(15, 13 - c.warm));   // warm places have milder seasons
                temp[ci] = 11 + c.warm - swing * Math.cos((m + 0.5) * 2 * Math.PI / 12) + r.gauss() * 3;
            }
            o.temp = (int) Math.round(temp[ci] + r.gauss() * 1.5 + (o.hour < 6 || o.hour >= 21 ? -4 : 0));
            boolean night = o.hour < 6 || o.hour >= 20;
            String sky = (night ? NIGHT_SKY : DAY_SKY)[r.nextInt(DAY_SKY.length)];
            if (o.temp <= 0 && sky.contains("RAIN")) { sky = sky.replace("RAIN", "SNOW"); o.snow = true; }
            if (o.temp >= 16 && sky.equals("FOG")) sky = "CLOUDY";
            if (o.temp >= 20 && !night && r.chance(0.1)) { sky = "STORM COMING"; o.storm = true; }
            o.wx = sky + pick(" ES ", " TEMP ", " ") + (c.us ? Math.round(o.temp * 1.8 + 32) + " F" : o.temp + " C");

            o.hobby = HOBBIES[r.nextInt(HOBBIES.length)];
            o.job = JOBS[r.nextInt(JOBS.length)];
            o.key = style == NOVICE ? pick("STRAIGHT KEY", "PADDLE") : KEYS[r.nextInt(KEYS.length)];
            o.age = style == CHATTY ? 55 + r.nextInt(30) : style == NOVICE ? 16 + r.nextInt(50) : 22 + r.nextInt(55);
            o.years = style == NOVICE ? 1 : 1 + r.nextInt(Math.max(1, o.age - 15));

            o.speed = style == TERSE ? 1.15 + 0.25 * r.uniform() : style == NOVICE ? 0.6 + 0.15 * r.uniform()
                : style == CHATTY ? 0.85 + 0.25 * r.uniform() : style == OUTDOOR ? 0.9 + 0.25 * r.uniform() : 0.9 + 0.3 * r.uniform();
            double u = r.uniform();
            o.baseDb = Math.min(32, 2 + 28 * u * u + (o.qrp ? 5 : 0));
            // What the listener hears: the station's own strength less the path to the listener.
            o.lossDb = Math.min(60, o.baseDb + (scene.here ? Prop.pathLossDb(scene.lat, scene.lon, o.lat, o.lon, month, hourUtc) : 0));
            o.qsbDb = 2 + 12 * r.uniform();
            spd[o.id] = o.speed;
            ops.add(o);
            return o;
        }

        boolean taken(String call) { for (Op o : ops) if (o.call.equals(call) || o.call.equals(call + "/P")) return true; return false; }
        static String pad(int n, int width) { String s = "" + n; while (s.length() < width) s = "0" + s; return s; }

        // ---- sending ----

        /** A natural pause before this operator answers. */
        double reply(Op o) {
            double p = 0.6 + 2 * r.uniform();
            if (o.style == NOVICE) p += 1 + 1.5 * r.uniform();
            if (o.style == TERSE) p *= 0.5;
            return p;
        }

        void send(Op o, String text) { send(o, text, reply(o)); }
        void send(Op o, String text, double pause) {
            String t = text.replaceAll("\\s+", " ").trim();
            slow(o);   // asked to slow down: does so even when the over has no room to say it
            if (r.chance(o.style == NOVICE ? 0.3 : o.key.equals("BUG") ? 0.05 : 0.015)) t = fumble(t);
            tx.add(new Tx(o.id, t, spd[o.id], pause));
        }

        /** Sends part of a word wrong, then the error sign and the word again. */
        String fumble(String text) {
            String[] w = text.split(" ");
            if (w.length < 4) return text;
            for (int tries = 0; tries < 6; tries++) {
                int i = r.nextInt(w.length);
                if (!w[i].matches("[A-Z]{4,}")) continue;
                int cut = 1 + r.nextInt(w[i].length() - 2);
                char wrong = (char) ('A' + r.nextInt(26));
                if (wrong == w[i].charAt(cut)) continue;
                w[i] = w[i].substring(0, cut) + wrong + " <HH> " + w[i];
                return String.join(" ", w);
            }
            return text;
        }

        /** Slows down if the other operator asked for it; returns what to say about it, or null. */
        String slow(Op me) {
            if (want[me.id] <= 0) return null;
            spd[me.id] = Math.min(spd[me.id], want[me.id]);
            want[me.id] = 0;
            return pick("SRI WILL QRS", "OK QRS NW", "R QRS");
        }

        // ---- phrases ----

        String report(Op me, Op heard) {
            String s = rst(between(heard, me) + 2 * r.gauss());
            // The last digit is the tone: 9 is a pure note. C marks chirp.
            if (heard.rough) s = s.substring(0, 2) + pick("7", "8");
            if (heard.chirp && r.chance(0.6)) s += "C";
            String q = heard.qsbDb > 9 && r.chance(0.7) ? pick(" WID QSB", " QSB") : between(heard, me) > 24 && r.chance(0.4) ? " WEAK BUT OK" : "";
            if (me.style == TERSE) return "UR " + s + q;
            return pick("UR RST ", "UR ", "RST IS ", "UR RST IS ") + s + " " + s + q;
        }

        /** How to address someone whose name is not known yet. Nobody is called OM on a guess. */
        String stranger(Op to) { return to.yl ? "" : pick(" OM", " DR OM", ""); }
        String friend(Op to) { return to.yl ? pick(to.name, "DR " + to.name) : pick(to.name, to.name, "DR " + to.name, "OM"); }

        String qth(Op me) {
            if (me.site == null) return pick("QTH ", "QTH IS ", "QTH NR ") + me.city + (me.style == CHATTY ? " " + me.city : "");
            if (me.site.equals("SOTA")) return "QTH SOTA SUMMIT " + me.ref + " NR " + me.city;
            if (me.site.equals("POTA")) return "QTH POTA PARK " + me.ref + " NR " + me.city;
            return pick("AM PORTABLE NR ", "QTH PORTABLE IN FIELD NR ", "ON HOLIDAY NR ") + me.city;
        }
        String name(Op me) { return me.style == TERSE ? "OP " + me.name : pick("NAME ", "NAME IS ", "OP ", "NAME HR ") + me.name + " " + me.name; }

        /** The first full over of a contact: thanks, report, QTH and name. */
        String basics(Op me, Op ot, boolean first) {
            List<String> seg = new ArrayList<>();
            String g = greeting(ot.hour), sl = slow(me);
            if (first) seg.add(g + stranger(ot) + " " + (me.style == CHATTY ? pick("TNX FER CALL ES NICE TO MEET U", "MNI TNX FER CALL") : pick("TNX FER CALL", "TNX CALL", "TNX FER THE CALL")));
            else seg.add(pick("R R ", "R ", "OK ") + g + " " + ot.name + " " + pick("TNX FER RPT", "TNX FER FB RPT", "TNX RPT"));
            if (sl != null) seg.add(sl);
            seg.add(report(me, ot));
            if (r.chance(0.5)) { seg.add(qth(me)); seg.add(name(me)); } else { seg.add(name(me)); seg.add(qth(me)); }
            if (me.style == NOVICE && spd[ot.id] > spd[me.id] * 1.2) {
                seg.add(pick("PSE QRS", "PSE QRS AM NEW TO CW", "QRS PSE"));
                want[ot.id] = spd[me.id] * 1.05;
            }
            return join(seg);
        }

        String say(int t, Op me, Op ot, boolean[] said) {
            said[t] = true;
            switch (t) {
                case RIG: {
                    String s = pick("RIG HR IS ", "RIG ", "RUNNING ", "HR USING ") + me.rig + (me.qrp ? pick(" QRP ", " PWR ONLY ", " ES ") : pick(" PWR ", " ES ", " ABT ")) + me.power + " W";
                    if (me.style == TERSE && !said[ANT]) { said[ANT] = true; s += " ANT " + me.ant; }
                    return s;
                }
                case ANT: return pick("ANT IS ", "ANT ", "ANT HR IS ") + me.ant;
                case WX: return pick("WX HR ", "WX IS ", "WX ") + me.wx + (me.temp >= 30 ? " VY HOT" : me.temp <= -5 ? " BRR" : "");
                case AGE:
                    if (me.style == NOVICE) return pick("GOT MY LICENSE LAST YEAR", "AM NEW HAM ES LEARNING CW");
                    return pick("AGE HR " + me.age + " ES HAM FER " + me.years + " YRS", "BEEN HAM FER " + me.years + " YRS", "AM " + me.age + " YRS OLD");
                case KEY: return me.key.equals("BUG") ? pick("USING OLD BUG SO SRI FER MY FIST HI", "KEY IS A BUG") : pick("KEY IS ", "USING ", "KEY HR ") + me.key;
                case HOBBY: return pick("WHEN NOT ON AIR I ENJOY ", "OTHER HOBBY IS ", "HR ALSO LIKE ", "BESIDES RADIO I LOVE ") + me.hobby;
                case WORK: return me.age >= 65 ? pick("NW RETIRED WAS ", "RETIRED ") + me.job : me.age < 20 ? "STILL AT SCHOOL" : pick("WORK AS ", "JOB IS ") + me.job;
                case QSL: return pick("QSL VIA BURO", "QSL VIA LOTW", "PSE QSL VIA EQSL", "QSL SURE VIA BURO ES LOTW");
                case PET: {
                    List<String> a = new ArrayList<>(Arrays.asList("MY CAT SLEEPS ON THE RIG HI", "DOG WANTS A WALK SOON", "SHACK IS A MESS AS ALWAYS HI"));
                    if (me.age >= 60) a.add("GRANDSON IS LEARNING CW NW");
                    if (!me.yl) a.add("XYL SAYS TOO MANY RADIOS HI");
                    if (me.hour >= 5 && me.hour < 11) a.add("COFFEE ES CW IS BEST START OF DAY");
                    return a.get(r.nextInt(a.size()));
                }
                case PROJECT: return pick("JUST PUT UP NEW ANT LAST WEEK", "BUILDING A QCX KIT NW", "RESTORING OLD HW-101", "NEW RIG ARRIVED LAST WEEK", "MADE A PADDLE FROM SCRAP HI", "WORKING ON MAG LOOP FER BALCONY");
                case NEWS: {
                    if (headline == null) return null;
                    String h = headline; headline = null;
                    return "BTW HRD ON NEWS TODAY " + h + " <BT> HV U HRD IT?";
                }
                case FACT: return "DID U KNOW " + FACTS[r.nextInt(FACTS.length)];
                case CONDX: {
                    if (ot.qsbDb > 9) return pick("QSB ON UR SIG BUT OK", "SOME QSB HR");
                    int m = me.c.south ? (month + 6) % 12 : month;
                    if (m >= 5 && m <= 7 && r.chance(0.5)) return "QRN FROM TSTORMS HR";
                    return pick("CONDX GUD TODAY", "BAND IS IN FB SHAPE", "40 M IS BUSY TODAY", "CONDX UP ES DOWN", "BAND WAS DEAD THIS MORNING");
                }
                case DXING: {
                    String d = DX[r.nextInt(DX.length)];
                    if (d.startsWith(me.c.prefix[0])) return null;
                    return me.qrp ? pick("CHASING DXCC WID QRP", "WKD " + d + " WID " + me.power + " W LAST WEEK") : "WKD " + d + " LAST NITE ON 40 M";
                }
                case SITE:
                    if (me.site == null) return null;
                    if (me.site.equals("SOTA")) return pick("NICE VIEW FROM SUMMIT", "VY WINDY UP HR", "CLIMBED 2 HRS TO GET HR") + (me.temp < 5 ? " ES FINGERS COLD HI" : "");
                    if (me.site.equals("POTA")) return pick("SITTING AT PICNIC TABLE IN THE PARK", "RIG ON A BENCH ES RUNNING ON BATT", "NICE QUIET PARK NO QRM");
                    return pick("RUNNING ON BATT", "RIG IN BACKPACK ES WIRE IN A TREE", "NO QRM OUT HR FB");
                case FIRST: return pick("U ARE MY " + (3 + r.nextInt(40)) + " CW QSO", "THIS IS MY FIRST CW QSO", "STILL LEARNING CW SO SRI FER ERRORS");
                case WEEK: return pick("HW WAS UR WEEK?", "ALL OK AT HOME?", "HW IS THE FAMILY?");
                case PLAN: return pick("GOING TO HAMFEST NEXT MONTH", "WILL BE QRV FROM A SUMMIT ON SAT", "ANT WORK PLANNED FER WEEKEND", "CLUB MEETING ON FRIDAY");
                default: return null;
            }
        }

        /** A short answer to what the other operator just said on a topic, or null. */
        String react(int t, Op me, Op ot) {
            switch (t) {
                case RIG:
                    if (ot.qrp) return between(ot, me) < 15 ? pick("ONLY " + ot.power + " W WOW", ot.power + " W DOING FB JOB", "FB QRP SIG") : pick(ot.power + " W BUT OK CPY", "QRP WORKS HI");
                    return ot.rig.equals(me.rig) ? "SAME RIG HR HI" : r.chance(0.5) ? pick("FB RIG", "NICE RIG") : null;
                case ANT: return r.chance(0.5) ? pick("UR ANT WORKS WELL", "FB ANT") : null;
                case WX:
                    if (ot.storm) return pick("STAY SAFE", "WATCH THAT STORM");
                    if (ot.snow) return "BRR SNOW HI";
                    if (me.temp - ot.temp >= 8) return "WARMER HR";
                    if (ot.temp - me.temp >= 8) return "COLDER HR";
                    return null;
                case AGE: return ot.style == NOVICE ? pick("WELCOME TO CW", "WELCOME TO THE HOBBY") : ot.years >= 40 ? "LONG TIME ON THE AIR FB" : null;
                case KEY: return ot.key.equals("BUG") ? "UR BUG SOUNDS FB" : ot.key.equals("STRAIGHT KEY") ? "NICE FIST ON STRAIGHT KEY" : null;
                case HOBBY: return ot.hobby.equals(me.hobby) ? "I ALSO LIKE " + ot.hobby + " HI" : r.chance(0.5) ? ot.hobby + " IS NICE" : null;
                case PET: return "HI HI";
                case PROJECT: return pick("GL WID PROJECT", "FB PROJECT");
                case NEWS: return pick("NO NOT YET WILL CHECK IT OUT TNX", "YES SAW IT TOO WOW HI", "NO NEWS HR ONLY RADIO HI");
                case FACT: return pick("DID NOT KNOW TNX", "FB FACT HI HI", "YES I KNEW HI");
                case CONDX: return pick("YES SAME HR", "AGREE");
                case DXING: return "FB DX CONGRATS";
                case SITE: return ot.site == null ? null : ot.site.equals("SOTA") ? pick("FB SOTA ENJOY THE SUMMIT", "TNX FER SOTA POINTS") : ot.site.equals("POTA") ? pick("ENJOY THE PARK", "TNX FER THE PARK") : "ENJOY THE FRESH AIR";
                case FIRST: return pick("CONGRATS UR CW IS FB", "U ARE DOING FB KEEP GOING");
                case WEEK: return pick("ALL FB HR TNX", "BUSY WEEK BUT OK");
                case PLAN: return pick("FB HPE IT GOES WELL", "SOUNDS GUD");
                default: return null;
            }
        }

        void plan(Op o, boolean friends) {
            List<Integer> core = new ArrayList<>(), extra = new ArrayList<>();
            int take;
            if (friends) { extra.addAll(Arrays.asList(WEEK, WX, PROJECT, PLAN, PET, NEWS, CONDX, DXING)); take = 2 + r.nextInt(4); }
            else if (o.style == TERSE) { core.add(RIG); if (r.chance(0.5)) core.add(QSL); take = 0; }
            else if (o.style == NOVICE) { core.addAll(Arrays.asList(FIRST, RIG, ANT, WX, KEY)); take = 0; }
            else if (o.style == OUTDOOR) { core.addAll(Arrays.asList(SITE, RIG, ANT, WX)); extra.addAll(Arrays.asList(QSL, CONDX)); take = r.nextInt(2); }
            else {
                core.addAll(Arrays.asList(RIG, ANT, WX));
                if (r.chance(0.4)) java.util.Collections.swap(core, 1, 2);
                extra.addAll(Arrays.asList(HOBBY, AGE, KEY, CONDX, QSL, NEWS, FACT, WORK, PROJECT, DXING));
                if (o.style == CHATTY) { extra.add(PET); take = 5 + r.nextInt(4); } else take = 2 + r.nextInt(3);
            }
            for (int i = extra.size() - 1; i > 0; i--) java.util.Collections.swap(extra, i, r.nextInt(i + 1));
            o.plan.clear(); o.plan.addAll(core);
            for (int i = 0; i < Math.min(take, extra.size()); i++) o.plan.add(extra.get(i));
        }

        /** Why this operator has to go, and what the other one says to that. Either may be null. */
        String[] reason(Op me, Op ot) {
            List<String[]> a = new ArrayList<>();
            if (me.hour >= 11 && me.hour < 14) a.add(new String[]{"LUNCH IS READY", "ENJOY UR LUNCH"});
            if (me.hour >= 17 && me.hour < 21) a.add(new String[]{me.yl ? "DINNER IS READY" : "XYL CALLS FER DINNER", "ENJOY UR DINNER"});
            if (me.hour >= 22 || me.hour < 4) a.add(new String[]{"GETTING LATE HR", "SLEEP WELL"});
            if (me.hour >= 5 && me.hour < 9) a.add(new String[]{"MUST GO TO WORK NW", "GL AT WORK"});
            if (me.storm) a.add(new String[]{"TSTORM COMING MUST PULL THE PLUG", "STAY SAFE"});
            if (me.site != null) { a.add(new String[]{"BATT IS LOW", "SAFE TRIP HOME"}); if (me.temp < 5) a.add(new String[]{"FINGERS ARE COLD HI", "GET WARM HI"}); }
            if (ot.qsbDb > 9) a.add(new String[]{"QSB GETTING WORSE", "YES QSB HR TOO"});
            if (!a.isEmpty() && r.chance(0.7)) return a.get(r.nextInt(a.size()));
            return r.chance(0.6) ? new String[]{pick("WILL LET U GO", "MUST QRT NW", "WILL LOOK FER MORE STNS"), null} : null;
        }

        String bye73(Op from, Op to) {
            String s = from.yl && to.yl ? pick("33 ES 73", "73 ES 33") : pick("73", "VY 73", "73 ES GL", "73 ES GB", "73 ES GD DX", "BEST 73");
            if (to.hour >= 21 || to.hour < 4) s += " GN";
            return s + (r.chance(0.3) ? pick(" CUL", " BCNU") : "");
        }

        // ---- contacts ----

        String cqText(Op a) {
            String A = a.call;
            if (a.site != null && !a.site.equals("FIELD") && r.chance(0.7)) return "CQ " + a.site + " CQ " + a.site + " DE " + A + " " + A + " K";
            if (a.qrp && r.chance(0.3)) return "CQ QRP CQ QRP DE " + A + " " + A + " K";
            if (a.style == TERSE) return r.chance(0.3) ? "CQ DX CQ DX DE " + A + " " + A + " DX K" : "CQ CQ DE " + A + " " + A + " K";
            if (!a.key.equals("PADDLE") && r.chance(0.2)) return "CQ SKCC CQ SKCC DE " + A + " " + A + " K";
            return pick("CQ CQ CQ DE " + A + " " + A + " " + A + " PSE K", "CQ CQ DE " + A + " " + A + " K", "CQ CQ CQ DE " + A + " " + A + " K", "CQ CQ CQ DE " + A + " " + A + " " + A + " <AR> K");
        }

        /** Checks the frequency, then calls CQ the given number of times. */
        void callCq(Op a, int times) {
            double first = 0.5 + 2 * r.uniform();
            if (r.chance(0.25)) { send(a, r.chance(0.5) ? "QRL?" : "QRL? DE " + a.call, first); first = 2 + 2 * r.uniform(); }
            String cq = cqText(a);
            for (int i = 0; i < times; i++) send(a, cq, i == 0 ? first : 3 + 4 * r.uniform());
        }

        /** Someone tunes up on the frequency: a carrier or two, maybe a few test letters, and gone. */
        void tuneUp() {
            Op a = op(style());
            int n = 1 + r.nextInt(3);
            for (int i = 0; i < n; i++) tx.add(new Tx(a.id, "", spd[a.id], i == 0 ? 0.5 + 2 * r.uniform() : 0.4 + 1.5 * r.uniform(), 1.5 + 3.5 * r.uniform()));
            if (r.chance(0.35)) send(a, pick("VVV", "VVV VVV", "E E E", "TEST"), 0.5 + r.uniform());
        }

        /** The part of the band an operator would pick for a CQ. */
        int home(Op a) {
            if (a.style == NOVICE || a.speed < 0.8) return SLOW_END;
            if (a.site != null || (a.qrp && r.chance(0.7))) return QRP_CORNER;
            if (a.style == TERSE) return FAST_END;
            return ANYWHERE;
        }

        void loneCq() {
            Op a = op(style());
            zone = home(a);
            callCq(a, 2 + r.nextInt(5));
        }

        /** One side asks for a repeat of something lost in the noise; the other sends it again, three times over. */
        void fill(Op asker, Op giver) {
            String cause = giver.qsbDb > 9 ? "QSB" : between(giver, asker) > 20 ? pick("UR SIG WEAK", "QRN") : pick("QRM", "QRN", "QRM HR");
            int what = r.nextInt(3);
            String n = giver.name, c = giver.city;
            send(asker, giver.call + " DE " + asker.call + " SRI " + cause + " <BT> PSE RPT " + (what == 0 ? "NAME" : what == 1 ? "QTH" : "NAME ES QTH") + " " + pick("BK", "<KN>"));
            send(giver, pick("BK ", "R ") + (what != 1 ? "NAME " + n + " " + n + " " + n : "") + (what == 2 ? " <BT> " : "") + (what != 0 ? "QTH " + c + " " + c + " " + c : "") + " " + pick("BK", "OK? BK"));
        }
        boolean lost(Op giver, Op asker) { return r.chance(0.05 + (giver.qsbDb > 9 ? 0.12 : 0) + (between(giver, asker) > 20 ? 0.15 : 0)); }

        void ragchew(boolean friends) {
            int sa = friends ? pick3(CASUAL, CHATTY, CASUAL) : style(), sb = friends ? pick3(CASUAL, CHATTY, CHATTY) : style();
            Op a, b;
            // Usually the listener can hear the station that calls; sometimes only the one that answers.
            if (!scene.here || r.chance(0.75)) { a = op(sa); b = op(sb, a); } else { b = op(sb); a = op(sa, b); }
            if (!friends) zone = home(a);
            String A = a.call, B = b.call;
            plan(a, friends); plan(b, friends);
            if (spd[b.id] > spd[a.id] * 1.15 && r.chance(0.8)) spd[b.id] = spd[a.id] * 1.1;   // a good operator answers at the caller's speed
            List<String> lead = null;
            if (friends) {
                send(a, B + " " + B + " DE " + A + " " + A + " K", 0.5 + 2 * r.uniform());
                if (r.chance(0.3)) send(a, B + " " + B + " DE " + A + " " + A + " K", 4 + 3 * r.uniform());
                send(b, A + " DE " + B + " " + greeting(a.hour) + " " + a.name + " <BT> " + pick("ON TIME AS ALWAYS HI", "GUD TO HR U AGN", "NICE TO HR U") + " <BT> UR " + rst(between(a, b) + 2 * r.gauss())
                    + " HR <BT> HW? " + A + " DE " + B + " <KN>");
                lead = new ArrayList<>();
                lead.add(greeting(b.hour) + " " + b.name + pick(" OLD FRIEND", " MY FRIEND", ""));
                lead.add("UR " + rst(between(b, a) + 2 * r.gauss()) + (b.qsbDb > 9 ? " WID QSB" : ""));
            } else {
                callCq(a, r.chance(0.25) ? 2 : 1);
                send(b, r.chance(0.7) ? A + " DE " + B + " " + B + " K" : B + " " + B);
                if (r.chance(0.1 + (between(b, a) > 20 ? 0.2 : 0))) {
                    send(a, r.chance(0.5) ? "QRZ? DE " + A + " K" : B.substring(0, 2 + r.nextInt(2)) + "? DE " + A + " K");
                    send(b, "DE " + B + " " + B + " " + B + " K");
                }
                if (spd[a.id] > spd[b.id] * 1.15 && r.chance(0.6)) spd[a.id] = spd[b.id] * 1.1;
                send(a, B + " DE " + A + " <BT> " + basics(a, b, true) + " <BT> HW? " + B + " DE " + A + " <KN>");
                if (lost(a, b)) fill(b, a);
                String sl = basics(b, a, false);
                send(b, A + " DE " + B + " <BT> " + sl + " <BT> HW? " + A + " DE " + B + " <KN>");
                if (lost(b, a) && r.chance(0.6)) fill(a, b);
            }
            chat(a, b, lead, friends);
            if (!friends && r.chance(0.15)) tailEnd(a);
        }
        int pick3(int a, int b, int c) { int i = r.nextInt(3); return i == 0 ? a : i == 1 ? b : c; }

        /** The body of a contact: operators take turns, answer what was said, add their own topics, and one of them wraps up. */
        void chat(Op a, Op b, List<String> lead, boolean friends) {
            Op[] o = {a, b};
            boolean bk = a.style != NOVICE && b.style != NOVICE && r.chance(0.35);
            boolean[][] said = new boolean[2][TOPICS];
            int[] left = new int[2];
            boolean[] sorry = new boolean[2];
            for (int i = 0; i < 2; i++) left[i] = o[i].style == CHATTY ? 3 + r.nextInt(2) : o[i].style == CASUAL ? 2 + r.nextInt(2) : o[i].style == TERSE ? 1 : 1 + r.nextInt(2);
            List<Integer> pending = new ArrayList<>();
            for (int n = 0, x = 0; ; n++, x = 1 - x) {
                Op me = o[x], ot = o[1 - x];
                List<String> seg = new ArrayList<>();
                if (n == 0 && lead != null) seg.addAll(lead);
                else if (ot.qsbDb > 9 && !sorry[x] && r.chance(0.25)) { sorry[x] = true; seg.add("R " + ot.name + " SRI QSB BUT GOT MOST"); }
                else seg.add(pick("R FB " + ot.name, "R R " + ot.name, "OK " + ot.name, "FB " + ot.name + " SOLID CPY", "R " + friend(ot), "R " + ot.name + " ALL CPY", "TNX " + ot.name));
                String sl = slow(me);
                if (sl != null) seg.add(sl);
                int reacts = me.style == CHATTY ? 2 : me.style == TERSE ? (r.chance(0.3) ? 1 : 0) : 1;
                for (int t : pending) {
                    if (reacts == 0) break;
                    String c = react(t, me, ot);
                    if (c != null) { seg.add(c); reacts--; }
                }
                List<Integer> raised = new ArrayList<>();
                if (left[x] > 0 && n < 10) {
                    int budget = me.style == CHATTY ? 3 + r.nextInt(2) : me.style == TERSE ? 1 + r.nextInt(2) : 2 + r.nextInt(2);
                    for (int t : pending) {
                        if (budget == 0 || t > LAST_MIRRORED || said[x][t] || !r.chance(me.style == TERSE ? 0.5 : 0.85)) continue;
                        String s = say(t, me, ot, said[x]);
                        if (s != null) { seg.add(s); raised.add(t); budget--; }
                    }
                    while (budget > 0 && !me.plan.isEmpty()) {
                        int t = me.plan.remove(0);
                        if (said[x][t] || (t > LAST_MIRRORED && said[1 - x][t])) continue;   // no second fun fact or band report
                        String s = say(t, me, ot, said[x]);
                        if (s != null) { seg.add(s); raised.add(t); budget--; }
                    }
                }
                if (raised.isEmpty()) { goodbye(me, ot, seg, friends); return; }
                left[x]--;
                String A = ot.call + " DE " + me.call;
                if (bk && n >= 1) send(me, "BK " + join(seg) + " BK");
                else if (seg.size() > 3 && r.chance(0.03)) {
                    // Something at home interrupts the over.
                    int cut = 1 + r.nextInt(seg.size() - 2);
                    send(me, A + " <BT> " + join(seg.subList(0, cut)) + " <AS>");
                    send(me, pick("SRI PHONE RANG", me.yl ? "SRI DOORBELL" : "SRI XYL CALLED", "BACK NW SRI") + " <BT> " + join(seg.subList(cut, seg.size())) + " <BT> HW? " + A + " <KN>", 5 + 5 * r.uniform());
                } else {
                    String tail = r.chance(0.15) ? " <AR> " : " <BT> " + pick("HW? ", "HW? ", "SO HW? ", "", "BK TO U ");
                    send(me, A + " <BT> " + join(seg) + tail + A + " " + (me.style == TERSE ? "K" : "<KN>"));
                }
                pending = raised;
            }
        }

        void goodbye(Op me, Op ot, List<String> seg, boolean friends) {
            String[] why = reason(me, ot);
            if (why != null) seg.add(why[0]);
            seg.add(friends ? pick("TNX FER SKED " + ot.name, "FB CHAT AS ALWAYS") : pick("TNX FER NICE QSO " + ot.name, "TNX FB QSO " + ot.name, "MNI TNX FER QSO", "TNX FER CHAT " + ot.name));
            if (friends) seg.add(pick("CU NEXT WEEK SAME TIME", "SKED AGN NEXT WEEK?", "CU ON SUNDAY"));
            else if (r.chance(0.6)) seg.add(pick("HPE CU AGN", "HPE CUAGN SN", "CU ON THE BAND"));
            if (month == 11 && r.chance(0.6)) seg.add("MERRY XMAS ES HNY");
            if (month == 0 && r.chance(0.4)) seg.add("HNY");
            seg.add(bye73(me, ot));
            String A = ot.call + " DE " + me.call, B = me.call + " DE " + ot.call;
            send(me, A + " <BT> " + join(seg) + " <BT> " + A + " " + pick("<KN>", "<KN>", "<SK>"));
            List<String> f = new ArrayList<>();
            f.add(pick("R ", "OK ", "") + pick("TNX QSO " + me.name, "TNX FER QSO " + me.name, "TNX " + me.name + " ENJOYED IT"));
            String sl = slow(ot);
            if (sl != null) f.add(sl);
            if (why != null && why[1] != null) f.add(why[1]);
            if (friends) f.add(pick("OK NEXT WEEK", "R CU THEN"));
            f.add(bye73(ot, me));
            send(ot, B + " <BT> " + join(f) + " <BT> " + B + " <SK>");
            if (r.chance(0.8)) send(me, pick("E E", "TU E E", "73 E E", "GB E E"), 0.4 + 0.6 * r.uniform());
            if (r.chance(0.35)) send(ot, "E E", 0.3 + 0.4 * r.uniform());
        }

        /** A third station was listening and calls one of the two as soon as they finish. */
        void tailEnd(Op a) {
            Op c = op(pick3(CASUAL, TERSE, CASUAL), a);
            if (spd[c.id] > spd[a.id] * 1.15) spd[c.id] = spd[a.id] * 1.1;
            send(c, a.call + " DE " + c.call + " " + c.call + " K", 1 + 1.5 * r.uniform());
            send(a, c.call + " DE " + a.call + " " + greeting(c.hour) + " UR " + rst(between(c, a) + 2 * r.gauss()) + " <BT> NAME " + a.name + " BK");
            send(c, "BK R TNX " + a.name + " UR " + rst(between(a, c) + 2 * r.gauss()) + " <BT> NAME " + c.name + " " + c.name + " QTH " + c.city + " <BT> 73 TU");
            send(a, "R TNX " + c.name + " 73 <SK> E E");
        }

        /** An activator in a park or on a summit, or a contest station, working a few callers one after another. */
        void quick() {
            // Contest stations fill the band on the big weekends, turn up on other weekends and are rare in the week.
            double u = r.uniform(), test = scene.contest ? 1 : !scene.here ? 0.4 : scene.weekend ? 0.3 : 0.08;
            String what = u < test ? "TEST" : u < test + (1 - test) * 0.65 ? "POTA" : "SOTA";
            zone = what.equals("TEST") ? FAST_END : QRP_CORNER;
            Op a;
            if (what.equals("TEST")) a = op(TERSE);
            else {
                do { ops.clear(); a = op(OUTDOOR); } while (!what.equals(a.site));
            }
            String A = a.call;
            int callers = 1 + r.nextInt(4), nr = 1 + r.nextInt(600);
            boolean refGiven = false;
            String cq = what.equals("TEST") ? pick("CQ TEST " + A + " " + A + " TEST", "TEST " + A + " " + A, "CQ TEST DE " + A + " " + A) : "CQ " + what + " CQ " + what + " DE " + A + " " + A + " K";
            send(a, cq, 0.5 + 2 * r.uniform());
            for (int i = 0; i < callers; i++) {
                Op c = op(pick3(CASUAL, TERSE, CASUAL), a);
                String C = c.call;
                spd[c.id] = Math.min(Math.max(spd[c.id], 0.95), spd[a.id] * 1.1);
                double quickly = 0.3 + 0.6 * r.uniform();
                send(c, r.chance(0.5) ? C : C + " " + C, i == 0 ? reply(c) : quickly);
                if (r.chance(0.2)) { send(a, C.substring(0, 2 + r.nextInt(2)) + "?", quickly); send(c, C + " " + C, quickly); }
                if (what.equals("TEST")) {
                    send(a, C + " 5NN " + pad(nr, 3), quickly);
                    if (r.chance(0.15)) { send(c, "NR?", quickly); send(a, pad(nr, 3) + " " + pad(nr, 3), quickly); }
                    send(c, (r.chance(0.5) ? "R " : "") + "5NN " + pad(1 + r.nextInt(300), 3) + (r.chance(0.3) ? " TU" : ""), quickly);
                    send(a, "TU " + A + (r.chance(0.5) ? " TEST" : ""), quickly);
                    nr++;
                } else {
                    String mine = rst(between(c, a) + 2 * r.gauss()), theirs = rst(between(a, c) + 2 * r.gauss());
                    boolean ref = !refGiven || r.chance(0.3);
                    refGiven = true;
                    send(a, C + " " + greeting(c.hour) + " UR " + mine + " " + mine + (ref ? " <BT> REF " + a.ref + " " + a.ref : "") + " BK");
                    if (!ref && r.chance(0.3)) { send(c, "REF?"); send(a, "REF " + a.ref + " " + a.ref + " BK"); }
                    send(c, "BK " + (r.chance(0.5) ? "R " : "") + "TU UR " + theirs + " " + theirs + pick(" 73", " 73 ES GL", " TNX FER " + what + " 73") + " BK");
                    send(a, i + 1 < callers || r.chance(0.5) ? "BK TU 73 DE " + A + " " + what + " K" : "TU 73 E E");
                }
            }
            if (r.chance(0.4)) send(a, cq, 3 + 3 * r.uniform());
        }
    }
}
