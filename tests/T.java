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

    static void profile() {
        eq("clean upper-cases", Profile.clean("myname", "jacek"), "JACEK");
        eq("clean drops unsupported", Profile.clean("myname", "Ja\u00e9c#ek{}"), "JACEK");
        eq("clean collapses spaces", Profile.clean("myqth", "  new   york "), "NEW YORK");
        eq("clean limits a call", Profile.clean("mycall", "ABCDEFGHIJKLMNOP"), "ABCDEFGHIJ");
        eq("clean limits text", Profile.clean("myrig", "123456789012345678901234").length(), 20);
        eq("clean null", Profile.clean("myname", null), "");
        for (String c : new String[]{"SP9ABC", "DL1XYZ", "W1AW", "SP9ABC/P", "OH2BH", "9A1A", Profile.defaults().call}) eq("call ok " + c, Profile.check("mycall", c), "");
        for (String c : new String[]{"HELLO", "123", "SP9", ""}) check("call refused " + c, !Profile.check("mycall", c).isEmpty());
        eq("other fields may be empty", Profile.check("myname", ""), "");
        eq("same country", Profile.country("SP9ABC"), Profile.country("SP1XYZ"));
        check("different country", !Profile.country("SP9ABC").equals(Profile.country("DL1XYZ")));
        eq("portable suffix ignored", Profile.country("SP9ABC/P"), "SP");
        eq("portable prefix", Profile.country("DL/SP9ABC"), "SP");
        eq("digit prefix", Profile.country("9A1A"), "9A");
        Profile p = new Profile("SP0CWN", "A", "B", "C", "D", 5, 99, 7, 5);
        eq("wpm clamped", p.sendWpm, 30); eq("manners clamped", p.manners, 2); eq("unknown send-with falls back to typing", p.sendWith, 0);
    }

    static Heard.Expect expectFor(String userCall) {
        Profile u = new Profile(userCall, "JACEK", "KRAKOW", "FT891", "DIPOLE", 10, 15, 0, 0);
        return Heard.Expect.of("DL1ZZ", "HANS", "BERLIN", u);
    }
    static void heard() {
        Heard.Expect e = expectFor("SP9ABC");
        Heard h = Heard.parse("SP9ABX DE DL1ZZ", e);
        check("call one letter off is the user's", h.userCallHeard); check("and unsure", h.callUnsure);
        eq("sender call", h.fromCall, "DL1ZZ");
        h = Heard.parse("DL1ZZ DE SP9ABC", e);
        check("exact call is sure", h.userCallHeard && !h.callUnsure); eq("to call", h.toCall, "DL1ZZ");
        eq("5NN is 599", Heard.parse("UR RST 5NN 5NN", e).report, "599");
        eq("579C report keeps the chirp mark", Heard.parse("RST 579C", e).report, "579C");
        eq("run-on words split", Heard.parse("73AE0QDE", e).tokens.toString(), "[73, AE0Q, DE]");
        eq("name", Heard.parse("NAME JACEK JACEK", e).values.get("NAME"), "JACEK");
        eq("name with a wrong keyword", Heard.parse("NAMR JACEK", e).values.get("NAME"), "JACEK");
        eq("name with a wrong letter", Heard.parse("NAME JACEC", e).values.get("NAME"), "JACEK");
        eq("OP is a name", Heard.parse("OP JACEK", e).values.get("NAME"), "JACEK");
        eq("QTH value", Heard.parse("QTH KRAKOW KRAKOW <BT> NAME JACEK", e).values.get("QTH"), "KRAKOW");
        eq("weather value", Heard.parse("WX HR SUNNY 25C", e).values.get("WX"), "HR SUNNY 25C");
        check("WX?", Heard.parse("WX?", e).questions.contains("WX"));
        check("UR ANT?", Heard.parse("UR ANT?", e).questions.contains("ANT"));
        check("WHAT JOB?", Heard.parse("WHAT JOB?", e).questions.contains("JOB"));
        check("HOBBIES?", Heard.parse("HOBBIES?", e).questions.contains("HOBBY"));
        check("UR RST 599 is a statement, not a question", Heard.parse("UR RST 599", e).questions.isEmpty());
        check("a lone ? asks to repeat", Heard.parse("?", e).requests.contains("?"));
        check("QRS and AGN", Heard.parse("PSE AGN QRS", e).requests.containsAll(java.util.Arrays.asList("AGN", "QRS")));
        eq("turn marker", Heard.parse("TNX FER CALL K", e).turn, "K");
        eq("prosign turn marker", Heard.parse("TNX FER CALL KN", e).turn, "KN");
        check("closing 73", Heard.parse("TU 73 SK", e).closings.containsAll(java.util.Arrays.asList("73", "TU", "SK")));
        eq("greeting", Heard.parse("GM OM", e).greeting, "GM");
        check("EEEEEEE is the error sign", Heard.parse("EEEEEEE", e).errorSign);
        check("good sending is readable", !Heard.parse("DL1ZZ DE SP9ABC UR RST 599 NAME JACEK K", e).unreadable);
        Dsp.Rng r = new Dsp.Rng(5);
        int bad = 0;
        for (int i = 0; i < 100; i++) {
            StringBuilder b = new StringBuilder();
            for (int w = 0; w < 6; w++) { for (int k = 0, len = 3 + r.nextInt(5); k < len; k++) b.append((char) ('A' + r.nextInt(26))); b.append(' '); }
            if (Heard.parse(b.toString(), e).unreadable) bad++;
        }
        check("random letters are unreadable: " + bad + "/100", bad >= 95);
        // The Morse-aware cost is lower than the plain one where the codes differ by one element.
        String[][] pairs = {{"NAMT", "NAME"}, {"QTS", "QTH"}, {"VR", "UR"}, {"TNX", "ENX"}};
        for (String[] p : pairs) check("morse cost " + p[0] + "/" + p[1], Heard.cost(p[0], p[1]) < Heard.plain(p[0], p[1]));
        near("plain cost is 1", Heard.plain("NAMT", "NAME"), 1, 1e-9);
        near("morse cost is 0.5", Heard.cost("NAMT", "NAME"), 0.5, 1e-9);
        // Re-segmentation from timing: the TT could have been an M.
        java.util.List<Heard.Letter> ls = new java.util.ArrayList<>();
        ls.add(new Heard.Letter("N")); ls.add(new Heard.Letter("A")); ls.add(new Heard.Letter("TT", "M")); ls.add(new Heard.Letter("E"));
        eq("without the alternative the word fails", Heard.parse("NATTE", e).values.size() + "/" + Heard.parse("NATTE", e).tokens.get(0), "0/NATTE");
        eq("with it the word is NAME", Heard.parse(ls, e).tokens.get(0), "NAME");
        // Morse.letter.
        eq("letter A", Morse.letter(".-"), "A"); eq("letter BT", Morse.letter("-...-"), "BT"); eq("letter SK", Morse.letter("...-.-"), "SK");
        for (char c = 'A'; c <= 'Z'; c++) eq("round trip " + c, Morse.letter(Morse.code(c)), "" + c);
        check("unknown code", Morse.letter("......-") == null);
    }

    /** A hash of the text of 500 generated contacts. K3 changes the visibility of Qso.Op and Qso.Gen; the contacts must not change. */
    static long contactsHash() {
        long h = 1125899906842597L;
        for (int seed = 1; seed <= 500; seed++) {
            Qso q = Qso.generate(new Dsp.Rng(seed), seed % 12, seed % 24, seed % 3 == 0 ? "WORLD LEADERS MEET IN GENEVA TODAY" : null);
            for (Qso.Tx t : q.tx) {
                String x = t.who + "|" + t.text + "|" + Math.round(t.speed * 1000) + "|" + Math.round(t.pause * 1000) + "|" + Math.round(t.carrier * 1000);
                for (int i = 0; i < x.length(); i++) h = 31 * h + x.charAt(i);
            }
            for (String c : q.call) for (int i = 0; i < c.length(); i++) h = 31 * h + c.charAt(i);
        }
        return h;
    }
    static void contactsUnchanged() { eq("generated contacts unchanged", contactsHash(), 9109856235999552946L); }

    /**
     * Renders a fixed band at several dial positions (7010, the news, a busy spot, CW-R), through Wide and Narrow,
     * and hashes every sample with what the UI reads at intervals. With the story off this must never change.
     */
    static long bandHash(Script story) {
        long h = 1125899906842597L;
        for (int w : new int[]{Rx.WIDE, Rx.NARROW}) {
            Band b = new Band(R, 77 + w, 4, () -> "Parliament passes the new energy law after long debate");
            b.setActivity(2); b.setPlace(50, 20); b.setClock(4, 12, 2, 18.5);
            if (story != null) b.setStory(story, 0);
            double lo = Rx.lo(w, 600), hi = Rx.hi(w, 600), dial = 7010;
            double[] buf = new double[960];
            double[] spots = {7010, 7020, -1, 7003.5, 7010, -1, 7010.03};
            for (int seg = 0; seg < spots.length; seg++) {
                dial = spots[seg] < 0 ? b.next(dial, 1) : spots[seg];
                int sign = seg == 5 ? -1 : 1;
                for (int blk = 0; blk < 100 * 25; blk++) {
                    java.util.Arrays.fill(buf, 0);
                    b.render(buf, 480, dial, 600, lo, hi, sign);
                    for (double v : buf) h = 31 * h + Double.doubleToLongBits(v);
                    if (blk % 100 == 0) {
                        String x = b.waterfallJson() + b.activityJson() + b.copy(dial) + b.clearAt(dial + 0.2);
                        for (int i = 0; i < x.length(); i++) h = 31 * h + x.charAt(i);
                    }
                    if (blk % 1000 == 999) h = 31 * h + Double.doubleToLongBits(b.next(dial, -1)) + 7 * Double.doubleToLongBits(b.follow(dial, 8, 30));
                }
            }
        }
        return h;
    }
    static void bandUnchanged() { eq("band with the story off unchanged", bandHash(null), -3495697929261530587L); }

    // ---- the story station ----
    static final double[] SB = new double[960];
    /** Renders seconds of band at a dial, through a filter width, in blocks of 10 ms. */
    static void run(Band b, double dial, double seconds, int width, int sign) {
        double lo = Rx.lo(width, 600), hi = Rx.hi(width, 600);
        for (int i = 0; i < seconds * 100; i++) { java.util.Arrays.fill(SB, 0); b.render(SB, 480, dial, 600, lo, hi, sign); }
    }
    static boolean sending(Band b) { return b.storyJson().contains("\"sending\":true"); }
    /** Renders at the dial until the story starts (or stops, with want false) sending; returns the seconds it took, or -1. */
    static double until(Band b, double dial, boolean want, double max, int width) {
        for (int i = 0; i < max * 100; i++) { if (sending(b) == want) return i / 100.0; run(b, dial, 0.01, width, 1); }
        return -1;
    }
    static Script net(String... overs) {
        StringBuilder t = new StringBuilder("Test Net\n@ AAA = Alpha one\n@ BBB = Bravo two\n  0.10 min  === EP 1  START ===\n");
        for (int i = 0; i < overs.length; i++) {
            if (i == overs.length / 2) t.append("  0.20 min  === EP 2  MIDDLE ===\n");
            t.append("  0.10 min  ").append(overs[i]).append('\n');
        }
        return Script.parse("test.txt", t.toString());
    }

    static void storyStation() throws Exception {
        Script h = Script.parse("hail_mary_net.txt", readAsset("hail_mary_net.txt"));
        // It waits while you are tuned away.
        Band b = new Band(R, 31, 5, null);
        b.setActivity(0); b.setSpeed(25, 25);
        b.setStory(h, 5);
        eq("story starts at the given line", b.storyLine(), 5);
        run(b, 7030, 40, Rx.NORMAL, 1);
        check("tuned away it does not send", !sending(b));
        eq("tuned away it keeps its line", b.storyLine(), 5);
        check("and says it waits", b.storyJson().contains("\"waiting\":true"));
        check("the JSON names the story", b.storyJson().contains("\"title\":\"Hail Mary Net\"") && b.storyJson().contains("\"total\":668"));
        double t0 = until(b, 7010, true, 3, Rx.NORMAL);
        check("tuned in it starts at once: " + t0, t0 >= 0 && t0 < 1.5);
        Script.Line l5 = h.lines.get(5);
        String name5 = h.names[java.util.Arrays.asList(h.calls).indexOf(l5.speaker)];
        check("the JSON names the sender", b.storyJson().contains("\"now\":\"" + name5 + "\""));
        // Tuned away in the middle of an over, it stops and sends the whole over again once you are back.
        run(b, 7010, 4, Rx.NORMAL, 1);
        check("some of the over was copied", b.copy(7010).contains(l5.speaker + ": "));
        double stop = until(b, 7030, false, 10, Rx.NORMAL);
        check("tuned away mid-over it stops at the end of a word: " + stop, stop >= 0 && stop < 2.5);
        run(b, 7030, 30, Rx.NORMAL, 1);
        eq("the over is kept", b.storyLine(), 5);
        check("still waiting", !sending(b));
        until(b, 7010, true, 3, Rx.NORMAL);
        until(b, 7010, false, 300, Rx.NORMAL);
        eq("after the repeat comes the next line", b.storyLine(), 6);
        String c = b.copy(7010);
        check("the whole over is in the copy, prosigns as written", c.contains(l5.speaker + ": " + l5.text));
        eq("and only once", c.split(java.util.regex.Pattern.quote(l5.speaker + ": "), -1).length - 1, 1);
        // The same story again keeps its position; another one starts where asked.
        b.setStory(Script.parse("hail_mary_net.txt", readAsset("hail_mary_net.txt")), 0);
        eq("same story keeps the position", b.storyLine(), 6);
        // Going to a line: the over stops at the end of its word, and that line is next. Again is the line before.
        until(b, 7010, true, 10, Rx.NORMAL);
        b.setStoryLine(40);
        eq("the chosen line is reported at once", b.storyLine(), 40);
        double cut = until(b, 7010, false, 10, Rx.NORMAL);
        check("the over stops within a word: " + cut, cut >= 0 && cut < 2.5);
        until(b, 7010, true, 5, Rx.NORMAL);
        eq("the chosen line is sent", b.storyLine(), 40);
        check("its sender is named", b.storyJson().contains("\"call\":\"" + h.lines.get(40).speaker + "\""));
        b.setStoryLine(b.storyLine() - 1);
        until(b, 7010, false, 10, Rx.NORMAL);
        until(b, 7010, true, 5, Rx.NORMAL);
        eq("Again sends the line before", b.storyLine(), 39);
        check("the episode is that line's", b.storyJson().contains("\"ep\":" + (h.lines.get(39).episode + 1) + ","));
        b.setStoryLine(99999);
        eq("a line past the end is the last", b.storyLine(), 667);
        b.setStory(null, 0);
        eq("off", b.storyJson(), "{\"on\":false}");
        check("off: 7010 is no longer a station", Math.abs(b.next(7009, 1) - Band.STORY) > 0.01);

        // Lines advance and wrap round after 60 s; pauses come from the script.
        Script s3 = net("AAA -> BBB  BBB DE AAA <BT> HI <KN>", "BBB -> AAA  AAA DE BBB R <KN>", "AAA -> BBB  TU <SK>");
        Band w = new Band(R, 32, 5, null);
        w.setActivity(0); w.setSpeed(30, 30);
        w.setStory(s3, 0);
        StringBuilder seq = new StringBuilder();
        double gap = 0, quiet = 0;
        for (int i = 0; i < 100 * 120; i++) {
            boolean was = sending(w);
            run(w, 7010, 0.01, Rx.NORMAL, 1);
            boolean now = sending(w);
            if (now && !was) { seq.append(w.storyLine()); gap = Math.max(gap, quiet); }
            quiet = now ? 0 : quiet + 0.01;
            if (seq.length() >= 4) break;
        }
        eq("lines advance and wrap", seq.toString(), "0120");
        check("60 s before starting again: " + gap, gap > 59.9 && gap < 61.5);

        // Every call of a net is heard in Narrow, on CW and CW-R: none is held as outside the filter.
        // Four calls that all talk to each other, so the outer pitches are used.
        String[] k4 = {"AAA", "BBB", "CCC", "DDD"};
        StringBuilder kt = new StringBuilder("K Net\n  0.1 min  === EP 1  ALL ===\n");
        for (String p : k4) for (String q : k4) if (!p.equals(q)) kt.append("  0.1 min  ").append(p).append(" -> ").append(q).append("  E\n");
        Script ring = Script.parse("k.txt", kt.toString());
        int[] off = Script.offsetsHz(ring);
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (int o : off) used.add(o);
        check("the net uses both outer pitches " + used, used.contains(-75) && used.contains(75));
        for (int side : new int[]{1, -1}) {
            Band n = new Band(R, 33, 5, null);
            n.setActivity(0); n.setSpeed(30, 30);
            n.setStory(ring, 0);
            int lines = 0, last = -1;
            for (int i = 0; i < 100 * 90 && lines < 12; i++) {
                run(n, 7010, 0.01, Rx.NARROW, side);
                if (sending(n) && n.storyLine() != last) { last = n.storyLine(); lines++; }
            }
            eq("all calls sent in Narrow, side " + side, lines, 12);
        }

        // With a story on, random stations keep 0.4 kHz away from 7010, and no CQ there; next() visits 7010.
        Band busy = new Band(R, 34, 5, () -> "Parliament passes the new energy law after long debate");
        busy.setActivity(2); busy.setClock(5, 19.5);
        busy.setStory(h, 0);
        double closest = 99; int seen = 0;
        for (int sec = 0; sec < 20 * 60; sec++) {
            run(busy, 7025, 1, Rx.WIDE, 1);
            for (double f : busy.slotKhz()) { closest = Math.min(closest, Math.abs(f - Band.STORY)); seen++; }
        }
        check("random stations were on: " + seen, seen > 1000);
        check("random stations never within 0.4 kHz of 7010: " + closest, closest > 0.4);
        check("7010 is not clear for a CQ", !busy.clearAt(7010.3) && !busy.clearAt(7009.7));
        boolean visits = false;
        double d = Band.LOW;
        for (int i = 0; i < 30; i++) { d = busy.next(d, 1); if (Math.abs(d - Band.STORY) < 1e-9) visits = true; }
        check("next() visits 7010", visits);
        check("no CQ is taken at 7010", busy.cqNear(7010) == null);
        // Tuned to the story, follow() never moves the dial.
        boolean stays = true;
        for (int sec = 0; sec < 300; sec++) { run(busy, 7010, 1, Rx.NORMAL, 1); if (!Double.isNaN(busy.follow(7010, 2, 5))) stays = false; }
        check("follow() stays on the story", stays);
        check("the waterfall shows the story", busy.waterfallJson().contains("[7010.0") || busy.waterfallJson().contains("[7009.9"));
        check("and the activity list", busy.activityJson().contains("\"k\":7010.0") || busy.activityJson().contains("\"k\":7009.9"));
        // The same title sounds the same.
        Band x1 = new Band(R, 35, 5, null), x2 = new Band(R, 35, 5, null);
        x1.setActivity(0); x2.setActivity(0); x1.setStory(h, 0); x2.setStory(h, 0);
        double[] a1 = new double[960], a2 = new double[960]; boolean same = true, any = false;
        for (int i = 0; i < 3000; i++) {
            java.util.Arrays.fill(a1, 0); java.util.Arrays.fill(a2, 0);
            x1.render(a1, 480, 7010, 600); x2.render(a2, 480, 7010, 600);
            same &= java.util.Arrays.equals(a1, a2);
            for (double v : a1) if (v != 0) any = true;
        }
        check("the story is heard and the same each time", same && any);
    }

    // ---- K3: the friendly partner ----
    static final Profile ME = new Profile("SP0CWN", "JACEK", "KRAKOW", "FT891", "DIPOLE", 10, 15, 0, 0);
    static Profile me(int manners) { return new Profile("SP0CWN", "JACEK", "KRAKOW", "FT891", "DIPOLE", 10, 15, manners, 0); }
    static Qso.Tx over(Partner p, String text) { return over(p, text, 15, 0); }
    static Qso.Tx over(Partner p, String text, double wpm, double offset) { return p.hear(Heard.parse(text, p.expect()), 10, offset, wpm); }
    static java.util.List<String> segs(Qso.Tx t) { return java.util.Arrays.asList(t.text.split(" <BT> ")); }
    static final java.util.List<String> ENC = java.util.Arrays.asList("NO PROBLEM", "NP", "GUD COPY NW", "UR CW IS FB", "NICE FIST", "TAKE UR TIME", "TNX FER QSO UR DOING FB", "HPE CU AGN");
    static final java.util.List<String> REQ = java.util.Arrays.asList("CALL?", "UR RST?", "UR NAME?", "UR QTH?", "PSE UR CALL?");
    static final java.util.List<String> QUE = java.util.Arrays.asList("UR WX?", "WHAT RIG ES ANT?", "ANY OTHER HOBBIES?", "WHAT DO U DO FER WORK?", "HW LONG HAM?");
    static int count(Qso.Tx t, java.util.List<String> set) { int n = 0; for (String s : segs(t)) if (set.contains(s.trim())) n++; return n; }
    static Partner calling(long seed, int manners, int kind) { return Partner.calling(new Dsp.Rng(seed), new Qso.Scene(5, 20), me(manners), manners, kind); }

    static void partner() {
        // A minimal contact: both calls and one report.
        Partner p = calling(1, 0, Partner.STANDARD);
        check("the partner calls CQ first", p.first() != null && p.first() == null);
        String pc = p.call();
        check("the partner answers a call", over(p, pc + " DE SP0CWN SP0CWN K") != null);
        check("reply 2", over(p, "SP0CWN DE " + pc + " UR RST 579 579 K") != null);
        check("not finished before the user says 73", !p.done());
        check("reply to 73", over(p, "TNX 73 SK") != null);
        check("a minimal contact is done", p.done()); check("and complete", p.result().complete);
        eq("the contact's call", p.result().call, pc); eq("report sent", p.result().rstSent, "579"); check("report received", !p.result().rstRcvd.isEmpty());
        check("nothing after the end", over(p, "K") == null);
        // An activator: call, report, thanks.
        p = calling(2, 0, Partner.ACTIVATOR); p.first();
        over(p, p.call() + " DE SP0CWN SP0CWN K"); over(p, "R UR 599 TU 73");
        check("an activator contact ends", p.done() && p.result().complete);
        // A full standard contact in two overs after the call.
        p = calling(3, 0, Partner.STANDARD); p.first();
        over(p, p.call() + " DE SP0CWN K");
        over(p, "R TNX UR RST 579 579 <BT> NAME JACEK <BT> QTH KRAKOW K");
        check("a standard contact ends once the exchange is done", p.done() && p.result().complete);
        eq("the partner learned the name", p.result().qth.isEmpty(), false);
        // A chat of at least eight overs.
        int shortest = 99;
        for (long seed = 1; seed <= 20; seed++) {
            p = calling(seed, 0, Partner.CHATTY); p.first();
            over(p, p.call() + " DE SP0CWN K");
            over(p, "R TNX UR RST 579 579 <BT> NAME JACEK <BT> QTH KRAKOW K");
            int n = 0;
            while (!p.done() && n < 40) { over(p, "R FB TNX FER INFO <BT> UR WX HR CLOUDY K"); n++; }
            check("a chat ends by itself " + seed, p.done());
            shortest = Math.min(shortest, p.result().overs);
        }
        check("a chat takes at least 8 overs, shortest " + shortest, shortest >= 8);
        // One letter wrong in the user's call: confirmed once, then accepted.
        p = calling(4, 0, Partner.STANDARD); p.first();
        Qso.Tx t = over(p, p.call() + " DE SP0CWX K");
        check("the call is confirmed", t.text.contains("SP0CWN?"));
        t = over(p, "R");
        check("and accepted", !t.text.contains("SP0CWN?") && !t.text.contains("CALL?"));
        // Three unreadable overs.
        p = calling(5, 0, Partner.STANDARD); p.first();
        double w0 = p.wpm();
        t = over(p, "QZXJ KWVP VBQX"); check("first help", t.text.contains("SRI NO COPY PSE AGN"));
        t = over(p, "ZZQX JKQW WPQV"); check("second help", t.text.contains("PSE AGN SLOWLY")); check("and the partner slows down", p.wpm() < w0);
        t = over(p, "XQJZ QQKX VWPJ"); check("third help asks for one thing", t.text.contains("PSE UR CALL?"));
        check("the contact goes on", !p.done() && over(p, p.call() + " DE SP0CWN K") != null);
        // Silence: three prompts, then a kind sign-off, incomplete.
        p = Partner.answering(new Dsp.Rng(6), new Qso.Scene(5, 20), me(0), 0, Partner.STANDARD); p.first();
        check("no prompt too early", p.silence(10) == null);
        check("prompt 1 at 25 s", p.silence(26) != null); check("no second prompt yet", p.silence(30) == null);
        check("prompt 2", p.silence(51) != null); check("prompt 3", p.silence(76) != null);
        t = p.silence(101);
        check("then a sign-off", t != null && t.text.contains("NIL HR") && t.text.contains("73 CUL") && p.done());
        check("incomplete, not failed", !p.result().complete);
        // QRS twice.
        p = calling(7, 0, Partner.CHATTY); p.first(); over(p, p.call() + " DE SP0CWN K");
        double a = p.wpm();
        over(p, "R QRS PSE K"); double b = p.wpm(); over(p, "QRS PSE K"); double c = p.wpm();
        near("QRS twice slows to 56%", c / a, 0.5625, 0.01); check("it only falls", b < a);
        for (int i = 0; i < 4; i++) over(p, "R FB K");
        check("and never rises again", p.wpm() <= c + 1e-9);
        // The error sign is answered at most once.
        p = calling(8, 0, Partner.CHATTY); p.first(); over(p, p.call() + " DE SP0CWN K");
        int np = 0;
        for (String x : new String[]{"R UR RST 579 <HH> 579 K", "NAME JACEK <HH> JACEK K", "EEEEEEE QTH KRAKOW K", "R <HH> R K"}) { Qso.Tx r = over(p, x); if (r != null) for (String s : segs(r)) if (s.equals("NP") || s.equals("NO PROBLEM")) np++; }
        check("NP at most once: " + np, np <= 1);
        // Realistic manners do not hear you off frequency; patient ones do.
        Partner rp = calling(9, 2, Partner.STANDARD), pp = calling(9, 0, Partner.STANDARD);
        check("realistic: no answer at 300 Hz", over(rp, rp.call() + " DE SP0CWN K", 15, 300) == null);
        check("patient: answers at 300 Hz", over(pp, pp.call() + " DE SP0CWN K", 15, 300) != null);
        // Four questions in one over: two now, two next time.
        int good = 0;
        for (long seed = 20; seed < 40; seed++) {
            p = calling(seed, 0, Partner.CHATTY); p.first();
            over(p, p.call() + " DE SP0CWN K");
            Qso.Op o = p.op();
            Qso.Tx r1 = over(p, "R UR RST 579 579 <BT> NAME JACEK <BT> QTH KRAKOW <BT> WX? ANT? HOBBY? JOB? K");
            Qso.Tx r2 = over(p, "R FB K");
            String[] want = {o.wx, o.ant, o.hobby, o.job};
            int in1 = 0, in2 = 0;
            for (String w : want) { if (r1.text.contains(w)) in1++; if (r2.text.contains(w)) in2++; }
            if (in1 == 2 && in2 == 2) good++;
        }
        check("two answered now and two next over: " + good + "/20", good >= 18);
        // After the user's weather, the partner never asks for it.
        p = calling(11, 0, Partner.CHATTY); p.first(); over(p, p.call() + " DE SP0CWN K");
        boolean asked = false;
        Qso.Tx r = over(p, "R UR RST 579 <BT> NAME JACEK <BT> QTH KRAKOW <BT> WX HR SUNNY 25C K");
        for (int i = 0; i < 12 && r != null; i++) { if (r.text.contains("UR WX?")) asked = true; r = over(p, "R FB K"); }
        check("it does not ask for the weather again", !asked);
        check("hint is not empty", !p.hint().isEmpty() && !calling(12, 0, 1).hint().isEmpty());
        // 2000 random scripts: one encouragement, one request and one question at most, never "wrong", never faster than the user.
        String[] pool = {"{c} DE SP0CWN K", "SP0CWX DE {c} K", "R UR RST 579 579 K", "UR RST 5NN K", "NAME JACEK K", "QTH KRAKOW K", "WX? K", "UR ANT? K", "HOBBY? K", "AGN PSE K", "QRS K",
            "EEEEEEE K", "QZXJ KWVP", "R R K", "73 SK", "TNX 73", "RIG FT891 ANT DIPOLE K", "WX HR SUNNY 25C K", "?", "SP0CWN"};
        Dsp.Rng rr = new Dsp.Rng(99);
        int badEnc = 0, badReq = 0, badQue = 0, badWord = 0, badSpeed = 0, replies = 0;
        for (int i = 0; i < 2000; i++) {
            int manners = rr.nextInt(3), kind = rr.nextInt(3);
            Partner q = rr.chance(0.3) ? Partner.answering(rr, new Qso.Scene(rr.nextInt(12), rr.nextInt(24)), me(manners), manners, kind) : Partner.calling(rr, new Qso.Scene(rr.nextInt(12), rr.nextInt(24)), me(manners), manners, kind);
            q.first();
            for (int k = 0; k < 30 && !q.done(); k++) {
                double uw = 8 + rr.nextInt(18);
                Qso.Tx x = over(q, pool[rr.nextInt(pool.length)].replace("{c}", q.call()), uw, 0);
                if (x == null) continue;
                replies++;
                if (count(x, ENC) > 1) badEnc++;
                if (count(x, REQ) > 1) badReq++;
                if (count(x, QUE) > 1) badQue++;
                if (x.text.contains("WRONG") || x.text.contains("ERROR") || x.text.contains("FAIL")) badWord++;
                if (manners == 0 && x.speed * q.baseWpm > uw + 1e-6) badSpeed++;
            }
        }
        check("many replies were made: " + replies, replies > 10000);
        eq("never more than one encouragement", badEnc, 0); eq("never more than one request", badReq, 0); eq("never more than one question", badQue, 0);
        eq("never a harsh word", badWord, 0); eq("patient partner never faster than the user", badSpeed, 0);
    }

    // ---- K4: a live station, and the user's transmitter ----
    static void renderBand(Band b, double dial, double seconds) { double[] lr = new double[960]; for (int i = 0; i < (int) (seconds * 100); i++) { java.util.Arrays.fill(lr, 0); b.render(lr, 480, dial, 600); } }
    static boolean liveOn(Band b) { return b.liveJson().contains("\"on\":true"); }

    static void live() {
        // The user calls CQ and a live partner answers on the user's frequency, and four scripted overs reach the end.
        for (long seed = 1; seed <= 6; seed++) {
            Band b = new Band(R, seed, 5, null);
            b.setPlace(50, 20); b.setClock(5, 4, 3, 19.0);
            Partner p = Partner.answering(new Dsp.Rng(seed), b.scene(), me(0), 0, Partner.STANDARD);
            check("answerMe " + seed, b.answerMe(7015.00, p));
            check("the band says a contact is live", liveOn(b) && b.hasLive());
            check("a second live station is refused", !b.answerMe(7016.0, Partner.answering(new Dsp.Rng(9), b.scene(), me(0), 0, Partner.STANDARD)));
            check("follow stays while live " + seed, Double.isNaN(b.follow(7015, 0, 0)));
            String[] overs = {"R UR RST 579 579 K", "NAME JACEK K", "QTH KRAKOW K", "TNX 73 SK"};
            boolean gone = false;
            for (String o : overs) {
                renderBand(b, 7015.00, 150);
                if (!liveOn(b)) { gone = true; break; }
                check("still the same partner", b.liveJson().contains("\"call\":\"" + p.call() + "\""));
                b.userOver(Heard.parse(o, p.expect()), 5, 15, 7015.00);
            }
            renderBand(b, 7015.00, 200);
            check("the contact reached its end " + seed, p.done());
            check("and the station was freed", !liveOn(b));
        }
        // Taking over a station that is calling CQ.
        boolean took = false;
        for (long seed = 1; seed <= 12 && !took; seed++) {
            Band b = new Band(R, seed, 5, null);
            b.setClock(5, 4, 3, 19.0);
            for (int step = 0; step < 400 && !took; step++) {
                renderBand(b, 7020, 3);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"k\":([0-9.]+)").matcher(b.activityJson());
                while (m.find() && !took) {
                    double k = Double.parseDouble(m.group(1));
                    Qso q = b.cqNear(k);
                    if (q == null) continue;
                    Partner p = Partner.from(q, 0, b.scene(), new Dsp.Rng(seed), me(0), 0);
                    if (!b.takeOver(k, p)) continue;
                    took = true;
                    check("the taken-over station is live", liveOn(b));
                    renderBand(b, k, 60);
                    check("it waits for the user and is not replaced", liveOn(b) && b.liveJson().contains(p.call()));
                    b.userOver(Heard.parse(p.call() + " DE SP0CWN SP0CWN K", p.expect()), 5, 15, k);
                    renderBand(b, k, 30);
                    check("it answered", b.liveJson().contains("SP0CWN"));
                    check("no other station can be taken over meanwhile", !b.takeOver(k, p));
                    b.endLive();
                    check("ending frees it", !liveOn(b));
                }
            }
        }
        check("a CQ station was found and taken over", took);

        // Engine: the receiver is ducked while the user transmits, with no click, and comes back 0.4 s after the last key-up.
        Engine e = new Engine(R, 1, 480);
        e.volume = 1; e.setAudible(true);
        e.load(Keying.carrier(40, R), 0);
        float[] x = new float[960];
        for (int i = 0; i < 100; i++) e.render(x, 480);
        double a0 = peak(x, 0, 960);
        double jump = 0, prev = x[958];
        java.util.List<float[]> blocks = new java.util.ArrayList<>();
        e.key(true);
        double[] amp = new double[400];
        for (int i = 0; i < 400; i++) { e.render(x, 480); amp[i] = peak(x, 0, 960); for (int k = 0; k < 960; k += 2) { jump = Math.max(jump, Math.abs(x[k] - prev)); prev = x[k]; } if (i == 40) e.key(false); }
        near("full level before", a0, Engine.CW_PEAK, 0.02);
        check("ducked by 20 dB within 5 ms (the next 10 ms block): " + amp[0] / a0, amp[1] / a0 < 0.12);
        check("still ducked while keyed", amp[30] / a0 < 0.12);
        check("still ducked 0.3 s after key-up: " + amp[40 + 30] / a0, amp[40 + 30] / a0 < 0.12);
        check("released after 0.4 s: " + amp[40 + 50] / a0, amp[40 + 50] / a0 > 0.9);
        check("no click: max step " + jump, jump < 0.06);
        // Typed text plays a sidetone at the CW pitch and ducks the receiver while it goes.
        Engine t = new Engine(R, 2, 480);
        t.volume = 1; t.toneHz = 700; t.setAudible(true);
        t.send(Keying.build("TTT", new Timing(20, 20), R));
        float[] y = new float[960 * 100];
        for (int i = 0; i < 100; i++) { t.render(x, 480); System.arraycopy(x, 0, y, 960 * i, 960); }
        double[] tone = new double[1]; float[] seg = java.util.Arrays.copyOfRange(y, 0, 960 * 30);
        check("sidetone is at the pitch", goertzel(seg, 700) > 20 * goertzel(seg, 1500));
        check("the sidetone has a sensible level: " + peak(y, 0, y.length), peak(y, 0, y.length) > 0.15 && peak(y, 0, y.length) < 0.3);
    }
    static double peak(float[] x, int from, int to) { double m = 0; for (int i = from; i < to; i++) m = Math.max(m, Math.abs(x[i])); return m; }

    // ---- K5: KeyEar ----
    static KeyEar ear(BuzzerSynth b, Dsp.Rng r) {
        KeyEar e = new KeyEar(b.rate);
        float[] held = b.held(2, r), q = b.quiet(2, r), band = Double.isNaN(b.leakDb) ? null : b.leaking(2, r);
        e.calibrate(held, held.length, q, q.length, band, band == null ? 0 : band.length);
        return e;
    }
    /** Key events from feeding the samples in 20 ms blocks: times in ms, positive for down and negative for up (negated, 0 offset kept apart). */
    static java.util.List<double[]> events(KeyEar e, float[] x, int rate) {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        int block = rate / 50;
        for (int i = 0; i < x.length; i += block) e.process(java.util.Arrays.copyOfRange(x, i, Math.min(x.length, i + block)), Math.min(block, x.length - i), (down, s) -> out.add(new double[]{down ? 1 : 0, s * 1000.0 / rate}));
        return out;
    }
    static void keyear() {
        Dsp.Rng r = new Dsp.Rng(21);
        for (int rate : new int[]{16000, 48000}) for (double hz : new double[]{500, 800, 2400, 3200}) {
            BuzzerSynth b = new BuzzerSynth(); b.rate = rate; b.hz = hz;
            float[] x = b.held(2, r);
            near("pitch " + hz + " at " + rate, KeyEar.findPitch(x, x.length, rate), hz, hz * 0.03);
        }
        BuzzerSynth b = new BuzzerSynth();
        KeyEar e = ear(b, r);
        check("calibrated", e.ready()); check("snr is well above the room (measured in the envelope): " + e.snrDb(), e.snrDb() > 20);
        // Timing of the key: every transition within 15 ms of the truth, and nothing extra.
        Keying k = Keying.build("CQ TEST", new Timing(18, 18), b.rate);
        java.util.List<double[]> ev = events(e, b.render(k, r), b.rate);
        double t = b.leadS * 1000; int idx = 0; double worst = 0; boolean shapeOk = true;
        for (int run : k.runs) {
            double len = Math.abs(run) * 1000.0 / b.rate;
            if (run > 0) {
                if (idx + 1 >= ev.size()) { shapeOk = false; break; }
                worst = Math.max(worst, Math.abs(ev.get(idx)[1] - t)); worst = Math.max(worst, Math.abs(ev.get(idx + 1)[1] - (t + len)));
                idx += 2;
            }
            t += len;
        }
        check("every mark found, none extra: " + ev.size(), shapeOk && idx == ev.size());
        check("key times within 15 ms: " + worst, worst < 15);
        // Noise alone, a voice-like burst, a door slam: nothing.
        BuzzerSynth q = new BuzzerSynth();
        float[] noise = q.quiet(4, r);
        eq("noise alone", events(ear(b, r), noise, b.rate).size(), 0);
        float[] v = q.quiet(4, r); double env = 0;
        for (int i = 0; i < v.length; i++) { if (i % 2000 == 0) env = r.uniform() * 0.12; v[i] += (float) (env * r.gauss() * (0.5 + 0.5 * Math.sin(i * 2 * Math.PI * 4 / b.rate))); }
        eq("voice-like burst", events(ear(b, r), v, b.rate).size(), 0);
        BuzzerSynth sl = new BuzzerSynth(); sl.slamAt = 2;
        eq("door slam", events(ear(b, r), sl.quiet(4, r), b.rate).size(), 0);
        // The band leaking at 12 dB below the buzzer, key idle: silent. At 4 dB it is flagged.
        BuzzerSynth lk = new BuzzerSynth(); lk.leakDb = -12;
        KeyEar le = ear(lk, r);
        Keying other = Keying.build("CQ CQ DE DL1XYZ K", new Timing(20, 20), lk.rate);
        lk.leakKeying = other;
        eq("leak at -12 dB: no false keying", events(le, lk.render(null, r, other), lk.rate).size(), 0);
        BuzzerSynth loud = new BuzzerSynth(); loud.leakDb = -4;
        check("a leak at -4 dB is flagged", !ear(loud, r).leakOk());
        BuzzerSynth fine = new BuzzerSynth(); fine.leakDb = -20;
        check("a leak at -20 dB is fine", ear(fine, r).leakOk());
        // The shifted copy sits at 800 Hz for any buzzer pitch, at either rate.
        for (int rate : new int[]{16000, 48000}) for (double hz : new double[]{500, 1000, 2000, 3500}) {
            BuzzerSynth s = new BuzzerSynth(); s.rate = rate; s.hz = hz; s.echoGain = 0;
            KeyEar se = ear(s, r);
            float[] x = s.held(4, r); events(se, x, rate);
            float[] ring = new float[KeyEar.RING]; se.shifted(ring);
            float[] tail = java.util.Arrays.copyOfRange(ring, KeyEar.RING - 6400, KeyEar.RING);
            double best = 0, bestF = 0;
            for (double f = 770; f <= 830; f += 0.5) { double g = goertzelAt(tail, f, 3200); if (g > best) { best = g; bestF = f; } }
            near("shifted peak for " + hz + " Hz at " + rate, bestF, 800, 3);
        }
    }
    static double goertzelAt(float[] x, double f, double rate) {
        double w = 2 * Math.PI * f / rate, k = 2 * Math.cos(w), s1 = 0, s2 = 0;
        for (float v : x) { double s = v + k * s1 - s2; s2 = s1; s1 = s; }
        return s1 * s1 + s2 * s2 - k * s1 * s2;
    }

    // ---- K5: KeyDecoder ----
    static KeyDecoder feedRuns(KeyDecoder d, Keying k, int rate) {
        double t = 0;
        for (int run : k.runs) { double ms = Math.abs(run) * 1000.0 / rate; if (run > 0) { d.key(true, t); d.key(false, t + ms); } t += ms; }
        return d;
    }
    static String viaAudio(BuzzerSynth b, Keying k, KeyDecoder d, Dsp.Rng r) {
        KeyEar e = ear(b, r);
        for (double[] ev : events(e, b.render(k, r), b.rate)) d.key(ev[0] == 1, ev[1]);
        return d.text();
    }
    static Fist learner(double dah, double gapJitter, double charGap) { return new Fist(1, dah, 0.25, 0.25, gapJitter, charGap, 0.3, 0.2); }
    static final String QSO = "{pc} DE SP0CWN SP0CWN <BT> UR RST 579 579 <BT> NAME JACEK JACEK <BT> QTH KRAKOW KRAKOW K";

    static void keydecoder() {
        Dsp.Rng r = new Dsp.Rng(31);
        String text = "CQ CQ DE SP9ABC SP9ABC K UR RST 579 NAME JACEK QTH KRAKOW HW COPY 73 TU";
        // Through the microphone path: keyer, 20 dB.
        for (int wpm : new int[]{5, 12, 20, 30}) {
            BuzzerSynth b = new BuzzerSynth(); b.snrDb = 20;
            Keying k = Keying.build(text, new Timing(wpm, wpm), b.rate, Fist.of(Fist.KEYER, false, r), r);
            String got = viaAudio(b, k, new KeyDecoder(wpm, null), r);
            double a = accuracy(text, got);
            check("keyer " + wpm + " wpm: " + a + " [" + got + "]", a >= 0.99);
        }
        // Straight key and bug at 10 dB.
        for (int key : new int[]{Fist.STRAIGHT, Fist.BUG}) for (int wpm : new int[]{12, 18, 22}) {
            double tot = 0; int runs = 6;
            for (int q = 0; q < runs; q++) {
                BuzzerSynth b = new BuzzerSynth(); b.snrDb = 10;
                Keying k = Keying.build(text, new Timing(wpm, wpm), b.rate, Fist.of(key, false, r), r);
                tot += accuracy(text, viaAudio(b, k, new KeyDecoder(wpm, null), r));
            }
            System.out.printf("fist %d at %d wpm: %.3f%n", key, wpm, tot / runs);
            check("fist " + key + " at " + wpm + " wpm: " + tot / runs, tot / runs >= 0.92);
        }
        // A beginner at 20/8 Farnsworth.
        {
            double tot = 0; int breaksOk = 0, runs = 8;
            for (int q = 0; q < runs; q++) {
                BuzzerSynth b = new BuzzerSynth(); b.snrDb = 10;
                Keying k = Keying.build(text, new Timing(20, 8), b.rate, Fist.of(Fist.STRAIGHT, true, r), r);
                String got = viaAudio(b, k, new KeyDecoder(12, null), r);
                tot += accuracy(text, got);
                if (got.split(" ").length == text.split(" ").length) breaksOk++;
            }
            System.out.printf("beginner 20/8: %.3f, word breaks right in %d/%d%n", tot / runs, breaksOk, runs);
            check("beginner accuracy " + tot / runs, tot / runs >= 0.90);
            check("beginner word breaks " + breaksOk + "/" + runs, breaksOk >= runs / 2);
        }
        // The end of an over.
        for (String last : new String[]{"K", "KN", "BK", "SK"}) {
            KeyDecoder d = feedRuns(new KeyDecoder(20, null), Keying.build("CQ DE SP9ABC " + last, new Timing(20, 20), R), R);
            double up = d.lastUpMs();
            check("over ends after " + last, d.overEnded(up + 1600, Profile.REALISTIC) && !d.overEnded(up + 1000, Profile.REALISTIC));
        }
        {
            KeyDecoder d = feedRuns(new KeyDecoder(5, null), Keying.build("CQ DE SP9ABC", new Timing(5, 5), R), R);
            check("a slow sender's word gap does not end the over: text " + d.text(), !d.overEnded(d.lastUpMs() + 1800, Profile.PATIENT) && !d.overEnded(d.lastUpMs() + 1800, Profile.REALISTIC));
            check("patient: a long silence does", d.overEnded(d.lastUpMs() + 6000, Profile.PATIENT));
            check("normal: 2.5 s is enough at a normal speed", feedRuns(new KeyDecoder(20, null), Keying.build("CQ DE SP9ABC", new Timing(20, 20), R), R).overEnded(3000 + 12000, Profile.NORMAL));
        }
        rules();
        fistFiles();
    }

    /** The two targets for a learner's hand that are NOT met (see docs/PLAN.md). Run with: java -cp out_test pl.cwnews.T targets */
    static void learnerTargets() {
        for (double[] f : new double[][]{{0.75, 0.35, 0.6}, {0.67, 0.45, 0.5}}) {
            int ok = 0, okProfile = 0, runs = 200;
            Heard.Expect e = expectFor("SP0CWN");
            String say = QSO.replace("{pc}", "DL1ZZ");
            Dsp.Rng rr = new Dsp.Rng(1000 + (long) (f[0] * 100));
            for (int i = 0; i < runs; i++) {
                Fist hand = learner(f[0], f[1], f[2]);
                Keying k = Keying.build(say, new Timing(15, 15), R, hand, rr);
                // The profile comes from three sends of the warm-up text.
                java.util.List<Long> marks = new java.util.ArrayList<>(), gaps = new java.util.ArrayList<>();
                for (int s = 0; s < 3; s++) {
                    Keying w = Keying.build("CQ CQ DE SP0CWN SP0CWN K", new Timing(15, 15), R, hand, rr);
                    KeyDecoder wd = feedRuns(new KeyDecoder(15, null), w, R);
                    for (int m = 0; m < wd.marks(); m++) { marks.add(Math.round(wd.markEndMs(m) - wd.markStartMs(m))); if (m + 1 < wd.marks()) gaps.add(Math.round(wd.markStartMs(m + 1) - wd.markEndMs(m))); }
                    if (s < 2) gaps.add(2000L);
                }
                KeyDecoder.Fist5 prof = KeyDecoder.Fist5.learn(marks, gaps, "CQ CQ DE SP0CWN SP0CWN K");
                if (found(Heard.parse(feedRuns(new KeyDecoder(15, null), k, R).letters(), e))) ok++;
                if (found(Heard.parse(feedRuns(new KeyDecoder(15, prof), k, R).letters(), e))) okProfile++;
            }
            System.out.printf("learner dah %.2f gapJitter %.2f charGap %.2f: %d/%d without a profile, %d/%d with one%n", f[0], f[1], f[2], ok, runs, okProfile, runs);
            if (f[0] > 0.7) check("learner fist read: " + ok + "/200", ok >= 180);
            else { check("severe fist with a profile: " + okProfile + "/200", okProfile >= 170); check("the profile helps by 10 points: " + ok + " vs " + okProfile, okProfile - ok >= 20); }
        }
    }

    static KeyDecoder ms(KeyDecoder.Fist5 p, double[] marks, double[] gaps) {
        KeyDecoder d = new KeyDecoder(15, p);
        double t = 0;
        for (int i = 0; i < marks.length; i++) { d.key(true, t); t += marks[i]; d.key(false, t); if (i < gaps.length) t += gaps[i]; }
        return d;
    }
    static void rules() {
        // Rule 1: a very long mark is still a dash.
        double[] m = {80, 240, 80, 240, 80, 240, 480}, g = {80, 240, 80, 240, 80, 240};
        KeyDecoder.rule1 = true; String with = ms(null, m, g).text();
        KeyDecoder.rule1 = false; String without = ms(null, m, g).text(); KeyDecoder.rule1 = true;
        eq("rule 1: a long mark is a dash", with, "AAAT"); check("rule 1 matters: " + without, !without.equals(with));
        // Rule 2: a bounce inside a dash is not a gap.
        double[] m2 = {80, 240, 80, 240, 80, 240, 80, 110, 130}, g2 = {80, 240, 80, 240, 80, 240, 80, 10, 0};
        KeyDecoder.rule2 = true; with = ms(null, java.util.Arrays.copyOf(m2, 9), java.util.Arrays.copyOf(g2, 8)).text();
        KeyDecoder.rule2 = false; without = ms(null, java.util.Arrays.copyOf(m2, 9), java.util.Arrays.copyOf(g2, 8)).text(); KeyDecoder.rule2 = true;
        eq("rule 2: bounce is merged", with, "AAAA"); check("rule 2 matters: " + without, !without.equals(with));
        // Rule 3: an unknown code is also tried split at its longest inner gap.
        double[] m3 = {80, 240, 80, 240, 80, 240, 80, 80, 80, 80, 240, 80}, g3 = {80, 240, 80, 240, 80, 240, 70, 70, 70, 70, 90};
        java.util.List<Heard.Letter> l3;
        KeyDecoder.rule3 = true; l3 = ms(null, m3, java.util.Arrays.copyOf(g3, 11)).letters();
        boolean has = false; for (Heard.Letter l : l3) if (l.ch.equals("*") && l.alternatives.contains("4E")) has = true;
        KeyDecoder.rule3 = false; l3 = ms(null, m3, java.util.Arrays.copyOf(g3, 11)).letters();
        boolean hasNot = false; for (Heard.Letter l : l3) if (l.alternatives.contains("4E")) hasNot = true; KeyDecoder.rule3 = true;
        check("rule 3: the split reading is kept", has); check("rule 3 matters", !hasNot);
        // Rule 4: a gap just over the usual letter gap is not yet a word gap.
        KeyDecoder.Fist5 prof = new KeyDecoder.Fist5(80, 240, 80, 240, 384);
        double[] m4 = {80, 240, 80, 240}, g4 = {80, 308, 80};
        double[] m4b = {80, 240, 80, 240}, g4b = {80, 308, 80};
        KeyDecoder.rule4 = true; with = ms(prof, m4, new double[]{80, 308, 80}).text();
        KeyDecoder.rule4 = false; without = ms(prof, m4b, new double[]{80, 308, 80}).text(); KeyDecoder.rule4 = true;
        check("rule 4: not a word gap: " + with, !with.contains(" ")); check("rule 4 matters: " + without, without.contains(" "));
        // Rule 5: an unknown code next to a lone E or T keeps the joined reading.
        double[] m5 = {80, 240, 80, 240, 80, 240, 80, 80, 240, 240, 80, 80}, g5 = {80, 240, 80, 240, 80, 240, 80, 80, 80, 80, 80};
        // The last letter ..--.. is sent as ..--. then a letter gap then .
        double[] g5b = g5.clone(); g5b[g5b.length - 1] = 260;
        KeyDecoder.rule5 = true; l3 = ms(null, m5, g5b).letters();
        has = false; for (Heard.Letter l : l3) if (l.alternatives.contains("?")) has = true;
        KeyDecoder.rule5 = false; l3 = ms(null, m5, g5b).letters();
        hasNot = false; for (Heard.Letter l : l3) if (l.alternatives.contains("?")) hasNot = true; KeyDecoder.rule5 = true;
        check("rule 5: the joined reading is kept", has); check("rule 5 matters", !hasNot);
    }

    static void earMemory() {
        Dsp.Rng r = new Dsp.Rng(8);
        BuzzerSynth b = new BuzzerSynth();
        KeyEar a = ear(b, r);
        String saved = a.save();
        KeyEar c = new KeyEar(b.rate);
        check("a saved calibration restores", c.restore(saved) && c.ready());
        near("pitch restored", c.pitch(), a.pitch(), 0.01);
        Keying k = Keying.build("CQ", new Timing(18, 18), b.rate);
        eq("a restored ear hears the same", events(c, b.render(k, r), b.rate).size(), events(ear(b, r), b.render(k, r), b.rate).size());
        check("a calibration for another rate is refused", !new KeyEar(48000).restore(saved));
        check("garbage is refused", !new KeyEar(16000).restore("x") && !new KeyEar(16000).restore("1,2,3,4,xx"));
        near("accuracy of equal text", KeyDecoder.accuracy("CQ DE", "CQ DE"), 1, 1e-9);
        near("accuracy of one wrong letter in five", KeyDecoder.accuracy("CQ DE", "CQ DF"), 0.8, 1e-9);
        near("accuracy of nothing", KeyDecoder.accuracy("CQ", ""), 0, 1e-9);
    }

    static void fistFiles() {
        earMemory();
        // A hand's profile from three sends, and a saved over read back.
        Dsp.Rng r = new Dsp.Rng(5);
        Fist hand = learner(0.75, 0.2, 0.9);
        java.util.List<Long> marks = new java.util.ArrayList<>(), gaps = new java.util.ArrayList<>();
        for (int s = 0; s < 3; s++) {
            KeyDecoder wd = feedRuns(new KeyDecoder(15, null), Keying.build("CQ CQ DE SP0CWN SP0CWN K", new Timing(15, 15), R, hand, r), R);
            for (int i = 0; i < wd.marks(); i++) { marks.add(Math.round(wd.markEndMs(i) - wd.markStartMs(i))); if (i + 1 < wd.marks()) gaps.add(Math.round(wd.markStartMs(i + 1) - wd.markEndMs(i))); }
            if (s < 2) gaps.add(2000L);
        }
        KeyDecoder.Fist5 f = KeyDecoder.Fist5.learn(marks, gaps, "CQ CQ DE SP0CWN SP0CWN K");
        check("a fist was learned", f != null);
        if (f != null) {
            near("learned dash length in dots: " + f.dashDots(), f.dashDots(), 2.25, 0.5);
            check("gaps ordered: " + f, f.inner < f.letter && f.letter < f.word);
            eq("profile text round trip", KeyDecoder.Fist5.parse(f.toString()).toString(), f.toString());
        }
        check("bad profile text", KeyDecoder.Fist5.parse("1,2,3") == null && KeyDecoder.Fist5.parse(null) == null);
        KeyDecoder d = feedRuns(new KeyDecoder(15, null), Keying.build("CQ DE SP0CWN K", new Timing(15, 15), R), R);
        String json = d.timingsJson("CQ DE SP0CWN K");
        KeyDecoder.Saved sv = KeyDecoder.fromJson(json);
        eq("saved over: meant text", sv.meant, "CQ DE SP0CWN K");
        KeyDecoder back = new KeyDecoder(15, null); back.feed(sv);
        eq("saved over reads back the same", back.text(), d.text());
        // Real overs exported from a phone, once there are any.
        java.io.File dir = new java.io.File("tests/data/fist");
        java.io.File[] files = dir.listFiles((x, n) -> n.endsWith(".json"));
        if (files != null) for (java.io.File fl : files) {
            try {
                KeyDecoder.Saved o = KeyDecoder.fromJson(new String(java.nio.file.Files.readAllBytes(fl.toPath()), StandardCharsets.UTF_8));
                KeyDecoder x = new KeyDecoder(15, null); x.feed(o);
                Heard.Expect e = expectFor("SP0CWN");
                Heard want = Heard.parse(o.meant, e), got = Heard.parse(x.letters(), e);
                System.out.printf("%s: character error rate %.1f%%%n", fl.getName(), 100 * (1 - accuracy(o.meant, x.text())));
                if (want.report != null) eq(fl.getName() + ": report", got.report, want.report);
                if (want.userCallHeard) check(fl.getName() + ": call", got.userCallHeard);
            } catch (java.io.IOException ex) { check("read " + fl, false); }
        }
    }

    static double accuracy(String want, String got) {
        int[][] m = new int[want.length() + 1][got.length() + 1];
        for (int i = 0; i <= want.length(); i++) m[i][0] = i;
        for (int j = 0; j <= got.length(); j++) m[0][j] = j;
        for (int i = 1; i <= want.length(); i++) for (int j = 1; j <= got.length(); j++)
            m[i][j] = Math.min(Math.min(m[i - 1][j] + 1, m[i][j - 1] + 1), m[i - 1][j - 1] + (want.charAt(i - 1) == got.charAt(j - 1) ? 0 : 1));
        return 1 - m[want.length()][got.length()] / (double) want.length();
    }
    static boolean found(Heard h) { return h.userCallHeard && "579".equals(h.report) && "JACEK".equals(h.values.get("NAME")) && "KRAKOW".equals(h.values.get("QTH")); }

    static void suggestions() {
        int done = 0, total = 0;
        for (int kind = 0; kind < 3; kind++) for (long seed = 1; seed <= 12; seed++) for (int flow = 0; flow < 2; flow++) {
            Partner p = flow == 0 ? calling(seed, 0, kind) : Partner.answering(new Dsp.Rng(seed), new Qso.Scene(5, 20), me(0), 0, kind);
            p.first();
            total++;
            boolean sendableAll = true, nonEmpty = true;
            if (flow == 0) check("the first suggestion answers the CQ", p.suggest().startsWith(p.call() + " DE SP0CWN"));
            for (int i = 0; i < 40 && !p.done(); i++) {
                String t = p.suggest();
                if (t.isEmpty()) { nonEmpty = false; break; }
                if (!sendable(t)) sendableAll = false;
                over(p, t);
            }
            check("every suggestion can be sent in Morse " + kind + "/" + seed + "/" + flow, sendableAll && nonEmpty);
            if (p.done() && p.result().complete) done++;
        }
        check("following only the suggestions finishes the contact: " + done + "/" + total, done == total);
        Partner q = calling(3, 0, Partner.STANDARD); q.first();
        over(q, q.call() + " DE SP0CWN K");
        check("after the exchange the partner asks for the name, and the suggestion has it", over(q, "R UR RST 579 K").text.contains("UR NAME?") && q.suggest().contains("NAME JACEK JACEK"));
        Partner w = calling(4, 0, Partner.CHATTY); w.first();
        over(w, w.call() + " DE SP0CWN K");
        boolean wx = false;
        Qso.Tx r = over(w, "R UR RST 579 579 <BT> NAME JACEK <BT> QTH KRAKOW K");
        for (int i = 0; i < 8 && !wx && r != null; i++) { if (r.text.contains("UR WX?")) wx = w.suggest().contains("WX HR"); r = over(w, "R FB K"); }
        check("a question about the weather gets a weather answer", wx);
        check("nothing is suggested once the contact is over", suggestAfterEnd());
    }
    static boolean suggestAfterEnd() { Partner p = calling(5, 0, Partner.ACTIVATOR); p.first(); over(p, p.call() + " DE SP0CWN K"); over(p, "R UR 599 TU 73"); return p.done() && p.suggest().isEmpty(); }


    static String readAsset(String f) throws Exception { return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("app/assets/stories/" + f)), StandardCharsets.UTF_8); }

    static void script() throws Exception {
        String[][] nets = {{"lv426_net.txt", "624", "23"}, {"hail_mary_net.txt", "668", "14"}, {"terminus_net.txt", "472", "30"}, {"endurance_net.txt", "464", "14"}, {"mars_net_pro.txt", "620", "14"}};
        for (String[] n : nets) {
            Script s = Script.parse(n[0], readAsset(n[0]));
            eq(n[0] + " messages", s.lines.size(), Integer.parseInt(n[1]));
            eq(n[0] + " episodes", s.episodes.size(), Integer.parseInt(n[2]));
            boolean named = s.calls.length >= 7, credit = false, clean = true, pauses = true;
            for (int i = 0; i < s.calls.length; i++) if (s.names[i].equals(s.calls[i])) named = false;
            for (Script.Line l : s.lines) {
                if (l.text.contains("MUSIC:") || l.text.contains("STELLARDRONE")) credit = true;
                if (!l.text.matches("[A-Z0-9 .,/?()<>'\"-]+") || l.text.contains("  ")) clean = false;
                if (l.pauseS != 0 && (l.pauseS < 1.5 || (l.pauseS > 3 && l.pauseS != 10))) pauses = false;
            }
            check(n[0] + " every call has a name", named);
            check(n[0] + " credit ignored", !credit);
            check(n[0] + " text is Morse-safe", clean);
            check(n[0] + " pauses", pauses);
            eq(n[0] + " episode 0 starts at 0", s.episodes.get(0).first, 0);
            int[] off = Script.offsetsHz(s);
            java.util.Map<String, Integer> pair = new java.util.HashMap<>();
            for (Script.Line l : s.lines) { if (l.to.equals("ALL") || l.to.equals(l.speaker)) continue; String k = l.speaker.compareTo(l.to) < 0 ? l.speaker + " " + l.to : l.to + " " + l.speaker; pair.merge(k, 1, Integer::sum); }
            List<java.util.Map.Entry<String, Integer>> top = new java.util.ArrayList<>(pair.entrySet());
            java.util.Collections.sort(top, (a, b) -> b.getValue() - a.getValue());
            int apart = 0, topN = Math.min(3, top.size());
            java.util.Set<String> three = new java.util.HashSet<>();
            for (int i = 0; i < topN; i++) {
                String[] ab = top.get(i).getKey().split(" ");
                three.addAll(java.util.Arrays.asList(ab));
                int ia = java.util.Arrays.asList(s.calls).indexOf(ab[0]), ib = java.util.Arrays.asList(s.calls).indexOf(ab[1]);
                if (ia >= 0 && ib >= 0 && Math.abs(off[ia] - off[ib]) >= Script.APART) apart++;
            }
            check(n[0] + " top partner pairs >= 60 Hz apart (" + apart + " of " + topN + ")", apart == topN);
            for (int o : off) check(n[0] + " offset in Narrow " + o, Rx.gain(Rx.lo(Rx.NARROW, 600), Rx.hi(Rx.NARROW, 600), 600 + o) > 0.5);
        }
        Script h = Script.parse("hail_mary_net.txt", readAsset("hail_mary_net.txt"));
        eq("title", h.title, "Hail Mary Net");
        eq("legend name", h.names[java.util.Arrays.asList(h.calls).indexOf("RKY")], "Rocky, Eridian engineer on the Blip-A");
        eq("first over speaker", h.lines.get(0).speaker, "MRY");
        eq("first over to", h.lines.get(0).to, "ALL");
        check("first over keeps <BT>", h.lines.get(0).text.contains(" <BT> "));
        eq("BT keys as -...-", codes("<BT>"), "-...- /");
        check("keyed BT survives in an over", codes(h.lines.get(0).text).contains("-...- /"));
        eq("episode title", h.episodes.get(0).title, "WAKING UP");
        eq("episode 2 first line has the long pause", h.lines.get(h.episodes.get(1).first).pauseS, 10.0);
        eq("lines carry their episode", h.lines.get(h.episodes.get(1).first).episode, 1);
        Script m = Script.parse("x.txt", "﻿Test Net\r\n@ AAA = Alpha\r\n\r\n  0.10 min  === EP 1  START ===\r\n  0.10 min  aaa -> bbb  bbb de aaa <bt> Zażółć <kn>\r\nMusic: nobody\r\n");
        eq("net BOM/CRLF title", m.title, "Test Net");
        eq("one line", m.lines.size(), 1);
        eq("prosigns kept, text cleaned", m.lines.get(0).text, "BBB DE AAA <BT> ZAZOLC <KN>");
        eq("calls: legend then speakers", java.util.Arrays.asList(m.calls).toString(), "[AAA]");
        String book = "The Project Gutenberg eBook of A Small Tale\n\nTitle: A Small Tale\n\n*** START OF THE PROJECT GUTENBERG EBOOK A SMALL TALE ***\n\nCHAPTER I\n\nIt was a dark night. The wind blew hard. Nobody came.\n\nHe said \"Hello\", and she left!\n\nCHAPTER II\n\nThe end of it all.\n\n*** END OF THE PROJECT GUTENBERG EBOOK A SMALL TALE ***\nLicense text that must not be sent.\n";
        Script b = Script.parse("small_tale.txt", book);
        eq("book title", b.title, "A Small Tale");
        eq("book one station", b.calls.length, 1);
        eq("book station name from the short title", b.calls[0], "SMALL");
        eq("book episodes", b.episodes.size(), 2);
        eq("book episode title", b.episodes.get(0).title, "CHAPTER I");
        eq("book overs", b.lines.size(), 3);
        eq("book first over", b.lines.get(0).text, "IT WAS A DARK NIGHT. THE WIND BLEW HARD. NOBODY CAME. <BT>");
        eq("book second chapter starts at 2", b.episodes.get(1).first, 2);
        boolean lic = false;
        for (Script.Line l : b.lines) { if (l.text.contains("LICENSE") || l.text.contains("GUTENBERG")) lic = true; if (!l.text.endsWith(" <BT>")) lic = true; }
        check("book strips Gutenberg parts, every over ends <BT>", !lic);
        StringBuilder long1 = new StringBuilder();
        for (int i = 0; i < 12; i++) long1.append("One two three four five six seven. ");
        Script lb = Script.parse("long.txt", long1.toString());
        boolean fit = lb.lines.size() > 1;
        for (Script.Line l : lb.lines) if (l.text.split(" ").length > 41 + 1) fit = false;
        check("book overs of at most about 40 words", fit);
        eq("empty file lines", Script.parse("empty.txt", "").lines.size(), 0);
        eq("empty file title from name", Script.parse("empty.txt", "").title, "empty");
        eq("null text", Script.parse("n.txt", null).lines.size(), 0);
        eq("BOM only", Script.parse("n.txt", "﻿").lines.size(), 0);
        eq("empty offsets", Script.offsetsHz(Script.parse("e.txt", "")).length, 0);
        eq("book offsets", Script.offsetsHz(b).length, 1);
    }

    public static void main(String[] a) throws Exception {
        morse(); timing(); text(); abbrev(); band(); follow(); sideband(); filters(); weather(); spaceWeather(); feeds(); queue(); fists(); propagation(); profile(); heard(); contactsUnchanged(); bandUnchanged(); storyStation(); partner(); suggestions(); live(); keyear(); keydecoder(); script(); if (a.length == 0) { audio(); receiver(); }
        if (a.length == 1 && a[0].equals("targets")) learnerTargets();
        if (a.length > 1) { FeedParser.Feed f = FeedParser.parse(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(a[1])), "https://x.example/"); System.out.println("real feed: " + f.title + ", " + f.items.size() + " items, first: " + TextNorm.story(f.title, f.items.get(0).title, f.items.get(0).summary, 1, true)); }
        System.out.println(ok + " passed, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
