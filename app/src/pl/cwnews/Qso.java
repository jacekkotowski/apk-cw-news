package pl.cwnews;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes up one amateur radio contact in CW, the way operators really send it: abbreviations, prosigns, RST reports,
 * rig and antenna talk, weather, a hobby, now and then a fun fact or a real headline from the user's feeds.
 * Pure Java, driven by a seeded random source, so a test can repeat any contact.
 */
final class Qso {
    static final int RAGCHEW = 0, QUICK = 1, CQ = 2;

    /** One transmission: who sends (0 or 1) and the text, with prosigns written as &lt;BT&gt;. */
    static final class Tx {
        final int who; final String text;
        Tx(int who, String text) { this.who = who; this.text = text; }
    }

    final int kind;
    final String[] call = new String[2];
    final boolean[] qrp = new boolean[2];
    final List<Tx> tx = new ArrayList<>();

    private Qso(int kind) { this.kind = kind; }

    private static final class Country {
        final String[] prefix, cities; final int digits; final boolean us, south;
        Country(String prefixes, int digits, String cities, boolean us, boolean south) {
            prefix = prefixes.split(","); this.digits = digits; this.cities = cities.split(","); this.us = us; this.south = south;
        }
    }

    private static final Country[] COUNTRIES = {
        new Country("W,K,N,KB,WA,AA,KD", 10, "BOSTON,DENVER,AUSTIN,OMAHA,TAMPA,SEATTLE,BOISE,DAYTON,ALBANY,TULSA,RENO,PORTLAND,MADISON", true, false),
        new Country("DL,DJ,DK,DF,DH", 9, "BERLIN,HAMBURG,MUNICH,KOELN,BREMEN,LEIPZIG,DRESDEN,KIEL,ESSEN", false, false),
        new Country("G,M", 9, "LONDON,LEEDS,BRISTOL,YORK,DERBY,DOVER,EXETER,OXFORD,CARDIFF", false, false),
        new Country("SP,SQ,SO", 9, "WARSAW,KRAKOW,GDANSK,LODZ,POZNAN,WROCLAW,LUBLIN,SZCZECIN,KATOWICE,BIALYSTOK", false, false),
        new Country("F", 9, "PARIS,LYON,NANTES,LILLE,NICE,BREST,TOULOUSE", false, false),
        new Country("I,IK", 8, "ROME,MILAN,TURIN,BARI,GENOA,NAPLES,VERONA", false, false),
        new Country("EA", 9, "MADRID,SEVILLE,VALENCIA,BILBAO,MALAGA", false, false),
        new Country("OK", 9, "PRAGUE,BRNO,OSTRAVA,PLZEN", false, false),
        new Country("OH", 9, "HELSINKI,TURKU,OULU,TAMPERE", false, false),
        new Country("SM", 8, "STOCKHOLM,MALMO,UMEA,GOTEBORG", false, false),
        new Country("LA", 9, "OSLO,BERGEN,TROMSO", false, false),
        new Country("OZ", 9, "COPENHAGEN,AARHUS,ODENSE", false, false),
        new Country("PA", 9, "AMSTERDAM,UTRECHT,DELFT,GRONINGEN", false, false),
        new Country("ON", 9, "BRUSSELS,GHENT,LEUVEN", false, false),
        new Country("HB", 9, "ZURICH,BERN,BASEL,LUGANO", false, false),
        new Country("OE", 9, "VIENNA,GRAZ,LINZ,SALZBURG", false, false),
        new Country("HA", 9, "BUDAPEST,GYOR,DEBRECEN", false, false),
        new Country("YO", 9, "BUCHAREST,CLUJ,IASI", false, false),
        new Country("LZ", 9, "SOFIA,PLOVDIV,VARNA", false, false),
        new Country("SV", 9, "ATHENS,PATRAS,VOLOS", false, false),
        new Country("JA", 10, "TOKYO,OSAKA,NAGOYA,SAPPORO,KYOTO", false, false),
        new Country("VE", 8, "TORONTO,OTTAWA,CALGARY,MONTREAL", false, false),
        new Country("VK", 8, "SYDNEY,PERTH,HOBART,ADELAIDE", false, true),
        new Country("ZL", 4, "AUCKLAND,WELLINGTON,DUNEDIN", false, true),
        new Country("ZS", 6, "CAPE TOWN,DURBAN,PRETORIA", false, true),
        new Country("PY", 8, "SAO PAULO,CURITIBA,RECIFE", false, true),
        new Country("LU", 8, "BUENOS AIRES,CORDOBA,ROSARIO", false, true),
    };

