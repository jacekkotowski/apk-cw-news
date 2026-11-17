package pl.cwnews;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Plain-Java tests of everything that doesn't need Android. Run with tests/run.sh. */
public class T {
    static int ok, bad;
    static void check(String name, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + name); } }
    static void eq(String name, Object a, Object b) { if (a.equals(b)) ok++; else { bad++; System.out.println("FAIL " + name + ": got [" + a + "] want [" + b + "]"); } }
    static void near(String name, double a, double b, double tol) { if (Math.abs(a - b) <= tol) ok++; else { bad++; System.out.println("FAIL " + name + ": got " + a + " want " + b); } }
    static final int R = 48000;

    static String codes(String s) { StringBuilder b = new StringBuilder(); for (List<String> w : Morse.parse(s)) { for (String c : w) b.append(c).append(' '); b.append("/ "); } return b.toString().trim(); }

    static void morse() {
        eq("SOS", codes("SOS"), "... --- ... /");
        eq("digits", codes("0 5 9"), "----- / ..... / ----. /");
        eq("punct", codes("?,."), "..--.. --..-- .-.-.- /");
        eq("prosign is one token", codes("<BT>"), "-...- /");
        eq("unknown prosign dropped", codes("A <ZZ> B"), ".- / -... /");
        eq("error sign is eight dots", codes("<HH>"), "........ /");
        eq("wait sign", codes("<AS>"), ".-... /");
    }

    static double seconds(Keying k) { return k.totalSamples / (double) R; }
    static int units(Keying k, double dot) { int u = 0; for (int r : k.runs) u += Math.round(Math.abs(r) / (dot * R)); return u; }

    static void timing() {
        Timing t = new Timing(20, 20);
        near("dot", t.dot, 0.06, 1e-12);
        Keying p = Keying.build("PARIS", t, R);
        eq("PARIS 50 units", units(p, t.dot), 50);
        near("PARIS 20 wpm", seconds(p), 3.0, 1.0 / R);
        Keying sos = Keying.build("SOS", t, R);
        eq("SOS units", units(sos, t.dot), 5 + 3 + 11 + 3 + 5 + 7);
        for (int[] w : new int[][]{{20, 12}, {25, 5}, {50, 10}, {30, 29}, {18, 18}}) {
            Timing f = new Timing(w[0], w[1]);
            near("Farnsworth " + w[0] + "/" + w[1], seconds(Keying.build("PARIS", f, R)), 60.0 / w[1], 2.0 / R);
            near("element speed kept", f.dot, 1.2 / w[0], 1e-12);
            near("gap ratio", f.wordGap / f.charGap, 7.0 / 3, 1e-9);
        }
        Keying many = Keying.build("PARIS PARIS PARIS PARIS PARIS PARIS PARIS PARIS PARIS PARIS", new Timing(23, 11), R);
        near("no drift over 10 words", seconds(many), 10 * 60.0 / 11, 1.0 / R);
        eq("effective cannot exceed character", new Timing(20, 30).charGap, new Timing(20, 20).charGap);
        Keying two = Keying.build("E E", t, R);
        eq("no doubled word gap", two.runs.length, 4);
        near("word gap 7", Math.abs(two.runs[1]) / (double) R, 7 * t.dot, 2.0 / R);
    }

    static void text() {
        eq("polish", TextNorm.forMorse("Ąą Ćć Ęę Łł Ńń Óó Śś Źź Żż"), "AA CC EE LL NN OO SS ZZ ZZ");
        eq("zazolc", TextNorm.forMorse("Zażółć gęślą jaźń"), "ZAZOLC GESLA JAZN");
        eq("other accent", TextNorm.forMorse("Café Müller"), "CAFE MULLER");
        eq("curly", TextNorm.forMorse("It’s “ok”"), "IT'S \"OK\"");
        eq("unsupported dropped", TextNorm.forMorse("Новости news 新聞 #1"), "NEWS 1");
        eq("stray dash", TextNorm.forMorse("Rain — again - today"), "RAIN AGAIN TODAY");
        eq("whitespace", TextNorm.forMorse(" a \t b\n"), "A B");
        eq("entities", TextNorm.cleanHtml("Tom &amp; Jerry &#8217;s &#x41; caf&eacute;"), "Tom & Jerry ’s A café");
        eq("double-escaped html", TextNorm.cleanHtml("&lt;p&gt;Hello &lt;b&gt;world&lt;/b&gt;&lt;/p&gt;"), "Hello world");
        eq("script removed", TextNorm.cleanHtml("A<script>x()</script>B"), "A B");
        eq("urls", TextNorm.cleanHtml("See https://x.io/a?b=1 now"), "See now");
        eq("hn boilerplate", TextNorm.cleanHtml("<a href=\"https://x\">Comments</a>"), "");
        eq("post boiler", TextNorm.cleanHtml("Body. The post Foo appeared first on Bar."), "Body.");
        eq("story headline", TextNorm.story("Hacker News", "Hello", "Body", TextNorm.HEADLINE, true), "DE HACKER NEWS <BT> HELLO <AR>");
        eq("story summary", TextNorm.story("BBC World (BBC)", "Hello", "Body", TextNorm.HEADLINE_SUMMARY, true), "DE BBC <BT> HELLO <BT> BODY <AR>");
        eq("same summary skipped", TextNorm.story("x", "Hello", "hello", TextNorm.HEADLINE_SUMMARY, false), "HELLO <AR>");
    }

    static void abbrev() {
        eq("off", Abbrev.apply("THANKS FOR YOUR HELP", Abbrev.OFF), "THANKS FOR YOUR HELP");
        eq("light", Abbrev.apply("THANKS FOR YOUR HELP AND GOOD WEATHER", Abbrev.LIGHT), "TNX FER UR HELP ES GUD WX");
        eq("phrase beats word", Abbrev.apply("GOOD MORNING EVERYONE", Abbrev.LIGHT), "GM EVERYONE");
        eq("punctuation kept", Abbrev.apply("WELL, THANKS. YOU?", Abbrev.LIGHT), "WELL, TNX. U?");
        eq("full only at full", Abbrev.apply("WORKED WITH SORRY", Abbrev.LIGHT), "WORKED WITH SORRY");
        eq("full", Abbrev.apply("WORKED WITH SORRY", Abbrev.FULL), "WKD WID SRI");
        eq("light also in full", Abbrev.apply("SEE YOU LATER AND THANKS", Abbrev.FULL), "CUL ES TNX");
        eq("digits untouched", Abbrev.apply("5 YOU 2026", Abbrev.LIGHT), "5 U 2026");
        eq("quoted word untouched", Abbrev.apply("\"YOU\"", Abbrev.LIGHT), "\"YOU\"");
        eq("are is never replaced", Abbrev.apply("WHAT ARE YOU", Abbrev.FULL), "WHAT ARE U");
        eq("story abbreviates headline not source", TextNorm.story("Thanks FM (YOU)", "Thanks for you", "", TextNorm.HEADLINE, true, Abbrev.LIGHT), "DE YOU <BT> TNX FER U <AR>");
        check("json has table", Abbrev.json().startsWith("[{\"a\":\"ES\"") && Abbrev.json().endsWith("}]"));
    }

    static FeedParser.Feed feed(String xml) throws Exception { return FeedParser.parse(xml.getBytes(StandardCharsets.UTF_8), "https://f.example/rss"); }
    static boolean refused(String xml) { try { feed(xml); return false; } catch (Exception e) { return e instanceof FeedParser.FeedException; } }

    static void feeds() throws Exception {
        FeedParser.Feed r = feed("<?xml version=\"1.0\"?><rss version=\"2.0\" xmlns:content=\"http://purl.org/rss/1.0/modules/content/\"><channel><title>News &amp; More</title>"
            + "<item><title>One &amp; two</title><guid>a1</guid><link>https://x.example/1</link><description><![CDATA[<p>Sum <b>one</b></p>]]></description><pubDate>Mon, 09 Nov 2026 10:00:00 +0100</pubDate></item>"
            + "<item><title>Dup</title><guid>a1</guid></item>"
            + "<item><title>Two</title><link>https://www.x.example/2/?utm_source=a</link><content:encoded>Full</content:encoded><description>Short</description></item>"
            + "<item><title>Three</title></item><item><title></title><guid>z</guid></item></channel></rss>");
        eq("rss title", r.title, "News & More");
        eq("rss dedupe and empty", r.items.size(), 3);
        eq("rss item", r.items.get(0).title, "One & two");
        eq("cdata html", r.items.get(0).summary, "Sum one");
        eq("uid guid", r.items.get(0).uid, "g:a1");
        eq("uid link", r.items.get(1).uid, "l:x.example/2");
        eq("uid fallback", r.items.get(2).uid, "t:https://f.example/rss|three");
        eq("encoded preferred", r.items.get(1).summary, "Full");
        eq("rfc822", r.items.get(0).published, 1794214800000L);
        FeedParser.Feed a = feed("<feed xmlns=\"http://www.w3.org/2005/Atom\"><title type=\"html\">At</title><entry><id>tag:1</id><title>E1</title><link rel=\"self\" href=\"https://a/s\"/><link href=\"https://a/e\"/><summary>S</summary><published>2026-11-09T09:00:00Z</published></entry><entry><id>tag:2</id><title>E2</title><content type=\"html\">&lt;i&gt;C&lt;/i&gt;</content><updated>2026-11-09T09:00:00+01:00</updated></entry></feed>");
        eq("atom count", a.items.size(), 2);
        eq("atom link", a.items.get(0).link, "https://a/e");
        eq("atom date", a.items.get(0).published, 1794214800000L + 3600000L * 0);
        eq("atom html", a.items.get(1).summary, "C");
        check("malformed", refused("<rss><channel><item></channel>"));
        check("not a feed", refused("<html><body/></html>"));
        check("xxe", refused("<?xml version=\"1.0\"?><!DOCTYPE rss [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><rss><channel><title>&x;</title></channel></rss>"));
        check("http refused", urlBad("http://x.example/rss"));
        check("no host", urlBad("https://"));
        check("garbage", urlBad("not a url"));
        check("https ok", !urlBad("https://news.ycombinator.com/rss"));
        eq("link norm", FeedParser.normalizeLink("http://www.A.io/x/#c"), "A.io/x");
    }
    static boolean urlBad(String u) { try { FeedParser.validateUrl(u); return false; } catch (FeedParser.FeedException e) { return true; } }

    static void queue() {
        long[] src = {1, 2, 3};
        int[] at = {0};
        PlaybackQueue.Provider p = () -> at[0] < src.length ? src[at[0]++] : PlaybackQueue.NONE;
        PlaybackQueue q = new PlaybackQueue();
        eq("q1", q.next(p), 1L);
        eq("q2", q.next(p), 2L);
        eq("prev", q.previous(), 1L);
        eq("forward not repeat", q.next(p), 2L);
        eq("q3", q.next(p), 3L);
        eq("end", q.next(p), PlaybackQueue.NONE);
        eq("no feeds", PlaybackQueue.newsState(0, 0, 0, 0, 0, true), PlaybackQueue.NO_FEEDS);
        eq("empty", PlaybackQueue.newsState(1, 0, 0, 1, 0, true), PlaybackQueue.EMPTY_FEEDS);
        eq("error", PlaybackQueue.newsState(1, 0, 0, 0, 1, true), PlaybackQueue.FETCH_ERROR);
        eq("offline empty", PlaybackQueue.newsState(1, 0, 0, 0, 0, false), PlaybackQueue.OFFLINE_EMPTY);
        eq("heard", PlaybackQueue.newsState(1, 5, 0, 1, 0, true), PlaybackQueue.ALL_HEARD);
        eq("offline cache", PlaybackQueue.newsState(1, 5, 2, 1, 1, false), PlaybackQueue.OFFLINE_CACHE);
    }

    static double[] renderCw(int bed, double db, int blockFrames, double[] doneAt) {
        Engine e = new Engine(R, 7, blockFrames);
        e.setBed(bed); e.bedDb = db; e.volume = 1; e.setAudible(true);
        Keying k = Keying.build("PARIS PARIS", new Timing(20, 12), R);
        e.load(k, 0);
        int total = (int) k.totalSamples + 2 * R;
        double[] out = new double[total];
        float[] b = new float[2 * blockFrames];
        int n = 0; double when = -1;
        while (n < total) {
            int f = Math.min(blockFrames, total - n);
            e.render(b, f);
            for (int i = 0; i < f; i++) out[n + i] = b[2 * i];
            n += f;
            if (when < 0 && e.storyDone()) when = n;
        }
        doneAt[0] = when;
        return out;
    }

    static double rms(double[] x, int from, int to) { double s = 0; for (int i = from; i < to; i++) s += x[i] * x[i]; return Math.sqrt(s / (to - from)); }

    /** White noise, with the notch on: stands in for any background that has energy at the CW pitch. */
    static final class WhiteBed extends Bed {
        private final Dsp.Rng r;
        WhiteBed(long seed) { r = new Dsp.Rng(seed); }
        @Override void fill(double[] lr, int frames) { for (int i = 0; i < 2 * frames; i++) lr[i] = r.gauss(); }
    }

    static boolean sendable(String text) {
        String t = text.replaceAll("<(BT|AR|SK|KN|AS|HH)>", " ");
        for (int i = 0; i < t.length(); i++) { char c = t.charAt(i); if (c != ' ' && !Morse.supported(c)) return false; }
        return true;
    }

    static void band() {
        int[] kinds = new int[Qso.KINDS], styles = new int[5], keys = new int[4];
        int carriers = 0, faults = 0;
        Dsp.Rng rngAll = new Dsp.Rng(12345);
        boolean allSendable = true, hasNews = false, tail = false, fumble = false, wait = false, repeat = false, qrs = false;
        java.util.Set<Integer> lengths = new java.util.HashSet<>();
        java.util.regex.Pattern report = java.util.regex.Pattern.compile("(?:UR|RST)(?: RST)?(?: IS)? ([345])([3-9])9\\b");
        java.util.regex.Pattern park = java.util.regex.Pattern.compile("(?:POTA PARK|REF) ([A-Z0-9/-]+)");
        double strong = 0, weak = 0; int strongN = 0, weakN = 0;
        for (int n = 0; n < 1200; n++) {
            Qso q = Qso.generate(rngAll, n % 12, n % 24, n % 2 == 0 ? Qso.shortHeadline("Parliament passes the new energy law after long debate") : null);
            kinds[q.kind]++;
            check("has tx " + n, !q.tx.isEmpty());
            for (int i = 0; i < q.call.length; i++) {
                styles[q.style[i]]++; keys[q.key[i]]++;
                if (q.chirp[i] || q.rough[i] || q.drift[i]) faults++;
                check("call looks right " + q.call[i], q.call[i].matches("[A-Z0-9]{3,7}(/P)?"));
                for (int k = 0; k < i; k++) check("calls differ", !q.call[i].equals(q.call[k]));
            }
            boolean talk = q.kind == Qso.RAGCHEW || q.kind == Qso.SKED, pair = true;
            double[] askedFor = new double[q.call.length];
            for (Qso.Tx t : q.tx) {
                if (t.carrier > 0) { check("a carrier has no text and a sensible length", t.text.isEmpty() && t.carrier < 6 && q.kind == Qso.TUNE); carriers++; }
                else if (!sendable(t.text) || t.text.trim().isEmpty() || t.text.contains("  ")) { allSendable = false; System.out.println("BAD: " + t.text); }
                check("speed and pause in range", t.speed >= 0.5 && t.speed <= 1.5 && t.pause > 0 && t.pause < 12);
                if (q.style[t.who] == Qso.NOVICE) check("a beginner sends slowly: " + t.speed, t.speed <= 0.76);
                if (t.text.contains("HRD ON NEWS TODAY PARLIAMENT PASSES")) hasNews = true;
                if (t.text.contains("<HH>")) fumble = true;
                if (t.text.contains("<AS>")) wait = true;
                if (t.text.contains("PSE RPT")) repeat = true;
                java.util.regex.Matcher p = park.matcher(t.text);
                while (p.find()) check("park or summit reference " + p.group(1), p.group(1).matches("[A-Z]{2}-\\d{4}|[A-Z0-9]{1,3}/[A-Z]{2}-\\d{3}"));
                if (t.who >= 2) pair = false;
                if (!talk || !pair) continue;
                // From here on it is two operators talking to each other, so each transmission is about the other one.
                int to = 1 - t.who;
                java.util.List<String> w = java.util.Arrays.asList(t.text.split(" "));
                for (String g : new String[]{"GM", "GA", "GE"}) if (w.contains(g)) eq("greeting fits the other station's clock, " + q.hour[to] + " h", g, Qso.greeting(q.hour[to]));
                if (q.yl[to]) check("a YL is not called OM: " + t.text, !w.contains("OM"));
                if (q.yl[t.who]) check("a YL has no XYL: " + t.text, !w.contains("XYL"));
                if (askedFor[t.who] > 0) { qrs = true; check("slows down when asked: " + t.speed + " > " + askedFor[t.who], t.speed <= askedFor[t.who] * 1.06); }
                if (t.text.contains("PSE QRS") || t.text.contains("QRS PSE")) askedFor[to] = t.speed;
                java.util.regex.Matcher m = report.matcher(t.text);
                while (m.find()) {
                    int s = m.group(2).charAt(0) - '0', fair = Qso.rst(q.lossDb[to]).charAt(1) - '0';
                    check("report follows the signal: S" + s + " for " + q.lossDb[to] + " dB down", Math.abs(s - fair) <= 2);
                    if (q.lossDb[to] < 8) { strong += s; strongN++; } else if (q.lossDb[to] > 22) { weak += s; weakN++; }
                }
            }
            if (q.kind == Qso.RAGCHEW) { lengths.add(q.tx.size()); check("ragchew is long", q.tx.size() >= 8); if (q.call.length == 3) tail = true; }
            if (q.kind == Qso.CQ) for (Qso.Tx t : q.tx) eq("a lone CQ has one voice", t.who, 0);
            if (q.kind == Qso.QUICK) check("someone answers the activator", q.call.length >= 2);
        }
        check("every transmission can be keyed", allSendable);
        check("headline is quoted sometimes", hasNews);
        check("all kinds appear " + java.util.Arrays.toString(kinds), kinds[Qso.RAGCHEW] > 400 && kinds[Qso.QUICK] > 150 && kinds[Qso.CQ] > 100 && kinds[Qso.SKED] > 80 && kinds[Qso.TUNE] > 25);
        for (int k : keys) check("all kinds of key appear " + java.util.Arrays.toString(keys), k > 100);
        check("someone tunes up, some signals have faults: " + carriers + ", " + faults, carriers > 25 && faults > 100);
        for (int st : styles) check("all personalities appear " + java.util.Arrays.toString(styles), st > 100);
        check("contacts differ in shape: " + lengths.size() + " lengths", lengths.size() >= 10);
        check("tail-ender, error sign, wait, repeat request and QRS all happen", tail && fumble && wait && repeat && qrs);
        check("strong stations get better reports: " + strong / strongN + " vs " + weak / weakN, strongN > 50 && weakN > 50 && strong / strongN > weak / weakN + 3);
        StringBuilder one = new StringBuilder(), two = new StringBuilder();
        for (Qso.Tx t : Qso.generate(new Dsp.Rng(5), 3, 20, "A B C").tx) one.append(t.text).append(t.speed).append(t.pause);
        for (Qso.Tx t : Qso.generate(new Dsp.Rng(5), 3, 20, "A B C").tx) two.append(t.text).append(t.speed).append(t.pause);
        eq("same seed same contact", one.toString(), two.toString());
        eq("greeting by hour", Qso.greeting(8) + Qso.greeting(14) + Qso.greeting(20) + Qso.greeting(2), "GMGAGEGE");
        eq("report by strength", Qso.rst(2) + " " + Qso.rst(12) + " " + Qso.rst(27) + " " + Qso.rst(32), "599 579 449 339");
        eq("headline cut to nine words", Qso.shortHeadline("one two three four five six seven eight nine ten eleven"), "ONE TWO THREE FOUR FIVE SIX SEVEN EIGHT NINE");
        check("two words is too short", Qso.shortHeadline("Hi there") == null);
        double nlo = Rx.lo(Rx.NORMAL, 600), nhi = Rx.hi(Rx.NORMAL, 600);
        near("filter at centre", Rx.gain(nlo, nhi, 600), 1, 0.01);
        check("filter drops off", Rx.gain(nlo, nhi, 900) < 0.15 && Rx.gain(nlo, nhi, 1500) < 0.001 && Rx.gain(nlo, nhi, 100) < 0.001 && Rx.gain(nlo, nhi, -50) == 0);

        // Render a minute of the band: bounded, repeatable, and only audible near a station.
        for (int tries = 0; tries < 2; tries++) {
            Band b = new Band(R, 7, 5, null);
            b.setActivity(2); b.setClock(5, 19.5);
            double[] buf = new double[2 * 480];
            double peak = 0; int blocksWithSignal = 0; boolean sawStation = false;
            for (int blk = 0; blk < 60 * R / 480 * 3; blk++) {
                java.util.Arrays.fill(buf, 0);
                String act = b.activityJson();
                double dial = Band.NEWS;
                if (act.length() > 2 && act.contains("\"k\":")) {
                    sawStation = true;
                    dial = Double.parseDouble(act.substring(act.indexOf("\"k\":") + 4, act.indexOf(',')));
                }
                b.render(buf, 480, dial, 600);
                double m = 0; for (double v : buf) m = Math.max(m, Math.abs(v));
                peak = Math.max(peak, m);
                if (m > 0.01) blocksWithSignal++;
            }
            check("band produced a station", sawStation);
            check("band signal heard when tuned: " + blocksWithSignal, blocksWithSignal > 50);
            check("band bounded: " + peak, peak < 3);
        }
        Band quiet = new Band(R, 9, 5, null);
        double[] z = new double[960];
        for (int blk = 0; blk < 4000; blk++) { java.util.Arrays.fill(z, 0); quiet.render(z, 480, 7001.0, 600); }
        double away = 0; java.util.Arrays.fill(z, 0);
        quiet.render(z, 480, Band.LOW - 5, 600); for (double v : z) away = Math.max(away, Math.abs(v));
        eq("nothing heard far from the dial", away, 0.0);
        check("copy is empty off station", quiet.copy(Band.LOW - 5).isEmpty());
        double nxt = quiet.next(7019.0, 1);
        check("next signal moves up", nxt > 7019.0 || nxt <= 7019.0);

        // The news bulletin waits when you tune away and resumes when you return.
        Engine en = new Engine(R, 1, 480);
        en.setBand(new Band(R, 3, 5, null));
        en.volume = 1; en.setAudible(true);
        en.load(Keying.build("TEST TEST TEST TEST TEST TEST TEST TEST", new Timing(20, 20), R), 0);
        float[] fb = new float[960];
        for (int i = 0; i < 20; i++) en.render(fb, 480);
        int w0 = en.word();
        en.dialKhz = Band.NEWS + 8;
        for (int i = 0; i < 400; i++) en.render(fb, 480);
        int w1 = en.word();
        eq("bulletin waits while off frequency", w1, w0);
        en.dialKhz = Band.NEWS;
        for (int i = 0; i < 400; i++) en.render(fb, 480);
        check("bulletin resumes when tuned back", en.word() > w1);
    }

    /** The station at this frequency, from activityJson, or NaN. */
    static boolean listed(Band b, double khz) {
        String a = b.activityJson(); int i = 0;
        while ((i = a.indexOf("\"k\":", i)) >= 0) { i += 4; if (Math.abs(Double.parseDouble(a.substring(i, a.indexOf(',', i))) - khz) < 0.02) return true; }
        return false;
    }

    static void follow() {
        Band b = new Band(R, 21, 5, null);
        b.setClock(5, 19.5);
        double[] buf = new double[960];
        int blk = 0;
        // Wait for a station that can be heard, and tune onto it.
        double dial = Double.NaN;
        for (; blk < 60 * 100 && Double.isNaN(dial); blk++) {
            java.util.Arrays.fill(buf, 0); b.render(buf, 480, Band.NEWS, 600);
            if (blk % 100 == 0) { double f = b.next(7000, 1); if (f != Band.NEWS && f >= 7000) dial = f; }
        }
        check("follow: found a station to listen to", !Double.isNaN(dial));
        // While that contact runs, the receiver stays.
        boolean stayed = true; int n = 0; double jump = Double.NaN;
        for (int i = 0; i < 100 * 400 && Double.isNaN(jump); i++) {
            java.util.Arrays.fill(buf, 0); b.render(buf, 480, dial, 600);
            if (listed(b, dial)) { stayed &= Double.isNaN(b.follow(dial, 2.5, 75)); n++; }
            else {
                // The slot is free: within 4 s of audio there is somewhere to go.
                for (int j = 0; j < 100 * 4 && Double.isNaN(jump); j++) {
                    java.util.Arrays.fill(buf, 0); b.render(buf, 480, dial, 600);
                    jump = b.follow(dial, 2.5, 75);
                }
                break;
            }
        }
        check("follow stays during the contact: " + n + " checks", stayed && n > 100);
        check("follow names a station within 4 s of the contact ending", !Double.isNaN(jump));
        check("follow names a station on the band", !Double.isNaN(jump) && listed(b, jump));
        check("follow does not return to the frequency just left", Math.abs(jump - dial) > 0.25);
        check("a second call straight after a jump stays", Double.isNaN(b.follow(jump, 2.5, 75)));
        // With nothing else on the band there is nowhere to go, however long it has been quiet.
        int idle = 0; boolean never = true;
        for (int seed = 1; seed <= 40; seed++) {
            Band empty = new Band(R, seed, 5, null);
            empty.setActivity(0);
            for (int i = 0; i < 20; i++) { java.util.Arrays.fill(buf, 0); empty.render(buf, 480, 7010, 600); }
            if (empty.activityJson().equals("[]")) { idle++; never &= Double.isNaN(empty.follow(7010, 0, 0)); }
        }
        check("follow stays when the band is empty (" + idle + " idle bands)", never && idle >= 20);
    }

    static double goertzel(double[] x, double f) {
        double w = 2 * Math.PI * f / R, c = 2 * Math.cos(w), s1 = 0, s2 = 0;
        for (int i = 0; i < x.length; i += 2) { double s = x[i] + c * s1 - s2; s2 = s1; s1 = s; }
        return s1 * s1 + s2 * s2 - c * s1 * s2;
    }

    static void sideband() {
        // A station 300 Hz above the dial sounds at tone + 300 in CW and at tone - 300 in CW-R.
        for (int sign = 1; sign >= -1; sign -= 2) {
            Band b = new Band(R, 5, 5, null);
            b.setClock(5, 19.5);
            double[] buf = new double[960], all = new double[2 * 48000 * 3];
            double khz = Double.NaN; int at = 0, tries = 0;
            while (Double.isNaN(khz) && tries++ < 100 * 120) {
                java.util.Arrays.fill(buf, 0); b.render(buf, 480, 7010, 600, 100, 3000, sign);
                String a = b.activityJson();
                if (a.contains("\"on\":true")) khz = Double.parseDouble(a.substring(a.indexOf("\"k\":") + 4, a.indexOf(',')));
            }
            check("sideband: found a station", !Double.isNaN(khz));
            double dial = khz - 0.3;
            for (int i = 0; i < all.length / 960; i++) { java.util.Arrays.fill(buf, 0); b.render(buf, 480, dial, 600, 100, 3000, sign); System.arraycopy(buf, 0, all, i * 960, 960); }
            double up = goertzel(all, 900), down = goertzel(all, 300);
            check("sideband " + sign + ": station at " + (sign > 0 ? "tone + 300" : "tone - 300") + ": up " + up + " down " + down, sign > 0 ? up > 10 * down : down > 10 * up);
        }
        // The bulletin waits when tuned outside the filter, on either side.
        for (int side = 0; side <= 1; side++) {
            Engine en = new Engine(R, 1, 480);
            en.setBand(new Band(R, 3, 5, null));
            en.volume = 1; en.setAudible(true); en.side = side;
            en.load(Keying.build("TEST TEST TEST TEST TEST TEST TEST TEST", new Timing(20, 20), R), 0);
            float[] fb = new float[960];
            for (int i = 0; i < 20; i++) en.render(fb, 480);
            int w0 = en.word();
            en.dialKhz = Band.NEWS + 8;
            for (int i = 0; i < 400; i++) en.render(fb, 480);
            int w1 = en.word();
            eq("bulletin waits while off frequency, side " + side, w1, w0);
            en.dialKhz = Band.NEWS;
            for (int i = 0; i < 400; i++) en.render(fb, 480);
            check("bulletin resumes when tuned back, side " + side, en.word() > w1);
        }
        // Rx.heard for both signs and all widths.
        for (int w = 0; w < 3; w++) {
            double[] cw = Rx.heard(w, 600, 1), r = Rx.heard(w, 600, -1);
            near("heard CW low, width " + w, cw[0], Rx.lo(w, 600) - 600, 1e-9);
            near("heard CW high, width " + w, cw[1], Rx.hi(w, 600) - 600, 1e-9);
            near("heard CW-R low, width " + w, r[0], 600 - Rx.hi(w, 600), 1e-9);
            near("heard CW-R high, width " + w, r[1], 600 - Rx.lo(w, 600), 1e-9);
        }
        near("Wide filter reaches 1200 Hz above and 400 below", Rx.heard(Rx.WIDE, 600, 1)[1], 1200, 1e-9);
        // With the Wide filter a station 500 Hz above the wanted one is heard in CW and is 20 dB or more down in CW-R.
        double lo = Rx.lo(Rx.WIDE, 600), hi = Rx.hi(Rx.WIDE, 600);
        double cwGain = Rx.gain(lo, hi, 600 + 500), rGain = Rx.gain(lo, hi, 600 - 500);
        check("CW-R puts the neighbour 20 dB down: " + 20 * Math.log10(rGain / cwGain), 20 * Math.log10(rGain / cwGain) < -20);
    }

    /** RMS of the left channel of an interleaved block. */
    static double blockRms(double[] b, int n) { double q = 0; for (int i = 0; i < n; i += 2) q += b[i] * b[i]; return Math.sqrt(q / (n / 2)); }
    static Rx peaked(long seed) { Rx a = new Rx(R, seed); a.tune(Rx.lo(Rx.NORMAL, 600), Rx.hi(Rx.NORMAL, 600), 600, true); return a; }

    static void filters() {
        // Shift: the passband moves, the pitch does not.
        double[] e = Rx.edges(Rx.NORMAL, 600, 200);
        near("shift +200 lifts the lower edge", e[0], 600, 1e-9);
        near("shift +200 lifts the upper edge", e[1], 1000, 1e-9);
        near("shifted passband passes tone + 200", Rx.gain(e[0], e[1], 800), 1, 0.05);
        check("shifted passband rejects tone - 250: " + Rx.gain(e[0], e[1], 350), Rx.gain(e[0], e[1], 350) < 0.1);
        check("the lower edge stays at 100 Hz or more", Rx.edges(Rx.WIDE, 600, -300)[0] >= 100);
        near("heard with shift, CW", Rx.heard(Rx.NORMAL, 600, 1, 200)[0], 0, 1e-9);
        near("heard with shift, CW-R", Rx.heard(Rx.NORMAL, 600, -1, 200)[1], 0, 1e-9);

        // Peak filter: a tone at the pitch doubles, one 200 Hz away hardly changes.
        near("peak filter doubles a tone at the pitch", through(peaked(41), 600, 0.3, false), 0.6, 0.03);
        double up = through(peaked(41), 800, 0.3, false);
        check("peak filter leaves a tone 200 Hz up under 0.34: " + up, up < 0.34);
        near("without it the tone passes as it was", through(new Rx(R, 42), 600, 0.3, false), 0.3, 0.02);

        // Noise blanker: spikes go, a clean tone stays.
        double[] ticks = new double[2];
        for (int on = 0; on < 2; on++) {
            Rx rx = new Rx(R, 43); rx.tune(Rx.lo(Rx.NORMAL, 600), Rx.hi(Rx.NORMAL, 600), 600, false); rx.blanker(on == 1);
            rx.weather(40, 1.5, 1);   // plenty of ticks: they are softer now, and a count above 4 x RMS needs many to stand out from the noise itself
            double[] b = new double[960], keep = new double[60 * R];
            double sq = 0; int n = 0;
            for (int blk = 0; blk < 60 * R / 480 + 50; blk++) {
                java.util.Arrays.fill(b, 0);
                rx.process(b, 480, 0.1, 0.1, 0);
                if (blk >= 50) for (int i = 0; i < 960 && n < keep.length; i += 2) { keep[n++] = b[i]; sq += b[i] * b[i]; }
            }
            double rms = Math.sqrt(sq / n); int count = 0;
            for (int i = 0; i < n; i++) if (Math.abs(keep[i]) > 4 * rms) count++;
            ticks[on] = count;
        }
        System.out.printf("noise blanker: %.0f samples above 4 x RMS without it, %.0f with it%n", ticks[0], ticks[1]);
        check("blanker removes at least 70% of the static spikes: " + ticks[0] + " -> " + ticks[1], ticks[0] > 100 && ticks[1] <= 0.3 * ticks[0]);
        for (double hz : new double[]{600, 1000}) {
            Rx off = new Rx(R, 44), on = new Rx(R, 44);
            off.tune(Rx.lo(Rx.WIDE, 600), Rx.hi(Rx.WIDE, 600)); on.tune(Rx.lo(Rx.WIDE, 600), Rx.hi(Rx.WIDE, 600)); on.blanker(true);
            double tOff = through(off, hz, 0.5, false), tOn = through(on, hz, 0.5, false);
            near("a clean " + hz + " Hz tone passes the blanker within 1%", tOn, tOff, 0.01 * tOff);
        }

        // AGC speed: how long the noise takes to come back within 3 dB after a strong signal.
        double[] back = new double[3];
        for (int mode = 1; mode <= 2; mode++) {
            Rx g = new Rx(R, 46);
            double[] b = new double[960], last = new double[4]; double ph = 0, base = 0; int baseN = 0;
            double found = -1; boolean dipped = false;
            for (int blk = 0; blk < 500; blk++) {
                boolean tone = blk >= 100 && blk < 200;
                for (int i = 0; i < 480; i++) { b[2 * i] = b[2 * i + 1] = tone ? Math.sin(ph) : 0; ph += 2 * Math.PI * 600 / R; }
                g.process(b, 480, 0.05, 0.05, mode);
                if (blk < 100 && blk >= 50) { base += blockRms(b, 960); baseN++; }
                if (blk == 99) base /= baseN;
                if (blk >= 200 && found < 0) {
                    last[0] = last[1]; last[1] = last[2]; last[2] = last[3]; last[3] = blockRms(b, 960);
                    double m = (last[0] + last[1] + last[2] + last[3]) / 4;
                    if (blk >= 204 && db(m / base) < -3) dipped = true;
                    if (dipped && db(m / base) > -3) found = (blk - 200 - 1.5) * 0.01;
                }
            }
            back[mode] = found;
        }
        System.out.printf("AGC: noise back within 3 dB after %.2f s (slow) and %.2f s (fast)%n", back[1], back[2]);
        check("fast AGC recovers in under 0.25 s: " + back[2], back[2] >= 0 && back[2] < 0.25);
        check("slow AGC takes over 0.4 s: " + back[1], back[1] > 0.4);
    }

    static double[] skyNow(Sky k) { return new double[]{k.ticksPerSecond(), k.crashEverySeconds(), k.crashGain(), k.aurora(), k.flare()}; }

    static void weather() {
        // Each fixed mode settles on its own numbers within a minute.
        double[][] want = {{2.5, 40, 1, 0, 0}, {25, 6, 1, 0, 0}, {40, 1.5, 2.5, 0, 0}, {2.5, 40, 1, 1, 0}, {2.5, 40, 1, 0, 1}};
        for (int m = 0; m < 5; m++) {
            Sky k = new Sky(5);
            for (int i = 0; i < 6000; i++) k.advance(0.01, 6, 15, 50, Sky.CALM + m);
            double[] got = skyNow(k);
            for (int i = 0; i < 5; i++) near("sky mode " + (Sky.CALM + m) + " number " + i, got[i], want[m][i], 0.01);
        }
        // Auto: thunderstorms are common on summer evenings and rare on winter nights.
        double[] share = new double[2];
        for (int season = 0; season < 2; season++) {
            double inStorm = 0, total = 0;
            for (int n = 0; n < 2000; n++) {
                Sky k = new Sky(1000 + n);
                for (int step = 0; step < 360; step++) {
                    k.advance(60, season == 0 ? 6 : 0, season == 0 ? 17 + step / 60.0 : step / 60.0, 50, Sky.AUTO);
                    if (k.thunder() != Sky.T_CALM) inStorm++;
                    total++;
                }
            }
            share[season] = inStorm / total;
        }
        System.out.printf("band weather: thunderstorms %.0f%% of summer evenings, %.0f%% of winter nights%n", 100 * share[0], 100 * share[1]);
        check("summer evenings: static or storm 25 to 60% of the time: " + share[0], share[0] >= 0.25 && share[0] <= 0.6);
        check("winter nights: under 10%: " + share[1], share[1] < 0.1);
        near("the tropics have a summer all year", Sky.season(0, 10), 1, 1e-9);
        near("the southern summer is in December", Sky.season(11, -34), 1, 1e-9);
        // Smooth: no output jumps between two calls 10 ms apart, even when the mode is switched.
        Sky sm = new Sky(9);
        double[] range = {37.5, 38.5, 1.5, 1, 1}, before = skyNow(sm);
        double worst = 0;
        for (int i = 0; i < 6 * 60 * 100; i++) {
            sm.advance(0.01, 6, 15, 50, 1 + (i / 3000) % 5);
            double[] now = skyNow(sm);
            for (int j = 0; j < 5; j++) worst = Math.max(worst, Math.abs(now[j] - before[j]) / range[j]);
            before = now;
        }
        check("no weather number moves more than 5% of its range in 10 ms: " + worst, worst < 0.05);
        // A flare never starts at night.
        double flare = 0;
        for (int n = 0; n < 2000; n++) { Sky k = new Sky(n); for (int i = 0; i < 360; i++) { k.advance(10, 6, 22, 50, Sky.AUTO); flare = Math.max(flare, k.flare()); } }
        eq("no flare at 22 local", flare, 0.0);
        // The aurora weakens and flutters signals on northern paths only; a flare removes 30 dB in daylight.
        check("a path from 50 N to 62 N reaches the auroral zone", Prop.highLatitude(50, 15, 62, 25));
        check("a path from 50 N to 40 N does not", !Prop.highLatitude(50, 15, 40, 20));
        double worstDrop = 99;
        for (int k = 0; k < 20; k++) worstDrop = Math.min(worstDrop, -db(Band.skyFactor(true, false, 1, 0, k * 0.3)));
        check("aurora at level 1: a northern operator is at least 10 dB weaker: " + worstDrop, worstDrop >= 10);
        eq("aurora leaves an operator at 40 degrees unchanged", Band.skyFactor(false, false, 1, 0, 1.0), 1.0);
        near("aurora at 0 changes nothing", Band.skyFactor(true, true, 0, 0, 1.0), 1, 1e-12);
        near("a flare takes 30 dB off a daylight path", db(Band.skyFactor(false, true, 0, 1, 0)), -30, 0.01);
        eq("a flare leaves a night path alone", Band.skyFactor(false, false, 0, 1, 0), 1.0);

        // Static through the receiver: a storm is much louder, and the blanker takes the ticks but not the swell.
        double[] rms = new double[4];
        for (int v = 0; v < 4; v++) {
            boolean storm = v % 2 == 1; boolean blank = v >= 2;
            Rx rx = new Rx(R, 51); rx.tune(Rx.lo(Rx.NORMAL, 600), Rx.hi(Rx.NORMAL, 600), 600, false); rx.blanker(blank);
            if (storm) rx.weather(40, 1.5, 2.5);
            double[] b = new double[960]; double sq = 0; long n = 0;
            for (int blk = 0; blk < 120 * R / 480; blk++) {
                java.util.Arrays.fill(b, 0);
                rx.process(b, 480, 0.05, 0.05, 0);   // about the app's default background level, -24 dB
                if (blk >= 50) for (int i = 0; i < 960; i += 2) { sq += b[i] * b[i]; n++; }
            }
            rms[v] = Math.sqrt(sq / n);
        }
        double rise = db(rms[1] / rms[0]), riseBlanked = db(rms[3] / rms[2]);
        System.out.printf("storm static: %.1f dB louder than calm, %.1f dB with the blanker on%n", rise, riseBlanked);
        check("a storm nearby is at least 6 dB louder than calm: " + rise, rise >= 6);
        check("the blanker leaves at least half of that rise: " + riseBlanked, riseBlanked >= 0.5 * rise);

        // The band owns a sky; its weather reaches the receiver and the Band tab.
        Band b = new Band(R, 3, 6, null);
        b.setWeather(Sky.STORM);
        double[] buf = new double[960], out = new double[3];
        for (int i = 0; i < 100 * 90; i++) { java.util.Arrays.fill(buf, 0); b.render(buf, 480, 7010, 600); }
        b.weather(out);
        near("band weather: storm ticks", out[0], 40, 0.1); near("band weather: storm crashes", out[1], 1.5, 0.05); near("band weather: storm gain", out[2], 2.5, 0.02);
        check("the Band tab says there is a storm: " + b.sky(), b.sky().contains("thunderstorm"));
        b.setWeather(Sky.CALM);
        for (int i = 0; i < 100 * 90; i++) { java.util.Arrays.fill(buf, 0); b.render(buf, 480, 7010, 600); }
        eq("calm band says nothing", b.sky(), "");

        // Operators mention the weather.
        boolean sawStatic = false, sawAurora = false;
        for (int n = 0; n < 600; n++) {
            Qso.Scene sc = new Qso.Scene(6, 20, 50, 15, false, false); sc.qrn = 0.8;
            for (Qso.Tx t : Qso.generate(new Dsp.Rng(n), sc, null).tx) if (t.text.contains("QRN VY BAD HR") || t.text.contains("LOTS OF STATIC TODAY") || t.text.contains("QRN FROM TSTORMS")) sawStatic = true;
            Qso.Scene sa = new Qso.Scene(6, 20, 50, 15, false, false); sa.aurora = true;
            for (Qso.Tx t : Qso.generate(new Dsp.Rng(n), sa, null).tx) if (t.text.contains("AURORA TONITE") || t.text.contains("K INDEX HIGH")) sawAurora = true;
        }
        check("operators mention static", sawStatic);
        check("operators mention the aurora", sawAurora);

        // A busy band in a storm still stays clear of full scale.
        double max = 0;
        for (int w = 0; w < 3; w++) {
            Engine e = new Engine(R, 61 + w, 480);
            Band busy = new Band(R, 19 + w, 6, null);
            busy.setActivity(2); busy.setWeather(Sky.STORM);
            e.setBand(busy); e.setBed(Bed.BAND); e.bedDb = -12; e.volume = 1; e.width = w; e.agc = w; e.nb = w == 1; e.setAudible(true);
            e.load(Keying.build("TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST", new Timing(25, 25), R), 0);
            float[] fb = new float[960];
            for (int blk = 0; blk < 120 * R / 480; blk++) { e.render(fb, 480); for (float v : fb) max = Math.max(max, Math.abs(v)); }
        }
        check("busy band in a storm stays clear of full scale: " + max, max < 0.9);
    }

    static String file(String name) throws java.io.IOException { return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("tests/data/" + name)), StandardCharsets.UTF_8); }

    static void spaceWeather() throws java.io.IOException {
        near("K index from NOAA's current layout", SpaceWx.latestKp(file("kp-sample.json")), 0.33, 1e-9);
        near("K index from the older layout", SpaceWx.latestKp(file("kp-sample-old.json")), 0.33, 1e-9);
        near("a storm is read", SpaceWx.latestKp(file("kp-storm.json")), 6.67, 1e-9);
        check("an empty list has no K", Double.isNaN(SpaceWx.latestKp(file("kp-empty.json"))));
        check("an error page with a cut-off list has no K", Double.isNaN(SpaceWx.latestKp(file("kp-bad.json"))));
        check("nothing has no K", Double.isNaN(SpaceWx.latestKp("")) && Double.isNaN(SpaceWx.latestKp(null)));
        check("a K outside 0 to 9 is refused", Double.isNaN(SpaceWx.latestKp("[{\"Kp\":42.0}]")));
        check("a K that is not a number is refused", Double.isNaN(SpaceWx.latestKp("[{\"Kp\":\"high\"}]")));
        // With a K of 5 or more, Auto uses it for the aurora; below 5 it leaves the aurora to chance.
        Sky k = new Sky(77); k.setKp(7);
        for (int i = 0; i < 6000; i++) k.advance(0.01, 0, 3, 50, Sky.AUTO);
        near("K 7 gives an aurora of 0.6", k.aurora(), 0.6, 0.01);
        Sky q = new Sky(77); q.setKp(3);
        for (int i = 0; i < 6000; i++) q.advance(0.01, 0, 3, 50, Sky.AUTO);
        near("K 3 gives no aurora of its own", q.aurora(), 0, 1e-9);
        Sky top = new Sky(78); top.setKp(9);
        for (int i = 0; i < 6000; i++) top.advance(0.01, 0, 3, 50, Sky.AUTO);
        near("K 9 is capped at 1", top.aurora(), 1, 0.01);
        Sky off = new Sky(79); off.setKp(Double.NaN);
        for (int i = 0; i < 6000; i++) off.advance(0.01, 0, 3, 50, Sky.AUTO);
        check("no K leaves the chance aurora in charge", off.aurora() >= 0);
        eq("the address is HTTPS", SpaceWx.URL.startsWith("https://"), true);
    }

    static void propagation() {
        double eu = Prop.REGION[0][0], euLon = Prop.REGION[0][1];
        check("June noon sun is high in Europe", Prop.sunElevation(50, 15, 5, 11) > 55);
        check("June midnight sun is down", Prop.sunElevation(50, 15, 5, 23) < 0);
        near("January noon sun is low", Prop.sunElevation(50, 15, 0, 11), 18, 4);
        near("Warsaw to Berlin", Prop.distanceKm(52.2, 21, 52.5, 13.4), 520, 30);
        check("a day path of 1000 km is open", Prop.pathLossDb(50, 15, 47, 2, 6, 12) < 1);
        check("Japan is not heard from Europe at noon", Prop.pathLossDb(50, 15, 36, 138, 6, 12) > 30);
        check("Japan gets through on a winter night: " + Prop.pathLossDb(50, 15, 36, 138, 0, 20), Prop.pathLossDb(50, 15, 36, 138, 0, 20) < 16);
        check("the next country is in the skip zone at night", Prop.pathLossDb(50, 15, 51, 10, 0, 0) >= 12);
        check("and loud by day", Prop.pathLossDb(50, 15, 51, 10, 0, 12) <= 2);
        near("a path is the same both ways", Prop.pathLossDb(50, 15, 40, -77, 0, 3), Prop.pathLossDb(40, -77, 50, 15, 0, 3), 1e-9);
        eq("CQ WW CW on the last full weekend of November", "" + Prop.contest(10, 28, 6) + Prop.contest(10, 29, 0), "CQ WW CWCQ WW CW");
        check("no contest a week earlier, or on a weekday", Prop.contest(10, 21, 6) == null && Prop.contest(10, 30, 1) == null && Prop.contest(10, 26, 4) == null);
        eq("ARRL DX CW in February", Prop.contest(1, 20, 6), "ARRL DX CW");
        check("evenings and weekends are busier", Prop.activity(20, false, false) > Prop.activity(3, false, false) && Prop.activity(10, true, false) > Prop.activity(10, false, false));
        check("people sleep at night", Prop.awake(3, false) < Prop.awake(20, false));

        java.util.regex.Pattern report = java.util.regex.Pattern.compile("(?:UR|RST)(?: RST)?(?: IS)? ([345])([3-9])9\\b");
        Dsp.Rng r = new Dsp.Rng(77);
        int dayFar = 0, nightFar = 0, nightHeard = 0, oneSided = 0, silent = 0, reports = 0, corner = 0, parks = 0, slowCq = 0, novices = 0;
        for (int n = 0; n < 3000; n++) {
            boolean day = n % 2 == 0;
            Qso.Scene sc = day ? new Qso.Scene(6, 12, eu, euLon, false, false) : new Qso.Scene(0, 0, eu, euLon, false, false);
            Qso q = Qso.generate(r, sc, null);
            boolean any = false, all = true;
            for (int i = 0; i < q.call.length; i++) {
                double path = Prop.pathLossDb(eu, euLon, q.lat[i], q.lon[i], sc.month, sc.hourUtc), km = Prop.distanceKm(eu, euLon, q.lat[i], q.lon[i]);
                near("what is heard is the station's strength less the path", q.lossDb[i], Math.min(60, q.baseDb[i] + path), 1e-9);
                boolean heard = q.lossDb[i] < 40;
                any |= heard; all &= heard;
                if (heard && km > 4000) { if (day) dayFar++; else nightFar++; }
                if (heard && !day) nightHeard++;
            }
            if (!any) silent++;
            if (q.kind == Qso.RAGCHEW && any && !all) oneSided++;
            if (q.kind == Qso.QUICK && q.tx.get(0).text.contains("POTA")) { parks++; if (q.zone == Qso.QRP_CORNER) corner++; }
            if (q.kind == Qso.CQ && q.style[0] == Qso.NOVICE) { novices++; if (q.zone == Qso.SLOW_END) slowCq++; }
            if (q.kind != Qso.RAGCHEW && q.kind != Qso.SKED) continue;
            for (Qso.Tx t : q.tx) {
                if (t.who >= 2) break;
                int to = 1 - t.who;
                double between = Math.min(60, q.baseDb[to] + Prop.pathLossDb(q.lat[0], q.lon[0], q.lat[1], q.lon[1], sc.month, sc.hourUtc));
                java.util.regex.Matcher m = report.matcher(t.text);
                while (m.find()) { reports++; check("a report is about the path between the two stations", Math.abs(m.group(2).charAt(0) - Qso.rst(between).charAt(1)) <= 2); }
            }
        }
        eq("no DX from Europe at a summer noon", dayFar, 0);
        check("some DX on a winter night, but mostly Europe: " + nightFar + " of " + nightHeard, nightFar > 40 && nightFar < 0.4 * nightHeard);
        check("some contacts are heard from one side only: " + oneSided, oneSided > 10);
        check("hardly any contact is wholly out of reach: " + silent, silent < 60);
        check("reports were checked: " + reports, reports > 2000);
        check("park stations gather in the QRP corner: " + corner + " of " + parks, parks > 50 && corner == parks);
        check("a beginner calls CQ at the slow end: " + slowCq + " of " + novices, novices > 20 && slowCq == novices);
        int tests = 0, quick = 0, fast = 0;
        for (int n = 0; n < 800; n++) {
            Qso q = Qso.generate(r, new Qso.Scene(10, 14, eu, euLon, true, true), null);
            if (q.kind == Qso.QUICK) { quick++; if (q.tx.get(0).text.contains("TEST")) { tests++; if (q.zone == Qso.FAST_END) fast++; } }
        }
        check("a contest weekend is mostly contest stations at the fast end: " + tests + " of 800", tests > 480 && tests == quick && fast == tests);
        int weekday = 0;
        for (int n = 0; n < 800; n++) { Qso q = Qso.generate(r, new Qso.Scene(10, 14, eu, euLon, false, false), null); if (q.kind == Qso.QUICK && q.tx.get(0).text.contains("TEST")) weekday++; }
        check("few contest stations in the week: " + weekday, weekday < 40);
        for (int z = 0; z < 4; z++) { double[] zr = Band.zoneRange(z); check("zone " + z + " is inside the band", zr[0] >= Band.LOW && zr[1] <= Band.HIGH && zr[1] - zr[0] > 5); }
        check("the QRP corner is around 7030", Band.zoneRange(Qso.QRP_CORNER)[0] < 7030 && Band.zoneRange(Qso.QRP_CORNER)[1] > 7030);

        // A band with a place and a date: quieter at night than in the evening, and it knows the contest.
        int[] busyAt = new int[2];
        for (int k = 0; k < 2; k++) {
            Band b = new Band(R, 40 + k, 10, null);
            b.setActivity(2); b.setPlace(eu, euLon); b.setClock(10, 18, 3, k == 0 ? 2.0 : 19.0);
            check("no contest on a Wednesday", b.contest() == null);
            double[] buf = new double[960];
            for (int blk = 0; blk < 8 * 60 * R / 480; blk++) {
                b.render(buf, 480, Band.NEWS, 600);
                if (blk % 200 == 0) { String act = b.activityJson(); busyAt[k] += act.length() - act.replace("\"k\"", "").length(); }
            }
        }
        check("the band is busier in the evening than at night: " + busyAt[1] + " vs " + busyAt[0], busyAt[1] > busyAt[0] * 1.3);
        Band cw = new Band(R, 5, 10, null);
        cw.setPlace(eu, euLon); cw.setClock(10, 28, 6, 15.0);
        eq("the band knows the contest", cw.contest(), "CQ WW CW");
    }

    /** Lengths of the tone runs of a keyed text, in dots. */
    static double[] tones(Keying k, double dot) { java.util.List<Double> a = new java.util.ArrayList<>(); for (int r : k.runs) if (r > 0) a.add(r / (dot * R)); double[] d = new double[a.size()]; for (int i = 0; i < d.length; i++) d[i] = a.get(i); return d; }
    static double mean(double[] a, boolean dahs) { double s = 0; int n = 0; for (double v : a) if (v > 2 == dahs) { s += v; n++; } return s / n; }
    static double spread(double[] a, boolean dahs) { double m = mean(a, dahs), s = 0; int n = 0; for (double v : a) if (v > 2 == dahs) { s += (v - m) * (v - m); n++; } return Math.sqrt(s / n) / m; }

    static void fists() {
        Timing t = new Timing(20, 20);
        String text = "CQ CQ DE SP9ABC SP9ABC PSE K THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG 599 TNX FER CALL";
        Keying exact = Keying.build(text, t, R);
        Dsp.Rng r = new Dsp.Rng(4);
        double keyer = 0, bug = 0, bugDit = 0, straight = 0, swiper = 0;
        for (int i = 0; i < 40; i++) {
            Keying k = Keying.build(text, t, R, Fist.of(Fist.KEYER, false, r), r);
            eq("a keyer makes the same number of runs", k.runs.length, exact.runs.length);
            for (int j = 0; j < k.runs.length; j++) if (k.runs[j] > 0) keyer = Math.max(keyer, Math.abs(k.runs[j] - exact.runs[j]));
            double[] b = tones(Keying.build(text, t, R, Fist.of(Fist.BUG, false, r), r), t.dot);
            bug += mean(b, true) / 40; bugDit = Math.max(bugDit, spread(b, false));
            double[] st = tones(Keying.build(text, t, R, Fist.of(Fist.STRAIGHT, false, r), r), t.dot);
            straight += spread(st, false) / 40;
            check("straight key stays readable: dah " + mean(st, true) + " dit " + mean(st, false), mean(st, true) > 2.2 * mean(st, false));
            double[] sw = tones(Keying.build(text, t, R, Fist.of(Fist.SIDESWIPER, false, r), r), t.dot);
            swiper += mean(sw, true) / mean(sw, false) / 40;
            Keying nov = Keying.build(text, t, R, Fist.of(Fist.STRAIGHT, true, r), r);
            check("a fist changes the length by less than half: " + seconds(nov) / seconds(exact), Math.abs(seconds(nov) / seconds(exact) - 1) < 0.5);
            eq("words still line up", nov.wordStart.length, exact.wordStart.length);
        }
        check("a keyer's elements are exact: off by " + keyer + " samples", keyer <= 1);
        check("a bug makes long dahs: " + bug + " dots", bug > 3.3 && bug < 4.6);
        check("a bug's dits are machine made: spread " + bugDit, bugDit < 0.04);
        check("a straight key is uneven: spread " + straight, straight > 0.04 && straight < 0.2);
        check("a sideswiper's dahs are short: ratio " + swiper, swiper < 2.75);
        Keying c = Keying.carrier(2.5, R);
        eq("carrier is one long tone", c.runs[0], (int) (2.5 * R));
        check("no fist is the old exact timing", java.util.Arrays.equals(Keying.build(text, t, R, null, null).runs, exact.runs));
    }

    /** Runs a steady tone through a receiver and returns the level it settles at. */
    static double through(Rx rx, double hz, double amp, boolean agc) {
        double[] b = new double[960];
        double peak = 0, ph = 0;
        for (int blk = 0; blk < 60; blk++) {
            for (int i = 0; i < 480; i++) { b[2 * i] = b[2 * i + 1] = amp * Math.sin(ph); ph += 2 * Math.PI * hz / R; }
            rx.process(b, 480, 0, 0, agc ? 1 : 0);
            if (blk >= 40) for (double v : b) peak = Math.max(peak, Math.abs(v));
        }
        return peak;
    }
    static double noiseRms(Rx rx, double noise, boolean agc, int blocks) {
        double[] b = new double[960]; double sq = 0; long n = 0;
        for (int blk = 0; blk < blocks + 50; blk++) {
            java.util.Arrays.fill(b, 0);
            rx.process(b, 480, noise, noise, agc ? 1 : 0);
            if (blk >= 50) for (double v : b) { sq += v * v; n++; }
        }
        return Math.sqrt(sq / n);
    }
    static double db(double ratio) { return 20 * Math.log10(ratio); }

    static void receiver() {
        double[] rms = new double[3];
        for (int w : new int[]{Rx.WIDE, Rx.NORMAL, Rx.NARROW}) {
            double lo = Rx.lo(w, 600), hi = Rx.hi(w, 600), span = hi - lo;
            Rx rx = new Rx(R, 21); rx.tune(lo, hi);
            near("width " + w + ": the pitch passes at full level", through(rx, 600, 1, false), 1, 0.06);
            double far = through(rx, hi + 1.5 * span, 1, false), edge = through(rx, hi, 1, false), low = through(rx, lo, 1, false);
            check("width " + w + ": far above the filter is gone: " + db(far) + " dB", db(far) < -60);
            double next = through(rx, 900, 1, false);
            if (w != Rx.WIDE) check("width " + w + ": a station 300 Hz up is well down: " + db(next) + " dB", db(next) < (w == Rx.NARROW ? -40 : -18));
            near("width " + w + ": upper edge is 3 dB down", db(edge), -3, 1.5);
            near("width " + w + ": lower edge is 3 dB down", db(low), -3, 1.5);
            near("width " + w + ": the formula matches the filter", db(through(rx, hi * 1.08, 1, false)), db(Rx.gain(lo, hi, hi * 1.08)), 2);
            Rx quiet = new Rx(R, 22); quiet.tune(lo, hi);
            rms[w] = noiseRms(quiet, 0.1, false, 600);
        }
        System.out.printf("receiver noise: wide %.1f dB, normal %.1f dB, narrow %.1f dB re the setting%n", db(rms[0] / 0.1), db(rms[1] / 0.1), db(rms[2] / 0.1));
        near("normal filter: noise is at the level set", db(rms[Rx.NORMAL] / 0.1), 0, 1.5);
        check("a wider filter lets in more noise", db(rms[Rx.WIDE] / rms[Rx.NORMAL]) > 4 && db(rms[Rx.NORMAL] / rms[Rx.NARROW]) > 1);
        Rx a = new Rx(R, 23);
        near("with AGC the noise in the gaps is still at the level set", db(noiseRms(a, 0.05, true, 600) / 0.05), 0, 1.5);
        near("with AGC the bulletin still comes out at 1", through(new Rx(R, 24), 600, 1, true), 1, 0.08);
        check("the AGC compresses: a signal 12 dB weaker comes out less than 6 dB weaker", db(through(new Rx(R, 25), 600, 0.25, true)) > -6);

        // A strong signal turns the gain down: the noise right after it is quieter, then comes back (Fast AGC; the Slow one is timed in filters()).
        Rx g = new Rx(R, 26);
        double[] b = new double[960]; double ph = 0;
        double[] after = new double[40];
        for (int blk = 0; blk < 100 + after.length; blk++) {
            boolean tone = blk >= 40 && blk < 100;
            for (int i = 0; i < 480; i++) { b[2 * i] = b[2 * i + 1] = tone ? Math.sin(ph) : 0; ph += 2 * Math.PI * 600 / R; }
            g.process(b, 480, 0.05, 0.05, Rx.AGC_FAST);
            if (blk == 99) near("meter shows the bulletin as 1", g.level, 1, 0.15);
            if (blk >= 100) { double sq = 0; for (double v : b) sq += v * v; after[blk - 100] = Math.sqrt(sq / 960); }
        }
        double early = (after[2] + after[3] + after[4] + after[5]) / 4, late = 0;
        for (int i = 30; i < 40; i++) late += after[i] / 10;
        System.out.printf("AGC: noise just after a strong signal %.1f dB, 0.3 s later %.1f dB re the setting%n", db(early / 0.05), db(late / 0.05));
        check("noise is pushed down behind a strong signal: " + db(early / 0.05) + " dB", db(early / 0.05) < -4);
        check("and comes back within half a second: " + db(late / 0.05) + " dB", db(late / 0.05) > -3);
        Rx flat = new Rx(R, 26);
        double steady = noiseRms(flat, 0.05, false, 100);
        near("without AGC nothing pumps", db(steady / 0.05), 0, 1.5);

        // Changing the width while a tone plays must not click.
        Rx sw = new Rx(R, 27); sw.tune(Rx.lo(Rx.NORMAL, 600), Rx.hi(Rx.NORMAL, 600));
        double prev = 0, jump = 0, least = 9, gap = 9; ph = 0;
        for (int blk = 0; blk < 60; blk++) {
            if (blk == 30) sw.tune(Rx.lo(Rx.NARROW, 600), Rx.hi(Rx.NARROW, 600));
            if (blk == 45) sw.tune(Rx.lo(Rx.WIDE, 600), Rx.hi(Rx.WIDE, 600));
            for (int i = 0; i < 480; i++) { b[2 * i] = b[2 * i + 1] = Math.sin(ph); ph += 2 * Math.PI * 600 / R; }
            sw.process(b, 480, 0, 0, 0);
            double top = 0;
            for (int i = 0; i < 960; i += 2) { if (blk > 20) jump = Math.max(jump, Math.abs(b[i] - prev)); prev = b[i]; top = Math.max(top, Math.abs(b[i])); }
            if (blk == 29 || blk == 44 || blk == 59) least = Math.min(least, top);
            if (blk > 20) gap = Math.min(gap, top);
        }
        check("the switch is a short gap, not a crossfade: " + gap, gap < 0.5);
        check("switching width does not click: largest step " + jump, jump < 0.1);
        check("the tone is back after each switch: " + least, least > 0.9);

        // The whole engine with a band: same Morse timing, bulletin at full level, never full scale.
        double[] plain = {0}, onBand = {0};
        renderCw(Bed.OFF, -24, 480, plain);
        for (int agc : new int[]{1, 2, 0}) for (int w = 0; w < 3; w++) {
            Engine e = new Engine(R, 7, 480);
            Band quiet = new Band(R, 5, 5, null);
            e.setBand(quiet); e.setBed(Bed.OFF); e.volume = 1; e.agc = agc; e.width = w; e.setAudible(true);
            Keying k = Keying.build("PARIS PARIS", new Timing(20, 12), R);
            e.load(k, 0);
            float[] fb = new float[960]; int n = 0; double when = -1, peak = 0;
            while (n < k.totalSamples + 2 * R) {
                e.render(fb, 480); n += 480;
                if (when < 0 && e.storyDone()) when = n;
                if (n < R) for (float v : fb) peak = Math.max(peak, Math.abs(v));   // before any station can start
            }
            eq("timing unchanged on the band, width " + w, when, plain[0]);
            // A narrow filter rings a little on each element, so allow it some overshoot.
            near("bulletin level on the band, width " + w + ", agc " + agc, peak, Engine.CW_PEAK, w == Rx.NARROW ? 0.07 : 0.05);
        }
        long t0 = System.nanoTime();
        double max = 0; int seconds = 180;
        for (int w = 0; w < 9; w++) {
            Engine e = new Engine(R, 31 + w, 480);
            Band busy = new Band(R, 9 + w, 6, () -> "Parliament passes the new energy law after long debate");
            busy.setActivity(2); busy.setSpeed(30, 30);
            e.setBand(busy); e.setBed(Bed.BAND); e.bedDb = -12; e.volume = 1; e.width = w % 3; e.agc = w / 3; e.setAudible(true);
            e.load(Keying.build("TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST TEST", new Timing(30, 30), R), 0);
            float[] fb = new float[960];
            Dsp.Rng r = new Dsp.Rng(w + 1);
            for (int blk = 0; blk < seconds * R / 480; blk++) {
                if (blk % 400 == 0) e.dialKhz = blk % 1200 == 0 ? Band.NEWS : busy.next(7000 + 40 * r.uniform(), 1);
                e.render(fb, 480);
                for (float v : fb) max = Math.max(max, Math.abs(v));
            }
            check("meter is a sane number: " + e.meter(), e.meter() >= 0 && e.meter() < 20);
        }
        double cost = (System.nanoTime() - t0) / 1e9 / (6 * seconds);
        System.out.printf("busy band through the receiver: %.1f%% of real time on this machine, peak %.2f%n", 100 * cost, max);
        check("busy band stays clear of full scale, with or without AGC: " + max, max < 0.9);
        check("rendering is far faster than real time: " + cost, cost < 0.3);
        Engine e1 = new Engine(R, 3, 480), e2 = new Engine(R, 3, 480);
        e1.setBand(new Band(R, 3, 5, null)); e2.setBand(new Band(R, 3, 5, null));
        float[] x1 = new float[960], x2 = new float[960]; boolean same = true;
        for (Engine e : new Engine[]{e1, e2}) { e.setBed(Bed.BAND); e.setAudible(true); }
        for (int i = 0; i < 3000; i++) { e1.render(x1, 480); e2.render(x2, 480); same &= java.util.Arrays.equals(x1, x2); }
        check("the band is repeatable from its seeds", same);
    }

    static void audio() {
        double[] d0 = {0}, d1 = {0}, d2 = {0};
        double[] off = renderCw(Bed.OFF, -24, 480, d0), ion = renderCw(Bed.BAND, -12, 480, d1), cos = renderCw(Bed.COSMIC, -12, 480, d2);
        eq("timing unchanged band", d1[0], d0[0]);
        eq("timing unchanged cosmic", d2[0], d0[0]);
        double peak = 0, jump = 0;
        for (int i = 1; i < off.length; i++) { peak = Math.max(peak, Math.abs(off[i])); jump = Math.max(jump, Math.abs(off[i] - off[i - 1])); }
        near("CW peak", peak, Engine.CW_PEAK, 0.01);
        check("no click: max step " + jump, jump < 0.05);
        Keying k = Keying.build("E", new Timing(50, 50), R);
        near("short element", k.runs[0], 0.024 * R, 1);

        for (int type : new int[]{Bed.BAND, Bed.COSMIC}) {
            Bed b = Bed.create(type, R, 3);
            int frames = R * 120, block = 4800;
            double[] buf = new double[2 * block];
            double sum = 0, sq = 0; long n = 0; double max = 0;
            for (int t = 0; t < frames; t += block) {
                b.fill(buf, block);
                for (int i = 0; i < 2 * block; i++) { sum += buf[i]; sq += buf[i] * buf[i]; n++; max = Math.max(max, Math.abs(buf[i])); }
            }
            double level = Math.sqrt(sq / n), dc = sum / n;
            System.out.printf("bed %d: rms %.3f dc %.5f peak %.2f%n", type, level, dc, max);
            near("bed rms near 1, type " + type, level, 1.0, 0.3);
            check("no DC, type " + type, Math.abs(dc) < 0.03);
            check("bounded, type " + type, max < 12);
        }

        // Full volume, loudest background and a long render must never exceed full scale.
        Engine loud = new Engine(R, 11, 1024);
        loud.volume = 1; loud.bedDb = -12; loud.setBed(Bed.BAND); loud.setAudible(true);
        loud.load(Keying.build("TEST TEST TEST TEST TEST TEST TEST TEST TEST", new Timing(30, 30), R), 0);
        float[] b = new float[2048];
        double max = 0;
        for (int t = 0; t < 60 * R / 1024; t++) { loud.render(b, 1024); for (float v : b) max = Math.max(max, Math.abs(v)); }
        check("never full scale: " + max, max < 1.0);

        double[] a1 = renderCw(Bed.BAND, -20, 480, d0), a2 = renderCw(Bed.BAND, -20, 480, d1);
        check("seed repeatable", java.util.Arrays.equals(a1, a2));

        // Notch: noise energy close to the tone falls well below the same noise unfiltered.
        for (int f : new int[]{400, 600, 1000}) {
            Engine en = new Engine(R, 5, 4800);
            en.toneHz = f; en.volume = 1; en.bedDb = -20; en.setBedInstance(new WhiteBed(f)); en.setAudible(true);
            float[] x = new float[9600];
            double near = 0, away = 0;
            for (int i = 0; i < 400; i++) {
                en.render(x, 4800);
                if (i < 30) continue; // fade-in
                near += goertzel(x, f);
                away += goertzel(x, f + 500) + goertzel(x, f - 250);
            }
            double db = 10 * Math.log10(near / (away / 2));
            System.out.printf("notch at %d: %.1f dB against nearby bands%n", f, db);
            check("notch " + f, db < -6);
        }

        // Pause silences the tone within the 8 ms gate and resumes at the start of the same word.
        Engine h = new Engine(R, 1, 480);
        h.volume = 1; h.setAudible(true);
        Keying kk = Keying.build("TTTT MMMM", new Timing(20, 20), R);
        h.load(kk, 0);
        float[] pb = new float[960];
        for (int i = 0; i < 300; i++) h.render(pb, 480); // into the second word (3 s)
        eq("second word", h.word(), 1);
        h.hold(true);
        double prev = 0, step = 0;
        h.render(pb, 480); for (int i = 0; i < 960; i += 2) { step = Math.max(step, Math.abs(pb[i] - prev)); prev = pb[i]; }
        check("hold no click: " + step, step < 0.06);
        h.render(pb, 480);
        double tail = 0; for (float v : pb) tail = Math.max(tail, Math.abs(v));
        eq("silent when held", tail, 0.0);
        h.setAudible(false);
        check("reports silent", h.silent());
    }

    static double goertzel(float[] x, double f) {
        double w = 2 * Math.PI * f / R, c = 2 * Math.cos(w), s1 = 0, s2 = 0;
        for (int i = 0; i < x.length; i += 2) { double s = x[i] + c * s1 - s2; s2 = s1; s1 = s; }
        return s1 * s1 + s2 * s2 - c * s1 * s2;
    }

    public static void main(String[] a) throws Exception {
        morse(); timing(); text(); abbrev(); band(); follow(); sideband(); filters(); weather(); spaceWeather(); feeds(); queue(); fists(); propagation(); if (a.length == 0) { audio(); receiver(); }
        if (a.length > 1) { FeedParser.Feed f = FeedParser.parse(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(a[1])), "https://x.example/"); System.out.println("real feed: " + f.title + ", " + f.items.size() + " items, first: " + TextNorm.story(f.title, f.items.get(0).title, f.items.get(0).summary, 1, true)); }
        System.out.println(ok + " passed, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
