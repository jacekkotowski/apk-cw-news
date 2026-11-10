package pl.cwnews;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fetches enabled feeds. Failures are stored on the feed so the screen can show them. */
final class Refresher {
    private Refresher() {}
    private static final int CONNECT_MS = 15000, READ_MS = 20000, MAX_BYTES = 5 * 1024 * 1024, TRIES = 2;
    private static final AtomicBoolean BUSY = new AtomicBoolean();

    static boolean busy() { return BUSY.get(); }

    static void all(Context c) {
        if (!BUSY.compareAndSet(false, true)) return;
        try {
            Db db = Db.get(c);
            for (long id : db.feedIds(true)) one(db, id);
        } finally { BUSY.set(false); }
    }

    static void single(Context c, long id) {
        if (!BUSY.compareAndSet(false, true)) return;
        try { one(Db.get(c), id); } finally { BUSY.set(false); }
    }

    private static void one(Db db, long id) {
        String url = db.feedUrl(id);
        if (url == null) return;
        String problem = null;
        for (int t = 0; t < TRIES; t++) {
            try {
                db.store(id, FeedParser.parse(fetch(url), url), System.currentTimeMillis());
                return;
            } catch (FeedParser.FeedException e) { db.feedFailed(id, e.getMessage()); return; }
            catch (UnknownHostException e) { problem = "No network, or the server name was not found"; }
            catch (SocketTimeoutException e) { problem = "The server took too long to answer"; }
            catch (javax.net.ssl.SSLException e) { problem = "The server's secure connection could not be verified"; }
            catch (IOException e) { problem = String.valueOf(e.getMessage()); }
            catch (RuntimeException e) { problem = "Fetch failed: " + e; }
        }
        db.feedFailed(id, problem);
    }

    private static byte[] fetch(String address) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(address).openConnection();
        try {
            h.setConnectTimeout(CONNECT_MS); h.setReadTimeout(READ_MS);
            h.setRequestProperty("User-Agent", "CWNews/2.0 (Android RSS to Morse)");
            h.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*");
            int code = h.getResponseCode();
            if (code != 200) throw new IOException("The server answered HTTP " + code);
            try (InputStream in = h.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[16384];
                for (int n; (n = in.read(b)) > 0; ) {
                    out.write(b, 0, n);
                    if (out.size() > MAX_BYTES) throw new IOException("The feed is larger than 5 MB");
                }
                return out.toByteArray();
            }
        } finally { h.disconnect(); }
    }

    /** Oldest publication time still allowed by the Story age setting. */
    static long notBefore(SharedPreferences p) {
        int hours = p.getInt("age", 0);
        return hours <= 0 ? 0 : System.currentTimeMillis() - hours * 3600000L;
    }
}