    private static final String[] NAMES = ("HANS,JOHN,TOM,PAUL,MARK,PETER,MIKE,JAN,TOMEK,MAREK,PIOTR,JACEK,KARL,LARS,ERIK,PIERRE,JEAN,LUIGI,MARCO,CARLOS,PABLO,JIRI,PAAVO,SVEN,ANDY,DAVE,BOB,STEVE,KEN,HIRO,TAKA,BILL,JIM,JOE,RICK,GREG,NICK,ALEX,MARIA,EVA,HELGA,ANNA,KASIA,ULI,STEFAN,ADAM,LUCA,DIETER,FRED,GEORGE").split(",");

    private static final String[] QRP_RIGS = {"KX3", "KX2", "K1", "FT-817", "FT-818", "IC-703", "MCHF", "TRUSDX", "UBITX", "QCX MINI", "MTR-3B", "NORCAL 40A", "SOFTROCK", "HOMEBREW 2 TRANSISTOR RIG", "RS-918 KIT", "ATS-MINI"};
    private static final String[] BIG_RIGS = {"IC-7300", "FT-991", "TS-590", "FT-DX10", "K3", "IC-7610", "TS-480", "FT-450", "TS-120S", "IC-756", "HW-101"};
    private static final String[] ANTS = {"DIPOLE", "INVERTED V", "END FED HALF WAVE", "G5RV", "VERTICAL", "GROUND PLANE", "MAGNETIC LOOP", "DELTA LOOP", "LONG WIRE", "3 EL YAGI", "WINDOM", "RANDOM WIRE", "LINKED DIPOLE", "ZS6BKW", "EFHW IN A TREE", "FAN DIPOLE"};
    private static final String[] SKY = {"SUNNY", "CLOUDY", "RAIN", "FOG", "WINDY", "CLEAR SKY", "OVERCAST", "LIGHT RAIN", "STORM COMING", "NICE"};
    private static final String[] HOBBIES = {"GARDENING", "HIKING", "CYCLING", "FISHING", "SOTA AND POTA", "MODEL TRAINS", "ASTRONOMY", "HOME BREWING", "WOODWORKING", "COOKING", "BIRDWATCHING", "SAILING", "GLIDING", "CHESS", "PHOTOGRAPHY", "3D PRINTING", "CODING", "READING", "SKIING", "MUSHROOM PICKING", "BEEKEEPING", "KAYAKING", "BUILDING KITS", "OLD RADIOS", "BAKING BREAD"};
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
    private static final String[] GREET = {"GM", "GA", "GE", "GD"};


    /** A made-up callsign in the style of the given country. */
    private static String callsign(Dsp.Rng r, Country c) {
        StringBuilder b = new StringBuilder(c.prefix[r.nextInt(c.prefix.length)]);
        if (b.toString().equals("HB")) b.append('9');
        int digit = c.digits == 10 ? r.nextInt(10) : 1 + r.nextInt(Math.min(c.digits, 9));
        if (!b.toString().equals("HB9")) b.append(digit);
        int letters = r.chance(0.7) ? 3 : 2;
        for (int i = 0; i < letters; i++) b.append((char) ('A' + r.nextInt(26)));
        return b.toString();
    }

    private static int tempC(Dsp.Rng r, int month, boolean south) {
        double m = south ? (month + 6) % 12 : month;
        double base = 11 - 13 * Math.cos((m + 0.5) * 2 * Math.PI / 12);
        return (int) Math.round(base + r.gauss() * 4);
    }

