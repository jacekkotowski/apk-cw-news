package pl.cwnews;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(16, 34, 44), LIST = 60, PICK_STORY = 7;
    private WebView web;
    private SharedPreferences prefs;
    private Db db;
    // The live contact with a partner on the band, kept here so that it can be logged when it ends.
    private final Object liveLock = new Object();
    private Partner contact;
    private double contactKhz;
    private String summary = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("cw", MODE_PRIVATE);
        CwService.dial = prefs.getFloat("dial", (float) Band.NEWS);
        CwService.rit = 0;   // RIT is no longer offered: Net puts the dial on the station
        prefs.edit().putInt("realwx", 0).apply();
        db = Db.get(this);
        web = new WebView(this);
        web.setBackgroundColor(BG);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setBlockNetworkLoads(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        // A WebView ignores its own padding, so the system-bar insets go on a parent layout.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        root.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((View v, WindowInsets i) -> {
                android.graphics.Insets p = i.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime() | WindowInsets.Type.displayCutout());
                v.setPadding(p.left, p.top, p.right, p.bottom);
                return WindowInsets.CONSUMED;
            });
        } else {
            root.setFitsSystemWindows(true);
        }
        setContentView(root);
        web.loadUrl("file:///android_asset/index.html");
        RefreshJob.schedule(this, prefs.getInt("refresh", 30));
    }

    /** The user's own station from the saved settings. */
    private Profile profile() {
        Profile d = Profile.defaults();
        return new Profile(prefs.getString("mycall", d.call), prefs.getString("myname", d.name), prefs.getString("myqth", d.qth), prefs.getString("myrig", d.rig), prefs.getString("myant", d.ant),
            prefs.getInt("power", d.power), prefs.getInt("sendwpm", d.sendWpm), prefs.getInt("manners", d.manners), prefs.getInt("sendwith", d.sendWith));
    }

    /** Logs the live contact, if any user over was made, and prepares the short summary the page shows. No score. */
    private void finishContact() {
        synchronized (liveLock) {
            Partner p = contact;
            if (p == null) return;
            contact = null;
            Partner.Contact c = p.result();
            if (c.overs > 0) db.addLog(System.currentTimeMillis(), c.call, c.name, c.qth, c.rstSent, c.rstRcvd, contactKhz, c.complete, c.overs);
            String who = c.call + (c.name.isEmpty() ? "" : " (" + c.name + (c.qth.isEmpty() ? "" : ", " + c.qth) + ")");
            summary = c.complete
                ? "Contact with " + who + ". You sent " + (c.rstSent.isEmpty() ? "no report" : c.rstSent) + " and received " + (c.rstRcvd.isEmpty() ? "none" : c.rstRcvd) + ". Thank you for the contact."
                : c.overs == 0 ? "" : "Contact with " + who + " ended early. That is fine; it is in the log as not finished.";
        }
    }

    private boolean online() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network n = cm.getActiveNetwork();
        NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
        return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void service(String action, long id) {
        Intent i = new Intent(this, CwService.class).setAction(action).putExtra("id", id);
        startForegroundService(i);
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
    }


    // ---- the key, heard through the microphone ----

    /**
     * Listens to the buzzer of the user's key, only while the page asks for it and the app is on screen. The sound is
     * analysed here and never stored or sent. It feeds the receiver's ducking, and at the end of an over gives the
     * decoded text to the live partner.
     */
    private final class Mic implements Runnable {
        static final int RATE = 16000, BLOCK = 320;
        private AudioRecord rec;
        private Thread thread;
        private volatile boolean run;
        final KeyEar ear = new KeyEar(RATE);
        final KeyDecoder[] decoder = {new KeyDecoder(15, null)};
        private final float[][] cap = new float[3][];
        private final int[] capN = new int[3];
        private volatile int capKind = -1, capWant;
        volatile String heard = "", status = "";
        volatile boolean keyDown;
        private volatile long warmEnd;
        volatile boolean learning, calibrating;
        private boolean bandFresh;
        private volatile String warmText = "";
        private long samples;
        private String lastJson = "", lastText = "";
        private int quietBlocks;

        boolean running() { return run; }

        synchronized boolean start() {
            if (run) return true;
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { status = "Microphone permission is needed."; return false; }
            try {
                String raw = ((AudioManager) getSystemService(AUDIO_SERVICE)).getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED);
                int source = "true".equals(raw) ? MediaRecorder.AudioSource.UNPROCESSED : MediaRecorder.AudioSource.VOICE_RECOGNITION;
                int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                rec = new AudioRecord(source, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, RATE));
                if (rec.getState() != AudioRecord.STATE_INITIALIZED) { rec.release(); rec = null; status = "The microphone could not be opened."; return false; }
                rec.startRecording();
            } catch (RuntimeException e) { status = "The microphone could not be opened."; return false; }
            String saved = prefs.getString("keycal", "");
            if (!saved.isEmpty()) ear.restore(saved);
            decoder[0] = new KeyDecoder(prefs.getInt("sendwpm", 15), KeyDecoder.Fist5.parse(prefs.getString("fist5", "")));
            run = true; samples = 0; status = "";
            thread = new Thread(this, "mic");
            thread.start();
            runOnUiThread(() -> getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            return true;
        }

        synchronized void stop() {
            run = false;
            if (thread != null) { try { thread.join(500); } catch (InterruptedException ignored) { } thread = null; }
            if (rec != null) { try { rec.stop(); } catch (RuntimeException ignored) { } rec.release(); rec = null; }
            CwService.Engine_key(false);
            keyDown = false;
            runOnUiThread(() -> getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        }

        /** Records seconds of sound for one calibration step: 0 key held, 1 room quiet, 2 band playing. */
        String record(int kind, double seconds) {
            if (!start()) return status;
            if (kind == 0) bandFresh = false;
            if (kind == 2) bandFresh = true;
            cap[kind] = new float[(int) (seconds * RATE)]; capN[kind] = 0; capWant = cap[kind].length; capKind = kind;
            return "";
        }
        boolean recording() { return capKind >= 0; }
        boolean have(int kind) { return cap[kind] != null && capN[kind] >= cap[kind].length; }
        boolean calibrate() {
            if (!have(0) || !have(1)) return false;
            calibrating = true;
            boolean ok = ear.calibrate(cap[0], capN[0], cap[1], capN[1], bandFresh && have(2) ? cap[2] : null, bandFresh && have(2) ? capN[2] : 0);
            if (ok) prefs.edit().putString("keycal", ear.save()).apply();
            calibrating = false;
            return ok;
        }
        void warm(boolean on) { warmEnd = on ? System.currentTimeMillis() + 60000 : 0; warmText = ""; decoder[0].reset(); heard = ""; }

        @Override public void run() {
            short[] blk = new short[BLOCK];
            float[] f = new float[BLOCK];
            KeyEar.Listener l = (down, sample) -> {
                keyDown = down;
                Band b = CwService.band;
                // While the partner is sending, what the user keys is not an answer: it only mutes the receiver.
                if (b == null || !b.liveSending()) decoder[0].key(down, sample * 1000.0 / RATE);
                CwService.Engine_key(down);
                heard = decoder[0].text();
            };
            while (run) {
                int n = rec.read(blk, 0, BLOCK);
                if (n <= 0) { if (n < 0) { status = "The microphone stopped."; break; } continue; }
                for (int i = 0; i < n; i++) f[i] = blk[i] / 32768f;
                int ck = capKind;
                if (ck >= 0) {
                    int take = Math.min(n, cap[ck].length - capN[ck]);
                    System.arraycopy(f, 0, cap[ck], capN[ck], take); capN[ck] += take;
                    if (capN[ck] >= cap[ck].length) capKind = -1;
                    continue;
                }
                if (calibrating || !ear.ready()) { samples += n; continue; }
                ear.process(f, n, l);
                samples += n;
                if (++quietBlocks % 5 == 0) overCheck();
            }
        }

        private void overCheck() {
            KeyDecoder d = decoder[0];
            double now = samples * 1000.0 / RATE;
            long we = warmEnd;
            if (learning) return;
            if (we > 0) {
                warmText = d.text();
                if (System.currentTimeMillis() > we) { warmEnd = 0; }
                return;
            }
            if (d.marks() == 0 || !d.overEnded(now, prefs.getInt("manners", 0))) return;
            Band b = CwService.band;
            Partner p = b == null ? null : b.livePartner();
            lastJson = d.timingsJson(""); lastText = d.text();
            if (p != null) {
                double wpm = Math.max(5, Math.min(40, d.wpm()));
                b.userOver(Heard.parse(d.letters(), p.expect()), (d.lastUpMs() - d.markStartMs(0)) / 1000.0, wpm, CwService.dial, 0);
            }
            d.reset();
            heard = "";
            sent = lastText;
        }
        volatile String sent = "";
        String warmText() { return warmText; }
        boolean warming() { return warmEnd > 0; }
        String lastJson() { return lastJson; }
    }
    private Mic mic;
    private Mic mic() { if (mic == null) mic = new Mic(); return mic; }

    @Override protected void onPause() { if (mic != null) mic.stop(); super.onPause(); }
    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) { super.onRequestPermissionsResult(code, perms, results); }

    class Bridge {
        @JavascriptInterface public String state() {
            try {
                JSONObject o = new JSONObject();
                o.put("status", CwService.status); o.put("title", CwService.title); o.put("source", CwService.source);
                o.put("word", CwService.word); o.put("words", CwService.words); o.put("error", CwService.error);
                o.put("id", "" + CwService.storyId); o.put("refreshing", Refresher.busy());
                long at = prefs.getLong("sleepAt", 0);
                o.put("sleepLeft", at > 0 ? (int) Math.max(1, (at - System.currentTimeMillis() + 59999) / 60000) : 0);
                o.put("news", db.counts(Refresher.notBefore(prefs), online()));
                return o.toString();
            } catch (Exception e) { return "{\"status\":\"stopped\",\"error\":\"" + e.getClass().getSimpleName() + "\",\"news\":{\"state\":\"has_news\"}}"; }
        }
        @JavascriptInterface public String band() {
            Band b = CwService.band;
            // passLo and passHi are the receiver's passband in Hz around the frequency it listens on.
            int tone = prefs.getInt("pitch", 600);
            double[] heard = Rx.heard(Math.max(Rx.WIDE, Math.min(Rx.NARROW, prefs.getInt("filter", Rx.WIDE))), tone, 1, 0);
            return "{\"on\":" + (b != null) + ",\"dial\":" + String.format(java.util.Locale.ROOT, "%.2f", CwService.dial) + ",\"rit\":" + CwService.rit
                + ",\"passLo\":" + Math.round(heard[0]) + ",\"passHi\":" + Math.round(heard[1])
                + ",\"low\":" + Band.LOW + ",\"high\":" + Band.HIGH + ",\"news\":" + Band.NEWS
                + ",\"sky\":\"" + (b == null ? "" : b.sky()) + "\""
                + ",\"contest\":\"" + (b == null || b.contest() == null ? "" : b.contest()) + "\""
                + ",\"act\":" + (b == null ? "[]" : b.activityJson())
                + ",\"story\":" + (b == null ? "{\"on\":false}" : b.storyJson()) + "}";
        }
        /** The regions the listener can be in, as a JSON list of names; the region setting is an index into it. */
        @JavascriptInterface public String regions() { return new org.json.JSONArray(java.util.Arrays.asList(Prop.REGION_NAME)).toString(); }
        /** For the waterfall and the S-meter, read many times a second: {"s": meter, "n": bulletin keyed share, "w": stations}. */
        @JavascriptInterface public String panel() {
            Band b = CwService.band;
            int all = CwService.newsAll.getAndSet(0), on = CwService.newsOn.getAndSet(0);
            return String.format(java.util.Locale.ROOT, "{\"s\":%.4f,\"n\":%.2f,\"w\":", CwService.meter, all == 0 ? 0 : Math.min(1, on / (double) all)) + (b == null ? "[]" : b.waterfallJson()) + "}";
        }
        @JavascriptInterface public void setRit(int hz) {
            int v = Math.max(-500, Math.min(500, hz));
            CwService.rit = v;
            CwService.lastUserTune = System.currentTimeMillis();
            prefs.edit().putInt("rit", v).apply();
        }
        /** A light click in the fingers, for the tuning knob. */
        @JavascriptInterface public void tick() { runOnUiThread(() -> web.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)); }
        @JavascriptInterface public String bandCopy() { Band b = CwService.band; return b == null ? "" : b.copy(CwService.rxKhz()); }
        @JavascriptInterface public void setDial(double khz) {
            double d = Math.max(Band.LOW, Math.min(Band.HIGH, khz));
            CwService.dial = d;
            CwService.lastUserTune = System.currentTimeMillis();
            prefs.edit().putFloat("dial", (float) d).apply();
        }
        @JavascriptInterface public double nextSignal(int dir) {
            Band b = CwService.band;
            // Lands the receiver on the station, so with RIT on the dial ends up beside it.
            double d = (b == null ? CwService.rxKhz() : b.next(CwService.rxKhz(), dir)) - CwService.rit / 1000.0;
            setDial(d);
            return CwService.dial;
        }
        @JavascriptInterface public String abbrevs() { return Abbrev.json(); }
        @JavascriptInterface public String settings() {
            try {
                JSONObject o = new JSONObject();
                o.put("wpm", prefs.getInt("wpm", 20)); o.put("eff", prefs.getInt("eff", 12)); o.put("pitch", prefs.getInt("pitch", 600));
                o.put("volume", prefs.getInt("volume", 80)); o.put("mode", prefs.getInt("mode", TextNorm.HEADLINE)); o.put("abbr", prefs.getInt("abbr", Abbrev.LIGHT)); o.put("band", prefs.getInt("band", 1)); o.put("activity", prefs.getInt("activity", 1));
                o.put("bed", prefs.getInt("bed", Bed.BAND)); o.put("bedDb", prefs.getInt("bedDb", -24));
                o.put("filter", prefs.getInt("filter", Rx.WIDE)); o.put("agc", prefs.getInt("agc", 1)); o.put("side", prefs.getInt("side", 0)); o.put("shift", prefs.getInt("shift", 0)); o.put("apf", prefs.getInt("apf", 0)); o.put("nb", prefs.getInt("nb", 0)); o.put("weather", prefs.getInt("weather", 0)); o.put("weather2", prefs.getInt("weather2", Sky.STATIC)); o.put("realwx", prefs.getInt("realwx", 0)); o.put("span", prefs.getInt("span", 40)); o.put("region", prefs.getInt("region", 0));
                o.put("auto", prefs.getBoolean("auto", true) ? 1 : 0); o.put("announce", prefs.getBoolean("announce", true) ? 1 : 0);
                o.put("follow", prefs.getInt("follow", 0)); o.put("sleep", prefs.getInt("sleep", 0));
                o.put("age", prefs.getInt("age", 0)); o.put("refresh", prefs.getInt("refresh", 30));
                Profile d = Profile.defaults();
                o.put("mycall", prefs.getString("mycall", d.call)); o.put("myname", prefs.getString("myname", d.name)); o.put("myqth", prefs.getString("myqth", d.qth));
                o.put("myrig", prefs.getString("myrig", d.rig)); o.put("myant", prefs.getString("myant", d.ant));
                o.put("power", prefs.getInt("power", d.power)); o.put("sendwpm", prefs.getInt("sendwpm", d.sendWpm)); o.put("manners", prefs.getInt("manners", d.manners)); o.put("sendwith", prefs.getInt("sendwith", d.sendWith));
                o.put("story", prefs.getString("story", ""));
                o.put("hint", prefs.getInt("hint", 1)); o.put("showtext", prefs.getInt("showtext", 0)); o.put("ckind", prefs.getInt("ckind", Partner.STANDARD));
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }
        @JavascriptInterface public void set(String key, int value) {
            SharedPreferences.Editor e = prefs.edit();
            if (key.equals("auto") || key.equals("announce")) e.putBoolean(key, value != 0); else e.putInt(key, value);
            if (key.equals("wpm") && prefs.getInt("eff", 12) > value) e.putInt("eff", value);
            if (key.equals("eff")) e.putInt("eff", Math.min(value, prefs.getInt("wpm", 20)));
            if (key.equals("sleep")) e.putLong("sleepAt", value > 0 ? System.currentTimeMillis() + value * 60000L : 0);
            e.commit();
            if (key.equals("refresh")) RefreshJob.schedule(MainActivity.this, value);
            if (!CwService.status.equals(CwService.STOPPED)) service(CwService.APPLY, -1);
        }
        /** Stores one text of "Your station". Returns "" when accepted, otherwise a message for the page and nothing is stored. */
        @JavascriptInterface public String setText(String key, String value) {
            if (!(key.equals("mycall") || key.equals("myname") || key.equals("myqth") || key.equals("myrig") || key.equals("myant"))) return "Unknown setting";
            String v = Profile.clean(key, value), msg = Profile.check(key, v);
            if (!msg.isEmpty()) return msg;
            prefs.edit().putString(key, v).commit();
            if (!CwService.status.equals(CwService.STOPPED)) service(CwService.APPLY, -1);
            return "";
        }

        // ---- the story station ----

        /** The stories that can be played, shipped ones first: [{"id","title","added","total","episodes":[{"t","first"}]}]. */
        @JavascriptInterface public String stories() { try { return Stories.listJson(MainActivity.this); } catch (Exception e) { return "[]"; } }
        /** Chooses the story on 7010 kHz ("" for none). It resumes where it was last heard. Returns "" or a message. */
        @JavascriptInterface public String chooseStory(String id) {
            String v = id == null ? "" : id;
            if (!v.isEmpty() && !Stories.exists(MainActivity.this, v)) return "That story is not there any more.";
            prefs.edit().putString("story", v).commit();
            if (!CwService.status.equals(CwService.STOPPED)) service(CwService.APPLY, -1);
            return "";
        }
        /** Opens the system file picker for a .txt story; the result comes to the page as onStoryPicked({id, title} or {error}). */
        @JavascriptInterface public void pickStory() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/*", "*/*"});
                try { startActivityForResult(i, PICK_STORY); }
                catch (RuntimeException e) { storyPicked("{\"error\":\"No file picker on this phone.\"}"); }
            });
        }
        /** Removes an added story; the shipped ones stay. Returns "" or a message. */
        @JavascriptInterface public String removeStory(String id) {
            if (id == null || !id.startsWith("file:")) return "Only added files can be removed.";
            boolean chosen = id.equals(prefs.getString("story", ""));
            SharedPreferences.Editor e = prefs.edit().remove(Stories.lineKey(id));
            if (chosen) e.putString("story", "");
            e.commit();
            if (chosen && !CwService.status.equals(CwService.STOPPED)) service(CwService.APPLY, -1);
            return Stories.remove(MainActivity.this, id) ? "" : "The file could not be removed.";
        }
        /** Goes to an over of the story (Episode menu, Position slider, Again) and keeps that position at once. */
        @JavascriptInterface public void storyFrom(int line) {
            Band b = CwService.band;
            String on = CwService.storyOn;
            if (b != null && !on.isEmpty()) {
                b.setStoryLine(line);
                prefs.edit().putInt(Stories.lineKey(on), b.storyLine()).apply();
            } else {
                String id = prefs.getString("story", "");
                if (!id.isEmpty()) prefs.edit().putInt(Stories.lineKey(id), Math.max(0, line)).apply();
            }
        }

        // ---- a live contact with a station on the band ----

        @JavascriptInterface public String liveState() {
            try {
                Band b = CwService.band;
                JSONObject o = new JSONObject(b == null ? "{\"on\":false}" : b.liveJson());
                boolean on = o.optBoolean("on");
                if (!on) finishContact();
                o.put("canAnswer", b != null && CwService.engine != null && !on && b.cqNear(CwService.rxKhz()) != null);
                o.put("canCall", b != null && CwService.engine != null && !on && b.clearAt(CwService.dial));
                o.put("tx", CwService.dial);
                synchronized (liveLock) { o.put("summary", on ? "" : summary); }
                return o.toString();
            } catch (Exception e) { return "{\"on\":false}"; }
        }
        /** Answers the CQ station in the filter. Returns "" or a message for the page. */
        @JavascriptInterface public String answer() {
            Band b = CwService.band;
            if (b == null || CwService.engine == null) return "Press play to start the band.";
            double khz = CwService.rxKhz();
            Qso q = b.cqNear(khz);
            if (q == null) return "No station is calling CQ here.";
            Profile me = profile();
            Partner p = Partner.from(q, 0, b.scene(), new Dsp.Rng(System.nanoTime()), me, me.manners);
            p.baseWpm = prefs.getInt("wpm", 20); p.logged = db.logCount();
            if (!b.takeOver(khz, p)) return "That station cannot be answered now.";
            synchronized (liveLock) { contact = p; contactKhz = khz; summary = ""; }
            return "";
        }
        /** Calls CQ on the dial: your CQ is played, and a station answers after it. */
        @JavascriptInterface public String callCq() {
            Band b = CwService.band;
            Engine e = CwService.engine;
            if (b == null || e == null) return "Press play to start the band.";
            if (!b.clearAt(CwService.dial)) return "Another station is on this frequency. Tune to a clear one.";
            Profile me = profile();
            Partner p = Partner.answering(new Dsp.Rng(System.nanoTime()), b.scene(), me, me.manners, Math.max(0, Math.min(2, prefs.getInt("ckind", Partner.STANDARD))));
            p.baseWpm = prefs.getInt("wpm", 20); p.logged = db.logCount();
            Keying k = Keying.build("CQ CQ DE " + me.call + " " + me.call + " K", new Timing(me.sendWpm, me.sendWpm), CwService.RATE);
            double after = k.totalSamples / (double) CwService.RATE;
            if (!b.answerMe(CwService.dial, p, after)) return "A contact is already running.";
            e.send(k);
            synchronized (liveLock) { contact = p; contactKhz = CwService.dial; summary = ""; }
            return "";
        }
        /** Plays what the user typed and gives it to the partner as an over. */
        @JavascriptInterface public String sendText(String text) {
            Band b = CwService.band;
            Engine e = CwService.engine;
            Partner p = b == null ? null : b.livePartner();
            if (p == null || e == null) return "No contact is running.";
            String t = text == null ? "" : text.toUpperCase().replaceAll("\\s+", " ").trim();
            if (t.isEmpty()) return "Type something to send.";
            Profile me = profile();
            Keying k = Keying.build(t, new Timing(me.sendWpm, me.sendWpm), CwService.RATE);
            double secs = k.totalSamples / (double) CwService.RATE;
            e.send(k);
            b.userOver(Heard.parse(t, p.expect()), secs, me.sendWpm, CwService.dial, secs);
            return "";
        }
        /** Puts the dial on the live station and clears RIT. */
        @JavascriptInterface public void net() {
            Band b = CwService.band;
            double d = b == null ? Double.NaN : b.liveKhz();
            if (Double.isNaN(d)) return;
            CwService.dial = d; CwService.rit = 0; CwService.lastUserTune = System.currentTimeMillis();
            prefs.edit().putFloat("dial", (float) d).putInt("rit", 0).apply();
        }
        @JavascriptInterface public void endContact() {
            Band b = CwService.band;
            finishContact();
            if (b != null) b.endLive();
        }

        // ---- the key through the microphone ----

        /** Asks for the microphone permission if it is not yet granted. Returns true when it is granted now. */
        @JavascriptInterface public boolean micPermission() {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true;
            runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 2));
            return false;
        }
        /** The page says whether the microphone is wanted (Band tab visible, a contact or warm-up open, "My key" chosen). */
        @JavascriptInterface public void micOn(boolean on) {
            if (on) mic().start(); else if (mic != null && mic.running() && !mic.recording() && !mic.warming()) mic.stop();
        }
        /** Records one calibration step: "held", "quiet" or "band". Returns "" or a message. */
        @JavascriptInterface public String micRecord(String kind, double seconds) {
            return mic().record(kind.equals("held") ? 0 : kind.equals("quiet") ? 1 : 2, seconds);
        }
        @JavascriptInterface public String micCalibrate() {
            try {
                Mic m = mic();
                boolean ok = m.calibrate();
                JSONObject o = new JSONObject();
                o.put("ok", ok); o.put("pitch", Math.round(m.ear.pitch())); o.put("snr", Math.round(m.ear.snrDb())); o.put("leakOk", m.ear.leakOk());
                return o.toString();
            } catch (Exception e) { return "{\"ok\":false}"; }
        }
        /** Learns the user's hand: call with listening on, send the text three times, then fistDone(). */
        @JavascriptInterface public void fistStart() { Mic m = mic(); m.start(); m.decoder[0].reset(); m.heard = ""; m.learning = true; }
        @JavascriptInterface public String fistDone(String text) {
            Mic m = mic();
            m.learning = false;
            KeyDecoder d = m.decoder[0];
            java.util.List<Long> marks = new java.util.ArrayList<>(), gaps = new java.util.ArrayList<>();
            for (int i = 0; i < d.marks(); i++) {
                marks.add(Math.round(d.markEndMs(i) - d.markStartMs(i)));
                if (i + 1 < d.marks()) gaps.add(Math.round(d.markStartMs(i + 1) - d.markEndMs(i)));
            }
            KeyDecoder.Fist5 f = KeyDecoder.Fist5.learn(marks, gaps, text);
            d.reset();
            if (f == null) return "";
            prefs.edit().putString("fist5", f.toString()).apply();
            m.decoder[0] = new KeyDecoder(prefs.getInt("sendwpm", 15), f);
            return String.format(java.util.Locale.ROOT, "Your dashes are about %.1f dots; I will read them that way.", f.dashDots());
        }
        @JavascriptInterface public void warmUp(boolean on) { Mic m = mic(); if (on) m.start(); m.warm(on); }
        /** State for the set-up, the warm-up and the "You sent" line. */
        @JavascriptInterface public String micState() {
            try {
                JSONObject o = new JSONObject();
                Mic m = mic;
                o.put("running", m != null && m.running());
                o.put("perm", checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
                o.put("calibrated", !prefs.getString("keycal", "").isEmpty());
                o.put("fist", !prefs.getString("fist5", "").isEmpty());
                if (m != null) {
                    o.put("recording", m.recording()); o.put("key", m.keyDown); o.put("heard", m.heard); o.put("sent", m.sent); o.put("status", m.status);
                    o.put("have0", m.have(0)); o.put("have1", m.have(1)); o.put("have2", m.have(2));
                    o.put("warming", m.warming()); o.put("warm", m.warmText());
                    o.put("pitch", Math.round(m.ear.pitch()));
                }
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }
        /** Keeps the last over's timings and what the user meant (timings only, never sound), up to 50. */
        @JavascriptInterface public void notWhatISent(String meant) {
            Mic m = mic;
            if (m == null || m.lastJson().isEmpty()) return;
            try {
                org.json.JSONArray a = new org.json.JSONArray(prefs.getString("saved", "[]"));
                JSONObject o = new JSONObject(m.lastJson()); o.put("meant", meant == null ? "" : meant.toUpperCase());
                a.put(o);
                org.json.JSONArray keep = new org.json.JSONArray();
                for (int i = Math.max(0, a.length() - 50); i < a.length(); i++) keep.put(a.get(i));
                prefs.edit().putString("saved", keep.toString()).apply();
            } catch (Exception ignored) { }
        }
        /** Shares the saved overs as JSON, for tests/data/fist/. */
        @JavascriptInterface public void exportSaved() {
            String t = prefs.getString("saved", "[]");
            runOnUiThread(() -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t).putExtra(Intent.EXTRA_SUBJECT, "CW News key timings"), "Export")));
        }
        @JavascriptInterface public String log() { try { return db.logJson(200); } catch (Exception e) { return "[]"; } }
        @JavascriptInterface public String logSummary() { return "{\"contacts\":" + db.logCount() + ",\"countries\":" + db.logCountries() + "}"; }
        @JavascriptInterface public void command(String a) {
            if (a.equals(CwService.PLAY) || a.equals(CwService.TOGGLE) || a.equals(CwService.REPLAY)) runOnUiThread(MainActivity.this::askNotifications);
            service(a, -1);
        }
        @JavascriptInterface public void playStory(String id) { runOnUiThread(MainActivity.this::askNotifications); service(CwService.PLAY_ID, Long.parseLong(id)); }
        @JavascriptInterface public String feeds() { try { return db.feedsJson(); } catch (Exception e) { return "[]"; } }
        @JavascriptInterface public String recent() { try { return db.recentJson(LIST); } catch (Exception e) { return "[]"; } }
        @JavascriptInterface public String addFeed(String url, String name) {
            try {
                String u = FeedParser.validateUrl(url);
                long id = db.addFeed(u, name.trim());
                if (id == -1) return "That feed is already in the list";
                new Thread(() -> Refresher.single(MainActivity.this, id), "add-feed").start();
                return "";
            } catch (FeedParser.FeedException e) { return e.getMessage(); }
        }
        @JavascriptInterface public String editFeed(String id, String url, String name) {
            try {
                String u = FeedParser.validateUrl(url);
                long n = Long.parseLong(id);
                if (!db.editFeed(n, u, name.trim())) return "That feed is already in the list";
                new Thread(() -> Refresher.single(MainActivity.this, n), "edit-feed").start();
                return "";
            } catch (FeedParser.FeedException e) { return e.getMessage(); }
        }
        @JavascriptInterface public void setFeedEnabled(String id, boolean on) { db.setEnabled(Long.parseLong(id), on); }
        @JavascriptInterface public void removeFeed(String id) { db.removeFeed(Long.parseLong(id)); }
        @JavascriptInterface public void refresh() { new Thread(() -> Refresher.all(MainActivity.this), "refresh").start(); }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != PICK_STORY) return;
        if (result != RESULT_OK || data == null || data.getData() == null) { storyPicked("{\"cancelled\":true}"); return; }
        android.net.Uri uri = data.getData();
        new Thread(() -> {
            String r = Stories.add(this, uri);
            try {
                String id = new JSONObject(r).optString("id", "");
                if (!id.isEmpty()) {
                    // An added story is the one you want to hear: it becomes the story on 7010 kHz.
                    prefs.edit().putString("story", id).commit();
                    if (!CwService.status.equals(CwService.STOPPED)) service(CwService.APPLY, -1);
                }
            } catch (org.json.JSONException ignored) { }
            storyPicked(r);
        }, "add-story").start();
    }

    /** Tells the page how adding a story went. */
    private void storyPicked(String json) {
        runOnUiThread(() -> web.evaluateJavascript("typeof onStoryPicked==='function'&&onStoryPicked(" + json + ")", null));
    }

    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
