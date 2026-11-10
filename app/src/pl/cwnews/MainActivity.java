package pl.cwnews;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioManager;
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
    private static final int BG = Color.rgb(16, 34, 44), LIST = 60;
    private WebView web;
    private SharedPreferences prefs;
    private Db db;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs = getSharedPreferences("cw", MODE_PRIVATE);
        CwService.dial = prefs.getFloat("dial", (float) Band.NEWS);
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

    class Bridge {
        @JavascriptInterface public String state() {
            try {
                JSONObject o = new JSONObject();
                o.put("status", CwService.status); o.put("title", CwService.title); o.put("source", CwService.source);
                o.put("word", CwService.word); o.put("words", CwService.words); o.put("error", CwService.error);
                o.put("id", "" + CwService.storyId); o.put("refreshing", Refresher.busy());
                o.put("news", db.counts(Refresher.notBefore(prefs), online()));
                return o.toString();
            } catch (Exception e) { return "{\"status\":\"stopped\",\"error\":\"" + e.getClass().getSimpleName() + "\",\"news\":{\"state\":\"has_news\"}}"; }
        }
        @JavascriptInterface public String band() {
            Band b = CwService.band;
            return "{\"on\":" + (b != null) + ",\"dial\":" + String.format(java.util.Locale.ROOT, "%.2f", CwService.dial) + ",\"low\":" + Band.LOW + ",\"high\":" + Band.HIGH + ",\"news\":" + Band.NEWS
                + ",\"act\":" + (b == null ? "[]" : b.activityJson()) + "}";
        }
        @JavascriptInterface public String bandCopy() { Band b = CwService.band; return b == null ? "" : b.copy(CwService.dial); }
        @JavascriptInterface public void setDial(double khz) {
            double d = Math.max(Band.LOW, Math.min(Band.HIGH, khz));
            CwService.dial = d;
            prefs.edit().putFloat("dial", (float) d).apply();
        }
        @JavascriptInterface public double nextSignal(int dir) {
            Band b = CwService.band;
            double d = b == null ? CwService.dial : b.next(CwService.dial, dir);
            setDial(d);
            return d;
        }
        @JavascriptInterface public String abbrevs() { return Abbrev.json(); }
        @JavascriptInterface public String settings() {
            try {
                JSONObject o = new JSONObject();
                o.put("wpm", prefs.getInt("wpm", 20)); o.put("eff", prefs.getInt("eff", 12)); o.put("pitch", prefs.getInt("pitch", 600));
                o.put("volume", prefs.getInt("volume", 80)); o.put("mode", prefs.getInt("mode", TextNorm.HEADLINE)); o.put("abbr", prefs.getInt("abbr", Abbrev.LIGHT)); o.put("band", prefs.getInt("band", 1)); o.put("activity", prefs.getInt("activity", 1));
                o.put("bed", prefs.getInt("bed", Bed.BAND)); o.put("bedDb", prefs.getInt("bedDb", -24));
                o.put("auto", prefs.getBoolean("auto", true) ? 1 : 0); o.put("announce", prefs.getBoolean("announce", true) ? 1 : 0);
                o.put("age", prefs.getInt("age", 0)); o.put("refresh", prefs.getInt("refresh", 30));
                return o.toString();
            } catch (Exception e) { return "{}"; }
        }
        @JavascriptInterface public void set(String key, int value) {
            SharedPreferences.Editor e = prefs.edit();
            if (key.equals("auto") || key.equals("announce")) e.putBoolean(key, value != 0); else e.putInt(key, value);
            if (key.equals("wpm") && prefs.getInt("eff", 12) > value) e.putInt("eff", value);
            if (key.equals("eff")) e.putInt("eff", Math.min(value, prefs.getInt("wpm", 20)));
            e.commit();
            if (key.equals("refresh")) RefreshJob.schedule(MainActivity.this, value);
            if (!CwService.status.equals(CwService.STOPPED)) service(CwService.APPLY, -1);
        }
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

    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