    private static String rst(Dsp.Rng r) {
        String[] a = {"599", "589", "579", "569", "559", "449", "339", "559", "579", "599"};
        return a[r.nextInt(a.length)];
    }

    private static final class Op {
        String call, name, city, rig, ant, wx, hobby;
        int power; boolean qrp, us;
    }

    private static Op op(Dsp.Rng r, int month, Country c) {
        Op o = new Op();
        o.call = callsign(r, c);
        o.name = NAMES[r.nextInt(NAMES.length)];
        o.city = c.cities[r.nextInt(c.cities.length)];
        o.us = c.us;
        o.qrp = r.chance(0.55);
        if (o.qrp) { o.rig = QRP_RIGS[r.nextInt(QRP_RIGS.length)]; o.power = new int[]{1, 2, 3, 5, 5, 5, 10}[r.nextInt(7)]; }
        else { o.rig = BIG_RIGS[r.nextInt(BIG_RIGS.length)]; o.power = new int[]{20, 50, 100, 100, 100}[r.nextInt(5)]; }
        o.ant = ANTS[r.nextInt(ANTS.length)];
        int t = tempC(r, month, c.south);
        o.wx = SKY[r.nextInt(SKY.length)] + " ES " + (c.us ? Math.round(t * 1.8 + 32) + " F" : t + " C");
        o.hobby = HOBBIES[r.nextInt(HOBBIES.length)];
        o.qrp = o.power <= 10;
        return o;
    }

