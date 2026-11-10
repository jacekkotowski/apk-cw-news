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
        morse(); timing(); text(); abbrev(); feeds(); queue(); if (a.length == 0) audio();
        if (a.length > 1) { FeedParser.Feed f = FeedParser.parse(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(a[1])), "https://x.example/"); System.out.println("real feed: " + f.title + ", " + f.items.size() + " items, first: " + TextNorm.story(f.title, f.items.get(0).title, f.items.get(0).summary, 1, true)); }
        System.out.println(ok + " passed, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