    private static String height(Dsp.Rng r, Op o) {
        int m = 5 + r.nextInt(20);
        return o.us ? "UP ABT " + Math.round(m * 3.3) + " FT" : "UP ABT " + m + " M";
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
     * @param headline a short headline to mention, or null
     */
    static Qso generate(Dsp.Rng r, int month, String headline) {
        double k = r.uniform();
        int kind = k < 0.55 ? RAGCHEW : k < 0.8 ? QUICK : CQ;
        Qso q = new Qso(kind);
        Country ca = COUNTRIES[r.nextInt(COUNTRIES.length)], cb = COUNTRIES[r.nextInt(COUNTRIES.length)];
        Op a = op(r, month, ca), b = op(r, month, cb);
        q.call[0] = a.call; q.call[1] = b.call; q.qrp[0] = a.qrp; q.qrp[1] = b.qrp;
        String A = a.call, B = b.call;
        if (kind == CQ) {
            int n = 3 + r.nextInt(5);
            String cq = r.chance(0.5) ? "CQ CQ CQ DE " + A + " " + A + " " + A + " PSE K" : "CQ CQ DE " + A + " " + A + " K";
            if (a.qrp && r.chance(0.5)) cq = "CQ QRP CQ QRP DE " + A + " " + A + " K";
            for (int i = 0; i < n; i++) q.tx.add(new Tx(0, cq));
            return q;
        }
        if (kind == QUICK) {
            String ref = a.us ? "K-" + (1000 + r.nextInt(8000)) : ca.prefix[0] + "-" + (100 + r.nextInt(900));
            String what = r.chance(0.6) ? "POTA" : "TEST";
            q.tx.add(new Tx(0, "CQ " + what + " CQ " + what + " DE " + A + " " + A + " K"));
            q.tx.add(new Tx(1, B + " " + B));
            if (what.equals("POTA")) {
                q.tx.add(new Tx(0, B + " TU UR " + rst(r) + " " + rst(r) + " <BT> " + ref + " " + ref + " <BT> BK"));
                q.tx.add(new Tx(1, A + " DE " + B + " R UR " + rst(r) + " " + rst(r) + " TU 73 <SK>"));
                q.tx.add(new Tx(0, "73 E E"));
            } else {
                int nr = 1 + r.nextInt(300);
                q.tx.add(new Tx(0, B + " 5NN " + nr));
                q.tx.add(new Tx(1, "R 5NN " + (1 + r.nextInt(300)) + " TU"));
                q.tx.add(new Tx(0, "TU E E"));
            }
            return q;
        }
        // Ragchew.
        int cqs = 2 + r.nextInt(2);
        StringBuilder cq = new StringBuilder();
        for (int i = 0; i < cqs; i++) cq.append("CQ ");
        q.tx.add(new Tx(0, cq + "DE " + A + " " + A + " " + A + " PSE K"));
        q.tx.add(new Tx(1, A + " DE " + B + " " + B + " K"));
        q.tx.add(new Tx(0, B + " DE " + A + " <BT> " + GREET[r.nextInt(GREET.length)] + " OM TNX FER CALL <BT> UR RST " + rst(r) + " " + rst(r)
            + " <BT> QTH " + a.city + " <BT> NAME " + a.name + " " + a.name + " <BT> HW? " + B + " DE " + A + " <KN>"));
        q.tx.add(new Tx(1, A + " DE " + B + " <BT> R R " + GREET[r.nextInt(GREET.length)] + " " + a.name + " TNX FER RPT <BT> UR RST " + rst(r) + " " + rst(r)
            + " <BT> QTH " + b.city + " <BT> NAME " + b.name + " " + b.name + " <BT> HW? " + A + " DE " + B + " <KN>"));
        q.tx.add(new Tx(0, B + " DE " + A + " <BT> R FB " + b.name + " TNX INFO <BT> RIG HR " + a.rig + " PWR " + a.power + " W <BT> ANT " + a.ant + " " + height(r, a)
            + " <BT> WX HR " + a.wx + " <BT> HW? " + B + " DE " + A + " <KN>"));
        q.tx.add(new Tx(1, A + " DE " + B + " <BT> R FB " + a.name + (a.qrp ? " ONLY " + a.power + " W WOW" : " NICE SIG") + " <BT> RIG HR " + b.rig + " PWR " + b.power
            + " W <BT> ANT " + b.ant + " " + height(r, b) + " <BT> WX HR " + b.wx + " <BT> HW? " + A + " DE " + B + " <KN>"));
        String[] hobbyLine = {"WHEN NOT ON AIR I ENJOY ", "OTHER HOBBY IS ", "HR ALSO LIKE ", "BESIDES RADIO I LOVE "};
        String t6 = B + " DE " + A + " <BT> FB " + b.name + " <BT> " + hobbyLine[r.nextInt(hobbyLine.length)] + a.hobby;
        boolean news = headline != null && r.chance(0.6);
        if (news) t6 += " <BT> BTW HRD ON NEWS TODAY " + headline + " <BT> HV U HRD IT ?";
        else if (r.chance(0.7)) t6 += " <BT> DID U KNOW " + FACTS[r.nextInt(FACTS.length)];
        q.tx.add(new Tx(0, t6 + " <BT> " + B + " DE " + A + " <KN>"));
        String t7 = A + " DE " + B + " <BT> R FB " + a.name + (r.chance(0.5) ? " " + a.hobby + " IS NICE" : "") + " <BT> I ENJOY " + b.hobby + " ES BUILDING ANTS";
        if (news) t7 += r.chance(0.5) ? " <BT> NO NOT YET WILL CHECK IT OUT TNX" : " <BT> YES SAW IT TOO WOW HI";
        else if (r.chance(0.5)) t7 += " <BT> FB FACT HI HI";
        q.tx.add(new Tx(1, t7 + " <BT> " + A + " DE " + B + " <KN>"));
        q.tx.add(new Tx(0, B + " DE " + A + " <BT> R R TNX FER NICE QSO " + b.name + " <BT> HPE CU AGN <BT> 73 ES GL <BT> " + B + " DE " + A + " <KN>"));
        q.tx.add(new Tx(1, A + " DE " + B + " <BT> TNX QSO " + a.name + " <BT> 73 ES GB <BT> " + A + " DE " + B + " <SK>"));
        q.tx.add(new Tx(0, "E E"));
        return q;
    }
}
